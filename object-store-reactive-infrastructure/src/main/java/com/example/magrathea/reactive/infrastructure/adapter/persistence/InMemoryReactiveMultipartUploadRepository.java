package com.example.magrathea.reactive.infrastructure.adapter.persistence;

import com.example.magrathea.objectstore.domain.aggregate.Bucket;
import com.example.magrathea.objectstore.domain.aggregate.MultipartUpload;
import com.example.magrathea.objectstore.domain.valueobject.UploadId;
import com.example.magrathea.objectstore.domain.valueobject.UploadPart;
import com.example.magrathea.objectstore.reactive.repository.application.MultipartUploadCommandRepository;
import com.example.magrathea.objectstore.reactive.repository.application.MultipartUploadQueryRepository;
import com.example.magrathea.objectstore.reactive.repository.application.CommandResult;
import reactor.core.publisher.Mono;
import reactor.core.publisher.Flux;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Repository;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;

/** In-memory multipart-upload repository active only in the {@code single-node} (default) profile. */
@Repository
@Profile("single-node")
public class InMemoryReactiveMultipartUploadRepository implements MultipartUploadCommandRepository, MultipartUploadQueryRepository {

    private final Map<UploadId, MultipartUpload> store = new ConcurrentHashMap<>();
    private final AtomicLong versionCounter = new AtomicLong(1);

    @Override
    public Mono<CommandResult<MultipartUpload>> save(MultipartUpload upload) {
        return Mono.defer(() -> {
            boolean exists = store.containsKey(upload.uploadId());
            MultipartUpload clean = upload.clearEvents();
            store.put(upload.uploadId(), clean);
            long version = versionCounter.getAndIncrement();
            CommandResult<MultipartUpload> result = exists
                ? new CommandResult.Updated<>(clean, upload.domainEvents(), version)
                : new CommandResult.Created<>(clean, upload.domainEvents(), version);
            return Mono.just(result);
        });
    }

    @Override
    public Mono<CommandResult<MultipartUpload>> delete(MultipartUpload upload) {
        return Mono.defer(() -> {
            MultipartUpload removed = store.remove(upload.uploadId());
            if (removed == null) {
                return Mono.error(new MultipartUploadNotFoundException(upload.uploadId()));
            }
            return Mono.just(new CommandResult.Deleted<>(removed, upload.domainEvents(), versionCounter.getAndIncrement()));
        });
    }

    @Override
    public Mono<MultipartUpload> findById(UploadId uploadId) {
        return Mono.defer(() -> Mono.justOrEmpty(store.get(uploadId)));
    }

    @Override
    public Flux<MultipartUpload> findByBucket(Bucket.Id bucketId) {
        return Flux.defer(() -> Flux.fromIterable(store.values())
            .filter(u -> u.bucketId().equals(bucketId)));
    }

    @Override
    public Flux<UploadPart> findParts(UploadId uploadId) {
        return Flux.defer(() -> {
            MultipartUpload upload = store.get(uploadId);
            if (upload == null) {
                return Flux.empty();
            }
            return Flux.fromIterable(upload.parts());
        });
    }

    public void reset() {
        store.clear();
        versionCounter.set(1);
    }
}
