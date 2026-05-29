package com.example.magrathea.objectstore.domain.valueobject;

import java.util.Objects;

/**
 * Reference to a KMS key or customer-managed key.
 */
public record EncryptionKeyReference(String keyId) {
    public EncryptionKeyReference {
        Objects.requireNonNull(keyId, "keyId must not be null");
    }

    public static EncryptionKeyReference of(String keyId) {
        return new EncryptionKeyReference(keyId);
    }
}
