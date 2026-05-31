package com.example.magrathea.storageengine.domain.valueobject;

import java.util.Optional;

public record StepPlan(
        StepId stepId,
        StepExecutionStatus expectedStatus,
        Optional<StepConfig> config,
        boolean requiresOperation,
        boolean requiresInputChecksumVerification,
        boolean allowsAlteration,
        boolean requiresOutputChecksumCalculation) {

    public StepPlan {
        java.util.Objects.requireNonNull(stepId, "stepId must not be null");
        java.util.Objects.requireNonNull(expectedStatus, "expectedStatus must not be null");
        java.util.Objects.requireNonNull(config, "config must not be null");
    }

    public static StepPlan of(
            StepId stepId,
            StepExecutionStatus expectedStatus,
            Optional<StepConfig> config,
            boolean requiresOperation,
            boolean requiresInputChecksumVerification,
            boolean allowsAlteration,
            boolean requiresOutputChecksumCalculation) {
        return new StepPlan(stepId, expectedStatus, config,
                requiresOperation, requiresInputChecksumVerification,
                allowsAlteration, requiresOutputChecksumCalculation);
    }
}
