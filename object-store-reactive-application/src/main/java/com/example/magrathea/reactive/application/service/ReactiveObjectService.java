package com.example.magrathea.reactive.application.service;

import com.example.magrathea.objectstore.domain.aggregate.Bucket;
import com.example.magrathea.objectstore.domain.aggregate.S3Object;
import com.example.magrathea.objectstore.domain.valueobject.LegalHold;
import com.example.magrathea.objectstore.domain.valueobject.ObjectKey;
import com.example.magrathea.objectstore.domain.valueobject.ObjectLockConfiguration;
import com.example.magrathea.objectstore.domain.valueobject.ObjectLambdaResponse;
import com.example.magrathea.objectstore.domain.valueobject.RestoreConfiguration;
import com.example.magrathea.objectstore.domain.valueobject.SelectRequest;
import com.example.magrathea.objectstore.domain.valueobject.SelectResponse;
import com.example.magrathea.objectstore.reactive.repository.application.BucketQueryRepository;
import com.example.magrathea.objectstore.reactive.repository.application.S3ObjectCommandRepository;
import com.example.magrathea.objectstore.reactive.repository.application.S3ObjectQueryRepository;
import com.example.magrathea.objectstore.reactive.repository.application.CommandResult;
import org.springframework.core.io.buffer.DataBuffer;
import reactor.core.publisher.Mono;
import reactor.core.publisher.Flux;
import org.springframework.stereotype.Service;

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

    public Mono<CommandResult<S3Object>> saveObject(S3Object object) {
        return commandRepository.save(object);
    }

    public Mono<CommandResult<S3Object>> deleteObject(S3Object object) {
        return commandRepository.delete(object.withDeleted());
    }

    public Mono<S3Object> findById(S3Object.Id objectId) {
        return queryRepository.findById(objectId);
    }

    public Mono<S3Object> findByBucketAndKey(Bucket.Id bucketId, ObjectKey key) {
        return queryRepository.findByBucketAndKey(bucketId, key);
    }

    public Flux<S3Object> findByBucket(Bucket.Id bucketId) {
        return queryRepository.findByBucket(bucketId);
    }

    public Flux<Byte> getContent(S3Object.Id objectId) {
        return queryRepository.getContent(objectId);
    }

    // ── Phase F: Legal hold ──

    /**
     * Get legal hold status for an object.
     */
    public Mono<LegalHold> getObjectLegalHold(String bucketName, ObjectKey key) {
        return queryRepository.findLegalHold(bucketName, key);
    }

    /**
     * Set legal hold status for an object.
     */
    public Mono<Void> putObjectLegalHold(String bucketName, ObjectKey key, LegalHold hold) {
        return commandRepository.saveLegalHold(bucketName, key, hold);
    }

    // ── Phase F: Object lock configuration ──

    /**
     * Get object lock configuration for an object.
     */
    public Mono<ObjectLockConfiguration> getObjectLockConfiguration(String bucketName, ObjectKey key) {
        return queryRepository.findObjectLockConfiguration(bucketName, key);
    }

    /**
     * Set object lock configuration for an object.
     */
    public Mono<Void> putObjectLockConfiguration(String bucketName, ObjectKey key, ObjectLockConfiguration config) {
        return commandRepository.saveObjectLockConfiguration(bucketName, key, config);
    }

    // ── Phase F: Retention ──

    /**
     * Get retention period for an object.
     */
    public Mono<ObjectLockConfiguration.RetentionPeriod> getObjectRetention(String bucketName, ObjectKey key) {
        return queryRepository.findRetention(bucketName, key);
    }

    /**
     * Set retention period for an object.
     */
    public Mono<Void> putObjectRetention(String bucketName, ObjectKey key, ObjectLockConfiguration.RetentionPeriod retention) {
        return commandRepository.saveRetention(bucketName, key, retention);
    }

    // ── Phase F: Restore ──

    /**
     * Restore an archived object.
     */
    public Mono<Void> restoreObject(String bucketName, ObjectKey key, RestoreConfiguration config) {
        return commandRepository.saveRestore(bucketName, key, config);
    }

    // ── Phase F: Encryption update ──

    /**
     * Update server-side encryption for an object.
     */
    public Mono<Void> updateObjectEncryption(String bucketName, ObjectKey key, String encryption) {
        return commandRepository.saveEncryption(bucketName, key, encryption);
    }

    // ── Phase F: Rename ──

    /**
     * Rename an object (change its key).
     */
    public Mono<Void> renameObject(String bucketName, ObjectKey sourceKey, ObjectKey destinationKey) {
        return commandRepository.renameObject(bucketName, sourceKey, destinationKey);
    }

    // ── Phase F: Torrent ──

    /**
     * Get torrent file for an object.
     */
    public Mono<Flux<DataBuffer>> getObjectTorrent(String bucketName, ObjectKey key) {
        return queryRepository.findTorrent(bucketName, key);
    }

    // ── Phase F: Select content ──

    /**
     * Select object content using SQL-like query.
     * Returns a SelectResponse with metadata about the query result.
     */
    public Mono<SelectResponse> selectObjectContent(String bucketName, ObjectKey key, SelectRequest request) {
        // Resolve bucket name to Bucket.Id, then look up the object
        return bucketQueryRepository.findByName(bucketName)
            .flatMap(bucket -> queryRepository.findByBucketAndKey(bucket.id(), key))
            .flatMap(obj -> Mono.just(SelectResponse.of(obj.size(), "OK", "placeholder-request-id")))
            .switchIfEmpty(Mono.just(SelectResponse.of(0L, "NoSuchKey", "placeholder-request-id")));
    }

    // ── Phase F: Object Lambda response ──

    /**
     * Write the response from an Object Lambda function.
     */
    public Mono<ObjectLambdaResponse> writeGetObjectResponse(ObjectLambdaResponse response) {
        // Domain-level validation and orchestration.
        // The actual write is handled by infrastructure.
        return Mono.just(response);
    }
}
