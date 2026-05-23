package com.example.magrathea.objectstorage.domain.valueobject;

import java.util.Objects;

/**
 * AWS S3 bucket metrics configuration — value object.
 * Pure domain — NO framework dependencies.
 */
public record BucketMetricsConfiguration(
    String bucketName,
    String id,
    String metricsKey,
    String prefix,
    String location
) {
    public BucketMetricsConfiguration {
        Objects.requireNonNull(bucketName);
    }

    public boolean hasMetrics() {
        return id != null && !id.isBlank();
    }
}
