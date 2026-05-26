package com.example.magrathea.objectstore.domain.valueobject;

import java.time.Instant;
import java.util.Objects;

/**
 * RestoreConfiguration — a value object for restoring an archived (Glacier/Deep Archive) S3Object.
 * <p>
 * Tracks when the restore was requested, when it expires, and the requested restore tier.
 * </p>
 * Pure domain — NO framework dependencies.
 */
public record RestoreConfiguration(
    Instant restoreRequestedAt,
    Instant expirationAt,
    RestoreTier tier
) {

    public RestoreConfiguration {
        Objects.requireNonNull(restoreRequestedAt);
        Objects.requireNonNull(expirationAt);
        Objects.requireNonNull(tier);
        if (expirationAt.isBefore(restoreRequestedAt)) {
            throw new IllegalArgumentException("expirationAt must be after restoreRequestedAt");
        }
    }

    /**
     * Factory method — create a new restore configuration.
     *
     * @param restoreRequestedAt when the restore was requested
     * @param expirationAt       when the restored copy expires
     * @param tier               the restore tier (STANDARD, BULK, or EXPEDITED)
     * @return a new {@code RestoreConfiguration}
     */
    public static RestoreConfiguration of(Instant restoreRequestedAt, Instant expirationAt, RestoreTier tier) {
        return new RestoreConfiguration(restoreRequestedAt, expirationAt, tier);
    }

    /**
     * Factory method — restore from persistence.
     *
     * @param restoreRequestedAt when the restore was requested
     * @param expirationAt       when the restored copy expires
     * @param tier               the restore tier
     * @return a new {@code RestoreConfiguration}
     */
    public static RestoreConfiguration restore(Instant restoreRequestedAt, Instant expirationAt, RestoreTier tier) {
        return new RestoreConfiguration(restoreRequestedAt, expirationAt, tier);
    }

    /**
     * AWS S3 restore tiers for Glacier/Deep Archive restoration.
     */
    public enum RestoreTier {
        STANDARD,
        BULK,
        EXPEDITED
    }
}
