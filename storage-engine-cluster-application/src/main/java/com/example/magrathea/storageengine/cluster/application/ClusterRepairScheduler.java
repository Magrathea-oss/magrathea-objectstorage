package com.example.magrathea.storageengine.cluster.application;

import reactor.core.Disposable;
import reactor.core.publisher.Mono;
import reactor.core.publisher.SignalType;

import java.time.Clock;
import java.time.Duration;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.Executors;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.AtomicReference;

/**
 * REQ-CLUSTER-024 restart-safe wake-up loop that always discovers executable work from consensus.
 */
public final class ClusterRepairScheduler implements ClusterRepairWorkerPort, AutoCloseable {
    private static final int MAXIMUM_JOBS_PER_SCAN = 16;

    private final NodeIdentity localNode;
    private final ClusterControlPlanePort controlPlane;
    private final ClusterRepairWorker worker;
    private final Clock clock;
    private final Duration interval;
    private final ScheduledExecutorService scheduler;
    private final AtomicBoolean scanning = new AtomicBoolean();
    private final AtomicLong scansStarted = new AtomicLong();
    private final AtomicLong scansCompleted = new AtomicLong();
    private final AtomicLong scansCancelled = new AtomicLong();
    private final AtomicLong scanFailures = new AtomicLong();
    private final AtomicReference<String> lastFailureStage = new AtomicReference<>("NONE");
    private volatile Disposable activeScan;
    private volatile boolean started;
    private volatile boolean closed;

    public ClusterRepairScheduler(NodeIdentity localNode, ClusterControlPlanePort controlPlane,
            ClusterRepairWorker worker, Clock clock, Duration interval) {
        this.localNode = Objects.requireNonNull(localNode, "localNode");
        this.controlPlane = Objects.requireNonNull(controlPlane, "controlPlane");
        this.worker = Objects.requireNonNull(worker, "worker");
        this.clock = Objects.requireNonNull(clock, "clock");
        if (interval == null || interval.isZero() || interval.isNegative()) {
            throw new IllegalArgumentException("repair scan interval must be positive");
        }
        this.interval = interval;
        this.scheduler = Executors.newSingleThreadScheduledExecutor(
                Thread.ofPlatform().name("cluster-repair-scheduler-", 0).factory());
    }

    public synchronized void start() {
        if (started || closed) return;
        started = true;
        scheduler.scheduleWithFixedDelay(this::scan, 0, interval.toMillis(), TimeUnit.MILLISECONDS);
    }

    @Override
    public Mono<RepairJob> repairNow(RepairJobId jobId) {
        return closed
                ? Mono.error(new IllegalStateException("repair scheduler is closed"))
                : worker.claimAndRepair(jobId);
    }

    /** Carries no job data: the wake merely causes another committed-state scan. */
    @Override
    public void signalCommittedWork() {
        if (!started || closed) return;
        try {
            scheduler.execute(this::scan);
        } catch (RejectedExecutionException ignored) {
            // close() won the lifecycle race; committed consensus work remains discoverable on restart.
        }
    }

    private synchronized void scan() {
        if (!started || closed || !scanning.compareAndSet(false, true)) return;
        scansStarted.incrementAndGet();
        RepairJobQuery query = new RepairJobQuery(
                Set.of(RepairState.READY, RepairState.RETRY_WAIT, RepairState.CLAIMED),
                clock.instant());
        Disposable subscription = controlPlane.repairJobs(query)
                .filter(job -> job.specification().target().equals(localNode))
                .take(MAXIMUM_JOBS_PER_SCAN)
                .concatMap(job -> worker.claimAndRepair(job.jobId())
                        .doOnNext(result -> {
                            if (result.state() == RepairState.RETRY_WAIT) {
                                recordFailure("WORKER");
                            }
                        })
                        .onErrorResume(failure -> {
                            recordFailure("WORKER");
                            return Mono.empty();
                        }), 1)
                .then()
                .doOnError(failure -> recordFailure("CONTROL_PLANE"))
                .doFinally(this::finishScan)
                .subscribe(ignored -> { }, ignored -> { });
        if (!started || closed || subscription.isDisposed()) {
            subscription.dispose();
        } else {
            activeScan = subscription;
        }
    }

    private void recordFailure(String stage) {
        scanFailures.incrementAndGet();
        lastFailureStage.set(stage);
    }

    private synchronized void finishScan(SignalType signal) {
        if (signal == SignalType.ON_COMPLETE) scansCompleted.incrementAndGet();
        if (signal == SignalType.CANCEL) scansCancelled.incrementAndGet();
        activeScan = null;
        scanning.set(false);
    }

    /** Bounded, message-free process state; committed jobs remain authoritative in consensus. */
    public Status status() {
        Disposable subscription = activeScan;
        return new Status(scansStarted.get(), scansCompleted.get(), scansCancelled.get(),
                scanFailures.get(), scanning.get() && subscription != null
                        && !subscription.isDisposed(), closed, lastFailureStage.get());
    }

    @Override
    public synchronized void close() {
        if (closed) return;
        started = false;
        closed = true;
        Disposable subscription = activeScan;
        activeScan = null;
        if (subscription != null) subscription.dispose();
        scanning.set(false);
        scheduler.shutdownNow();
    }

    public record Status(long scansStarted, long scansCompleted, long scansCancelled,
                         long scanFailures, boolean active, boolean closed,
                         String lastFailureStage) { }
}
