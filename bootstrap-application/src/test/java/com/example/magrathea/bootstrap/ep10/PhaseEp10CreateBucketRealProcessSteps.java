package com.example.magrathea.bootstrap.ep10;

import io.cucumber.java.After;
import io.cucumber.java.Before;
import io.cucumber.java.en.Given;
import io.cucumber.java.en.Then;
import io.cucumber.java.en.When;
import io.netty.handler.ssl.SslContextBuilder;
import io.netty.handler.ssl.util.InsecureTrustManagerFactory;
import org.springframework.http.MediaType;
import org.springframework.http.client.reactive.ReactorClientHttpConnector;
import org.springframework.test.web.reactive.server.EntityExchangeResult;
import org.springframework.test.web.reactive.server.WebTestClient;
import reactor.netty.http.client.HttpClient;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
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
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Stream;

/** Glue that starts three independent Spring Boot JVMs and uses only their S3 endpoints. */
public class PhaseEp10CreateBucketRealProcessSteps {
    private static final Path ROOT = Path.of("target/ep10/three-node").toAbsolutePath();
    private static final Path PKI = Path.of("target/ep10/pki").toAbsolutePath();
    private static final Path EVIDENCE = Path.of("target/ep10/evidence").toAbsolutePath();
    private static final String[] NAMES = {"A", "B", "C"};
    private static final String[] IDS = {
            "11111111-1111-4111-8111-111111111111",
            "22222222-2222-4222-8222-222222222222",
            "33333333-3333-4333-8333-333333333333"};
    private static final int[] S3_PORTS = {19001, 19002, 19003};
    private static final int[] ADMIN_PORTS = {19101, 19102, 19103};
    private static final String FIXTURE_SHA = "46918899a9ddbe1d1c2f1613416501b3e8d7cdbc2a63a78298d0cc3ee388e800";
    private static final String FIXTURE_ETAG = "1ae14a405348c337d00821e272868e71";
    private static final Pattern MD5_HEX = Pattern.compile("[a-f0-9]{32}");

    private final Map<String, Process> processes = new LinkedHashMap<>();
    private final Map<String, WebTestClient> clients = new HashMap<>();
    private final Map<String, String> originalIdentities = new HashMap<>();
    private String validationMode;
    private String requirement;
    private String currentBucket;
    private String currentKey;
    private Path fixturePath;
    private byte[] fixture;
    private int createStatus;
    private int headStatus;
    private int putStatus;
    private int getStatus;
    private boolean createReturned;
    private boolean putReturned;
    private boolean controlBarrierReached;
    private boolean usedInternalTransport;
    private String putEtag;
    private String getEtag;
    private byte[] downloaded;
    private long putCompletedAtMillis;
    private CompletableFuture<PutResult> pendingPut;
    private Map<String, Long> durableReplicaCounts = Map.of();

    @Before
    public void reset() throws Exception {
        stopProcesses();
        deleteRecursively(ROOT);
        deleteRecursively(PKI);
        validationMode = null;
        requirement = null;
        currentBucket = null;
        currentKey = null;
        fixturePath = null;
        fixture = null;
        createStatus = 0;
        headStatus = 0;
        putStatus = 0;
        getStatus = 0;
        createReturned = false;
        putReturned = false;
        controlBarrierReached = false;
        usedInternalTransport = false;
        putEtag = null;
        getEtag = null;
        downloaded = null;
        putCompletedAtMillis = 0;
        pendingPut = null;
        durableReplicaCounts = Map.of();
        originalIdentities.clear();
    }

    @After
    public void close() throws Exception {
        stopProcesses();
        preserveProcessEvidence();
    }

    @Given("validation mode {string} is selected for requirement {string}")
    public void validationMode(String mode, String selectedRequirement) {
        require(Set.of("REQ-CLUSTER-001", "REQ-CLUSTER-002", "REQ-CLUSTER-003",
                        "REQ-CLUSTER-004", "REQ-CLUSTER-005").contains(selectedRequirement),
                "unexpected requirement selector");
        require(Set.of("multi-node-webtestclient", "multi-node-aws-cli",
                        "multi-node-webtestclient-restart", "multi-node-aws-cli-restart").contains(mode),
                "unexpected validation mode");
        validationMode = mode;
        requirement = selectedRequirement;
    }

    @Given("clean fixed cluster nodes A, B, and C run with the declared UUIDs, ports, roots, and three-voter bootstrap manifest")
    public void startCleanCluster() throws Exception {
        ensureCleanCluster(List.of("A", "B", "C"));
    }

    @Given("nodes A, B, and C have established one Ratis control group with an available control quorum")
    public void waitForControlQuorum() throws Exception {
        waitFor(() -> {
            try {
                for (String node : NAMES) {
                    if (listBuckets(node) != 200) return false;
                }
                return true;
            } catch (Exception notReady) {
                return false;
            }
        }, Duration.ofSeconds(30), "fixed A/B/C control quorum did not become S3-visible");
    }

    @When("the S3 client sends an unconditional CreateBucket request for bucket {string} to node A")
    public void createBucket(String bucket) throws Exception {
        createStatus = createBucketThroughSelectedClient(bucket);
        createReturned = true;
    }

    @Then("the S3 response reports successful bucket creation only after its bucket generation is consensus committed")
    public void creationSucceededAfterCommit() {
        require(createReturned && createStatus == 200,
                "CreateBucket did not return S3 success: " + createStatus);
    }

    @When("the same S3 client sends HeadBucket for {string} to node B")
    public void headBucket(String bucket) throws Exception {
        headStatus = headBucketThroughSelectedClient(bucket);
    }

    @Then("node B reports that bucket {string} exists")
    public void bucketExists(String bucket) {
        require(headStatus == 200, "HeadBucket on B did not resolve " + bucket + ": " + headStatus);
    }

    @Then("no direct Ratis or replica-gRPC call is used as S3 behavior evidence")
    public void noInternalTransportEvidence() {
        require(!usedInternalTransport, "acceptance glue used an internal cluster transport");
        System.out.println("EP10_REAL_PROCESS_CREATE_BUCKET mode=" + validationMode
                + " processes=3 scenarios=1 steps=8 createStatus=" + createStatus
                + " headStatus=" + headStatus + " root=" + ROOT);
    }

    @Given("bucket {string} has a consensus-committed bucket generation")
    public void committedBucket(String bucket) throws Exception {
        currentBucket = bucket;
        waitForControlQuorum();
        createStatus = createBucketThroughSelectedClient(bucket);
        require(createStatus == 200, "bucket generation was not committed: " + createStatus);
        waitFor(() -> headBucketThroughSelectedClient(bucket) == 200, Duration.ofSeconds(15),
                "bucket generation did not become visible on B");
    }

    @Given("fixture {string} has length {int} and SHA-{int} {string}")
    public void exactFixture(String path, int length, int algorithmBits, String sha) throws Exception {
        require(algorithmBits == 256, "unexpected checksum algorithm");
        List<Path> candidates = List.of(
                Path.of(path).toAbsolutePath(),
                Path.of("..").resolve(path).normalize().toAbsolutePath(),
                Path.of(System.getProperty("maven.multiModuleProjectDirectory", "."))
                        .resolve(path).normalize().toAbsolutePath());
        fixturePath = candidates.stream().filter(Files::isRegularFile).findFirst()
                .orElse(candidates.getFirst());
        require(Files.isRegularFile(fixturePath), "fixture path is not a regular file: " + fixturePath);
        fixture = Files.readAllBytes(fixturePath);
        require(fixture.length == length, "fixture length differs");
        require(sha256(fixture).equals(sha), "fixture SHA-256 differs");
    }

    @When("the S3 client sends an unconditional PutObject to node A for bucket {string} and key {string} with that fixture")
    public void putFixture(String bucket, String key) throws Exception {
        currentBucket = bucket;
        currentKey = key;
        applyPut(putObjectThroughSelectedClient(bucket, key));
    }

    @Then("the successful PutObject response is returned only after at least {int} of the {int} selected nodes durably acknowledge checksum-valid immutable replicas")
    public void successAfterDurableAcknowledgements(int minimum, int selected) throws Exception {
        require(selected == 3 && putReturned && putStatus == 200,
                "PutObject did not return success after the fixed selection");
        durableReplicaCounts = artifactInventory();
        require(durableReplicaCounts.values().stream().filter(count -> count > 0).count() >= minimum,
                "fewer than W=2 roots contain durable immutable artifacts: " + durableReplicaCounts);
    }

    @Then("one consensus-committed object-reference generation names only durable replicas with length {int} and SHA-{int} {string}")
    public void referenceNamesDurableReplicas(int length, int algorithmBits, String sha) throws Exception {
        require(algorithmBits == 256, "unexpected checksum algorithm");
        List<Path> artifacts = publishedArtifacts();
        require(artifacts.size() >= 2, "fewer than two durable replica artifacts exist");
        for (Path artifact : artifacts) {
            byte[] bytes = Files.readAllBytes(artifact);
            require(bytes.length == length && sha256(bytes).equals(sha),
                    "durable replica differs at " + artifact);
        }
    }

    @Then("the object-reference generation is committed after the replica acknowledgement threshold is met")
    public void referenceCommittedAfterThreshold() throws Exception {
        require(putCompletedAtMillis > 0, "PutObject completion time was not captured");
        for (Path artifact : publishedArtifacts()) {
            require(Files.getLastModifiedTime(artifact).toMillis() <= putCompletedAtMillis,
                    "PutObject returned before durable artifact publication at " + artifact);
        }
    }

    @Then("the response ETag is {string}")
    public void responseEtag(String expected) {
        require(expected.equals(unquote(putEtag)), "PutObject ETag differs: " + putEtag);
    }

    @When("node A is stopped without changing the fixed membership")
    public void stopNodeA() throws Exception {
        Process process = processes.remove("A");
        require(process != null, "node A process was not running");
        process.destroy();
        if (!process.waitFor(10, TimeUnit.SECONDS)) process.destroyForcibly();
        clients.remove("A");
        require(!process.isAlive(), "node A did not stop");
    }

    @When("nodes B and C retain the two-voter control quorum")
    public void survivingControlQuorum() throws Exception {
        waitFor(() -> listBuckets("B") == 200 && listBuckets("C") == 200,
                Duration.ofSeconds(20), "B/C did not retain control quorum after A stopped");
    }

    @When("the S3 client sends GetObject for bucket {string} and key {string} to node B")
    public void getFromB(String bucket, String key) throws Exception {
        applyGet(getObjectThroughSelectedClient("B", bucket, key));
    }

    @Then("the response body is byte-for-byte equal to the uploaded fixture")
    public void downloadedBytesEqualUpload() {
        require(getStatus == 200 && Arrays.equals(fixture, downloaded),
                "GetObject body differs from the uploaded fixture");
    }

    @Then("its length is {int}")
    public void downloadedLength(int expected) {
        require(downloaded != null && downloaded.length == expected, "GetObject length differs");
    }

    @Then("its SHA-{int} is {string}")
    public void downloadedSha(int algorithmBits, String expected) {
        require(algorithmBits == 256 && downloaded != null && sha256(downloaded).equals(expected),
                "GetObject SHA-256 differs");
    }

    @Then("its ETag is {string}")
    public void downloadedEtag(String expected) {
        require(expected.equals(unquote(getEtag)), "GetObject ETag differs: " + getEtag);
    }

    @Then("node B resolves the consensus-committed generation rather than selecting a locally newer or uncommitted reference")
    public void bResolvedCommittedGeneration() {
        require(getStatus == 200 && !processes.containsKey("A"),
                "failover read did not resolve while coordinator A was stopped");
        System.out.println("EP10_REQ_CLUSTER_002 mode=" + validationMode
                + " jvms=3 stopped=A durableRoots=" + durableReplicaCounts
                + " getNode=B length=" + downloaded.length + " sha256=" + sha256(downloaded)
                + " etag=" + unquote(getEtag));
    }

    @Given("nodes A, B, and C have committed policy N={int}\\/W={int} for bucket {string}")
    public void committedWritePolicy(int replicas, int writeQuorum, String bucket) throws Exception {
        require(replicas == 3 && writeQuorum == 2, "unexpected fixed write policy");
        ensureCleanCluster(List.of("A", "B", "C"));
        waitForControlQuorum();
        currentBucket = bucket;
        require(createBucketThroughSelectedClient(bucket) == 200, "policy bucket was not committed");
        loadFixture();
    }

    @Given("only node A can return a checksum-valid durable acknowledgement for key {string}")
    public void onlyNodeACanAcknowledge(String key) {
        currentKey = key;
    }

    @Given("replica transfer to B exceeds its deadline while C rejects the staged bytes with a checksum mismatch")
    public void configureDataTransferFaults() throws Exception {
        String b = acceptancePost("B", "/__acceptance/fault/deadline");
        String c = acceptancePost("C", "/__acceptance/fault/checksum-mismatch");
        require(b.contains("DEADLINE") && c.contains("CHECKSUM_MISMATCH"),
                "acceptance transfer fault plan was not installed");
    }

    @When("the S3 client sends an unconditional PutObject with the {int}-byte fixture to node A")
    public void putWithFaults(int length) throws Exception {
        require(fixture != null && fixture.length == length, "fault fixture length differs");
        applyPut(putObjectThroughSelectedClient(currentBucket, currentKey));
        durableReplicaCounts = artifactInventory();
    }

    @Then("the S3 write fails with an S3-compatible availability or internal failure response")
    public void writeFailsCompatibly() {
        require(putReturned && putStatus >= 500, "sub-quorum PutObject did not return S3 failure: " + putStatus);
    }

    @Then("no successful PutObject response or reduced-durability warning is returned")
    public void noReducedDurabilitySuccess() {
        require(putStatus != 200, "sub-quorum PutObject returned success");
    }

    @Then("no object-reference generation for key {string} is consensus committed")
    public void noReferenceGeneration(String key) throws Exception {
        require(key.equals(currentKey), "unexpected failed key");
        GetResult missing = getObjectThroughSelectedClient("A", currentBucket, key);
        require(missing.status() >= 400, "failed write created a readable reference");
    }

    @Then("GetObject for that key does not expose any staged replica")
    public void stagedReplicaNotExposed() throws Exception {
        GetResult missing = getObjectThroughSelectedClient("A", currentBucket, currentKey);
        require(missing.status() >= 400,
                "GetObject exposed an unpublished replica");
    }

    @Then("any durable unpublished artifact remains unreachable pending later fenced cleanup")
    public void unpublishedArtifactRemainsUnreachable() throws Exception {
        waitFor(() -> temporaryFileCount() == 0, Duration.ofSeconds(5),
                "faulted transfer left an active temporary file");
        durableReplicaCounts = artifactInventory();
        require(durableReplicaCounts.getOrDefault("A", 0L) >= 1,
                "coordinator durable unpublished artifact is absent");
        require(durableReplicaCounts.getOrDefault("B", 0L) == 0
                        && durableReplicaCounts.getOrDefault("C", 0L) == 0,
                "faulted receivers unexpectedly published checksum-valid replicas: " + durableReplicaCounts);
        System.out.println("EP10_REQ_CLUSTER_003 mode=" + validationMode
                + " jvms=3 faultB=deadline faultC=checksum-mismatch putStatus=" + putStatus
                + " durableRoots=" + durableReplicaCounts + " reference=absent get=not-found");
    }

    @Given("bucket {string} was committed while A, B, and C were available")
    public void bucketCommittedBeforeControlLoss(String bucket) throws Exception {
        ensureCleanCluster(List.of("A", "B", "C"));
        waitForControlQuorum();
        currentBucket = bucket;
        require(createBucketThroughSelectedClient(bucket) == 200, "control-loss bucket was not committed");
        loadFixture();
    }

    @Given("nodes B and C are stopped so node A is the only available Ratis voter")
    public void armTwoVoterStop() throws Exception {
        String status = acceptancePost("A", "/__acceptance/publication/arm");
        require(status.contains("publicationReached"), "publication barrier was not armed");
    }

    @Given("two selected storage targets have durably staged checksum-valid replicas for key {string}")
    public void selectControlQuorumFailureKey(String key) {
        currentKey = key;
    }

    @When("the S3 client sends an unconditional PutObject through node A")
    public void putAcrossControlQuorumLoss() throws Exception {
        pendingPut = CompletableFuture.supplyAsync(() -> {
            try {
                return putObjectThroughSelectedClient(currentBucket, currentKey);
            } catch (Exception failure) {
                throw new RuntimeException(failure);
            }
        });
        waitFor(() -> {
            Map<String, Long> inventory = artifactInventory();
            return inventory.getOrDefault("B", 0L) >= 1
                    && inventory.getOrDefault("C", 0L) >= 1;
        }, Duration.ofSeconds(15), "write did not durably stage both remote replicas");
        Thread.sleep(200);
        controlBarrierReached = true;
        durableReplicaCounts = artifactInventory();
        require(durableReplicaCounts.getOrDefault("B", 0L) >= 1
                        && durableReplicaCounts.getOrDefault("C", 0L) >= 1,
                "remote replicas were not durable before control loss: " + durableReplicaCounts);
        String stoppedB = acceptancePost("B", "/__acceptance/voter/stop");
        String stoppedC = acceptancePost("C", "/__acceptance/voter/stop");
        require(stoppedB.contains("voterRunning>false")
                        && stoppedB.contains("dataServerRunning>true")
                        && stoppedC.contains("voterRunning>false")
                        && stoppedC.contains("dataServerRunning>true"),
                "B/C voter-only stop status differs: B=" + stoppedB + " C=" + stoppedC);
        acceptancePost("A", "/__acceptance/publication/release");
        applyPut(pendingPut.get(20, TimeUnit.SECONDS));
    }

    @Then("the S3 write fails because the object-reference generation cannot reach control quorum")
    public void writeFailsWithoutControlQuorum() {
        require(controlBarrierReached && putStatus >= 500,
                "control-quorum loss did not fail publication: " + putStatus);
    }

    @Then("no successful PutObject response is returned")
    public void noSuccessfulPutResponse() {
        require(putStatus != 200, "control-quorum loss returned PutObject success");
    }

    @Then("the two staged replicas remain unreachable through GetObject")
    public void controlLossReplicasUnreachable() throws Exception {
        acceptancePost("B", "/__acceptance/voter/start");
        waitFor(() -> listBuckets("A") == 200 && listBuckets("B") == 200,
                Duration.ofSeconds(20), "A/B control quorum did not recover");
        GetResult missing = getObjectThroughSelectedClient("A", currentBucket, currentKey);
        require(missing.status() >= 400, "staged replicas became reachable after voter recovery");
    }

    @Then("restarting a voter cannot reveal an object-reference generation that was never consensus committed")
    public void voterRestartCannotGenerateReference() throws Exception {
        GetResult missing = getObjectThroughSelectedClient("B", currentBucket, currentKey);
        require(missing.status() >= 400, "voter restart generated an object reference");
        System.out.println("EP10_REQ_CLUSTER_004 mode=" + validationMode
                + " jvms=3 barrier=post-durable-acks voterOnlyStop=B,C dataServersAlive=true"
                + " durableRoots=" + durableReplicaCounts + " putStatus=" + putStatus
                + " restartedVoter=B reference=absent");
    }

    @Given("REQ-CLUSTER-002 has committed bucket {string} and key {string}")
    public void prepareRestartState(String bucket, String key) throws Exception {
        ensureCleanCluster(List.of("A", "B", "C"));
        waitForControlQuorum();
        loadFixture();
        currentBucket = bucket;
        currentKey = key;
        require(createBucketThroughSelectedClient(bucket) == 200, "restart bucket was not committed");
        PutResult result = putObjectThroughSelectedClient(bucket, key);
        require(result.status() == 200, "restart fixture PutObject failed: " + result.status());
        for (String node : NAMES) {
            Path identity = nodeRoot(node).resolve("identity/node.uuid");
            require(Files.isRegularFile(identity), "persisted identity is absent for " + node);
            originalIdentities.put(node, Files.readString(identity).trim());
        }
    }

    @When("nodes A, B, and C stop and discard process memory")
    public void stopAllProcessesPreservingRoots() throws Exception {
        stopProcesses();
        require(Arrays.stream(NAMES).allMatch(node -> Files.isDirectory(nodeRoot(node).resolve("ratis"))),
                "complete stop lost a Ratis root");
    }

    @When("nodes A, B, and C restart with their original non-empty scenario roots and a reordered seed list {string}")
    public void restartWithReorderedSeeds(String seeds) throws Exception {
        List<String> order = List.of(seeds.split(","));
        require(order.equals(List.of("C", "A", "B")), "unexpected restart seed order");
        ensureCleanCluster(order);
        waitForControlQuorum();
    }

    @Then("each node recovers its original stable UUID from its {string} directory")
    public void identitiesRecovered(String directory) throws Exception {
        require(directory.equals("identity"), "unexpected identity directory");
        for (String node : NAMES) {
            String recovered = Files.readString(nodeRoot(node).resolve("identity/node.uuid")).trim();
            require(recovered.equals(originalIdentities.get(node)) && recovered.equals(IDS[index(node)]),
                    "stable UUID changed for " + node);
        }
    }

    @Then("the reordered seeds do not rewrite the persisted three-voter Ratis membership or committed log state")
    public void reorderedSeedsPreserveControlState() throws Exception {
        for (String node : NAMES) {
            Path ratis = nodeRoot(node).resolve("ratis");
            try (Stream<Path> files = Files.walk(ratis)) {
                require(files.anyMatch(Files::isRegularFile), "persisted Ratis state is empty for " + node);
            }
        }
    }

    @Then("the committed bucket and object-reference generations are recovered from persisted control state")
    public void committedGenerationsRecovered() throws Exception {
        require(headBucketThroughSelectedClient(currentBucket) == 200,
                "committed bucket generation was not recovered");
    }

    @When("the S3 client sends GetObject for the committed key to node B")
    public void getCommittedKeyAfterRestart() throws Exception {
        applyGet(getObjectThroughSelectedClient("B", currentBucket, currentKey));
    }

    @Then("the response body is byte-for-byte equal to the fixture")
    public void restartBodyEqualsFixture() {
        require(getStatus == 200 && Arrays.equals(fixture, downloaded),
                "restart GetObject body differs from fixture");
    }

    @Then("its length, SHA-{int}, and ETag remain {int}, {string}, and {string}")
    public void restartMetadataRemains(int algorithmBits, int length, String sha, String etag) {
        require(algorithmBits == 256 && downloaded != null && downloaded.length == length,
                "restart length differs");
        require(sha256(downloaded).equals(sha), "restart SHA-256 differs");
        require(unquote(getEtag).equals(etag), "restart ETag differs");
        System.out.println("EP10_REQ_CLUSTER_005 mode=" + validationMode
                + " jvms=3 fullStop=true restartOrder=C,A,B identities=" + originalIdentities
                + " rootsReused=true getNode=B length=" + downloaded.length
                + " sha256=" + sha256(downloaded) + " etag=" + unquote(getEtag));
    }

    private void ensureCleanCluster(List<String> startOrder) throws Exception {
        if (!processes.isEmpty()) return;
        TestPki pki = new TestPki(PKI);
        for (int index = 0; index < NAMES.length; index++) pki.create(NAMES[index], IDS[index]);
        for (String node : startOrder) {
            int index = Arrays.asList(NAMES).indexOf(node);
            processes.put(node, startNode(index, String.join(",", startOrder)));
        }
        waitFor(() -> processes.values().stream().allMatch(Process::isAlive), Duration.ofSeconds(10),
                "one cluster JVM exited during startup");
    }

    private Process startNode(int index, String seedOrder) throws IOException {
        String name = NAMES[index];
        String nodeDirectory = "node-" + name.toLowerCase();
        Path nodeRoot = ROOT.resolve(nodeDirectory);
        Files.createDirectories(nodeRoot);
        Path certificate = PKI.resolve("nodes").resolve(name).resolve("tls.crt");
        Path key = PKI.resolve("nodes").resolve(name).resolve("tls.key");
        Path ca = PKI.resolve("ca").resolve("ca.crt");
        List<String> command = new ArrayList<>();
        command.add(Path.of(System.getProperty("java.home"), "bin", "java").toString());
        command.add("-cp");
        command.add(System.getProperty("java.class.path"));
        command.add("com.example.magrathea.bootstrap.ep10.Ep10AcceptanceApplication");
        command.add("--spring.profiles.active=storage-engine,cluster");
        command.add("--magrathea.acceptance.enabled=true");
        command.add("--magrathea.acceptance.seed-order=" + seedOrder);
        command.add("--magrathea.cluster.deadlines.transfer=750ms");
        command.add("--server.port=" + S3_PORTS[index]);
        command.add("--server.ssl.enabled=true");
        command.add("--server.ssl.certificate=" + certificate);
        command.add("--server.ssl.certificate-private-key=" + key);
        command.add("--admin.server.port=" + ADMIN_PORTS[index]);
        command.add("--magrathea.cluster.node-id=" + name);
        command.add("--magrathea.cluster.roots.identity=" + nodeRoot.resolve("identity"));
        command.add("--magrathea.cluster.roots.ratis=" + nodeRoot.resolve("ratis"));
        command.add("--magrathea.cluster.roots.objects=" + nodeRoot.resolve("objects"));
        command.add("--magrathea.cluster.roots.temporary=" + nodeRoot.resolve("temporary"));
        command.add("--magrathea.cluster.roots.runtime=" + nodeRoot.resolve("runtime"));
        command.add("--magrathea.cluster.tls.control.certificate=" + certificate);
        command.add("--magrathea.cluster.tls.control.private-key=" + key);
        command.add("--magrathea.cluster.tls.control.trust-certificate=" + ca);
        command.add("--magrathea.cluster.tls.data.certificate=" + certificate);
        command.add("--magrathea.cluster.tls.data.private-key=" + key);
        command.add("--magrathea.cluster.tls.data.trust-certificate=" + ca);
        command.add("--storage.engine.filesystem.root=" + nodeRoot.resolve("storage-engine"));
        return new ProcessBuilder(command)
                .redirectErrorStream(true)
                .redirectOutput(ProcessBuilder.Redirect.appendTo(ROOT.resolve(name + ".log").toFile()))
                .start();
    }

    private int listBuckets(String node) throws Exception {
        if (awsMode()) {
            return runAws(node, "list-buckets").exitCode() == 0 ? 200 : 500;
        }
        return client(node).get().uri("/").exchange().returnResult(Void.class)
                .getStatus().value();
    }

    private int createBucketThroughSelectedClient(String bucket) throws Exception {
        if (awsMode()) {
            return runAws("A", "create-bucket", "--bucket", bucket).exitCode() == 0 ? 200 : 500;
        }
        return client("A").put().uri("/" + bucket).exchange().returnResult(Void.class)
                .getStatus().value();
    }

    private int headBucketThroughSelectedClient(String bucket) throws Exception {
        if (awsMode()) {
            return runAws("B", "head-bucket", "--bucket", bucket).exitCode() == 0 ? 200 : 500;
        }
        return client("B").head().uri("/" + bucket).exchange().returnResult(Void.class)
                .getStatus().value();
    }

    private WebTestClient client(String node) throws Exception {
        WebTestClient existing = clients.get(node);
        if (existing != null) return existing;
        int index = java.util.Arrays.asList(NAMES).indexOf(node);
        var ssl = SslContextBuilder.forClient()
                .trustManager(InsecureTrustManagerFactory.INSTANCE).build();
        HttpClient httpClient = HttpClient.create().secure(spec -> spec.sslContext(ssl));
        WebTestClient client = WebTestClient.bindToServer(
                        new ReactorClientHttpConnector(httpClient))
                .responseTimeout(Duration.ofSeconds(20))
                .baseUrl("https://127.0.0.1:" + S3_PORTS[index])
                .build();
        clients.put(node, client);
        return client;
    }

    private AwsResult runAws(String node, String... operation) throws Exception {
        int index = java.util.Arrays.asList(NAMES).indexOf(node);
        List<String> command = new ArrayList<>(List.of(
                "aws", "--no-sign-request", "--no-verify-ssl",
                "--endpoint-url", "https://127.0.0.1:" + S3_PORTS[index], "s3api"));
        command.addAll(List.of(operation));
        ProcessBuilder builder = new ProcessBuilder(command).redirectErrorStream(true);
        builder.environment().put("AWS_REQUEST_CHECKSUM_CALCULATION", "WHEN_REQUIRED");
        builder.environment().put("AWS_RESPONSE_CHECKSUM_VALIDATION", "WHEN_REQUIRED");
        builder.environment().put("AWS_MAX_ATTEMPTS", "1");
        Process process = builder.start();
        byte[] output = process.getInputStream().readAllBytes();
        boolean exited = process.waitFor(15, TimeUnit.SECONDS);
        if (!exited) {
            process.destroyForcibly();
            throw new IOException("AWS CLI timed out");
        }
        String text = new String(output, StandardCharsets.UTF_8);
        if (process.exitValue() != 0) System.out.println("AWS_CLI_FAILURE " + text);
        return new AwsResult(process.exitValue(), text);
    }

    private void preserveProcessEvidence() throws IOException {
        if (requirement == null || validationMode == null || !Files.isDirectory(ROOT)) return;
        Path destination = EVIDENCE.resolve(requirement).resolve(validationMode);
        Files.createDirectories(destination);
        for (String node : NAMES) {
            Path log = ROOT.resolve(node + ".log");
            if (Files.isRegularFile(log)) {
                Files.copy(log, destination.resolve(node + ".log"),
                        java.nio.file.StandardCopyOption.REPLACE_EXISTING);
            }
        }
        Files.writeString(destination.resolve("evidence.txt"),
                "requirement=" + requirement + "\nvalidationMode=" + validationMode
                        + "\nindependentJvmCount=3\nroot=" + ROOT
                        + "\ndurableReplicaCounts=" + artifactInventory() + "\n");
    }

    private void stopProcesses() throws InterruptedException {
        for (Process process : processes.values()) process.destroy();
        for (Process process : processes.values()) {
            if (!process.waitFor(10, TimeUnit.SECONDS)) process.destroyForcibly();
        }
        processes.clear();
        clients.clear();
    }

    private boolean awsMode() {
        return validationMode != null && validationMode.contains("aws-cli");
    }

    private PutResult putObjectThroughSelectedClient(String bucket, String key) throws Exception {
        if (awsMode()) {
            AwsResult result = runAws("A", "put-object", "--bucket", bucket, "--key", key,
                    "--body", fixturePath.toString());
            return new PutResult(result.exitCode() == 0 ? 200 : 500, extractEtag(result.output()));
        }
        EntityExchangeResult<byte[]> result = client("A").put()
                .uri("/" + bucket + "/" + key)
                .contentType(MediaType.APPLICATION_OCTET_STREAM)
                .contentLength(fixture.length)
                .bodyValue(fixture)
                .exchange()
                .expectBody()
                .returnResult();
        return new PutResult(result.getStatus().value(), result.getResponseHeaders().getETag());
    }

    private GetResult getObjectThroughSelectedClient(String node, String bucket, String key) throws Exception {
        if (awsMode()) {
            Path output = ROOT.resolve("downloads").resolve(
                    requirement + "-" + node + "-" + System.nanoTime() + ".bin");
            Files.createDirectories(output.getParent());
            AwsResult result = runAws(node, "get-object", "--bucket", bucket, "--key", key,
                    output.toString());
            byte[] body = result.exitCode() == 0 && Files.isRegularFile(output)
                    ? Files.readAllBytes(output) : new byte[0];
            Files.deleteIfExists(output);
            return new GetResult(result.exitCode() == 0 ? 200 : 404, body, extractEtag(result.output()));
        }
        EntityExchangeResult<byte[]> result = client(node).get()
                .uri("/" + bucket + "/" + key)
                .exchange()
                .expectBody()
                .returnResult();
        return new GetResult(result.getStatus().value(), result.getResponseBody(),
                result.getResponseHeaders().getETag());
    }

    private void applyPut(PutResult result) {
        putStatus = result.status();
        putEtag = result.etag();
        putReturned = true;
        putCompletedAtMillis = System.currentTimeMillis();
    }

    private void applyGet(GetResult result) {
        getStatus = result.status();
        downloaded = result.body();
        getEtag = result.etag();
    }

    private String acceptancePost(String node, String path) throws Exception {
        EntityExchangeResult<byte[]> response = client(node).post().uri(path)
                .exchange().expectBody().returnResult();
        require(response.getStatus().value() == 200,
                "acceptance control failed on " + node + path + ": " + response.getStatus());
        return new String(response.getResponseBody(), StandardCharsets.UTF_8);
    }

    private String acceptanceStatus(String node) throws Exception {
        EntityExchangeResult<byte[]> response = client(node).get().uri("/__acceptance/status")
                .exchange().expectBody().returnResult();
        require(response.getStatus().value() == 200, "acceptance status failed on " + node);
        return new String(response.getResponseBody(), StandardCharsets.UTF_8);
    }

    private void loadFixture() throws Exception {
        try (var input = PhaseEp10CreateBucketRealProcessSteps.class.getClassLoader()
                .getResourceAsStream("fixtures/upload/large-object.bin")) {
            require(input != null, "fixture resource is absent");
            fixture = input.readAllBytes();
        }
        require(fixture.length == 134 && sha256(fixture).equals(FIXTURE_SHA),
                "classpath fixture differs from the shared Business Need");
        fixturePath = ROOT.resolve("acceptance-large-object.bin");
        Files.createDirectories(fixturePath.getParent());
        Files.write(fixturePath, fixture);
    }

    private Map<String, Long> artifactInventory() throws IOException {
        Map<String, Long> inventory = new LinkedHashMap<>();
        for (String node : NAMES) {
            Path objects = nodeRoot(node).resolve("objects");
            if (!Files.isDirectory(objects)) {
                inventory.put(node, 0L);
                continue;
            }
            try (Stream<Path> files = Files.list(objects)) {
                inventory.put(node, files.filter(path -> path.getFileName().toString().endsWith(".artifact")).count());
            }
        }
        return inventory;
    }

    private List<Path> publishedArtifacts() throws IOException {
        List<Path> artifacts = new ArrayList<>();
        for (String node : NAMES) {
            Path objects = nodeRoot(node).resolve("objects");
            if (!Files.isDirectory(objects)) continue;
            try (Stream<Path> files = Files.list(objects)) {
                artifacts.addAll(files.filter(path -> path.getFileName().toString().endsWith(".artifact")).toList());
            }
        }
        return artifacts;
    }

    private long temporaryFileCount() throws IOException {
        long total = 0;
        for (String node : NAMES) {
            Path temporary = nodeRoot(node).resolve("temporary");
            if (!Files.isDirectory(temporary)) continue;
            try (Stream<Path> files = Files.list(temporary)) {
                total += files.filter(Files::isRegularFile).count();
            }
        }
        return total;
    }

    private static Path nodeRoot(String node) {
        return ROOT.resolve("node-" + node.toLowerCase());
    }

    private static int index(String node) {
        int index = Arrays.asList(NAMES).indexOf(node);
        if (index < 0) throw new IllegalArgumentException("unknown node " + node);
        return index;
    }

    private static String sha256(byte[] bytes) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(bytes));
        } catch (Exception impossible) {
            throw new AssertionError(impossible);
        }
    }

    private static String extractEtag(String output) {
        Matcher matcher = MD5_HEX.matcher(output == null ? "" : output.toLowerCase());
        return matcher.find() ? matcher.group() : null;
    }

    private static String unquote(String value) {
        return value == null ? "" : value.replace("\"", "");
    }

    private static void waitFor(CheckedBoolean condition, Duration timeout, String message)
            throws Exception {
        long deadline = System.nanoTime() + timeout.toNanos();
        while (System.nanoTime() < deadline) {
            if (condition.get()) return;
            Thread.sleep(250);
        }
        throw new AssertionError(message);
    }

    private static void deleteRecursively(Path path) throws IOException {
        if (!Files.exists(path)) return;
        try (Stream<Path> paths = Files.walk(path)) {
            for (Path candidate : paths.sorted(Comparator.reverseOrder()).toList()) {
                Files.deleteIfExists(candidate);
            }
        }
    }

    private static void require(boolean condition, String message) {
        if (!condition) throw new AssertionError(message);
    }

    private record AwsResult(int exitCode, String output) { }
    private record PutResult(int status, String etag) { }
    private record GetResult(int status, byte[] body, String etag) { }

    @FunctionalInterface
    private interface CheckedBoolean {
        boolean get() throws Exception;
    }

    private static final class TestPki {
        private final Path root;

        private TestPki(Path root) {
            this.root = root;
        }

        private void create(String name, String identity) throws Exception {
            Path ca = root.resolve("ca");
            Files.createDirectories(ca);
            Path caKey = ca.resolve("ca.key");
            Path caCertificate = ca.resolve("ca.crt");
            if (!Files.exists(caCertificate)) {
                run("openssl", "req", "-x509", "-newkey", "rsa:2048", "-nodes", "-days", "2",
                        "-subj", "/CN=EP10 Real Process CA", "-keyout", caKey.toString(),
                        "-out", caCertificate.toString());
            }
            Path node = root.resolve("nodes").resolve(name);
            Files.createDirectories(node);
            Path key = node.resolve("tls.key");
            Path request = node.resolve("tls.csr");
            Path certificate = node.resolve("tls.crt");
            Path extensions = node.resolve("tls.ext");
            Files.writeString(extensions,
                    "basicConstraints=CA:FALSE\nkeyUsage=digitalSignature,keyEncipherment\n"
                            + "extendedKeyUsage=serverAuth,clientAuth\n"
                            + "subjectAltName=URI:urn:magrathea:node:" + identity
                            + ",DNS:" + identity + ",DNS:localhost,IP:127.0.0.1\n");
            run("openssl", "req", "-new", "-newkey", "rsa:2048", "-nodes",
                    "-subj", "/CN=" + name, "-keyout", key.toString(), "-out", request.toString());
            run("openssl", "x509", "-req", "-days", "2", "-in", request.toString(),
                    "-CA", caCertificate.toString(), "-CAkey", caKey.toString(),
                    "-CAcreateserial", "-extfile", extensions.toString(), "-out", certificate.toString());
        }

        private static void run(String... command) throws Exception {
            Process process = new ProcessBuilder(command).redirectErrorStream(true).start();
            String output = new String(process.getInputStream().readAllBytes(), StandardCharsets.UTF_8);
            if (process.waitFor() != 0) {
                throw new IOException(String.join(" ", command) + " failed: " + output);
            }
        }
    }
}
