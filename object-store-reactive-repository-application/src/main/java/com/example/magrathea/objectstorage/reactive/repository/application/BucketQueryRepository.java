package com.example.magrathea.objectstore.reactive.repository.application;

import com.example.magrathea.objectstore.domain.aggregate.Bucket;
import reactor.core.publisher.Mono;
import reactor.core.publisher.Flux;

public interface BucketQueryRepository {
    Mono<Bucket> findById(Bucket.Id bucketId);
    Mono<Bucket> findByName(String bucketName);
    Flux<Bucket> findAll();
}
