package com.example.magrathea.objectstorage.domain.valueobject;

import java.util.Objects;

/**
 * AWS S3 bucket policy — value object.
 * The policy is stored as a JSON string (IAM policy document).
 * Pure domain — NO framework dependencies.
 */
public record BucketPolicy(
    String bucketName,
    String policyJson
) {
    public BucketPolicy {
        Objects.requireNonNull(bucketName);
        Objects.requireNonNull(policyJson);
    }

    public boolean hasPolicy() {
        return policyJson != null && !policyJson.isBlank();
    }
}
