package com.example.magrathea.storageengine.cluster.application;

/** Durable evidence emitted by a selected replica after exact artifact validation. */
public record ReplicaAcknowledgement(
        String operationId,
        String artifactId,
        NodeIdentity node,
        long length,
        String sha256,
        String topologyEpoch,
        String policyEpoch,
        boolean durable) {
    public ReplicaAcknowledgement {
        if (operationId == null || operationId.isBlank()) throw new IllegalArgumentException("operation ID is required");
        if (artifactId == null || artifactId.isBlank()) throw new IllegalArgumentException("artifact ID is required");
        if (node == null) throw new IllegalArgumentException("node is required");
        if (length < 0) throw new IllegalArgumentException("length cannot be negative");
        if (sha256 == null || !sha256.matches("[0-9a-f]{64}")) throw new IllegalArgumentException("lowercase SHA-256 is required");
        if (topologyEpoch == null || topologyEpoch.isBlank()) throw new IllegalArgumentException("topology epoch is required");
        if (policyEpoch == null || policyEpoch.isBlank()) throw new IllegalArgumentException("policy epoch is required");
    }
}
