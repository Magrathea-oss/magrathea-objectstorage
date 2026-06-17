package com.example.magrathea.storageengine.infrastructure.observability;

import com.example.magrathea.storageengine.application.observability.StorageSpan;
import com.example.magrathea.storageengine.application.observability.StorageSpanRecorder;
import reactor.core.publisher.Mono;

import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;

public final class InMemoryStorageSpanRecorder implements StorageSpanRecorder {

    private final List<StorageSpan> spans = new CopyOnWriteArrayList<>();

    @Override
    public Mono<Void> record(StorageSpan span) {
        spans.add(span);
        return Mono.empty();
    }

    public List<StorageSpan> spans() {
        return List.copyOf(spans);
    }
}
