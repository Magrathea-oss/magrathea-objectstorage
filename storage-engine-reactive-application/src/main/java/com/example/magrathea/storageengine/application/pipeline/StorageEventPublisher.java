package com.example.magrathea.storageengine.application.pipeline;

import reactor.core.publisher.Mono;

@FunctionalInterface
public interface StorageEventPublisher {
    Mono<Void> publish(StorageEvent event);

    static StorageEventPublisher noop() {
        return event -> Mono.empty();
    }
}
