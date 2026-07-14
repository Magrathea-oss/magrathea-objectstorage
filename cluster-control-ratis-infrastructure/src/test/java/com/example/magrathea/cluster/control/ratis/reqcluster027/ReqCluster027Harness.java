package com.example.magrathea.cluster.control.ratis.reqcluster027;

import com.example.magrathea.cluster.control.ratis.FixedThreeNodeRatisCluster;
import com.example.magrathea.cluster.control.ratis.RatisTlsConfig;
import com.example.magrathea.cluster.control.ratis.ReferencePageCodecEvidence;
import com.example.magrathea.cluster.data.grpc.FileLocalArtifactStore;
import com.example.magrathea.cluster.data.grpc.GrpcReplicaClient;
import com.example.magrathea.cluster.data.grpc.GrpcReplicaServer;
import com.example.magrathea.cluster.data.grpc.ReplicaTlsConfig;
import com.example.magrathea.cluster.data.grpc.ReplicaTransferMetrics;
import com.example.magrathea.storageengine.cluster.application.*;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.nio.ByteBuffer;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.BooleanSupplier;

/** Real Ratis, filesystem, and UUID-bound grpc-java mTLS acceptance harness. */
final class ReqCluster027Harness implements AutoCloseable {
    static final NodeIdentity A = NodeIdentity.parse("11111111-1111-4111-8111-111111111111");
    static final NodeIdentity B = NodeIdentity.parse("22222222-2222-4222-8222-222222222222");
    static final NodeIdentity C = NodeIdentity.parse("33333333-3333-4333-8333-333333333333");
    static final String BUCKET = "ep10-anti-entropy-archive";
    static final String SHA = "46918899a9ddbe1d1c2f1613416501b3e8d7cdbc2a63a78298d0cc3ee388e800";
    static final String CORRUPT = "whole-ae-corrupt-7f351d76-50d8-4f48-9b86-001";
    static final String EXACT = "whole-ae-exact-7f351d76-50d8-4f48-9b86-002";
    static final String MISSING = "whole-ae-missing-7f351d76-50d8-4f48-9b86-003";
    static final String OTHER = "whole-ae-other-7f351d76-50d8-4f48-9b86-004";
    static final String ENSURE_RACE7 = "whole-ae-ensure-race-7f351d76-50d8-4f48-007";
    static final String ENSURE_RACE8 = "whole-ae-ensure-race-7f351d76-50d8-4f48-008";
    static final String PUBLICATION_RACE7 =
            "whole-ae-publication-race-7f351d76-50d8-4f48-007";
    static final String PUBLICATION_RACE8 =
            "whole-ae-publication-race-7f351d76-50d8-4f48-008";
    static final Path ROOT = Path.of("target/ep10/three-node");
    static final Path PKI = Path.of("target/ep10/pki");
    private static final Duration WAIT = Duration.ofSeconds(20);

    private final MembershipSnapshot membership = new MembershipSnapshot(List.of(
            new ClusterMember("A", A, "127.0.0.1", 19801, "127.0.0.1", 19901, "rack-a"),
            new ClusterMember("B", B, "127.0.0.1", 19802, "127.0.0.1", 19902, "rack-b"),
            new ClusterMember("C", C, "127.0.0.1", 19803, "127.0.0.1", 19903, "rack-c")),
            "topology-1", "policy-1");
    private final Map<String, AtomicLong> probes = new HashMap<>();
    private final Map<String, AtomicLong> ensureAttempts = new HashMap<>();
    private final Map<String, AtomicLong> rpcReads = new HashMap<>();
    private final AtomicInteger pageActive = new AtomicInteger();
    private final AtomicInteger maximumPageActive = new AtomicInteger();
    private final List<ReferencePage> observedPages = new ArrayList<>();
    private final List<ReferencePageQuery> observedQueries = new ArrayList<>();
    private final AtomicBoolean probesOnBoundedElastic = new AtomicBoolean(true);
    private final AtomicBoolean failQuery = new AtomicBoolean();
    private final AtomicBoolean failProbe = new AtomicBoolean();
    private final AtomicBoolean failEnsure = new AtomicBoolean();
    private final AtomicInteger failRepairReads = new AtomicInteger();

    private FixedThreeNodeRatisCluster cluster;
    private ClusterControlPlanePort baseControl;
    private ClusterControlPlanePort observedControl;
    private FileLocalArtifactStore aStore;
    private FileLocalArtifactStore bStore;
    private FileLocalArtifactStore cStore;
    private LocalArtifactPort observedBStore;
    private GrpcReplicaServer aServer;
    private GrpcReplicaServer cServer;
    private GrpcReplicaClient aClient;
    private GrpcReplicaClient cClient;
    private ClusterRepairMetrics repairMetrics;
    private ClusterRepairScheduler repairScheduler;
    private ClusterRepairCoordinator repairCoordinator;
    private ClusterRepairWorker repairWorker;
    private ClusterAntiEntropyScheduler antiEntropy;
    private byte[] fixture;

    Evidence runSuccessfulDiscovery() throws Exception {
        start(false, RepairExecutionGate.open());
        publish("evidence/2026/anti-entropy/corrupt-on-b.bin", 7, CORRUPT, Set.of(A, B, C));
        publish("evidence/2026/anti-entropy/exact-on-b.bin", 3, EXACT, Set.of(A, B, C));
        publish("evidence/2026/anti-entropy/missing-on-b.bin", 11, MISSING, Set.of(A, B, C));
        publish("evidence/2026/anti-entropy/not-named-on-b.bin", 5, OTHER, Set.of(A, C));
        for (String artifact : List.of(CORRUPT, EXACT, MISSING, OTHER)) seedSources(artifact);
        Files.createDirectories(bStore.publishedPath(EXACT).getParent());
        Files.write(bStore.publishedPath(EXACT), fixture);
        byte[] corrupt = fixture.clone();
        corrupt[corrupt.length - 1] ^= (byte) 0xff;
        Files.write(bStore.publishedPath(CORRUPT), corrupt);

        boolean fixedNodesInspected = inspectExactNode(A, aStore)
                && inspectExactNode(C, cStore);
        repairScheduler.start();
        antiEntropy.start();
        await(() -> exact(B, CORRUPT) && exact(B, MISSING)
                        && succeeded(CORRUPT) && succeeded(MISSING),
                "periodic discovery did not durably repair B");
        ClusterAntiEntropyScheduler.Status status = antiEntropy.status();
        antiEntropy.close();
        repairScheduler.close();
        boolean schedulersClosed = antiEntropy.status().closed()
                && !antiEntropy.status().activePage()
                && !antiEntropy.status().activeTarget()
                && repairScheduler.status().closed()
                && !repairScheduler.status().active();
        boolean activeCloseCancelled = verifyActiveCycleCancellation();

        List<RepairJob> jobs = baseControl.repairJobs(new RepairJobQuery(Set.of(), null))
                .collectList().block(WAIT);
        require(jobs != null, "repair jobs were unavailable");
        require(jobs.stream().filter(job -> Set.of(CORRUPT, MISSING)
                .contains(job.specification().artifactId())).count() == 2,
                "canonical repair identity created an unexpected job count");
        return new Evidence(status, List.copyOf(observedPages), Map.copyOf(probeCounts()),
                Map.copyOf(ensureCounts()), Map.copyOf(rpcCounts()), jobs,
                exact(B, CORRUPT) && exact(B, EXACT) && exact(B, MISSING),
                !probes.containsKey(OTHER), maximumPageActive.get(), fixedNodesInspected,
                false, false, false, false, false, false, true, false,
                false, false, ReferencePageCodecEvidence.verify().complete(),
                probesOnBoundedElastic.get(), schedulersClosed, false,
                activeCloseCancelled);
    }

    Evidence runRecoveryAndRaces() throws Exception {
        PublicationRaceGate publicationGate = new PublicationRaceGate();
        start(true, publicationGate);
        String ensureKey = "evidence/2026/anti-entropy/ensure-reference-race.bin";
        String publicationKey = "evidence/2026/anti-entropy/publication-reference-race.bin";
        ObjectReferenceGeneration ensureGeneration7 = publish(
                ensureKey, 7, ENSURE_RACE7, Set.of(A, B, C));
        ObjectReferenceGeneration publicationGeneration7 = publish(
                publicationKey, 7, PUBLICATION_RACE7, Set.of(A, B, C));
        for (String artifact : List.of(ENSURE_RACE7, ENSURE_RACE8,
                PUBLICATION_RACE7, PUBLICATION_RACE8)) {
            seedSources(artifact);
        }

        ReferencePage first = baseControl.currentReferences(new ReferencePageQuery(null, 2))
                .block(WAIT);
        require(first != null && first.references().contains(ensureGeneration7)
                        && first.references().contains(publicationGeneration7),
                "both generation 7 races were not observed from real Ratis");
        repairCoordinator.ensureDetailed(ensureGeneration7,
                "focused before-ensure race obligation").block(WAIT);
        ClusterRepairCoordinator.EnsureOutcome publicationOutcome =
                repairCoordinator.ensureDetailed(publicationGeneration7,
                        "focused before-publication race obligation").block(WAIT);

        publishNext(ensureKey, 7, ENSURE_RACE8, Set.of(A, B, C));
        boolean staleBeforeEnsure;
        try {
            repairCoordinator.ensureDetailed(
                    ensureGeneration7, "stale focused observation").block(WAIT);
            staleBeforeEnsure = false;
        } catch (RuntimeException expected) {
            staleBeforeEnsure = root(expected) instanceof
                    ClusterRepairCoordinator.RepairEnsureRejected rejected
                    && rejected.code() == RepairCommandResult.Code.INVALID_REFERENCE;
        }

        CompletableFuture<RepairJob> publicationRace = repairWorker
                .claimAndRepair(publicationOutcome.jobId()).toFuture();
        await(publicationGate::reached,
                "repair did not reach BEFORE_TARGET_PUBLICATION");
        boolean oldBytesAbsentAtBoundary =
                !Files.exists(bStore.publishedPath(PUBLICATION_RACE7));
        publishNext(publicationKey, 7, PUBLICATION_RACE8, Set.of(A, B, C));
        publicationGate.release();
        RepairJob publicationResult = publicationRace.get(WAIT.toMillis(), TimeUnit.MILLISECONDS);
        boolean publicationBoundaryRejected = publicationResult.state() == RepairState.OBSOLETE
                && !Files.exists(bStore.publishedPath(PUBLICATION_RACE7))
                && rpcReads.getOrDefault(PUBLICATION_RACE7, new AtomicLong()).get() == 1;

        failQuery.set(true);
        failProbe.set(true);
        failEnsure.set(true);
        failRepairReads.set(2);
        repairScheduler.start();
        antiEntropy.start();
        await(() -> exact(B, ENSURE_RACE8) && succeeded(ENSURE_RACE8)
                        && exact(B, PUBLICATION_RACE8) && succeeded(PUBLICATION_RACE8),
                "failure recovery did not repair both current generation 8 obligations");
        ClusterAntiEntropyScheduler.Status beforeReconstruction = antiEntropy.status();
        antiEntropy.close();
        repairScheduler.close();

        Files.deleteIfExists(bStore.publishedPath(PUBLICATION_RACE8));
        repairWorker = worker(false, RepairExecutionGate.open());
        repairScheduler = new ClusterRepairScheduler(B, observedControl, repairWorker,
                Clock.systemUTC(), Duration.ofMillis(25));
        repairCoordinator = new ClusterRepairCoordinator(B, observedControl, observedBStore,
                repairScheduler, repairMetrics, Clock.systemUTC());
        antiEntropy = new ClusterAntiEntropyScheduler(B, observedControl, observedBStore,
                repairCoordinator, repairScheduler, Duration.ofSeconds(30), 2,
                () -> repairScheduler.status().scanFailures());
        long pagesBeforeReconstruction = observedPages.size();
        antiEntropy.runCycle().block(WAIT);
        boolean reconstructionFromFirst = observedPages.size() > pagesBeforeReconstruction
                && observedPages.get((int) pagesBeforeReconstruction).references().stream()
                .findFirst().map(reference -> reference.bucket().equals(BUCKET)).orElse(false);
        ClusterAntiEntropyScheduler.Status afterReconstruction = antiEntropy.status();
        repairScheduler.start();
        repairScheduler.signalCommittedWork();
        await(() -> exact(B, PUBLICATION_RACE8) && succeeded(PUBLICATION_RACE8),
                "reconstructed B schedulers did not deduplicate and repair generation 8");
        antiEntropy.close();
        repairScheduler.close();
        boolean schedulersClosed = antiEntropy.status().closed()
                && !antiEntropy.status().activePage()
                && !antiEntropy.status().activeTarget()
                && repairScheduler.status().closed()
                && !repairScheduler.status().active();

        List<RepairJob> jobs = baseControl.repairJobs(new RepairJobQuery(Set.of(), null))
                .collectList().block(WAIT);
        boolean oldObsolete = jobs != null && jobs.stream()
                .filter(job -> Set.of(ENSURE_RACE7, PUBLICATION_RACE7)
                        .contains(job.specification().artifactId()))
                .count() == 2
                && jobs.stream().filter(job -> Set.of(ENSURE_RACE7, PUBLICATION_RACE7)
                        .contains(job.specification().artifactId()))
                .allMatch(job -> job.state() == RepairState.OBSOLETE);
        return new Evidence(beforeReconstruction, List.copyOf(observedPages),
                Map.copyOf(probeCounts()), Map.copyOf(ensureCounts()), Map.copyOf(rpcCounts()),
                jobs == null ? List.of() : jobs,
                exact(B, ENSURE_RACE8) && exact(B, PUBLICATION_RACE8), true,
                maximumPageActive.get(), true, staleBeforeEnsure, oldObsolete,
                beforeReconstruction.queryFailures() > 0,
                beforeReconstruction.probeFailures() > 0,
                beforeReconstruction.ensureFailures() > 0,
                beforeReconstruction.repairFailures() > 0, reconstructionFromFirst,
                afterReconstruction.pages() == 1 && afterReconstruction.deduplications() > 0,
                publicationBoundaryRejected, oldBytesAbsentAtBoundary,
                ReferencePageCodecEvidence.verify().complete(), probesOnBoundedElastic.get(),
                schedulersClosed, repeatedQueryAfterFailure(),
                verifyActiveCycleCancellation());
    }

    private boolean inspectExactNode(NodeIdentity node, LocalArtifactPort artifacts) {
        ClusterRepairWorkerPort unusedWorker = new ClusterRepairWorkerPort() {
            @Override public Mono<RepairJob> repairNow(RepairJobId jobId) {
                return Mono.error(new AssertionError("exact source unexpectedly requested repair"));
            }
            @Override public void signalCommittedWork() {
                throw new AssertionError("exact source unexpectedly signaled repair");
            }
        };
        ClusterRepairCoordinator coordinator = new ClusterRepairCoordinator(node, baseControl,
                artifacts, unusedWorker, new ClusterRepairMetrics(), Clock.systemUTC());
        try (ClusterAntiEntropyScheduler scheduler = new ClusterAntiEntropyScheduler(node,
                baseControl, artifacts, coordinator, unusedWorker, Duration.ofSeconds(30), 2)) {
            scheduler.runCycle().block(WAIT);
            scheduler.runCycle().block(WAIT);
            ClusterAntiEntropyScheduler.Status status = scheduler.status();
            return status.pages() == 2 && status.records() == 4
                    && status.exactTargets() == 4 && status.ensures() == 0;
        }
    }

    private void start(boolean failures, RepairExecutionGate executionGate) throws Exception {
        deleteRecursively(ROOT);
        deleteRecursively(PKI);
        Path fixturePath = Path.of(
                "s3-reactive-api-adapter/src/test/resources/fixtures/upload/large-object.bin");
        if (!Files.isRegularFile(fixturePath)) fixturePath = Path.of("..").resolve(fixturePath);
        fixture = Files.readAllBytes(fixturePath.toAbsolutePath().normalize());
        require(fixture.length == 134, "fixture length changed");
        CertificateAuthority authority = new CertificateAuthority(PKI);
        Map<NodeIdentity, Material> materials = new LinkedHashMap<>();
        materials.put(A, authority.create("A", A));
        materials.put(B, authority.create("B", B));
        materials.put(C, authority.create("C", C));
        Map<NodeIdentity, RatisTlsConfig> ratis = Map.of(
                A, ratis(materials.get(A), A), B, ratis(materials.get(B), B),
                C, ratis(materials.get(C), C));
        cluster = new FixedThreeNodeRatisCluster(membership, roots("identity"), roots("ratis"),
                ratis, ratis.get(A));
        cluster.start(List.of(A, B, C)).block(WAIT);
        baseControl = cluster.controlPlane(ratis.get(A));
        await(() -> cluster.leaderIdentity().isPresent(), "Ratis leader was not elected");
        baseControl.createBucket(BUCKET).block(WAIT);

        aStore = store(A);
        bStore = store(B);
        cStore = store(C);
        observedBStore = new ObservedArtifacts(bStore);
        ReplicaTlsConfig aTls = replica(materials.get(A), A);
        ReplicaTlsConfig bTls = replica(materials.get(B), B);
        ReplicaTlsConfig cTls = replica(materials.get(C), C);
        aServer = new GrpcReplicaServer(membership.member(A).dataAddress(), aTls, aStore,
                new ReplicaTransferMetrics(), Duration.ZERO).start();
        cServer = new GrpcReplicaServer(membership.member(C).dataAddress(), cTls, cStore,
                new ReplicaTransferMetrics(), Duration.ZERO).start();
        aClient = new GrpcReplicaClient(membership.member(A).dataAddress(), bTls, A);
        cClient = new GrpcReplicaClient(membership.member(C).dataAddress(), bTls, C);
        observedControl = new ObservedControl(baseControl);
        repairMetrics = new ClusterRepairMetrics();
        repairWorker = worker(failures, executionGate);
        repairScheduler = new ClusterRepairScheduler(B, observedControl, repairWorker,
                Clock.systemUTC(), Duration.ofMillis(25));
        repairCoordinator = new ClusterRepairCoordinator(B, observedControl, observedBStore,
                repairScheduler, repairMetrics, Clock.systemUTC());
        antiEntropy = new ClusterAntiEntropyScheduler(B, observedControl, observedBStore,
                repairCoordinator, repairScheduler, Duration.ofMillis(25), 2,
                () -> repairScheduler.status().scanFailures());
    }

    private ClusterRepairWorker worker(
            boolean failures, RepairExecutionGate executionGate) {
        ReplicaReadPort reads = (source, request, sink) -> {
            rpcReads.computeIfAbsent(request.artifactId(), ignored -> new AtomicLong())
                    .incrementAndGet();
            GrpcReplicaClient client = source.identity().equals(A) ? aClient : cClient;
            CompletionStage<TransferResult> actual = client.read(request, sink);
            if (failures && failRepairReads.getAndUpdate(
                    remaining -> Math.max(0, remaining - 1)) > 0) {
                CompletableFuture<TransferResult> failed = new CompletableFuture<>();
                actual.whenComplete((result, failure) -> failed.completeExceptionally(
                        new IOException("injected repair execution failure")));
                return failed;
            }
            return actual;
        };
        return new ClusterRepairWorker(B, "req-cluster-027-b-session", observedControl,
                observedBStore, reads, Duration.ofSeconds(5), executionGate,
                repairMetrics, Clock.systemUTC());
    }

    private ObjectReferenceGeneration publish(String key, int generation, String artifact,
            Set<NodeIdentity> replicas) {
        ObjectReferenceGeneration current = null;
        for (int next = 1; next <= generation; next++) {
            current = publishNext(key, next - 1L, artifact, replicas);
        }
        return current;
    }

    private ObjectReferenceGeneration publishNext(String key, long prior, String artifact,
            Set<NodeIdentity> replicas) {
        String operation = "req-cluster-027-" + artifact + "-" + (prior + 1);
        List<ReplicaAcknowledgement> acknowledgements = replicas.stream().sorted()
                .map(node -> new ReplicaAcknowledgement(operation, artifact, node, 134, SHA,
                        "topology-1", "policy-1", true)).toList();
        return baseControl.compareAndPublish(new PublicationProposal(BUCKET, key, prior,
                operation, artifact, 134, SHA, "topology-1", "policy-1", Set.of(A, B, C),
                acknowledgements, new ClusterObjectMetadata("STANDARD", Map.of(), Map.of(), "",
                Instant.parse("2026-07-14T12:00:00Z").plusSeconds(prior + 1)))).block(WAIT);
    }

    private void seedSources(String artifact) throws Exception {
        seed(aStore, artifact, "source-a-" + artifact);
        seed(cStore, artifact, "source-c-" + artifact);
    }

    private void seed(FileLocalArtifactStore store, String artifact, String operation)
            throws Exception {
        if (Files.exists(store.publishedPath(artifact))) return;
        try (LocalArtifactPort.IncomingSink sink = store.beginIncoming(operation, artifact)) {
            sink.accept(ByteBuffer.wrap(fixture));
            sink.publish();
        }
    }

    private boolean succeeded(String artifact) {
        try {
            List<RepairJob> jobs = baseControl.repairJobs(new RepairJobQuery(Set.of(), null))
                    .collectList().block(Duration.ofSeconds(2));
            return jobs != null && jobs.stream().anyMatch(job ->
                    job.specification().artifactId().equals(artifact)
                            && job.state() == RepairState.SUCCEEDED);
        } catch (RuntimeException unavailable) {
            return false;
        }
    }

    private boolean exact(NodeIdentity node, String artifact) {
        try {
            FileLocalArtifactStore store = node.equals(A) ? aStore : node.equals(B) ? bStore : cStore;
            return store.probePublished(artifact, 134, SHA).exact()
                    && Arrays.equals(Files.readAllBytes(store.publishedPath(artifact)), fixture);
        } catch (IOException failure) {
            return false;
        }
    }

    private Map<String, Long> probeCounts() {
        Map<String, Long> values = new HashMap<>();
        probes.forEach((key, value) -> values.put(key, value.get()));
        return values;
    }

    private Map<String, Long> ensureCounts() {
        Map<String, Long> values = new HashMap<>();
        ensureAttempts.forEach((key, value) -> values.put(key, value.get()));
        return values;
    }

    private Map<String, Long> rpcCounts() {
        Map<String, Long> values = new HashMap<>();
        rpcReads.forEach((key, value) -> values.put(key, value.get()));
        return values;
    }

    private boolean verifyActiveCycleCancellation() throws Exception {
        AtomicBoolean delayed = new AtomicBoolean();
        AtomicBoolean cancelled = new AtomicBoolean();
        ClusterControlPlanePort delayedControl = new ClusterControlPlanePort() {
            @Override public Mono<MembershipSnapshot> membership() { return baseControl.membership(); }
            @Override public Mono<BucketNamespace> createBucket(String bucket) { return baseControl.createBucket(bucket); }
            @Override public Mono<BucketNamespace> bucket(String bucket) { return baseControl.bucket(bucket); }
            @Override public Mono<ObjectReferenceGeneration> compareAndPublish(PublicationProposal proposal) { return baseControl.compareAndPublish(proposal); }
            @Override public Mono<ObjectReferenceGeneration> objectReference(String bucket, String key) { return baseControl.objectReference(bucket, key); }
            @Override public Mono<ReferencePage> currentReferences(ReferencePageQuery query) {
                return baseControl.currentReferences(query).flatMap(page -> Mono.defer(() -> {
                    delayed.set(true);
                    return Mono.<ReferencePage>never()
                            .doOnCancel(() -> cancelled.set(true));
                }));
            }
        };
        ClusterRepairWorkerPort noWork = new ClusterRepairWorkerPort() {
            @Override public Mono<RepairJob> repairNow(RepairJobId jobId) {
                return Mono.error(new AssertionError("delayed page unexpectedly requested repair"));
            }
            @Override public void signalCommittedWork() {
                throw new AssertionError("delayed page unexpectedly signaled repair");
            }
        };
        ClusterAntiEntropyScheduler closing = new ClusterAntiEntropyScheduler(B,
                delayedControl, observedBStore, repairCoordinator, noWork,
                Duration.ofSeconds(30), 2);
        CompletableFuture<Void> cycle = closing.runCycle().toFuture();
        await(delayed::get, "active close probe did not reach delayed page response");
        closing.close();
        cycle.get(WAIT.toMillis(), TimeUnit.MILLISECONDS);
        ClusterAntiEntropyScheduler.Status status = closing.status();
        return cancelled.get() && status.closed() && !status.activePage()
                && !status.activeTarget();
    }

    private boolean repeatedQueryAfterFailure() {
        synchronized (observedQueries) {
            for (int index = 1; index < observedQueries.size(); index++) {
                if (observedQueries.get(index - 1).equals(observedQueries.get(index))) {
                    return true;
                }
            }
            return false;
        }
    }

    @Override
    public void close() {
        if (antiEntropy != null) antiEntropy.close();
        if (repairScheduler != null) repairScheduler.close();
        if (aClient != null) aClient.close();
        if (cClient != null) cClient.close();
        if (aServer != null) aServer.close();
        if (cServer != null) cServer.close();
        if (cluster != null) cluster.close();
    }

    private final class ObservedArtifacts implements LocalArtifactPort {
        private final LocalArtifactPort delegate;
        private ObservedArtifacts(LocalArtifactPort delegate) { this.delegate = delegate; }
        @Override public Source openPublished(String artifactId) throws IOException { return delegate.openPublished(artifactId); }
        @Override public boolean publishedExists(String artifactId) { return delegate.publishedExists(artifactId); }
        @Override public ArtifactProbe probePublished(String artifactId, long length, String sha) throws IOException {
            probes.computeIfAbsent(artifactId, ignored -> new AtomicLong()).incrementAndGet();
            if (!Thread.currentThread().getName().startsWith("boundedElastic-")) {
                probesOnBoundedElastic.set(false);
            }
            if (failProbe.compareAndSet(true, false)) throw new IOException("injected probe failure");
            return delegate.probePublished(artifactId, length, sha);
        }
        @Override public Sink beginUnpublished(TransferRequest request) throws IOException { return delegate.beginUnpublished(request); }
        @Override public RepairSink beginRepair(TransferRequest request, RepairToken token) throws IOException { return delegate.beginRepair(request, token); }
        @Override public IncomingSink beginIncoming(String operationId, String artifactId) throws IOException { return delegate.beginIncoming(operationId, artifactId); }
    }

    private final class ObservedControl implements ClusterControlPlanePort {
        private final ClusterControlPlanePort delegate;
        private ObservedControl(ClusterControlPlanePort delegate) { this.delegate = delegate; }
        @Override public Mono<MembershipSnapshot> membership() { return delegate.membership(); }
        @Override public Mono<BucketNamespace> createBucket(String bucket) { return delegate.createBucket(bucket); }
        @Override public Mono<BucketNamespace> bucket(String bucket) { return delegate.bucket(bucket); }
        @Override public Flux<BucketNamespace> buckets() { return delegate.buckets(); }
        @Override public Mono<ObjectReferenceGeneration> compareAndPublish(PublicationProposal proposal) { return delegate.compareAndPublish(proposal); }
        @Override public Mono<ObjectReferenceGeneration> objectReference(String bucket, String key) { return delegate.objectReference(bucket, key); }
        @Override public Mono<ReferencePage> currentReferences(ReferencePageQuery query) {
            synchronized (observedQueries) { observedQueries.add(query); }
            if (failQuery.compareAndSet(true, false)) return Mono.error(new IOException("injected query failure"));
            return Mono.defer(() -> {
                int now = pageActive.incrementAndGet();
                maximumPageActive.accumulateAndGet(now, Math::max);
                return delegate.currentReferences(query).doOnNext(page -> {
                    synchronized (observedPages) { observedPages.add(page); }
                    require(page.references().size() <= query.limit(), "Ratis page exceeded query limit");
                    if (query.exclusiveAfter() != null && !page.references().isEmpty()) {
                        require(page.references().get(0).namespaceKey()
                                        .compareTo(query.exclusiveAfter().namespaceKey()) > 0,
                                "Ratis page cursor was not exclusive");
                    }
                }).doFinally(ignored -> pageActive.decrementAndGet());
            });
        }
        @Override public Mono<RepairCommandResult> ensureRepair(RepairCommands.Ensure command) {
            String artifact = command.specification().artifactId();
            ensureAttempts.computeIfAbsent(artifact, ignored -> new AtomicLong()).incrementAndGet();
            if (failEnsure.compareAndSet(true, false)) return Mono.error(new IOException("injected ensure failure"));
            return delegate.ensureRepair(command);
        }
        @Override public Mono<RepairJob> repairJob(RepairJobId id) { return delegate.repairJob(id); }
        @Override public Flux<RepairJob> repairJobs(RepairJobQuery query) { return delegate.repairJobs(query); }
        @Override public Mono<RepairCommandResult> claimRepair(RepairCommands.Claim command) { return delegate.claimRepair(command); }
        @Override public Mono<RepairCommandResult> renewRepair(RepairCommands.Renew command) { return delegate.renewRepair(command); }
        @Override public Mono<RepairCommandResult> retryRepair(RepairCommands.Retry command) { return delegate.retryRepair(command); }
        @Override public Mono<RepairCommandResult> blockRepair(RepairCommands.Block command) { return delegate.blockRepair(command); }
        @Override public Mono<RepairCommandResult> succeedRepair(RepairCommands.Succeed command) { return delegate.succeedRepair(command); }
        @Override public Mono<RepairCommandResult> obsoleteRepair(RepairCommands.Obsolete command) { return delegate.obsoleteRepair(command); }
        @Override public Mono<RepairCommandResult> reevaluateRepair(RepairCommands.Reevaluate command) { return delegate.reevaluateRepair(command); }
    }

    record Evidence(ClusterAntiEntropyScheduler.Status status, List<ReferencePage> pages,
                    Map<String, Long> probes, Map<String, Long> ensures, Map<String, Long> rpcReads,
                    List<RepairJob> jobs, boolean exactTargets, boolean otherUnprobed,
                    int maximumPageActive, boolean fixedNodesInspected,
                    boolean staleBeforeEnsure, boolean oldObsolete,
                    boolean queryFailure, boolean probeFailure, boolean ensureFailure,
                    boolean repairFailure, boolean restartFromFirst, boolean rebuiltOnePage,
                    boolean publicationBoundaryRejected, boolean oldBytesAbsentAtBoundary,
                    boolean codecFailClosed, boolean probesOnBoundedElastic,
                    boolean schedulersClosed, boolean failedCursorRetried,
                    boolean activeCloseCancelled) { }

    private static final class PublicationRaceGate implements RepairExecutionGate {
        private final AtomicBoolean selected = new AtomicBoolean();
        private final AtomicBoolean reached = new AtomicBoolean();
        private final CompletableFuture<Void> release = new CompletableFuture<>();
        private final AtomicReference<Observation> observation = new AtomicReference<>();

        @Override
        public Mono<Void> awaitClaimPermission() {
            return Mono.empty();
        }

        @Override
        public Mono<Void> await(Checkpoint checkpoint, Observation current) {
            if (checkpoint != Checkpoint.BEFORE_TARGET_PUBLICATION
                    || !selected.compareAndSet(false, true)) {
                return Mono.empty();
            }
            observation.set(current);
            reached.set(true);
            return Mono.fromFuture(release);
        }

        boolean reached() {
            Observation current = observation.get();
            return reached.get() && current != null && current.stagedBytes() == 134;
        }

        void release() {
            release.complete(null);
        }
    }

    private Map<NodeIdentity, Path> roots(String child) {
        return Map.of(A, nodeRoot(A).resolve(child), B, nodeRoot(B).resolve(child),
                C, nodeRoot(C).resolve(child));
    }

    private FileLocalArtifactStore store(NodeIdentity node) throws IOException {
        return new FileLocalArtifactStore(nodeRoot(node).resolve("objects"),
                nodeRoot(node).resolve("temporary"), node);
    }

    private Path nodeRoot(NodeIdentity node) {
        return ROOT.resolve(node.equals(A) ? "node-a" : node.equals(B) ? "node-b" : "node-c");
    }

    private RatisTlsConfig ratis(Material material, NodeIdentity node) {
        return new RatisTlsConfig(material.certificate, material.key, material.ca, node,
                Set.of(A, B, C));
    }

    private ReplicaTlsConfig replica(Material material, NodeIdentity node) {
        return new ReplicaTlsConfig(material.certificate, material.key, material.ca, node,
                Set.of(A, B, C));
    }

    private static Throwable root(Throwable failure) {
        Throwable value = failure;
        while (value.getCause() != null) value = value.getCause();
        return value;
    }

    private static void await(BooleanSupplier condition, String failure) throws Exception {
        long deadline = System.nanoTime() + WAIT.toNanos();
        while (System.nanoTime() < deadline) {
            if (condition.getAsBoolean()) return;
            Thread.sleep(25);
        }
        throw new AssertionError(failure);
    }

    private static void require(boolean condition, String failure) {
        if (!condition) throw new AssertionError(failure);
    }

    private static void deleteRecursively(Path root) throws IOException {
        if (!Files.exists(root)) return;
        try (var paths = Files.walk(root)) {
            for (Path path : paths.sorted(Comparator.reverseOrder()).toList()) Files.deleteIfExists(path);
        }
    }

    private record Material(Path certificate, Path key, Path ca) { }

    private static final class CertificateAuthority {
        private final Path root;
        private CertificateAuthority(Path root) { this.root = root; }
        private Material create(String name, NodeIdentity identity) throws Exception {
            Path ca = root.resolve("ca");
            Files.createDirectories(ca);
            Path caKey = ca.resolve("ca.key");
            Path caCertificate = ca.resolve("ca.crt");
            if (!Files.exists(caCertificate)) {
                run("openssl", "req", "-x509", "-newkey", "rsa:2048", "-nodes", "-days", "2",
                        "-subj", "/CN=REQ CLUSTER 027 CA", "-keyout", caKey.toString(),
                        "-out", caCertificate.toString());
            }
            Path node = root.resolve("nodes").resolve(name);
            Files.createDirectories(node);
            Path key = node.resolve("tls.key");
            Path request = node.resolve("tls.csr");
            Path certificate = node.resolve("tls.crt");
            Path extensions = node.resolve("tls.ext");
            Files.writeString(extensions, "basicConstraints=CA:FALSE\n"
                    + "keyUsage=digitalSignature,keyEncipherment\n"
                    + "extendedKeyUsage=serverAuth,clientAuth\n"
                    + "subjectAltName=URI:urn:magrathea:node:" + identity
                    + ",DNS:" + identity + ",DNS:localhost,IP:127.0.0.1\n");
            run("openssl", "req", "-new", "-newkey", "rsa:2048", "-nodes", "-subj",
                    "/CN=" + name, "-keyout", key.toString(), "-out", request.toString());
            run("openssl", "x509", "-req", "-days", "2", "-in", request.toString(),
                    "-CA", caCertificate.toString(), "-CAkey", caKey.toString(),
                    "-CAcreateserial", "-extfile", extensions.toString(), "-out",
                    certificate.toString());
            return new Material(certificate, key, caCertificate);
        }
        private static void run(String... command) throws Exception {
            Process process = new ProcessBuilder(command).redirectErrorStream(true).start();
            String output = new String(process.getInputStream().readAllBytes());
            if (!process.waitFor(30, TimeUnit.SECONDS) || process.exitValue() != 0) {
                throw new IOException(String.join(" ", command) + " failed: " + output);
            }
        }
    }
}
