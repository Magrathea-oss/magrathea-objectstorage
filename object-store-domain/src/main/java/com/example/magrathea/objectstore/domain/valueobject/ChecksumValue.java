package com.example.magrathea.objectstore.domain.valueobject;

import java.util.Objects;

/**
 * A typed checksum value with a known algorithm and BASE64-encoded hash.
 * Uses proper domain enum {@link ChecksumAlgorithm}.
 */
public record ChecksumValue(
    ChecksumAlgorithm algorithm,
    String base64Value
) {
    public ChecksumValue {
        Objects.requireNonNull(algorithm, "algorithm must not be null");
        Objects.requireNonNull(base64Value, "base64Value must not be null");
        if (base64Value.isBlank()) {
            throw new IllegalArgumentException("base64Value must not be blank");
        }
    }
}
