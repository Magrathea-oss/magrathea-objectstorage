package com.example.magrathea.storageengine.domain.valueobject;

public record Fingerprint(FingerprintAlgorithm algorithm, String value) {
    public Fingerprint {
        java.util.Objects.requireNonNull(algorithm, "algorithm must not be null");
        java.util.Objects.requireNonNull(value, "value must not be null");
        if (value.isBlank()) {
            throw new IllegalArgumentException("value must not be blank");
        }
    }

    public static Fingerprint of(FingerprintAlgorithm algorithm, String value) {
        return new Fingerprint(algorithm, value);
    }
}
