package com.example.magrathea.storageengine.cluster.application;

import java.util.HexFormat;

/** Metadata for the coordinator's already durable, immutable local artifact. */
public record PreparedArtifact(
        String operationId,
        String artifactId,
        NodeIdentity localNode,
        long length,
        String sha256,
        ClusterObjectMetadata metadata) {
    public PreparedArtifact {
        if (operationId == null || operationId.isBlank()) {
            throw new IllegalArgumentException("operation ID is required");
        }
        if (artifactId == null || artifactId.isBlank()) {
            throw new IllegalArgumentException("artifact ID is required");
        }
        if (localNode == null) throw new IllegalArgumentException("local node is required");
        if (length < 0) throw new IllegalArgumentException("length cannot be negative");
        if (sha256 == null || !sha256.matches("[0-9a-f]{64}")) {
            throw new IllegalArgumentException("lowercase SHA-256 is required");
        }
        if (metadata == null) metadata = ClusterObjectMetadata.EMPTY;
    }

    public PreparedArtifact(
            String operationId,
            String artifactId,
            NodeIdentity localNode,
            long length,
            String sha256) {
        this(operationId, artifactId, localNode, length, sha256, ClusterObjectMetadata.EMPTY);
    }

    public byte[] sha256Bytes() {
        return HexFormat.of().parseHex(sha256);
    }
}
