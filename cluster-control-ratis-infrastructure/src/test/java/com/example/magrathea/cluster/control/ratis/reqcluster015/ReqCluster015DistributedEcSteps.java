package com.example.magrathea.cluster.control.ratis.reqcluster015;

import com.example.magrathea.cluster.control.ratis.FixedThreeNodeRatisCluster;
import com.example.magrathea.cluster.control.ratis.RatisTlsConfig;
import com.example.magrathea.cluster.control.ratis.ep10.TestCertificateAuthority;
import com.example.magrathea.cluster.data.grpc.FileLocalArtifactStore;
import com.example.magrathea.cluster.data.grpc.GrpcReplicaClient;
import com.example.magrathea.cluster.data.grpc.GrpcReplicaServer;
import com.example.magrathea.cluster.data.grpc.ReplicaTlsConfig;
import com.example.magrathea.cluster.data.grpc.ReplicaTransferMetrics;
import com.example.magrathea.storageengine.cluster.application.ClusterControlPlanePort;
import com.example.magrathea.storageengine.cluster.application.ClusterEcWriteCoordinator;
import com.example.magrathea.storageengine.cluster.application.ClusterMember;
import com.example.magrathea.storageengine.cluster.application.ClusterObjectMetadata;
import com.example.magrathea.storageengine.cluster.application.ClusterStorageLayout;
import com.example.magrathea.storageengine.cluster.application.ControlPlaneException;
import com.example.magrathea.storageengine.cluster.application.EcPublicationProposal;
import com.example.magrathea.storageengine.cluster.application.EcReferencePublicationService;
import com.example.magrathea.storageengine.cluster.application.EcShardReference;
import com.example.magrathea.storageengine.cluster.application.FixedEc42Placement;
import com.example.magrathea.storageengine.cluster.application.LocalArtifactPort;
import com.example.magrathea.storageengine.cluster.application.MembershipSnapshot;
import com.example.magrathea.storageengine.cluster.application.NodeIdentity;
import com.example.magrathea.storageengine.cluster.application.ObjectReferenceGeneration;
import com.example.magrathea.storageengine.cluster.application.PreparedArtifact;
import com.example.magrathea.storageengine.cluster.application.PreparedEcObject;
import com.example.magrathea.storageengine.cluster.application.PreparedEcShard;
import com.example.magrathea.storageengine.cluster.application.ReplicaAcknowledgement;
import com.example.magrathea.storageengine.cluster.application.ReplicaTransferPort;
import com.example.magrathea.storageengine.cluster.application.TransferError;
import com.example.magrathea.storageengine.cluster.application.TransferException;
import com.example.magrathea.storageengine.cluster.application.TransferRequest;
import com.example.magrathea.storageengine.cluster.application.TransferResult;
import com.example.magrathea.storageengine.domain.valueobject.ErasureCodingConfig;
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
import java.util.Arrays;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HexFormat;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.CompletionStage;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.Stream;

/** Real Ratis, grpc-java/mTLS, and filesystem evidence for fixed distributed EC 4+2. */
public final class ReqCluster015DistributedEcSteps {
    private static final NodeIdentity A = NodeIdentity.parse("11111111-1111-4111-8111-111111111111");
    private static final NodeIdentity B = NodeIdentity.parse("22222222-2222-4222-8222-222222222222");
    private static final NodeIdentity C = NodeIdentity.parse("33333333-3333-4333-8333-333333333333");
    private static final Path ROOT = Path.of("target/ep10");
    private static final String MODE = "fixed A/B/C EC 4+2 placement with real gRPC/mTLS transfer, Ratis publication, restart, and filesystem inspection";
    private static final String OPERATION = "put-distributed-ec-4-2-001";

    private MembershipSnapshot membership;
    private List<PreparedEcShard> plannedInput;
    private List<EcShardReference> placement;
    private Harness harness;
    private PreparedEcObject object;
    private ClusterEcWriteCoordinator.PreparedEcPublication prepared;
    private ObjectReferenceGeneration published;
    private ObjectReferenceGeneration beforeRestart;
    private String failureCondition;
    private Throwable publicationFailure;

    @Before
    public void reset() throws Exception {
        deleteRecursively(ROOT);
        membership = staticMembership(20901, 20902, 20903);
        plannedInput = dummyPreparedShards();
    }

    @After
    public void close() {
        if (harness != null) harness.close();
    }

    @Given("validation mode {string} is selected for requirement {string}")
    public void validationMode(String mode, String requirement) {
        require(MODE.equals(mode), "unexpected validation mode");
        require("REQ-CLUSTER-015".equals(requirement), "unexpected requirement");
    }

    @Given("the only supported erasure-coding geometry is four {int} MiB data shards plus two {int} MiB parity shards per {int} MiB logical stripe")
    public void fixedGeometry(int dataShardMiB, int parityShardMiB, int stripeMiB) {
        require(dataShardMiB == 1 && parityShardMiB == 1 && stripeMiB == 4,
                "unexpected fixed shard geometry");
        ErasureCodingConfig config = ErasureCodingConfig.of(4, 2);
        require(config.dataBlocks() == 4 && config.parityBlocks() == 2,
                "fixed EC 4+2 configuration missing");
        try {
            ErasureCodingConfig.of(3, 2);
            throw new AssertionError("unsupported parameterized EC geometry was accepted");
        } catch (IllegalArgumentException expected) {
            require(expected.getMessage().contains("only fixed EC 4+2"),
                    "unexpected geometry rejection");
        }
    }

    @Given("committed membership arranges A, B, and C as three distinct failure domains under topology epoch {string} and policy epoch {string}")
    public void arrangedMembership(String topology, String policy) {
        require(membership.topologyEpoch().equals(topology)
                        && membership.policyEpoch().equals(policy),
                "committed epochs differ");
        require(membership.voters().stream().map(ClusterMember::failureDomain)
                        .distinct().count() == 3,
                "three failure domains are required");
    }

    @When("the transport-neutral distributed EC coordinator plans schema-{int} stripe {int}")
    public void plan(int schema, int stripe) {
        require(schema == 3 && stripe == 0, "unexpected schema or stripe");
        placement = new FixedEc42Placement().plan(membership, plannedInput);
    }

    @Then("shard indices {int} and {int} are assigned to A, {int} and {int} to B, and {int} and {int} to C")
    public void assigned(int a0, int a1, int b0, int b1, int c0, int c1) {
        require(indicesAt(A).equals(Set.of(a0, a1)), "A placement differs");
        require(indicesAt(B).equals(Set.of(b0, b1)), "B placement differs");
        require(indicesAt(C).equals(Set.of(c0, c1)), "C placement differs");
    }

    @Then("each node owns exactly two unique shard obligations in its own failure domain")
    public void twoPerNode() {
        require(placement.size() == 6, "six shard obligations are required");
        for (NodeIdentity node : List.of(A, B, C)) {
            require(indicesAt(node).size() == 2, "each node must own two shards");
        }
    }

    @Then("removing A, B, or C independently leaves exactly four committed shard identities sufficient for local EC {int}+{int} reconstruction")
    public void anyNodeLoss(int k, int m) {
        require(k == 4 && m == 2, "unexpected reconstruction geometry");
        for (NodeIdentity lost : List.of(A, B, C)) {
            require(placement.stream().filter(shard -> !shard.location().equals(lost)).count() == 4,
                    "one-node loss must leave four shards");
        }
    }

    @Then("the placement result contains no whole-object replica fallback or second external object API")
    public void noFallback() throws Exception {
        require(placement.stream().allMatch(shard -> shard.storedLength() == EcShardReference.SHARD_BYTES),
                "placement contains a non-shard artifact");
        String protocol = Files.readString(Path.of(
                "../cluster-protocol/src/main/proto/magrathea/cluster/v1/replica_service.proto").normalize());
        for (String forbidden : List.of("PutObject", "GetObject", "CreateBucket")) {
            require(!protocol.contains(forbidden), "internal protocol became an object API");
        }
    }

    @Given("coordinator A owns six locally prepared schema-{int} shard artifacts for deterministic fixture {string}")
    public void preparedShards(int schema, String fixture) throws Exception {
        require(schema == 3 && fixture.equals("target/ep10/fixtures/ec-4-2-stripe.bin"),
                "unexpected fixture declaration");
        startHarness();
        object = harness.prepareObject(Path.of(fixture));
        require(object.shards().size() == 6 && object.logicalLength() == EcShardReference.STRIPE_BYTES,
                "prepared EC object differs");
    }

    @Given("the bounded publication contains exactly one stripe and six shard obligations")
    public void oneBoundedStripe() {
        require(object.shards().size() == EcShardReference.TOTAL_SHARDS
                        && object.shards().stream().map(PreparedEcShard::stripeIndex)
                        .distinct().toList().equals(List.of(0L)),
                "distributed EC publication must remain bounded to one stripe");
        try {
            new PreparedEcObject(OPERATION, object.logicalLength() * 2, object.sha256(),
                    Stream.concat(object.shards().stream(), object.shards().stream()
                            .map(shard -> new PreparedEcShard(shard.artifact(), 1,
                                    shard.shardIndex(), shard.parity(),
                                    shard.stripeLogicalLength(), shard.logicalDataLength())))
                            .toList());
            throw new AssertionError("multi-stripe publication was accepted");
        } catch (IllegalArgumentException expected) {
            require(expected.getMessage().contains("exactly one stripe"),
                    "unexpected multi-stripe rejection");
        }
    }

    @Given("B and C expose internal replica-data services authenticated by UUID-bound certificates under {string} and {string}")
    public void dataServices(String bPath, String cPath) {
        require(bPath.equals("target/ep10/pki/nodes/B")
                        && cPath.equals("target/ep10/pki/nodes/C"),
                "unexpected certificate roots");
        require(harness.bServer.port() > 0 && harness.cServer.port() > 0,
                "replica-data servers are not listening");
        require(Files.isRegularFile(Path.of(bPath).resolve("tls.crt"))
                        && Files.isRegularFile(Path.of(cPath).resolve("tls.crt")),
                "UUID-bound certificates are missing");
    }

    @When("A distributes stripe {int} according to the committed placement and requests publication for bucket {string} and key {string}")
    public void distribute(int stripe, String bucket, String key) {
        require(stripe == 0, "only stripe zero was prepared");
        prepared = harness.coordinator.prepare(bucket, key, object).block(Duration.ofSeconds(30));
        require(prepared != null, "distributed EC preparation returned no evidence");
        published = harness.coordinator.publish(prepared).block(Duration.ofSeconds(15));
        require(published != null, "distributed EC reference was not published");
    }

    @Then("A retains checksum-valid shard indices {int} and {int}, B durably receives {int} and {int}, and C durably receives {int} and {int}")
    public void durableLocations(int a0, int a1, int b0, int b1, int c0, int c1) throws Exception {
        assertNodeShards(harness.aStore, Set.of(a0, a1), A);
        assertNodeShards(harness.bStore, Set.of(b0, b1), B);
        assertNodeShards(harness.cStore, Set.of(c0, c1), C);
    }

    @Then("every remote shard uses a real readiness-gated grpc-java stream with frames no larger than {int} bytes and mutual TLS identity validation")
    public void boundedGrpc(int maximumFrame) {
        require(maximumFrame == 65_536, "unexpected frame limit");
        for (ReplicaTransferMetrics metrics : List.of(harness.bMetrics, harness.cMetrics)) {
            require(metrics.maximumPayloadFrameBytes() <= maximumFrame,
                    "oversized gRPC shard frame observed");
            require(metrics.maximumInboundOutstanding() <= 1,
                    "manual inbound demand exceeded one frame");
        }
        require(harness.remoteStageCalls.get() == 4,
                "exactly four remote shard streams are required");
    }

    @Then("exactly six unique acknowledgements bind operation, artifact, shard location, stored length, SHA-{int}, topology epoch, and policy epoch")
    public void sixAcknowledgements(int algorithmBits) {
        require(algorithmBits == 256, "unexpected checksum algorithm");
        EcPublicationProposal proposal = prepared.proposal();
        require(proposal.acknowledgements().size() == 6
                        && proposal.acknowledgements().stream()
                        .map(ReplicaAcknowledgement::artifactId).distinct().count() == 6,
                "six unique shard acknowledgements are required");
        EcReferencePublicationService.validate(proposal);
    }

    @Then("Ratis commits generation {int} only after all six acknowledgements and revalidated membership fencing")
    public void committedGeneration(int generation) {
        require(published.generation() == generation && published.erasureCoded(),
                "Ratis EC generation differs");
        require(harness.control.objectReference(published.bucket(), published.objectKey())
                        .block(Duration.ofSeconds(8)).equals(published),
                "queried EC reference differs");
    }

    @Then("the authoritative reference records layout {string}, exact object length and SHA-{int}, and all six schema-{int} shard facts with transport-neutral node locations")
    public void authoritativeReference(String layout, int algorithmBits, int schema) {
        require(layout.equals("EC_4_2") && algorithmBits == 256 && schema == 3,
                "unexpected authoritative schema");
        require(published.storageLayout() == ClusterStorageLayout.EC_4_2
                        && published.length() == object.logicalLength()
                        && published.sha256().equals(object.sha256())
                        && published.ecShards().equals(prepared.proposal().shards()),
                "authoritative EC facts differ");
    }

    @Then("object or shard payload bytes are absent from the Ratis log and snapshot")
    public void payloadOutsideRatis() throws Exception {
        for (NodeIdentity node : List.of(A, B, C)) harness.controlCluster.snapshot(node);
        byte[] objectPrefix = Arrays.copyOf(harness.fixtureBytes, 512);
        for (NodeIdentity node : List.of(A, B, C)) {
            try (Stream<Path> files = Files.walk(harness.ratisRoots.get(node))) {
                for (Path file : files.filter(Files::isRegularFile).toList()) {
                    require(!contains(Files.readAllBytes(file), objectPrefix),
                            "object payload entered Ratis persistence: " + file);
                }
            }
        }
    }

    @When("all three voters restart from their original non-empty identity and Ratis roots")
    public void restartVoters() throws Exception {
        beforeRestart = published;
        harness.restartControl();
    }

    @Then("the same generation {int} EC reference and all six shard locations are recovered exactly")
    public void recovered(int generation) {
        ObjectReferenceGeneration recovered = harness.control
                .objectReference(beforeRestart.bucket(), beforeRestart.objectKey())
                .block(Duration.ofSeconds(10));
        require(recovered != null && recovered.equals(beforeRestart)
                        && recovered.generation() == generation
                        && recovered.ecShards().size() == 6,
                "EC reference did not survive complete voter restart");
    }

    @Then("this evidence does not claim S3 read integration, shard repair or replacement, a scanner or daemon, rebalance, cleanup, dynamic membership, or generalized chaos")
    public void boundedClaim() {
        require(published.erasureCoded() && published.ecShards().size() == 6,
                "bounded distributed transfer evidence is missing");
    }

    @Given("a clean fixed A\\/B\\/C EC {int}+{int} publication attempt has failure condition {string}")
    public void failedAttempt(int k, int m, String condition) throws Exception {
        require(k == 4 && m == 2, "unexpected geometry");
        failureCondition = condition;
        startHarness();
        object = harness.prepareObject(Path.of("target/ep10/fixtures/ec-4-2-stripe.bin"));
        prepared = harness.coordinator.prepare(
                "ep10-ec-failure", "failure/distributed-ec.bin", object)
                .block(Duration.ofSeconds(30));
    }

    @When("the distributed EC coordinator evaluates authoritative publication")
    public void evaluateFailure() {
        EcPublicationProposal original = prepared.proposal();
        List<ReplicaAcknowledgement> acknowledgements = new ArrayList<>(original.acknowledgements());
        String topology = original.topologyEpoch();
        if (failureCondition.startsWith("one of")) {
            acknowledgements.remove(acknowledgements.size() - 1);
        } else if (failureCondition.contains("different stored SHA-256")) {
            ReplicaAcknowledgement first = acknowledgements.get(0);
            acknowledgements.set(0, new ReplicaAcknowledgement(
                    first.operationId(), first.artifactId(), first.node(), first.length(),
                    "0".repeat(64), first.topologyEpoch(), first.policyEpoch(), true));
        } else if (failureCondition.startsWith("topology epoch")) {
            topology = "topology-2";
            String staleTopology = topology;
            acknowledgements = acknowledgements.stream().map(ack ->
                    new ReplicaAcknowledgement(ack.operationId(), ack.artifactId(), ack.node(),
                            ack.length(), ack.sha256(), staleTopology,
                            ack.policyEpoch(), ack.durable()))
                    .toList();
        }
        EcPublicationProposal attempted = new EcPublicationProposal(
                original.bucket(), original.objectKey(), original.priorGeneration(),
                original.operationId(), original.objectLength(), original.objectSha256(),
                topology, original.policyEpoch(), original.shards(), acknowledgements,
                original.metadata());
        try {
            published = new EcReferencePublicationService(harness.control)
                    .publish(attempted).block(Duration.ofSeconds(10));
        } catch (Throwable failure) {
            publicationFailure = reactor.core.Exceptions.unwrap(failure);
        }
    }

    @Then("no EC object-reference generation is committed")
    public void noEcGeneration() {
        require(publicationFailure != null && published == null,
                "invalid EC publication unexpectedly succeeded");
        try {
            harness.control.objectReference("ep10-ec-failure", "failure/distributed-ec.bin")
                    .block(Duration.ofSeconds(4));
            throw new AssertionError("failed EC reference exists");
        } catch (Throwable expected) {
            require(expected instanceof ControlPlaneException
                            || expected.getCause() instanceof ControlPlaneException,
                    "unexpected missing-reference outcome");
        }
    }

    @Then("no whole-object fallback, reduced-shard publication, or successful external signal is permitted")
    public void noDegradedFallback() {
        require(published == null && publicationFailure != null,
                "degraded or fallback publication occurred");
    }

    @Then("any transferred shard remains unreachable non-authoritative data for later fenced cleanup")
    public void unreachableTransferredShards() {
        require(harness.remoteStageCalls.get() == 4,
                "failure setup did not execute real remote shard transfer");
        require(harness.bStore.publishedExists("ec-s0-shard-1")
                        && harness.cStore.publishedExists("ec-s0-shard-2"),
                "transferred non-authoritative shard evidence is missing");
    }

    private Set<Integer> indicesAt(NodeIdentity node) {
        return placement.stream().filter(shard -> shard.location().equals(node))
                .map(EcShardReference::shardIndex)
                .collect(java.util.stream.Collectors.toSet());
    }

    private void assertNodeShards(
            FileLocalArtifactStore store, Set<Integer> indices, NodeIdentity node) throws Exception {
        Map<Integer, EcShardReference> authoritative = published.ecShards().stream()
                .filter(shard -> shard.location().equals(node))
                .collect(java.util.stream.Collectors.toMap(
                        EcShardReference::shardIndex, shard -> shard));
        require(authoritative.keySet().equals(indices), "authoritative node shard set differs");
        for (EcShardReference shard : authoritative.values()) {
            Path path = store.publishedPath(shard.artifactId());
            require(Files.isRegularFile(path)
                            && Files.size(path) == shard.storedLength()
                            && sha256(path).equals(shard.sha256()),
                    "durable shard bytes differ at " + node);
        }
    }

    private void startHarness() throws Exception {
        if (harness == null) harness = new Harness();
    }

    private static MembershipSnapshot staticMembership(int a, int b, int c) {
        return new MembershipSnapshot(List.of(
                new ClusterMember("A", A, "127.0.0.1", 20801, "127.0.0.1", a, "zone-a"),
                new ClusterMember("B", B, "127.0.0.1", 20802, "127.0.0.1", b, "zone-b"),
                new ClusterMember("C", C, "127.0.0.1", 20803, "127.0.0.1", c, "zone-c")),
                "topology-1", "policy-1");
    }

    private static List<PreparedEcShard> dummyPreparedShards() {
        List<PreparedEcShard> shards = new ArrayList<>();
        for (int index = 0; index < 6; index++) {
            shards.add(new PreparedEcShard(
                    new PreparedArtifact(OPERATION, "dummy-shard-" + index, A,
                            EcShardReference.SHARD_BYTES, "0".repeat(64)),
                    0, index, index >= 4, EcShardReference.STRIPE_BYTES,
                    index < 4 ? EcShardReference.SHARD_BYTES : 0));
        }
        return List.copyOf(shards);
    }

    private final class Harness implements AutoCloseable {
        final TestCertificateAuthority.Material aMaterial;
        final TestCertificateAuthority.Material bMaterial;
        final TestCertificateAuthority.Material cMaterial;
        final FileLocalArtifactStore aStore;
        final FileLocalArtifactStore bStore;
        final FileLocalArtifactStore cStore;
        final ReplicaTransferMetrics bMetrics = new ReplicaTransferMetrics();
        final ReplicaTransferMetrics cMetrics = new ReplicaTransferMetrics();
        final GrpcReplicaServer bServer;
        final GrpcReplicaServer cServer;
        final GrpcReplicaClient bClient;
        final GrpcReplicaClient cClient;
        final Map<NodeIdentity, Path> identityRoots = new LinkedHashMap<>();
        final Map<NodeIdentity, Path> ratisRoots = new LinkedHashMap<>();
        final Map<NodeIdentity, RatisTlsConfig> controlTls = new LinkedHashMap<>();
        MembershipSnapshot membership;
        FixedThreeNodeRatisCluster controlCluster;
        ClusterControlPlanePort control;
        ClusterEcWriteCoordinator coordinator;
        final AtomicInteger remoteStageCalls = new AtomicInteger();
        byte[] fixtureBytes;

        Harness() throws Exception {
            TestCertificateAuthority authority = new TestCertificateAuthority(ROOT.resolve("pki"));
            aMaterial = authority.create("A", A);
            bMaterial = authority.create("B", B);
            cMaterial = authority.create("C", C);
            aStore = store("node-a", A);
            bStore = store("node-b", B);
            cStore = store("node-c", C);
            ReplicaTlsConfig bServerTls = new ReplicaTlsConfig(
                    bMaterial.certificate(), bMaterial.key(), bMaterial.ca(), B, Set.of(A));
            ReplicaTlsConfig cServerTls = new ReplicaTlsConfig(
                    cMaterial.certificate(), cMaterial.key(), cMaterial.ca(), C, Set.of(A));
            bServer = new GrpcReplicaServer(
                    new java.net.InetSocketAddress("127.0.0.1", 0), bServerTls,
                    bStore, bMetrics, Duration.ZERO).start();
            cServer = new GrpcReplicaServer(
                    new java.net.InetSocketAddress("127.0.0.1", 0), cServerTls,
                    cStore, cMetrics, Duration.ZERO).start();
            membership = staticMembership(20901, bServer.port(), cServer.port());
            Set<NodeIdentity> voters = membership.voterIdentities();
            controlTls.put(A, ratisTls(aMaterial, A, voters));
            controlTls.put(B, ratisTls(bMaterial, B, voters));
            controlTls.put(C, ratisTls(cMaterial, C, voters));
            for (ClusterMember member : membership.voters()) {
                identityRoots.put(member.identity(), ROOT.resolve("three-node/node-"
                        + member.name().toLowerCase() + "/identity"));
                ratisRoots.put(member.identity(), ROOT.resolve("three-node/node-"
                        + member.name().toLowerCase() + "/ratis"));
            }
            startControl();
            ReplicaTlsConfig aDataTls = new ReplicaTlsConfig(
                    aMaterial.certificate(), aMaterial.key(), aMaterial.ca(), A, Set.of(B, C));
            bClient = new GrpcReplicaClient(
                    new java.net.InetSocketAddress("127.0.0.1", bServer.port()), aDataTls, B);
            cClient = new GrpcReplicaClient(
                    new java.net.InetSocketAddress("127.0.0.1", cServer.port()), aDataTls, C);
            ReplicaTransferPort routing = new ReplicaTransferPort() {
                @Override
                public CompletionStage<TransferResult> stage(
                        TransferRequest request, LocalArtifactPort.Source source) {
                    remoteStageCalls.incrementAndGet();
                    return client(request.targetNode()).stage(request, source);
                }
            };
            coordinator = new ClusterEcWriteCoordinator(
                    A, control, aStore, routing, Duration.ofSeconds(15));
        }

        PreparedEcObject prepareObject(Path fixturePath) throws Exception {
            fixtureBytes = new byte[Math.toIntExact(EcShardReference.STRIPE_BYTES)];
            for (int index = 0; index < fixtureBytes.length; index++) {
                fixtureBytes[index] = (byte) (index % 251);
            }
            Files.createDirectories(fixturePath.getParent());
            Files.write(fixturePath, fixtureBytes);
            byte[][] shards = encode42(fixtureBytes);
            List<PreparedEcShard> preparedShards = new ArrayList<>();
            for (int index = 0; index < shards.length; index++) {
                String artifactId = "ec-s0-shard-" + index;
                LocalArtifactPort.IncomingSink sink = aStore.beginIncoming(OPERATION, artifactId);
                for (int offset = 0; offset < shards[index].length; offset += 65_536) {
                    int count = Math.min(65_536, shards[index].length - offset);
                    sink.accept(ByteBuffer.wrap(shards[index], offset, count));
                }
                PreparedArtifact artifact = sink.publish().artifact();
                sink.close();
                preparedShards.add(new PreparedEcShard(
                        artifact, 0, index, index >= 4,
                        EcShardReference.STRIPE_BYTES,
                        index < 4 ? EcShardReference.SHARD_BYTES : 0));
            }
            return new PreparedEcObject(
                    OPERATION, fixtureBytes.length,
                    HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256")
                            .digest(fixtureBytes)), preparedShards);
        }

        void restartControl() throws Exception {
            controlCluster.close();
            Thread.sleep(300);
            startControl();
            coordinator = new ClusterEcWriteCoordinator(
                    A, control, aStore, (request, source) ->
                            client(request.targetNode()).stage(request, source),
                    Duration.ofSeconds(15));
        }

        private void startControl() {
            controlCluster = new FixedThreeNodeRatisCluster(
                    membership, identityRoots, ratisRoots, controlTls, controlTls.get(A));
            controlCluster.start(List.of(A, B, C)).block(Duration.ofSeconds(15));
            control = controlCluster.controlPlane();
        }

        private GrpcReplicaClient client(NodeIdentity target) {
            if (target.equals(B)) return bClient;
            if (target.equals(C)) return cClient;
            throw new IllegalArgumentException("no remote client for " + target);
        }

        @Override
        public void close() {
            if (controlCluster != null) controlCluster.close();
            if (bClient != null) bClient.close();
            if (cClient != null) cClient.close();
            bServer.close();
            cServer.close();
        }

        private FileLocalArtifactStore store(String node, NodeIdentity identity) throws IOException {
            return new FileLocalArtifactStore(
                    ROOT.resolve("three-node").resolve(node).resolve("objects"),
                    ROOT.resolve("three-node").resolve(node).resolve("temporary"), identity);
        }
    }

    private static RatisTlsConfig ratisTls(
            TestCertificateAuthority.Material material,
            NodeIdentity identity,
            Set<NodeIdentity> voters) {
        return new RatisTlsConfig(material.certificate(), material.key(), material.ca(),
                identity, voters);
    }

    private static byte[][] encode42(byte[] object) {
        int shardBytes = Math.toIntExact(EcShardReference.SHARD_BYTES);
        byte[][] shards = new byte[6][shardBytes];
        for (int data = 0; data < 4; data++) {
            System.arraycopy(object, data * shardBytes, shards[data], 0, shardBytes);
        }
        for (int offset = 0; offset < shardBytes; offset++) {
            int p0 = 0;
            int p1 = 0;
            for (int data = 0; data < 4; data++) {
                int value = shards[data][offset] & 0xff;
                p0 ^= value;
                p1 ^= multiply(value, data + 1);
            }
            shards[4][offset] = (byte) p0;
            shards[5][offset] = (byte) p1;
        }
        return shards;
    }

    private static int multiply(int left, int right) {
        int result = 0;
        int a = left;
        int b = right;
        while (b != 0) {
            if ((b & 1) != 0) result ^= a;
            boolean high = (a & 0x80) != 0;
            a = (a << 1) & 0xff;
            if (high) a ^= 0x1d;
            b >>>= 1;
        }
        return result;
    }

    private static String sha256(Path path) throws Exception {
        return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256")
                .digest(Files.readAllBytes(path)));
    }

    private static boolean contains(byte[] haystack, byte[] needle) {
        outer: for (int offset = 0; offset <= haystack.length - needle.length; offset++) {
            for (int index = 0; index < needle.length; index++) {
                if (haystack[offset + index] != needle[index]) continue outer;
            }
            return true;
        }
        return false;
    }

    private static void deleteRecursively(Path path) throws Exception {
        if (!Files.exists(path)) return;
        try (Stream<Path> walk = Files.walk(path)) {
            for (Path entry : walk.sorted(Comparator.reverseOrder()).toList()) {
                Files.deleteIfExists(entry);
            }
        }
    }

    private static void require(boolean condition, String message) {
        if (!condition) throw new AssertionError(message);
    }
}
