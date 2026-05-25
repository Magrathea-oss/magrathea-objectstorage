package com.example.magrathea.reactive.infrastructure.adapter.persistence;

import com.example.magrathea.objectstorage.domain.aggregate.Bucket;
import com.example.magrathea.objectstorage.domain.aggregate.MultipartUpload;
import com.example.magrathea.objectstorage.domain.valueobject.UploadId;
import com.example.magrathea.objectstorage.domain.valueobject.UploadPart;
import com.example.magrathea.objectstorage.reactive.repository.application.MultipartUploadCommandRepository;
import com.example.magrathea.objectstorage.reactive.repository.application.MultipartUploadQueryRepository;
import com.example.magrathea.objectstorage.reactive.repository.application.CommandResult;
import reactor.core.publisher.Mono;
import reactor.core.publisher.Flux;
import org.springframework.stereotype.Repository;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;
import java.util.stream.Collectors;

@Repository
public class InMemoryReactiveMultipartUploadRepository implements MultipartUploadCommandRepository, MultipartUploadQueryRepository {

    private final Map<UploadId, MultipartUpload> store = new ConcurrentHashMap<>();
    private final AtomicLong versionCounter = new AtomicLong(1);

    @Override
    public Mono<CommandResult<MultipartUpload>> save(MultipartUpload upload) {
        return Mono.fromCallable(() -> {
            boolean exists = store.containsKey(upload.uploadId());
            MultipartUpload clean = upload.clearEvents();
            store.put(upload.uploadId(), clean);
            long version = versionCounter.getAndIncrement();
            if (exists) {
                return new CommandResult.Updated<>(clean, upload.domainEvents(), version);
            } else {
                return new CommandResult.Created<>(clean, upload.domainEvents(), version);
            }
        });
    }

    @Override
    public Mono<CommandResult<MultipartUpload>> delete(MultipartUpload upload) {
        return Mono.fromCallable(() -> {
            MultipartUpload removed = store.remove(upload.uploadId());
            if (removed == null) {
                throw new MultipartUploadNotFoundException(upload.uploadId());
            }
            return new CommandResult.Deleted<>(removed, upload.domainEvents(), versionCounter.getAndIncrement());
        });
    }

    @Override
    public Mono<MultipartUpload> findById(UploadId uploadId) {
        return Mono.fromCallable(() -> store.get(uploadId))
                .flatMap(Mono::justOrEmpty);
    }

    @Override
    public Flux<MultipartUpload> findByBucket(Bucket.Id bucketId) {
        return Flux.fromIterable(
            store.values().stream()
                .filter(u -> u.bucketId().equals(bucketId))
                .collect(Collectors.toList())
        );
    }

    @Override
    public Flux<UploadPart> findParts(UploadId uploadId) {
        MultipartUpload upload = store.get(uploadId);
        if (upload == null) {
            return Flux.empty();
        }
        return Flux.fromIterable(upload.parts());
    }
}
