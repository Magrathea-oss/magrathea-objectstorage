package com.example.magrathea.storageengine.application.observability;

import com.example.magrathea.storageengine.application.pipeline.StorageEvent;
import com.example.magrathea.storageengine.application.pipeline.StorageEventMeasurements;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Duration;
import java.util.HexFormat;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Builds safe observability fields. Raw object keys, bucket names, body bytes, and
 * user metadata values are intentionally not copied into metrics, traces, or logs.
 */
public final class StorageObservabilityFields {

    private StorageObservabilityFields() {
    }

    public static Map<String, String> safeFields(StorageEvent event) {
        LinkedHashMap<String, String> fields = new LinkedHashMap<>();
        fields.put("correlation.id", event.correlationId());
        fields.put("request.id", event.correlationId());
        fields.put("operation", operationName(event));
        fields.put("backend", "filesystem");
        fields.put("event.type", event.type().name());
        fields.put("stage", event.stageName());
        fields.put("outcome", safeStatus(event));
        fields.put("failure.classification", failureClassification(event));
        event.bucket().map(StorageObservabilityFields::sha256Hex).ifPresent(value -> fields.put("bucket.hash", value));
        event.objectKey().map(StorageObservabilityFields::sha256Hex).ifPresent(value -> fields.put("object.key.hash", value));
        event.manifestId().ifPresent(value -> fields.put("manifest.id", value));
        if (event instanceof StorageEvent.RecoveryArtifactQuarantined quarantined) {
            fields.put("artifact.type", quarantined.artifactType());
            fields.put("artifact.hash", quarantined.artifactHash());
        }
        event.duration().map(Duration::toMillis).ifPresent(value -> fields.put("duration.ms", Long.toString(value)));
        putMeasurements(fields, event.measurements());
        return Map.copyOf(fields);
    }

    public static String sha256Hex(String value) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            return HexFormat.of().formatHex(digest.digest(value.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 is not available", e);
        }
    }

    public static String operationName(StorageEvent event) {
        return switch (event.operation()) {
            case WRITE -> "put-object";
            case READ -> "get-object";
            case RECOVERY -> "recovery";
        };
    }

    public static String failureClassification(StorageEvent event) {
        if (event instanceof StorageEvent.StageCancelled) {
            return "cancellation";
        }
        if (!(event instanceof StorageEvent.StageFailed)) {
            return "none";
        }
        String outcome = event.outcome().orElse("").toLowerCase();
        if (outcome.contains("checksum") || outcome.contains("integrity") || outcome.contains("corrupt")) {
            return "integrity-failure";
        }
        if (outcome.contains("validation") || outcome.contains("invalid")) {
            return "validation-failure";
        }
        if (outcome.contains("cancel")) {
            return "cancellation";
        }
        if (outcome.contains("recovery")) {
            return "recovery-failure";
        }
        return "storage-io-failure";
    }

    public static String safeStatus(StorageEvent event) {
        return switch (event.type()) {
            case STAGE_FAILED -> "failure";
            case STAGE_CANCELLED -> "cancelled";
            case RECOVERY_ARTIFACT_QUARANTINED -> "quarantined";
            case RECOVERY_SCAN_COMPLETED -> event.outcome().orElse("completed");
            default -> "success";
        };
    }

    private static void putMeasurements(Map<String, String> fields, StorageEventMeasurements measurements) {
        putPositive(fields, "bytes.written", measurements.bytesWritten());
        putPositive(fields, "bytes.read", measurements.bytesRead());
        putPositive(fields, "chunks", measurements.chunks());
        putPositive(fields, "manifests", measurements.manifests());
        putPositive(fields, "failures", measurements.failures());
        putPositive(fields, "recovery.findings", measurements.recoveryFindings());
        putPositive(fields, "recovery.quarantines", measurements.recoveryQuarantines());
        putPositive(fields, "dedup.hits", measurements.dedupHits());
        putPositive(fields, "dedup.misses", measurements.dedupMisses());
    }

    private static void putPositive(Map<String, String> fields, String key, long value) {
        if (value > 0) {
            fields.put(key, Long.toString(value));
        }
    }
}
