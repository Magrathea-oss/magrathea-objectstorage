package com.example.magrathea.objectstore.reactive.repository.application;

import com.example.magrathea.objectstore.domain.aggregate.S3Object;
import com.example.magrathea.objectstore.domain.valueobject.LegalHold;
import com.example.magrathea.objectstore.domain.valueobject.ObjectKey;
import com.example.magrathea.objectstore.domain.valueobject.ObjectLockConfiguration;
import com.example.magrathea.objectstore.domain.valueobject.EncryptionConfiguration;
import com.example.magrathea.objectstore.domain.valueobject.RestoreConfiguration;
import org.springframework.core.io.buffer.DataBuffer;
import reactor.core.publisher.Mono;
import reactor.core.publisher.Flux;

public interface S3ObjectCommandRepository {
    Mono<CommandResult<S3Object>> save(S3Object object);

    /** Save object with content bytes streamed as DataBuffer flux. */
    Mono<CommandResult<S3Object>> saveWithContent(S3Object object, Flux<DataBuffer> content,
                                                   String storageClass);

    /** Save object with content, identified by natural ObjectKey. */
    Mono<CommandResult<S3Object>> saveWithContent(ObjectKey objectKey, Flux<DataBuffer> content,
                                                   String storageClass);

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
    Mono<Void> saveEncryption(String bucketName, ObjectKey key, EncryptionConfiguration encryption);

    /** Rename an object — changes its key from oldKey to newKey within the same bucket. */
    Mono<Void> renameObject(String bucketName, ObjectKey oldKey, ObjectKey newKey);
}
