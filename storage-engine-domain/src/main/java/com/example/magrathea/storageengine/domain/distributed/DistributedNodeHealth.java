package com.example.magrathea.storageengine.domain.distributed;

/** Health snapshot used by modeled distributed storage decisions. */
public enum DistributedNodeHealth {
    HEALTHY,
    DEGRADED,
    DOWN
}
