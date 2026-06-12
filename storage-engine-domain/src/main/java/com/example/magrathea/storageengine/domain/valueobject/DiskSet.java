package com.example.magrathea.storageengine.domain.valueobject;

import java.util.List;
import java.util.Objects;

/**
 * A named, ordered collection of {@link StorageDeviceId} references that share
 * a common {@link FailureDomain} label.
 *
 * <p>A {@link DiskSet} is the logical grouping unit used for placement decisions:
 * data that must survive the failure of an entire failure domain is spread across
 * multiple disk sets, each in an independent failure domain.
 *
 * <p>Invariants:
 * <ul>
 *   <li>{@code name} must be non-null and non-blank.</li>
 *   <li>{@code failureDomain} must be non-null.</li>
 *   <li>{@code devices} must contain at least one device ID.</li>
 *   <li>The returned {@code devices} list is immutable; mutations are rejected.</li>
 * </ul>
 */
public record DiskSet(
        String name,
        FailureDomain failureDomain,
        List<StorageDeviceId> devices) {

    public DiskSet {
        Objects.requireNonNull(name, "name must not be null");
        if (name.isBlank()) {
            throw new IllegalArgumentException("DiskSet name must not be blank");
        }
        Objects.requireNonNull(failureDomain, "failureDomain must not be null");
        Objects.requireNonNull(devices, "devices must not be null");
        if (devices.isEmpty()) {
            throw new IllegalArgumentException(
                    "DiskSet '" + name + "' must contain at least one device; found 0");
        }
        // Defensive copy: public API must never expose a mutable collection.
        devices = List.copyOf(devices);
    }

    /**
     * Factory method for constructing a {@link DiskSet} with explicit parameters.
     *
     * @param name          the human-readable set name (e.g., {@code "rack-1-set"})
     * @param failureDomain the failure domain all devices in this set share
     * @param devices       the device IDs in this set (at least one required)
     * @return a new immutable {@link DiskSet}
     */
    public static DiskSet of(String name, FailureDomain failureDomain, List<StorageDeviceId> devices) {
        return new DiskSet(name, failureDomain, devices);
    }

    /**
     * Returns the number of devices in this set.
     */
    public int size() {
        return devices.size();
    }

    /**
     * Returns {@code true} if the specified device ID is a member of this set.
     */
    public boolean contains(StorageDeviceId deviceId) {
        return devices.contains(deviceId);
    }

    @Override
    public String toString() {
        return "DiskSet[" + name + " domain=" + failureDomain + " size=" + devices.size() + "]";
    }
}
