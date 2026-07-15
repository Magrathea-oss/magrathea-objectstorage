package com.example.magrathea.storageengine.cluster.application;

import reactor.core.publisher.Mono;

import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static com.example.magrathea.storageengine.cluster.application.ControlPlaneException.Code.INVALID_ACKNOWLEDGEMENT;
import static com.example.magrathea.storageengine.cluster.application.ControlPlaneException.Code.INSUFFICIENT_DURABLE_ACKNOWLEDGEMENTS;

/** Requires exact durable evidence for every fixed EC 4+2 shard before consensus publication. */
public final class EcReferencePublicationService {
    private final ClusterControlPlanePort controlPlane;

    public EcReferencePublicationService(ClusterControlPlanePort controlPlane) {
        this.controlPlane = java.util.Objects.requireNonNull(controlPlane, "controlPlane");
    }

    public Mono<ObjectReferenceGeneration> publish(EcPublicationProposal proposal) {
        return Mono.defer(() -> controlPlane.compareAndPublishEc(validate(proposal)));
    }

    public static EcPublicationProposal validate(EcPublicationProposal proposal) {
        java.util.Objects.requireNonNull(proposal, "proposal");
        validateLayout(proposal);
        Map<String, EcShardReference> byArtifact = new LinkedHashMap<>();
        for (EcShardReference shard : proposal.shards()) {
            if (byArtifact.putIfAbsent(shard.artifactId(), shard) != null) {
                throw new ControlPlaneException(INVALID_ACKNOWLEDGEMENT,
                        "EC artifact IDs must be unique");
            }
        }

        Map<String, ReplicaAcknowledgement> accepted = new LinkedHashMap<>();
        for (ReplicaAcknowledgement acknowledgement : proposal.acknowledgements()) {
            EcShardReference shard = byArtifact.get(acknowledgement.artifactId());
            boolean matches = shard != null
                    && acknowledgement.durable()
                    && proposal.operationId().equals(acknowledgement.operationId())
                    && shard.location().equals(acknowledgement.node())
                    && shard.storedLength() == acknowledgement.length()
                    && shard.sha256().equals(acknowledgement.sha256())
                    && proposal.topologyEpoch().equals(acknowledgement.topologyEpoch())
                    && proposal.policyEpoch().equals(acknowledgement.policyEpoch());
            if (!matches) {
                throw new ControlPlaneException(INVALID_ACKNOWLEDGEMENT,
                        "acknowledgement does not match its fenced EC shard obligation");
            }
            ReplicaAcknowledgement previous = accepted.putIfAbsent(
                    acknowledgement.artifactId(), acknowledgement);
            if (previous != null && !previous.equals(acknowledgement)) {
                throw new ControlPlaneException(INVALID_ACKNOWLEDGEMENT,
                        "conflicting acknowledgement for EC artifact "
                                + acknowledgement.artifactId());
            }
        }
        if (accepted.size() != byArtifact.size()) {
            throw new ControlPlaneException(INSUFFICIENT_DURABLE_ACKNOWLEDGEMENTS,
                    "every fixed EC 4+2 shard requires one exact durable acknowledgement");
        }
        return new EcPublicationProposal(
                proposal.bucket(), proposal.objectKey(), proposal.priorGeneration(),
                proposal.operationId(), proposal.objectLength(), proposal.objectSha256(),
                proposal.topologyEpoch(), proposal.policyEpoch(), proposal.shards(),
                accepted.values().stream().toList(), proposal.metadata());
    }

    public static void validateLayout(EcPublicationProposal proposal) {
        Map<Long, List<EcShardReference>> stripes = proposal.shards().stream()
                .collect(java.util.stream.Collectors.groupingBy(
                        EcShardReference::stripeIndex, java.util.TreeMap::new,
                        java.util.stream.Collectors.toList()));
        if (stripes.size() != 1 || !stripes.containsKey(0L)) {
            throw new ControlPlaneException(INVALID_ACKNOWLEDGEMENT,
                    "bounded distributed EC publication requires exactly one stripe");
        }
        long logicalLength = 0;
        long expectedStripe = 0;
        for (Map.Entry<Long, List<EcShardReference>> entry : stripes.entrySet()) {
            if (entry.getKey() != expectedStripe++) {
                throw new ControlPlaneException(INVALID_ACKNOWLEDGEMENT,
                        "EC stripe indexes must be contiguous from zero");
            }
            List<EcShardReference> stripe = entry.getValue();
            if (stripe.size() != EcShardReference.TOTAL_SHARDS) {
                throw new ControlPlaneException(INSUFFICIENT_DURABLE_ACKNOWLEDGEMENTS,
                        "every EC 4+2 stripe requires six shard obligations");
            }
            Set<Integer> indices = new HashSet<>();
            Set<NodeIdentity> locations = new HashSet<>();
            Map<NodeIdentity, Integer> perNode = new HashMap<>();
            long stripeLength = stripe.get(0).stripeLogicalLength();
            for (EcShardReference shard : stripe) {
                if (!indices.add(shard.shardIndex())
                        || shard.stripeLogicalLength() != stripeLength) {
                    throw new ControlPlaneException(INVALID_ACKNOWLEDGEMENT,
                            "EC stripe metadata is duplicate or contradictory");
                }
                locations.add(shard.location());
                perNode.merge(shard.location(), 1, Integer::sum);
            }
            if (indices.size() != EcShardReference.TOTAL_SHARDS
                    || locations.size() != 3
                    || perNode.values().stream().anyMatch(count -> count != 2)) {
                throw new ControlPlaneException(INVALID_ACKNOWLEDGEMENT,
                        "fixed EC 4+2 requires two shards in each of three failure domains");
            }
            logicalLength = Math.addExact(logicalLength, stripeLength);
        }
        if (logicalLength != proposal.objectLength()) {
            throw new ControlPlaneException(INVALID_ACKNOWLEDGEMENT,
                    "EC shard layout contradicts object logical length");
        }
    }
}
