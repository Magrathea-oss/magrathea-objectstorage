package com.example.magrathea.storageengine.infrastructure.observability;

import com.example.magrathea.storageengine.application.observability.StorageObservabilityFields;
import com.example.magrathea.storageengine.application.observability.StorageSpan;
import com.example.magrathea.storageengine.application.observability.StorageSpanRecorder;
import com.example.magrathea.storageengine.application.pipeline.StorageEvent;
import com.example.magrathea.storageengine.application.pipeline.StorageEventListener;
import com.example.magrathea.storageengine.application.pipeline.StorageEventType;
import reactor.core.publisher.Mono;

import java.time.Duration;
import java.util.Objects;

/**
 * Records safe span payloads through a small tracing abstraction. A future
 * OpenTelemetry adapter can implement {@link StorageSpanRecorder} without
 * changing pipeline instrumentation.
 */
public final class StorageTracingEventListener implements StorageEventListener {

    private final StorageSpanRecorder recorder;

    public StorageTracingEventListener(StorageSpanRecorder recorder) {
        this.recorder = Objects.requireNonNull(recorder, "recorder must not be null");
    }

    @Override
    public Mono<Void> onEvent(StorageEvent event) {
        if (event.type() == StorageEventType.STAGE_STARTED) {
            return Mono.empty();
        }
        StorageSpan span = new StorageSpan(
                "magrathea.storage." + event.operation().name().toLowerCase() + "." + event.stageName(),
                event.occurredAt(),
                event.duration().orElse(Duration.ZERO),
                StorageObservabilityFields.safeFields(event));
        return recorder.record(span);
    }
}
