package com.example.magrathea.storageengine.cluster.application;

import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import reactor.core.publisher.SignalType;
import reactor.core.publisher.Sinks;
import reactor.core.scheduler.Schedulers;

import java.time.Duration;
import java.util.Objects;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.LongSupplier;

/**
 * REQ-CLUSTER-027 process-local periodic discovery of damaged current replica obligations.
 * One read-only page and one named local target are processed serially per cycle.
 */
public final class ClusterAntiEntropyScheduler implements AutoCloseable {
    private final NodeIdentity localNode;
    private final ClusterControlPlanePort controlPlane;
    private final LocalArtifactPort localArtifacts;
    private final ClusterRepairCoordinator repairCoordinator;
    private final ClusterRepairWorkerPort repairWorker;
    private final Duration interval;
    private final int pageSize;
    private final LongSupplier repairFailures;
    private final ScheduledExecutorService scheduler;
    private final AtomicReference<ReferencePageQuery.Cursor> cursor = new AtomicReference<>();
    private final AtomicBoolean active = new AtomicBoolean();
    private final AtomicBoolean targetActive = new AtomicBoolean();
    private final AtomicBoolean retryPending = new AtomicBoolean();
    private final AtomicLong cycles = new AtomicLong();
    private final AtomicLong completedCycles = new AtomicLong();
    private final AtomicLong pages = new AtomicLong();
    private final AtomicLong records = new AtomicLong();
    private final AtomicLong exactTargets = new AtomicLong();
    private final AtomicLong missingTargets = new AtomicLong();
    private final AtomicLong corruptTargets = new AtomicLong();
    private final AtomicLong ensures = new AtomicLong();
    private final AtomicLong queryFailures = new AtomicLong();
    private final AtomicLong probeFailures = new AtomicLong();
    private final AtomicLong ensureFailures = new AtomicLong();
    private final AtomicLong retries = new AtomicLong();
    private final AtomicLong cursorResets = new AtomicLong();
    private final AtomicLong staleReferences = new AtomicLong();
    private final AtomicLong deduplications = new AtomicLong();
    private final AtomicLong overlaps = new AtomicLong();
    private final AtomicReference<FailureStage> lastFailure =
            new AtomicReference<>(FailureStage.NONE);
    private final Sinks.Empty<Void> shutdown = Sinks.empty();
    private volatile boolean started;
    private volatile boolean closed;

    public ClusterAntiEntropyScheduler(NodeIdentity localNode,
            ClusterControlPlanePort controlPlane, LocalArtifactPort localArtifacts,
            ClusterRepairCoordinator repairCoordinator, ClusterRepairWorkerPort repairWorker,
            Duration interval, int pageSize) {
        this(localNode, controlPlane, localArtifacts, repairCoordinator, repairWorker, interval,
                pageSize, () -> 0);
    }

    public ClusterAntiEntropyScheduler(NodeIdentity localNode,
            ClusterControlPlanePort controlPlane, LocalArtifactPort localArtifacts,
            ClusterRepairCoordinator repairCoordinator, ClusterRepairWorkerPort repairWorker,
            Duration interval, int pageSize, LongSupplier repairFailures) {
        this.localNode = Objects.requireNonNull(localNode, "localNode");
        this.controlPlane = Objects.requireNonNull(controlPlane, "controlPlane");
        this.localArtifacts = Objects.requireNonNull(localArtifacts, "localArtifacts");
        this.repairCoordinator = Objects.requireNonNull(repairCoordinator, "repairCoordinator");
        this.repairWorker = Objects.requireNonNull(repairWorker, "repairWorker");
        if (interval == null || interval.isZero() || interval.isNegative()
                || interval.toMillis() < 1) {
            throw new IllegalArgumentException(
                    "anti-entropy interval must be at least one millisecond");
        }
        new ReferencePageQuery(null, pageSize);
        this.interval = interval;
        this.pageSize = pageSize;
        this.repairFailures = Objects.requireNonNull(repairFailures, "repairFailures");
        this.scheduler = Executors.newSingleThreadScheduledExecutor(
                Thread.ofPlatform().name("cluster-anti-entropy-", 0).factory());
    }

    public synchronized void start() {
        if (started || closed) return;
        started = true;
        scheduler.scheduleWithFixedDelay(this::scheduledCycle, 0, interval.toMillis(),
                TimeUnit.MILLISECONDS);
    }

    /** Executes one bounded page cycle; exposed for deterministic lifecycle validation. */
    public Mono<Void> runCycle() {
        return Mono.<Void>defer(() -> {
            if (closed) return Mono.error(new IllegalStateException(
                    "anti-entropy scheduler is closed"));
            if (!active.compareAndSet(false, true)) {
                return Mono.empty();
            }
            cycles.incrementAndGet();
            if (retryPending.getAndSet(false)) retries.incrementAndGet();
            ReferencePageQuery query = new ReferencePageQuery(cursor.get(), pageSize);
            return controlPlane.currentReferences(query)
                    .doOnError(failure -> recordFailure(FailureStage.QUERY))
                    .flatMap(page -> {
                        pages.incrementAndGet();
                        return Flux.fromIterable(page.references())
                                .concatMap(this::inspectIfNamed, 1)
                                .then(Mono.fromRunnable(() -> completePage(page)))
                                .then();
                    })
                    .doOnSuccess(ignored -> completedCycles.incrementAndGet())
                    .doOnError(ignored -> retryPending.set(true))
                    .takeUntilOther(shutdown.asMono())
                    .doFinally(this::finishCycle);
        });
    }

    private Mono<Void> inspectIfNamed(ObjectReferenceGeneration reference) {
        // EC shard monitoring and repair have their own later consensus-owned requirement slice.
        if (reference.erasureCoded() || !reference.replicas().contains(localNode)) {
            return Mono.empty();
        }
        return Mono.defer(() -> {
            if (!targetActive.compareAndSet(false, true)) {
                overlaps.incrementAndGet();
                return Mono.error(new IllegalStateException(
                        "anti-entropy target processing overlapped"));
            }
            records.incrementAndGet();
            return Mono.fromCallable(() -> localArtifacts.probePublished(
                            reference.artifactId(), reference.length(), reference.sha256()))
                    .subscribeOn(Schedulers.boundedElastic())
                    .doOnError(failure -> recordFailure(FailureStage.PROBE))
                    .flatMap(probe -> switch (probe.status()) {
                        case EXACT -> {
                            exactTargets.incrementAndGet();
                            yield Mono.empty();
                        }
                        case MISSING -> {
                            missingTargets.incrementAndGet();
                            yield ensure(reference, "periodic discovery found missing local target");
                        }
                        case INVALID -> {
                            corruptTargets.incrementAndGet();
                            yield ensure(reference, "periodic discovery found invalid local target");
                        }
                    })
                    .doOnEach(signal -> {
                        if (signal.isOnComplete() || signal.isOnError()) {
                            targetActive.set(false);
                        }
                    })
                    .doFinally(ignored -> targetActive.set(false));
        });
    }

    private Mono<Void> ensure(ObjectReferenceGeneration reference, String reason) {
        ensures.incrementAndGet();
        return repairCoordinator.ensureDetailed(reference, reason)
                .doOnNext(outcome -> {
                    if (outcome.deduplicated()) deduplications.incrementAndGet();
                    repairWorker.signalCommittedWork();
                })
                .doOnError(failure -> {
                    recordFailure(FailureStage.ENSURE);
                    if (failure instanceof ClusterRepairCoordinator.RepairEnsureRejected rejected
                            && rejected.code() == RepairCommandResult.Code.INVALID_REFERENCE) {
                        staleReferences.incrementAndGet();
                    }
                })
                .then();
    }

    private void completePage(ReferencePage page) {
        if (page.terminal()) {
            cursor.set(null);
            cursorResets.incrementAndGet();
        } else {
            cursor.set(page.nextExclusiveCursor());
        }
    }

    private void recordFailure(FailureStage stage) {
        switch (stage) {
            case QUERY -> queryFailures.incrementAndGet();
            case PROBE -> probeFailures.incrementAndGet();
            case ENSURE -> ensureFailures.incrementAndGet();
            case NONE -> { }
        }
        lastFailure.set(stage);
    }

    private void scheduledCycle() {
        if (!started || closed) return;
        runCycle().subscribe(ignored -> { }, ignored -> { });
    }

    private synchronized void finishCycle(SignalType signal) {
        targetActive.set(false);
        active.set(false);
    }

    /** Bounded message-free counters; references, payloads, cursors, and credentials are absent. */
    public Status status() {
        return new Status(cycles.get(), completedCycles.get(), pages.get(), records.get(),
                exactTargets.get(), missingTargets.get(), corruptTargets.get(), ensures.get(),
                queryFailures.get(), probeFailures.get(), ensureFailures.get(),
                repairFailures.getAsLong(), retries.get(), cursorResets.get(),
                staleReferences.get(), deduplications.get(), overlaps.get(), active.get(),
                targetActive.get(), closed, lastFailure.get());
    }

    @Override
    public synchronized void close() {
        if (closed) return;
        started = false;
        closed = true;
        scheduler.shutdownNow();
        shutdown.tryEmitEmpty();
        active.set(false);
        targetActive.set(false);
        cursor.set(null);
    }

    public enum FailureStage { NONE, QUERY, PROBE, ENSURE }

    public record Status(long cycles, long completedCycles, long pages, long records,
                         long exactTargets, long missingTargets, long corruptTargets,
                         long ensures, long queryFailures, long probeFailures,
                         long ensureFailures, long repairFailures, long retries,
                         long cursorResets, long staleReferences, long deduplications,
                         long overlaps, boolean activePage, boolean activeTarget,
                         boolean closed, FailureStage lastFailure) { }
}
