package com.example.magrathea.storageengine.domain.distributed;

import java.util.Objects;

/** Snapshot of a selected node target for modeled replica placement. */
public record ReplicaTarget(
        String nodeId,
        String failureDomain,
        String diskSetId,
        String storageRoot,
        DistributedNodeHealth health) {

    public ReplicaTarget {
        requireNonBlank(nodeId, "nodeId");
        requireNonBlank(failureDomain, "failureDomain");
        requireNonBlank(diskSetId, "diskSetId");
        requireNonBlank(storageRoot, "storageRoot");
        Objects.requireNonNull(health, "health must not be null");
    }

    public static ReplicaTarget from(DistributedNode node) {
        Objects.requireNonNull(node, "node must not be null");
        return new ReplicaTarget(
                node.nodeId(),
                node.failureDomain(),
                node.diskSetId(),
                node.storageRoot(),
                node.health());
    }

    private static void requireNonBlank(String value, String fieldName) {
        Objects.requireNonNull(value, fieldName + " must not be null");
        if (value.isBlank()) {
            throw new IllegalArgumentException(fieldName + " must not be blank");
        }
    }
}
