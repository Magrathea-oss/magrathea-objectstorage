package com.example.magrathea.objectstore.domain.valueobject;

import java.util.Objects;

/**
 * AWS S3 public access block configuration — value object.
 * Pure domain — NO framework dependencies.
 */
public record PublicAccessBlockConfiguration(
    String bucketName,
    boolean blockPublicAcls,
    boolean ignorePublicAcls,
    boolean blockPublicPolicy,
    boolean restrictPublicBuckets
) {
    public PublicAccessBlockConfiguration {
        Objects.requireNonNull(bucketName);
    }
}
