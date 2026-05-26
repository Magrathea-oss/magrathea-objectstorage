package com.example.magrathea.objectstore.domain.valueobject;

import java.time.Duration;
import java.time.Instant;
import java.util.Objects;

/**
 * ObjectLockConfiguration — a value object holding the object lock configuration for an S3Object.
 * <p>
 * Object lock modes:
 * <ul>
 *   <li>{@code GOVERNANCE} — users with s3:PutObjectLockConfiguration and s3:BypassGovernanceRetention
 *       can override the retention period</li>
 *   <li>{@code COMPLIANCE} — no one can override the retention period</li>
 * </ul>
 * </p>
 * Pure domain — NO framework dependencies.
 */
public record ObjectLockConfiguration(
    ObjectLockMode mode,
    RetentionPeriod retention
) {

    public ObjectLockConfiguration {
        Objects.requireNonNull(mode);
        Objects.requireNonNull(retention);
    }

    /**
     * Factory method — create a new lock configuration.
     *
     * @param mode      the lock mode (GOVERNANCE or COMPLIANCE)
     * @param retention the retention period
     * @return a new {@code ObjectLockConfiguration}
     */
    public static ObjectLockConfiguration of(ObjectLockMode mode, RetentionPeriod retention) {
        return new ObjectLockConfiguration(mode, retention);
    }

    /**
     * Object lock modes as defined by AWS S3 Object Lock.
     */
    public enum ObjectLockMode {
        GOVERNANCE,
        COMPLIANCE
    }

    /**
     * RetentionPeriod — a value object holding a retention duration and when it was applied.
     */
    public record RetentionPeriod(
        Duration duration,
        Instant appliedAt
    ) {

        public RetentionPeriod {
            Objects.requireNonNull(duration);
            Objects.requireNonNull(appliedAt);
            if (duration.isNegative() || duration.isZero()) {
                throw new IllegalArgumentException("Retention duration must be positive");
            }
        }

        /**
         * Factory method — create a retention period from a positive duration.
         *
         * @param duration  the retention duration (must be positive)
         * @param appliedAt the instant when the retention was applied
         * @return a new {@code RetentionPeriod}
         */
        public static RetentionPeriod of(Duration duration, Instant appliedAt) {
            return new RetentionPeriod(duration, appliedAt);
        }

        /**
         * Factory method — create a retention period starting now.
         *
         * @param duration the retention duration (must be positive)
         * @return a new {@code RetentionPeriod}
         */
        public static RetentionPeriod startingNow(Duration duration) {
            Objects.requireNonNull(duration);
            if (duration.isNegative() || duration.isZero()) {
                throw new IllegalArgumentException("Retention duration must be positive");
            }
            return new RetentionPeriod(duration, Instant.now());
        }
    }
}
