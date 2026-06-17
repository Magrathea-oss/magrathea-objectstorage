package com.example.magrathea.storageengine.domain.distributed;

import java.util.List;
import java.util.Objects;

/** Observable modeled healing plan. No copy has been executed by this domain object. */
public record HealingPlan(
        List<AntiEntropyFinding> findings,
        List<HealingTask> tasks,
        String readinessStatus,
        boolean observableIntegrityAlert) {

    public static final String PLANNED_NOT_EXECUTED = "planned-not-executed";
    public static final String SIMULATION_UNRECOVERABLE = "simulation-unrecoverable";
    public static final String NOT_IMPLEMENTED = "not-implemented";

    public HealingPlan {
        Objects.requireNonNull(findings, "findings must not be null");
        Objects.requireNonNull(tasks, "tasks must not be null");
        requireNonBlank(readinessStatus, "readinessStatus");
        findings = List.copyOf(findings);
        tasks = List.copyOf(tasks);
    }

    private static void requireNonBlank(String value, String fieldName) {
        Objects.requireNonNull(value, fieldName + " must not be null");
        if (value.isBlank()) {
            throw new IllegalArgumentException(fieldName + " must not be blank");
        }
    }
}
