package com.example.magrathea.objectstorage.domain.valueobject;

import java.util.Objects;

/**
 * AWS S3 bucket accelerate configuration — value object.
 * Pure domain — NO framework dependencies.
 */
public record BucketAccelerateConfiguration(
    String bucketName,
    String status
) {
    public BucketAccelerateConfiguration {
        Objects.requireNonNull(bucketName);
        // status: "Enabled" or "Suspended"
    }
}
