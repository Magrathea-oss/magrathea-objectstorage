package com.example.magrathea.storageengine.cluster.application;

import java.time.Instant;
import java.util.List;
import java.util.Objects;

/** Immutable query view of one consensus-owned repair job. */
public record RepairJob(
        RepairSpecification specification,
        RepairState state,
        long attemptNumber,
        long claimGeneration,
        RepairClaim claim,
        Instant nextEligibleAt,
        String reason,
        Instant createdAt,
        Instant updatedAt,
        List<RepairHistoryEntry> history,
        List<RepairCommandResult> commandResults) {
    public RepairJob {
        Objects.requireNonNull(specification, "specification");
        Objects.requireNonNull(state, "state");
        Objects.requireNonNull(createdAt, "createdAt");
        Objects.requireNonNull(updatedAt, "updatedAt");
        history = List.copyOf(history);
        commandResults = List.copyOf(commandResults);
        reason = reason == null ? "" : reason;
        if (attemptNumber < 0 || claimGeneration < 0) throw new IllegalArgumentException("generations must not be negative");
        if ((state == RepairState.CLAIMED) != (claim != null)) throw new IllegalArgumentException("only CLAIMED jobs carry a claim");
        if ((state == RepairState.RETRY_WAIT) != (nextEligibleAt != null)) throw new IllegalArgumentException("only RETRY_WAIT jobs carry retry eligibility");
        if (claim != null && (claim.attemptNumber() != attemptNumber || claim.claimGeneration() != claimGeneration)) {
            throw new IllegalArgumentException("claim generations must match job generations");
        }
    }

    public RepairJobId jobId() { return specification.jobId(); }
}
