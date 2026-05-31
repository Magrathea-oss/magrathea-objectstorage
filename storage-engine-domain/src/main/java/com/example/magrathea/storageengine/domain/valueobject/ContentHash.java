package com.example.magrathea.storageengine.domain.valueobject;

public record ContentHash(ChecksumAlgorithm algorithm, String value) {
    public ContentHash {
        java.util.Objects.requireNonNull(algorithm, "algorithm must not be null");
        java.util.Objects.requireNonNull(value, "value must not be null");
        if (value.isBlank()) {
            throw new IllegalArgumentException("value must not be blank");
        }
    }

    public static ContentHash of(ChecksumAlgorithm algorithm, String value) {
        return new ContentHash(algorithm, value);
    }
}
