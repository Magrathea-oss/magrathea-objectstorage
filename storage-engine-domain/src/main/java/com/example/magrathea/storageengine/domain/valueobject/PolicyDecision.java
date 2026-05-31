package com.example.magrathea.storageengine.domain.valueobject;

public record PolicyDecision(StepId feature, PolicyDecisionStatus status, PolicyDecisionReason reason) {
    public PolicyDecision {
        java.util.Objects.requireNonNull(feature, "feature must not be null");
        java.util.Objects.requireNonNull(status, "status must not be null");
        java.util.Objects.requireNonNull(reason, "reason must not be null");
    }

    public static PolicyDecision of(StepId feature, PolicyDecisionStatus status, PolicyDecisionReason reason) {
        return new PolicyDecision(feature, status, reason);
    }
}
