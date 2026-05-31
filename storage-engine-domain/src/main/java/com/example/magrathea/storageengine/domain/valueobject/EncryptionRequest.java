package com.example.magrathea.storageengine.domain.valueobject;

import java.util.Optional;

public record EncryptionRequest(EncryptionMode mode, Optional<KeyReference> keyReference) {
    public EncryptionRequest {
        java.util.Objects.requireNonNull(mode, "mode must not be null");
        java.util.Objects.requireNonNull(keyReference, "keyReference must not be null");
    }

    public static EncryptionRequest of(EncryptionMode mode, Optional<KeyReference> keyReference) {
        return new EncryptionRequest(mode, keyReference);
    }

    public static EncryptionRequest none() {
        return new EncryptionRequest(EncryptionMode.NONE, Optional.empty());
    }
}
