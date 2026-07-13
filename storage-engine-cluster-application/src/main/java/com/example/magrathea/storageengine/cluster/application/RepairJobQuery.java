package com.example.magrathea.storageengine.cluster.application;

import java.time.Instant;
import java.util.Set;

/** Deterministic committed repair-list filter; time is caller-supplied query data. */
public record RepairJobQuery(Set<RepairState> states, Instant eligibleAt) {
    public RepairJobQuery {
        states = states == null ? Set.of() : Set.copyOf(states);
    }

    public static RepairJobQuery all() { return new RepairJobQuery(Set.of(), null); }
}
