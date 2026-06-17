package com.example.magrathea.storageengine.domain.distributed;

import java.util.Objects;
import java.util.Optional;

/** Observable integrity finding produced by read quorum validation. */
public record IntegrityFinding(
        String bucket,
        String key,
        String nodeId,
        String findingType,
        Optional<String> expectedChecksum,
        Optional<String> observedChecksum) {

    public IntegrityFinding {
        requireNonBlank(bucket, "bucket");
        requireNonBlank(key, "key");
        requireNonBlank(nodeId, "nodeId");
        requireNonBlank(findingType, "findingType");
        Objects.requireNonNull(expectedChecksum, "expectedChecksum must not be null");
        Objects.requireNonNull(observedChecksum, "observedChecksum must not be null");
    }

    public static IntegrityFinding checksumMismatch(
            String bucket, String key, String nodeId, String expectedChecksum, String observedChecksum) {
        return new IntegrityFinding(
                bucket,
                key,
                nodeId,
                "checksum-mismatch",
                Optional.of(expectedChecksum),
                Optional.ofNullable(observedChecksum));
    }

    private static void requireNonBlank(String value, String fieldName) {
        Objects.requireNonNull(value, fieldName + " must not be null");
        if (value.isBlank()) {
            throw new IllegalArgumentException(fieldName + " must not be blank");
        }
    }
}
