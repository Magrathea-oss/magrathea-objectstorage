package com.example.magrathea.s3api.cucumber.ep8;

import io.cucumber.datatable.DataTable;
import io.cucumber.java.Before;
import io.cucumber.java.en.Given;
import io.cucumber.java.en.Then;
import io.cucumber.java.en.When;

import java.io.IOException;
import java.nio.file.Files;
import java.util.List;
import java.util.Set;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;

/** Executable document/source boundary checks. These steps never simulate cluster execution. */
public class PhaseEp8ArchitectureContractSteps {
    private static final String ADR_PATH = "docs/adr/0027-authoritative-cluster-control-plane-and-direct-quorum-data-path.md";
    private static final List<String> PUBLICATION_STAGES = List.of(
            "reject or redirect a stale or fenced coordinator",
            "deterministically place the generation on the exact PA-6-selected targets",
            "stream immutable bytes directly to those targets without routing payload through the consensus log",
            "validate length and SHA-256 before each target durably publishes its local immutable artifact",
            "collect at least 2 idempotent durable acknowledgements bound to node UUID, artifact ID, checksum, and epochs",
            "revalidate fencing and consensus-commit one object-reference or manifest generation naming only verified artifacts",
            "return the successful S3 response only after that consensus publication commits");
    private static final Set<String> FAILURES = Set.of(
            "committed topology cannot select 3 independent targets",
            "only 1 checksum-valid durable acknowledgement is received",
            "a stream is cancelled or exceeds its deadline",
            "a target reports a checksum or length mismatch",
            "the coordinator uses a stale topology or policy epoch",
            "control-plane quorum is unavailable before reference publication",
            "consensus reference publication fails after 2 durable acknowledgements");

    private String adr;
    private String arc42;
    private String c4;
    private String failure;

    @Before
    public void reset() {
        adr = null;
        arc42 = null;
        c4 = null;
        failure = null;
    }

    @Given("the canonical decision is {string}")
    public void canonicalDecision(String path) throws IOException {
        assertThat(path).isEqualTo(ADR_PATH);
        adr = Ep8EvidenceSupport.read(path);
    }

    @When("the architecture-contract runner evaluates ADR 0027")
    public void evaluateAdr() throws IOException {
        loadArchitectureDocuments();
        assertThat(adr).contains("# ADR 0027", "## Decision", "## Alternatives Considered", "## Consequences");
    }

    @Then("its status is {string}")
    public void statusIs(String status) {
        assertThat(adr).contains("## Status", status);
    }

    @Then("it selects internal protobuf gRPC over HTTP\\/{int} with mutual TLS and bounded Reactor bridges")
    public void transportDecision(int protocolVersion) {
        assertThat(protocolVersion).isEqualTo(2);
        assertThat(adr).contains("internal-only protobuf gRPC over HTTP/2", "requires mutual TLS", "bounded queues");
        assertThat(arc42).contains("internal-only protobuf gRPC over HTTP/2", "mandatory mTLS");
    }

    @Then("it selects consensus-committed membership and metadata with direct checksum-validated data transfer outside the consensus log")
    public void consensusAndDataSplit() {
        assertThat(adr).contains("Membership is authoritative only when committed through consensus",
                "Large object bytes, replicas, chunks, EC stripes, and EC data/parity shards are not stored in the control log",
                "validates length and checksum");
    }

    @Then("it records embedded Apache Ratis as a planned implementation subject to a time-boxed integration and fault-behavior spike")
    public void ratisIsSpikeGated() {
        assertThat(adr).contains("Use an embedded Apache Ratis integration as the planned Raft implementation",
                "time-boxed spike", "partition/fault behavior", "No current repository evidence proves Ratis");
    }

    @Then("it decides static seeds, stable node identity, membership authority, topology hierarchy, ordered write and read publication, fencing, and failure semantics")
    public void completeDecisionTopics() {
        assertThat(adr).contains("Static seed addresses are bootstrap hints only", "stable persisted UUID",
                "### Ordered write semantics", "### Ordered read semantics", "### Failure, partition, and cancellation semantics",
                "zone → rack → host → disk-set → device", "fencing");
    }

    @Then("it defines CycloneDX, SPDX-normalized license, OWASP, and hardened-runtime evidence gates")
    public void supplyChainDecision() {
        assertThat(adr).contains("CycloneDX SBOMs in both JSON and XML", "SPDX-normalized dependency license inventory",
                "OWASP Dependency-Check", "runtime container-hardening validation");
    }

    @Then("it compares RSocket, plain HTTP\\/{int}, gossip authority, static membership authority, quorum-only metadata, external consensus, data in Raft, and degraded writes")
    public void alternativesAreCompared(int protocolVersion) {
        assertThat(protocolVersion).isEqualTo(2);
        assertThat(adr).contains("### RSocket", "### Plain HTTP/2", "### Gossip as authoritative",
                "### Static membership", "### PA-6 quorum decisions", "### External consensus service",
                "### Store object data in Raft", "### Allow degraded writes");
    }

    @Then("it identifies EP-10 as the owner of networked execution and multi-node fault validation")
    public void ep10OwnsExecution() {
        assertThat(adr).contains("EP-10 must execute", "EP-10", "semantic multi-node, fault-injection");
        assertThat(arc42).contains("EP-10", "planned / absent");
    }

    @Then("no accepted wording reports Ratis, Raft correctness, membership, quorum transfer, healing, rebalance, or multi-node durability as implemented")
    public void noImplementationClaim() {
        assertThat(adr).contains("architectural decision only", "remain planned/absent", "not evidence of networked membership");
        assertThat(c4).contains("PLANNED / NOT IMPLEMENTED");
        assertNoClusterRuntimeDependenciesOrRoutes();
    }

    @Given("ADR 0027 defines the planned gRPC surface for membership, control coordination, artifact transfer, verification, health evidence, and durable recovery-job execution")
    public void grpcSurface() throws IOException {
        loadArchitectureDocuments();
        assertThat(adr).contains("membership/control coordination", "replica or EC-shard transfer", "verification",
                "health evidence", "execution of durable recovery jobs");
    }

    @When("the architecture boundary is compared with the S3 Data Plane and Admin Control Plane")
    public void compareBoundaries() {
        assertThat(arc42).contains("S3 Data Plane", "Admin Control Plane", "MUST NOT expose object/bucket CRUD");
        assertThat(c4).contains("S3 Data Plane", "Admin Control Plane", "Internal only; not an object API");
    }

    @Then("external create, list, read, write, delete, tagging, versioning, ACL, metadata, multipart, bucket, and object operations remain available only through S3-compatible endpoints")
    public void s3OnlyObjectApi() throws IOException {
        String s3Routes = Ep8EvidenceSupport.read("s3-reactive-api-adapter/src/main/java/com/example/magrathea/s3api/adapter/web/S3PathRouter.java");
        assertThat(s3Routes).contains("createBucket", "listObjects", "putObject", "getObject", "deleteObject",
                "tagging", "versioning", "Acl", "multipart");
        assertThat(adr).contains("S3-compatible endpoints remain the only external object and bucket API");
    }

    @Then("no cluster protobuf service or Admin route exposes those object or bucket operations")
    public void noAlternateObjectApi() throws IOException {
        String admin = Ep8EvidenceSupport.read("admin-api-adapter/src/main/java/com/example/magrathea/admin/web/AdminRouter.java");
        assertThat(admin).doesNotContain("/admin/objects", "/admin/buckets/{bucket}/objects", "/admin/multipart");
        assertNoClusterRuntimeDependenciesOrRoutes();
    }

    @Then("an inter-node request cannot bypass S3 authentication, authorization, or the object-store to storage-engine application boundary")
    public void noBoundaryBypass() {
        assertThat(adr).contains("cannot expose public object/bucket semantics", "bypass S3 authorization");
        assertThat(c4).contains("storageEngineRepositoryAdapter", "storageEngineReactiveApplication");
    }

    @Then("Admin access remains limited to topology, configuration, backend status, and operational evidence that has no S3 API equivalent")
    public void adminBoundary() throws IOException {
        String admin = Ep8EvidenceSupport.read("admin-api-adapter/src/main/java/com/example/magrathea/admin/web/AdminRouter.java");
        assertThat(admin).contains("/admin/backend-status", "/admin/storage-policies", "/admin/storage-devices",
                "/admin/disk-sets", "/admin/reports/");
        assertThat(arc42).contains("Admin Control Plane MUST NOT expose object/bucket CRUD");
    }

    @Then("the inter-node listener is classified as internal-only and mutual-TLS authenticated rather than advertised as a supported client endpoint")
    public void listenerIsInternal() {
        assertThat(adr).contains("internal-only protobuf gRPC", "mutual TLS", "not a third public facade");
        assertThat(c4).contains("Internal protobuf gRPC/HTTP2 with mTLS", "PLANNED / NOT IMPLEMENTED");
    }

    @Given("the initial replicated policy is {string} with degraded writes disabled")
    public void replicatedPolicy(String policy) throws IOException {
        loadArchitectureDocuments();
        assertThat(policy).isEqualTo("N=3, W=2");
        assertThat(adr).contains("initial replicated-data policy is `N=3, W=2`", "Degraded writes are disabled");
    }

    @Given("a coordinator has resolved a consensus-committed membership snapshot, topology epoch {string}, policy epoch {string}, and current object generation {string}")
    public void committedSnapshot(String topologyEpoch, String policyEpoch, String generation) {
        assertThat(List.of(topologyEpoch, policyEpoch, generation)).containsExactly("topology-42", "policy-17", "generation-8");
    }

    @When("the architecture-contract runner evaluates publication of operation {string} for bucket {string} and key {string}")
    public void evaluatePublication(String operation, String bucket, String key) {
        assertThat(List.of(operation, bucket, key)).containsExactly("put-photos-2026-001", "archive-2026", "photos/launch-day/original.tiff");
    }

    @Then("the required stages have exactly this order:")
    public void exactPublicationOrder(DataTable table) {
        List<List<String>> rows = table.cells();
        assertThat(rows.get(0)).containsExactly("order", "required stage");
        assertThat(rows.subList(1, rows.size())).hasSize(PUBLICATION_STAGES.size());
        for (int i = 0; i < PUBLICATION_STAGES.size(); i++) {
            assertThat(rows.get(i + 1)).containsExactly(Integer.toString(i + 1), PUBLICATION_STAGES.get(i));
        }
        int previous = -1;
        for (String marker : List.of("1. Resolve", "2. Invoke", "3. Stream", "4. Each target", "5. Require", "6. Revalidate", "7. Acknowledge")) {
            int current = adr.indexOf(marker);
            assertThat(current).as("ordered ADR marker %s", marker).isGreaterThan(previous);
            previous = current;
        }
        assertThat(c4).contains("9. PLANNED: stream", "10. PLANNED: transfer directly",
                "11. PLANNED: submit verified", "12. PLANNED: revalidate fencing");
    }

    @Then("stable operation and artifact identifiers make a retried stage idempotent")
    public void idempotentRetries() {
        assertThat(adr).contains("Retries use stable operation and artifact identifiers", "idempotent acknowledgement");
    }

    @Then("the contract does not assert that any stage has a networked implementation in EP-8")
    public void publicationNotImplemented() {
        assertThat(c4).contains("PLANNED / NOT IMPLEMENTED (EP-8 EARLY)");
        assertNoClusterRuntimeDependenciesOrRoutes();
    }

    @Given("replicated write operation {string} requires policy {string}")
    public void failedWritePolicy(String operation, String policy) throws IOException {
        loadArchitectureDocuments();
        assertThat(operation).isEqualTo("put-photos-2026-002");
        assertThat(policy).isEqualTo("N=3, W=2");
    }

    @Given("the planned failure is {string}")
    public void plannedFailure(String value) {
        assertThat(FAILURES).contains(value);
        failure = value;
    }

    @When("the architecture-contract runner evaluates its publication outcome")
    public void evaluateFailureOutcome() {
        assertThat(failure).isNotBlank();
    }

    @Then("the outcome is {string}")
    public void failureOutcome(String outcome) {
        assertThat(outcome).isEqualTo("write-failed-not-published");
    }

    @Then("no successful S3 response is permitted")
    public void noSuccessfulResponse() { assertThat(adr).contains("fails and no object-reference generation is published"); }

    @Then("no object-reference or manifest generation is visible for the failed operation")
    public void noVisibleGeneration() { assertThat(adr).contains("must not expose them as an object generation"); }

    @Then("any durable staged artifacts remain unreachable orphans eligible only for durable fenced cleanup")
    public void orphanCleanup() { assertThat(adr).contains("safe orphans for a durable, fenced cleanup job"); }

    @Then("the architecture never lowers target diversity, write quorum, checksum validation, or consensus publication requirements as an implicit fallback")
    public void noDegradedFallback() { assertThat(adr).contains("Allow degraded writes below", "Rejected by default"); }

    @Then("this expected failure is a planned EP-10 contract rather than evidence of implemented fault handling")
    public void failureOwnedByEp10() { assertThat(arc42).contains("fault-injection evidence", "EP-10"); }

    @Given("storage node {string} has persisted UUID {string}")
    public void stableUuid(String node, String uuid) throws IOException {
        loadArchitectureDocuments();
        assertThat(node).isEqualTo("node-7f4c");
        assertThat(uuid).matches("[0-9a-f-]{36}");
    }

    @Given("static seed addresses {string} are bootstrap hints")
    public void staticSeeds(String seeds) { assertThat(seeds.split(",")).hasSize(3); assertThat(adr).contains("bootstrap hints only"); }

    @When("the planned node changes address and rotates its mutual-TLS certificate")
    public void addressAndCertificateChange() { assertThat(adr).contains("Certificate rotation and address changes retain the UUID"); }

    @Then("its persisted UUID remains its storage identity")
    public void uuidRemainsIdentity() { assertThat(adr).contains("stable persisted UUID created once for its storage identity"); }

    @Then("hostname, address, process ID, certificate serial, and seed-list position are not identity")
    public void transientValuesNotIdentity() { assertThat(adr).contains("Hostname, address, process ID, certificate serial number, and seed-list position are not node identity"); }

    @Then("adding or removing a seed does not add, remove, promote, demote, or replace a member")
    public void seedsDoNotChangeMembership() { assertThat(adr).contains("neither the membership database nor an availability oracle"); }

    @Then("admission, promotion, demotion, replacement, removal, incarnation, and fencing become authoritative only through a committed consensus transition")
    public void consensusMembershipAuthority() { assertThat(adr).contains("Admission, promotion, demotion, replacement, and removal are explicit consensus-controlled transitions", "incarnation/fencing state"); }

    @Then("a second live node presenting UUID {string} is rejected or fenced")
    public void duplicateUuidFenced(String uuid) { assertThat(uuid).hasSize(36); assertThat(adr).contains("cloning one UUID into two live nodes is rejected or fenced"); }

    @Then("these transitions remain planned until EP-10 supplies networked consensus evidence")
    public void transitionsRemainPlanned() { assertThat(adr).contains("EP-10", "No current repository evidence proves Ratis or Raft production behavior"); }

    @Given("node {string} is a consensus-committed member")
    public void committedMember(String node) throws IOException { loadArchitectureDocuments(); assertThat(node).isEqualTo("node-7f4c"); }

    @Given("its heartbeats are missed until liveness state {string} is reached")
    public void suspicionState(String state) { assertThat(state).isEqualTo("SUSPECT"); }

    @When("the planned placement service evaluates a new generation")
    public void placementEvaluatesGeneration() { assertThat(adr).contains("temporarily ineligible for new placement"); }

    @Then("it may exclude {string} from proposed new targets and record the suspicion evidence")
    public void suspicionAffectsPlacement(String node) { assertThat(node).isEqualTo("node-7f4c"); assertThat(adr).contains("Health suspicion can exclude a target"); }

    @Then("missed heartbeats, RPC timeout, partition, or seed removal do not change authoritative membership")
    public void suspicionCannotEvict() { assertThat(adr).contains("missed heartbeats, RPC timeout, or seed removal never auto-evicts"); }

    @Then("eviction or replacement requires an explicit safe consensus configuration transition")
    public void safeMembershipTransition() { assertThat(adr).contains("explicit consensus-controlled transitions using safe configuration changes"); }

    @Then("loss of control-plane quorum prevents that transition and all new object-reference publication")
    public void quorumLossBlocksPublication() { assertThat(adr).contains("Loss of control-plane quorum prevents membership changes and new reference publication"); }

    @Then("the cluster contract fails writes instead of allowing split-brain or degraded publication")
    public void failInsteadOfSplitBrain() { assertThat(adr).contains("fails writes rather than accepting split-brain or degraded writes"); }

    @Given("the planned YAML topology catalog {string} declares the hierarchy {string}")
    public void topologyHierarchy(String path, String hierarchy) throws IOException {
        loadArchitectureDocuments();
        assertThat(path).isEqualTo("config/storage/cluster-topology.yml");
        assertThat(hierarchy).isEqualTo("zone → rack → host → disk-set → device");
        assertThat(adr).contains(hierarchy);
    }

    @Given("host {string} binds node UUID {string}")
    public void hostBindsUuid(String host, String uuid) { assertThat(host).isEqualTo("host-a-01"); assertThat(uuid).hasSize(36); }

    @Given("disk set {string} owns device {string} at storage root {string}")
    public void diskSetOwnsDevice(String diskSet, String device, String root) { assertThat(List.of(diskSet, device, root)).containsExactly("disk-set-hot-a-01", "nvme-a-01", "/var/lib/magrathea/data/nvme-a-01"); }

    @When("an authoritative topology and policy snapshot is constructed for PA-6")
    public void authoritativeSnapshot() { assertThat(adr).contains("construct authoritative snapshots"); }

    @Then("every identity has one valid parent and every device has one owner")
    public void uniqueParentAndOwner() { assertThat(adr).contains("parent-linked identities", "conflicting device ownership"); }

    @Then("duplicate identities, broken parent links, conflicting device ownership, and impossible policy constraints are rejected")
    public void topologyValidation() { assertThat(adr).contains("rejects duplicate identities, broken parent links, impossible policy constraints, and conflicting device ownership"); }

    @Then("topology and policy snapshots carry consensus-controlled epochs used to fence stale publication")
    public void epochFencing() { assertThat(adr).contains("consensus-controlled epochs", "fenced from publishing"); }

    @Then("{string}, {string}, {string}, {string}, and {string} remain deterministic side-effect-free components in {string}")
    public void purePa6Components(String placement, String quorum, String antiEntropy, String rebalance, String readiness, String module) throws IOException {
        assertThat(List.of(placement, quorum, antiEntropy, rebalance, readiness)).containsExactly(
                "DistributedPlacementPlanner", "QuorumPolicy", "AntiEntropyPlanner", "RebalancePlanner", "DistributedReadinessReporter");
        assertThat(module).isEqualTo("storage-engine-domain");
        for (String type : List.of(placement, quorum, antiEntropy, rebalance, readiness)) {
            assertThat(Ep8EvidenceSupport.path("storage-engine-domain/src/main/java/com/example/magrathea/storageengine/domain/distributed/" + type + ".java")).isRegularFile();
        }
        assertThat(adr).contains("remain deterministic, side-effect-free policy components");
    }

    @Then("no gRPC, protobuf, Ratis, clock, persistence, retry-loop, or network-client type enters that pure policy core")
    public void noInfrastructureInPolicyCore() throws IOException {
        try (Stream<java.nio.file.Path> files = Files.walk(Ep8EvidenceSupport.path("storage-engine-domain/src/main/java/com/example/magrathea/storageengine/domain/distributed"))) {
            String source = files.filter(path -> path.toString().endsWith(".java"))
                    .map(path -> { try { return Files.readString(path); } catch (IOException e) { throw new RuntimeException(e); } })
                    .reduce("", (left, right) -> left + "\n" + right);
            assertThat(source).doesNotContain("io.grpc", "org.apache.ratis", "com.google.protobuf", "java.time.Clock", "Repository", "Retry", "NetworkClient");
        }
    }

    @Then("cluster application services adapt authoritative snapshots into PA-6 inputs and execute its decisions without replacing PA-6 semantics")
    public void applicationAdaptsPolicy() { assertThat(adr).contains("construct authoritative snapshots for them and execute their decisions"); }

    private void loadArchitectureDocuments() throws IOException {
        if (adr == null) adr = Ep8EvidenceSupport.read(ADR_PATH);
        if (arc42 == null) arc42 = Ep8EvidenceSupport.read("docs/arc42/src/02_architecture_constraints.adoc")
                + Ep8EvidenceSupport.read("docs/arc42/src/04_solution_strategy.adoc")
                + Ep8EvidenceSupport.read("docs/arc42/src/05_building_block_view.adoc")
                + Ep8EvidenceSupport.read("docs/arc42/src/06_runtime_view.adoc")
                + Ep8EvidenceSupport.read("docs/arc42/src/07_deployment_view.adoc")
                + Ep8EvidenceSupport.read("docs/arc42/src/08_concepts.adoc")
                + Ep8EvidenceSupport.read("docs/arc42/src/10_quality_requirements.adoc");
        if (c4 == null) c4 = Ep8EvidenceSupport.read("docs/c4/workspace.dsl");
    }

    private void assertNoClusterRuntimeDependenciesOrRoutes() {
        try {
            StringBuilder build = new StringBuilder();
            try (Stream<java.nio.file.Path> files = Files.walk(Ep8EvidenceSupport.ROOT)) {
                files.filter(path -> path.getFileName().toString().equals("pom.xml"))
                        .forEach(path -> { try { build.append(Files.readString(path)); } catch (IOException e) { throw new RuntimeException(e); } });
            }
            assertThat(build.toString()).doesNotContain("<artifactId>grpc-", "<artifactId>ratis-", "<artifactId>protobuf-java");
            assertThat(Ep8EvidenceSupport.read("s3-reactive-api-adapter/src/main/java/com/example/magrathea/s3api/adapter/web/S3PathRouter.java"))
                    .doesNotContain("/cluster", "/grpc", "/raft", "/ratis");
            assertThat(Ep8EvidenceSupport.read("admin-api-adapter/src/main/java/com/example/magrathea/admin/web/AdminRouter.java"))
                    .doesNotContain("/admin/cluster/objects", "/admin/cluster/buckets", "/admin/objects");
        } catch (IOException e) {
            throw new AssertionError(e);
        }
    }
}
