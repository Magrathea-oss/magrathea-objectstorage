package com.example.magrathea.objectstorage.domain.valueobject;

import java.util.List;
import java.util.Objects;

/**
 * AWS S3 bucket inventory configuration — value object.
 * Pure domain — NO framework dependencies.
 */
public record BucketInventoryConfiguration(
    String bucketName,
    String id,
    String destinationBucket,
    String prefix,
    String format,
    String frequency,
    List<String> optionalFields
) {
    public BucketInventoryConfiguration {
        Objects.requireNonNull(bucketName);
        if (optionalFields != null) {
            optionalFields = List.copyOf(optionalFields);
        }
    }

    public boolean hasInventory() {
        return id != null && !id.isBlank();
    }
}
