package com.example.magrathea.storageengine.domain.distributed;

/** Modeled status of an observed replica during quorum or anti-entropy validation. */
public enum ReplicaObservationStatus {
    VERIFIED,
    MISSING,
    CORRUPT,
    UNAVAILABLE
}
