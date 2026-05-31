package com.example.magrathea.storageengine.domain.valueobject;

import java.util.Optional;

public record EncryptionPolicy(EncryptionAlgorithm algorithm, Optional<KeyReference> defaultKeyReference) {
    public EncryptionPolicy {
        java.util.Objects.requireNonNull(algorithm, "algorithm must not be null");
        java.util.Objects.requireNonNull(defaultKeyReference, "defaultKeyReference must not be null");
    }

    public static EncryptionPolicy of(EncryptionAlgorithm algorithm, Optional<KeyReference> defaultKeyReference) {
        return new EncryptionPolicy(algorithm, defaultKeyReference);
    }

    public static EncryptionPolicy of(EncryptionAlgorithm algorithm) {
        return new EncryptionPolicy(algorithm, Optional.empty());
    }
}
