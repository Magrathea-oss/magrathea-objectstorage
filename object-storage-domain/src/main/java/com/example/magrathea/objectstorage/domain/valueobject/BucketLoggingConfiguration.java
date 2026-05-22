package com.example.magrathea.objectstorage.domain.valueobject;

import java.util.Objects;

/**
 * AWS S3 bucket logging configuration — value object.
 * Pure domain — NO framework dependencies.
 */
public record BucketLoggingConfiguration(
    String bucketName,
    String targetBucket,
    String targetPrefix
) {
    public BucketLoggingConfiguration {
        Objects.requireNonNull(bucketName);
    }

    public boolean hasLogging() {
        return targetBucket != null && !targetBucket.isBlank();
    }
}
