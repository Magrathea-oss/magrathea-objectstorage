package com.example.magrathea.storageengine.domain.valueobject;

public record KeyReference(String keyId) {
    public KeyReference {
        java.util.Objects.requireNonNull(keyId, "keyId must not be null");
        if (keyId.isBlank()) {
            throw new IllegalArgumentException("keyId must not be blank");
        }
    }

    public static KeyReference of(String keyId) {
        return new KeyReference(keyId);
    }
}
