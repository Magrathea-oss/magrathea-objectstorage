package com.example.magrathea.objectstore.domain.valueobject;

import java.util.Objects;

/**
 * EncryptionConfiguration — a pure domain concept representing encryption intent for an S3 object.
 * <p>
 * NOT HTTP headers. This is a domain abstraction of the encryption decision:
 * algorithm, key reference, and context.
 * </p>
 * Pure domain — NO framework dependencies.
 */
public record EncryptionConfiguration(
    EncryptionAlgorithm algorithm,
    EncryptionKeyReference keyReference,
    EncryptionContext encryptionContext
) {
    public EncryptionConfiguration {
        Objects.requireNonNull(algorithm, "algorithm must not be null");
    }

    /**
     * Factory method — create a new encryption configuration.
     */
    public static EncryptionConfiguration of(EncryptionAlgorithm algorithm) {
        return new EncryptionConfiguration(algorithm, null, null);
    }

    /**
     * Factory method — create with algorithm and key reference.
     */
    public static EncryptionConfiguration of(EncryptionAlgorithm algorithm, EncryptionKeyReference keyReference) {
        return new EncryptionConfiguration(algorithm, keyReference, null);
    }

    /**
     * Factory method — create with all fields.
     */
    public static EncryptionConfiguration of(EncryptionAlgorithm algorithm,
                                              EncryptionKeyReference keyReference,
                                              EncryptionContext encryptionContext) {
        return new EncryptionConfiguration(algorithm, keyReference, encryptionContext);
    }
}
