package com.example.magrathea.storageengine.cluster.application;

import com.example.magrathea.storageengine.domain.distributed.DistributedNode;
import com.example.magrathea.storageengine.domain.distributed.DistributedNodeHealth;
import com.example.magrathea.storageengine.domain.distributed.DistributedPlacementPlanner;
import com.example.magrathea.storageengine.domain.distributed.DistributedTopology;
import com.example.magrathea.storageengine.domain.distributed.PlacementDecision;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import reactor.core.scheduler.Schedulers;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.CompletionStage;

/**
 * Coordinates one fixed N=3/W=2 whole-object write without knowing Ratis, gRPC, or filesystem types.
 *
 * <p>The caller first durably prepares one immutable local artifact. This use case resolves committed
 * membership and the current generation, invokes the existing PA-6 planner, verifies the local source,
 * independently opens that same published artifact for each selected remote transfer, collects only
 * matching durable evidence, revalidates fencing, and only then asks the publication service to commit.
 */
public final class ClusterWriteCoordinator {
    private static final int REPLICATION_FACTOR = 3;
    private static final int MAX_PARALLEL_TRANSFERS = 2;
    private static final int CHECKSUM_BUFFER_BYTES = 65_536;

    private final NodeIdentity coordinator;
    private final ClusterControlPlanePort controlPlane;
    private final LocalArtifactPort localArtifacts;
    private final ReplicaTransferPort replicaTransfers;
    private final DistributedPlacementPlanner placementPlanner;
    private final ReferencePublicationService publicationService;
    private final ReferencePublicationBarrier publicationBarrier;
    private final Duration transferDeadline;

    public ClusterWriteCoordinator(
            NodeIdentity coordinator,
            ClusterControlPlanePort controlPlane,
            LocalArtifactPort localArtifacts,
            ReplicaTransferPort replicaTransfers,
            Duration transferDeadline) {
        this(coordinator, controlPlane, localArtifacts, replicaTransfers,
                new DistributedPlacementPlanner(), new ReferencePublicationService(controlPlane),
                ReferencePublicationBarrier.none(), transferDeadline);
    }

    public ClusterWriteCoordinator(
            NodeIdentity coordinator,
            ClusterControlPlanePort controlPlane,
            LocalArtifactPort localArtifacts,
            ReplicaTransferPort replicaTransfers,
            Duration transferDeadline,
            ReferencePublicationBarrier publicationBarrier) {
        this(coordinator, controlPlane, localArtifacts, replicaTransfers,
                new DistributedPlacementPlanner(), new ReferencePublicationService(controlPlane),
                publicationBarrier, transferDeadline);
    }

    public ClusterWriteCoordinator(
            NodeIdentity coordinator,
            ClusterControlPlanePort controlPlane,
            LocalArtifactPort localArtifacts,
            ReplicaTransferPort replicaTransfers,
            DistributedPlacementPlanner placementPlanner,
            ReferencePublicationService publicationService,
            Duration transferDeadline) {
        this(coordinator, controlPlane, localArtifacts, replicaTransfers, placementPlanner,
                publicationService, ReferencePublicationBarrier.none(), transferDeadline);
    }

    public ClusterWriteCoordinator(
            NodeIdentity coordinator,
            ClusterControlPlanePort controlPlane,
            LocalArtifactPort localArtifacts,
            ReplicaTransferPort replicaTransfers,
            DistributedPlacementPlanner placementPlanner,
            ReferencePublicationService publicationService,
            ReferencePublicationBarrier publicationBarrier,
            Duration transferDeadline) {
        this.coordinator = Objects.requireNonNull(coordinator, "coordinator");
        this.controlPlane = Objects.requireNonNull(controlPlane, "controlPlane");
        this.localArtifacts = Objects.requireNonNull(localArtifacts, "localArtifacts");
        this.replicaTransfers = Objects.requireNonNull(replicaTransfers, "replicaTransfers");
        this.placementPlanner = Objects.requireNonNull(placementPlanner, "placementPlanner");
        this.publicationService = Objects.requireNonNull(publicationService, "publicationService");
        this.publicationBarrier = Objects.requireNonNull(publicationBarrier, "publicationBarrier");
        this.transferDeadline = Objects.requireNonNull(transferDeadline, "transferDeadline");
        if (transferDeadline.isNegative() || transferDeadline.isZero()) {
            throw new IllegalArgumentException("transfer deadline must be positive");
        }
    }

    /** Performs placement and direct transfer while leaving the artifacts non-authoritative. */
    public Mono<PreparedPublication> prepare(String bucket, String objectKey, PreparedArtifact artifact) {
        requireNonBlank(bucket, "bucket");
        requireNonBlank(objectKey, "objectKey");
        Objects.requireNonNull(artifact, "artifact");
        if (!coordinator.equals(artifact.localNode())) {
            return Mono.error(new IllegalArgumentException("prepared artifact is not durable on the coordinator"));
        }

        return controlPlane.membership()
                .flatMap(snapshot -> currentGeneration(bucket, objectKey)
                        .map(prior -> context(bucket, objectKey, artifact, prior, snapshot)))
                .flatMap(context -> verifiedLocalAcknowledgement(context)
                        .flatMapMany(local -> remoteAcknowledgements(context)
                                .startWith(local))
                        .collectList()
                        .map(acks -> preparedPublication(context, acks)));
    }

    /** Revalidates committed fencing and returns only after consensus publication. */
    public Mono<ObjectReferenceGeneration> publish(PreparedPublication prepared) {
        Objects.requireNonNull(prepared, "prepared");
        return publicationBarrier.await(prepared.proposal())
                .then(controlPlane.membership())
                .flatMap(latest -> {
                    if (!latest.equals(prepared.membership())) {
                        return Mono.error(new ControlPlaneException(
                                ControlPlaneException.Code.STALE_TOPOLOGY_EPOCH,
                                "committed membership or epochs changed before reference publication"));
                    }
                    return publicationService.publish(prepared.proposal());
                });
    }

    /** Convenience composition for callers that do not need the pre-commit observation point. */
    public Mono<ObjectReferenceGeneration> publish(String bucket, String objectKey, PreparedArtifact artifact) {
        return prepare(bucket, objectKey, artifact).flatMap(this::publish);
    }

    private Mono<Long> currentGeneration(String bucket, String objectKey) {
        return controlPlane.objectReference(bucket, objectKey)
                .map(ObjectReferenceGeneration::generation)
                .onErrorResume(ClusterWriteCoordinator::isNotFound, ignored -> Mono.just(0L));
    }

    private WriteContext context(
            String bucket,
            String objectKey,
            PreparedArtifact artifact,
            long priorGeneration,
            MembershipSnapshot snapshot) {
        List<DistributedNode> nodes = snapshot.voters().stream()
                .map(member -> DistributedNode.of(
                        member.identity().toString(),
                        member.failureDomain(),
                        "fixed-" + member.name(),
                        member.dataHost() + ":" + member.dataPort(),
                        DistributedNodeHealth.HEALTHY))
                .toList();
        PlacementDecision placement = placementPlanner.plan(
                bucket, objectKey, REPLICATION_FACTOR,
                DistributedTopology.of(snapshot.topologyEpoch(), nodes));
        if (!placement.readyForCommit() || placement.selectedTargets().size() != REPLICATION_FACTOR) {
            throw new ControlPlaneException(ControlPlaneException.Code.INVALID_MEMBERSHIP,
                    "PA-6 could not select three independent fixed replica targets");
        }
        List<ClusterMember> selected = placement.selectedNodeIds().stream()
                .map(NodeIdentity::parse)
                .map(snapshot::member)
                .sorted(Comparator.comparing(ClusterMember::identity))
                .toList();
        if (selected.stream().map(ClusterMember::identity).distinct().count() != REPLICATION_FACTOR
                || selected.stream().noneMatch(member -> member.identity().equals(coordinator))) {
            throw new ControlPlaneException(ControlPlaneException.Code.INVALID_MEMBERSHIP,
                    "PA-6 placement must contain coordinator and three unique committed voters");
        }
        return new WriteContext(bucket, objectKey, artifact, priorGeneration, snapshot, selected);
    }

    private Mono<ReplicaAcknowledgement> verifiedLocalAcknowledgement(WriteContext context) {
        return Mono.fromCallable(() -> {
            PreparedArtifact artifact = context.artifact();
            try (LocalArtifactPort.Source source = localArtifacts.openPublished(artifact.artifactId())) {
                if (source.length() != artifact.length()) {
                    throw new TransferException(TransferError.LENGTH_MISMATCH,
                            "prepared local artifact length differs from declared length");
                }
                MessageDigest digest = sha256();
                ByteBuffer buffer = ByteBuffer.allocate(CHECKSUM_BUFFER_BYTES);
                long read = 0;
                while (true) {
                    buffer.clear();
                    int count = source.read(buffer);
                    if (count < 0) break;
                    if (count == 0) continue;
                    read += count;
                    buffer.flip();
                    digest.update(buffer);
                }
                if (read != artifact.length()) {
                    throw new TransferException(TransferError.LENGTH_MISMATCH,
                            "prepared local artifact ended before declared length");
                }
                if (!MessageDigest.isEqual(digest.digest(), artifact.sha256Bytes())) {
                    throw new TransferException(TransferError.CHECKSUM_MISMATCH,
                            "prepared local artifact SHA-256 differs from declared checksum");
                }
                return acknowledgement(context, coordinator);
            } catch (IOException failure) {
                throw new TransferException(TransferError.IO_FAILURE,
                        "cannot verify prepared local artifact", failure);
            }
        }).subscribeOn(Schedulers.boundedElastic());
    }

    private Flux<ReplicaAcknowledgement> remoteAcknowledgements(WriteContext context) {
        return Flux.fromIterable(context.selected())
                .filter(member -> !member.identity().equals(coordinator))
                .flatMap(member -> transfer(context, member)
                                .flatMap(result -> validResult(context, member, result)
                                        ? Mono.just(acknowledgement(context, member.identity()))
                                        : Mono.empty())
                                .onErrorResume(ignored -> Mono.empty()),
                        MAX_PARALLEL_TRANSFERS);
    }

    private Mono<TransferResult> transfer(WriteContext context, ClusterMember target) {
        return Mono.fromCallable(() -> localArtifacts.openPublished(context.artifact().artifactId()))
                .subscribeOn(Schedulers.boundedElastic())
                .flatMap(source -> Mono.defer(() -> {
                    TransferRequest request = new TransferRequest(
                            context.artifact().operationId(),
                            context.artifact().artifactId(),
                            target.identity(),
                            context.artifact().length(),
                            context.artifact().sha256Bytes(),
                            context.snapshot().topologyEpoch(),
                            context.snapshot().policyEpoch(),
                            transferDeadline);
                    CompletionStage<TransferResult> transfer;
                    try {
                        transfer = replicaTransfers.stage(target, request, source);
                    } catch (Throwable failure) {
                        closeQuietly(source);
                        return Mono.error(failure);
                    }
                    return Mono.fromCompletionStage(transfer)
                            .doFinally(ignored -> closeQuietly(source));
                }));
    }

    private PreparedPublication preparedPublication(
            WriteContext context,
            List<ReplicaAcknowledgement> acknowledgements) {
        Map<NodeIdentity, ReplicaAcknowledgement> unique = new LinkedHashMap<>();
        acknowledgements.forEach(ack -> unique.putIfAbsent(ack.node(), ack));
        PublicationProposal proposal = new PublicationProposal(
                context.bucket(),
                context.objectKey(),
                context.priorGeneration(),
                context.artifact().operationId(),
                context.artifact().artifactId(),
                context.artifact().length(),
                context.artifact().sha256(),
                context.snapshot().topologyEpoch(),
                context.snapshot().policyEpoch(),
                context.selected().stream().map(ClusterMember::identity).collect(java.util.stream.Collectors.toSet()),
                new ArrayList<>(unique.values()),
                context.artifact().metadata());
        return new PreparedPublication(proposal, context.snapshot());
    }

    private static ReplicaAcknowledgement acknowledgement(WriteContext context, NodeIdentity node) {
        return new ReplicaAcknowledgement(
                context.artifact().operationId(),
                context.artifact().artifactId(),
                node,
                context.artifact().length(),
                context.artifact().sha256(),
                context.snapshot().topologyEpoch(),
                context.snapshot().policyEpoch(),
                true);
    }

    private static boolean validResult(WriteContext context, ClusterMember target, TransferResult result) {
        PreparedArtifact artifact = context.artifact();
        return result != null
                && artifact.operationId().equals(result.operationId())
                && artifact.artifactId().equals(result.artifactId())
                && target.identity().equals(result.node())
                && artifact.length() == result.durableLength()
                && MessageDigest.isEqual(artifact.sha256Bytes(), result.durableSha256())
                && context.snapshot().topologyEpoch().equals(result.topologyEpoch())
                && context.snapshot().policyEpoch().equals(result.policyEpoch());
    }

    private static boolean isNotFound(Throwable failure) {
        return failure instanceof ControlPlaneException exception
                && exception.code() == ControlPlaneException.Code.NOT_FOUND;
    }

    private static MessageDigest sha256() {
        try {
            return MessageDigest.getInstance("SHA-256");
        } catch (NoSuchAlgorithmException impossible) {
            throw new AssertionError(impossible);
        }
    }

    private static void closeQuietly(LocalArtifactPort.Source source) {
        try {
            source.close();
        } catch (IOException ignored) {
            // The transfer result already owns the terminal outcome.
        }
    }

    private static void requireNonBlank(String value, String field) {
        if (value == null || value.isBlank()) throw new IllegalArgumentException(field + " is required");
    }

    /** Non-authoritative transfer evidence awaiting fencing revalidation and consensus publication. */
    public record PreparedPublication(PublicationProposal proposal, MembershipSnapshot membership) {
        public PreparedPublication {
            Objects.requireNonNull(proposal, "proposal");
            Objects.requireNonNull(membership, "membership");
        }
    }

    private record WriteContext(
            String bucket,
            String objectKey,
            PreparedArtifact artifact,
            long priorGeneration,
            MembershipSnapshot snapshot,
            List<ClusterMember> selected) {
        private WriteContext {
            selected = List.copyOf(selected);
        }
    }
}
