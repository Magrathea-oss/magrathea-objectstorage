package com.example.magrathea.objectstore.domain.valueobject;

import java.util.Map;
import java.util.Objects;

/**
 * Encryption context as a map of key-value pairs.
 */
public record EncryptionContext(Map<String, String> context) {
    public EncryptionContext {
        Objects.requireNonNull(context, "context must not be null");
    }

    public static EncryptionContext of(Map<String, String> context) {
        return new EncryptionContext(Map.copyOf(context));
    }
}
