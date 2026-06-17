package com.example.magrathea.storageengine.application.pipeline;

import reactor.core.publisher.Mono;

public interface StorageStage {
    String name();

    StorageOperation operation();

    Mono<StorageContext> execute(StorageContext context);
}
