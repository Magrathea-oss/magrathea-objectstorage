package com.example.magrathea.storageengine.application.pipeline;

import java.time.Duration;
import java.time.Instant;
import java.util.Optional;

public sealed interface StorageEvent permits StorageEvent.StageStarted, StorageEvent.StageSucceeded,
        StorageEvent.StageFailed, StorageEvent.StageCancelled, StorageEvent.CleanupCompleted,
        StorageEvent.RecoveryScanCompleted, StorageEvent.RecoveryArtifactQuarantined {

    StorageEventType type();

    StorageOperation operation();

    String correlationId();

    String stageName();

    Optional<String> bucket();

    Optional<String> objectKey();

    Optional<String> manifestId();

    Instant occurredAt();

    Optional<Duration> duration();

    Optional<String> outcome();

    default StorageEventMeasurements measurements() {
        return StorageEventMeasurements.empty();
    }

    record StageStarted(
            StorageOperation operation,
            String correlationId,
            String stageName,
            Optional<String> bucket,
            Optional<String> objectKey,
            Optional<String> manifestId,
            Instant occurredAt) implements StorageEvent {
        @Override
        public StorageEventType type() {
            return StorageEventType.STAGE_STARTED;
        }

        @Override
        public Optional<Duration> duration() {
            return Optional.empty();
        }

        @Override
        public Optional<String> outcome() {
            return Optional.of("started");
        }
    }

    record StageSucceeded(
            StorageOperation operation,
            String correlationId,
            String stageName,
            Optional<String> bucket,
            Optional<String> objectKey,
            Optional<String> manifestId,
            Instant occurredAt,
            Duration elapsed,
            StorageEventMeasurements measurements) implements StorageEvent {

        public StageSucceeded(
                StorageOperation operation,
                String correlationId,
                String stageName,
                Optional<String> bucket,
                Optional<String> objectKey,
                Optional<String> manifestId,
                Instant occurredAt,
                Duration elapsed) {
            this(operation, correlationId, stageName, bucket, objectKey, manifestId, occurredAt,
                    elapsed, StorageEventMeasurements.empty());
        }

        @Override
        public StorageEventType type() {
            return StorageEventType.STAGE_SUCCEEDED;
        }

        @Override
        public Optional<Duration> duration() {
            return Optional.of(elapsed);
        }

        @Override
        public Optional<String> outcome() {
            return Optional.of("success");
        }
    }

    record StageFailed(
            StorageOperation operation,
            String correlationId,
            String stageName,
            Optional<String> bucket,
            Optional<String> objectKey,
            Optional<String> manifestId,
            Instant occurredAt,
            Duration elapsed,
            String reason,
            StorageEventMeasurements measurements) implements StorageEvent {

        public StageFailed(
                StorageOperation operation,
                String correlationId,
                String stageName,
                Optional<String> bucket,
                Optional<String> objectKey,
                Optional<String> manifestId,
                Instant occurredAt,
                Duration elapsed,
                String reason) {
            this(operation, correlationId, stageName, bucket, objectKey, manifestId, occurredAt,
                    elapsed, reason, StorageEventMeasurements.failure());
        }

        @Override
        public StorageEventType type() {
            return StorageEventType.STAGE_FAILED;
        }

        @Override
        public Optional<Duration> duration() {
            return Optional.of(elapsed);
        }

        @Override
        public Optional<String> outcome() {
            return Optional.of(reason);
        }
    }

    record StageCancelled(
            StorageOperation operation,
            String correlationId,
            String stageName,
            Optional<String> bucket,
            Optional<String> objectKey,
            Optional<String> manifestId,
            Instant occurredAt) implements StorageEvent {
        @Override
        public StorageEventType type() {
            return StorageEventType.STAGE_CANCELLED;
        }

        @Override
        public Optional<Duration> duration() {
            return Optional.empty();
        }

        @Override
        public Optional<String> outcome() {
            return Optional.of("cancelled");
        }
    }

    record CleanupCompleted(
            StorageOperation operation,
            String correlationId,
            String stageName,
            Optional<String> bucket,
            Optional<String> objectKey,
            Optional<String> manifestId,
            Instant occurredAt,
            Duration elapsed,
            String cleanupName) implements StorageEvent {
        @Override
        public StorageEventType type() {
            return StorageEventType.CLEANUP_COMPLETED;
        }

        @Override
        public Optional<Duration> duration() {
            return Optional.of(elapsed);
        }

        @Override
        public Optional<String> outcome() {
            return Optional.of(cleanupName);
        }
    }

    record RecoveryScanCompleted(
            String correlationId,
            String stageName,
            Instant occurredAt,
            Duration elapsed,
            String status,
            StorageEventMeasurements measurements) implements StorageEvent {
        @Override
        public StorageEventType type() {
            return StorageEventType.RECOVERY_SCAN_COMPLETED;
        }

        @Override
        public StorageOperation operation() {
            return StorageOperation.RECOVERY;
        }

        @Override
        public Optional<String> bucket() {
            return Optional.empty();
        }

        @Override
        public Optional<String> objectKey() {
            return Optional.empty();
        }

        @Override
        public Optional<String> manifestId() {
            return Optional.empty();
        }

        @Override
        public Optional<Duration> duration() {
            return Optional.of(elapsed);
        }

        @Override
        public Optional<String> outcome() {
            return Optional.of(status);
        }
    }

    record RecoveryArtifactQuarantined(
            String correlationId,
            String stageName,
            Instant occurredAt,
            String artifactType,
            String artifactHash,
            StorageEventMeasurements measurements) implements StorageEvent {
        @Override
        public StorageEventType type() {
            return StorageEventType.RECOVERY_ARTIFACT_QUARANTINED;
        }

        @Override
        public StorageOperation operation() {
            return StorageOperation.RECOVERY;
        }

        @Override
        public Optional<String> bucket() {
            return Optional.empty();
        }

        @Override
        public Optional<String> objectKey() {
            return Optional.empty();
        }

        @Override
        public Optional<String> manifestId() {
            return Optional.empty();
        }

        @Override
        public Optional<Duration> duration() {
            return Optional.empty();
        }

        @Override
        public Optional<String> outcome() {
            return Optional.of(artifactType);
        }
    }
}
