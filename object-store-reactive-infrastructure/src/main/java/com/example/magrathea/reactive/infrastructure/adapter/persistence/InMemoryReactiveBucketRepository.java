package com.example.magrathea.reactive.infrastructure.adapter.persistence;

import com.example.magrathea.objectstore.domain.aggregate.Bucket;
import com.example.magrathea.objectstore.reactive.repository.application.BucketCommandRepository;
import com.example.magrathea.objectstore.reactive.repository.application.BucketQueryRepository;
import com.example.magrathea.objectstore.reactive.repository.application.CommandResult;
import reactor.core.publisher.Mono;
import reactor.core.publisher.Flux;
import org.springframework.stereotype.Repository;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;

@Repository
public class InMemoryReactiveBucketRepository implements BucketCommandRepository, BucketQueryRepository {

    private final Map<Bucket.Id, Bucket> store = new ConcurrentHashMap<>();
    private final AtomicLong versionCounter = new AtomicLong(1);

    @Override
    public Mono<CommandResult<Bucket>> save(Bucket bucket) {
        return Mono.fromCallable(() -> {
            boolean exists = store.containsKey(bucket.id());
            Bucket clean = bucket.clearEvents();
            store.put(bucket.id(), clean);
            long version = versionCounter.getAndIncrement();
            if (exists) {
                return new CommandResult.Updated<>(clean, bucket.domainEvents(), version);
            } else {
                return new CommandResult.Created<>(clean, bucket.domainEvents(), version);
            }
        });
    }

    @Override
    public Mono<CommandResult<Bucket>> delete(Bucket bucket) {
        return Mono.fromCallable(() -> {
            Bucket removed = store.remove(bucket.id());
            if (removed == null) {
                throw new BucketNotFoundException(bucket.id());
            }
            return new CommandResult.Deleted<>(removed, bucket.domainEvents(), versionCounter.getAndIncrement());
        });
    }

    @Override
    public Mono<Bucket> findById(Bucket.Id bucketId) {
        return Mono.fromCallable(() -> store.get(bucketId))
                .flatMap(Mono::justOrEmpty);
    }

    @Override
    public Mono<Bucket> findByName(String bucketName) {
        return Mono.fromCallable(() ->
            store.values().stream()
                .filter(b -> b.name().equals(bucketName))
                .findFirst()
                .orElse(null)
        ).flatMap(Mono::justOrEmpty);
    }

    @Override
    public Flux<Bucket> findAll() {
        return Flux.fromIterable(store.values());
    }

    public void reset() {
        store.clear();
        versionCounter.set(1);
    }
}
