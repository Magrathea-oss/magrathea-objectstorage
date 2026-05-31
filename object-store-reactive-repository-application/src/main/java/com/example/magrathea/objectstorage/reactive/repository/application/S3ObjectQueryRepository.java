package com.example.magrathea.objectstore.reactive.repository.application;

import com.example.magrathea.objectstore.domain.aggregate.Bucket;
import com.example.magrathea.objectstore.domain.aggregate.S3Object;
import com.example.magrathea.objectstore.domain.valueobject.LegalHold;
import com.example.magrathea.objectstore.domain.valueobject.ObjectKey;
import com.example.magrathea.objectstore.domain.valueobject.ObjectLockConfiguration;
import org.springframework.core.io.buffer.DataBuffer;
import reactor.core.publisher.Mono;
import reactor.core.publisher.Flux;

public interface S3ObjectQueryRepository {
    Mono<S3Object> findByBucketAndKey(Bucket.Id bucketId, ObjectKey key);
    Mono<S3Object> findByBucketAndKey(ObjectKey key);
    Flux<S3Object> findByBucket(String bucketName);
    Flux<DataBuffer> getContent(ObjectKey key);

    // ── Phase F object config queries ──

    /** Find legal hold for an object by bucket name and key. */
    Mono<LegalHold> findLegalHold(String bucketName, ObjectKey key);

    /** Find object lock configuration for an object by bucket name and key. */
    Mono<ObjectLockConfiguration> findObjectLockConfiguration(String bucketName, ObjectKey key);

    /** Find retention period for an object by bucket name and key. */
    Mono<ObjectLockConfiguration.RetentionPeriod> findRetention(String bucketName, ObjectKey key);

    /** Find torrent content for an object by bucket name and key. */
    Flux<DataBuffer> findTorrent(String bucketName, ObjectKey key);
}
