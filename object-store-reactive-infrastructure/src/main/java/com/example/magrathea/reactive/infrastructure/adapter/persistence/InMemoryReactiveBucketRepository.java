package com.example.magrathea.reactive.infrastructure.adapter.persistence;

import com.example.magrathea.objectstore.domain.aggregate.Bucket;
import com.example.magrathea.objectstore.domain.valueobject.AbacConfiguration;
import com.example.magrathea.objectstore.domain.valueobject.BucketMetadataConfiguration;
import com.example.magrathea.objectstore.domain.valueobject.BucketMetadataTableConfiguration;
import com.example.magrathea.objectstore.reactive.repository.application.BucketCommandRepository;
import com.example.magrathea.objectstore.reactive.repository.application.BucketQueryRepository;
import com.example.magrathea.objectstore.reactive.repository.application.CommandResult;
import reactor.core.publisher.Mono;
import reactor.core.publisher.Flux;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Repository;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;

/** In-memory bucket repository active only in the {@code single-node} (default) profile. */
@Repository
@Profile("single-node")
public class InMemoryReactiveBucketRepository implements BucketCommandRepository, BucketQueryRepository {

    private final Map<Bucket.Id, Bucket> store = new ConcurrentHashMap<>();
    private final AtomicLong versionCounter = new AtomicLong(1);

    // ── Aggregate operations ──

    @Override
    public Mono<CommandResult<Bucket>> save(Bucket bucket) {
        return Mono.defer(() -> {
            boolean exists = store.containsKey(bucket.id());
            Bucket clean = bucket.clearEvents();
            store.put(bucket.id(), clean);
            long version = versionCounter.getAndIncrement();
            CommandResult<Bucket> result = exists
                ? new CommandResult.Updated<>(clean, bucket.domainEvents(), version)
                : new CommandResult.Created<>(clean, bucket.domainEvents(), version);
            return Mono.just(result);
        });
    }

    @Override
    public Mono<CommandResult<Bucket>> delete(Bucket bucket) {
        return Mono.defer(() -> {
            Bucket removed = store.remove(bucket.id());
            if (removed == null) {
                return Mono.error(new BucketNotFoundException(bucket.name()));
            }
            return Mono.just(new CommandResult.Deleted<>(removed, bucket.domainEvents(), versionCounter.getAndIncrement()));
        });
    }

    @Override
    public Mono<Bucket> findById(Bucket.Id bucketId) {
        return Mono.defer(() -> Mono.justOrEmpty(store.get(bucketId)));
    }

    @Override
    public Mono<Bucket> findByName(String bucketName) {
        return Mono.defer(() -> Mono.justOrEmpty(
            store.values().stream()
                .filter(b -> b.name().equals(bucketName))
                .findFirst()
        ));
    }

    @Override
    public Flux<Bucket> findAll() {
        return Flux.defer(() -> Flux.fromIterable(store.values()));
    }

    // ── Phase F config queries ──

    @Override
    public Mono<AbacConfiguration> findAbacConfiguration(String bucketName) {
        return findByName(bucketName)
            .flatMap(b -> {
                var config = b.bucketConfig() != null
                    ? b.bucketConfig().getAbacConfiguration().orElse(null) : null;
                return Mono.justOrEmpty(config);
            });
    }

    @Override
    public Mono<BucketMetadataConfiguration> findMetadataConfiguration(String bucketName) {
        return findByName(bucketName)
            .flatMap(b -> {
                var config = b.bucketConfig() != null
                    ? b.bucketConfig().getMetadataConfiguration().orElse(null) : null;
                return Mono.justOrEmpty(config);
            });
    }

    @Override
    public Mono<BucketMetadataTableConfiguration> findMetadataTableConfiguration(String bucketName) {
        return findByName(bucketName)
            .flatMap(b -> {
                var config = b.bucketConfig() != null
                    ? b.bucketConfig().getMetadataTableConfiguration().orElse(null) : null;
                return Mono.justOrEmpty(config);
            });
    }

    // ── Phase F config writes ──

    @Override
    public Mono<Void> saveAbacConfiguration(String bucketName, AbacConfiguration config) {
        return findByName(bucketName)
            .flatMap(b -> {
                var updated = b.withAbacConfiguration(config).clearEvents();
                store.put(updated.id(), updated);
                return Mono.<Void>empty();
            })
            .switchIfEmpty(Mono.<Void>error(new BucketNotFoundException(bucketName)));
    }

    @Override
    public Mono<Void> saveMetadataConfiguration(String bucketName, BucketMetadataConfiguration config) {
        return findByName(bucketName)
            .flatMap(b -> {
                var updated = b.withMetadataConfiguration(config).clearEvents();
                store.put(updated.id(), updated);
                return Mono.<Void>empty();
            })
            .switchIfEmpty(Mono.<Void>error(new BucketNotFoundException(bucketName)));
    }

    @Override
    public Mono<Void> saveMetadataTableConfiguration(String bucketName, BucketMetadataTableConfiguration config) {
        return findByName(bucketName)
            .flatMap(b -> {
                var updated = b.withMetadataTableConfiguration(config).clearEvents();
                store.put(updated.id(), updated);
                return Mono.<Void>empty();
            })
            .switchIfEmpty(Mono.<Void>error(new BucketNotFoundException(bucketName)));
    }

    public void reset() {
        store.clear();
        versionCounter.set(1);
    }
}
