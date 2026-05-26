package com.example.magrathea.objectstore.reactive.repository.application;

import com.example.magrathea.objectstore.domain.aggregate.S3Object;
import reactor.core.publisher.Mono;

public interface S3ObjectCommandRepository {
    Mono<CommandResult<S3Object>> save(S3Object object);
    Mono<CommandResult<S3Object>> delete(S3Object object);
}
