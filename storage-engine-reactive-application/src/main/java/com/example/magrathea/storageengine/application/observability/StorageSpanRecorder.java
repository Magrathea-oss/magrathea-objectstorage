package com.example.magrathea.storageengine.application.observability;

import reactor.core.publisher.Mono;

@FunctionalInterface
public interface StorageSpanRecorder {
    Mono<Void> record(StorageSpan span);

    static StorageSpanRecorder noop() {
        return span -> Mono.empty();
    }
}
