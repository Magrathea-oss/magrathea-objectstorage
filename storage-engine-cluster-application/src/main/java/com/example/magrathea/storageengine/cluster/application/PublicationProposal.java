package com.example.magrathea.storageengine.cluster.application;

import java.util.List;
import java.util.Set;

/** Compare-and-publish input assembled only after direct data transfer. */
public record PublicationProposal(
        String bucket,
        String objectKey,
        long priorGeneration,
        String operationId,
        String artifactId,
        long length,
        String sha256,
        String topologyEpoch,
        String policyEpoch,
        Set<NodeIdentity> plannedNodes,
        List<ReplicaAcknowledgement> acknowledgements,
        ClusterObjectMetadata metadata) {
    public PublicationProposal {
        plannedNodes = Set.copyOf(plannedNodes);
        acknowledgements = List.copyOf(acknowledgements);
        if (plannedNodes.size() != 3) throw new IllegalArgumentException("N=3 requires three unique planned nodes");
        if (priorGeneration < 0) throw new IllegalArgumentException("prior generation cannot be negative");
        if (metadata == null) metadata = ClusterObjectMetadata.EMPTY;
    }

    public PublicationProposal(
            String bucket,
            String objectKey,
            long priorGeneration,
            String operationId,
            String artifactId,
            long length,
            String sha256,
            String topologyEpoch,
            String policyEpoch,
            Set<NodeIdentity> plannedNodes,
            List<ReplicaAcknowledgement> acknowledgements) {
        this(bucket, objectKey, priorGeneration, operationId, artifactId, length, sha256,
                topologyEpoch, policyEpoch, plannedNodes, acknowledgements, ClusterObjectMetadata.EMPTY);
    }
}
