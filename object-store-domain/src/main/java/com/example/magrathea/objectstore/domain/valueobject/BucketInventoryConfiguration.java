package com.example.magrathea.objectstore.domain.valueobject;

import java.util.Objects;

/**
 * AWS S3 bucket inventory configuration — value object.
 * Pure domain — NO framework dependencies.
 */
public record BucketInventoryConfiguration(
    String bucketName,
    String inventoryId,
    String format,
    String frequency,
    boolean enabled
) {
    public BucketInventoryConfiguration {
        Objects.requireNonNull(bucketName);
        // inventoryId, format, frequency are nullable
    }
}
