package com.example.magrathea.objectstore.domain.valueobject;

/**
 * BucketInventoryTableConfiguration — bucket metadata inventory-table settings exposed
 * through the extended S3 bucket configuration API (inventory-table-config).
 *
 * Pure domain — NO framework dependencies.
 */
public record BucketInventoryTableConfiguration(
    String id,
    String destinationFormat,
    String scheduleFrequency,
    boolean enabled
) {
    public static BucketInventoryTableConfiguration of(
            String id, String destinationFormat, String scheduleFrequency, boolean enabled) {
        return new BucketInventoryTableConfiguration(id, destinationFormat, scheduleFrequency, enabled);
    }
}
