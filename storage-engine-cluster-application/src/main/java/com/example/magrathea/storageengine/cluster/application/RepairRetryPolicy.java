package com.example.magrathea.storageengine.cluster.application;

import java.time.Duration;
import java.time.Instant;

/** Finite retry and claim bounds snapshotted with a repair job. */
public record RepairRetryPolicy(
        int maximumAttempts,
        Duration initialBackoff,
        Duration maximumBackoff,
        Duration maximumClaimDuration) {

    public RepairRetryPolicy {
        if (maximumAttempts < 1) throw new IllegalArgumentException("maximum attempts must be positive");
        requirePositive(initialBackoff, "initial backoff");
        requirePositive(maximumBackoff, "maximum backoff");
        requirePositive(maximumClaimDuration, "maximum claim duration");
        if (maximumBackoff.compareTo(initialBackoff) < 0) {
            throw new IllegalArgumentException("maximum backoff must not be shorter than initial backoff");
        }
    }

    public Instant nextEligibleAt(Instant failedAt, long attemptNumber) {
        if (attemptNumber < 1) throw new IllegalArgumentException("attempt number must be positive");
        long initial = initialBackoff.toMillis();
        long capped = maximumBackoff.toMillis();
        int shifts = (int) Math.min(62, attemptNumber - 1);
        long multiplier = 1L << shifts;
        long delay = initial > Long.MAX_VALUE / multiplier ? capped : Math.min(capped, initial * multiplier);
        return failedAt.plusMillis(delay);
    }

    public boolean permitsAttempt(long attemptNumber) {
        return attemptNumber < maximumAttempts;
    }

    private static void requirePositive(Duration value, String name) {
        if (value == null || value.isZero() || value.isNegative()) {
            throw new IllegalArgumentException(name + " must be positive");
        }
    }
}
