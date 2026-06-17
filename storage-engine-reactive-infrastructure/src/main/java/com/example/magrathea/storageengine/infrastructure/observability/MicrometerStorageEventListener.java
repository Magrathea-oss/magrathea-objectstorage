package com.example.magrathea.storageengine.infrastructure.observability;

import com.example.magrathea.storageengine.application.observability.StorageObservabilityFields;
import com.example.magrathea.storageengine.application.pipeline.StorageEvent;
import com.example.magrathea.storageengine.application.pipeline.StorageEventListener;
import com.example.magrathea.storageengine.application.pipeline.StorageEventMeasurements;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import reactor.core.publisher.Mono;

import java.util.Objects;
import java.util.concurrent.TimeUnit;

public final class MicrometerStorageEventListener implements StorageEventListener {

    public static final String STAGE_DURATION = "magrathea.storage.pipeline.stage.duration";
    public static final String FAILURES = "magrathea.storage.pipeline.failures";
    public static final String BYTES = "magrathea.storage.pipeline.bytes";
    public static final String CHUNKS = "magrathea.storage.pipeline.chunks";
    public static final String MANIFESTS = "magrathea.storage.pipeline.manifests";
    public static final String DEDUP_HITS = "magrathea.storage.pipeline.dedup.hits";
    public static final String DEDUP_MISSES = "magrathea.storage.pipeline.dedup.misses";
    public static final String RECOVERY_FINDINGS = "magrathea.storage.recovery.findings";
    public static final String RECOVERY_QUARANTINES = "magrathea.storage.recovery.quarantines";

    private final MeterRegistry registry;

    public MicrometerStorageEventListener(MeterRegistry registry) {
        this.registry = Objects.requireNonNull(registry, "registry must not be null");
    }

    @Override
    public Mono<Void> onEvent(StorageEvent event) {
        return Mono.fromRunnable(() -> record(event));
    }

    private void record(StorageEvent event) {
        recordDuration(event);
        StorageEventMeasurements measurements = event.measurements();
        increment(FAILURES, measurements.failures(), event, "failure.classification", classification(event));
        increment(BYTES, measurements.bytesWritten(), event, "direction", "write");
        increment(BYTES, measurements.bytesRead(), event, "direction", "read");
        increment(CHUNKS, measurements.chunks(), event);
        increment(MANIFESTS, measurements.manifests(), event);
        increment(DEDUP_HITS, measurements.dedupHits(), event);
        increment(DEDUP_MISSES, measurements.dedupMisses(), event);
        increment(RECOVERY_FINDINGS, measurements.recoveryFindings(), event);
        increment(RECOVERY_QUARANTINES, measurements.recoveryQuarantines(), event);
    }

    private void recordDuration(StorageEvent event) {
        event.duration().ifPresent(duration -> {
            Timer.Builder builder = Timer.builder(STAGE_DURATION)
                    .tag("operation", operation(event))
                    .tag("stage", event.stageName())
                    .tag("backend", "filesystem")
                    .tag("outcome", outcome(event));
            if (isFailure(event)) {
                builder.tag("failure.classification", classification(event));
            }
            builder.register(registry).record(duration.toNanos(), TimeUnit.NANOSECONDS);
        });
    }

    private void increment(String name, long amount, StorageEvent event, String... extraTags) {
        if (amount <= 0) {
            return;
        }
        Counter.Builder builder = Counter.builder(name)
                .tag("operation", operation(event))
                .tag("stage", event.stageName())
                .tag("backend", "filesystem")
                .tag("outcome", outcome(event));
        for (int index = 0; index < extraTags.length; index += 2) {
            builder.tag(extraTags[index], extraTags[index + 1]);
        }
        builder.register(registry).increment(amount);
    }

    private static String operation(StorageEvent event) {
        return StorageObservabilityFields.operationName(event);
    }

    private static String outcome(StorageEvent event) {
        return StorageObservabilityFields.safeStatus(event);
    }

    private static String classification(StorageEvent event) {
        return StorageObservabilityFields.failureClassification(event);
    }

    private static boolean isFailure(StorageEvent event) {
        return "failure".equals(outcome(event));
    }
}
