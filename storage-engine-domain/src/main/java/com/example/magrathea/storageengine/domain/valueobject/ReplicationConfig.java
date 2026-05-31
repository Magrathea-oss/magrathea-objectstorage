package com.example.magrathea.storageengine.domain.valueobject;

public record ReplicationConfig(int factor) {
    public ReplicationConfig {
        if (factor < 1) {
            throw new IllegalArgumentException("factor must be >= 1: " + factor);
        }
    }

    public static ReplicationConfig of(int factor) {
        return new ReplicationConfig(factor);
    }
}
