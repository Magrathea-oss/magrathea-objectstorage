package com.example.magrathea.storageengine.cluster.application;

import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import reactor.core.scheduler.Schedulers;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Duration;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.CompletionStage;

/** Distributes already encoded fixed EC 4+2 shards and publishes only complete fenced layouts. */
public final class ClusterEcWriteCoordinator {
    private static final int CHECKSUM_BUFFER_BYTES = 65_536;
    private static final int MAX_PARALLEL_TRANSFERS = 2;

    private final NodeIdentity coordinator;
    private final ClusterControlPlanePort controlPlane;
    private final LocalArtifactPort localArtifacts;
    private final ReplicaTransferPort replicaTransfers;
    private final FixedEc42Placement placement;
    private final EcReferencePublicationService publicationService;
    private final ReferencePublicationBarrier publicationBarrier;
    private final Duration transferDeadline;

    public ClusterEcWriteCoordinator(
            NodeIdentity coordinator,
            ClusterControlPlanePort controlPlane,
            LocalArtifactPort localArtifacts,
            ReplicaTransferPort replicaTransfers,
            Duration transferDeadline) {
        this(coordinator, controlPlane, localArtifacts, replicaTransfers,
                new FixedEc42Placement(), new EcReferencePublicationService(controlPlane),
                ReferencePublicationBarrier.none(), transferDeadline);
    }

    public ClusterEcWriteCoordinator(
            NodeIdentity coordinator,
            ClusterControlPlanePort controlPlane,
            LocalArtifactPort localArtifacts,
            ReplicaTransferPort replicaTransfers,
            FixedEc42Placement placement,
            EcReferencePublicationService publicationService,
            ReferencePublicationBarrier publicationBarrier,
            Duration transferDeadline) {
        this.coordinator = Objects.requireNonNull(coordinator, "coordinator");
        this.controlPlane = Objects.requireNonNull(controlPlane, "controlPlane");
        this.localArtifacts = Objects.requireNonNull(localArtifacts, "localArtifacts");
        this.replicaTransfers = Objects.requireNonNull(replicaTransfers, "replicaTransfers");
        this.placement = Objects.requireNonNull(placement, "placement");
        this.publicationService = Objects.requireNonNull(publicationService, "publicationService");
        this.publicationBarrier = Objects.requireNonNull(publicationBarrier, "publicationBarrier");
        this.transferDeadline = Objects.requireNonNull(transferDeadline, "transferDeadline");
        if (transferDeadline.isZero() || transferDeadline.isNegative()) {
            throw new IllegalArgumentException("transfer deadline must be positive");
        }
    }

    public Mono<PreparedEcPublication> prepare(
            String bucket, String objectKey, PreparedEcObject object) {
        requireNonBlank(bucket, "bucket");
        requireNonBlank(objectKey, "objectKey");
        Objects.requireNonNull(object, "object");
        if (object.shards().stream().anyMatch(
                shard -> !coordinator.equals(shard.artifact().localNode()))) {
            return Mono.error(new IllegalArgumentException(
                    "every prepared EC shard must be durable on the coordinator"));
        }
        return verifyPreparedObject(object)
                .then(controlPlane.membership())
                .flatMap(snapshot -> currentGeneration(bucket, objectKey)
                        .flatMap(prior -> prepareTransfers(
                                bucket, objectKey, object, prior, snapshot)));
    }

    public Mono<ObjectReferenceGeneration> publish(PreparedEcPublication prepared) {
        Objects.requireNonNull(prepared, "prepared");
        return publicationBarrier.awaitEc(prepared.proposal())
                .then(controlPlane.membership())
                .flatMap(latest -> {
                    if (!latest.equals(prepared.membership())) {
                        return Mono.error(new ControlPlaneException(
                                ControlPlaneException.Code.STALE_TOPOLOGY_EPOCH,
                                "committed membership or epochs changed before EC publication"));
                    }
                    return publicationService.publish(prepared.proposal());
                });
    }

    public Mono<ObjectReferenceGeneration> publish(
            String bucket, String objectKey, PreparedEcObject object) {
        return prepare(bucket, objectKey, object).flatMap(this::publish);
    }

    private Mono<PreparedEcPublication> prepareTransfers(
            String bucket,
            String objectKey,
            PreparedEcObject object,
            long priorGeneration,
            MembershipSnapshot snapshot) {
        List<EcShardReference> references = placement.plan(snapshot, object.shards());
        Map<String, PreparedEcShard> preparedById = new LinkedHashMap<>();
        object.shards().forEach(shard -> preparedById.put(
                shard.artifact().artifactId(), shard));

        return Flux.fromIterable(references)
                .flatMap(reference -> acknowledgement(
                        snapshot, preparedById.get(reference.artifactId()), reference),
                        MAX_PARALLEL_TRANSFERS)
                .collectList()
                .map(acks -> new PreparedEcPublication(
                        new EcPublicationProposal(
                                bucket, objectKey, priorGeneration, object.operationId(),
                                object.logicalLength(), object.sha256(),
                                snapshot.topologyEpoch(), snapshot.policyEpoch(),
                                references, acks, object.metadata()),
                        snapshot));
    }

    private Mono<Void> verifyPreparedObject(PreparedEcObject object) {
        return Mono.fromRunnable(() -> {
            MessageDigest objectDigest = sha256();
            long logicalBytes = 0;
            for (PreparedEcShard shard : object.shards()) {
                MessageDigest shardDigest = sha256();
                long storedBytes = 0;
                long logicalRemaining = shard.logicalDataLength();
                try (LocalArtifactPort.Source source = localArtifacts.openPublished(
                        shard.artifact().artifactId())) {
                    if (source.length() != shard.artifact().length()) {
                        throw new TransferException(TransferError.LENGTH_MISMATCH,
                                "prepared EC shard length differs from schema-3 facts");
                    }
                    ByteBuffer buffer = ByteBuffer.allocate(CHECKSUM_BUFFER_BYTES);
                    while (true) {
                        buffer.clear();
                        int count = source.read(buffer);
                        if (count < 0) break;
                        if (count == 0) continue;
                        storedBytes += count;
                        buffer.flip();
                        ByteBuffer shardView = buffer.asReadOnlyBuffer();
                        shardDigest.update(shardView);
                        if (!shard.parity() && logicalRemaining > 0) {
                            int accepted = Math.toIntExact(Math.min(logicalRemaining, count));
                            ByteBuffer logicalView = buffer.asReadOnlyBuffer();
                            logicalView.limit(logicalView.position() + accepted);
                            objectDigest.update(logicalView);
                            logicalRemaining -= accepted;
                            logicalBytes += accepted;
                        }
                    }
                } catch (IOException failure) {
                    throw new TransferException(TransferError.IO_FAILURE,
                            "cannot verify prepared EC object", failure);
                }
                if (storedBytes != shard.artifact().length()
                        || logicalRemaining != 0
                        || !MessageDigest.isEqual(shardDigest.digest(),
                                shard.artifact().sha256Bytes())) {
                    throw new TransferException(TransferError.CHECKSUM_MISMATCH,
                            "prepared EC shard integrity differs from schema-3 facts");
                }
            }
            if (logicalBytes != object.logicalLength()
                    || !MessageDigest.isEqual(objectDigest.digest(),
                            java.util.HexFormat.of().parseHex(object.sha256()))) {
                throw new TransferException(TransferError.CHECKSUM_MISMATCH,
                        "prepared EC data shards do not match the declared object SHA-256");
            }
        }).subscribeOn(Schedulers.boundedElastic()).then();
    }

    private Mono<ReplicaAcknowledgement> acknowledgement(
            MembershipSnapshot snapshot,
            PreparedEcShard prepared,
            EcShardReference reference) {
        if (prepared == null) return Mono.error(new IllegalStateException("prepared EC shard is missing"));
        if (reference.location().equals(coordinator)) {
            return verifyLocal(snapshot, prepared, reference);
        }
        ClusterMember target = snapshot.member(reference.location());
        return Mono.fromCallable(() -> localArtifacts.openPublished(reference.artifactId()))
                .subscribeOn(Schedulers.boundedElastic())
                .flatMap(source -> Mono.defer(() -> {
                    TransferRequest request = request(snapshot, prepared, reference);
                    CompletionStage<TransferResult> transfer;
                    try {
                        transfer = replicaTransfers.stage(target, request, source);
                    } catch (Throwable failure) {
                        closeQuietly(source);
                        return Mono.error(failure);
                    }
                    return Mono.fromCompletionStage(transfer)
                            .flatMap(result -> validResult(
                                    snapshot, reference, prepared.artifact().operationId(), result)
                                    ? Mono.just(ack(snapshot, reference,
                                            prepared.artifact().operationId()))
                                    : Mono.error(new TransferException(
                                            TransferError.CHECKSUM_MISMATCH,
                                            "remote EC shard acknowledgement is invalid")))
                            .doFinally(ignored -> closeQuietly(source));
                }));
    }

    private Mono<ReplicaAcknowledgement> verifyLocal(
            MembershipSnapshot snapshot,
            PreparedEcShard prepared,
            EcShardReference reference) {
        return Mono.fromCallable(() -> {
            try (LocalArtifactPort.Source source = localArtifacts.openPublished(
                    reference.artifactId())) {
                if (source.length() != reference.storedLength()) {
                    throw new TransferException(TransferError.LENGTH_MISMATCH,
                            "local EC shard length differs from committed length");
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
                if (read != reference.storedLength()
                        || !MessageDigest.isEqual(digest.digest(),
                                prepared.artifact().sha256Bytes())) {
                    throw new TransferException(TransferError.CHECKSUM_MISMATCH,
                            "local EC shard checksum differs from committed SHA-256");
                }
                return ack(snapshot, reference, prepared.artifact().operationId());
            } catch (IOException failure) {
                throw new TransferException(TransferError.IO_FAILURE,
                        "cannot verify local EC shard", failure);
            }
        }).subscribeOn(Schedulers.boundedElastic());
    }

    private TransferRequest request(
            MembershipSnapshot snapshot,
            PreparedEcShard prepared,
            EcShardReference reference) {
        return new TransferRequest(
                prepared.artifact().operationId(), reference.artifactId(),
                reference.location(), reference.storedLength(),
                prepared.artifact().sha256Bytes(), snapshot.topologyEpoch(),
                snapshot.policyEpoch(), transferDeadline);
    }

    private static ReplicaAcknowledgement ack(
            MembershipSnapshot snapshot,
            EcShardReference reference,
            String operationId) {
        return new ReplicaAcknowledgement(
                operationId, reference.artifactId(), reference.location(),
                reference.storedLength(), reference.sha256(),
                snapshot.topologyEpoch(), snapshot.policyEpoch(), true);
    }

    private Mono<Long> currentGeneration(String bucket, String objectKey) {
        return controlPlane.objectReference(bucket, objectKey)
                .map(ObjectReferenceGeneration::generation)
                .onErrorResume(ClusterEcWriteCoordinator::isNotFound, ignored -> Mono.just(0L));
    }

    private static boolean validResult(
            MembershipSnapshot snapshot,
            EcShardReference reference,
            String operationId,
            TransferResult result) {
        return result != null
                && operationId.equals(result.operationId())
                && reference.artifactId().equals(result.artifactId())
                && reference.location().equals(result.node())
                && reference.storedLength() == result.durableLength()
                && MessageDigest.isEqual(
                        java.util.HexFormat.of().parseHex(reference.sha256()),
                        result.durableSha256())
                && snapshot.topologyEpoch().equals(result.topologyEpoch())
                && snapshot.policyEpoch().equals(result.policyEpoch());
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
            // The transfer result owns the terminal outcome.
        }
    }

    private static void requireNonBlank(String value, String field) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(field + " is required");
        }
    }

    /** Complete non-authoritative shard evidence awaiting membership revalidation. */
    public record PreparedEcPublication(
            EcPublicationProposal proposal, MembershipSnapshot membership) {
        public PreparedEcPublication {
            Objects.requireNonNull(proposal, "proposal");
            Objects.requireNonNull(membership, "membership");
        }
    }
}
