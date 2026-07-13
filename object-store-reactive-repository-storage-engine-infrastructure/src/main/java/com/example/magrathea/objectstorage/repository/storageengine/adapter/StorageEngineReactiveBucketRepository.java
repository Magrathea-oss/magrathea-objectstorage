package com.example.magrathea.objectstorage.repository.storageengine.adapter;

import com.example.magrathea.objectstore.domain.aggregate.Bucket;
import com.example.magrathea.objectstore.domain.valueobject.AbacConfiguration;
import com.example.magrathea.objectstore.domain.valueobject.BucketMetadataConfiguration;
import com.example.magrathea.objectstore.domain.valueobject.BucketMetadataTableConfiguration;
import com.example.magrathea.objectstore.reactive.repository.application.BucketCommandRepository;
import com.example.magrathea.objectstore.reactive.repository.application.BucketQueryRepository;
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
import java.util.function.UnaryOperator;

/**
 * Implementation of Bucket repository interfaces backed by the Storage Engine.
 * <p>
 * The bucket namespace and every bucket configuration family are durably
 * persisted through {@link BucketStore} (JSON documents under
 * {@code {storageRoot}/metadata/buckets}) with crash-safe atomic commits, so
 * buckets survive a process restart. Active only when the
 * {@code storage-engine} Spring profile is enabled.
 * </p>
 */
@Repository
@Profile("storage-engine & !cluster")
public class StorageEngineReactiveBucketRepository
        implements BucketCommandRepository, BucketQueryRepository {

    private final ReactiveStorageOrchestrator orchestrator;
    private final BucketStore store;
    private final AtomicLong versionCounter = new AtomicLong(1);

    public StorageEngineReactiveBucketRepository(ReactiveStorageOrchestrator orchestrator) {
        this(orchestrator, "");
    }

    @Autowired
    public StorageEngineReactiveBucketRepository(
            ReactiveStorageOrchestrator orchestrator,
            @Value("${storage.engine.filesystem.root:}") String storageRoot) {
        this.orchestrator = orchestrator;
        this.store = new BucketStore(DurableJson.resolveStorageRoot(storageRoot)
            .resolve("metadata").resolve("buckets"));
    }

    // ── Aggregate operations ──

    @Override
    public Mono<CommandResult<Bucket>> save(Bucket bucket) {
        return Mono.fromCallable(() -> {
                boolean exists = store.findByName(bucket.name()).isPresent();
                Bucket clean = store.save(bucket);
                long version = versionCounter.getAndIncrement();
                return exists
                    ? new CommandResult.Updated<>(clean, bucket.domainEvents(), version)
                    : (CommandResult<Bucket>) new CommandResult.Created<>(
                        clean, bucket.domainEvents(), version);
            })
            .subscribeOn(Schedulers.boundedElastic());
    }

    @Override
    public Mono<CommandResult<Bucket>> delete(Bucket bucket) {
        return Mono.fromCallable(() -> store.delete(bucket.name()))
            .subscribeOn(Schedulers.boundedElastic())
            .flatMap(removed -> removed
                .<Mono<CommandResult<Bucket>>>map(value -> Mono.just(new CommandResult.Deleted<>(
                    value, bucket.domainEvents(), versionCounter.getAndIncrement())))
                .orElseGet(() -> Mono.error(new RuntimeException(
                    "Bucket not found: " + bucket.name()))));
    }

    @Override
    public Mono<Bucket> findById(Bucket.Id bucketId) {
        return Mono.defer(() -> Mono.justOrEmpty(store.findById(bucketId)));
    }

    @Override
    public Mono<Bucket> findByName(String bucketName) {
        return Mono.defer(() -> Mono.justOrEmpty(store.findByName(bucketName)));
    }

    @Override
    public Flux<Bucket> findAll() {
        return Flux.defer(() -> Flux.fromIterable(store.findAll()));
    }

    // ── Phase F config queries ──

    @Override
    public Mono<AbacConfiguration> findAbacConfiguration(String bucketName) {
        return findByName(bucketName)
            .flatMap(b -> Mono.justOrEmpty(
                b.bucketConfig() != null
                    ? b.bucketConfig().getAbacConfiguration().orElse(null) : null));
    }

    @Override
    public Mono<BucketMetadataConfiguration> findMetadataConfiguration(String bucketName) {
        return findByName(bucketName)
            .flatMap(b -> Mono.justOrEmpty(
                b.bucketConfig() != null
                    ? b.bucketConfig().getMetadataConfiguration().orElse(null) : null));
    }

    @Override
    public Mono<BucketMetadataTableConfiguration> findMetadataTableConfiguration(String bucketName) {
        return findByName(bucketName)
            .flatMap(b -> Mono.justOrEmpty(
                b.bucketConfig() != null
                    ? b.bucketConfig().getMetadataTableConfiguration().orElse(null) : null));
    }

    // ── Phase F config writes ──

    @Override
    public Mono<Void> saveAbacConfiguration(String bucketName, AbacConfiguration config) {
        return updateBucket(bucketName, b -> b.withAbacConfiguration(config));
    }

    @Override
    public Mono<Void> saveMetadataConfiguration(String bucketName,
                                                 BucketMetadataConfiguration config) {
        return updateBucket(bucketName, b -> b.withMetadataConfiguration(config));
    }

    @Override
    public Mono<Void> saveMetadataTableConfiguration(String bucketName,
                                                      BucketMetadataTableConfiguration config) {
        return updateBucket(bucketName, b -> b.withMetadataTableConfiguration(config));
    }

    /** Applies a config mutation to the named bucket and durably commits it. */
    private Mono<Void> updateBucket(String bucketName, UnaryOperator<Bucket> mutation) {
        return Mono.fromCallable(() -> {
                Bucket bucket = store.findByName(bucketName)
                    .orElseThrow(() -> new RuntimeException("Bucket not found: " + bucketName));
                store.save(mutation.apply(bucket));
                return true;
            })
            .subscribeOn(Schedulers.boundedElastic())
            .then();
    }

    /**
     * Reset the store (for testing) — clears both the in-memory view and the
     * durable bucket registry files.
     */
    public void reset() {
        store.wipe();
        versionCounter.set(1);
    }

    /**
     * Restart simulation (for testing) — discards all in-memory state and
     * reloads the bucket registry from the durable filesystem files, exactly
     * like a process restart would. Persisted files are kept.
     */
    public void reloadFromDisk() {
        store.reload();
    }
}
