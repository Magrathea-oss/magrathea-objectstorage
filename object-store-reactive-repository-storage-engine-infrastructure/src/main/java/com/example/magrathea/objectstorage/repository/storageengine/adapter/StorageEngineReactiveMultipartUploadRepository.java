package com.example.magrathea.objectstorage.repository.storageengine.adapter;

import com.example.magrathea.objectstore.domain.aggregate.Bucket;
import com.example.magrathea.objectstore.domain.aggregate.MultipartUpload;
import com.example.magrathea.objectstore.domain.valueobject.UploadId;
import com.example.magrathea.objectstore.domain.valueobject.UploadPart;
import com.example.magrathea.objectstore.reactive.repository.application.MultipartUploadCommandRepository;
import com.example.magrathea.objectstore.reactive.repository.application.MultipartUploadQueryRepository;
import com.example.magrathea.objectstore.reactive.repository.application.CommandResult;
import com.example.magrathea.storageengine.application.service.ReactiveStorageOrchestrator;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Repository;
import reactor.core.publisher.Mono;
import reactor.core.publisher.Flux;
import reactor.core.scheduler.Schedulers;

import java.util.concurrent.atomic.AtomicLong;

/**
 * Implementation of MultipartUpload repository interfaces backed by the Storage Engine.
 * <p>
 * Multipart upload sessions (upload id, bucket, key, recorded parts with part
 * numbers, ETags and sizes, completed/aborted state) are durably persisted
 * through {@link MultipartUploadStateStore} (JSON documents under
 * {@code {storageRoot}/metadata/multipart-uploads}) with crash-safe atomic
 * commits, so in-progress multipart uploads survive a process restart.
 * Active only when the {@code storage-engine} Spring profile is enabled.
 * </p>
 */
@Repository
@Profile("storage-engine")
public class StorageEngineReactiveMultipartUploadRepository
        implements MultipartUploadCommandRepository, MultipartUploadQueryRepository {

    private final ReactiveStorageOrchestrator orchestrator;
    private final MultipartUploadStateStore store;
    private final AtomicLong versionCounter = new AtomicLong(1);

    public StorageEngineReactiveMultipartUploadRepository(
            ReactiveStorageOrchestrator orchestrator) {
        this(orchestrator, "");
    }

    @Autowired
    public StorageEngineReactiveMultipartUploadRepository(
            ReactiveStorageOrchestrator orchestrator,
            @Value("${storage.engine.filesystem.root:}") String storageRoot) {
        this.orchestrator = orchestrator;
        this.store = new MultipartUploadStateStore(DurableJson.resolveStorageRoot(storageRoot)
            .resolve("metadata").resolve("multipart-uploads"));
    }

    @Override
    public Mono<CommandResult<MultipartUpload>> save(MultipartUpload upload) {
        return Mono.fromCallable(() -> {
                boolean exists = store.exists(upload.uploadId());
                MultipartUpload clean = store.save(upload);
                long version = versionCounter.getAndIncrement();
                return exists
                    ? new CommandResult.Updated<>(clean, upload.domainEvents(), version)
                    : (CommandResult<MultipartUpload>) new CommandResult.Created<>(
                        clean, upload.domainEvents(), version);
            })
            .subscribeOn(Schedulers.boundedElastic());
    }

    @Override
    public Mono<CommandResult<MultipartUpload>> delete(MultipartUpload upload) {
        return Mono.fromCallable(() -> store.delete(upload.uploadId()))
            .subscribeOn(Schedulers.boundedElastic())
            .flatMap(removed -> removed
                .<Mono<CommandResult<MultipartUpload>>>map(value -> Mono.just(
                    new CommandResult.Deleted<>(
                        value, upload.domainEvents(), versionCounter.getAndIncrement())))
                .orElseGet(() -> Mono.error(new RuntimeException(
                    "MultipartUpload not found: " + upload.uploadId().value()))));
    }

    @Override
    public Mono<MultipartUpload> findById(UploadId uploadId) {
        return Mono.defer(() -> Mono.justOrEmpty(store.findById(uploadId)));
    }

    @Override
    public Flux<MultipartUpload> findByBucket(Bucket.Id bucketId) {
        return Flux.defer(() -> Flux.fromIterable(store.findAll())
            .filter(u -> u.bucketId().equals(bucketId)));
    }

    @Override
    public Flux<UploadPart> findParts(UploadId uploadId) {
        return Flux.defer(() -> store.findById(uploadId)
            .map(upload -> Flux.fromIterable(upload.parts()))
            .orElseGet(Flux::empty));
    }

    /**
     * Reset the store (for testing) — clears both the in-memory view and the
     * durable multipart state files.
     */
    public void reset() {
        store.wipe();
        versionCounter.set(1);
    }

    /**
     * Restart simulation (for testing) — discards all in-memory state and
     * reloads multipart upload state from the durable filesystem files, exactly
     * like a process restart would. Persisted files are kept.
     */
    public void reloadFromDisk() {
        store.reload();
    }
}
