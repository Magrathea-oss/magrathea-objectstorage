package com.example.magrathea.objectstore.domain.valueobject;

/**
 * BucketObjectLockConfiguration — bucket-level S3 Object Lock default retention.
 *
 * <p>Distinct from per-object {@link ObjectLockConfiguration}: this value object is the
 * bucket-wide default applied by {@code PutObjectLockConfiguration}. When {@code enabled}
 * is false the bucket declares no default retention.</p>
 *
 * Pure domain — NO framework dependencies.
 */
public record BucketObjectLockConfiguration(
    boolean enabled,
    String mode,
    int days
) {
    public static BucketObjectLockConfiguration disabled() {
        return new BucketObjectLockConfiguration(false, null, 0);
    }

    public static BucketObjectLockConfiguration of(String mode, int days) {
        return new BucketObjectLockConfiguration(true, mode, days);
    }
}
