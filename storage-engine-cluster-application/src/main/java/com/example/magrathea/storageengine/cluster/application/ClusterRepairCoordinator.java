package com.example.magrathea.storageengine.cluster.application;

import reactor.core.publisher.Mono;
import reactor.core.scheduler.Schedulers;

import java.io.IOException;
import java.time.Clock;
import java.time.Duration;
import java.util.Objects;
import java.util.UUID;

/**
 * Request-facing use case for the missing and corrupt GET paths in REQ-CLUSTER-019/020.
 * It durably ensures consensus-owned work before delegating execution.
 */
public final class ClusterRepairCoordinator {
    private static final RepairRetryPolicy RETRY_POLICY = new RepairRetryPolicy(
            5, Duration.ofMillis(250), Duration.ofSeconds(2), Duration.ofSeconds(10));

    private final NodeIdentity localNode;
    private final ClusterControlPlanePort controlPlane;
    private final LocalArtifactPort localArtifacts;
    private final ClusterRepairWorkerPort worker;
    private final ClusterRepairMetrics metrics;
    private final Clock clock;

    public ClusterRepairCoordinator(NodeIdentity localNode, ClusterControlPlanePort controlPlane,
            LocalArtifactPort localArtifacts, ClusterRepairWorkerPort worker,
            ClusterRepairMetrics metrics, Clock clock) {
        this.localNode = Objects.requireNonNull(localNode, "localNode");
        this.controlPlane = Objects.requireNonNull(controlPlane, "controlPlane");
        this.localArtifacts = Objects.requireNonNull(localArtifacts, "localArtifacts");
        this.worker = Objects.requireNonNull(worker, "worker");
        this.metrics = Objects.requireNonNull(metrics, "metrics");
        this.clock = Objects.requireNonNull(clock, "clock");
    }

    public Mono<Boolean> localArtifactExists(ObjectReferenceGeneration reference) {
        return Mono.fromCallable(() -> localArtifacts.publishedExists(reference.artifactId()))
                .subscribeOn(Schedulers.boundedElastic());
    }

    /** Missing bytes are repaired to durable success before the caller opens the response stream. */
    public Mono<Void> repairMissingBeforeResponse(ObjectReferenceGeneration reference) {
        return ensure(reference, "missing local current-generation artifact")
                .flatMap(worker::repairNow)
                .flatMap(job -> job.state() == RepairState.SUCCEEDED
                        ? Mono.empty()
                        : Mono.error(new IOException("repair did not reach durable success: "
                                + job.state() + " " + job.reason())));
    }

    /** A streamed integrity failure is durably recorded before the original failure propagates. */
    public Mono<Void> scheduleAfterIntegrityFailure(ObjectReferenceGeneration reference) {
        metrics.integrityFailure();
        return ensure(reference, "streamed local artifact failed committed integrity facts")
                .doOnSuccess(ignored -> worker.signalCommittedWork())
                .then();
    }

    public Mono<RepairJobId> ensure(ObjectReferenceGeneration reference, String reason) {
        RepairSpecification specification = new RepairSpecification(reference.bucket(),
                reference.objectKey(), reference.generation(), reference.artifactId(), localNode,
                reference.length(), reference.sha256(), reference.topologyEpoch(),
                reference.policyEpoch(), RETRY_POLICY);
        NodeIdentity sourceHint = reference.replicas().stream()
                .filter(identity -> !identity.equals(localNode)).findFirst().orElse(null);
        RepairCommands.Ensure command = new RepairCommands.Ensure(commandId("ensure"),
                specification, clock.instant(), sourceHint);
        return controlPlane.ensureRepair(command).flatMap(result -> {
            if (!result.accepted()) {
                return Mono.error(new IllegalStateException(
                        "repair ensure rejected: " + result.code() + " " + result.reason()));
            }
            metrics.ensured();
            return controlPlane.repairJob(specification.jobId()).flatMap(job -> {
                if (job.state() != RepairState.SUCCEEDED && job.state() != RepairState.BLOCKED) {
                    return Mono.just(job);
                }
                RepairCommands.Reevaluate reevaluate = new RepairCommands.Reevaluate(
                        commandId("reevaluate"), specification.jobId(), clock.instant(), reason,
                        sourceHint);
                return controlPlane.reevaluateRepair(reevaluate)
                        .then(controlPlane.repairJob(specification.jobId()));
            }).map(RepairJob::jobId);
        });
    }

    public ClusterRepairMetrics metrics() {
        return metrics;
    }

    private static String commandId(String action) {
        return "repair-" + action + "-" + UUID.randomUUID();
    }
}
