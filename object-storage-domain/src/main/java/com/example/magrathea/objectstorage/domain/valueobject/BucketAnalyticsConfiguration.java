package com.example.magrathea.objectstorage.domain.valueobject;

import java.util.Objects;

/**
 * AWS S3 bucket analytics configuration — value object.
 * Pure domain — NO framework dependencies.
 */
public record BucketAnalyticsConfiguration(
    String bucketName,
    String id,
    String analyticsKey,
    String format,
    String prefix,
    String location
) {
    public BucketAnalyticsConfiguration {
        Objects.requireNonNull(bucketName);
    }

    public boolean hasAnalytics() {
        return id != null && !id.isBlank();
    }
}
