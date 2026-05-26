package com.example.magrathea.objectstore.domain.valueobject;

import java.time.Duration;
import java.util.List;
import java.util.Objects;

/**
 * AWS S3 bucket lifecycle configuration — value object.
 * Pure domain — NO framework dependencies.
 */
public record BucketLifecycleConfiguration(
    String bucketName,
    List<LifecycleRule> rules
) {
    public BucketLifecycleConfiguration {
        Objects.requireNonNull(bucketName);
        if (rules != null) {
            rules = List.copyOf(rules);
        }
    }

    public record LifecycleRule(
        String id,
        String status,
        String prefix,
        Expiration expiration,
        NoncurrentVersionExpiration noncurrentVersionExpiration,
        AbortIncompleteMultipartUpload abortIncompleteMultipartUpload
    ) {
        public LifecycleRule {
            Objects.requireNonNull(status);
            if ("Enabled".equals(status) || "Disabled".equals(status)) {
                // valid status
            }
        }
    }

    public record Expiration(
        String days,
        String date
    ) {}

    public record NoncurrentVersionExpiration(
        String noncurrentDays
    ) {}

    public record AbortIncompleteMultipartUpload(
        String daysAfterInitiation
    ) {}

    public boolean hasRules() {
        return rules != null && !rules.isEmpty();
    }
}
