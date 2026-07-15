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
        ClusterObjectMetadata metadata,
        ClusterStorageLayout storageLayout,
        List<EcShardReference> ecShards) {

    public ObjectReferenceGeneration {
        if (bucket == null || bucket.isBlank()) throw new IllegalArgumentException("bucket is required");
        if (objectKey == null || objectKey.isBlank()) throw new IllegalArgumentException("object key is required");
        if (generation < 1) throw new IllegalArgumentException("generation must be positive");
        if (operationId == null || operationId.isBlank()) throw new IllegalArgumentException("operation ID is required");
        if (artifactId == null || artifactId.isBlank()) throw new IllegalArgumentException("reference artifact ID is required");
        if (length < 0) throw new IllegalArgumentException("length cannot be negative");
        if (sha256 == null || !sha256.matches("[0-9a-f]{64}")) {
            throw new IllegalArgumentException("lowercase SHA-256 is required");
        }
        replicas = List.copyOf(replicas);
        if (replicas.size() < 2 || replicas.stream().distinct().count() != replicas.size()) {
            throw new IllegalArgumentException("at least two unique replica locations are required");
        }
        if (metadata == null) metadata = ClusterObjectMetadata.EMPTY;
        if (storageLayout == null) storageLayout = ClusterStorageLayout.WHOLE_OBJECT_REPLICATED;
        ecShards = ecShards == null ? List.of() : ecShards.stream().sorted().toList();
        if (storageLayout == ClusterStorageLayout.WHOLE_OBJECT_REPLICATED && !ecShards.isEmpty()) {
            throw new IllegalArgumentException("whole-object references cannot contain EC shards");
        }
        if (storageLayout == ClusterStorageLayout.EC_4_2) {
            if (ecShards.isEmpty()) throw new IllegalArgumentException("EC_4_2 references require shard facts");
            if (!replicas.equals(ecShards.stream().map(EcShardReference::location)
                    .distinct().sorted().toList())) {
                throw new IllegalArgumentException("EC replica locations must equal shard locations");
            }
        }
    }

    /** Compatibility constructor for whole-object references. */
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
            List<NodeIdentity> replicas,
            ClusterObjectMetadata metadata) {
        this(bucket, objectKey, generation, operationId, artifactId, length, sha256,
                topologyEpoch, policyEpoch, replicas, metadata,
                ClusterStorageLayout.WHOLE_OBJECT_REPLICATED, List.of());
    }

    /** Compatibility constructor for historical whole-object callers. */
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

    public static ObjectReferenceGeneration ec42(
            String bucket,
            String objectKey,
            long generation,
            String operationId,
            long objectLength,
            String objectSha256,
            String topologyEpoch,
            String policyEpoch,
            List<EcShardReference> shards,
            ClusterObjectMetadata metadata) {
        List<EcShardReference> ordered = shards.stream().sorted().toList();
        List<NodeIdentity> locations = ordered.stream().map(EcShardReference::location)
                .distinct().sorted().toList();
        return new ObjectReferenceGeneration(
                bucket, objectKey, generation, operationId,
                "ec-4-2:" + operationId, objectLength, objectSha256,
                topologyEpoch, policyEpoch, locations, metadata,
                ClusterStorageLayout.EC_4_2, ordered);
    }

    public boolean erasureCoded() {
        return storageLayout == ClusterStorageLayout.EC_4_2;
    }

    public String namespaceKey() {
        return bucket + "\u0000" + objectKey;
    }
}
