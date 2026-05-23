package com.example.magrathea.objectstorage.domain.valueobject;

import java.util.Objects;

/**
 * AWS S3 bucket intelligent-tiering configuration — value object.
 * Pure domain — NO framework dependencies.
 */
public record BucketIntelligentTieringConfiguration(
    String bucketName,
    String id,
    String status,
    String tieringPolicy
) {
    public BucketIntelligentTieringConfiguration {
        Objects.requireNonNull(bucketName);
    }

    public boolean hasIntelligentTiering() {
        return id != null && !id.isBlank();
    }
}
