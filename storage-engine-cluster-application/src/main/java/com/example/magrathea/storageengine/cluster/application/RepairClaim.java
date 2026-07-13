package com.example.magrathea.storageengine.cluster.application;

import java.time.Instant;
import java.util.Objects;

/** Current process-session claim and monotonic fencing token. */
public record RepairClaim(
        NodeIdentity owner,
        String processSession,
        Instant deadline,
        long attemptNumber,
        long claimGeneration) {
    public RepairClaim {
        Objects.requireNonNull(owner, "owner");
        if (processSession == null || processSession.isBlank()) throw new IllegalArgumentException("process session is required");
        Objects.requireNonNull(deadline, "deadline");
        if (attemptNumber < 1 || claimGeneration < 1) throw new IllegalArgumentException("attempt and claim generation must be positive");
    }

    public String token(RepairJobId jobId) { return jobId + "," + claimGeneration; }
}
