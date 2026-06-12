package com.example.magrathea.storageengine.infrastructure.yaml.dto;

/**
 * YAML DTO representing a serialised {@code StorageDevice}.
 *
 * <p>Public fields are intentional — Jackson binds YAML properties to public fields
 * without requiring getter/setter boilerplate.
 */
public class StorageDeviceYaml {

    /**
     * Stable device identifier, e.g. {@code "node-1-disk-0"}.
     * Maps to {@code StorageDeviceId.value()}.
     */
    public String deviceId;

    /**
     * Filesystem path to the storage root, e.g. {@code "/data/node-1/disk-0"}.
     * Domain-layer string; not interpreted as {@code java.nio.file.Path} here.
     */
    public String storagePath;

    /** Total capacity in bytes. */
    public long totalCapacityBytes;

    /**
     * Available (free) capacity in bytes.
     * Defaults to {@code totalCapacityBytes} when not specified.
     */
    public long availableCapacityBytes = -1L; // sentinel — resolved at mapping time

    /**
     * Device health status.
     * Recognised values: {@code HEALTHY}, {@code DEGRADED}, {@code UNAVAILABLE}.
     * Defaults to {@code HEALTHY}.
     */
    public String health = "HEALTHY";

    /**
     * Failure domain of this device.
     * Recognised values: {@code RACK}, {@code HOST}, {@code DISK}.
     * Informational only — not persisted in the domain record.
     */
    public String failureDomain = "DISK";
}
