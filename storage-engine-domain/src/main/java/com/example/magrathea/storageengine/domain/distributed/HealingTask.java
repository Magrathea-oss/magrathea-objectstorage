package com.example.magrathea.storageengine.domain.distributed;

import java.util.Objects;
import java.util.Optional;

/** Planned, not executed, healing action produced by anti-entropy simulation. */
public record HealingTask(
        String taskId,
        String action,
        String targetNodeId,
        Optional<String> sourceNodeId,
        String status,
        String readiness) {

    public static final String COPY_VERIFIED_REPLICA = "copy-verified-replica";
    public static final String REPLACE_CORRUPT_REPLICA = "replace-corrupt-replica";
    public static final String PLANNED = "PLANNED";
    public static final String PLANNED_NOT_EXECUTED = "planned-not-executed";

    public HealingTask {
        requireNonBlank(taskId, "taskId");
        requireNonBlank(action, "action");
        requireNonBlank(targetNodeId, "targetNodeId");
        Objects.requireNonNull(sourceNodeId, "sourceNodeId must not be null");
        requireNonBlank(status, "status");
        requireNonBlank(readiness, "readiness");
        sourceNodeId = sourceNodeId.filter(value -> !value.isBlank());
    }

    private static void requireNonBlank(String value, String fieldName) {
        Objects.requireNonNull(value, fieldName + " must not be null");
        if (value.isBlank()) {
            throw new IllegalArgumentException(fieldName + " must not be blank");
        }
    }
}
