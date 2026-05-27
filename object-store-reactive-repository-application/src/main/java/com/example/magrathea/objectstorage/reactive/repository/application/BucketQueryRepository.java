package com.example.magrathea.objectstore.reactive.repository.application;

import com.example.magrathea.objectstore.domain.aggregate.Bucket;
import com.example.magrathea.objectstore.domain.valueobject.AbacConfiguration;
import com.example.magrathea.objectstore.domain.valueobject.BucketMetadataConfiguration;
import com.example.magrathea.objectstore.domain.valueobject.BucketMetadataTableConfiguration;
import reactor.core.publisher.Mono;
import reactor.core.publisher.Flux;

public interface BucketQueryRepository {
    Mono<Bucket> findById(Bucket.Id bucketId);
    Mono<Bucket> findByName(String bucketName);
    Flux<Bucket> findAll();

    // ── Phase F config queries ──

    /** Find ABAC configuration for a bucket by name. */
    Mono<AbacConfiguration> findAbacConfiguration(String bucketName);

    /** Find metadata configuration for a bucket by name. */
    Mono<BucketMetadataConfiguration> findMetadataConfiguration(String bucketName);

    /** Find metadata table configuration for a bucket by name. */
    Mono<BucketMetadataTableConfiguration> findMetadataTableConfiguration(String bucketName);
}
