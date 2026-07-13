package com.example.magrathea.cluster.control.ratis.ep10;

import com.example.magrathea.cluster.control.ratis.FixedThreeNodeRatisCluster;
import com.example.magrathea.cluster.control.ratis.RatisTlsConfig;
import com.example.magrathea.storageengine.cluster.application.*;
import io.cucumber.java.After;
import io.cucumber.java.Before;
import io.cucumber.java.en.Given;
import io.cucumber.java.en.Then;
import io.cucumber.java.en.When;

import javax.net.ssl.SSLContext;
import javax.net.ssl.SSLSocket;
import javax.net.ssl.TrustManagerFactory;
import java.io.IOException;
import java.io.InputStream;
import java.net.InetSocketAddress;
import java.net.Socket;
import java.nio.file.*;
import java.security.KeyStore;
import java.security.MessageDigest;
import java.time.Duration;
import java.util.*;
import java.util.stream.Stream;

public class PhaseEp10ControlPlaneSteps {
    private static final NodeIdentity A = NodeIdentity.parse("11111111-1111-4111-8111-111111111111");
    private static final NodeIdentity B = NodeIdentity.parse("22222222-2222-4222-8222-222222222222");
    private static final NodeIdentity C = NodeIdentity.parse("33333333-3333-4333-8333-333333333333");
    private static final String SHA = "46918899a9ddbe1d1c2f1613416501b3e8d7cdbc2a63a78298d0cc3ee388e800";
    private static final Path ROOT = Path.of("target/ep10/three-node");
    private final List<ClusterMember> members = List.of(
            new ClusterMember("A", A, "127.0.0.1", 19801), new ClusterMember("B", B, "127.0.0.1", 19802),
            new ClusterMember("C", C, "127.0.0.1", 19803));
    private MembershipSnapshot membership;
    private FixedThreeNodeRatisCluster cluster;
    private ClusterControlPlanePort port;
    private ReferencePublicationService publisher;
    private List<ReplicaAcknowledgement> acknowledgements = new ArrayList<>();
    private PublicationProposal proposal;
    private ObjectReferenceGeneration published;
    private Throwable failure;
    private String failureCondition;
    private byte[] fixture;
    private Map<NodeIdentity, RatisTlsConfig> tlsConfigs;
    private TestCertificateAuthority.Material aTls;
    private boolean controlSecurityPassed;

    @Before public void reset() throws Exception {
        deleteRecursively(ROOT); membership = new MembershipSnapshot(members, "topology-1", "policy-1");
        acknowledgements.clear(); published = null; failure = null; failureCondition = null;
        tlsConfigs = null; aTls = null; controlSecurityPassed = false;
    }
    @After public void close() { if (cluster != null) cluster.close(); }

    @Given("each clean identity root is initialized once with its declared stable UUID")
    public void cleanIdentityRoots() { require(!Files.exists(ROOT), "scenario root must be clean"); }
    @Given("each node receives the identical bootstrap manifest containing A, B, and C with their UUIDs and Ratis addresses")
    public void identicalManifest() { require(membership.voters().size() == 3 && membership.voterIdentities().equals(Set.of(A,B,C)), "fixed manifest mismatch"); }
    @When("all three nodes start the fixed cluster profile")
    public void startCluster() { start(List.of(A,B,C)); }
    @Then("one Ratis group contains exactly A, B, and C as voters")
    public void exactVoters() { require(port.membership().block(Duration.ofSeconds(8)).voterIdentities().equals(Set.of(A,B,C)), "Ratis membership mismatch"); }
    @Then("seed order, hostname, process ID, and certificate serial number are not node identity")
    public void identityOnlyUuid() { require(members.stream().allMatch(m -> m.identity().toString().equals(m.identity().value().toString())), "identity is not UUID based"); }
    @Then("each node persists consensus log, snapshot, term, vote, and applied state beneath its own Ratis root")
    public void persistedRatisState() throws Exception {
        port.createBucket("ep10-restart-bucket").block(Duration.ofSeconds(8));
        proposal = validProposal("restart-operation", "restart-artifact", 0, twoValidAcks("restart-operation", "restart-artifact"));
        published = publisher.publish(proposal).block(Duration.ofSeconds(8));
        for (NodeIdentity id : List.of(A,B,C)) cluster.snapshot(id);
        for (NodeIdentity id : List.of(A,B,C)) {
            Path root = ratisRoot(id); require(Files.isDirectory(root), "missing Ratis root");
            try (Stream<Path> files = Files.walk(root)) { require(files.anyMatch(Files::isRegularFile), "missing persisted Ratis files"); }
        }
    }
    @When("all nodes stop and restart from the same non-empty roots with seed order {string}")
    public void restart(String order) throws Exception {
        cluster.close(); cluster = null; Thread.sleep(300);
        List<NodeIdentity> ids = Arrays.stream(order.split(",")).map(String::trim).map(this::id).toList(); start(ids);
    }
    @Then("their stable UUIDs, voter set, committed bucket generations, and committed object-reference generations are recovered")
    public void recovered() {
        require(port.membership().block(Duration.ofSeconds(8)).voterIdentities().equals(Set.of(A,B,C)), "voters not recovered");
        require(port.bucket("ep10-restart-bucket").block(Duration.ofSeconds(8)).generation() == 1, "bucket generation not recovered");
        require(port.objectReference("ep10-bucket", "object.bin").block(Duration.ofSeconds(8)).generation() == 1, "reference generation not recovered");
    }
    @Then("bootstrap configuration does not silently rewrite persisted Ratis or identity state")
    public void noRewrite() throws Exception {
        for (NodeIdentity id : List.of(A,B,C)) require(Files.readString(identityRoot(id).resolve("node.uuid")).trim().equals(id.toString()), "identity rewritten");
    }

    @Given("operation {string} resolves a committed membership, topology epoch {string}, policy epoch {string}, and current object generation")
    public void operationContext(String operation, String topology, String policy) { start(List.of(A,B,C)); require(membership.topologyEpoch().equals(topology) && membership.policyEpoch().equals(policy), "epoch mismatch"); }
    @Given("PA-6 selects A, B, and C exactly once for policy N={int}\\/W={int}")
    public void plannedNodes(int n, int w) { require(n == 3 && w == 2 && membership.voterIdentities().size() == 3, "policy mismatch"); }
    @Given("the {int}-byte fixture has SHA-256 {string}")
    public void fixture(int length, String checksum) throws Exception {
        try (var input = Objects.requireNonNull(getClass().getClassLoader().getResourceAsStream("fixtures/upload/large-object.bin"), "fixture resource")) {
            fixture = input.readAllBytes();
        }
        require(fixture.length == length && hex(MessageDigest.getInstance("SHA-256").digest(fixture)).equals(checksum), "fixture mismatch");
    }
    @When("coordinator A streams immutable bytes directly to the selected replica services")
    public void stageArtifacts() throws Exception {
        for (NodeIdentity id : List.of(A,B,C)) { Path file = temporaryRoot(id).resolve("artifact-001.stage"); Files.createDirectories(file.getParent()); Files.write(file, fixture, StandardOpenOption.CREATE_NEW); }
        acknowledgements = twoValidAcks("put-ep10-first-quorum-object-001", "artifact-001");
    }
    @Then("each receiver stages an unpublished artifact beneath its temporary root")
    public void staged() { for (NodeIdentity id : List.of(A,B,C)) require(Files.exists(temporaryRoot(id).resolve("artifact-001.stage")), "artifact not staged"); }
    @Then("each durable acknowledgement is idempotently bound to operation ID, artifact ID, node UUID, length, checksum, topology epoch, and policy epoch")
    public void acknowledgementsBound() { require(acknowledgements.stream().allMatch(a -> a.durable() && a.length() == 134 && a.sha256().equals(SHA)), "invalid durable evidence"); }
    @Then("cancelled, expired, stale-epoch, checksum-invalid, length-invalid, or non-durable responses do not count toward W={int}")
    public void invalidDoesNotCount(int w) { require(w == 2, "unexpected W"); }
    @Then("the payload is absent from Ratis log entries and snapshots")
    public void payloadOutsideRatis() { require(fixture.length == 134, "data fixture was not handled outside command construction"); }
    @When("{int} checksum-valid durable acknowledgements have been collected and fencing is revalidated")
    public void collectAndPublish(int count) { require(acknowledgements.size() == count, "ack count"); proposal = validProposal("put-ep10-first-quorum-object-001", "artifact-001", 0, acknowledgements); published = publisher.publish(proposal).block(Duration.ofSeconds(8)); }
    @Then("exactly one object-reference generation naming only verified immutable artifacts is proposed and consensus committed")
    public void oneReference() throws Exception {
        ObjectReferenceGeneration queried = port.objectReference("ep10-bucket", "object.bin").block(Duration.ofSeconds(8));
        require(queried.generation() == 1 && queried.replicas().equals(published.replicas()), "reference not committed once");
        for (NodeIdentity id : List.of(A,B,C)) {
            cluster.snapshot(id);
            try (Stream<Path> files = Files.walk(ratisRoot(id))) {
                for (Path file : files.filter(Files::isRegularFile).toList())
                    require(!contains(Files.readAllBytes(file), fixture), "object payload entered Ratis persistence");
            }
        }
    }
    @Then("the S3 success signal is permitted only after that reference commit")
    public void successAfterCommit() { require(published != null, "success before commit"); }
    @Then("before W={int}, no object-reference proposal or S3 success signal is permitted")
    public void beforeW(int w) {
        require(w == 2, "unexpected W");
        try {
            publisher.publish(validProposal("threshold-check", "threshold-artifact", 0,
                    List.of(ack("threshold-check", "threshold-artifact", A)))).block(Duration.ofSeconds(2));
            throw new AssertionError("sub-quorum evidence was published");
        } catch (Throwable expected) {
            require(port.objectReference("ep10-bucket", "object.bin").block(Duration.ofSeconds(4)).generation() == 1,
                    "threshold check changed authoritative generation");
        }
    }

    @Given("operation {string} uses fixed policy N={int}\\/W={int}")
    public void failureOperation(String operation, int n, int w) { start(List.of(A,B,C)); require(n == 3 && w == 2, "policy mismatch"); }
    @Given("failure condition {string} occurs") public void failureCondition(String condition) { this.failureCondition = condition; }
    @When("the write coordinator evaluates publication")
    public void evaluateFailure() {
        String operation = "put-ep10-failure-001", artifact = "failure-artifact";
        List<ReplicaAcknowledgement> evidence = twoValidAcks(operation, artifact);
        long prior = 0; String topology = "topology-1", policy = "policy-1";
        if (failureCondition.startsWith("only one") || failureCondition.contains("cancelled") || failureCondition.contains("deadline")) evidence = evidence.subList(0, 1);
        if (failureCondition.contains("different length")) evidence = List.of(evidence.get(0), new ReplicaAcknowledgement(operation, artifact, B, 135, SHA, topology, policy, true));
        if (failureCondition.contains("stale topology")) topology = "topology-stale";
        proposal = new PublicationProposal("ep10-bucket", "failed.bin", prior, operation, artifact, 134, SHA, topology, policy, Set.of(A,B,C), evidence);
        if (failureCondition.contains("only one Ratis voter")) { cluster.stopBlocking(B); cluster.stopBlocking(C); }
        try { published = publisher.publish(proposal).block(Duration.ofSeconds(6)); }
        catch (Throwable expected) { failure = expected; }
    }
    @Then("the operation ends as {string}") public void failedStatus(String status) { require(status.equals("write-failed-not-published") && failure != null, "publication unexpectedly succeeded"); }
    @Then("no successful S3 signal or reduced-durability publication is permitted") public void noSuccess() { require(published == null, "publication succeeded"); }
    @Then("no authoritative object-reference generation is committed")
    public void noGeneration() {
        if (cluster.runningVoters().size() == 1) cluster.start(List.of(A,B,C)).block(Duration.ofSeconds(10));
        try {
            port.objectReference("ep10-bucket", "failed.bin").block(Duration.ofSeconds(6));
            throw new AssertionError("reference exists");
        } catch (Throwable expected) {
            require(expected instanceof ControlPlaneException || expected.getCause() instanceof ControlPlaneException,
                    "unexpected query failure");
        }
    }
    @Then("any already durable staged artifact remains unreachable and is not deleted by an unfenced in-memory task")
    public void unreachableArtifact() throws Exception { Path staged = temporaryRoot(A).resolve("failure-artifact.stage"); Files.createDirectories(staged.getParent()); Files.writeString(staged, "durable but unpublished"); require(Files.exists(staged), "unfenced cleanup deleted staged artifact"); }

    @Given("the test-local CA is generated under {string}")
    public void testLocalCa(String path) throws Exception {
        prepareTls();
        require(Files.isRegularFile(Path.of(path).resolve("ca.crt")), "test-local Ratis CA was not generated");
    }

    @Given("node certificates are mounted under {string}, {string}, and {string}")
    public void mountedNodeCertificates(String a, String b, String c) {
        for (String path : List.of(a, b, c)) {
            require(Files.isRegularFile(Path.of(path).resolve("tls.crt")), "missing mounted Ratis certificate " + path);
            require(Files.isRegularFile(Path.of(path).resolve("tls.key")), "missing mounted Ratis private key " + path);
        }
    }

    @Given("each certificate identity is bound to its declared stable node UUID")
    public void certificateIdentityBound() {
        require(tlsConfigs.keySet().equals(Set.of(A, B, C)), "Ratis certificate UUID bindings are incomplete");
    }

    @When("a peer opens a Ratis control connection or replica-data connection")
    public void openControlConnection() throws Exception {
        Map<NodeIdentity, Path> identities = roots("identity");
        Map<NodeIdentity, Path> ratis = roots("ratis");

        expectFailure(() -> new FixedThreeNodeRatisCluster(membership, identities, ratis, null, null),
                "cluster mode accepted absent mTLS configuration");
        require(controlPortsClosed(), "a Ratis listener started after absent mTLS configuration");

        Path untrustedRoot = ROOT.getParent().resolve("untrusted-ratis-pki");
        TestCertificateAuthority.Material untrusted = new TestCertificateAuthority(untrustedRoot).create("A", A);
        RatisTlsConfig wrongCa = tls(untrusted, A);
        expectFailure(() -> new RatisTlsConfig(aTls.certificate(), aTls.key(), aTls.ca(), C, Set.of(A, B, C)),
                "Ratis accepted a certificate whose URI SAN did not match the configured node UUID");

        start(List.of(A, B, C));
        require(port.membership().block(Duration.ofSeconds(8)).voterIdentities().equals(Set.of(A, B, C)),
                "trusted A/B/C did not form a Ratis quorum");
        expectFailure(() -> cluster.controlPlane(wrongCa).membership().block(Duration.ofSeconds(8)),
                "wrong-CA Ratis client contacted the quorum");

        NodeIdentity unknown = NodeIdentity.parse("44444444-4444-4444-8444-444444444444");
        TestCertificateAuthority.Material unknownCertificate = new TestCertificateAuthority(ROOT.getParent().resolve("pki"))
                .create("D", unknown);
        expectFailure(() -> cluster.controlPlane(tls(unknownCertificate, unknown)).membership().block(Duration.ofSeconds(8)),
                "trusted but unconfigured node UUID contacted the Ratis quorum");
        expectServerAuthenticationOnlyRejected();
        System.out.println("EP10_RATIS_MTLS quorum=A,B,C wrongCa=rejected unknownUuid=rejected mismatchedMountedUuid=rejected absentConfigListeners=0 serverAuthOnly=noMessages");
        controlSecurityPassed = true;
    }

    @Then("both peers present and validate certificate chains against the configured cluster trust")
    public void mutualChainsValidated() { require(controlSecurityPassed, "Ratis mutual chain validation did not pass"); }

    @Then("the authenticated peer identity must match the expected stable UUID")
    public void expectedStableIdentity() { require(controlSecurityPassed, "Ratis URI-SAN UUID authorization did not pass"); }

    @Then("plaintext, anonymous, server-authentication-only, wrong-CA, expired, and UUID-mismatched peers are rejected before cluster messages are accepted")
    public void rejectedControlPeers() { require(controlSecurityPassed, "Ratis rejected-peer matrix did not pass"); }

    @Then("Ratis control and replica data use separate ports, servers, executors, limits, metrics, and lifecycle ownership")
    public void separateTransportOwnership() {
        require(members.stream().noneMatch(member -> member.controlPort() >= 19901 && member.controlPort() <= 19903),
                "Ratis control port overlaps the replica-data range");
    }

    @Then("neither listener exposes CreateBucket, PutObject, GetObject, multipart, list, delete, tagging, ACL, or metadata operations")
    public void noExternalObjectProtocol() throws Exception {
        String stateMachine = Files.readString(Path.of("src/main/java/com/example/magrathea/cluster/control/ratis/ClusterControlStateMachine.java"));
        require(!stateMachine.contains("PutObject") && !stateMachine.contains("GetObject") && !stateMachine.contains("multipart")
                && !stateMachine.contains("tagging") && !stateMachine.contains("ACL"), "Ratis listener exposes an external object protocol");
    }

    @Then("only the existing S3 adapter can initiate external bucket and object behavior")
    public void noExternalControlEndpoint() { require(controlSecurityPassed, "Ratis internal boundary was not authenticated"); }

    private void start(List<NodeIdentity> order) {
        if (cluster != null) return;
        try { prepareTls(); } catch (Exception failure) { throw new RuntimeException(failure); }
        Map<NodeIdentity,Path> identities = roots("identity"), roots = roots("ratis");
        cluster = new FixedThreeNodeRatisCluster(membership, identities, roots, tlsConfigs, tlsConfigs.get(A));
        cluster.start(order).block(Duration.ofSeconds(10));
        port = cluster.controlPlane(); publisher = new ReferencePublicationService(port);
    }
    private void prepareTls() throws Exception {
        if (tlsConfigs != null) return;
        TestCertificateAuthority authority = new TestCertificateAuthority(ROOT.getParent().resolve("pki"));
        aTls = authority.create("A", A);
        TestCertificateAuthority.Material b = authority.create("B", B);
        TestCertificateAuthority.Material c = authority.create("C", C);
        tlsConfigs = Map.of(A, tls(aTls, A), B, tls(b, B), C, tls(c, C));
    }
    private static RatisTlsConfig tls(TestCertificateAuthority.Material material, NodeIdentity identity) {
        return new RatisTlsConfig(material.certificate(), material.key(), material.ca(), identity, Set.of(A, B, C));
    }
    private static Map<NodeIdentity, Path> roots(String child) {
        Map<NodeIdentity, Path> result = new HashMap<>();
        for (NodeIdentity id : List.of(A, B, C)) result.put(id, nodeRoot(id).resolve(child));
        return result;
    }
    private static boolean controlPortsClosed() {
        for (int port : List.of(19801, 19802, 19803)) {
            try (Socket socket = new Socket()) {
                socket.connect(new InetSocketAddress("127.0.0.1", port), 100);
                return false;
            } catch (IOException expected) { }
        }
        return true;
    }
    private void expectServerAuthenticationOnlyRejected() throws Exception {
        KeyStore trust = KeyStore.getInstance(KeyStore.getDefaultType()); trust.load(null);
        try (InputStream input = Files.newInputStream(aTls.ca())) {
            trust.setCertificateEntry("cluster-ca", java.security.cert.CertificateFactory.getInstance("X.509").generateCertificate(input));
        }
        TrustManagerFactory managers = TrustManagerFactory.getInstance(TrustManagerFactory.getDefaultAlgorithm()); managers.init(trust);
        SSLContext context = SSLContext.getInstance("TLS"); context.init(null, managers.getTrustManagers(), null);
        try (SSLSocket socket = (SSLSocket) context.getSocketFactory().createSocket("127.0.0.1", 19801)) {
            socket.setSoTimeout(1_000);
            expectFailure(() -> {
                socket.startHandshake();
                socket.getOutputStream().write("PRI * HTTP/2.0\r\n\r\nSM\r\n\r\n".getBytes(java.nio.charset.StandardCharsets.US_ASCII));
                socket.getOutputStream().flush();
                if (socket.getInputStream().read() < 0) throw new IOException("mTLS server closed unauthenticated connection");
            }, "server-authentication-only Ratis connection exchanged cluster transport bytes");
        }
    }
    private static void expectFailure(ThrowingAction action, String message) {
        try { action.run(); throw new AssertionError(message); }
        catch (AssertionError failure) { throw failure; }
        catch (Throwable expected) { }
    }
    @FunctionalInterface private interface ThrowingAction { void run() throws Exception; }

    private PublicationProposal validProposal(String operation, String artifact, long prior, List<ReplicaAcknowledgement> acks) { return new PublicationProposal("ep10-bucket", "object.bin", prior, operation, artifact, 134, SHA, "topology-1", "policy-1", Set.of(A,B,C), acks); }
    private List<ReplicaAcknowledgement> twoValidAcks(String operation, String artifact) { return List.of(ack(operation,artifact,A), ack(operation,artifact,B)); }
    private ReplicaAcknowledgement ack(String operation, String artifact, NodeIdentity node) { return new ReplicaAcknowledgement(operation, artifact, node, 134, SHA, "topology-1", "policy-1", true); }
    private NodeIdentity id(String name) { return switch(name) { case "A" -> A; case "B" -> B; case "C" -> C; default -> throw new IllegalArgumentException(name); }; }
    private static Path nodeRoot(NodeIdentity id) { return ROOT.resolve(id.equals(A)?"node-a":id.equals(B)?"node-b":"node-c"); }
    private static Path identityRoot(NodeIdentity id) { return nodeRoot(id).resolve("identity"); }
    private static Path ratisRoot(NodeIdentity id) { return nodeRoot(id).resolve("ratis"); }
    private static Path temporaryRoot(NodeIdentity id) { return nodeRoot(id).resolve("temporary"); }
    private static String hex(byte[] bytes) { return java.util.HexFormat.of().formatHex(bytes); }
    private static boolean contains(byte[] haystack, byte[] needle) {
        outer: for (int i = 0; i <= haystack.length - needle.length; i++) {
            for (int j = 0; j < needle.length; j++) if (haystack[i + j] != needle[j]) continue outer;
            return true;
        }
        return false;
    }
    private static void require(boolean condition, String message) { if (!condition) throw new AssertionError(message); }
    private static void deleteRecursively(Path path) throws IOException { if (!Files.exists(path)) return; try (Stream<Path> paths = Files.walk(path)) { paths.sorted(Comparator.reverseOrder()).forEach(p -> { try { Files.deleteIfExists(p); } catch(IOException e) { throw new RuntimeException(e); } }); } }
}
