package com.example.magrathea.storageengine.domain.valueobject;

import java.util.Optional;

public record AlterationResult(boolean applied, Optional<String> faultType) {
    public AlterationResult {
        java.util.Objects.requireNonNull(faultType, "faultType must not be null");
    }

    public static AlterationResult of(boolean applied, Optional<String> faultType) {
        return new AlterationResult(applied, faultType);
    }

    public static AlterationResult notApplied() {
        return new AlterationResult(false, Optional.empty());
    }

    public static AlterationResult wasApplied() {
        return new AlterationResult(true, Optional.empty());
    }

    public static AlterationResult fault(String faultType) {
        return new AlterationResult(false, Optional.of(faultType));
    }
}
