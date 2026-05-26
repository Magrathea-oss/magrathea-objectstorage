package com.example.magrathea.reactive.infrastructure.adapter.persistence;

import com.example.magrathea.objectstore.domain.aggregate.Bucket;
import com.example.magrathea.objectstore.domain.aggregate.S3Object;
import com.example.magrathea.objectstore.domain.valueobject.ObjectKey;
import com.example.magrathea.objectstore.reactive.repository.application.S3ObjectCommandRepository;
import com.example.magrathea.objectstore.reactive.repository.application.S3ObjectQueryRepository;
import com.example.magrathea.objectstore.reactive.repository.application.CommandResult;
import reactor.core.publisher.Mono;
import reactor.core.publisher.Flux;
import org.springframework.stereotype.Repository;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;
import java.util.stream.Collectors;

@Repository
public class InMemoryReactiveS3ObjectRepository implements S3ObjectCommandRepository, S3ObjectQueryRepository {

    private final Map<S3Object.Id, S3Object> store = new ConcurrentHashMap<>();
    private final AtomicLong versionCounter = new AtomicLong(1);

    @Override
    public Mono<CommandResult<S3Object>> save(S3Object object) {
        return Mono.fromCallable(() -> {
            boolean exists = store.containsKey(object.id());
            S3Object clean = object.clearEvents();
            store.put(object.id(), clean);
            long version = versionCounter.getAndIncrement();
            if (exists) {
                return new CommandResult.Updated<>(clean, object.domainEvents(), version);
            } else {
                return new CommandResult.Created<>(clean, object.domainEvents(), version);
            }
        });
    }

    @Override
    public Mono<CommandResult<S3Object>> delete(S3Object object) {
        return Mono.fromCallable(() -> {
            S3Object removed = store.remove(object.id());
            if (removed == null) {
                throw new S3ObjectNotFoundException(object.id());
            }
            return new CommandResult.Deleted<>(removed, object.domainEvents(), versionCounter.getAndIncrement());
        });
    }

    @Override
    public Mono<S3Object> findById(S3Object.Id objectId) {
        return Mono.fromCallable(() -> store.get(objectId))
                .flatMap(Mono::justOrEmpty);
    }

    @Override
    public Mono<S3Object> findByBucketAndKey(Bucket.Id bucketId, ObjectKey key) {
        return Mono.fromCallable(() ->
            store.values().stream()
                .filter(obj -> obj.bucketId().equals(bucketId) && obj.key().equals(key))
                .findFirst()
                .orElse(null)
        ).flatMap(Mono::justOrEmpty);
    }

    @Override
    public Flux<S3Object> findByBucket(Bucket.Id bucketId) {
        return Flux.fromIterable(
            store.values().stream()
                .filter(obj -> obj.bucketId().equals(bucketId))
                .collect(Collectors.toList())
        );
    }

    @Override
    public Flux<Byte> getContent(S3Object.Id objectId) {
        // Content bytes live in infrastructure, not domain.
        // ContentDescriptor in the stored S3Object provides size and contentId
        // for an external content store — actual byte retrieval not implemented yet.
        return Flux.empty();
    }

    public void reset() {
        store.clear();
        versionCounter.set(1);
    }
}
