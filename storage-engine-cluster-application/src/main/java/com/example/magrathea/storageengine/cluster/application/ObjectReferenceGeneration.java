package com.example.magrathea.storageengine.cluster.application;

import java.util.List;

/** Consensus-authoritative reference; it names immutable artifacts and never carries payload bytes. */
public record ObjectReferenceGeneration(
        String bucket,
        String objectKey,
        long generation,
        String operationId,
        String artifactId,
        long length,
        String sha256,
        String topologyEpoch,
        String policyEpoch,
        List<NodeIdentity> replicas,
        ClusterObjectMetadata metadata) {
    public ObjectReferenceGeneration {
        if (bucket == null || bucket.isBlank()) throw new IllegalArgumentException("bucket is required");
        if (objectKey == null || objectKey.isBlank()) throw new IllegalArgumentException("object key is required");
        if (generation < 1) throw new IllegalArgumentException("generation must be positive");
        replicas = List.copyOf(replicas);
        if (replicas.size() < 2 || replicas.stream().distinct().count() != replicas.size()) {
            throw new IllegalArgumentException("at least two unique replicas are required");
        }
        if (metadata == null) metadata = ClusterObjectMetadata.EMPTY;
    }

    public ObjectReferenceGeneration(
            String bucket,
            String objectKey,
            long generation,
            String operationId,
            String artifactId,
            long length,
            String sha256,
            String topologyEpoch,
            String policyEpoch,
            List<NodeIdentity> replicas) {
        this(bucket, objectKey, generation, operationId, artifactId, length, sha256,
                topologyEpoch, policyEpoch, replicas, ClusterObjectMetadata.EMPTY);
    }

    public String namespaceKey() {
        return bucket + "\u0000" + objectKey;
    }
}
