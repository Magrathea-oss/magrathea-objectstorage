package com.example.magrathea.storageengine.application.pipeline;

import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.util.List;
import java.util.Objects;

public final class CompositeStorageEventPublisher implements StorageEventPublisher {

    private final List<StorageEventListener> listeners;

    public CompositeStorageEventPublisher(List<StorageEventListener> listeners) {
        this.listeners = List.copyOf(Objects.requireNonNull(listeners, "listeners must not be null"));
    }

    @Override
    public Mono<Void> publish(StorageEvent event) {
        Objects.requireNonNull(event, "event must not be null");
        return Flux.fromIterable(listeners)
                .concatMap(listener -> listener.onEvent(event))
                .then();
    }
}
