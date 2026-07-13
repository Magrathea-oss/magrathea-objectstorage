package com.example.magrathea.storageengine.cluster.application;

import java.time.Instant;
import java.util.Objects;

/** Deterministic committed result retained for duplicate-command replay. */
public record RepairCommandResult(
        String commandId,
        RepairJobId jobId,
        boolean accepted,
        Code code,
        RepairState state,
        long attemptNumber,
        long claimGeneration,
        Instant occurredAt,
        String reason) {

    public enum Code {
        APPLIED,
        NO_CHANGE,
        STALE_TOKEN,
        ILLEGAL_TRANSITION,
        INVALID_REFERENCE,
        INVALID_DEADLINE,
        RETRY_EXHAUSTED,
        NOT_FOUND
    }

    public RepairCommandResult {
        if (commandId == null || commandId.isBlank()) throw new IllegalArgumentException("command ID is required");
        Objects.requireNonNull(jobId, "jobId");
        Objects.requireNonNull(code, "code");
        Objects.requireNonNull(occurredAt, "occurredAt");
        if (attemptNumber < 0 || claimGeneration < 0) throw new IllegalArgumentException("generations must not be negative");
        reason = reason == null ? "" : reason;
    }
}
