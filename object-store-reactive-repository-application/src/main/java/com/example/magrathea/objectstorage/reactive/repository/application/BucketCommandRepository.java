package com.example.magrathea.objectstore.reactive.repository.application;

import com.example.magrathea.objectstore.domain.aggregate.Bucket;
import reactor.core.publisher.Mono;

public interface BucketCommandRepository {
    Mono<CommandResult<Bucket>> save(Bucket bucket);
    Mono<CommandResult<Bucket>> delete(Bucket bucket);
}
