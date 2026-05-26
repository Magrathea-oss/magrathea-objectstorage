package com.example.magrathea.objectstore.domain.valueobject;

import java.util.Objects;

/**
 * AWS S3 bucket intelligent-tiering configuration — value object.
 * Pure domain — NO framework dependencies.
 */
public record BucketIntelligentTieringConfiguration(
    String bucketName,
    String tieringId,
    String tieringPolicy,
    String autoTieringStatus
) {
    public BucketIntelligentTieringConfiguration {
        Objects.requireNonNull(bucketName);
        // tieringId, tieringPolicy, autoTieringStatus are nullable
    }
}
