package com.example.magrathea.objectstorage.domain.valueobject;

import java.util.Objects;

/**
 * AWS S3 bucket metrics configuration — value object.
 * Pure domain — NO framework dependencies.
 */
public record BucketMetricsConfiguration(
    String bucketName,
    String metricsId,
    String filterRule
) {
    public BucketMetricsConfiguration {
        Objects.requireNonNull(bucketName);
        // metricsId and filterRule are optional (nullable)
    }
}
