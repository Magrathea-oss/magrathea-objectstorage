package com.example.magrathea.storageengine.cluster.application;

import reactor.core.publisher.Mono;

import java.util.LinkedHashMap;
import java.util.Map;

import static com.example.magrathea.storageengine.cluster.application.ControlPlaneException.Code.INVALID_ACKNOWLEDGEMENT;
import static com.example.magrathea.storageengine.cluster.application.ControlPlaneException.Code.INSUFFICIENT_DURABLE_ACKNOWLEDGEMENTS;

/** Validates W=2 durable evidence before delegating the fenced compare-and-publish command. */
public final class ReferencePublicationService {
    private static final int WRITE_QUORUM = 2;
    private final ClusterControlPlanePort controlPlane;

    public ReferencePublicationService(ClusterControlPlanePort controlPlane) {
        this.controlPlane = controlPlane;
    }

    public Mono<ObjectReferenceGeneration> publish(PublicationProposal proposal) {
        return Mono.defer(() -> controlPlane.compareAndPublish(validate(proposal)));
    }

    /** Returns a canonical proposal or fails before any control-log command can be constructed. */
    public static PublicationProposal validate(PublicationProposal proposal) {
            Map<NodeIdentity, ReplicaAcknowledgement> unique = new LinkedHashMap<>();
            for (ReplicaAcknowledgement acknowledgement : proposal.acknowledgements()) {
                boolean matches = acknowledgement.durable()
                        && proposal.plannedNodes().contains(acknowledgement.node())
                        && proposal.operationId().equals(acknowledgement.operationId())
                        && proposal.artifactId().equals(acknowledgement.artifactId())
                        && proposal.length() == acknowledgement.length()
                        && proposal.sha256().equals(acknowledgement.sha256())
                        && proposal.topologyEpoch().equals(acknowledgement.topologyEpoch())
                        && proposal.policyEpoch().equals(acknowledgement.policyEpoch());
                if (!matches) {
                    throw new ControlPlaneException(INVALID_ACKNOWLEDGEMENT,
                            "acknowledgement does not match the fenced artifact proposal");
                }
                ReplicaAcknowledgement previous = unique.putIfAbsent(acknowledgement.node(), acknowledgement);
                if (previous != null && !previous.equals(acknowledgement)) {
                    throw new ControlPlaneException(INVALID_ACKNOWLEDGEMENT,
                            "conflicting acknowledgement from " + acknowledgement.node());
                }
            }
            if (unique.size() < WRITE_QUORUM) {
                throw new ControlPlaneException(INSUFFICIENT_DURABLE_ACKNOWLEDGEMENTS,
                        "W=2 unique durable acknowledgements are required");
            }
            return new PublicationProposal(proposal.bucket(), proposal.objectKey(),
                    proposal.priorGeneration(), proposal.operationId(), proposal.artifactId(), proposal.length(),
                    proposal.sha256(), proposal.topologyEpoch(), proposal.policyEpoch(), proposal.plannedNodes(),
                    unique.values().stream().toList(), proposal.metadata());
    }
}
