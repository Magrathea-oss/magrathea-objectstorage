package com.example.magrathea.storageengine.domain.distributed;

import java.util.Objects;

/** Planned rebalance move or additional copy. */
public record RebalanceMove(
        String sourceNodeId,
        String targetNodeId,
        String sourceFailureDomain,
        String targetFailureDomain,
        String manifestId,
        int committedReplicasKept) {

    public RebalanceMove {
        requireNonBlank(sourceNodeId, "sourceNodeId");
        requireNonBlank(targetNodeId, "targetNodeId");
        requireNonBlank(sourceFailureDomain, "sourceFailureDomain");
        requireNonBlank(targetFailureDomain, "targetFailureDomain");
        requireNonBlank(manifestId, "manifestId");
        if (committedReplicasKept < 0) {
            throw new IllegalArgumentException("committedReplicasKept must not be negative");
        }
    }

    private static void requireNonBlank(String value, String fieldName) {
        Objects.requireNonNull(value, fieldName + " must not be null");
        if (value.isBlank()) {
            throw new IllegalArgumentException(fieldName + " must not be blank");
        }
    }
}
