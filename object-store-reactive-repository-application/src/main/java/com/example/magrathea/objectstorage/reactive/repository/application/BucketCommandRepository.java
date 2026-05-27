package com.example.magrathea.objectstore.reactive.repository.application;

import com.example.magrathea.objectstore.domain.aggregate.Bucket;
import com.example.magrathea.objectstore.domain.valueobject.AbacConfiguration;
import com.example.magrathea.objectstore.domain.valueobject.BucketMetadataConfiguration;
import com.example.magrathea.objectstore.domain.valueobject.BucketMetadataTableConfiguration;
import reactor.core.publisher.Mono;

public interface BucketCommandRepository {
    Mono<CommandResult<Bucket>> save(Bucket bucket);
    Mono<CommandResult<Bucket>> delete(Bucket bucket);

    // ── Phase F config writes ──

    /** Save ABAC configuration for a bucket. */
    Mono<Void> saveAbacConfiguration(String bucketName, AbacConfiguration config);

    /** Save metadata configuration for a bucket. */
    Mono<Void> saveMetadataConfiguration(String bucketName, BucketMetadataConfiguration config);

    /** Save metadata table configuration for a bucket. */
    Mono<Void> saveMetadataTableConfiguration(String bucketName, BucketMetadataTableConfiguration config);
}
