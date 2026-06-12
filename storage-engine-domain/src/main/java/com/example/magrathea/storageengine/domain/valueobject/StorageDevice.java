package com.example.magrathea.storageengine.domain.valueobject;

import java.util.Objects;

/**
 * Domain value object representing a physical storage device.
 *
 * <p>Intentionally uses a plain {@code String} for {@code storagePath} rather than
 * {@code java.nio.file.Path} to keep the domain layer free of I/O infrastructure
 * concerns. The path is validated for non-null and non-blank at construction time.
 *
 * <p>This record is immutable. Health state transitions are performed through
 * {@link #withHealth(DeviceHealth)}, which returns a new instance with the
 * updated health and validates the transition is legal according to
 * {@link DeviceHealth#canTransitionTo(DeviceHealth)}.
 */
public record StorageDevice(
        StorageDeviceId id,
        String storagePath,
        long totalCapacityBytes,
        long availableCapacityBytes,
        DeviceHealth health) {

    public StorageDevice {
        Objects.requireNonNull(id, "id must not be null");
        Objects.requireNonNull(storagePath, "storagePath must not be null");
        if (storagePath.isBlank()) {
            throw new IllegalArgumentException(
                    "storagePath must not be blank for device: " + id);
        }
        Objects.requireNonNull(health, "health must not be null");
        if (totalCapacityBytes < 0) {
            throw new IllegalArgumentException(
                    "totalCapacityBytes must be >= 0 for device " + id + ": " + totalCapacityBytes);
        }
        if (availableCapacityBytes < 0) {
            throw new IllegalArgumentException(
                    "availableCapacityBytes must be >= 0 for device " + id + ": " + availableCapacityBytes);
        }
        if (availableCapacityBytes > totalCapacityBytes) {
            throw new IllegalArgumentException(
                    "availableCapacityBytes (" + availableCapacityBytes
                    + ") must not exceed totalCapacityBytes (" + totalCapacityBytes
                    + ") for device " + id);
        }
    }

    /**
     * Factory method for constructing a healthy device with full capacity available.
     *
     * @param id           the device identifier
     * @param storagePath  filesystem path to the storage root (domain String, not Path)
     * @param totalBytes   total capacity in bytes
     * @return a {@link StorageDevice} with {@link DeviceHealth#HEALTHY} status
     */
    public static StorageDevice create(StorageDeviceId id, String storagePath, long totalBytes) {
        return new StorageDevice(id, storagePath, totalBytes, totalBytes, DeviceHealth.HEALTHY);
    }

    /**
     * Factory method for restoring a device from persisted state.
     *
     * @param id                  the device identifier
     * @param storagePath         filesystem path to the storage root
     * @param totalCapacityBytes  total capacity in bytes
     * @param availableBytes      available (unused) capacity in bytes
     * @param health              the persisted health status
     * @return a restored {@link StorageDevice}
     */
    public static StorageDevice restore(
            StorageDeviceId id,
            String storagePath,
            long totalCapacityBytes,
            long availableBytes,
            DeviceHealth health) {
        return new StorageDevice(id, storagePath, totalCapacityBytes, availableBytes, health);
    }

    /**
     * Returns a new {@link StorageDevice} with the updated health status.
     *
     * <p>Only valid health transitions as defined by
     * {@link DeviceHealth#canTransitionTo(DeviceHealth)} are permitted.
     *
     * @param newHealth the desired new health status
     * @return a new immutable {@link StorageDevice} with the updated health
     * @throws IllegalArgumentException if the transition is not permitted
     */
    public StorageDevice withHealth(DeviceHealth newHealth) {
        Objects.requireNonNull(newHealth, "newHealth must not be null");
        if (!health.canTransitionTo(newHealth)) {
            throw new IllegalArgumentException(
                    "Invalid health transition for device " + id
                    + ": " + health + " → " + newHealth + " is not a permitted transition.");
        }
        return new StorageDevice(id, storagePath, totalCapacityBytes, availableCapacityBytes, newHealth);
    }

    /**
     * Returns {@code true} if this device is eligible to accept new write operations.
     * Only {@link DeviceHealth#HEALTHY} devices are considered write-eligible.
     */
    public boolean isWriteEligible() {
        return health == DeviceHealth.HEALTHY;
    }

    /**
     * Returns {@code true} if this device can serve read operations.
     * Both {@link DeviceHealth#HEALTHY} and {@link DeviceHealth#DEGRADED} devices
     * may serve reads; {@link DeviceHealth#UNAVAILABLE} devices cannot.
     */
    public boolean isReadEligible() {
        return health != DeviceHealth.UNAVAILABLE;
    }

    @Override
    public String toString() {
        return "StorageDevice[" + id.value() + " path=" + storagePath
               + " capacity=" + totalCapacityBytes + " avail=" + availableCapacityBytes
               + " health=" + health + "]";
    }
}
