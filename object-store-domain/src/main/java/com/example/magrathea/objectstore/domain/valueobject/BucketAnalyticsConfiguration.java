package com.example.magrathea.objectstore.domain.valueobject;

import java.util.Objects;

/**
 * AWS S3 bucket analytics configuration — value object.
 * Pure domain — NO framework dependencies.
 */
public record BucketAnalyticsConfiguration(
    String bucketName,
    String analyticsId,
    String filterRule
) {
    public BucketAnalyticsConfiguration {
        Objects.requireNonNull(bucketName);
        // analyticsId and filterRule are optional (nullable)
    }
}
