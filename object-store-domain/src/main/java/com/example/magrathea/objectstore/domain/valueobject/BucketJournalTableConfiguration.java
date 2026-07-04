package com.example.magrathea.objectstore.domain.valueobject;

/**
 * BucketJournalTableConfiguration — bucket metadata journal-table settings exposed
 * through the extended S3 bucket configuration API (journal-table-config).
 *
 * Pure domain — NO framework dependencies.
 */
public record BucketJournalTableConfiguration(
    String id,
    String destinationFormat,
    String scheduleFrequency,
    boolean enabled
) {
    public static BucketJournalTableConfiguration of(
            String id, String destinationFormat, String scheduleFrequency, boolean enabled) {
        return new BucketJournalTableConfiguration(id, destinationFormat, scheduleFrequency, enabled);
    }
}
