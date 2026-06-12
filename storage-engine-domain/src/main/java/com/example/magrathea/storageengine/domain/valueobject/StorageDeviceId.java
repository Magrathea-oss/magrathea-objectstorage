package com.example.magrathea.storageengine.domain.valueobject;

import java.util.Objects;

/**
 * Stable unique identifier for a physical storage device.
 *
 * <p>The value must be a non-null, non-blank string. Typical formats are
 * UUIDs, serial numbers, or administrator-assigned names such as
 * {@code "disk-rack1-host2-sda"}. The domain makes no assumption about the
 * format beyond non-blankness.
 */
public record StorageDeviceId(String value) {

    public StorageDeviceId {
        Objects.requireNonNull(value, "StorageDeviceId value must not be null");
        if (value.isBlank()) {
            throw new IllegalArgumentException("StorageDeviceId value must not be blank");
        }
    }

    /**
     * Factory method for readability at call sites.
     *
     * @param value the device identifier string
     * @return a new {@link StorageDeviceId}
     */
    public static StorageDeviceId of(String value) {
        return new StorageDeviceId(value);
    }

    @Override
    public String toString() {
        return "StorageDeviceId[" + value + "]";
    }
}
