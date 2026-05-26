package com.example.magrathea.objectstore.domain.valueobject;

import java.util.Objects;

/**
 * AWS S3 bucket encryption configuration — value object.
 * Pure domain — NO framework dependencies.
 */
public record BucketEncryptionConfiguration(
    String bucketName,
    String ruleId,
    String algorithm,
    String kmsKeyId
) {
    public BucketEncryptionConfiguration {
        Objects.requireNonNull(bucketName);
        // algorithm can be null (no encryption) or "AES256", "KMS", etc.
    }

    public boolean hasEncryption() {
        return algorithm != null && !algorithm.isBlank();
    }
}
