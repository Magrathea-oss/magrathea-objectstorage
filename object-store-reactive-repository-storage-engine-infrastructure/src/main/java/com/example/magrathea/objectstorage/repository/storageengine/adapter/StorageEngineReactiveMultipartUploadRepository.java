package com.example.magrathea.objectstorage.repository.storageengine.adapter;

import com.example.magrathea.objectstore.domain.aggregate.Bucket;
import com.example.magrathea.objectstore.domain.aggregate.MultipartUpload;
import com.example.magrathea.objectstore.domain.valueobject.UploadId;
import com.example.magrathea.objectstore.domain.valueobject.UploadPart;
import com.example.magrathea.objectstore.reactive.repository.application.MultipartUploadCommandRepository;
import com.example.magrathea.objectstore.reactive.repository.application.MultipartUploadQueryRepository;
import com.example.magrathea.objectstore.reactive.repository.application.CommandResult;
import com.example.magrathea.storageengine.application.service.ReactiveStorageOrchestrator;

import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Repository;
import reactor.core.publisher.Mono;
import reactor.core.publisher.Flux;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;

/**
 * Implementation of MultipartUpload repository interfaces backed by the Storage Engine.
 * <p>
 * Delegates multipart upload state to the Storage Engine via {@link ReactiveStorageOrchestrator}.
 * Active only when the {@code storage-engine} Spring profile is enabled.
 * </p>
 */
@Repository
@Profile("storage-engine")
public class StorageEngineReactiveMultipartUploadRepository
        implements MultipartUploadCommandRepository, MultipartUploadQueryRepository {

    private final ReactiveStorageOrchestrator orchestrator;
    private final Map<UploadId, MultipartUpload> store = new ConcurrentHashMap<>();
    private final AtomicLong versionCounter = new AtomicLong(1);

    public StorageEngineReactiveMultipartUploadRepository(
            ReactiveStorageOrchestrator orchestrator) {
        this.orchestrator = orchestrator;
    }

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
                return Mono.error(new RuntimeException(
                    "MultipartUpload not found: " + upload.uploadId().value()));
            }
            return Mono.just(new CommandResult.Deleted<>(
                removed, upload.domainEvents(), versionCounter.getAndIncrement()));
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

    /**
     * Reset the in-memory store (for testing).
     */
    public void reset() {
        store.clear();
        versionCounter.set(1);
    }
}
