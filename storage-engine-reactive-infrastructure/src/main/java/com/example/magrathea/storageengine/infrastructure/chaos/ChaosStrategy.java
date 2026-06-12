package com.example.magrathea.storageengine.infrastructure.chaos;

/**
 * Strategy for fault injection in the storage cluster.
 */
public enum ChaosStrategy {
    /** No fault injection — normal operation. */
    NONE,
    /** Random data corruption on read/write. */
    RANDOM_CORRUPTION,
    /** Nodes randomly go offline. */
    NODE_OFFLINE,
    /** Nodes respond with delays. */
    SLOW_NODE,
    /** All fault types are enabled. */
    ALL
}
