package com.example.magrathea.reactive.infrastructure.adapter.persistence;

import com.example.magrathea.objectstore.domain.aggregate.ActiveS3Object;
import com.example.magrathea.objectstore.domain.aggregate.Bucket;
import com.example.magrathea.objectstore.domain.aggregate.S3Object;
import com.example.magrathea.objectstore.domain.valueobject.ChecksumValue;
import com.example.magrathea.objectstore.domain.valueobject.EncryptionConfiguration;
import com.example.magrathea.objectstore.domain.valueobject.LegalHold;
import com.example.magrathea.objectstore.domain.valueobject.ObjectChecksum;
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
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Repository;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;

/** In-memory S3 object repository active only in the {@code single-node} (default) profile. */
@Repository
@Profile({"single-node", "default"})
public class InMemoryReactiveS3ObjectRepository implements S3ObjectCommandRepository, S3ObjectQueryRepository {

    private final Map<String, S3Object> storeByObjectKey = new ConcurrentHashMap<>();
    private final Map<String, byte[]> contentByKey = new ConcurrentHashMap<>();
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
            var storeKey = objectKeyStoreKey(object);
            boolean exists = storeByObjectKey.containsKey(storeKey);
            S3Object clean = object.clearEvents();
            storeByObjectKey.put(storeKey, clean);
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
            var storeKey = objectKeyStoreKey(object);
            S3Object removed = storeByObjectKey.remove(storeKey);
            if (removed == null) {
                return Mono.error(new com.example.magrathea.objectstore.reactive.repository.application.S3ObjectNotFoundException(
                    ObjectKey.of("unknown", object.key().key())));
            }
            long version = versionCounter.getAndIncrement();
            return Mono.just(new CommandResult.Deleted<>(removed, object.domainEvents(), version));
        });
    }

    @Override
    public Mono<S3Object> findByBucketAndKey(Bucket.Id bucketId, ObjectKey key) {
        return Mono.defer(() -> Mono.justOrEmpty(
            storeByObjectKey.get(objectKeyStoreKey(key))
        ));
    }

    @Override
    public Mono<S3Object> findByBucketAndKey(ObjectKey key) {
        return Mono.defer(() -> Mono.justOrEmpty(
            storeByObjectKey.get(objectKeyStoreKey(key))
        ));
    }

    @Override
    public Flux<S3Object> findByBucket(String bucketName) {
        return Flux.defer(() -> Flux.fromIterable(storeByObjectKey.values())
            .filter(obj -> obj.key().bucket().equals(bucketName)));
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
                var md5Hash = md5Base64(bytes);

                // Build ObjectChecksum with MD5 checksum
                var existingChecksum = object.checksum();
                Set<ChecksumValue> allChecksums = new java.util.HashSet<>();
                if (existingChecksum != null) {
                    allChecksums.addAll(existingChecksum.checksums());
                }
                // Use existing MD5 checksum if present (from Content-MD5 header), otherwise compute
                boolean hasExistingMd5 = existingChecksum != null && existingChecksum.checksums().stream()
                    .anyMatch(cv -> cv.algorithm() == com.example.magrathea.objectstore.domain.valueobject.ChecksumAlgorithm.MD5);
                if (!hasExistingMd5) {
                    var md5Checksum = new ChecksumValue(
                        com.example.magrathea.objectstore.domain.valueobject.ChecksumAlgorithm.MD5, md5Hash);
                    allChecksums.add(md5Checksum);
                }
                var sdkAlgorithm = existingChecksum != null ? existingChecksum.sdkAlgorithm() : null;
                var combinedChecksum = ObjectChecksum.of(allChecksums, sdkAlgorithm);

                // Store the content keyed by ObjectKey string
                var storeKey = objectKeyStoreKey(object);
                contentByKey.put(storeKey, bytes);

                // Compute hex MD5 ETag (S3-compatible format) only when no Content-MD5 was provided.
                // When Content-MD5 is provided by the client, the existing checksum is preserved
                // and echoed back as the ETag (backward compat).
                String hexEtag = hasExistingMd5 ? null : hexMd5ETag(bytes);

                // Create a clean ActiveS3Object with the computed checksum
                // If the object is already an ActiveS3Object, use its properties
                // Otherwise, create a fresh ActiveS3Object from the object's data
                S3Object storedObject;
                if (object instanceof ActiveS3Object active) {
                    // Re-create with updated checksum and hex ETag
                    storedObject = ActiveS3Object.restoreActive(
                        active.key(), active.storageClass(), active.userMetadata(),
                        active.encryption(), combinedChecksum, bytes.length,
                        active.createdAt(), active.domainEvents()).clearEvents();
                    if (hexEtag != null) {
                        storedObject = ((ActiveS3Object) storedObject).withEtag(hexEtag);
                    }
                } else {
                    storedObject = ActiveS3Object.restoreActive(
                        object.key(), object.storageClass(), object.userMetadata(),
                        object.encryption(), combinedChecksum, bytes.length,
                        object.createdAt(), object.domainEvents()).clearEvents();
                    if (hexEtag != null) {
                        storedObject = ((ActiveS3Object) storedObject).withEtag(hexEtag);
                    }
                }

                storeByObjectKey.put(storeKey, storedObject);
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
                var md5Hash = md5Base64(bytes);
                var md5Checksum = new ChecksumValue(
                    com.example.magrathea.objectstore.domain.valueobject.ChecksumAlgorithm.MD5, md5Hash);
                var combinedChecksum = ObjectChecksum.of(Set.of(md5Checksum), null);
                // Note: for the ObjectKey-based saveWithContent, there's no pre-existing checksum,
                // so always compute from content.

                // Create the S3Object domain aggregate directly as ActiveS3Object
                var storedObject = ActiveS3Object.create(objectKey, storageClass,
                    Map.<String, String>of(), null, combinedChecksum, bytes.length);

                var storeKey = objectKeyStoreKey(storedObject);
                storeByObjectKey.put(storeKey, storedObject);
                contentByKey.put(storeKey, bytes);
                long version = versionCounter.getAndIncrement();
                CommandResult<S3Object> result = new CommandResult.Created<>(storedObject, storedObject.domainEvents(), version);
                return Mono.just(result);
            });
    }

    @Override
    public Flux<DataBuffer> getContent(ObjectKey key) {
        return Flux.defer(() -> {
            var bytes = contentByKey.get(objectKeyStoreKey(key));
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
                // Update the stored object's key by creating a new ActiveS3Object with the new key
                var storedObject = ActiveS3Object.restoreActive(
                    newKey, obj.storageClass(), obj.userMetadata(),
                    obj.encryption(), obj.checksum(), obj.size(),
                    obj.createdAt(), obj.domainEvents());
                var clean = storedObject.clearEvents();
                var oldStoreKey = objectKeyStoreKey(oldKey);
                var newStoreKey = objectKeyStoreKey(newKey);
                storeByObjectKey.remove(oldStoreKey);
                storeByObjectKey.put(newStoreKey, clean);
                // Move content if present
                var content = contentByKey.remove(oldStoreKey);
                if (content != null) {
                    contentByKey.put(newStoreKey, content);
                }
                return Mono.<Void>empty();
            })
            .switchIfEmpty(Mono.<Void>error(
                new com.example.magrathea.objectstore.reactive.repository.application.S3ObjectNotFoundException(ObjectKey.of(bucketName, oldKey.key()))));
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
        storeByObjectKey.clear();
        contentByKey.clear();
        versionCounter.set(1);
        legalHoldByKey.clear();
        encryptionByKey.clear();
        lockConfigByKey.clear();
        retentionByKey.clear();
        restoreByKey.clear();
    }

    private static String md5Base64(byte[] bytes) {
        try {
            var md = MessageDigest.getInstance("MD5");
            md.update(bytes);
            var digest = md.digest();
            return java.util.Base64.getEncoder().encodeToString(digest);
        } catch (NoSuchAlgorithmException e) {
            throw new RuntimeException("MD5 not available", e);
        }
    }

    /**
     * Computes the quoted lowercase hex MD5 ETag of a byte array, as used by S3.
     * Format: {@code "xxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxx"} (32 hex chars enclosed in double quotes).
     */
    private static String hexMd5ETag(byte[] bytes) {
        try {
            var md = MessageDigest.getInstance("MD5");
            md.update(bytes);
            var digest = md.digest();
            var sb = new StringBuilder(digest.length * 2);
            for (byte b : digest) {
                sb.append(String.format("%02x", b & 0xff));
            }
            return "\"" + sb + "\"";
        } catch (NoSuchAlgorithmException e) {
            throw new RuntimeException("MD5 not available", e);
        }
    }
}
