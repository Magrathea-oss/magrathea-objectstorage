package com.example.magrathea.storageengine.application.pipeline;

import reactor.core.publisher.Mono;

@FunctionalInterface
public interface StorageEventListener {
    Mono<Void> onEvent(StorageEvent event);
}
