package com.example.magrathea.storageengine.cluster.application;

/** Consensus-owned lifecycle of one current-generation replica repair obligation. */
public enum RepairState {
    READY,
    CLAIMED,
    RETRY_WAIT,
    BLOCKED,
    SUCCEEDED,
    OBSOLETE
}
