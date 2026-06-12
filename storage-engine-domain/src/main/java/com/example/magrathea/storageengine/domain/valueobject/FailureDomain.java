package com.example.magrathea.storageengine.domain.valueobject;

/**
 * Failure domain granularity for storage device topology.
 *
 * <p>A failure domain defines the boundary within which a single hardware
 * or network failure can affect multiple storage devices simultaneously.
 * Placement decisions use this to distribute redundant data across independent
 * failure domains.
 *
 * <p>Ordering from broadest to narrowest: {@code RACK} > {@code HOST} > {@code DISK}.
 * Choose the broadest isolation your hardware topology can support while satisfying
 * the required number of independent failure domains.
 */
public enum FailureDomain {

    /**
     * A physical rack or cabinet. Multiple hosts (and their disks) share the same
     * rack-level power and network uplink. Rack-level isolation provides the strongest
     * redundancy guarantee.
     */
    RACK,

    /**
     * A single physical or virtual host/server. Multiple disks may share the same host.
     * Host-level isolation protects against OS, NIC, HBA, or chassis failures.
     */
    HOST,

    /**
     * A single physical disk (drive). Disk-level isolation protects only against
     * individual disk failures; it does not protect against host or rack failures.
     */
    DISK
}
