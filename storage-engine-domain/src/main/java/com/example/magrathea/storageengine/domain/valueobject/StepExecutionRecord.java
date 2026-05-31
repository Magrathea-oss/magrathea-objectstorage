package com.example.magrathea.storageengine.domain.valueobject;

import java.util.Optional;

public record StepExecutionRecord(
        StepId stepId,
        StepExecutionStatus status,
        Optional<StepOutcome> operationOutcome,
        Optional<ChecksumCalculationResult> checksumCalculationResult,
        Optional<AlterationResult> alterationResult,
        Optional<ChecksumVerificationResult> verificationResult,
        Optional<PolicyDecisionReason> bypassReason,
        Optional<ContentHash> inputChecksum,
        Optional<ContentHash> outputChecksum) {

    public StepExecutionRecord {
        java.util.Objects.requireNonNull(stepId, "stepId must not be null");
        java.util.Objects.requireNonNull(status, "status must not be null");
        java.util.Objects.requireNonNull(operationOutcome, "operationOutcome must not be null");
        java.util.Objects.requireNonNull(checksumCalculationResult, "checksumCalculationResult must not be null");
        java.util.Objects.requireNonNull(alterationResult, "alterationResult must not be null");
        java.util.Objects.requireNonNull(verificationResult, "verificationResult must not be null");
        java.util.Objects.requireNonNull(bypassReason, "bypassReason must not be null");
        java.util.Objects.requireNonNull(inputChecksum, "inputChecksum must not be null");
        java.util.Objects.requireNonNull(outputChecksum, "outputChecksum must not be null");

        // Invariants per status
        switch (status) {
            case EXECUTED -> {
                if (operationOutcome.isEmpty()) {
                    throw new IllegalArgumentException(
                            "EXECUTED steps must have an operationOutcome: " + stepId);
                }
                if (outputChecksum.isEmpty()) {
                    throw new IllegalArgumentException(
                            "EXECUTED steps must have an outputChecksum: " + stepId);
                }
            }
            case SKIPPED -> {
                if (operationOutcome.isPresent()) {
                    throw new IllegalArgumentException(
                            "SKIPPED steps must not have an operationOutcome: " + stepId);
                }
            }
            case BYPASSED -> {
                if (bypassReason.isEmpty()) {
                    throw new IllegalArgumentException(
                            "BYPASSED steps must have a bypassReason: " + stepId);
                }
            }
        }
    }
}
