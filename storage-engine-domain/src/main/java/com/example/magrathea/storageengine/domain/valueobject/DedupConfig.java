package com.example.magrathea.storageengine.domain.valueobject;

public record DedupConfig(DedupScope scope, FingerprintAlgorithm algorithm) {
    public DedupConfig {
        java.util.Objects.requireNonNull(scope, "scope must not be null");
        java.util.Objects.requireNonNull(algorithm, "algorithm must not be null");
    }

    public static DedupConfig of(DedupScope scope, FingerprintAlgorithm algorithm) {
        return new DedupConfig(scope, algorithm);
    }
}
