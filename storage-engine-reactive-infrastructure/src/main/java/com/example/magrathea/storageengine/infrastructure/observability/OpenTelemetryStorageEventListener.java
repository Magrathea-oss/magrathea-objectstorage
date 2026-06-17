package com.example.magrathea.storageengine.infrastructure.observability;

import com.example.magrathea.storageengine.application.observability.StorageObservabilityFields;
import com.example.magrathea.storageengine.application.pipeline.StorageEvent;
import com.example.magrathea.storageengine.application.pipeline.StorageEventListener;
import com.example.magrathea.storageengine.application.pipeline.StorageEventType;
import io.opentelemetry.api.common.AttributeKey;
import io.opentelemetry.api.trace.Span;
import io.opentelemetry.api.trace.StatusCode;
import io.opentelemetry.api.trace.Tracer;
import io.opentelemetry.context.Context;
import reactor.core.publisher.Mono;

import java.time.Duration;
import java.time.Instant;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;

/**
 * OpenTelemetry bridge for storage-engine pipeline and recovery events.
 *
 * <p>The listener creates one request-level span per correlation identifier and
 * emits storage stage spans as children. Only fields from
 * {@link StorageObservabilityFields#safeFields(StorageEvent)} are copied to span
 * attributes, so payload bytes, user metadata values, authorization material,
 * and arbitrary exception text are not exported.</p>
 */
public final class OpenTelemetryStorageEventListener implements StorageEventListener {

    private final Tracer tracer;
    private final Map<String, RequestSpan> requestSpans = new ConcurrentHashMap<>();

    public OpenTelemetryStorageEventListener(Tracer tracer) {
        this.tracer = Objects.requireNonNull(tracer, "tracer must not be null");
    }

    @Override
    public Mono<Void> onEvent(StorageEvent event) {
        return Mono.fromRunnable(() -> record(event));
    }

    private void record(StorageEvent event) {
        if (event.type() == StorageEventType.STAGE_STARTED) {
            requestSpan(event);
            return;
        }

        RequestSpan requestSpan = requestSpan(event);
        Span stageSpan = tracer.spanBuilder(stageSpanName(event))
                .setParent(Context.current().with(requestSpan.span()))
                .setStartTimestamp(stageStart(event), TimeUnit.NANOSECONDS)
                .startSpan();
        StorageObservabilityFields.safeFields(event)
                .forEach((key, value) -> stageSpan.setAttribute(AttributeKey.stringKey(key), value));
        event.duration().ifPresent(duration ->
                stageSpan.setAttribute(AttributeKey.longKey("stage.duration.ms"), duration.toMillis()));
        if (event.type() == StorageEventType.STAGE_FAILED) {
            stageSpan.setStatus(StatusCode.ERROR, StorageObservabilityFields.failureClassification(event));
        }
        stageSpan.end(event.occurredAt().toEpochMilli(), TimeUnit.MILLISECONDS);

        if (isTerminal(event)) {
            finishRequestSpan(event, requestSpan);
        }
    }

    private RequestSpan requestSpan(StorageEvent event) {
        return requestSpans.computeIfAbsent(event.correlationId(), ignored -> {
            Span span = tracer.spanBuilder(requestSpanName(event))
                    .setStartTimestamp(event.occurredAt().toEpochMilli(), TimeUnit.MILLISECONDS)
                    .startSpan();
            StorageObservabilityFields.safeFields(event)
                    .forEach((key, value) -> span.setAttribute(AttributeKey.stringKey(key), value));
            span.setAttribute(AttributeKey.stringKey("span.kind"), "storage-request");
            return new RequestSpan(span, event.occurredAt());
        });
    }

    private void finishRequestSpan(StorageEvent event, RequestSpan requestSpan) {
        RequestSpan removed = requestSpans.remove(event.correlationId());
        Span span = removed == null ? requestSpan.span() : removed.span();
        StorageObservabilityFields.safeFields(event)
                .forEach((key, value) -> span.setAttribute(AttributeKey.stringKey(key), value));
        if (event.type() == StorageEventType.STAGE_FAILED) {
            span.setStatus(StatusCode.ERROR, StorageObservabilityFields.failureClassification(event));
        }
        span.end(event.occurredAt().toEpochMilli(), TimeUnit.MILLISECONDS);
    }

    private static long stageStart(StorageEvent event) {
        Instant started = event.occurredAt().minus(event.duration().orElse(Duration.ZERO));
        return TimeUnit.MILLISECONDS.toNanos(started.toEpochMilli());
    }

    private static String requestSpanName(StorageEvent event) {
        return switch (event.operation()) {
            case WRITE -> "PutObject";
            case READ -> "GetObject";
            case RECOVERY -> "StorageRecovery";
        };
    }

    private static String stageSpanName(StorageEvent event) {
        return "magrathea.storage." + StorageObservabilityFields.operationName(event) + "." + event.stageName();
    }

    private static boolean isTerminal(StorageEvent event) {
        if (event.type() == StorageEventType.STAGE_FAILED || event.type() == StorageEventType.STAGE_CANCELLED) {
            return true;
        }
        return switch (event.operation()) {
            case WRITE -> event.type() == StorageEventType.STAGE_SUCCEEDED
                    && "object-index-persistence".equals(event.stageName());
            case READ -> event.type() == StorageEventType.STAGE_SUCCEEDED
                    && "response-streaming".equals(event.stageName());
            case RECOVERY -> event.type() == StorageEventType.RECOVERY_SCAN_COMPLETED
                    || event.type() == StorageEventType.RECOVERY_ARTIFACT_QUARANTINED;
        };
    }

    private record RequestSpan(Span span, Instant startedAt) {
    }
}
