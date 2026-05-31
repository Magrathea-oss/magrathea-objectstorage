package com.example.magrathea.storageengine.domain.valueobject;

public record PolicyDecisionReason(String code, String description) {
    public PolicyDecisionReason {
        java.util.Objects.requireNonNull(code, "code must not be null");
        java.util.Objects.requireNonNull(description, "description must not be null");
        if (code.isBlank()) {
            throw new IllegalArgumentException("code must not be blank");
        }
        if (description.isBlank()) {
            throw new IllegalArgumentException("description must not be blank");
        }
    }

    public static PolicyDecisionReason of(String code, String description) {
        return new PolicyDecisionReason(code, description);
    }
}
