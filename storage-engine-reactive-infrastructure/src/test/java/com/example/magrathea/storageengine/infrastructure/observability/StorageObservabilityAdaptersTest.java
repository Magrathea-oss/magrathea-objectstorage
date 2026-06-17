package com.example.magrathea.storageengine.infrastructure.observability;

import com.example.magrathea.storageengine.application.observability.StorageSpan;
import com.example.magrathea.storageengine.application.pipeline.CompositeStorageEventPublisher;
import com.example.magrathea.storageengine.application.pipeline.StorageEvent;
import com.example.magrathea.storageengine.application.pipeline.StorageEventMeasurements;
import com.example.magrathea.storageengine.application.pipeline.StorageEventPublisher;
import com.example.magrathea.storageengine.application.pipeline.StorageOperation;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;

class StorageObservabilityAdaptersTest {

    @Test
    void successfulPipelineEventRecordsMetricsAndSafeTraceFields() {
        SimpleMeterRegistry meterRegistry = new SimpleMeterRegistry();
        InMemoryStorageSpanRecorder spanRecorder = new InMemoryStorageSpanRecorder();
        StorageEventPublisher publisher = new CompositeStorageEventPublisher(List.of(
                new MicrometerStorageEventListener(meterRegistry),
                new StorageTracingEventListener(spanRecorder)));

        publisher.publish(new StorageEvent.StageSucceeded(
                StorageOperation.WRITE,
                "corr-success",
                "chunk-persistence",
                Optional.of("customer-private-bucket"),
                Optional.of("private/path/customer-secret-object.bin"),
                Optional.of("manifest-123"),
                Instant.now(),
                Duration.ofMillis(42),
                StorageEventMeasurements.writeChunkStage(8192, 2, 1, 1))).block();

        assertThat(meterRegistry.counter(MicrometerStorageEventListener.BYTES,
                "operation", "put-object", "stage", "chunk-persistence", "backend", "filesystem",
                "outcome", "success", "direction", "write").count())
                .isEqualTo(8192.0);
        assertThat(meterRegistry.counter(MicrometerStorageEventListener.CHUNKS,
                "operation", "put-object", "stage", "chunk-persistence", "backend", "filesystem",
                "outcome", "success").count())
                .isEqualTo(2.0);
        assertThat(meterRegistry.counter(MicrometerStorageEventListener.DEDUP_HITS,
                "operation", "put-object", "stage", "chunk-persistence", "backend", "filesystem",
                "outcome", "success").count())
                .isEqualTo(1.0);
        assertThat(meterRegistry.counter(MicrometerStorageEventListener.DEDUP_MISSES,
                "operation", "put-object", "stage", "chunk-persistence", "backend", "filesystem",
                "outcome", "success").count())
                .isEqualTo(1.0);
        assertThat(meterRegistry.timer(MicrometerStorageEventListener.STAGE_DURATION,
                "operation", "put-object", "stage", "chunk-persistence", "backend", "filesystem",
                "outcome", "success").count())
                .isEqualTo(1);

        StorageSpan span = spanRecorder.spans().getFirst();
        assertThat(span.attributes())
                .containsEntry("correlation.id", "corr-success")
                .containsEntry("request.id", "corr-success")
                .containsEntry("operation", "put-object")
                .containsEntry("backend", "filesystem")
                .containsEntry("stage", "chunk-persistence")
                .containsKey("bucket.hash")
                .containsKey("object.key.hash")
                .containsEntry("manifest.id", "manifest-123")
                .containsEntry("duration.ms", "42");
        assertThat(span.attributes().values()).doesNotContain("customer-private-bucket");
        assertThat(span.attributes().values()).doesNotContain("private/path/customer-secret-object.bin");
    }

    @Test
    void failedPipelineEventRecordsFailureMetricsWithoutLeakingSensitiveOutcome() {
        SimpleMeterRegistry meterRegistry = new SimpleMeterRegistry();
        InMemoryStorageSpanRecorder spanRecorder = new InMemoryStorageSpanRecorder();
        StorageEventPublisher publisher = new CompositeStorageEventPublisher(List.of(
                new MicrometerStorageEventListener(meterRegistry),
                new StorageTracingEventListener(spanRecorder)));

        publisher.publish(new StorageEvent.StageFailed(
                StorageOperation.WRITE,
                "corr-failure",
                "manifest-persistence",
                Optional.of("secret-bucket"),
                Optional.of("object-with-sensitive-metadata"),
                Optional.empty(),
                Instant.now(),
                Duration.ofMillis(5),
                "user-metadata=top-secret body=RAW-PAYLOAD",
                StorageEventMeasurements.failure())).block();

        assertThat(meterRegistry.counter(MicrometerStorageEventListener.FAILURES,
                "operation", "put-object", "stage", "manifest-persistence", "backend", "filesystem",
                "outcome", "failure", "failure.classification", "storage-io-failure").count())
                .isEqualTo(1.0);
        assertThat(meterRegistry.timer(MicrometerStorageEventListener.STAGE_DURATION,
                "operation", "put-object", "stage", "manifest-persistence", "backend", "filesystem",
                "outcome", "failure", "failure.classification", "storage-io-failure").count())
                .isEqualTo(1);

        String tracePayload = spanRecorder.spans().toString();
        String meterPayload = meterRegistry.getMeters().stream()
                .map(meter -> meter.getId().toString())
                .toList()
                .toString();
        assertThat(tracePayload).doesNotContain("top-secret", "RAW-PAYLOAD", "secret-bucket",
                "object-with-sensitive-metadata");
        assertThat(meterPayload).doesNotContain("top-secret", "RAW-PAYLOAD", "secret-bucket",
                "object-with-sensitive-metadata");
    }
}
