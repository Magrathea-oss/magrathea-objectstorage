package com.example.magrathea.objectstorage.domain.valueobject;

import java.util.Objects;

/**
 * AWS S3 bucket request payment configuration — value object.
 * Pure domain — NO framework dependencies.
 */
public record BucketRequestPaymentConfiguration(
    String bucketName,
    String payer
) {
    public BucketRequestPaymentConfiguration {
        Objects.requireNonNull(bucketName);
        // payer: "Requester" or "BucketOwner"
    }
}
