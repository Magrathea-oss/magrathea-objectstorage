package com.example.magrathea.storageengine.cluster.application;

import java.time.Instant;
import java.util.Objects;

/** Auditable accepted command event; rejected stale commands do not enter attempt history. */
public record RepairHistoryEntry(
        String commandId,
        String event,
        RepairState fromState,
        RepairState toState,
        Instant occurredAt,
        long attemptNumber,
        long claimGeneration,
        NodeIdentity owner,
        String processSession,
        String reason,
        NodeIdentity sourceHint) {
    public RepairHistoryEntry {
        if (commandId == null || commandId.isBlank()) throw new IllegalArgumentException("command ID is required");
        if (event == null || event.isBlank()) throw new IllegalArgumentException("event is required");
        Objects.requireNonNull(toState, "toState");
        Objects.requireNonNull(occurredAt, "occurredAt");
        if (attemptNumber < 0 || claimGeneration < 0) throw new IllegalArgumentException("generations must not be negative");
        reason = reason == null ? "" : reason;
        processSession = processSession == null ? "" : processSession;
    }
}
