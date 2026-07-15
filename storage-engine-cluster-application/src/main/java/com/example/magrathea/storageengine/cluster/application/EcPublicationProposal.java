package com.example.magrathea.storageengine.cluster.application;

import java.util.List;

/** Compare-and-publish input for one complete fixed EC 4+2 object generation. */
public record EcPublicationProposal(
        String bucket,
        String objectKey,
        long priorGeneration,
        String operationId,
        long objectLength,
        String objectSha256,
        String topologyEpoch,
        String policyEpoch,
        List<EcShardReference> shards,
        List<ReplicaAcknowledgement> acknowledgements,
        ClusterObjectMetadata metadata) {

    public EcPublicationProposal {
        if (bucket == null || bucket.isBlank()) throw new IllegalArgumentException("bucket is required");
        if (objectKey == null || objectKey.isBlank()) throw new IllegalArgumentException("object key is required");
        if (priorGeneration < 0) throw new IllegalArgumentException("prior generation cannot be negative");
        if (operationId == null || operationId.isBlank()) throw new IllegalArgumentException("operation ID is required");
        if (objectLength < 1) throw new IllegalArgumentException("object length must be positive");
        if (objectSha256 == null || !objectSha256.matches("[0-9a-f]{64}")) {
            throw new IllegalArgumentException("lowercase object SHA-256 is required");
        }
        if (topologyEpoch == null || topologyEpoch.isBlank()) throw new IllegalArgumentException("topology epoch is required");
        if (policyEpoch == null || policyEpoch.isBlank()) throw new IllegalArgumentException("policy epoch is required");
        if (shards == null || shards.isEmpty()) throw new IllegalArgumentException("EC shard references are required");
        if (acknowledgements == null) throw new IllegalArgumentException("acknowledgements are required");
        shards = shards.stream().sorted().toList();
        acknowledgements = List.copyOf(acknowledgements);
        if (metadata == null) metadata = ClusterObjectMetadata.EMPTY;
    }
}
