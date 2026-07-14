package com.example.magrathea.storageengine.cluster.application;

import reactor.core.publisher.Mono;
import reactor.core.scheduler.Schedulers;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.HexFormat;
import java.util.List;
import java.util.Objects;
import java.util.UUID;

/**
 * Executes one REQ-CLUSTER-021..025 fenced claim with bounded direct transport and durable publication.
 */
public final class ClusterRepairWorker {
    private final NodeIdentity localNode;
    private final String processSession;
    private final ClusterControlPlanePort controlPlane;
    private final LocalArtifactPort localArtifacts;
    private final ReplicaReadPort replicaReads;
    private final Duration readDeadline;
    private final RepairExecutionGate executionGate;
    private final ClusterRepairMetrics metrics;
    private final Clock clock;

    public ClusterRepairWorker(NodeIdentity localNode, String processSession,
            ClusterControlPlanePort controlPlane, LocalArtifactPort localArtifacts,
            ReplicaReadPort replicaReads, Duration readDeadline,
            RepairExecutionGate executionGate, ClusterRepairMetrics metrics, Clock clock) {
        this.localNode = Objects.requireNonNull(localNode, "localNode");
        this.processSession = requireText(processSession, "process session");
        this.controlPlane = Objects.requireNonNull(controlPlane, "controlPlane");
        this.localArtifacts = Objects.requireNonNull(localArtifacts, "localArtifacts");
        this.replicaReads = Objects.requireNonNull(replicaReads, "replicaReads");
        this.readDeadline = positive(readDeadline, "read deadline");
        this.executionGate = Objects.requireNonNull(executionGate, "executionGate");
        this.metrics = Objects.requireNonNull(metrics, "metrics");
        this.clock = Objects.requireNonNull(clock, "clock");
    }

    public Mono<RepairJob> claimAndRepair(RepairJobId jobId) {
        return controlPlane.repairJob(jobId).flatMap(job -> {
            if (!job.specification().target().equals(localNode)
                    || terminal(job.state())) return Mono.just(job);
            return exactCurrent(job.specification()).flatMap(current -> {
                Instant now = clock.instant();
                NodeIdentity sourceHint = current.replicas().stream()
                        .filter(identity -> !identity.equals(job.specification().target()))
                        .findFirst().orElse(null);
                Instant deadline = now.plus(job.specification().retryPolicy().maximumClaimDuration());
                RepairCommands.Claim claim = new RepairCommands.Claim(commandId("claim"), jobId,
                        localNode, processSession, now, deadline, sourceHint);
                RepairExecutionGate.Observation beforeClaim = new RepairExecutionGate.Observation(
                        jobId, job.claimGeneration(), 0);
                return executionGate.await(
                                RepairExecutionGate.Checkpoint.BEFORE_CLAIM_PROPOSED, beforeClaim)
                        .then(controlPlane.claimRepair(claim))
                        .flatMap(result -> {
                            if (!result.accepted() || result.state() != RepairState.CLAIMED) {
                                return awaitResult(jobId, 0);
                            }
                            metrics.claimed();
                            RepairExecutionGate.Observation claimed =
                                    new RepairExecutionGate.Observation(
                                            jobId, result.claimGeneration(), 0);
                            return executionGate.await(
                                            RepairExecutionGate.Checkpoint
                                                    .CLAIM_COMMITTED_BEFORE_TRANSFER,
                                            claimed)
                                    .then(controlPlane.repairJob(jobId))
                                    .flatMap(this::executeClaim);
                        });
            });
        });
    }

    private Mono<RepairJob> executeClaim(RepairJob job) {
        RepairSpecification specification = job.specification();
        RepairClaim claim = Objects.requireNonNull(job.claim(), "claimed repair must carry a claim");
        return exactCurrent(specification)
                .flatMap(current -> probe(specification).flatMap(probe -> {
                    if (probe.exact()) {
                        metrics.alreadyValid();
                        return exactCurrent(specification).then(succeed(job, "exact durable target already valid"));
                    }
                    return tryNamedSources(job, current);
                }))
                .onErrorResume(ReferenceChanged.class,
                        ignored -> obsolete(job, "bound reference changed during repair execution"))
                .onErrorResume(NoSource.class,
                        ignored -> block(job, "no different named current replica source remains"))
                .onErrorResume(CandidatesExhausted.class, failure -> failure.retryable()
                        ? retry(job, failure.getMessage())
                        : block(job, failure.getMessage()))
                .onErrorResume(failure -> retry(job, compactReason(failure)))
                .then(controlPlane.repairJob(specification.jobId()));
    }

    private Mono<RepairJob> tryNamedSources(
            RepairJob job, ObjectReferenceGeneration current) {
        RepairSpecification specification = job.specification();
        return controlPlane.membership().flatMap(snapshot -> {
            List<ClusterMember> candidates = current.replicas().stream()
                    .filter(identity -> !identity.equals(specification.target()))
                    .map(snapshot::member)
                    .toList();
            if (candidates.isEmpty()) return Mono.error(new NoSource());
            return tryNamedSource(job, candidates, 0, new CandidateFailures(0, false));
        });
    }

    private Mono<RepairJob> tryNamedSource(
            RepairJob job, List<ClusterMember> candidates, int index,
            CandidateFailures failures) {
        if (index == candidates.size()) {
            return Mono.error(new CandidatesExhausted(failures.attempts(),
                    failures.retryableSeen()));
        }
        return exactCurrent(job.specification())
                .then(transferAndPublish(job, candidates.get(index)))
                .onErrorResume(ReferenceChanged.class, Mono::error)
                .onErrorResume(failure -> tryNamedSource(job, candidates, index + 1,
                        failures.append(failure)));
    }

    private Mono<RepairJob> transferAndPublish(RepairJob job, ClusterMember source) {
        RepairSpecification specification = job.specification();
        RepairClaim claim = job.claim();
        TransferRequest request = new TransferRequest(
                specification.jobId() + "-" + claim.claimGeneration(),
                specification.artifactId(), localNode, specification.length(),
                HexFormat.of().parseHex(specification.sha256()), specification.topologyEpoch(),
                specification.policyEpoch(), readDeadline);
        LocalArtifactPort.RepairToken token = new LocalArtifactPort.RepairToken(
                specification.jobId(), claim.claimGeneration());
        return Mono.usingWhen(
                Mono.fromCallable(() -> checkpointingSink(
                                localArtifacts.beginRepair(request, token), specification, claim))
                        .subscribeOn(Schedulers.boundedElastic()),
                sink -> {
                    metrics.fetched();
                    RepairExecutionGate.Observation beforePublication =
                            new RepairExecutionGate.Observation(specification.jobId(),
                                    claim.claimGeneration(), specification.length());
                    return Mono.fromCompletionStage(replicaReads.fetch(source, request, sink))
                            .then(executionGate.await(
                                    RepairExecutionGate.Checkpoint.BEFORE_TARGET_PUBLICATION,
                                    beforePublication))
                            .then(exactCurrent(specification))
                            .then(Mono.fromCallable(sink::publishVerified)
                                    .subscribeOn(Schedulers.boundedElastic()))
                            .doOnNext(ignored -> metrics.published())
                            .flatMap(result -> executionGate.await(
                                            RepairExecutionGate.Checkpoint
                                                    .TARGET_DURABLY_PUBLISHED_BEFORE_COMPLETION,
                                            new RepairExecutionGate.Observation(
                                                    specification.jobId(),
                                                    claim.claimGeneration(),
                                                    specification.length()))
                                    .thenReturn(result))
                            .then(exactCurrent(specification))
                            .then(succeed(job, "exact durable publication"));
                },
                sink -> close(sink, false),
                (sink, failure) -> close(sink, true),
                sink -> close(sink, true));
    }

    private Mono<RepairJob> succeed(RepairJob job, String reason) {
        RepairSpecification specification = job.specification();
        RepairClaim claim = job.claim();
        RepairCommands.Succeed command = new RepairCommands.Succeed(commandId("succeed"),
                specification.jobId(), claim.claimGeneration(), localNode, processSession,
                clock.instant(), specification.length(), specification.sha256(), reason);
        return controlPlane.succeedRepair(command)
                .then(controlPlane.repairJob(specification.jobId()));
    }

    private Mono<RepairJob> obsolete(RepairJob job, String reason) {
        RepairClaim claim = job.claim();
        metrics.obsolete();
        return controlPlane.obsoleteRepair(new RepairCommands.Obsolete(commandId("obsolete"),
                        job.jobId(), claim == null ? 0 : claim.claimGeneration(),
                        claim == null ? null : localNode, claim == null ? null : processSession,
                        clock.instant(), reason))
                .then(controlPlane.repairJob(job.jobId()));
    }

    private Mono<RepairJob> retry(RepairJob job, String reason) {
        RepairClaim claim = job.claim();
        if (claim == null) return controlPlane.repairJob(job.jobId());
        return controlPlane.retryRepair(new RepairCommands.Retry(commandId("retry"), job.jobId(),
                        claim.claimGeneration(), localNode, processSession, clock.instant(), reason))
                .then(controlPlane.repairJob(job.jobId()));
    }

    private Mono<RepairJob> block(RepairJob job, String reason) {
        RepairClaim claim = job.claim();
        return controlPlane.blockRepair(new RepairCommands.Block(commandId("block"), job.jobId(),
                        claim.claimGeneration(), localNode, processSession, clock.instant(), reason))
                .then(controlPlane.repairJob(job.jobId()));
    }

    private Mono<ObjectReferenceGeneration> exactCurrent(RepairSpecification specification) {
        return controlPlane.objectReference(specification.bucket(), specification.objectKey())
                .filter(reference -> exact(reference, specification))
                .switchIfEmpty(Mono.error(new ReferenceChanged()));
    }

    private Mono<LocalArtifactPort.ArtifactProbe> probe(RepairSpecification specification) {
        return Mono.fromCallable(() -> localArtifacts.probePublished(specification.artifactId(),
                        specification.length(), specification.sha256()))
                .subscribeOn(Schedulers.boundedElastic());
    }

    private Mono<RepairJob> awaitResult(RepairJobId jobId, int poll) {
        return controlPlane.repairJob(jobId).flatMap(job -> {
            if (terminal(job.state()) || poll >= 100) return Mono.just(job);
            if (job.state() == RepairState.READY || job.state() == RepairState.RETRY_WAIT) {
                return Mono.just(job);
            }
            return Mono.delay(Duration.ofMillis(50)).then(awaitResult(jobId, poll + 1));
        });
    }

    private static Mono<Void> close(LocalArtifactPort.RepairSink sink, boolean abort) {
        return Mono.fromRunnable(() -> {
            if (abort) sink.abort();
            try {
                sink.close();
            } catch (IOException ignored) {
                // The primary terminal signal owns the outcome.
            }
        }).subscribeOn(Schedulers.boundedElastic()).then();
    }

    private static boolean exact(ObjectReferenceGeneration reference,
            RepairSpecification specification) {
        return reference.generation() == specification.referenceGeneration()
                && reference.artifactId().equals(specification.artifactId())
                && reference.length() == specification.length()
                && reference.sha256().equalsIgnoreCase(specification.sha256())
                && reference.topologyEpoch().equals(specification.topologyEpoch())
                && reference.policyEpoch().equals(specification.policyEpoch())
                && reference.replicas().contains(specification.target());
    }

    private static boolean terminal(RepairState state) {
        return state == RepairState.SUCCEEDED || state == RepairState.BLOCKED
                || state == RepairState.OBSOLETE;
    }

    private static String commandId(String action) {
        return "repair-" + action + "-" + UUID.randomUUID();
    }

    private static String compactReason(Throwable failure) {
        Throwable cause = failure;
        while (cause.getCause() != null) cause = cause.getCause();
        String message = cause.getMessage();
        return cause.getClass().getSimpleName() + (message == null ? "" : ": " + message);
    }

    private static boolean permanentSourceFailure(Throwable failure) {
        Throwable cause = failure;
        while (cause != null) {
            if (cause instanceof TransferException transfer) {
                return switch (transfer.error()) {
                    case CHECKSUM_MISMATCH, LENGTH_MISMATCH, OFFSET_MISMATCH,
                            FRAME_TOO_LARGE, IDENTITY_MISMATCH, UNTRUSTED_PEER,
                            ARTIFACT_CONFLICT, PROTOCOL_ERROR -> true;
                    case CANCELLED, DEADLINE_EXCEEDED, IO_FAILURE -> false;
                };
            }
            cause = cause.getCause();
        }
        return false;
    }

    private static String requireText(String value, String name) {
        if (value == null || value.isBlank()) throw new IllegalArgumentException(name + " is required");
        return value;
    }

    private static Duration positive(Duration value, String name) {
        if (value == null || value.isZero() || value.isNegative()) {
            throw new IllegalArgumentException(name + " must be positive");
        }
        return value;
    }

    private LocalArtifactPort.RepairSink checkpointingSink(
            LocalArtifactPort.RepairSink delegate, RepairSpecification specification,
            RepairClaim claim) {
        return new LocalArtifactPort.RepairSink() {
            @Override
            public void accept(long offset, ByteBuffer bytes) throws IOException {
                int count = bytes.remaining();
                delegate.accept(offset, bytes);
                executionGate.awaitSynchronously(
                        RepairExecutionGate.Checkpoint.PAYLOAD_BYTES_STAGED,
                        new RepairExecutionGate.Observation(
                                specification.jobId(), claim.claimGeneration(),
                                offset + count));
            }

            @Override
            public TransferResult verify() throws IOException {
                return delegate.verify();
            }

            @Override
            public TransferResult publishVerified() throws IOException {
                return delegate.publishVerified();
            }

            @Override
            public void abort() {
                delegate.abort();
            }

            @Override
            public void close() throws IOException {
                delegate.close();
            }
        };
    }

    private record CandidateFailures(int attempts, boolean retryableSeen) {
        CandidateFailures append(Throwable failure) {
            return new CandidateFailures(attempts + 1,
                    retryableSeen || !permanentSourceFailure(failure));
        }
    }

    private static final class CandidatesExhausted extends RuntimeException {
        private final boolean retryable;

        CandidatesExhausted(int attempts, boolean retryable) {
            super("all " + attempts + " different named current replica candidates were exhausted; "
                    + (retryable ? "a temporary failure remains"
                    : "every candidate failed integrity or permanent validation"));
            this.retryable = retryable;
        }

        boolean retryable() {
            return retryable;
        }
    }

    private static final class ReferenceChanged extends RuntimeException { }
    private static final class NoSource extends RuntimeException { }
}
