package com.example.magrathea.objectstorage.reactive.repository.application;

import com.example.magrathea.objectstorage.domain.aggregate.Bucket;
import reactor.core.publisher.Mono;

public interface BucketCommandRepository {
    Mono<CommandResult<Bucket>> save(Bucket bucket);
    Mono<CommandResult<Bucket>> delete(Bucket bucket);
}
