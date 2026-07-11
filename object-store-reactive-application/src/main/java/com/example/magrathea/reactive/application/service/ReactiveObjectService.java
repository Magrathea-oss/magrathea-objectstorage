package com.example.magrathea.reactive.application.service;

import com.example.magrathea.objectstore.domain.aggregate.ActiveS3Object;
import com.example.magrathea.objectstore.domain.aggregate.Bucket;
import com.example.magrathea.objectstore.domain.aggregate.LockedS3Object;
import com.example.magrathea.objectstore.domain.aggregate.S3Object;
import com.example.magrathea.objectstore.domain.valueobject.EncryptionConfiguration;
import com.example.magrathea.objectstore.domain.valueobject.LegalHold;
import com.example.magrathea.objectstore.domain.valueobject.ObjectChecksum;
import com.example.magrathea.objectstore.domain.valueobject.ObjectKey;
import com.example.magrathea.objectstore.domain.valueobject.ObjectLockConfiguration;
import com.example.magrathea.objectstore.domain.valueobject.ObjectLambdaResponse;
import com.example.magrathea.objectstore.domain.valueobject.RestoreConfiguration;
import com.example.magrathea.objectstore.domain.valueobject.SelectRequest;
import com.example.magrathea.objectstore.domain.valueobject.SelectResponse;
import com.example.magrathea.objectstore.domain.valueobject.UserMetadata;
import com.example.magrathea.objectstore.reactive.repository.application.BucketQueryRepository;
import com.example.magrathea.objectstore.reactive.repository.application.S3ObjectCommandRepository;
import com.example.magrathea.objectstore.reactive.repository.application.S3ObjectQueryRepository;
import com.example.magrathea.objectstore.reactive.repository.application.CommandResult;
import com.example.magrathea.objectstore.reactive.repository.application.BucketNotFoundException;
import org.springframework.core.io.buffer.DataBuffer;
import reactor.core.publisher.Mono;
import reactor.core.publisher.Flux;
import org.springframework.stereotype.Service;

import java.util.Map;

@Service
public class ReactiveObjectService {

    private final S3ObjectCommandRepository commandRepository;
    private final S3ObjectQueryRepository queryRepository;
    private final BucketQueryRepository bucketQueryRepository;

    public ReactiveObjectService(S3ObjectCommandRepository commandRepository,
                                  S3ObjectQueryRepository queryRepository,
                                  BucketQueryRepository bucketQueryRepository) {
        this.commandRepository = commandRepository;
        this.queryRepository = queryRepository;
        this.bucketQueryRepository = bucketQueryRepository;
    }

    // ── Core object operations ──

    /**
     * Save an S3Object (metadata-only, no content).
     */
    public Mono<CommandResult<S3Object>> saveObject(S3Object object) {
        return commandRepository.save(object);
    }

    /**
     * Save an S3Object with streaming content.
     * Creates an ActiveS3Object via the domain factory, then delegates to the repository
     * which computes checksums and stores content.
     */
    public Mono<CommandResult<S3Object>> saveObjectWithContent(
            ObjectKey key,
            String storageClass,
            ObjectChecksum checksum,
            EncryptionConfiguration encryption,
            String contentType,
            long contentLength,
            Map<String, String> userMetadata,
            Flux<DataBuffer> content) {
        var active = ActiveS3Object.create(key, storageClass,
            userMetadata, encryption, checksum, contentLength);
        return commandRepository.saveWithContent(active, content, storageClass);
    }

    /**
     * Save an S3Object with streaming content (accepts pre-built S3Object).
     */
    public Mono<CommandResult<S3Object>> saveObjectWithContent(S3Object object, Flux<DataBuffer> content,
                                                                 String storageClass) {
        return commandRepository.saveWithContent(object, content, storageClass);
    }

    /**
     * Save an S3Object with streaming content, using the new domain container types
     * ({@link ObjectChecksum}, {@link UserMetadata}).
     * <p>
     * The {@code ObjectKey} carries both bucket name and object key. The service looks
     * up the bucket, creates the domain aggregate internally, and delegates to the
     * repository for persistence.
     * </p>
     * <p>
     * <strong>Note:</strong> The {@code checksum} parameter is accepted but not yet
     * validated against the computed checksum. The repository computes checksums
     * internally.
     * </p>
     */
    public Mono<CommandResult<S3Object>> saveObjectWithContent(
            ObjectKey objectKey,
            String storageClass,
            ObjectChecksum checksum,
            EncryptionConfiguration encryption,
            String contentType,
            long contentLength,
            UserMetadata userMetadata,
            Flux<DataBuffer> content) {
        var userMetadataMap = userMetadata != null ? userMetadata.entries() : Map.<String, String>of();
        var active = ActiveS3Object.create(objectKey, storageClass,
            userMetadataMap, encryption, checksum, contentLength);
        return commandRepository.saveWithContent(active, content, storageClass);
    }

    /**
     * Save an S3Object with streaming content (legacy signature for backward compat).
     */
    public Mono<CommandResult<S3Object>> saveObjectWithContent(S3Object object, Flux<DataBuffer> content) {
        return commandRepository.saveWithContent(object, content, null);
    }

    /**
     * Delete an S3Object via state machine transition.
     */
    public Mono<CommandResult<S3Object>> deleteObject(S3Object object) {
        var deleted = switch (object) {
            case ActiveS3Object a -> a.delete();
            case LockedS3Object l -> l.removeLegalHold().delete();
            default -> object; // Archived or Deleted
        };
        return commandRepository.delete(deleted);
    }

    /**
     * Find an S3Object by its natural key (ObjectKey carries both bucket name and key).
     */
    public Mono<S3Object> findByBucketAndKey(ObjectKey key) {
        return queryRepository.findByBucketAndKey(key);
    }

    /**
     * Find an S3Object by bucket ID and ObjectKey.
     */
    public Mono<S3Object> findByBucketAndKey(Bucket.Id bucketId, ObjectKey key) {
        return queryRepository.findByBucketAndKey(bucketId, key);
    }

    public Flux<S3Object> findByBucket(String bucketName) {
        return queryRepository.findByBucket(bucketName);
    }

    /**
     * Get content for an object by its ObjectKey.
     */
    public Flux<DataBuffer> getContent(ObjectKey key) {
        return queryRepository.getContent(key);
    }

    /**
     * Check if a bucket exists by name. Returns error Mono if not found.
     */
    public Mono<Bucket> verifyBucketExists(String bucketName) {
        return bucketQueryRepository.findByName(bucketName)
            .switchIfEmpty(Mono.error(new BucketNotFoundException(bucketName)));
    }

    // ── Phase F: Legal hold ──

    public Mono<LegalHold> getObjectLegalHold(String bucketName, ObjectKey key) {
        return queryRepository.findLegalHold(bucketName, key);
    }

    public Mono<Void> putObjectLegalHold(String bucketName, ObjectKey key, LegalHold hold) {
        return commandRepository.saveLegalHold(bucketName, key, hold);
    }

    // ── Phase F: Object lock configuration ──

    public Mono<ObjectLockConfiguration> getObjectLockConfiguration(String bucketName, ObjectKey key) {
        return queryRepository.findObjectLockConfiguration(bucketName, key);
    }

    public Mono<Void> putObjectLockConfiguration(String bucketName, ObjectKey key, ObjectLockConfiguration config) {
        return commandRepository.saveObjectLockConfiguration(bucketName, key, config);
    }

    // ── Phase F: Retention ──

    public Mono<ObjectLockConfiguration.RetentionPeriod> getObjectRetention(String bucketName, ObjectKey key) {
        return queryRepository.findRetention(bucketName, key);
    }

    public Mono<Void> putObjectRetention(String bucketName, ObjectKey key, ObjectLockConfiguration.RetentionPeriod retention) {
        return commandRepository.saveRetention(bucketName, key, retention);
    }

    // ── Phase F: Restore ──

    public Mono<RestoreConfiguration> getObjectRestore(String bucketName, ObjectKey key) {
        return queryRepository.findRestore(bucketName, key);
    }

    public Mono<Void> restoreObject(String bucketName, ObjectKey key, RestoreConfiguration config) {
        return commandRepository.saveRestore(bucketName, key, config);
    }

    // ── Phase F: Encryption update ──

    public Mono<EncryptionConfiguration> getObjectEncryption(String bucketName, ObjectKey key) {
        return queryRepository.findEncryption(bucketName, key);
    }

    public Mono<Void> updateObjectEncryption(String bucketName, ObjectKey key, EncryptionConfiguration encryption) {
        return commandRepository.saveEncryption(bucketName, key, encryption);
    }

    // ── Phase F: Rename ──

    public Mono<Void> renameObject(String bucketName, ObjectKey sourceKey, ObjectKey destinationKey) {
        return commandRepository.renameObject(bucketName, sourceKey, destinationKey);
    }

    // ── Phase F: Torrent ──

    public Flux<DataBuffer> getObjectTorrent(String bucketName, ObjectKey key) {
        return queryRepository.findTorrent(bucketName, key);
    }

    // ── Phase F: Select content ──

    public Mono<SelectResponse> selectObjectContent(String bucketName, ObjectKey key, SelectRequest request) {
        return bucketQueryRepository.findByName(bucketName)
            .flatMap(bucket -> queryRepository.findByBucketAndKey(key))
            .map(obj -> SelectResponse.of(obj.size(), "OK", "placeholder-request-id"))
            .switchIfEmpty(Mono.just(SelectResponse.of(0L, "NoSuchKey", "placeholder-request-id")));
    }

    // ── Phase F: Object Lambda response ──

    public Mono<ObjectLambdaResponse> writeGetObjectResponse(ObjectLambdaResponse response) {
        return Mono.just(response);
    }

    // ── ObjectKey-based convenience methods ──

    /**
     * Simple record holding both an S3Object and its streaming content.
     */
    public record ObjectWithContent(S3Object object, Flux<DataBuffer> content) {}

    /**
     * Update object tags on an existing object identified by its natural {@link ObjectKey}.
     * Gets the current object, applies the new tags, and persists.
     */
    public Mono<CommandResult<S3Object>> updateObjectTags(ObjectKey objectKey, Map<String, String> tags) {
        return getObject(objectKey)
            .flatMap(obj -> {
                if (obj instanceof ActiveS3Object active) {
                    return commandRepository.save(active.withObjectTags(tags));
                }
                return Mono.error(new IllegalStateException(
                    "Object is not in active state: " + objectKey));
            });
    }

    /**
     * Delete an object identified by its natural {@link ObjectKey}.
     * Looks up the bucket internally.
     * <p>If the bucket is not found, returns {@code Mono.error(BucketNotFoundException)}.
     * If the object is not found, returns {@code Mono.empty()}.</p>
     */
    public Mono<Void> deleteObject(ObjectKey objectKey) {
        return bucketQueryRepository.findByName(objectKey.bucket())
            .switchIfEmpty(Mono.error(new BucketNotFoundException(objectKey.bucket())))
            .flatMap(bucket -> queryRepository.findByBucketAndKey(bucket.id(), objectKey))
            .flatMap(obj -> commandRepository.delete(obj).then());
    }

    /**
     * Get an object identified by its natural {@link ObjectKey}.
     * Looks up the bucket internally.
     * <p>If the bucket is not found, returns {@code Mono.error(BucketNotFoundException)}.
     * If the object is not found, returns {@code Mono.empty()}.</p>
     */
    public Mono<S3Object> getObject(ObjectKey objectKey) {
        return bucketQueryRepository.findByName(objectKey.bucket())
            .switchIfEmpty(Mono.error(new BucketNotFoundException(objectKey.bucket())))
            .flatMap(bucket -> queryRepository.findByBucketAndKey(bucket.id(), objectKey));
    }

    /**
     * Get an object with its streaming content, identified by its natural {@link ObjectKey}.
     * Looks up the bucket internally.
     * <p>If the bucket is not found, returns {@code Mono.error(BucketNotFoundException)}.
     * If the object is not found, returns {@code Mono.empty()}.</p>
     */
    public Mono<ObjectWithContent> getObjectWithContent(ObjectKey objectKey) {
        return bucketQueryRepository.findByName(objectKey.bucket())
            .switchIfEmpty(Mono.error(new BucketNotFoundException(objectKey.bucket())))
            .flatMap(bucket -> queryRepository.findByBucketAndKey(bucket.id(), objectKey))
            .flatMap(obj -> Mono.just(new ObjectWithContent(obj, queryRepository.getContent(obj.key()))));
    }

    /**
     * Validates all persisted content before exposing a fresh streaming read.
     *
     * <p>This application boundary preserves the deterministic S3 XML integrity-error
     * contract of REQ-FS-003 and REQ-FS-004: a checksum failure must be discovered before
     * the HTTP response is committed. Validation retains no object bytes, and the content
     * stream exposed by the use case remains the repository's bounded response stream.
     */
    public Mono<ObjectWithContent> getIntegrityVerifiedObjectWithContent(ObjectKey objectKey) {
        return getObjectWithContent(objectKey)
            .flatMap(objectWithContent -> queryRepository
                .validateContentIntegrity(objectWithContent.object().key())
                .thenReturn(objectWithContent));
    }

    /**
     * Get torrent data for an object identified by its natural {@link ObjectKey}.
     * Looks up the bucket internally.
     * <p>If the bucket is not found, returns {@code Mono.error(BucketNotFoundException)}.
     * If the object is not found, returns {@code Mono.empty()}.</p>
     */
    public Mono<Flux<DataBuffer>> getObjectTorrent(ObjectKey objectKey) {
        return bucketQueryRepository.findByName(objectKey.bucket())
            .switchIfEmpty(Mono.error(new BucketNotFoundException(objectKey.bucket())))
            .flatMap(bucket -> queryRepository.findByBucketAndKey(bucket.id(), objectKey))
            .flatMap(obj -> Mono.just(queryRepository.findTorrent(objectKey.bucket(), objectKey)));
    }

    /**
     * Check whether an object exists by its natural {@link ObjectKey}.
     * Looks up the bucket internally.
     * <p>If the bucket is not found, returns {@code Mono.error(BucketNotFoundException)}.
     * If the object is not found, returns {@code Mono.just(false)}.</p>
     */
    public Mono<Boolean> objectExists(ObjectKey objectKey) {
        return bucketQueryRepository.findByName(objectKey.bucket())
            .switchIfEmpty(Mono.error(new BucketNotFoundException(objectKey.bucket())))
            .flatMap(bucket -> queryRepository.findByBucketAndKey(bucket.id(), objectKey))
            .map(obj -> true)
            .switchIfEmpty(Mono.just(false));
    }
}
