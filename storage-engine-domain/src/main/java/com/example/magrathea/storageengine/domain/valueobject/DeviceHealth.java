package com.example.magrathea.storageengine.domain.valueobject;

/**
 * Health status of a physical storage device.
 *
 * <p>Valid transitions are:
 * <ul>
 *   <li>{@code HEALTHY} → {@code DEGRADED} (partial failure detected)</li>
 *   <li>{@code HEALTHY} → {@code UNAVAILABLE} (catastrophic failure or offline)</li>
 *   <li>{@code DEGRADED} → {@code UNAVAILABLE} (degraded device becomes unavailable)</li>
 *   <li>{@code DEGRADED} → {@code HEALTHY} (recovery after repair/rebuild)</li>
 *   <li>{@code UNAVAILABLE} → {@code HEALTHY} (device brought back online and verified)</li>
 * </ul>
 *
 * <p>Transitions are enforced by {@link StorageDevice#withHealth(DeviceHealth)}
 * which validates the requested transition and returns a new immutable instance.
 */
public enum DeviceHealth {

    /**
     * Device is fully operational. Reads and writes may proceed normally.
     */
    HEALTHY,

    /**
     * Device is partially operational. Performance may be reduced and the
     * device requires attention. Reads may proceed but new writes should
     * prefer healthier devices when alternatives are available.
     */
    DEGRADED,

    /**
     * Device is not operational and cannot serve reads or writes.
     * The device must be repaired or replaced before returning to service.
     */
    UNAVAILABLE;

    /**
     * Returns {@code true} if transitioning from this health status to
     * {@code target} is a permitted transition.
     *
     * @param target the desired new health status
     * @return {@code true} when the transition is allowed
     */
    public boolean canTransitionTo(DeviceHealth target) {
        if (this == target) {
            return false; // self-transition is a no-op, not a legal transition
        }
        return switch (this) {
            case HEALTHY -> target == DEGRADED || target == UNAVAILABLE;
            case DEGRADED -> target == HEALTHY || target == UNAVAILABLE;
            case UNAVAILABLE -> target == HEALTHY; // recovery path only
        };
    }
}
