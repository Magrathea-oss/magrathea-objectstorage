package com.example.magrathea.storageengine.cluster.application;

import java.util.Objects;
import java.util.UUID;

/** Stable, persisted identity of one cluster voter. */
public record NodeIdentity(UUID value) implements Comparable<NodeIdentity> {
    public NodeIdentity {
        Objects.requireNonNull(value, "value");
    }

    public static NodeIdentity parse(String value) {
        return new NodeIdentity(UUID.fromString(value));
    }

    @Override
    public int compareTo(NodeIdentity other) {
        return value.compareTo(other.value);
    }

    @Override
    public String toString() {
        return value.toString();
    }
}
