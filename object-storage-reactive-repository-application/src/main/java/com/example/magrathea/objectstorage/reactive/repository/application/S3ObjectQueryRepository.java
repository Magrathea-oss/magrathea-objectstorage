package com.example.magrathea.objectstorage.reactive.repository.application;

import com.example.magrathea.objectstorage.domain.aggregate.Bucket;
import com.example.magrathea.objectstorage.domain.aggregate.S3Object;
import com.example.magrathea.objectstorage.domain.valueobject.ObjectKey;
import reactor.core.publisher.Mono;
import reactor.core.publisher.Flux;

public interface S3ObjectQueryRepository {
    Mono<S3Object> findById(S3Object.Id objectId);
    Mono<S3Object> findByBucketAndKey(Bucket.Id bucketId, ObjectKey key);
    Flux<S3Object> findByBucket(Bucket.Id bucketId);
    Flux<Byte> getContent(S3Object.Id objectId);
}
