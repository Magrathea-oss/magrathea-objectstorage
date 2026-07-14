package com.example.magrathea.cluster.control.ratis.ep10;

import com.example.magrathea.cluster.control.ratis.ControlSnapshotCheckpoint;
import com.example.magrathea.cluster.control.ratis.FixedThreeNodeRatisCluster;
import com.example.magrathea.cluster.control.ratis.RatisTlsConfig;
import com.example.magrathea.cluster.data.grpc.FileLocalArtifactStore;
import com.example.magrathea.cluster.data.grpc.GrpcReplicaClient;
import com.example.magrathea.cluster.data.grpc.GrpcReplicaServer;
import com.example.magrathea.cluster.data.grpc.ReplicaTlsConfig;
import com.example.magrathea.cluster.data.grpc.ReplicaTransferFaultPlan;
import com.example.magrathea.cluster.data.grpc.ReplicaTransferLimits;
import com.example.magrathea.cluster.data.grpc.ReplicaTransferMetrics;
import com.example.magrathea.storageengine.cluster.application.BucketNamespace;
import com.example.magrathea.storageengine.cluster.application.ClusterControlPlanePort;
import com.example.magrathea.storageengine.cluster.application.ClusterMember;
import com.example.magrathea.storageengine.cluster.application.ClusterObjectMetadata;
import com.example.magrathea.storageengine.cluster.application.ClusterRepairMetrics;
import com.example.magrathea.storageengine.cluster.application.ClusterRepairScheduler;
import com.example.magrathea.storageengine.cluster.application.ClusterRepairWorker;
import com.example.magrathea.storageengine.cluster.application.LocalArtifactPort;
import com.example.magrathea.storageengine.cluster.application.MembershipSnapshot;
import com.example.magrathea.storageengine.cluster.application.NodeIdentity;
import com.example.magrathea.storageengine.cluster.application.ObjectReferenceGeneration;
import com.example.magrathea.storageengine.cluster.application.PublicationProposal;
import com.example.magrathea.storageengine.cluster.application.RepairCommandResult;
import com.example.magrathea.storageengine.cluster.application.RepairCommands;
import com.example.magrathea.storageengine.cluster.application.RepairExecutionGate;
import com.example.magrathea.storageengine.cluster.application.RepairHistoryEntry;
import com.example.magrathea.storageengine.cluster.application.RepairJob;
import com.example.magrathea.storageengine.cluster.application.RepairJobId;
import com.example.magrathea.storageengine.cluster.application.RepairJobQuery;
import com.example.magrathea.storageengine.cluster.application.RepairRetryPolicy;
import com.example.magrathea.storageengine.cluster.application.RepairSpecification;
import com.example.magrathea.storageengine.cluster.application.RepairState;
import com.example.magrathea.storageengine.cluster.application.ReplicaAcknowledgement;
import com.example.magrathea.storageengine.cluster.application.ReplicaReadPort;
import com.example.magrathea.storageengine.cluster.application.TransferRequest;
import com.example.magrathea.storageengine.cluster.application.TransferResult;
import io.cucumber.java.After;
import io.cucumber.java.Before;
import io.cucumber.java.en.Given;
import io.cucumber.java.en.Then;
import io.cucumber.java.en.When;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.nio.ByteBuffer;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.nio.file.attribute.BasicFileAttributes;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.HexFormat;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.AtomicReference;
import java.util.stream.Stream;

/** Exact Story BDD glue for the seven REQ-CLUSTER-024 real data-path examples. */
public final class ReqCluster024RealDataPathSteps {
    private static final NodeIdentity A = NodeIdentity.parse(
            "11111111-1111-4111-8111-111111111111");
    private static final NodeIdentity B = NodeIdentity.parse(
            "22222222-2222-4222-8222-222222222222");
    private static final NodeIdentity C = NodeIdentity.parse(
            "33333333-3333-4333-8333-333333333333");
    private static final String BUCKET = "ep10-repair-archive";
    private static final String KEY = "evidence/2026/current-generation-repair.bin";
    private static final String ARTIFACT =
            "whole-7f351d76-50d8-4f48-9b86-6f94e777a101";
    private static final String JOB =
            "repair-e0e88640c5538c99201e0fa7201b08fdb6024ecef4fc739640e2d3ef3a1dcdd2";
    private static final String SHA =
            "46918899a9ddbe1d1c2f1613416501b3e8d7cdbc2a63a78298d0cc3ee388e800";
    private static final String MODE = "fixed A/B/C real-process repair with Ratis persistence, "
            + "gRPC/mTLS transfer, filesystem inspection, and replica-read attempt counting";
    private static final String FIRST_SESSION = "req-cluster-024-b-session-11";
    private static final String RECOVERY_SESSION = "req-cluster-024-b-session-12";
    private static final Path ROOT = Path.of("target/ep10/three-node");
    private static final Path PKI = Path.of("target/ep10/pki");
    private static final Path B_OBJECTS = ROOT.resolve("node-b/objects");
    private static final Path B_TEMPORARY = ROOT.resolve("node-b/temporary");
    private static final Path C_OBJECTS = ROOT.resolve("node-c/objects");
    private static final Path C_TEMPORARY = ROOT.resolve("node-c/temporary");
    private static final Instant BASE = Instant.parse("2026-07-14T04:00:00Z");
    private static final Instant FIRST_PROCESS_TIME = BASE.plusSeconds(100);
    private static final Instant RECOVERY_PROCESS_TIME = BASE.plusSeconds(103);
    private static final Duration WAIT = Duration.ofSeconds(20);
    private static final ReplicaTransferLimits FRAME_LIMITS = new ReplicaTransferLimits(
            1, 1, 64, 1_048_576, 1, Duration.ofSeconds(30), Duration.ofSeconds(10));

    private final MembershipSnapshot membership = new MembershipSnapshot(List.of(
            new ClusterMember("A", A, "127.0.0.1", 19801),
            new ClusterMember("B", B, "127.0.0.1", 19802),
            new ClusterMember("C", C, "127.0.0.1", 19803)),
            "topology-1", "policy-1");
    private final RepairRetryPolicy retryPolicy = new RepairRetryPolicy(
            20, Duration.ofMillis(1), Duration.ofMillis(1), Duration.ofSeconds(1));
    private final List<CheckpointEvent> checkpointEvents = new CopyOnWriteArrayList<>();
    private final List<StagingObservation> stagingObservations = new CopyOnWriteArrayList<>();
    private final List<TargetObservation> targetObservations = new CopyOnWriteArrayList<>();
    private final TransferEvidence transfers = new TransferEvidence();
    private final Set<String> parsedCheckpointLines = new java.util.HashSet<>();
    private final Set<String> parsedRpcLines = new java.util.HashSet<>();
    private final Set<Long> bProcessPids = new java.util.HashSet<>();

    private Map<NodeIdentity, TestCertificateAuthority.Material> materials;
    private Map<NodeIdentity, RatisTlsConfig> ratisTls;
    private ReplicaTlsConfig bDataTls;
    private ReplicaTlsConfig cDataTls;
    private FixedThreeNodeRatisCluster cluster;
    private ClusterControlPlanePort control;
    private FileLocalArtifactStore sourceStore;
    private FileLocalArtifactStore targetStore;
    private GrpcReplicaServer sourceServer;
    private Process bProcess;
    private Path activeProcessRoot;
    private long activeProcessPid;
    private int processOrdinal;
    private RepairSpecification specification;
    private RepairJobId jobId;
    private ObjectReferenceGeneration reference;
    private byte[] fixture;
    private Path sourcePath;
    private Path targetPath;
    private Path claim11Payload;
    private Path claim12Payload;
    private String validationMode;
    private String requirement;
    private String failurePoint;
    private int attemptsAtInterruption;
    private long sourceOpensAtInterruption;
    private FileFingerprint durableTargetAtInterruption;
    private long baselineSnapshotIndex;
    private long appliedIndexBeforeCrash;
    private boolean bCrashed;
    private boolean bRestarted;
    private boolean leaderTransferred;
    private boolean staleTokenRejected;
    private boolean schedulerRecoveryObserved;
    private boolean snapshotFailureObserved;
    private Path interruptedSnapshotPath;
    private int interruptedSnapshotVersion;
    private RepairCommands.Succeed completionReplay;
    private RepairCommandResult committedCompletionResult;
    private RepairCommandResult duplicateCompletionResult;
    private int historyBeforeDuplicateCompletion;

    @Before(value = "@REQ-CLUSTER-024", order = 20_000)
    public void cleanFocusedRoots() throws Exception {
        deleteRecursively(ROOT);
        deleteRecursively(PKI);
    }

    @After(value = "@REQ-CLUSTER-024", order = 20_000)
    public void closeFocusedRuntime() {
        stopBProcess();
        if (sourceServer != null) sourceServer.close();
        sourceServer = null;
        if (cluster != null) cluster.close();
        cluster = null;
    }

    @Given("validation mode {string} is selected for requirement {string}")
    public void validationModeIsSelected(String selectedMode, String selectedRequirement) {
        require(MODE.equals(selectedMode), "REQ-CLUSTER-024 validation mode changed");
        require("REQ-CLUSTER-024".equals(selectedRequirement),
                "unexpected focused requirement");
        validationMode = selectedMode;
        requirement = selectedRequirement;
    }

    @Given("current consensus-committed {string} reference generation {int} for bucket {string} and key {string} names artifact {string} on source C and target B")
    public void currentConsensusReference(String kind, int generation, String bucket,
            String key, String artifact) throws Exception {
        require("WHOLE_OBJECT".equals(kind) && generation == 7,
                "unexpected reference type or generation");
        require(BUCKET.equals(bucket) && KEY.equals(key) && ARTIFACT.equals(artifact),
                "unexpected immutable reference facts");
        startCluster();
        control.createBucket(bucket).block(WAIT);
        for (int next = 1; next <= generation; next++) {
            String operation = "req-cluster-024-reference-" + next;
            ClusterObjectMetadata metadata = new ClusterObjectMetadata(
                    "STANDARD", Map.of(), Map.of(), "", BASE.plusSeconds(next));
            List<ReplicaAcknowledgement> acknowledgements = List.of(
                    acknowledgement(operation, B), acknowledgement(operation, C));
            reference = control.compareAndPublish(new PublicationProposal(
                    bucket, key, next - 1L, operation, artifact, 134, SHA,
                    membership.topologyEpoch(), membership.policyEpoch(), Set.of(A, B, C),
                    acknowledgements, metadata)).block(WAIT);
        }
        require(reference != null && reference.generation() == 7
                        && Set.copyOf(reference.replicas()).equals(Set.of(B, C)),
                "Ratis did not commit the exact B/C reference obligation");
    }

    @Given("fixture {string} has length {int} and SHA-{int} {string}")
    public void fixtureHasLengthAndSha(String path, int length, int bits, String sha)
            throws Exception {
        require(bits == 256 && length == 134 && SHA.equals(sha),
                "unexpected fixture facts");
        Path fixturePath = projectPath(path);
        require(Files.isRegularFile(fixturePath),
                "fixture path is not a regular file: " + fixturePath);
        fixture = Files.readAllBytes(fixturePath);
        require(fixture.length == length && SHA.equals(sha256(fixture)),
                "fixture bytes do not match declared length and SHA-256");
    }

    @Given("source replica C path {string} is byte-for-byte equal to that fixture")
    public void sourceReplicaCIsFixture(String path) throws Exception {
        require(fixture != null, "fixture was not loaded before source setup");
        sourceStore = new FileLocalArtifactStore(C_OBJECTS, C_TEMPORARY, C);
        sourcePath = sourceStore.publishedPath(ARTIFACT);
        require(sourcePath.equals(Path.of(path)), "source C path changed");
        try (LocalArtifactPort.IncomingSink incoming = sourceStore.beginIncoming(
                "req-cluster-024-source-seed", ARTIFACT)) {
            incoming.accept(ByteBuffer.wrap(fixture.clone()));
            incoming.publish();
        }
        require(Arrays.equals(fixture, Files.readAllBytes(sourcePath)),
                "source C was not seeded with the exact fixture");
        sourceServer = new GrpcReplicaServer(
                new InetSocketAddress("127.0.0.1", 19903), cDataTls, sourceStore,
                new ReplicaTransferMetrics(), Duration.ZERO, FRAME_LIMITS,
                ReplicaTransferFaultPlan.none()).start();
        require(sourceServer.port() == 19903, "source C did not bind fixed port 19903");
    }

    @Given("target B path {string} is initially absent")
    public void targetBIsInitiallyAbsent(String path) throws Exception {
        targetStore = new FileLocalArtifactStore(B_OBJECTS, B_TEMPORARY, B);
        targetPath = targetStore.publishedPath(ARTIFACT);
        require(targetPath.equals(Path.of(path)), "target B path changed");
        require(!Files.exists(targetPath), "target B was pre-seeded");
        observeTarget("initial target precondition");
    }

    @Given("the one canonical consensus-owned job {string} targets B and has next admissible claim generation {int}")
    public void canonicalJobHasNextClaim(String expectedJob, int nextClaim) throws Exception {
        jobId = RepairJobId.canonical(BUCKET, KEY, 7, ARTIFACT, B);
        require(JOB.equals(expectedJob) && expectedJob.equals(jobId.toString()),
                "canonical repair identity changed");
        require(nextClaim == 11, "focused gate requires claim generation 11");
        specification = new RepairSpecification(
                BUCKET, KEY, 7, ARTIFACT, B, 134, SHA,
                membership.topologyEpoch(), membership.policyEpoch(), retryPolicy);
        require(specification.jobId().equals(jobId), "specification changed job identity");
        RepairCommandResult ensured = control.ensureRepair(new RepairCommands.Ensure(
                "req-cluster-024-ensure", specification, BASE.plusSeconds(20), C)).block(WAIT);
        require(ensured != null && ensured.accepted(), "repair ensure was not committed");

        Instant claimAt = BASE.plusSeconds(21);
        for (int generation = 1; generation <= 10; generation++) {
            String session = "req-cluster-024-history-" + generation;
            RepairCommandResult claimed = control.claimRepair(new RepairCommands.Claim(
                    "req-cluster-024-history-claim-" + generation, jobId, B, session,
                    claimAt, claimAt.plusSeconds(1), C)).block(WAIT);
            require(claimed != null && claimed.accepted()
                            && claimed.claimGeneration() == generation,
                    "historical claim generation was not committed monotonically");
            Instant transitionAt = claimAt.plusMillis(1);
            if (generation < 10) {
                RepairCommandResult retry = control.retryRepair(new RepairCommands.Retry(
                        "req-cluster-024-history-retry-" + generation, jobId, generation,
                        B, session, transitionAt, "bounded historical setup retry")).block(WAIT);
                require(retry != null && retry.accepted(),
                        "historical retry was not committed");
                claimAt = transitionAt.plusMillis(1);
            } else {
                RepairCommandResult blocked = control.blockRepair(new RepairCommands.Block(
                        "req-cluster-024-history-block", jobId, generation, B, session,
                        transitionAt, "historical setup complete before source admission"))
                        .block(WAIT);
                require(blocked != null && blocked.accepted(),
                        "historical setup block was not committed");
            }
        }
        RepairCommandResult ready = control.reevaluateRepair(new RepairCommands.Reevaluate(
                "req-cluster-024-history-ready", jobId, claimAt.plusSeconds(1),
                "source C admitted for focused real data-path validation", C)).block(WAIT);
        RepairJob prepared = job();
        require(ready != null && ready.accepted() && prepared.state() == RepairState.READY
                        && prepared.attemptNumber() == 10
                        && prepared.claimGeneration() == 10,
                "job was not READY with next admissible generation 11");

        awaitAllApplied();
        cluster.snapshot(A);
        baselineSnapshotIndex = cluster.snapshot(B);
        cluster.snapshot(C);
        require(baselineSnapshotIndex > 0 && regularFileCount(nodeRoot(B).resolve("ratis")) > 0,
                "B did not persist non-empty Ratis state before interruption");
    }

    @Given("claim {int} may stage only at {string}, while a post-restart claim {int} may stage only at {string}")
    public void claimsMayUseOnlyTokenPaths(int firstGeneration, String firstPath,
            int secondGeneration, String secondPath) {
        require(firstGeneration == 11 && secondGeneration == 12,
                "unexpected claim generations in staging contract");
        claim11Payload = payloadPath(11);
        claim12Payload = payloadPath(12);
        require(claim11Payload.equals(Path.of(firstPath))
                        && claim12Payload.equals(Path.of(secondPath)),
                "token-specific staging path changed");
        require(!Files.exists(claim11Payload) && !Files.exists(claim12Payload),
                "claim staging existed before execution");
    }

    @When("the focused requirement gate reaches {string}")
    public void focusedGateReaches(String point) throws Exception {
        failurePoint = point;
        RepairExecutionGate.Checkpoint checkpoint = checkpointFor(point);
        if (point.contains("while A is the Ratis leader")) ensureLeader(A);
        startRepairProcess(FIRST_SESSION, FIRST_PROCESS_TIME, checkpoint);
        ExternalCheckpoint reached = readCheckpoint(activeProcessRoot.resolve(
                "checkpoint.reached"));
        refreshExternalEvidence();
        require(reached.jobId().equals(jobId.toString()),
                "checkpoint observed a different repair job");
        require(reached.pid() == activeProcessPid,
                "checkpoint did not come from the active B JVM");
        if (checkpoint == RepairExecutionGate.Checkpoint.BEFORE_CLAIM_PROPOSED) {
            require(reached.claimGeneration() == 10,
                    "before-claim checkpoint did not preserve next generation 11");
        } else {
            require(reached.claimGeneration() == 11,
                    "focused interruption did not bind claim generation 11");
        }
        if (checkpoint == RepairExecutionGate.Checkpoint.PAYLOAD_BYTES_STAGED) {
            require(reached.stagedBytes() > 0 && reached.stagedBytes() < fixture.length,
                    "bounded transfer checkpoint was not a strict fixture prefix");
        }
        attemptsAtInterruption = transfers.count();
        sourceOpensAtInterruption = sourceStore.publishedOpenCount(ARTIFACT);
        durableTargetAtInterruption = observeTarget("focused interruption checkpoint");
        if (checkpoint == RepairExecutionGate.Checkpoint
                .COMPLETION_COMMITTED_BEFORE_ACKNOWLEDGEMENT) {
            captureCommittedCompletion();
        }
    }

    @Then("immediately before interruption, claim and staging inspection reports {string}")
    public void claimAndStagingInspectionReports(String expected) throws Exception {
        RepairJob current = job();
        switch (expected) {
            case "the job is READY and neither the claim-11 directory nor payload.part exists" -> {
                require(current.state() == RepairState.READY
                                && current.claimGeneration() == 10,
                        "READY checkpoint changed committed counters");
                require(!Files.exists(claim11Payload.getParent())
                                && !Files.exists(claim11Payload),
                        "claim-11 staging existed before claim");
            }
            case "claim 11 is committed and claim-11 payload.part does not exist" -> {
                require(current.state() == RepairState.CLAIMED
                                && current.claimGeneration() == 11,
                        "claim 11 was not committed");
                require(!Files.exists(claim11Payload),
                        "payload existed before replica read");
            }
            case "claim 11 is committed and claim-11 payload.part is a non-empty strict fixture prefix" -> {
                require(current.state() == RepairState.CLAIMED
                                && current.claimGeneration() == 11,
                        "partial transfer lost claim 11");
                byte[] prefix = Files.readAllBytes(claim11Payload);
                require(prefix.length > 0 && prefix.length < fixture.length
                                && Arrays.equals(prefix, Arrays.copyOf(fixture, prefix.length)),
                        "claim-11 payload was not a strict fixture prefix");
            }
            case "claim-11 staging has been atomically removed after publication" -> {
                require(current.state() == RepairState.CLAIMED
                                && current.claimGeneration() == 11,
                        "publication checkpoint no longer owned claim 11");
                require(!Files.exists(claim11Payload.getParent()),
                        "claim-11 directory survived atomic publication");
            }
            case "claim-11 staging is absent and the committed job is SUCCEEDED" -> {
                require(current.state() == RepairState.SUCCEEDED,
                        "completion was not committed before reply withholding");
                require(!Files.exists(claim11Payload),
                        "committed completion retained staging");
            }
            case "live B owns claim 11 and claim-11 payload.part does not exist" -> {
                require(current.state() == RepairState.CLAIMED
                                && current.claimGeneration() == 11
                                && current.claim().owner().equals(B)
                                && current.claim().processSession().equals(FIRST_SESSION),
                        "live B did not own claim 11");
                require(!Files.exists(claim11Payload),
                        "leader-change claim staged before transfer");
            }
            default -> throw new AssertionError("unsupported claim inspection: " + expected);
        }
        observeTarget("claim and staging inspection");
    }

    @Then("replica-read instrumentation reports {string}")
    public void replicaReadInstrumentationReports(String expected) throws Exception {
        switch (expected) {
            case "zero replica-read RPCs" -> require(transfers.count() == 0
                            && sourceStore.publishedOpenCount(ARTIFACT) == 0,
                    "replica read opened before the selected boundary");
            case "one live mTLS gRPC read from C has delivered fewer than 134 bytes" -> {
                require(transfers.count() == 1
                                && sourceStore.publishedOpenCount(ARTIFACT) == 1,
                        "partial transfer was not one actual source-C read");
                require(Files.size(claim11Payload) > 0
                                && Files.size(claim11Payload) < fixture.length,
                        "live read did not stop at a strict prefix");
            }
            case "one mTLS gRPC read from C delivered and verified exactly 134 bytes" -> {
                require(transfers.count() == 1
                                && sourceStore.publishedOpenCount(ARTIFACT) == 1,
                        "exact transfer was not one actual source-C read");
                require(durableTargetAtInterruption != null,
                        "verified read did not durably publish the target");
            }
            default -> throw new AssertionError("unsupported transfer inspection: " + expected);
        }
        observeTarget("replica-read instrumentation");
    }

    @Then("target filesystem inspection reports {string}")
    public void targetFilesystemInspectionReports(String expected) throws Exception {
        if (expected.equals("absent")
                || expected.equals("absent while partial bytes exist only in claim-11 staging")) {
            require(!Files.exists(targetPath), "partial or premature target became visible");
            if (expected.contains("partial bytes")) {
                require(Files.isRegularFile(claim11Payload),
                        "strict prefix was not isolated in claim-11 staging");
            }
        } else if (expected.startsWith("present with length 134 and SHA-256 ")) {
            requireExactTarget();
        } else {
            throw new AssertionError("unsupported target inspection: " + expected);
        }
        observeTarget("target filesystem inspection");
    }

    @When("validation performs {string} at that exact semantic point")
    public void validationPerformsInterruption(String action) throws Exception {
        if (action.startsWith("transfer Ratis leadership from A to C")) {
            require(awaitLeader().equals(A), "A was not leader at transfer boundary");
            cluster.transferLeadership(A, C);
            awaitLeader(C);
            leaderTransferred = true;
            Files.writeString(activeProcessRoot.resolve("checkpoint.release"), "release\n");
            awaitSucceeded();
        } else {
            if (action.startsWith("crash B during its snapshot write")) {
                Files.writeString(activeProcessRoot.resolve("snapshot.request"), "snapshot\n");
                waitFor(() -> Files.isRegularFile(
                                activeProcessRoot.resolve("snapshot.reached"))
                                && Files.isRegularFile(
                                        activeProcessRoot.resolve("snapshot.failed")),
                        WAIT, "B JVM did not reach the version-2 snapshot interruption");
                Map<String, String> snapshot = properties(
                        activeProcessRoot.resolve("snapshot.reached"));
                interruptedSnapshotVersion = Integer.parseInt(snapshot.get("version"));
                interruptedSnapshotPath = Path.of(snapshot.get("path"));
                snapshotFailureObserved = true;
            }
            crashB();
            restartBAndRecover();
        }
        refreshExternalEvidence();
        if (completionReplay != null) replayCommittedCompletion();
        require(job().state() == RepairState.SUCCEEDED,
                "interruption recovery did not reach SUCCEEDED");
        observeTarget("after interruption recovery");
    }

    @Then("a crashed B process recovers from its original non-empty roots, or the named leader action completes while B remains alive")
    public void crashedBRecoversOrLeaderActionCompletes() throws Exception {
        if (leaderTransferred) {
            require(!bCrashed && bProcess != null && bProcess.isAlive()
                            && awaitLeader().equals(C),
                    "live B JVM or named C leadership was lost");
        } else {
            require(bCrashed && bRestarted && bProcess != null && bProcess.isAlive(),
                    "B was not restarted as an independent JVM");
            require(regularFileCount(nodeRoot(B).resolve("identity")) > 0
                            && regularFileCount(nodeRoot(B).resolve("ratis")) > 0,
                    "B did not recover from its original non-empty roots");
            require(readProcessLastApplied() >= appliedIndexBeforeCrash,
                    "B did not restore persisted state through the interruption index");
        }
        if (failurePoint.contains("snapshot version 2")) {
            require(snapshotFailureObserved && interruptedSnapshotPath != null
                            && interruptedSnapshotVersion == 2,
                    "version-2 snapshot interruption was not observed before restart");
            require(appliedIndexBeforeCrash > baselineSnapshotIndex,
                    "claim 11 was not later than B's last valid baseline snapshot");
        }
    }

    @Then("the scheduler recovers the one committed job before proposing work, with monotonic attempt and claim generations and no authority for a stale token")
    public void schedulerRecoversBeforeWork() {
        List<String> operations = processControlOperations(activeProcessPid);
        int repairQuery = operations.indexOf("REPAIR_QUERY");
        int claim = operations.indexOf("CLAIM");
        require(repairQuery >= 0, "scheduler did not query committed repair work");
        if (claim >= 0) {
            require(repairQuery < claim,
                    "scheduler proposed a claim before committed-state discovery");
        }
        RepairJob current = job();
        long previousAttempt = 0;
        long previousGeneration = 0;
        for (RepairHistoryEntry entry : current.history()) {
            require(entry.attemptNumber() >= previousAttempt
                            && entry.claimGeneration() >= previousGeneration,
                    "attempt or claim generation regressed in committed history");
            previousAttempt = entry.attemptNumber();
            previousGeneration = entry.claimGeneration();
        }
        if (current.claimGeneration() == 12) {
            int historySize = current.history().size();
            RepairCommandResult stale = control.succeedRepair(new RepairCommands.Succeed(
                    "req-cluster-024-stale-claim-11-" + failurePoint.hashCode(), jobId,
                    11, B, FIRST_SESSION, RECOVERY_PROCESS_TIME.plusSeconds(1),
                    134, SHA, "stale process completion after reclaim")).block(WAIT);
            staleTokenRejected = stale != null && !stale.accepted()
                    && stale.code() == RepairCommandResult.Code.STALE_TOKEN
                    && job().history().size() == historySize;
            require(staleTokenRejected, "stale claim 11 retained lifecycle authority");
        } else {
            staleTokenRejected = true;
        }
        schedulerRecoveryObserved = true;
    }

    @Then("direct-repair evidence across interruption and recovery is {string}")
    public void directRepairEvidenceIs(String expected) {
        List<Long> generations = transfers.attempts().stream()
                .map(TransferAttempt::claimGeneration).toList();
        switch (expected) {
            case "one post-restart claim-11 mTLS gRPC read from C and no other replica-read RPC" ->
                    require(generations.equals(List.of(11L))
                                    && attemptsAtInterruption == 0 && bRestarted,
                            "READY recovery transfer evidence changed");
            case "one post-restart claim-12 mTLS gRPC read from C and no other replica-read RPC" ->
                    require(generations.equals(List.of(12L))
                                    && attemptsAtInterruption == 0 && bRestarted,
                            "reclaimed transfer evidence changed");
            case "the interrupted claim-11 RPC plus one complete claim-12 mTLS gRPC read from C and no third RPC" ->
                    require(generations.equals(List.of(11L, 12L))
                                    && attemptsAtInterruption == 1 && bRestarted,
                            "partial-read recovery did not use exactly two fenced RPCs");
            case "the one completed claim-11 mTLS gRPC read from C and zero replica-read RPCs after restart" ->
                    require(generations.equals(List.of(11L))
                                    && attemptsAtInterruption == 1
                                    && transfers.count() == attemptsAtInterruption,
                            "durable target was recopied after restart");
            case "one claim-11 mTLS gRPC read from C after leader transfer and no duplicate replica-read RPC" ->
                    require(generations.equals(List.of(11L))
                                    && attemptsAtInterruption == 0 && leaderTransferred,
                            "leader transfer duplicated or replaced the live claim");
            default -> throw new AssertionError("unsupported direct repair evidence: " + expected);
        }
        require(sourceStore.publishedOpenCount(ARTIFACT) == transfers.count(),
                "client attempt count and actual source filesystem opens differ");
    }

    @Then("every expected transfer is an actual grpc-java replica-read RPC opened by B to source C at {string} with B and C mutually authenticated by their UUID-bound certificates under {string} and {string}")
    public void transfersAreActualMutualTlsGrpc(String endpoint, String bPki, String cPki) {
        require(endpoint.equals("127.0.0.1:19903") && sourceServer.port() == 19903,
                "source C endpoint changed");
        require(Path.of(bPki).equals(PKI.resolve("nodes/B"))
                        && Path.of(cPki).equals(PKI.resolve("nodes/C")),
                "UUID-bound certificate roots changed");
        require(bDataTls.localIdentity().equals(B) && cDataTls.localIdentity().equals(C)
                        && bDataTls.acceptedPeers().equals(Set.of(A, B, C))
                        && cDataTls.acceptedPeers().equals(Set.of(A, B, C)),
                "replica mTLS identity policy was not fixed A/B/C");
        require(transfers.attempts().stream().allMatch(attempt ->
                        attempt.client().equals(B) && attempt.source().equals(C)
                                && attempt.address().equals(new InetSocketAddress(
                                        "127.0.0.1", 19903))
                                && attempt.clientCertificate().startsWith(
                                        absolute(PKI.resolve("nodes/B")))
                                && bProcessPids.contains(attempt.pid())
                                && attempt.pid() != ProcessHandle.current().pid()),
                "one transfer did not use B's grpc-java client and UUID-bound material");
        require(sourceStore.publishedOpenCount(ARTIFACT) == transfers.count(),
                "mTLS RPC evidence did not reach source C's FileLocalArtifactStore");
    }

    @Then("no in-memory replica-read port, Ratis command, target pre-seeding, or test-file copy counts as replica transfer evidence")
    public void noFakeTransferEvidence() throws Exception {
        require(sourceStore.getClass().equals(FileLocalArtifactStore.class)
                        && targetStore.getClass().equals(FileLocalArtifactStore.class)
                        && sourceServer.getClass().equals(GrpcReplicaServer.class),
                "real filesystem or grpc-java adapter was replaced");
        require(transfers.count() > 0
                        && sourceStore.publishedOpenCount(ARTIFACT) == transfers.count(),
                "transfer evidence came from a call count without source filesystem reads");
        require(Arrays.equals(fixture, Files.readAllBytes(sourcePath)),
                "source setup changed during repair");
        requireExactTarget();
    }

    @Then("each received byte is written only to the current token's {string} before incremental length and SHA-{int} verification, file fsync, atomic target publication, and parent-directory fsync")
    public void bytesUseOnlyCurrentTokenPayload(String payloadName, int bits) throws Exception {
        require(payloadName.equals("payload.part") && bits == 256,
                "repair staging contract changed");
        require(!stagingObservations.isEmpty(),
                "no actual staging observation was captured");
        for (StagingObservation observation : stagingObservations) {
            require(observation.path().equals(payloadPath(observation.claimGeneration()))
                            && observation.path().getFileName().toString().equals(payloadName)
                            && observation.stagedBytes() > 0
                            && observation.stagedBytes() <= fixture.length,
                    "bytes escaped the current claim token's payload.part");
        }
        long publications = checkpointEvents.stream()
                .filter(event -> event.checkpoint() == RepairExecutionGate.Checkpoint
                        .TARGET_DURABLY_PUBLISHED_BEFORE_COMPLETION)
                .count();
        require(publications == 1, "target publication was not singular");
        requireExactTarget();
        List<Path> remaining = repairTemporaryFiles();
        if (!remaining.isEmpty()) {
            require(failurePoint.startsWith(
                            "claim 11 has staged a non-empty strict prefix")
                            && remaining.equals(List.of(claim11Payload)),
                    "only fenced claim-11 crash staging may remain: " + remaining);
            byte[] prefix = Files.readAllBytes(claim11Payload);
            require(prefix.length > 0 && prefix.length < fixture.length
                            && Arrays.equals(prefix, Arrays.copyOf(fixture, prefix.length)),
                    "remaining claim-11 staging is not the interrupted fixture prefix");
        }
    }

    @Then("at every observation the target path is either absent or the exact {int}-byte fixture, never a partial or checksum-invalid publication")
    public void targetAlwaysAbsentOrExact(int length) throws Exception {
        require(length == fixture.length, "target length contract changed");
        observeTarget("final target invariant");
        require(targetObservations.stream().noneMatch(TargetObservation::invalid),
                "partial or checksum-invalid target was observed");
        require(targetObservations.stream().anyMatch(TargetObservation::exact),
                "exact target was never observed");
    }

    @Then("recovery reconciliation is {string}")
    public void recoveryReconciliationIs(String expected) {
        RepairJob current = job();
        List<Long> claims = current.history().stream()
                .filter(entry -> entry.toState() == RepairState.CLAIMED)
                .map(RepairHistoryEntry::claimGeneration).toList();
        List<Long> publicationClaims = checkpointEvents.stream()
                .filter(event -> event.checkpoint() == RepairExecutionGate.Checkpoint
                        .TARGET_DURABLY_PUBLISHED_BEFORE_COMPLETION)
                .map(event -> event.observation().claimGeneration()).toList();
        switch (expected) {
            case "claim 11 publishes once and commits SUCCEEDED once" ->
                    require(claims.getLast() == 11 && publicationClaims.equals(List.of(11L)),
                            "claim 11 did not publish exactly once");
            case "stale claim 11 cannot publish; claim 12 publishes once and commits SUCCEEDED once" ->
                    require(claims.containsAll(List.of(11L, 12L))
                                    && publicationClaims.equals(List.of(12L))
                                    && staleTokenRejected,
                            "claim-12 fencing reconciliation changed");
            case "partial claim-11 staging is never published; claim 12 publishes once and commits SUCCEEDED once" ->
                    require(stagingObservations.stream().anyMatch(observation ->
                                    observation.claimGeneration() == 11
                                            && observation.stagedBytes() < fixture.length)
                                    && publicationClaims.equals(List.of(12L)),
                            "partial claim-11 bytes were published or claim 12 did not publish");
            case "claim 12 probes the already-exact target and commits SUCCEEDED without recopy" ->
                    require(claims.getLast() == 12 && publicationClaims.equals(List.of(11L))
                                    && alreadyValidCount() == 1,
                            "already-valid claim-12 reconciliation recopied the target");
            case "restored SUCCEEDED and duplicate completion return the committed result without another claim or recopy" ->
                    require(current.claimGeneration() == 11
                                    && duplicateCompletionResult != null
                                    && duplicateCompletionResult.equals(committedCompletionResult)
                                    && current.history().size() == historyBeforeDuplicateCompletion
                                    && publicationClaims.equals(List.of(11L)),
                            "committed completion replay was not idempotent");
            case "restored claim fencing rejects claim 11; claim 12 publishes once and commits SUCCEEDED once" ->
                    require(snapshotFailureObserved && staleTokenRejected
                                    && publicationClaims.equals(List.of(12L)),
                            "snapshot recovery did not restore claim fencing");
            case "the still-current claim publishes once and completion commits under the new leader without a replacement claim" ->
                    require(leaderTransferred && awaitLeaderUnchecked().equals(C)
                                    && claims.getLast() == 11 && !claims.contains(12L)
                                    && publicationClaims.equals(List.of(11L)),
                            "live leadership transfer replaced or duplicated the claim");
            default -> throw new AssertionError("unsupported reconciliation: " + expected);
        }
        require(current.history().stream()
                        .filter(entry -> entry.toState() == RepairState.SUCCEEDED).count() == 1,
                "SUCCEEDED transition was not singular");
    }

    @Then("if the exact target was durable before interruption, recovery probes it in place without another replica-read RPC, another staging payload, or replacement of its bytes")
    public void durableTargetIsProbedWithoutRecopy() throws Exception {
        if (durableTargetAtInterruption == null) return;
        require(transfers.count() == attemptsAtInterruption
                        && sourceStore.publishedOpenCount(ARTIFACT)
                                == sourceOpensAtInterruption,
                "recovery opened another replica read for an exact target");
        require(checkpointEvents.stream().noneMatch(event ->
                        event.checkpoint() == RepairExecutionGate.Checkpoint.PAYLOAD_BYTES_STAGED
                                && event.observation().claimGeneration() == 12),
                "already-valid recovery created claim-12 staging");
        require(durableTargetAtInterruption.equals(fingerprint(targetPath)),
                "already-valid target bytes or file identity were replaced");
    }

    @Then("the final target is byte-for-byte equal to the fixture with length {int} and SHA-{int} {string}")
    public void finalTargetEqualsFixture(int length, int bits, String sha) throws Exception {
        require(length == 134 && bits == 256 && SHA.equals(sha),
                "final target expectation changed");
        requireExactTarget();
        require(Arrays.equals(fixture, Files.readAllBytes(targetPath)),
                "final target differs byte-for-byte from fixture");
    }

    @Then("the one repair job commits or restores {string} exactly once while reference generation {int}, its artifact, the fixed A\\/B\\/C topology, and its named replica obligation remain unchanged")
    public void oneRepairJobSucceedsWithReferenceUnchanged(String state, int generation) {
        require("SUCCEEDED".equals(state) && generation == 7,
                "unexpected terminal requirement facts");
        List<RepairJob> jobs = control.repairJobs(RepairJobQuery.all())
                .collectList().block(WAIT);
        RepairJob current = job();
        ObjectReferenceGeneration restored = control.objectReference(BUCKET, KEY).block(WAIT);
        require(jobs != null && jobs.size() == 1 && current.state() == RepairState.SUCCEEDED
                        && current.history().stream().filter(entry ->
                                entry.toState() == RepairState.SUCCEEDED).count() == 1,
                "logical repair job or terminal transition was duplicated");
        require(restored != null && restored.generation() == 7
                        && restored.artifactId().equals(ARTIFACT)
                        && Set.copyOf(restored.replicas()).equals(Set.of(B, C))
                        && control.membership().block(WAIT).voterIdentities()
                                .equals(Set.of(A, B, C)),
                "repair rewrote reference generation, artifact, obligation, or topology");
        require(schedulerRecoveryObserved, "scheduler recovery evidence was not checked");
    }

    @Then("the focused semantic gate does not provide a general chaos or partition engine, periodic or broad anti-entropy, rebalance, orphan cleanup, superseded-generation collection, or reference rewriting")
    public void focusedGateHasNoBroadEngine() {
        require(requirement.equals("REQ-CLUSTER-024") && validationMode.equals(MODE),
                "focused gate scope changed");
        require(checkpointEvents.stream().allMatch(event -> Set.of(
                        RepairExecutionGate.Checkpoint.BEFORE_CLAIM_PROPOSED,
                        RepairExecutionGate.Checkpoint.CLAIM_COMMITTED_BEFORE_TRANSFER,
                        RepairExecutionGate.Checkpoint.PAYLOAD_BYTES_STAGED,
                        RepairExecutionGate.Checkpoint.BEFORE_TARGET_PUBLICATION,
                        RepairExecutionGate.Checkpoint.TARGET_DURABLY_PUBLISHED_BEFORE_COMPLETION,
                        RepairExecutionGate.Checkpoint
                                .COMPLETION_COMMITTED_BEFORE_ACKNOWLEDGEMENT)
                        .contains(event.checkpoint())),
                "focused gate exposed an unrelated chaos operation");
        System.out.println("REQ_CLUSTER_024_GREEN failurePoint=\"" + failurePoint
                + "\" rpcGenerations=" + transfers.attempts().stream()
                        .map(TransferAttempt::claimGeneration).toList()
                + " claimGeneration=" + job().claimGeneration()
                + " restarted=" + bRestarted
                + " bJvmPids=" + bProcessPids
                + " leader=" + awaitLeaderUnchecked()
                + " snapshotInterrupted=" + snapshotFailureObserved);
    }

    private void startCluster() throws Exception {
        TestCertificateAuthority authority = new TestCertificateAuthority(PKI);
        Map<NodeIdentity, TestCertificateAuthority.Material> generated = new LinkedHashMap<>();
        generated.put(A, authority.create("A", A));
        generated.put(B, authority.create("B", B));
        generated.put(C, authority.create("C", C));
        materials = Map.copyOf(generated);
        ratisTls = Map.of(
                A, ratisTls(materials.get(A), A),
                B, ratisTls(materials.get(B), B),
                C, ratisTls(materials.get(C), C));
        bDataTls = dataTls(materials.get(B), B);
        cDataTls = dataTls(materials.get(C), C);
        Map<NodeIdentity, ControlSnapshotCheckpoint> snapshotCheckpoints = Map.of(
                A, ControlSnapshotCheckpoint.open(),
                B, ControlSnapshotCheckpoint.open(),
                C, ControlSnapshotCheckpoint.open());
        cluster = new FixedThreeNodeRatisCluster(
                membership, roots("identity"), roots("ratis"), ratisTls, ratisTls.get(A),
                snapshotCheckpoints);
        cluster.start(List.of(A, B, C)).block(Duration.ofSeconds(15));
        control = cluster.controlPlane(ratisTls.get(A));
        awaitLeader();
    }

    private void startRepairProcess(String session, Instant now,
            RepairExecutionGate.Checkpoint checkpoint) throws Exception {
        if (cluster.runningVoters().contains(B)) {
            cluster.stopBlocking(B);
            awaitLeader();
        }
        processOrdinal++;
        activeProcessRoot = nodeRoot(B).resolve("runtime/req-cluster-024/process-"
                + processOrdinal);
        deleteRecursively(activeProcessRoot);
        Files.createDirectories(activeProcessRoot);
        Path evidenceRoot = nodeRoot(B).resolve("runtime/req-cluster-024/evidence");
        Files.createDirectories(evidenceRoot);
        TestCertificateAuthority.Material bMaterial = materials.get(B);
        List<String> command = new ArrayList<>();
        command.add(Path.of(System.getProperty("java.home"), "bin", "java").toString());
        command.add("-cp");
        command.add(System.getProperty("java.class.path"));
        command.add(ReqCluster024RepairProcess.class.getName());
        command.add("--session=" + session);
        command.add("--clock=" + now);
        command.add("--checkpoint=" + (checkpoint == null ? "OPEN" : checkpoint.name()));
        command.add("--run-root=" + absolute(activeProcessRoot));
        command.add("--evidence-root=" + absolute(evidenceRoot));
        command.add("--identity-root=" + absolute(nodeRoot(B).resolve("identity")));
        command.add("--ratis-root=" + absolute(nodeRoot(B).resolve("ratis")));
        command.add("--objects-root=" + absolute(B_OBJECTS));
        command.add("--temporary-root=" + absolute(B_TEMPORARY));
        command.add("--certificate=" + absolute(bMaterial.certificate()));
        command.add("--private-key=" + absolute(bMaterial.key()));
        command.add("--ca-certificate=" + absolute(bMaterial.ca()));
        Path log = activeProcessRoot.resolve("process.log");
        bProcess = new ProcessBuilder(command).redirectErrorStream(true)
                .redirectOutput(ProcessBuilder.Redirect.appendTo(log.toFile())).start();
        activeProcessPid = bProcess.pid();
        bProcessPids.add(activeProcessPid);
        waitFor(() -> bProcess.isAlive()
                        && Files.isRegularFile(activeProcessRoot.resolve("status.properties")),
                WAIT, "independent B JVM did not become ready; log=" + log);
        require(Long.parseLong(properties(activeProcessRoot.resolve("status.properties"))
                        .get("pid")) == activeProcessPid,
                "B process status came from a different JVM");
    }

    private void crashB() throws Exception {
        long committedIndex = cluster.lastAppliedIndex(awaitLeader());
        waitFor(() -> readProcessLastApplied() >= committedIndex, WAIT,
                "B JVM did not apply the interruption-point control state");
        appliedIndexBeforeCrash = readProcessLastApplied();
        require(appliedIndexBeforeCrash >= baselineSnapshotIndex,
                "B lost committed state before crash");
        require(regularFileCount(nodeRoot(B).resolve("identity")) > 0
                        && regularFileCount(nodeRoot(B).resolve("ratis")) > 0,
                "B roots were empty at crash");
        long crashedPid = activeProcessPid;
        stopBProcess();
        bCrashed = true;
        require(ProcessHandle.of(crashedPid).isEmpty()
                        || !ProcessHandle.of(crashedPid).orElseThrow().isAlive(),
                "B JVM remained alive after forced crash");
        require(cluster.runningVoters().equals(Set.of(A, C)),
                "A/C quorum was not retained while B was stopped");
        require(control.membership().block(WAIT).voterIdentities().equals(Set.of(A, B, C)),
                "surviving quorum lost fixed committed membership");
    }

    private void restartBAndRecover() throws Exception {
        targetStore = new FileLocalArtifactStore(B_OBJECTS, B_TEMPORARY, B);
        startRepairProcess(RECOVERY_SESSION, RECOVERY_PROCESS_TIME, null);
        waitFor(() -> readProcessLastApplied() >= appliedIndexBeforeCrash,
                WAIT, "B did not replay persisted snapshot and log state");
        bRestarted = true;
        awaitSucceeded();
    }

    private void awaitSucceeded() throws Exception {
        waitFor(() -> {
            try {
                return bProcess != null && bProcess.isAlive()
                        && job().state() == RepairState.SUCCEEDED;
            } catch (RuntimeException unavailable) {
                return false;
            }
        }, WAIT, "repair did not commit SUCCEEDED; last state=" + safeJobState());
        refreshExternalEvidence();
    }

    private void captureCommittedCompletion() {
        RepairJob committed = job();
        require(committed.state() == RepairState.SUCCEEDED,
                "completion checkpoint was reached before commit");
        RepairHistoryEntry succeeded = committed.history().stream()
                .filter(entry -> entry.toState() == RepairState.SUCCEEDED)
                .findFirst().orElseThrow();
        committedCompletionResult = committed.commandResults().stream()
                .filter(result -> result.commandId().equals(succeeded.commandId()))
                .findFirst().orElseThrow();
        completionReplay = new RepairCommands.Succeed(
                succeeded.commandId(), jobId, 11, B, FIRST_SESSION,
                succeeded.occurredAt(), 134, SHA, "exact durable publication");
    }

    private void replayCommittedCompletion() {
        RepairJob before = job();
        historyBeforeDuplicateCompletion = before.history().size();
        duplicateCompletionResult = control.succeedRepair(completionReplay).block(WAIT);
        require(duplicateCompletionResult != null
                        && duplicateCompletionResult.equals(committedCompletionResult)
                        && job().history().size() == historyBeforeDuplicateCompletion,
                "duplicate completion did not return its committed result");
    }

    private RepairExecutionGate.Checkpoint checkpointFor(String point) {
        if (point.startsWith("READY ensure is committed")) {
            return RepairExecutionGate.Checkpoint.BEFORE_CLAIM_PROPOSED;
        }
        if (point.startsWith("claim 11 is committed")
                || point.startsWith("B writes snapshot version 2")
                || point.startsWith("claim 11 is active before transfer")) {
            return RepairExecutionGate.Checkpoint.CLAIM_COMMITTED_BEFORE_TRANSFER;
        }
        if (point.startsWith("claim 11 has staged a non-empty strict prefix")) {
            return RepairExecutionGate.Checkpoint.PAYLOAD_BYTES_STAGED;
        }
        if (point.startsWith("claim 11 has durably published")) {
            return RepairExecutionGate.Checkpoint
                    .TARGET_DURABLY_PUBLISHED_BEFORE_COMPLETION;
        }
        if (point.startsWith("claim-11 completion is committed")) {
            return RepairExecutionGate.Checkpoint
                    .COMPLETION_COMMITTED_BEFORE_ACKNOWLEDGEMENT;
        }
        throw new AssertionError("unsupported failure point: " + point);
    }

    private ExternalCheckpoint readCheckpoint(Path path) throws Exception {
        waitFor(() -> bProcess != null && bProcess.isAlive() && Files.isRegularFile(path),
                WAIT, "independent B JVM did not reach the selected repair checkpoint");
        Map<String, String> values = properties(path);
        return new ExternalCheckpoint(
                RepairExecutionGate.Checkpoint.valueOf(values.get("checkpoint")),
                Long.parseLong(values.get("claimGeneration")),
                Long.parseLong(values.get("stagedBytes")), values.get("jobId"),
                Long.parseLong(values.get("pid")));
    }

    private void refreshExternalEvidence() throws Exception {
        Path evidenceRoot = nodeRoot(B).resolve("runtime/req-cluster-024/evidence");
        Path checkpointLog = evidenceRoot.resolve("checkpoints.log");
        if (Files.isRegularFile(checkpointLog)) {
            for (String line : Files.readAllLines(checkpointLog)) {
                if (line.isBlank() || !parsedCheckpointLines.add(line)) continue;
                String[] fields = line.split("\\t", -1);
                require(fields.length == 6 && fields[1].equals("CHECKPOINT"),
                        "invalid checkpoint evidence line");
                RepairExecutionGate.Checkpoint checkpoint =
                        RepairExecutionGate.Checkpoint.valueOf(fields[2]);
                long generation = Long.parseLong(fields[3]);
                long staged = Long.parseLong(fields[4]);
                RepairExecutionGate.Observation observation =
                        new RepairExecutionGate.Observation(jobId, generation, staged);
                checkpointEvents.add(new CheckpointEvent(checkpoint, observation));
                if (checkpoint == RepairExecutionGate.Checkpoint.PAYLOAD_BYTES_STAGED) {
                    stagingObservations.add(new StagingObservation(
                            generation, payloadPath(generation), staged));
                }
            }
        }
        Path rpcLog = evidenceRoot.resolve("rpc-attempts.log");
        if (Files.isRegularFile(rpcLog)) {
            for (String line : Files.readAllLines(rpcLog)) {
                if (line.isBlank() || !parsedRpcLines.add(line)) continue;
                String[] fields = line.split("\\t", -1);
                require(fields.length == 6 && fields[1].equals("RPC"),
                        "invalid replica-read evidence line");
                transfers.record(new TransferAttempt(
                        B, C, new InetSocketAddress("127.0.0.1", 19903),
                        Long.parseLong(fields[2]), fields[3],
                        Long.parseLong(fields[4]), Path.of(fields[5])));
            }
        }
    }

    private List<String> processControlOperations(long pid) {
        Path log = nodeRoot(B).resolve(
                "runtime/req-cluster-024/evidence/control-operations.log");
        if (!Files.isRegularFile(log)) return List.of();
        try {
            List<String> operations = new ArrayList<>();
            for (String line : Files.readAllLines(log)) {
                String[] fields = line.split("\\t", -1);
                if (fields.length == 4 && fields[1].equals("CONTROL")
                        && Long.parseLong(fields[3]) == pid) {
                    operations.add(fields[2]);
                }
            }
            return List.copyOf(operations);
        } catch (IOException failure) {
            throw new AssertionError(failure);
        }
    }

    private long readProcessLastApplied() throws Exception {
        require(activeProcessRoot != null, "B process root is absent");
        Path status = activeProcessRoot.resolve("status.properties");
        waitFor(() -> Files.isRegularFile(status), WAIT,
                "B process status was not persisted");
        return Long.parseLong(properties(status).get("lastApplied"));
    }

    private void stopBProcess() {
        Process process = bProcess;
        bProcess = null;
        if (process == null) return;
        process.destroyForcibly();
        try {
            if (!process.waitFor(10, TimeUnit.SECONDS)) process.destroyForcibly();
        } catch (InterruptedException interrupted) {
            Thread.currentThread().interrupt();
        }
    }

    private static Map<String, String> properties(Path path) throws IOException {
        Map<String, String> values = new LinkedHashMap<>();
        for (String line : Files.readAllLines(path)) {
            int separator = line.indexOf('=');
            if (separator > 0) {
                values.put(line.substring(0, separator), line.substring(separator + 1));
            }
        }
        return Map.copyOf(values);
    }

    private static Path absolute(Path path) {
        return path.toAbsolutePath().normalize();
    }

    private synchronized FileFingerprint observeTarget(String stage) throws Exception {
        if (!Files.exists(targetPath)) {
            targetObservations.add(new TargetObservation(stage, false, false, -1, ""));
            return null;
        }
        long length = Files.size(targetPath);
        String digest = sha256(Files.readAllBytes(targetPath));
        boolean exact = length == fixture.length && digest.equals(SHA)
                && Arrays.equals(fixture, Files.readAllBytes(targetPath));
        targetObservations.add(new TargetObservation(stage, true, exact, length, digest));
        require(exact, "invalid target observed at " + stage + ": " + length + "/" + digest);
        return fingerprint(targetPath);
    }

    private void requireExactTarget() throws Exception {
        require(Files.isRegularFile(targetPath), "final target is absent");
        byte[] bytes = Files.readAllBytes(targetPath);
        require(bytes.length == 134 && sha256(bytes).equals(SHA)
                        && Arrays.equals(bytes, fixture),
                "target is partial or checksum-invalid");
    }

    private FileFingerprint fingerprint(Path path) throws Exception {
        BasicFileAttributes attributes = Files.readAttributes(path, BasicFileAttributes.class);
        Object fileKey = attributes.fileKey();
        return new FileFingerprint(attributes.size(), sha256(Files.readAllBytes(path)),
                fileKey == null ? "" : fileKey.toString(),
                attributes.lastModifiedTime().toMillis());
    }

    private void awaitAllApplied() throws Exception {
        long committed = cluster.lastAppliedIndex(awaitLeader());
        waitFor(() -> List.of(A, B, C).stream()
                        .allMatch(identity -> cluster.lastAppliedIndex(identity) >= committed),
                WAIT, "fixed voters did not apply committed repair setup");
    }

    private NodeIdentity awaitLeader() throws Exception {
        AtomicReference<NodeIdentity> selected = new AtomicReference<>();
        waitFor(() -> {
            Optional<NodeIdentity> leader = cluster.leaderIdentity();
            leader.ifPresent(selected::set);
            return leader.isPresent();
        }, WAIT, "Ratis leader was not ready");
        return selected.get();
    }

    private void awaitLeader(NodeIdentity expected) throws Exception {
        waitFor(() -> cluster.leaderIdentity().filter(expected::equals).isPresent(), WAIT,
                "Ratis leadership did not transfer to " + expected);
    }

    private void ensureLeader(NodeIdentity expected) throws Exception {
        NodeIdentity current = awaitLeader();
        if (!current.equals(expected)) {
            cluster.transferLeadership(current, expected);
            awaitLeader(expected);
        }
    }

    private NodeIdentity awaitLeaderUnchecked() {
        try {
            return awaitLeader();
        } catch (Exception failure) {
            throw new AssertionError(failure);
        }
    }

    private RepairJob job() {
        RepairJob current = control.repairJob(jobId).block(WAIT);
        if (current == null) throw new AssertionError("repair job query returned no result");
        return current;
    }

    private String safeJobState() {
        try {
            RepairJob current = job();
            return current.state() + "/" + current.reason();
        } catch (RuntimeException unavailable) {
            return unavailable.toString();
        }
    }

    private long alreadyValidCount() {
        Path processRoot = nodeRoot(B).resolve("runtime/req-cluster-024");
        if (!Files.isDirectory(processRoot)) return 0;
        try (Stream<Path> paths = Files.list(processRoot)) {
            long total = 0;
            for (Path process : paths.filter(path -> path.getFileName().toString()
                    .startsWith("process-")).toList()) {
                Path status = process.resolve("status.properties");
                if (Files.isRegularFile(status)) {
                    total += Long.parseLong(properties(status).getOrDefault(
                            "alreadyValid", "0"));
                }
            }
            return total;
        } catch (IOException failure) {
            throw new AssertionError(failure);
        }
    }

    private List<Path> repairTemporaryFiles() throws IOException {
        Path repairRoot = B_TEMPORARY.resolve("repair");
        if (!Files.isDirectory(repairRoot)) return List.of();
        try (Stream<Path> paths = Files.walk(repairRoot)) {
            return paths.filter(Files::isRegularFile).sorted().toList();
        }
    }

    private Path payloadPath(long claimGeneration) {
        return B_TEMPORARY.resolve("repair").resolve(JOB)
                .resolve(Long.toString(claimGeneration)).resolve("payload.part");
    }

    private ReplicaAcknowledgement acknowledgement(String operation, NodeIdentity node) {
        return new ReplicaAcknowledgement(operation, ARTIFACT, node, 134, SHA,
                membership.topologyEpoch(), membership.policyEpoch(), true);
    }

    private static RatisTlsConfig ratisTls(
            TestCertificateAuthority.Material material, NodeIdentity identity) {
        return new RatisTlsConfig(material.certificate(), material.key(), material.ca(),
                identity, Set.of(A, B, C));
    }

    private static ReplicaTlsConfig dataTls(
            TestCertificateAuthority.Material material, NodeIdentity identity) {
        return new ReplicaTlsConfig(material.certificate(), material.key(), material.ca(),
                identity, Set.of(A, B, C));
    }

    private static Map<NodeIdentity, Path> roots(String child) {
        return Map.of(
                A, nodeRoot(A).resolve(child),
                B, nodeRoot(B).resolve(child),
                C, nodeRoot(C).resolve(child));
    }

    private static Path projectPath(String path) {
        Path direct = Path.of(path);
        if (Files.isRegularFile(direct)) return direct;
        String reactorRoot = System.getProperty("maven.multiModuleProjectDirectory");
        if (reactorRoot != null && !reactorRoot.isBlank()) {
            Path resolved = Path.of(reactorRoot).resolve(path);
            if (Files.isRegularFile(resolved)) return resolved;
        }
        return Path.of("..").resolve(path).normalize();
    }

    private static Path nodeRoot(NodeIdentity identity) {
        return ROOT.resolve(identity.equals(A) ? "node-a"
                : identity.equals(B) ? "node-b" : "node-c");
    }

    private static long regularFileCount(Path root) throws IOException {
        if (!Files.isDirectory(root)) return 0;
        try (Stream<Path> paths = Files.walk(root)) {
            return paths.filter(Files::isRegularFile).count();
        }
    }

    private static void waitFor(CheckedBoolean condition, Duration timeout, String message)
            throws Exception {
        long deadline = System.nanoTime() + timeout.toNanos();
        Throwable last = null;
        while (System.nanoTime() < deadline) {
            try {
                if (condition.get()) return;
            } catch (Throwable failure) {
                last = failure;
            }
            Thread.sleep(20);
        }
        AssertionError timeoutFailure = new AssertionError(message);
        if (last != null) timeoutFailure.initCause(last);
        throw timeoutFailure;
    }

    private static String sha256(byte[] bytes) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(bytes));
        } catch (NoSuchAlgorithmException impossible) {
            throw new AssertionError(impossible);
        }
    }

    private static void deleteRecursively(Path root) throws IOException {
        if (!Files.exists(root)) return;
        try (Stream<Path> paths = Files.walk(root)) {
            for (Path path : paths.sorted(Comparator.reverseOrder()).toList()) {
                Files.deleteIfExists(path);
            }
        }
    }

    private static void require(boolean condition, String message) {
        if (!condition) throw new AssertionError(message);
    }

    @FunctionalInterface
    private interface CheckedBoolean {
        boolean get() throws Exception;
    }

    private static final class TransferEvidence {
        private final List<TransferAttempt> attempts = new CopyOnWriteArrayList<>();

        void record(TransferAttempt attempt) {
            attempts.add(attempt);
        }

        int count() {
            return attempts.size();
        }

        List<TransferAttempt> attempts() {
            return List.copyOf(attempts);
        }
    }

    private record ExternalCheckpoint(
            RepairExecutionGate.Checkpoint checkpoint, long claimGeneration,
            long stagedBytes, String jobId, long pid) { }

    private record CheckpointEvent(
            RepairExecutionGate.Checkpoint checkpoint,
            RepairExecutionGate.Observation observation) { }

    private record StagingObservation(
            long claimGeneration, Path path, long stagedBytes) { }

    private record TargetObservation(
            String stage, boolean present, boolean exact, long length, String sha256) {
        boolean invalid() {
            return present && !exact;
        }
    }

    private record FileFingerprint(
            long length, String sha256, String fileKey, long modifiedMillis) { }

    private record TransferAttempt(
            NodeIdentity client, NodeIdentity source, InetSocketAddress address,
            long claimGeneration, String operationId, long pid,
            Path clientCertificate) { }
}
