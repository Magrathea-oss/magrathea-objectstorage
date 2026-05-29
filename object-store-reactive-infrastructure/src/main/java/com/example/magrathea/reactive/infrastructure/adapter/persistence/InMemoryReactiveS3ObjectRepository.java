package com.example.magrathea.reactive.infrastructure.adapter.persistence;

import com.example.magrathea.objectstore.domain.aggregate.ActiveS3Object;
import com.example.magrathea.objectstore.domain.aggregate.Bucket;
import com.example.magrathea.objectstore.domain.aggregate.CreatingS3Object;
import com.example.magrathea.objectstore.domain.aggregate.S3Object;
import com.example.magrathea.objectstore.domain.valueobject.ChecksumValue;
import com.example.magrathea.objectstore.domain.valueobject.ContentDescriptor;
import com.example.magrathea.objectstore.domain.valueobject.EncryptionConfiguration;
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
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;

@Repository
public class InMemoryReactiveS3ObjectRepository implements S3ObjectCommandRepository, S3ObjectQueryRepository {

    private final Map<S3Object.Id, S3Object> store = new ConcurrentHashMap<>();
    private final Map<String, S3Object> storeByObjectKey = new ConcurrentHashMap<>();
    private final Map<String, byte[]> contentById = new ConcurrentHashMap<>();
    private final AtomicLong versionCounter = new AtomicLong(1);
    private static final DefaultDataBufferFactory DATA_BUFFER_FACTORY = new DefaultDataBufferFactory();

    // Separate maps for Phase F data not supported by sealed hierarchy transitions
    private final Map<String, LegalHold> legalHoldByKey = new ConcurrentHashMap<>();
    private final Map<String, EncryptionConfiguration> encryptionByKey = new ConcurrentHashMap<>();
    private final Map<String, ObjectLockConfiguration> lockConfigByKey = new ConcurrentHashMap<>();
    private final Map<String, ObjectLockConfiguration.RetentionPeriod> retentionByKey = new ConcurrentHashMap<>();
    private final Map<String, RestoreConfiguration> restoreByKey = new ConcurrentHashMap<>();

    // ── Aggregate operations ──

    @Override
    public Mono<CommandResult<S3Object>> save(S3Object object) {
        return Mono.defer(() -> {
            boolean exists = store.containsKey(object.id());
            S3Object clean = object.clearEvents();
            store.put(object.id(), clean);
            storeByObjectKey.put(objectKeyStoreKey(object), clean);
            long version = versionCounter.getAndIncrement();
            CommandResult<S3Object> result = exists
                ? new CommandResult.Updated<>(clean, object.domainEvents(), version)
                : new CommandResult.Created<>(clean, object.domainEvents(), version);
            return Mono.just(result);
        });
    }

    @Override
    public Mono<CommandResult<S3Object>> delete(S3Object object) {
        return Mono.defer(() -> {
            S3Object removed = store.remove(object.id());
            if (removed == null) {
                return Mono.error(new S3ObjectNotFoundException(
                    ObjectKey.of("unknown", object.key().key())));
            }
            storeByObjectKey.remove(objectKeyStoreKey(object));
            long version = versionCounter.getAndIncrement();
            return Mono.just(new CommandResult.Deleted<>(removed, object.domainEvents(), version));
        });
    }

    @Override
    public Mono<S3Object> findById(S3Object.Id objectId) {
        return Mono.defer(() -> Mono.justOrEmpty(store.get(objectId)));
    }

    @Override
    public Mono<S3Object> findByBucketAndKey(Bucket.Id bucketId, ObjectKey key) {
        return Mono.defer(() -> Mono.justOrEmpty(
            store.values().stream()
                .filter(obj -> obj.bucketId().equals(bucketId) && obj.key().equals(key))
                .findFirst()
        ));
    }

    @Override
    public Mono<S3Object> findByBucketAndKey(ObjectKey key) {
        return Mono.defer(() -> Mono.justOrEmpty(
            storeByObjectKey.get(objectKeyStoreKey(key))
        ));
    }

    @Override
    public Flux<S3Object> findByBucket(Bucket.Id bucketId) {
        return Flux.defer(() -> Flux.fromIterable(store.values())
            .filter(obj -> obj.bucketId().equals(bucketId)));
    }

    @Override
    public Mono<CommandResult<S3Object>> saveWithContent(S3Object object, Flux<DataBuffer> content,
                                                          String storageClass) {
        return content.reduceWith(
                () -> new byte[0],
                (acc, buf) -> {
                    var bytes = new byte[acc.length + buf.readableByteCount()];
                    System.arraycopy(acc, 0, bytes, 0, acc.length);
                    buf.read(bytes, acc.length, buf.readableByteCount());
                    return bytes;
                }
            )
            .flatMap(bytes -> {
                var md5Hash = md5Hex(bytes);
                var etag = "\"" + md5Hash + "\"";
                var versionId = UUID.randomUUID().toString();
                var contentId = object.id().value();

                // Build ContentDescriptor with size, md5Hash, contentId, and checksums
                var existingDescriptor = object.contentDescriptor();
                var checksums = existingDescriptor != null ? existingDescriptor.checksums() : Set.<ChecksumValue>of();
                var descriptor = ContentDescriptor.of(bytes.length, md5Hash, contentId, checksums, null);

                // Transition CreatingS3Object → ActiveS3Object via attachContent
                S3Object storedObject;
                if (object instanceof CreatingS3Object creating) {
                    storedObject = creating.attachContent(descriptor).clearEvents();
                } else {
                    storedObject = object.clearEvents();
                }

                store.put(object.id(), storedObject);
                storeByObjectKey.put(objectKeyStoreKey(storedObject), storedObject);
                contentById.put(contentId, bytes);
                long version = versionCounter.getAndIncrement();
                boolean exists = version > 1;
                CommandResult<S3Object> result = exists
                    ? new CommandResult.Updated<>(storedObject, object.domainEvents(), version)
                    : new CommandResult.Created<>(storedObject, object.domainEvents(), version);
                return Mono.just(result);
            });
    }

    @Override
    public Mono<CommandResult<S3Object>> saveWithContent(ObjectKey objectKey, Flux<DataBuffer> content,
                                                          String storageClass) {
        return content.reduceWith(
                () -> new byte[0],
                (acc, buf) -> {
                    var bytes = new byte[acc.length + buf.readableByteCount()];
                    System.arraycopy(acc, 0, bytes, 0, acc.length);
                    buf.read(bytes, acc.length, buf.readableByteCount());
                    return bytes;
                }
            )
            .flatMap(bytes -> {
                var id = S3Object.Id.generate();
                var md5Hash = md5Hex(bytes);
                var etag = "\"" + md5Hash + "\"";
                var versionId = UUID.randomUUID().toString();
                var contentId = id.value();

                // Build ContentDescriptor with size, md5Hash, contentId, and checksums
                var descriptor = ContentDescriptor.of(bytes.length, md5Hash, contentId, Set.<ChecksumValue>of(), null);

                // Create the S3Object domain aggregate directly as ActiveS3Object
                // We use restoreActive since we have all the content information
                var storedObject = S3Object.restoreActive(id, Bucket.Id.of(objectKey.bucket()), objectKey,
                    etag, storageClass, java.time.Instant.now(), null, null, null,
                    Map.<String, String>of(), descriptor, null).clearEvents();

                store.put(storedObject.id(), storedObject);
                storeByObjectKey.put(objectKeyStoreKey(storedObject), storedObject);
                contentById.put(contentId, bytes);
                long version = versionCounter.getAndIncrement();
                CommandResult<S3Object> result = new CommandResult.Created<>(storedObject, storedObject.domainEvents(), version);
                return Mono.just(result);
            });
    }

    @Override
    public Flux<DataBuffer> getContent(S3Object.Id objectId) {
        return Flux.defer(() -> {
            var stored = store.get(objectId);
            if (stored == null || stored.contentDescriptor() == null) {
                return Flux.empty();
            }
            var contentId = stored.contentDescriptor().contentId();
            var bytes = contentById.get(contentId);
            if (bytes == null) {
                return Flux.empty();
            }
            return Flux.just(DATA_BUFFER_FACTORY.wrap(bytes));
        });
    }

    // ── Phase F object config queries ──

    @Override
    public Mono<LegalHold> findLegalHold(String bucketName, ObjectKey key) {
        return Mono.defer(() -> Mono.justOrEmpty(
            legalHoldByKey.get(storeKey(bucketName, key))
        ));
    }

    @Override
    public Mono<ObjectLockConfiguration> findObjectLockConfiguration(String bucketName, ObjectKey key) {
        return Mono.defer(() -> Mono.justOrEmpty(
            lockConfigByKey.get(storeKey(bucketName, key))
        ));
    }

    @Override
    public Mono<ObjectLockConfiguration.RetentionPeriod> findRetention(String bucketName, ObjectKey key) {
        return Mono.defer(() -> Mono.justOrEmpty(
            retentionByKey.get(storeKey(bucketName, key))
        ));
    }

    @Override
    public Flux<DataBuffer> findTorrent(String bucketName, ObjectKey key) {
        return findObjectByBucketNameAndKey(bucketName, key)
            .flatMapMany(obj -> {
                var torrentContent = "Placeholder torrent file for %s/%s%nThis is a mock torrent response for S3 API compatibility."
                    .formatted(bucketName, key.key());
                var dataBuffer = DATA_BUFFER_FACTORY.wrap(torrentContent.getBytes(StandardCharsets.UTF_8));
                return Flux.just(dataBuffer);
            });
    }

    // ── Phase F object config writes ──

    @Override
    public Mono<Void> saveLegalHold(String bucketName, ObjectKey key, LegalHold hold) {
        legalHoldByKey.put(storeKey(bucketName, key), hold);
        return Mono.empty();
    }

    @Override
    public Mono<Void> saveObjectLockConfiguration(String bucketName, ObjectKey key, ObjectLockConfiguration config) {
        lockConfigByKey.put(storeKey(bucketName, key), config);
        return Mono.empty();
    }

    @Override
    public Mono<Void> saveRetention(String bucketName, ObjectKey key, ObjectLockConfiguration.RetentionPeriod retention) {
        retentionByKey.put(storeKey(bucketName, key), retention);
        return Mono.empty();
    }

    @Override
    public Mono<Void> saveRestore(String bucketName, ObjectKey key, RestoreConfiguration config) {
        restoreByKey.put(storeKey(bucketName, key), config);
        return Mono.empty();
    }

    @Override
    public Mono<Void> saveEncryption(String bucketName, ObjectKey key, EncryptionConfiguration encryption) {
        encryptionByKey.put(storeKey(bucketName, key), encryption);
        return Mono.empty();
    }

    @Override
    public Mono<Void> renameObject(String bucketName, ObjectKey oldKey, ObjectKey newKey) {
        return findObjectByBucketNameAndKey(bucketName, oldKey)
            .flatMap(obj -> {
                // Update the stored object's key by creating a new S3Object with updated key
                // Use the sealed hierarchy's restoreActive with the new key
                var newId = S3Object.Id.generate();
                var descriptor = obj.contentDescriptor();
                var encryption = obj.encryption();
                var meta = obj.userMetadata();
                var storageClass = obj.storageClass();
                var etag = obj.etag();
                // Create a new ActiveS3Object with the new key via restoreActive
                var renamed = S3Object.restoreActive(newId, obj.bucketId(), newKey,
                    etag, storageClass, java.time.Instant.now(), null, null, null,
                    meta, descriptor, encryption);
                var clean = renamed.clearEvents();
                store.remove(obj.id());
                store.put(clean.id(), clean);
                storeByObjectKey.remove(objectKeyStoreKey(obj));
                storeByObjectKey.put(objectKeyStoreKey(clean), clean);
                return Mono.<Void>empty();
            })
            .switchIfEmpty(Mono.<Void>error(
                new S3ObjectNotFoundException(ObjectKey.of(bucketName, oldKey.key()))));
    }

    // ── Helper ──

    private static String storeKey(String bucketName, ObjectKey key) {
        return bucketName + "/" + key.key();
    }

    private static String objectKeyStoreKey(S3Object object) {
        return object.key().bucket() + "/" + object.key().key();
    }

    private static String objectKeyStoreKey(ObjectKey key) {
        return key.bucket() + "/" + key.key();
    }

    /**
     * Finds an S3Object by bucket name and key.
     */
    private Mono<S3Object> findObjectByBucketNameAndKey(String bucketName, ObjectKey key) {
        return Mono.defer(() -> Mono.justOrEmpty(
            storeByObjectKey.get(objectKeyStoreKey(key))
        ));
    }

    public void reset() {
        store.clear();
        storeByObjectKey.clear();
        contentById.clear();
        versionCounter.set(1);
        legalHoldByKey.clear();
        encryptionByKey.clear();
        lockConfigByKey.clear();
        retentionByKey.clear();
        restoreByKey.clear();
    }

    private static String md5Hex(byte[] bytes) {
        try {
            var md = MessageDigest.getInstance("MD5");
            md.update(bytes);
            return HexFormat.of().formatHex(md.digest());
        } catch (NoSuchAlgorithmException e) {
            throw new RuntimeException("MD5 not available", e);
        }
    }
}
