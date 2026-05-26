package com.example.magrathea.objectstore.domain.valueobject;

import java.util.Objects;

/**
 * AWS S3 bucket ownership controls — value object.
 * Pure domain — NO framework dependencies.
 */
public record BucketOwnershipControls(
    String bucketName,
    String ruleId,
    String ownership
) {
    public BucketOwnershipControls {
        Objects.requireNonNull(bucketName);
        // ownership: "BucketOwnerPreferred", "ObjectWriter", "BucketOwnerEnforced"
    }
}
