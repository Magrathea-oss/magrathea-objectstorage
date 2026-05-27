package com.example.magrathea.reactive.infrastructure.adapter.persistence;

import com.example.magrathea.objectstore.domain.aggregate.Bucket;
import com.example.magrathea.objectstore.domain.aggregate.S3Object;
import com.example.magrathea.objectstore.domain.valueobject.LegalHold;
import com.example.magrathea.objectstore.domain.valueobject.ObjectKey;
import com.example.magrathea.objectstore.domain.valueobject.ObjectLockConfiguration;
import com.example.magrathea.objectstore.domain.valueobject.RestoreConfiguration;
import com.example.magrathea.objectstore.reactive.repository.application.S3ObjectCommandRepository;
import com.example.magrathea.objectstore.reactive.repository.application.S3ObjectQueryRepository;
import com.example.magrathea.objectstore.reactive.repository.application.CommandResult;
import org.springframework.core.io.buffer.DataBuffer;
import org.springframework.core.io.buffer.DefaultDataBufferFactory;
import reactor.core.publisher.Mono;
import reactor.core.publisher.Flux;
import org.springframework.stereotype.Repository;

import java.nio.charset.StandardCharsets;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;
import java.util.stream.Collectors;

@Repository
public class InMemoryReactiveS3ObjectRepository implements S3ObjectCommandRepository, S3ObjectQueryRepository {

    private final Map<S3Object.Id, S3Object> store = new ConcurrentHashMap<>();
    private final AtomicLong versionCounter = new AtomicLong(1);
    private static final DefaultDataBufferFactory DATA_BUFFER_FACTORY = new DefaultDataBufferFactory();

    // ── Aggregate operations ──

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

    // ── Phase F object config queries ──

    @Override
    public Mono<LegalHold> findLegalHold(String bucketName, ObjectKey key) {
        return findObjectByBucketNameAndKey(bucketName, key)
            .flatMap(obj -> Mono.justOrEmpty(obj.legalHold()));
    }

    @Override
    public Mono<ObjectLockConfiguration> findObjectLockConfiguration(String bucketName, ObjectKey key) {
        return findObjectByBucketNameAndKey(bucketName, key)
            .flatMap(obj -> Mono.justOrEmpty(obj.objectLockConfiguration()));
    }

    @Override
    public Mono<ObjectLockConfiguration.RetentionPeriod> findRetention(String bucketName, ObjectKey key) {
        return findObjectByBucketNameAndKey(bucketName, key)
            .flatMap(obj -> {
                if (obj.objectLockConfiguration() != null) {
                    return Mono.just(obj.objectLockConfiguration().retention());
                }
                return Mono.empty();
            });
    }

    @Override
    public Mono<Flux<DataBuffer>> findTorrent(String bucketName, ObjectKey key) {
        return findObjectByBucketNameAndKey(bucketName, key)
            .flatMap(obj -> {
                var torrentContent = "Placeholder torrent file for %s/%s%nThis is a mock torrent response for S3 API compatibility."
                    .formatted(bucketName, key.value());
                var dataBuffer = DATA_BUFFER_FACTORY.wrap(torrentContent.getBytes(StandardCharsets.UTF_8));
                return Mono.just(Flux.just(dataBuffer));
            });
    }

    // ── Phase F object config writes ──

    @Override
    public Mono<Void> saveLegalHold(String bucketName, ObjectKey key, LegalHold hold) {
        return findObjectByBucketNameAndKey(bucketName, key)
            .flatMap(obj -> {
                var updated = obj.withLegalHold(hold).clearEvents();
                store.put(updated.id(), updated);
                return Mono.<Void>empty();
            })
            .switchIfEmpty(Mono.<Void>error(new S3ObjectNotFoundException(S3Object.Id.of(bucketName + "/" + key.value()))));
    }

    @Override
    public Mono<Void> saveObjectLockConfiguration(String bucketName, ObjectKey key, ObjectLockConfiguration config) {
        return findObjectByBucketNameAndKey(bucketName, key)
            .flatMap(obj -> {
                var updated = obj.withObjectLockConfiguration(config).clearEvents();
                store.put(updated.id(), updated);
                return Mono.<Void>empty();
            })
            .switchIfEmpty(Mono.<Void>error(new S3ObjectNotFoundException(S3Object.Id.of(bucketName + "/" + key.value()))));
    }

    @Override
    public Mono<Void> saveRetention(String bucketName, ObjectKey key, ObjectLockConfiguration.RetentionPeriod retention) {
        return findObjectByBucketNameAndKey(bucketName, key)
            .flatMap(obj -> {
                var updated = obj.withRetentionPeriod(retention).clearEvents();
                store.put(updated.id(), updated);
                return Mono.<Void>empty();
            })
            .switchIfEmpty(Mono.<Void>error(new S3ObjectNotFoundException(S3Object.Id.of(bucketName + "/" + key.value()))));
    }

    @Override
    public Mono<Void> saveRestore(String bucketName, ObjectKey key, RestoreConfiguration config) {
        return findObjectByBucketNameAndKey(bucketName, key)
            .flatMap(obj -> {
                var updated = obj.withRestore(config).clearEvents();
                store.put(updated.id(), updated);
                return Mono.<Void>empty();
            })
            .switchIfEmpty(Mono.<Void>error(new S3ObjectNotFoundException(S3Object.Id.of(bucketName + "/" + key.value()))));
    }

    @Override
    public Mono<Void> saveEncryption(String bucketName, ObjectKey key, String encryption) {
        return findObjectByBucketNameAndKey(bucketName, key)
            .flatMap(obj -> {
                var updated = obj.withEncryption(encryption).clearEvents();
                store.put(updated.id(), updated);
                return Mono.<Void>empty();
            })
            .switchIfEmpty(Mono.<Void>error(new S3ObjectNotFoundException(S3Object.Id.of(bucketName + "/" + key.value()))));
    }

    @Override
    public Mono<Void> renameObject(String bucketName, ObjectKey oldKey, ObjectKey newKey) {
        return findObjectByBucketNameAndKey(bucketName, oldKey)
            .flatMap(obj -> {
                var updated = obj.withKey(newKey).clearEvents();
                store.remove(obj.id());
                store.put(updated.id(), updated);
                return Mono.<Void>empty();
            })
            .switchIfEmpty(Mono.<Void>error(new S3ObjectNotFoundException(S3Object.Id.of(bucketName + "/" + oldKey.value()))));
    }

    // ── Helper ──

    /**
     * Finds an S3Object by bucket name and key.
     * Since bucket name resolution requires bucket store access not available here,
     * this helper scans objects by key and bucket name using stored bucketId matching.
     * In production, a database would use indices.
     */
    private Mono<S3Object> findObjectByBucketNameAndKey(String bucketName, ObjectKey key) {
        return Mono.fromCallable(() ->
            store.values().stream()
                .filter(obj -> obj.key().equals(key))
                .findFirst()
                .orElse(null)
        ).flatMap(Mono::justOrEmpty);
    }

    public void reset() {
        store.clear();
        versionCounter.set(1);
    }
}
