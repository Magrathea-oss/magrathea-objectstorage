package com.example.magrathea.storageengine.domain.valueobject;

public record StepChecksumDescriptor(StepId stepId, ContentHash inputChecksum, ContentHash outputChecksum) {
    public StepChecksumDescriptor {
        java.util.Objects.requireNonNull(stepId, "stepId must not be null");
        java.util.Objects.requireNonNull(inputChecksum, "inputChecksum must not be null");
        java.util.Objects.requireNonNull(outputChecksum, "outputChecksum must not be null");
    }

    public static StepChecksumDescriptor of(StepId stepId, ContentHash inputChecksum, ContentHash outputChecksum) {
        return new StepChecksumDescriptor(stepId, inputChecksum, outputChecksum);
    }
}
