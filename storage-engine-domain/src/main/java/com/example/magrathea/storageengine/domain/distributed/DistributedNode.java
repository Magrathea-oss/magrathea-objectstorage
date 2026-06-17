package com.example.magrathea.storageengine.domain.distributed;

import java.util.Objects;
import java.util.Optional;

/**
 * Immutable modeled member of a distributed storage topology.
 */
public record DistributedNode(
        String nodeId,
        String failureDomain,
        String diskSetId,
        String storageRoot,
        DistributedNodeHealth health,
        Optional<String> healthReason) {

    public DistributedNode {
        requireNonBlank(nodeId, "nodeId");
        requireNonBlank(failureDomain, "failureDomain");
        requireNonBlank(diskSetId, "diskSetId");
        requireNonBlank(storageRoot, "storageRoot");
        Objects.requireNonNull(health, "health must not be null");
        Objects.requireNonNull(healthReason, "healthReason must not be null");
        healthReason = healthReason.filter(reason -> !reason.isBlank());
    }

    public static DistributedNode of(
            String nodeId,
            String failureDomain,
            String diskSetId,
            String storageRoot,
            DistributedNodeHealth health) {
        return new DistributedNode(nodeId, failureDomain, diskSetId, storageRoot, health, Optional.empty());
    }

    public static DistributedNode withReason(
            String nodeId,
            String failureDomain,
            String diskSetId,
            String storageRoot,
            DistributedNodeHealth health,
            String healthReason) {
        return new DistributedNode(nodeId, failureDomain, diskSetId, storageRoot, health, Optional.ofNullable(healthReason));
    }

    public boolean isHealthy() {
        return health == DistributedNodeHealth.HEALTHY;
    }

    public boolean isDegraded() {
        return health == DistributedNodeHealth.DEGRADED;
    }

    public boolean isDown() {
        return health == DistributedNodeHealth.DOWN;
    }

    private static void requireNonBlank(String value, String fieldName) {
        Objects.requireNonNull(value, fieldName + " must not be null");
        if (value.isBlank()) {
            throw new IllegalArgumentException(fieldName + " must not be blank");
        }
    }
}
