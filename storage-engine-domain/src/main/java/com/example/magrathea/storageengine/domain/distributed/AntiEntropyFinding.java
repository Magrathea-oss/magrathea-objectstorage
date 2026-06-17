package com.example.magrathea.storageengine.domain.distributed;

import java.util.Objects;
import java.util.Optional;

/** Modeled anti-entropy finding for a manifest replica set. */
public record AntiEntropyFinding(String findingType, Optional<String> nodeId, String bucket, String key) {

    public static final String MISSING_REPLICA = "missing-replica";
    public static final String CORRUPT_REPLICA = "corrupt-replica";
    public static final String UNRECOVERABLE_NO_VERIFIED_SOURCE = "unrecoverable-no-verified-source";

    public AntiEntropyFinding {
        requireNonBlank(findingType, "findingType");
        Objects.requireNonNull(nodeId, "nodeId must not be null");
        requireNonBlank(bucket, "bucket");
        requireNonBlank(key, "key");
        nodeId = nodeId.filter(value -> !value.isBlank());
    }

    public static AntiEntropyFinding forNode(String findingType, String nodeId, String bucket, String key) {
        return new AntiEntropyFinding(findingType, Optional.of(nodeId), bucket, key);
    }

    public static AntiEntropyFinding unrecoverable(String bucket, String key) {
        return new AntiEntropyFinding(UNRECOVERABLE_NO_VERIFIED_SOURCE, Optional.empty(), bucket, key);
    }

    private static void requireNonBlank(String value, String fieldName) {
        Objects.requireNonNull(value, fieldName + " must not be null");
        if (value.isBlank()) {
            throw new IllegalArgumentException(fieldName + " must not be blank");
        }
    }
}
