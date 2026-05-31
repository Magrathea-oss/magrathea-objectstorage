package com.example.magrathea.storageengine.domain.valueobject;

public record DeclaredChecksum(ChecksumAlgorithm algorithm, String value) {
    public DeclaredChecksum {
        java.util.Objects.requireNonNull(algorithm, "algorithm must not be null");
        java.util.Objects.requireNonNull(value, "value must not be null");
        if (value.isBlank()) {
            throw new IllegalArgumentException("value must not be blank");
        }
    }

    public static DeclaredChecksum of(ChecksumAlgorithm algorithm, String value) {
        return new DeclaredChecksum(algorithm, value);
    }
}
