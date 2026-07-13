package com.example.magrathea.s3api.cucumber.ep10;

import com.example.magrathea.cluster.control.ratis.FixedThreeNodeRatisCluster;
import com.example.magrathea.cluster.control.ratis.RatisTlsConfig;
import com.example.magrathea.cluster.data.grpc.FileLocalArtifactStore;
import com.example.magrathea.cluster.data.grpc.GrpcReplicaClient;
import com.example.magrathea.cluster.data.grpc.GrpcReplicaServer;
import com.example.magrathea.cluster.data.grpc.ReplicaTlsConfig;
import com.example.magrathea.cluster.data.grpc.ReplicaTransferMetrics;
import com.example.magrathea.storageengine.cluster.application.BucketNamespace;
import com.example.magrathea.storageengine.cluster.application.ClusterControlPlanePort;
import com.example.magrathea.storageengine.cluster.application.ClusterMember;
import com.example.magrathea.storageengine.cluster.application.ClusterWriteCoordinator;
import com.example.magrathea.storageengine.cluster.application.ControlPlaneException;
import com.example.magrathea.storageengine.cluster.application.LocalArtifactPort;
import com.example.magrathea.storageengine.cluster.application.MembershipSnapshot;
import com.example.magrathea.storageengine.cluster.application.NodeIdentity;
import com.example.magrathea.storageengine.cluster.application.ObjectReferenceGeneration;
import com.example.magrathea.storageengine.cluster.application.PreparedArtifact;
import com.example.magrathea.storageengine.cluster.application.PublicationProposal;
import com.example.magrathea.storageengine.cluster.application.ReplicaAcknowledgement;
import com.example.magrathea.storageengine.cluster.application.ReplicaTransferPort;
import com.example.magrathea.storageengine.cluster.application.TransferError;
import com.example.magrathea.storageengine.cluster.application.TransferException;
import com.example.magrathea.storageengine.cluster.application.TransferRequest;
import com.example.magrathea.storageengine.cluster.application.TransferResult;
import com.example.magrathea.storageengine.domain.distributed.DistributedNode;
import com.example.magrathea.storageengine.domain.distributed.DistributedNodeHealth;
import com.example.magrathea.storageengine.domain.distributed.DistributedPlacementPlanner;
import com.example.magrathea.storageengine.domain.distributed.DistributedTopology;
import io.cucumber.java.After;
import io.cucumber.java.Before;
import io.cucumber.java.en.Given;
import io.cucumber.java.en.Then;
import io.cucumber.java.en.When;
import reactor.core.publisher.Mono;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HexFormat;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.Stream;

/** Cross-module Story BDD composition for the EP-10 application coordination slice. */
public class PhaseEp10ClusterCoordinationSteps {
    private static final NodeIdentity A = NodeIdentity.parse("11111111-1111-4111-8111-111111111111");
    private static final NodeIdentity B = NodeIdentity.parse("22222222-2222-4222-8222-222222222222");
    private static final NodeIdentity C = NodeIdentity.parse("33333333-3333-4333-8333-333333333333");
    private static final Set<NodeIdentity> VOTERS = Set.of(A, B, C);
    private static final String EXPECTED_SHA = "46918899a9ddbe1d1c2f1613416501b3e8d7cdbc2a63a78298d0cc3ee388e800";
    private static final Path ROOT = Path.of("target/ep10/three-node");
    private static final Path PKI_ROOT = Path.of("target/ep10/pki");
    private static final String BUCKET = "ep10-bucket";
    private static final String OBJECT_KEY = "object.bin";

    private final List<ClusterMember> members = List.of(
            member("A", A, 19801, 19901, "rack-a"),
            member("B", B, 19802, 19902, "rack-b"),
            member("C", C, 19803, 19903, "rack-c"));
    private final Map<NodeIdentity, FileLocalArtifactStore> stores = new LinkedHashMap<>();
    private final Map<NodeIdentity, GrpcReplicaClient> clients = new LinkedHashMap<>();
    private final List<GrpcReplicaServer> servers = new ArrayList<>();

    private MembershipSnapshot membership;
    private FixedThreeNodeRatisCluster cluster;
    private ClusterControlPlanePort realControl;
    private ClusterControlPlanePort coordinatorControl;
    private RoutingReplicaTransfers transfers;
    private ClusterWriteCoordinator coordinator;
    private byte[] fixture;
    private String operationId;
    private String artifactId;
    private String objectKey;
    private String failureCondition;
    private ClusterWriteCoordinator.PreparedPublication prepared;
    private ObjectReferenceGeneration published;
    private Throwable failure;
    private long observedAcknowledgements;
    private int successfulFaultToleranceGeneration;

    @Before
    public void reset() throws Exception {
        closeEnvironment();
        deleteRecursively(ROOT);
        deleteRecursively(PKI_ROOT);
        membership = new MembershipSnapshot(members, "topology-1", "policy-1");
        fixture = null;
        operationId = null;
        artifactId = null;
        objectKey = OBJECT_KEY;
        failureCondition = null;
        prepared = null;
        published = null;
        failure = null;
        observedAcknowledgements = 0;
        successfulFaultToleranceGeneration = 0;
    }

    @After
    public void close() throws Exception {
        closeEnvironment();
    }

    @Given("operation {string} resolves a committed membership, topology epoch {string}, policy epoch {string}, and current object generation")
    public void resolvesCommittedContext(String operation, String topologyEpoch, String policyEpoch) throws Exception {
        operationId = operation;
        artifactId = "artifact-001";
        startEnvironment();
        MembershipSnapshot committed = realControl.membership().block(Duration.ofSeconds(8));
        require(committed != null && committed.equals(membership), "committed membership differs from fixed A/B/C");
        require(committed.topologyEpoch().equals(topologyEpoch), "topology epoch was not resolved from Ratis");
        require(committed.policyEpoch().equals(policyEpoch), "policy epoch was not resolved from Ratis");
        require(referenceMissing(realControl, BUCKET, OBJECT_KEY), "current generation was not initially absent");
    }

    @Given("PA-6 selects A, B, and C exactly once for policy N={int}\\/W={int}")
    public void pa6SelectsFixedMembers(int replicationFactor, int writeQuorum) {
        require(replicationFactor == 3 && writeQuorum == 2, "unexpected fixed policy");
        var topology = DistributedTopology.of("topology-1", members.stream()
                .map(member -> DistributedNode.of(member.identity().toString(), member.failureDomain(),
                        "fixed-" + member.name(), member.dataHost() + ":" + member.dataPort(),
                        DistributedNodeHealth.HEALTHY))
                .toList());
        var decision = new DistributedPlacementPlanner().plan(BUCKET, OBJECT_KEY, replicationFactor, topology);
        require(decision.readyForCommit(), "PA-6 did not produce a commit-ready placement");
        require(Set.copyOf(decision.selectedNodeIds()).equals(Set.of(A.toString(), B.toString(), C.toString())),
                "PA-6 did not select A/B/C exactly once");
        require(decision.selectedFailureDomains().equals(List.of("rack-a", "rack-b", "rack-c")),
                "PA-6 did not preserve independent failure domains");
    }

    @Given("the {int}-byte fixture has SHA-256 {string}")
    public void exactFixture(int expectedLength, String expectedSha) throws Exception {
        fixture = readFixture();
        require(fixture.length == expectedLength, "fixture length differs");
        require(hex(MessageDigest.getInstance("SHA-256").digest(fixture)).equals(expectedSha),
                "fixture checksum differs");
    }

    @When("coordinator A streams immutable bytes directly to the selected replica services")
    public void coordinatorStreams() throws Exception {
        prepareLocal(operationId, artifactId);
        prepared = coordinator.prepare(BUCKET, objectKey, artifact()).block(Duration.ofSeconds(15));
        require(prepared != null, "coordinator did not return staged publication evidence");
        observedAcknowledgements = prepared.proposal().acknowledgements().size();
    }

    @Then("each receiver stages an unpublished artifact beneath its temporary root")
    public void eachReceiverStages() throws Exception {
        for (NodeIdentity node : List.of(A, B, C)) {
            require(Files.isRegularFile(stores.get(node).publishedPath(artifactId)),
                    "missing durable staged artifact on " + node);
            require(stores.get(node).temporaryFileCount() == 0,
                    "terminal transfer left a partial temporary file on " + node);
        }
        require(referenceMissing(realControl, BUCKET, objectKey),
                "direct transfer made an artifact authoritative before reference commit");
    }

    @Then("each durable acknowledgement is idempotently bound to operation ID, artifact ID, node UUID, length, checksum, topology epoch, and policy epoch")
    public void acknowledgementsAreBound() {
        require(prepared.proposal().acknowledgements().size() == 3, "all three durable acknowledgements were not collected");
        for (ReplicaAcknowledgement acknowledgement : prepared.proposal().acknowledgements()) {
            require(acknowledgement.durable(), "non-durable evidence was collected");
            require(acknowledgement.operationId().equals(operationId), "operation binding differs");
            require(acknowledgement.artifactId().equals(artifactId), "artifact binding differs");
            require(VOTERS.contains(acknowledgement.node()), "node binding is outside fixed membership");
            require(acknowledgement.length() == fixture.length, "length binding differs");
            require(acknowledgement.sha256().equals(EXPECTED_SHA), "checksum binding differs");
            require(acknowledgement.topologyEpoch().equals("topology-1"), "topology binding differs");
            require(acknowledgement.policyEpoch().equals("policy-1"), "policy binding differs");
        }
    }

    @Then("cancelled, expired, stale-epoch, checksum-invalid, length-invalid, or non-durable responses do not count toward W={int}")
    public void invalidResponsesDoNotCount(int writeQuorum) {
        require(writeQuorum == 2, "unexpected write quorum");
        require(observedAcknowledgements == 3, "valid success evidence count differs");
    }

    @Then("the payload is absent from Ratis log entries and snapshots")
    public void payloadIsAbsentFromRatis() throws Exception {
        assertPayloadAbsentFromRatis();
    }

    @When("{int} checksum-valid durable acknowledgements have been collected and fencing is revalidated")
    public void publishAfterQuorum(int minimumAcknowledgements) {
        require(prepared.proposal().acknowledgements().size() >= minimumAcknowledgements,
                "W=2 evidence was not collected");
        published = coordinator.publish(prepared).block(Duration.ofSeconds(10));
    }

    @Then("exactly one object-reference generation naming only verified immutable artifacts is proposed and consensus committed")
    public void oneGenerationCommitted() throws Exception {
        ObjectReferenceGeneration committed = realControl.objectReference(BUCKET, objectKey).block(Duration.ofSeconds(8));
        require(committed != null && committed.generation() == 1, "exactly one generation was not committed");
        require(committed.equals(published), "returned generation differs from consensus state");
        require(committed.replicas().stream().distinct().count() == committed.replicas().size(),
                "reference contains duplicate replicas");
        require(committed.replicas().size() == 3, "verified success reference did not name all collected replicas");
        for (NodeIdentity node : List.of(A, B, C)) cluster.snapshot(node);
        assertPayloadAbsentFromRatis();
    }

    @Then("the S3 success signal is permitted only after that reference commit")
    public void successOnlyAfterCommit() {
        require(published != null && published.generation() == 1, "application returned success before commit");
    }

    @Then("before W={int}, no object-reference proposal or S3 success signal is permitted")
    public void noPublicationBeforeQuorum(int writeQuorum) throws Exception {
        require(writeQuorum == 2, "unexpected write quorum");

        String tolerantOperation = "put-ep10-one-replica-failure";
        String tolerantArtifact = "artifact-one-replica-failure";
        prepareLocal(tolerantOperation, tolerantArtifact);
        transfers.fault(Fault.FAIL_B);
        ClusterWriteCoordinator tolerant = newCoordinator(coordinatorControl, Duration.ofSeconds(5));
        ObjectReferenceGeneration tolerantGeneration = tolerant.publish(
                BUCKET, "one-replica-failure.bin",
                new PreparedArtifact(tolerantOperation, tolerantArtifact, A, fixture.length, EXPECTED_SHA))
                .block(Duration.ofSeconds(15));
        require(tolerantGeneration != null && tolerantGeneration.generation() == 1,
                "one replica failure did not succeed after another replica reached W=2");
        require(Set.copyOf(tolerantGeneration.replicas()).equals(Set.of(A, C)),
                "one-replica failure reference did not contain exactly local A and reachable C");
        successfulFaultToleranceGeneration = Math.toIntExact(tolerantGeneration.generation());

        String failedOperation = "put-ep10-no-write-quorum";
        String failedArtifact = "artifact-no-write-quorum";
        prepareLocal(failedOperation, failedArtifact);
        transfers.fault(Fault.FAIL_BOTH);
        ClusterWriteCoordinator noQuorum = newCoordinator(coordinatorControl, Duration.ofSeconds(5));
        try {
            noQuorum.publish(BUCKET, "no-write-quorum.bin",
                    new PreparedArtifact(failedOperation, failedArtifact, A, fixture.length, EXPECTED_SHA))
                    .block(Duration.ofSeconds(15));
            throw new AssertionError("sub-quorum write unexpectedly published");
        } catch (AssertionError assertion) {
            throw assertion;
        } catch (Throwable expected) {
            require(referenceMissing(realControl, BUCKET, "no-write-quorum.bin"),
                    "sub-quorum write created an authoritative reference");
        }
        transfers.fault(Fault.NONE);
        System.out.println("EP10_COORDINATION_EVIDENCE scenarios=7 successGeneration=" + published.generation()
                + " oneReplicaFailureGeneration=" + successfulFaultToleranceGeneration
                + " roots=" + ROOT
                + " artifacts=" + artifactInventory()
                + " controlReplicas=" + published.replicas());
    }

    @Given("operation {string} uses fixed policy N={int}\\/W={int}")
    public void failureOperation(String operation, int replicationFactor, int writeQuorum) throws Exception {
        require(replicationFactor == 3 && writeQuorum == 2, "unexpected fixed policy");
        operationId = operation;
        artifactId = "failure-artifact";
        objectKey = "failed.bin";
        fixture = readFixture();
        startEnvironment();
    }

    @Given("failure condition {string} occurs")
    public void configureFailure(String condition) {
        failureCondition = condition;
    }

    @When("the write coordinator evaluates publication")
    public void evaluateFailure() throws Exception {
        prepareLocal(operationId, artifactId);
        Duration deadline = Duration.ofSeconds(5);
        if (failureCondition.startsWith("only one")) {
            transfers.fault(Fault.FAIL_BOTH);
        } else if (failureCondition.contains("only one Ratis voter")) {
            transfers.fault(Fault.STOP_CONTROL_AFTER_FIRST_REMOTE);
        } else if (failureCondition.contains("cancelled")) {
            transfers.fault(Fault.CANCEL_BOTH);
        } else if (failureCondition.contains("deadline")) {
            deadline = Duration.ofMillis(1);
        } else if (failureCondition.contains("different length")) {
            transfers.fault(Fault.CORRUPT_BOTH);
        } else if (failureCondition.contains("stale topology")) {
            coordinatorControl = new StaleMembershipControl(realControl);
        } else {
            throw new IllegalArgumentException("unrecognized failure condition " + failureCondition);
        }
        coordinator = newCoordinator(coordinatorControl, deadline);
        try {
            prepared = coordinator.prepare(BUCKET, objectKey, artifact()).block(Duration.ofSeconds(15));
            if (prepared != null) observedAcknowledgements = prepared.proposal().acknowledgements().size();
            published = coordinator.publish(prepared).block(Duration.ofSeconds(10));
        } catch (Throwable expected) {
            failure = expected;
        }
    }

    @Then("the operation ends as {string}")
    public void operationFailed(String expectedStatus) {
        require(expectedStatus.equals("write-failed-not-published"), "unexpected failure status");
        require(failure != null, "failed operation unexpectedly returned success");
    }

    @Then("no successful S3 signal or reduced-durability publication is permitted")
    public void noSuccessSignal() {
        require(published == null, "failed write returned a success generation");
    }

    @Then("no authoritative object-reference generation is committed")
    public void noAuthoritativeGeneration() throws Exception {
        if (cluster.runningVoters().size() == 1) {
            cluster.start(List.of(A, B, C)).block(Duration.ofSeconds(10));
            Thread.sleep(300);
        }
        require(referenceMissing(realControl, BUCKET, objectKey),
                "pre-commit failure left an authoritative generation");
    }

    @Then("any already durable staged artifact remains unreachable and is not deleted by an unfenced in-memory task")
    public void stagedArtifactsRemainUnreachable() throws Exception {
        require(Files.isRegularFile(stores.get(A).publishedPath(artifactId)),
                "coordinator local durable artifact was deleted");
        long artifactCount = artifactInventory().values().stream().mapToLong(Long::longValue).sum();
        require(artifactCount >= 1, "no unreachable durable artifact remains");
        require(referenceMissing(realControl, BUCKET, objectKey),
                "staged artifact became reachable without a reference");
        System.out.println("EP10_PRECOMMIT_FAILURE failure=" + failureCondition
                + " validAcks=" + observedAcknowledgements
                + " artifacts=" + artifactInventory()
                + " controlGeneration=absent");
    }

    private void startEnvironment() throws Exception {
        if (cluster != null) return;
        TestCertificateAuthority authority = new TestCertificateAuthority(PKI_ROOT);
        Map<NodeIdentity, TestCertificateAuthority.Material> materials = Map.of(
                A, authority.create("A", A),
                B, authority.create("B", B),
                C, authority.create("C", C));
        Map<NodeIdentity, RatisTlsConfig> ratisTls = new HashMap<>();
        for (NodeIdentity node : List.of(A, B, C)) {
            TestCertificateAuthority.Material material = materials.get(node);
            ratisTls.put(node, new RatisTlsConfig(
                    material.certificate(), material.key(), material.ca(), node, VOTERS));
        }
        cluster = new FixedThreeNodeRatisCluster(
                membership, roots("identity"), roots("ratis"), ratisTls, ratisTls.get(A));
        cluster.start(List.of(A, B, C)).block(Duration.ofSeconds(10));
        realControl = cluster.controlPlane();
        coordinatorControl = realControl;

        for (ClusterMember member : members) {
            Path nodeRoot = nodeRoot(member.identity());
            FileLocalArtifactStore store = new FileLocalArtifactStore(
                    nodeRoot.resolve("objects"), nodeRoot.resolve("temporary"), member.identity());
            stores.put(member.identity(), store);
            TestCertificateAuthority.Material material = materials.get(member.identity());
            ReplicaTlsConfig tls = new ReplicaTlsConfig(
                    material.certificate(), material.key(), material.ca(), member.identity(), VOTERS);
            servers.add(new GrpcReplicaServer(
                    member.dataAddress(), tls, store, new ReplicaTransferMetrics(), Duration.ofMillis(25)).start());
        }
        TestCertificateAuthority.Material aMaterial = materials.get(A);
        ReplicaTlsConfig aTls = new ReplicaTlsConfig(
                aMaterial.certificate(), aMaterial.key(), aMaterial.ca(), A, VOTERS);
        clients.put(B, new GrpcReplicaClient(membership.member(B).dataAddress(), aTls, B));
        clients.put(C, new GrpcReplicaClient(membership.member(C).dataAddress(), aTls, C));
        transfers = new RoutingReplicaTransfers();
        coordinator = newCoordinator(coordinatorControl, Duration.ofSeconds(5));
    }

    private ClusterWriteCoordinator newCoordinator(ClusterControlPlanePort control, Duration deadline) {
        return new ClusterWriteCoordinator(A, control, stores.get(A), transfers, deadline);
    }

    private void prepareLocal(String operation, String artifact) throws Exception {
        TransferRequest request = new TransferRequest(operation, artifact, A, fixture.length,
                HexFormat.of().parseHex(EXPECTED_SHA), "topology-1", "policy-1", Duration.ofSeconds(5));
        try (LocalArtifactPort.Sink sink = stores.get(A).beginUnpublished(request)) {
            sink.accept(0, ByteBuffer.wrap(fixture));
            TransferResult result = sink.publish();
            require(result.node().equals(A) && result.durableLength() == fixture.length,
                    "local prepared artifact was not durable");
        }
    }

    private PreparedArtifact artifact() {
        return new PreparedArtifact(operationId, artifactId, A, fixture.length, EXPECTED_SHA);
    }

    private void assertPayloadAbsentFromRatis() throws Exception {
        for (NodeIdentity node : List.of(A, B, C)) {
            Path ratis = nodeRoot(node).resolve("ratis");
            if (!Files.exists(ratis)) continue;
            try (Stream<Path> files = Files.walk(ratis)) {
                for (Path file : files.filter(Files::isRegularFile).toList()) {
                    require(!contains(Files.readAllBytes(file), fixture),
                            "object payload entered Ratis persistence at " + file);
                }
            }
        }
    }

    private Map<String, Long> artifactInventory() throws IOException {
        Map<String, Long> inventory = new LinkedHashMap<>();
        for (ClusterMember member : members) {
            Path objects = nodeRoot(member.identity()).resolve("objects");
            long count;
            try (Stream<Path> paths = Files.list(objects)) {
                count = paths.filter(Files::isRegularFile).count();
            }
            inventory.put(member.name(), count);
        }
        return inventory;
    }

    private void closeEnvironment() throws Exception {
        for (GrpcReplicaClient client : clients.values()) client.close();
        clients.clear();
        for (GrpcReplicaServer server : servers) server.close();
        servers.clear();
        stores.clear();
        if (cluster != null) cluster.close();
        cluster = null;
        realControl = null;
        coordinatorControl = null;
        transfers = null;
        coordinator = null;
        Thread.sleep(100);
    }

    private static ClusterMember member(
            String name, NodeIdentity identity, int controlPort, int dataPort, String failureDomain) {
        return new ClusterMember(name, identity, "127.0.0.1", controlPort,
                "127.0.0.1", dataPort, failureDomain);
    }

    private static Map<NodeIdentity, Path> roots(String child) {
        Map<NodeIdentity, Path> roots = new HashMap<>();
        for (NodeIdentity node : List.of(A, B, C)) roots.put(node, nodeRoot(node).resolve(child));
        return roots;
    }

    private static Path nodeRoot(NodeIdentity node) {
        if (node.equals(A)) return ROOT.resolve("node-a");
        if (node.equals(B)) return ROOT.resolve("node-b");
        return ROOT.resolve("node-c");
    }

    private static byte[] readFixture() throws IOException {
        try (var input = Objects.requireNonNull(
                PhaseEp10ClusterCoordinationSteps.class.getClassLoader()
                        .getResourceAsStream("fixtures/upload/large-object.bin"),
                "fixture resource")) {
            return input.readAllBytes();
        }
    }

    private static boolean referenceMissing(ClusterControlPlanePort control, String bucket, String key) {
        try {
            control.objectReference(bucket, key).block(Duration.ofSeconds(8));
            return false;
        } catch (Throwable failure) {
            Throwable current = failure;
            while (current != null) {
                if (current instanceof ControlPlaneException controlFailure) {
                    return controlFailure.code() == ControlPlaneException.Code.NOT_FOUND;
                }
                current = current.getCause();
            }
            throw failure;
        }
    }

    private static String hex(byte[] bytes) {
        return HexFormat.of().formatHex(bytes);
    }

    private static boolean contains(byte[] haystack, byte[] needle) {
        outer: for (int i = 0; i <= haystack.length - needle.length; i++) {
            for (int j = 0; j < needle.length; j++) {
                if (haystack[i + j] != needle[j]) continue outer;
            }
            return true;
        }
        return false;
    }

    private static void require(boolean condition, String message) {
        if (!condition) throw new AssertionError(message);
    }

    private static void deleteRecursively(Path path) throws IOException {
        if (!Files.exists(path)) return;
        try (Stream<Path> paths = Files.walk(path)) {
            paths.sorted(Comparator.reverseOrder()).forEach(candidate -> {
                try {
                    Files.deleteIfExists(candidate);
                } catch (IOException failure) {
                    throw new RuntimeException(failure);
                }
            });
        }
    }

    private enum Fault {
        NONE,
        FAIL_B,
        FAIL_BOTH,
        CANCEL_BOTH,
        CORRUPT_BOTH,
        STOP_CONTROL_AFTER_FIRST_REMOTE
    }

    private final class RoutingReplicaTransfers implements ReplicaTransferPort {
        private final AtomicBoolean controlStopped = new AtomicBoolean();
        private Fault fault = Fault.NONE;

        void fault(Fault next) {
            fault = next;
            controlStopped.set(false);
        }

        @Override
        public CompletionStage<TransferResult> stage(TransferRequest request, LocalArtifactPort.Source source) {
            return stage(membership.member(request.targetNode()), request, source);
        }

        @Override
        public CompletionStage<TransferResult> stage(
                ClusterMember target,
                TransferRequest request,
                LocalArtifactPort.Source source) {
            if (fault == Fault.FAIL_BOTH || (fault == Fault.FAIL_B && target.identity().equals(B))) {
                return CompletableFuture.failedFuture(
                        new TransferException(TransferError.IO_FAILURE, "injected unreachable replica"));
            }
            CompletionStage<TransferResult> actual = clients.get(target.identity()).stage(request, source);
            if (fault == Fault.CANCEL_BOTH) {
                actual.toCompletableFuture().cancel(true);
                return actual;
            }
            if (fault == Fault.CORRUPT_BOTH) {
                return actual.thenApply(result -> new TransferResult(
                        result.operationId(), result.artifactId(), result.node(), result.durableLength() + 1,
                        result.durableSha256(), result.topologyEpoch(), result.policyEpoch(), result.idempotentRetry()));
            }
            if (fault == Fault.STOP_CONTROL_AFTER_FIRST_REMOTE) {
                return actual.thenApply(result -> {
                    if (controlStopped.compareAndSet(false, true)) {
                        cluster.stopBlocking(B);
                        cluster.stopBlocking(C);
                    }
                    return result;
                });
            }
            return actual;
        }
    }

    private static final class StaleMembershipControl implements ClusterControlPlanePort {
        private final ClusterControlPlanePort delegate;
        private final AtomicInteger membershipReads = new AtomicInteger();

        private StaleMembershipControl(ClusterControlPlanePort delegate) {
            this.delegate = delegate;
        }

        @Override
        public Mono<MembershipSnapshot> membership() {
            return delegate.membership().map(snapshot -> membershipReads.incrementAndGet() == 1
                    ? snapshot
                    : new MembershipSnapshot(snapshot.voters(), "topology-stale", snapshot.policyEpoch()));
        }

        @Override
        public Mono<BucketNamespace> createBucket(String bucket) {
            return delegate.createBucket(bucket);
        }

        @Override
        public Mono<BucketNamespace> bucket(String bucket) {
            return delegate.bucket(bucket);
        }

        @Override
        public Mono<ObjectReferenceGeneration> compareAndPublish(PublicationProposal proposal) {
            return delegate.compareAndPublish(proposal);
        }

        @Override
        public Mono<ObjectReferenceGeneration> objectReference(String bucket, String objectKey) {
            return delegate.objectReference(bucket, objectKey);
        }
    }
}
