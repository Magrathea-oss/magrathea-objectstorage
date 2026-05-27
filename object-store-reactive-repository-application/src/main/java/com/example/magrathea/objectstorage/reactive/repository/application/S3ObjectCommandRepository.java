package com.example.magrathea.objectstore.reactive.repository.application;

import com.example.magrathea.objectstore.domain.aggregate.S3Object;
import com.example.magrathea.objectstore.domain.valueobject.LegalHold;
import com.example.magrathea.objectstore.domain.valueobject.ObjectKey;
import com.example.magrathea.objectstore.domain.valueobject.ObjectLockConfiguration;
import com.example.magrathea.objectstore.domain.valueobject.RestoreConfiguration;
import reactor.core.publisher.Mono;

public interface S3ObjectCommandRepository {
    Mono<CommandResult<S3Object>> save(S3Object object);
    Mono<CommandResult<S3Object>> delete(S3Object object);

    // ── Phase F object config writes ──

    /** Save legal hold for an object by bucket name and key. */
    Mono<Void> saveLegalHold(String bucketName, ObjectKey key, LegalHold hold);

    /** Save object lock configuration for an object by bucket name and key. */
    Mono<Void> saveObjectLockConfiguration(String bucketName, ObjectKey key, ObjectLockConfiguration config);

    /** Save retention period for an object by bucket name and key. */
    Mono<Void> saveRetention(String bucketName, ObjectKey key, ObjectLockConfiguration.RetentionPeriod retention);

    /** Save restore configuration for an object by bucket name and key. */
    Mono<Void> saveRestore(String bucketName, ObjectKey key, RestoreConfiguration config);

    /** Save encryption for an object by bucket name and key. */
    Mono<Void> saveEncryption(String bucketName, ObjectKey key, String encryption);

    /** Rename an object — changes its key from oldKey to newKey within the same bucket. */
    Mono<Void> renameObject(String bucketName, ObjectKey oldKey, ObjectKey newKey);
}
