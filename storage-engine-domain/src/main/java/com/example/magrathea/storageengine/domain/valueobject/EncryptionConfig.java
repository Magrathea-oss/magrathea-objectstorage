package com.example.magrathea.storageengine.domain.valueobject;

import java.util.Optional;

public record EncryptionConfig(EncryptionAlgorithm algorithm, Optional<KeyReference> keyReference) {
    public EncryptionConfig {
        java.util.Objects.requireNonNull(algorithm, "algorithm must not be null");
        java.util.Objects.requireNonNull(keyReference, "keyReference must not be null");
    }

    public static EncryptionConfig of(EncryptionAlgorithm algorithm, Optional<KeyReference> keyReference) {
        return new EncryptionConfig(algorithm, keyReference);
    }

    public static EncryptionConfig of(EncryptionAlgorithm algorithm) {
        return new EncryptionConfig(algorithm, Optional.empty());
    }

    public static EncryptionConfig of(EncryptionAlgorithm algorithm, KeyReference keyReference) {
        return new EncryptionConfig(algorithm, Optional.of(keyReference));
    }
}
