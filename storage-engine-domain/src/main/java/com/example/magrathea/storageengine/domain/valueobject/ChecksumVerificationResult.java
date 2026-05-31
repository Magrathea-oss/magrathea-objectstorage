package com.example.magrathea.storageengine.domain.valueobject;

import java.util.Optional;

public record ChecksumVerificationResult(boolean passed, Optional<String> failureReason) {
    public ChecksumVerificationResult {
        java.util.Objects.requireNonNull(failureReason, "failureReason must not be null");
    }

    public static ChecksumVerificationResult of(boolean passed, Optional<String> failureReason) {
        return new ChecksumVerificationResult(passed, failureReason);
    }

    public static ChecksumVerificationResult verified() {
        return new ChecksumVerificationResult(true, Optional.empty());
    }

    public static ChecksumVerificationResult failed(String reason) {
        return new ChecksumVerificationResult(false, Optional.of(reason));
    }
}
