package com.example.magrathea.objectstore.domain.valueobject;

import java.time.Instant;
import java.util.Objects;

/**
 * LegalHold — a value object representing the legal hold status of an S3Object.
 * <p>
 * When {@code status} is true, the object is under legal hold and cannot be
 * overwritten or deleted. The {@code appliedAt} timestamp records when the
 * hold was applied (or removed).
 * </p>
 * Pure domain — NO framework dependencies.
 */
public record LegalHold(
    boolean status,
    Instant appliedAt
) {

    public LegalHold {
        Objects.requireNonNull(appliedAt);
    }

    /**
     * Factory method — apply a legal hold at the current time.
     *
     * @return a new {@code LegalHold} with status=true and appliedAt=now
     */
    public static LegalHold apply() {
        return new LegalHold(true, Instant.now());
    }

    /**
     * Factory method — remove a legal hold at the given time.
     *
     * @param removedAt the instant when the hold was removed
     * @return a new {@code LegalHold} with status=false
     */
    public static LegalHold remove(Instant removedAt) {
        Objects.requireNonNull(removedAt);
        return new LegalHold(false, removedAt);
    }

    /**
     * Factory method — restore from persistence.
     *
     * @param status    true if legal hold is active
     * @param appliedAt the timestamp of the last status change
     * @return a new {@code LegalHold}
     */
    public static LegalHold restore(boolean status, Instant appliedAt) {
        return new LegalHold(status, appliedAt);
    }
}
