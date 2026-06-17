package com.example.magrathea.storageengine.application.pipeline;

import reactor.core.publisher.Mono;

@FunctionalInterface
public interface StorageCleanupHandle {
    Mono<Void> cleanup();

    default String name() {
        return "cleanup";
    }

    static StorageCleanupHandle named(String name, Mono<Void> cleanup) {
        return new StorageCleanupHandle() {
            @Override
            public Mono<Void> cleanup() {
                return cleanup;
            }

            @Override
            public String name() {
                return name;
            }
        };
    }
}
