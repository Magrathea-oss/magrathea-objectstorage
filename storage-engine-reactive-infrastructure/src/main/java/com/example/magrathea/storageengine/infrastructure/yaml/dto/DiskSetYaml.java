package com.example.magrathea.storageengine.infrastructure.yaml.dto;

import java.util.List;

/**
 * YAML DTO representing a serialised {@code DiskSet}.
 *
 * <p>Public fields are intentional — Jackson binds YAML properties to public fields
 * without requiring getter/setter boilerplate.
 */
public class DiskSetYaml {

    /**
     * Stable disk-set identifier, e.g. {@code "default-diskset"}.
     * Used as the catalog lookup key.
     */
    public String diskSetId;

    /**
     * Failure domain shared by all devices in this set.
     * Recognised values: {@code RACK}, {@code HOST}, {@code DISK}.
     */
    public String failureDomain;

    /**
     * List of device identifiers that belong to this disk set.
     * Each value must correspond to a {@code StorageDeviceId} in the device catalog.
     * At least one entry is required.
     */
    public List<String> deviceIds;
}
