package com.example.magrathea.storageengine.domain.distributed;

import java.util.List;
import java.util.Objects;

/** Modeled outcome for a rebalance task. */
public record RebalanceTaskResult(
        String taskId,
        String status,
        String retryEligibility,
        String failureReason,
        String manifestId,
        List<String> originalReplicasCommitted,
        boolean aboveWriteQuorum) {

    public static final String FAILED = "FAILED";
    public static final String PLANNED = "PLANNED";
    public static final String RETRYABLE = "RETRYABLE";

    public RebalanceTaskResult {
        requireNonBlank(taskId, "taskId");
        requireNonBlank(status, "status");
        requireNonBlank(retryEligibility, "retryEligibility");
        requireNonBlank(failureReason, "failureReason");
        requireNonBlank(manifestId, "manifestId");
        Objects.requireNonNull(originalReplicasCommitted, "originalReplicasCommitted must not be null");
        originalReplicasCommitted = originalReplicasCommitted.stream().sorted().toList();
    }

    private static void requireNonBlank(String value, String fieldName) {
        Objects.requireNonNull(value, fieldName + " must not be null");
        if (value.isBlank()) {
            throw new IllegalArgumentException(fieldName + " must not be blank");
        }
    }
}
