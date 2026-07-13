package com.example.magrathea.s3api.cucumber.ep8;

import tools.jackson.databind.JsonNode;
import io.cucumber.datatable.DataTable;
import io.cucumber.java.Before;
import io.cucumber.java.en.Given;
import io.cucumber.java.en.Then;
import io.cucumber.java.en.When;
import org.w3c.dom.Element;
import org.w3c.dom.NodeList;

import javax.xml.parsers.DocumentBuilderFactory;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.stream.StreamSupport;

import static org.assertj.core.api.Assertions.assertThat;

/** Opt-in validation of real, previously generated release evidence. No evidence is fabricated here. */
public class PhaseEp8SupplyChainEvidenceSteps {
    private static final String MANIFEST_PATH = "target/supply-chain/evidence-manifest.json";
    private static final Set<String> PRODUCTION_MODULES = Set.of(
            "admin-api-adapter", "s3-reactive-api-adapter", "object-store-domain", "storage-engine-domain",
            "storage-engine-reactive-repository-application", "storage-engine-reactive-application",
            "storage-engine-reactive-infrastructure", "bootstrap-application",
            "object-store-reactive-repository-application", "object-store-reactive-application",
            "object-store-reactive-infrastructure", "object-store-reactive-repository-storage-engine-infrastructure");

    private JsonNode manifest;
    private JsonNode application;
    private JsonNode image;
    private JsonNode licenses;
    private JsonNode owasp;
    private JsonNode hardening;
    private String scanStatus;

    @Before
    public void reset() {
        manifest = application = image = licenses = owasp = hardening = null;
        scanStatus = null;
    }

    @Given("the current checkout has a clean working tree")
    public void cleanWorkingTree() throws Exception {
        String status = command("git", "status", "--porcelain", "--untracked-files=all");
        assertThat(status).as("release evidence requires a clean checkout").isBlank();
        loadManifest();
        assertThat(manifest.path("source").path("dirtyTree").asBoolean()).isFalse();
        assertThat(manifest.path("acceptanceEligible").asBoolean()).isTrue();
        assertThat(manifest.path("evidenceMode").asText()).isEqualTo("clean-release-evidence");
    }

    @Given("the evidence runner records the full Git revision of the checked-out HEAD")
    public void revisionMatchesHead() throws Exception {
        loadManifest();
        assertThat(manifest.path("source").path("revision").asText())
                .isEqualTo(command("git", "rev-parse", "HEAD").trim()).matches("[0-9a-f]{40}");
    }

    @Given("the root Maven project declares the application release version")
    public void rootVersionIsRecorded() throws IOException {
        loadManifest();
        assertThat(manifest.path("application").path("version").asText()).isNotBlank().doesNotContain("SNAPSHOT");
        assertThat(Ep8EvidenceSupport.read("pom.xml")).contains("<version>" + manifest.path("application").path("version").asText());
    }

    @Given("SOURCE_DATE_EPOCH is deterministically associated with that revision and determines the recorded UTC evidence timestamp")
    public void deterministicTimestamp() throws Exception {
        loadManifest();
        long epoch = manifest.path("source").path("sourceDateEpoch").asLong(-1);
        assertThat(epoch).isPositive();
        assertThat(epoch).isEqualTo(Long.parseLong(command("git", "show", "-s", "--format=%ct", "HEAD").trim()));
        assertThat(manifest.path("source").path("timestamp").asText()).isEqualTo(Instant.ofEpochSecond(epoch).toString());
    }

    @Given("the production reactor inventory is:")
    public void productionReactorInventory(DataTable table) {
        Set<String> supplied = new HashSet<>(table.asList().subList(1, table.asList().size()));
        assertThat(supplied).containsExactlyInAnyOrderElementsOf(PRODUCTION_MODULES);
    }

    @When("the canonical release evidence build generates {string} and {string}")
    public void loadApplicationSboms(String jsonPath, String xmlPath) throws IOException {
        assertThat(jsonPath).isEqualTo("target/supply-chain/application.cdx.json");
        assertThat(xmlPath).isEqualTo("target/supply-chain/application.cdx.xml");
        application = Ep8EvidenceSupport.json(jsonPath);
        Ep8EvidenceSupport.requiredFile(xmlPath);
    }

    @Then("both artifacts validate against the same supported CycloneDX specification version")
    public void matchingCycloneDxVersions() throws Exception {
        loadApplication();
        Element xml = DocumentBuilderFactory.newInstance().newDocumentBuilder()
                .parse(Ep8EvidenceSupport.requiredFile("target/supply-chain/application.cdx.xml").toFile()).getDocumentElement();
        assertThat(application.path("bomFormat").asText()).isEqualTo("CycloneDX");
        assertThat(application.path("specVersion").asText()).isEqualTo("1.6");
        assertThat(xml.getNamespaceURI()).endsWith("/bom/1.6");
    }

    @Then("both represent every production module and all resolved production dependencies without test-only or coverage-aggregator components")
    public void completeProductionInventory() {
        Set<String> names = values(application.path("components"), "name");
        assertThat(names).containsAll(PRODUCTION_MODULES).doesNotContain("coverage-report-aggregate");
        assertThat(application.path("components")).allSatisfy(component ->
                assertThat(component.path("scope").asText()).isNotEqualTo("test"));
        assertThat(application.path("dependencies").size()).isGreaterThan(PRODUCTION_MODULES.size());
    }

    @Then("their metadata agrees on the recorded full source revision, root Maven release version, SOURCE_DATE_EPOCH-derived timestamp, and released application component")
    public void applicationIdentityAgrees() throws Exception {
        loadManifest();
        assertIdentityProperties(application.path("metadata").path("component"), false);
        var factory = DocumentBuilderFactory.newInstance();
        factory.setNamespaceAware(true);
        var document = factory.newDocumentBuilder().parse(Ep8EvidenceSupport.requiredFile("target/supply-chain/application.cdx.xml").toFile());
        assertThat(document.getElementsByTagNameNS("*", "timestamp").item(0).getTextContent())
                .isEqualTo(manifest.path("source").path("timestamp").asText());
        String xmlText = Files.readString(Ep8EvidenceSupport.path("target/supply-chain/application.cdx.xml"));
        assertThat(xmlText).contains(manifest.path("source").path("revision").asText(),
                manifest.path("application").path("version").asText(),
                Long.toString(manifest.path("source").path("sourceDateEpoch").asLong()));
    }

    @Then("{string} records those exact identity values and the SHA-256 of each SBOM")
    public void manifestRecordsApplicationHashes(String path) throws IOException {
        assertThat(path).isEqualTo(MANIFEST_PATH);
        loadManifest();
        assertManifestHash("target/supply-chain/application.cdx.json");
        assertManifestHash("target/supply-chain/application.cdx.xml");
    }

    @Then("components retain package coordinates, exact resolved versions, dependency relationships, and available cryptographic hashes")
    public void componentTraceability() {
        assertThat(application.path("components").size()).isGreaterThan(0);
        assertThat(application.path("components")).allSatisfy(component -> {
            assertThat(component.path("bom-ref").asText()).isNotBlank();
            assertThat(component.path("version").asText()).isNotBlank();
        });
        assertThat(application.path("components")).anySatisfy(component -> assertThat(component.path("hashes").size()).isPositive());
        assertThat(application.path("dependencies").size()).isPositive();
    }

    @Then("normalized JSON and XML inventories reconcile to the same component identities and dependency relationships")
    public void jsonXmlReconcile() throws Exception {
        var factory = DocumentBuilderFactory.newInstance();
        factory.setNamespaceAware(true);
        var xml = factory.newDocumentBuilder().parse(Ep8EvidenceSupport.path("target/supply-chain/application.cdx.xml").toFile());
        Set<String> xmlRefs = attributes(xml.getElementsByTagNameNS("*", "component"), "bom-ref");
        Set<String> jsonRefs = values(application.path("components"), "bom-ref");
        jsonRefs.add(application.path("metadata").path("component").path("bom-ref").asText());
        assertThat(xmlRefs).containsExactlyInAnyOrderElementsOf(jsonRefs);
        assertThat(xml.getElementsByTagNameNS("*", "dependency").getLength())
                .isEqualTo(application.path("dependencies").size());
    }

    @Then("rebuilding from the same locked inputs and SOURCE_DATE_EPOCH produces identical normalized content and SHA-256 evidence")
    public void reproducibilityContract() throws IOException {
        assertManifestHash("target/supply-chain/application.cdx.json");
        assertManifestHash("target/supply-chain/application.cdx.xml");
        assertThat(Ep8EvidenceSupport.read("scripts/normalize-application-sbom.py"))
                .contains("sort_keys=True", "source_date_epoch");
        assertThat(application.path("metadata").path("properties").toString()).contains("cdx:reproducible");
    }

    @Given("the current checkout contains a tracked or untracked working-tree change")
    public void dirtyTreeContract() throws IOException {
        String resolver = Ep8EvidenceSupport.read("scripts/resolve-supply-chain-identity.sh");
        assertThat(resolver).contains("git -C \"$root\" status --porcelain --untracked-files=all",
                "Refusing supply-chain evidence generation: the working tree is dirty");
    }

    @When("the canonical release evidence build derives its source identity")
    public void inspectIdentityDerivation() throws IOException {
        assertThat(Ep8EvidenceSupport.read("scripts/run-supply-chain-evidence.sh"))
                .contains("resolve_supply_chain_identity \"$ROOT\"")
                .containsSubsequence("resolve_supply_chain_identity \"$ROOT\"", "rm -rf");
    }

    @Then("release-evidence generation fails before publishing application, image, or license artifacts")
    public void dirtyFailsBeforeGeneration() throws IOException {
        String script = Ep8EvidenceSupport.read("scripts/run-supply-chain-evidence.sh");
        assertThat(script.indexOf("resolve_supply_chain_identity \"$ROOT\""))
                .isLessThan(script.indexOf("rm -rf \"$ROOT/target/supply-chain\""));
    }

    @Then("it does not label the dirty content with the full revision of the checked-out HEAD")
    public void dirtyCannotUseHeadLabel() throws IOException {
        assertThat(Ep8EvidenceSupport.read("scripts/resolve-supply-chain-identity.sh"))
                .contains("Non-acceptance diagnostics only; cannot satisfy any REQ-SUPPLY requirement.");
    }

    @Then("it does not reuse identity values or artifacts from a prior clean evidence run")
    public void staleArtifactsRemoved() throws IOException {
        assertThat(Ep8EvidenceSupport.read("scripts/run-supply-chain-evidence.sh"))
                .contains("rm -rf \"$ROOT/target/supply-chain\" \"$ROOT/target/site/supply-chain\"");
    }

    @Given("the shared evidence identity records the current clean checkout's full Git revision, root Maven release version, SOURCE_DATE_EPOCH, and derived UTC timestamp")
    public void sharedIdentity() throws Exception { cleanWorkingTree(); revisionMatchesHead(); deterministicTimestamp(); }

    @Given("the local image reference for that recorded version resolves to the exact immutable local image ID produced by the release build")
    public void immutableImageIdentity() throws IOException {
        loadManifest();
        JsonNode identity = Ep8EvidenceSupport.json("target/supply-chain/image-identity.json");
        assertThat(identity.path("id").asText()).matches("sha256:[0-9a-f]{64}");
        assertThat(identity.path("id").asText()).isEqualTo(manifest.path("image").path("id").asText());
    }

    @When("the evidence runner generates {string} by inspecting that recorded image ID rather than a mutable tag")
    public void loadImageSbom(String path) throws IOException {
        assertThat(path).isEqualTo("target/supply-chain/image.cdx.json");
        image = Ep8EvidenceSupport.json(path);
        assertThat(Ep8EvidenceSupport.read("scripts/build-image-supply-chain.sh"))
                .contains("\"docker:$IMAGE_ID\"", "Image tag moved while evidence was generated");
    }

    @Then("the SBOM identifies the subject image by that exact immutable image ID")
    public void imageSubjectIdentity() throws IOException {
        loadImage();
        assertThat(image.path("metadata").path("component").path("bom-ref").asText())
                .isEqualTo(Ep8EvidenceSupport.json("target/supply-chain/image-identity.json").path("id").asText());
    }

    @Then("its source revision, application version, and timestamp agree with the shared recorded evidence identity")
    public void imageIdentityAgrees() throws IOException { assertIdentityProperties(image.path("metadata").path("component"), true); }

    @Then("it inventories application, JVM, operating-system package, and other runtime components discoverable in the final image")
    public void imageInventoryIsBroad() {
        Set<String> types = values(image.path("components"), "type");
        assertThat(types).contains("file", "library");
        String inventory = image.path("components").toString().toLowerCase();
        assertThat(inventory).contains("java").containsAnyOf("ubuntu", "dpkg", "debian");
        assertThat(image.path("components").size()).isGreaterThan(application.path("components").size());
    }

    @Then("the evidence manifest records the same image ID, full source revision, application version, SOURCE_DATE_EPOCH, derived timestamp, and SBOM SHA-256")
    public void manifestImageIdentity() throws IOException {
        loadManifest();
        assertThat(manifest.path("image").path("id").asText()).isEqualTo(image.path("metadata").path("component").path("bom-ref").asText());
        assertManifestHash("target/supply-chain/image.cdx.json");
        assertIdentityProperties(image.path("metadata").path("component"), true);
    }

    @Then("runtime hardening and smoke validation use the same immutable image ID")
    public void hardeningUsesImageId() throws IOException {
        hardening = Ep8EvidenceSupport.json("target/supply-chain/hardening-evidence.json");
        assertThat(hardening.path("imageId").asText()).isEqualTo(manifest.path("image").path("id").asText());
    }

    @Then("tag movement or a subject-image-ID mismatch fails the gate instead of silently regenerating evidence for another image")
    public void imageMismatchFails() throws IOException {
        assertThat(Ep8EvidenceSupport.read("scripts/build-image-supply-chain.sh")).contains("Image tag moved", "exit 1");
        assertThat(Ep8EvidenceSupport.read("scripts/update-supply-chain-manifest.py")).contains("Image SBOM subject does not match");
    }

    @Then("repeated inspection of the same image bytes produces identical normalized inventory and SHA-256 evidence")
    public void imageReproducibility() throws IOException {
        assertManifestHash("target/supply-chain/image.cdx.json");
        assertThat(Ep8EvidenceSupport.read("scripts/normalize-image-sbom.py")).contains("sort_keys=True");
    }

    @Given("the CycloneDX application inventory for the shared recorded full source revision and root Maven release version has passed reactor reconciliation")
    public void reconciledApplicationInventory() throws IOException { loadApplication(); completeProductionInventory(); }

    @Given("its timestamp is derived from the recorded SOURCE_DATE_EPOCH associated with that revision")
    public void applicationTimestampDerived() throws Exception { deterministicTimestamp(); applicationIdentityAgrees(); }

    @When("license evidence is generated at {string} and {string}")
    public void loadLicenseEvidence(String jsonPath, String htmlPath) throws IOException {
        licenses = Ep8EvidenceSupport.json(jsonPath);
        assertThat(Files.readString(Ep8EvidenceSupport.requiredFile(htmlPath))).contains("License", "NOASSERTION");
    }

    @Then("both license artifacts agree with the application JSON, application XML, image SBOM, and evidence manifest on the recorded source revision, application version, SOURCE_DATE_EPOCH, and derived timestamp")
    public void licenseIdentityAgrees() throws IOException {
        loadManifest();
        for (String field : List.of("sourceRevision", "applicationVersion", "timestamp")) assertThat(licenses.path(field).asText()).isNotBlank();
        assertThat(licenses.path("sourceRevision").asText()).isEqualTo(manifest.path("source").path("revision").asText());
        assertThat(licenses.path("applicationVersion").asText()).isEqualTo(manifest.path("application").path("version").asText());
        assertThat(licenses.path("sourceDateEpoch").asLong()).isEqualTo(manifest.path("source").path("sourceDateEpoch").asLong());
        assertThat(licenses.path("timestamp").asText()).isEqualTo(manifest.path("source").path("timestamp").asText());
        assertManifestHash("target/supply-chain/license-inventory.json");
    }

    @Then("each production component retains package identity, exact version, source evidence, detected license, concluded SPDX expression, and review status")
    public void completeLicenseRows() {
        assertThat(licenses.path("components")).allSatisfy(component -> {
            for (String field : List.of("package", "version", "source", "concludedSpdxExpression", "reviewStatus"))
                assertThat(component.path(field).asText()).as(field).isNotBlank();
            assertThat(component.has("detectedLicenseEvidence")).isTrue();
        });
    }

    @Then("recognized licenses use valid SPDX license identifiers or expressions")
    public void recognizedSpdx() { assertThat(licenses.path("components")).filteredOn(c -> c.path("reviewStatus").asText().equals("recognized")).allSatisfy(c -> assertThat(c.path("concludedSpdxExpression").asText()).doesNotContain("NOASSERTION")); }

    @Then("missing evidence is reported as {string} with review status {string}")
    public void unknownLicenses(String conclusion, String status) { assertThat(licenses.path("components")).anySatisfy(c -> { assertThat(c.path("concludedSpdxExpression").asText()).isEqualTo(conclusion); assertThat(c.path("reviewStatus").asText()).isEqualTo(status); }); }

    @Then("conflicting or non-normalizable evidence is retained with review status {string}")
    public void ambiguousLicenses(String status) { assertThat(licenses.path("components")).anySatisfy(c -> assertThat(c.path("reviewStatus").asText()).isEqualTo(status)); }

    @Then("unknown, ambiguous, copyleft, exception-bearing, and manually concluded entries remain visible in machine-readable and human-readable reports")
    public void licenseVisibility() throws IOException {
        String json = licenses.toString();
        String html = Files.readString(Ep8EvidenceSupport.path("target/supply-chain/license-inventory.html"));
        assertThat(json).contains("unknown", "ambiguous", "copyleftDetected", "exceptionBearing", "manualConclusion");
        assertThat(html).contains("unknown", "ambiguous");
    }

    @Then("generation does not label the release compliant merely because an inventory exists")
    public void noComplianceClaim() { assertThat(licenses.path("complianceConclusion").asText()).isEqualTo("NOASSERTION"); }

    @Then("no license identifier, conclusion, approval, or compatibility decision is fabricated from absent evidence")
    public void noFabricatedLicense() throws IOException { assertThat(Ep8EvidenceSupport.read("scripts/generate-license-inventory.py")).contains("NOASSERTION").doesNotContain("compliant\": true"); }

    @Then("repeated normalization of the same component and source evidence produces the same ordered inventory and SHA-256")
    public void licenseReproducibility() throws IOException {
        assertManifestHash("target/supply-chain/license-inventory.json");
        assertThat(Ep8EvidenceSupport.read("scripts/generate-license-inventory.py")).contains("sort_keys=True");
    }

    @Given("OWASP Dependency-Check scans every resolved production reactor dependency")
    public void loadOwaspEvidence() throws IOException {
        loadManifest();
        owasp = Ep8EvidenceSupport.json(manifest.path("owaspDependencyCheck").path("analysis").asText());
        assertThat(Ep8EvidenceSupport.read("pom.xml")).contains("dependency-check-maven", "<goal>aggregate</goal>");
    }

    @Given("the configured gate rejects unsuppressed findings with CVSS 7.0 or greater")
    public void owaspThreshold() { assertThat(owasp.path("policy").path("failBuildOnCVSS").asDouble()).isEqualTo(7.0); }

    @Given("the pre-run dependency coordinates, plugin configuration, and suppression file content are captured")
    public void owaspInputsCaptured() throws IOException {
        assertManifestHash("target/site/dependency-check-analysis.json");
        assertConfigurationHash("pom.xml");
        assertConfigurationHash("config/dependency-check-suppressions.xml");
    }

    @When("the scan ends with status {string}")
    public void scanEndsWithStatus(String status) { scanStatus = status; assertThat(status).isIn("complete with no threshold finding", "complete with unsuppressed CVSS 7.0+", "incomplete because vulnerability data is unavailable or stale"); }

    @Then("verification handling is {string}")
    public void expectedOwaspHandling(String handling) {
        if (scanStatus.startsWith("complete with no")) assertThat(handling).isEqualTo("pass while retaining all findings");
        else assertThat(handling).isEqualTo("fail");
        assertThat(manifest.path("owaspDependencyCheck").path("failClosed").asBoolean()).isTrue();
    }

    @Then("machine-readable evidence distinguishes unsuppressed findings, suppressed findings, and scan errors")
    public void owaspCategories() { assertThat(owasp.has("unsuppressedFindings") && owasp.has("suppressedFindings") && owasp.has("scanErrors")).isTrue(); }

    @Then("findings below the failure threshold remain visible")
    public void lowerFindingsVisible() throws IOException { assertThat(Ep8EvidenceSupport.read("scripts/analyze-dependency-check.py")).contains("unsuppressedFindings"); }

    @Then("an incomplete scan is never reported as clean or as zero vulnerabilities")
    public void incompleteNotClean() { if (owasp.path("scanStatus").asText().equals("error")) { assertThat(owasp.path("clean").isNull()).isTrue(); assertThat(owasp.path("scanErrors").path("count").asInt()).isPositive(); } }

    @Then("dependency coordinates, plugin configuration, and suppression file content are unchanged by this EP-8 evidence run")
    public void owaspInputsUnchanged() throws IOException { assertConfigurationHash("pom.xml"); assertConfigurationHash("config/dependency-check-suppressions.xml"); }

    @Then("no dependency upgrade, downgrade, exclusion, generated suppression, vulnerability waiver, or finding deletion is performed to make the gate pass")
    public void noRemediation() throws IOException {
        String runner = Ep8EvidenceSupport.read("scripts/run-dependency-check.sh");
        assertThat(runner).doesNotContain("versions:set", "dependency:purge", "sed -i", "suppression.xml >", "rm target/dependency-check-report");
    }

    @Given("the hardened-runtime runner builds the current source revision into a local JVM image")
    public void hardenedRunnerBuildsImage() throws IOException { assertThat(Ep8EvidenceSupport.read("scripts/run-supply-chain-evidence.sh")).contains("build-image-supply-chain.sh", "validate-hardened-runtime.sh"); }

    @Given("the runner records the exact immutable local image ID produced for that revision")
    public void hardeningImageId() throws IOException { immutableImageIdentity(); }

    @Given("every inspection, start, and replacement uses that recorded image ID rather than resolving a mutable image tag")
    public void runtimeUsesImageId() throws IOException { assertThat(Ep8EvidenceSupport.read("scripts/validate-hardened-runtime.sh")).contains("\"$IMAGE_ID\"").doesNotContain("docker run magrathea-objectstorage:"); }

    @Given("the container user and group are configured as numeric non-zero IDs")
    public void numericNonRoot() throws IOException { loadHardening(); assertThat(hardening.at("/runtime/uid").asInt()).isPositive(); assertThat(hardening.at("/runtime/gid").asInt()).isPositive(); }

    @Given("the root filesystem is read-only")
    public void readOnlyRoot() { assertThat(hardening.at("/runtime/readOnlyRootFilesystem").asBoolean()).isTrue(); }

    @Given("only data mount {string} and temporary runtime mount {string} are writable")
    public void exactWritablePaths(String data, String temporary) { assertThat(values(hardening.at("/runtime/writableApplicationPaths"), null)).containsExactlyInAnyOrder(data, temporary); }

    @Given("privilege escalation is disabled with {string}")
    public void noNewPrivileges(String value) { assertThat(value).isEqualTo("no-new-privileges"); assertThat(hardening.at("/runtime/noNewPrivileges").asBoolean()).isTrue(); }

    @Given("all Linux capabilities are dropped with no capabilities added")
    public void noCapabilities() { assertThat(values(hardening.at("/runtime/capabilitiesDropped"), null)).containsExactly("ALL"); assertThat(Ep8EvidenceSupport.path("scripts/validate-hardened-runtime.sh")).hasContent("--cap-drop ALL"); }

    @Given("the container is configured without host PID, IPC, network, or UTS namespace flags and mounts no container engine socket")
    public void isolatedNamespaces() { assertThat(hardening.at("/runtime/hostNamespaces").isEmpty()).isTrue(); assertThat(hardening.at("/runtime/containerEngineSocketMounted").asBoolean()).isFalse(); }

    @Given("daemon-level user-namespace remapping is not required by this portable runtime baseline")
    public void usernsOptional() { assertThat(hardening.at("/runtime/userNamespaceRemapping").asText()).isIn("enabled", "unavailable"); }

    @When("the hardened-runtime runner starts the JVM application with the filesystem backend")
    public void runtimeStarted() { assertThat(hardening.path("status").asText()).isEqualTo("passed"); }

    @Then("process inspection confirms the numeric non-root user and group and every declared hardening restriction")
    public void processHardeningConfirmed() { assertThat(hardening.at("/runtime/uid").asInt()).isEqualTo(10001); assertThat(hardening.at("/runtime/gid").asInt()).isEqualTo(10001); readOnlyRoot(); noCapabilities(); }

    @Then("runtime evidence reports user-namespace remapping support and enabled state, or records it as unavailable, according to inspected engine capabilities and configuration, and never claims support or enablement without evidence")
    public void truthfulUsernsEvidence() { usernsOptional(); assertThat(hardening.at("/runtime/engineSecurityOptions").isArray()).isTrue(); }

    @Then("the Admin readiness representation identifies the filesystem backend as ready without exposing an Admin object API")
    public void adminReady() throws IOException { assertThat(hardening.at("/admin/ready").asBoolean()).isTrue(); assertThat(hardening.at("/admin/backend").asText()).isEqualTo("storage-engine"); assertThat(Ep8EvidenceSupport.read("admin-api-adapter/src/main/java/com/example/magrathea/admin/web/AdminRouter.java")).doesNotContain("/admin/objects"); }

    @When("an S3 client creates bucket {string}, uploads fixture {string} as key {string}, and reads it back")
    public void exactS3Exercise(String bucket, String fixture, String key) throws IOException { assertThat(hardening.at("/s3/bucket").asText()).isEqualTo(bucket); assertThat(hardening.at("/s3/key").asText()).isEqualTo(key); assertThat(Ep8EvidenceSupport.sha256(Ep8EvidenceSupport.requiredFile(fixture))).isEqualTo(hardening.at("/s3/sha256").asText()); }

    @Then("returned bytes, content length {int}, ETag, and SHA-256 {string} agree with the fixture and persisted object")
    public void exactS3Evidence(int bytes, String sha) { assertThat(hardening.at("/s3/bytes").asInt()).isEqualTo(bytes); assertThat(hardening.at("/s3/sha256").asText()).isEqualTo(sha); assertThat(hardening.at("/s3/etag").asText()).isNotBlank(); assertThat(hardening.at("/s3/exactBytes").asBoolean()).isTrue(); }

    @When("the container is replaced using the same recorded image ID and data mount")
    public void containerReplacement() { assertThat(hardening.at("/s3/restartPersistence").asBoolean()).isTrue(); }

    @Then("the Admin readiness representation is healthy again")
    public void adminReadyAgain() { assertThat(hardening.at("/admin/ready").asBoolean()).isTrue(); }

    @Then("an S3 read returns the same object bytes, content length, ETag, and SHA-256")
    public void sameObjectAfterRestart() { assertThat(hardening.at("/s3/restartPersistence").asBoolean()).isTrue(); assertThat(hardening.at("/s3/exactBytes").asBoolean()).isTrue(); }

    @Then("no write outside the two explicit writable mounts was required for startup, request handling, or restart recovery")
    public void noOtherWrites() { assertThat(values(hardening.at("/runtime/writableApplicationPaths"), null)).containsExactlyInAnyOrder("/var/lib/magrathea", "/tmp/magrathea"); }

    @Given("ADR 0027 is accepted")
    public void adrAccepted() throws IOException { assertThat(Ep8EvidenceSupport.read("docs/adr/0027-authoritative-cluster-control-plane-and-direct-quorum-data-path.md")).contains("Accepted — architectural decision only"); }

    @Given("all required application SBOM, image SBOM, license, OWASP, and hardened-runtime evidence gates have passed for one immutable revision and image digest")
    public void allSupplyGatesPassed() throws IOException {
        loadManifest(); loadApplication(); loadImage(); loadHardening(); licenses = Ep8EvidenceSupport.json("target/supply-chain/license-inventory.json");
        assertThat(manifest.path("acceptanceEligible").asBoolean()).isTrue();
        assertThat(manifest.at("/owaspDependencyCheck/status").asText()).isEqualTo("complete");
        assertThat(manifest.at("/owaspDependencyCheck/currentRevision").asBoolean()).isTrue();
        assertThat(hardening.path("status").asText()).isEqualTo("passed");
    }

    @When("the requirements appendix, roadmap, and release evidence summarize EP-8")
    public void loadStatusSources() throws IOException { assertThat(Ep8EvidenceSupport.requiredFile("docs/arc42/generated/gherkin-requirements.adoc")).isRegularFile(); }

    @Then("they may report the validated EP-8 architecture and supply-chain gate scope as complete")
    public void ep8ScopeOnly() { assertThat(manifest.path("schema").asText()).isEqualTo("magrathea-supply-chain-evidence-1.0"); }

    @Then("EP-10 and networked cluster execution remain {string} until their own shared semantic multi-node and fault-injection scenarios pass")
    public void ep10Absent(String status) throws IOException { assertThat(status).isEqualTo("@absent"); assertThat(Ep8EvidenceSupport.read("docs/arc42/src/07_deployment_view.adoc")).contains("No production distributed-cluster claims are made until EP-10"); }

    @Then("no EP-8 result claims implemented membership, consensus correctness, multi-node persistence, quorum transfer, healing, rebalance, partition safety, or distributed production readiness")
    public void noClusterClaim() throws IOException { assertThat(Ep8EvidenceSupport.read("docs/adr/0027-authoritative-cluster-control-plane-and-direct-quorum-data-path.md")).contains("not evidence of networked membership", "not evidence", "remain planned/absent"); }

    @Then("architecture-contract scenarios are reported separately from runtime and artifact evidence")
    public void separateReports() { assertThat(Ep8EvidenceSupport.path("target/cucumber-json/ep8-architecture-contract.json")).isNotEqualTo(Ep8EvidenceSupport.path("target/cucumber-json/ep8-supply-chain-evidence.json")); }

    private void loadManifest() throws IOException { if (manifest == null) manifest = Ep8EvidenceSupport.json(MANIFEST_PATH); }
    private void loadApplication() throws IOException { if (application == null) application = Ep8EvidenceSupport.json("target/supply-chain/application.cdx.json"); }
    private void loadImage() throws IOException { if (image == null) image = Ep8EvidenceSupport.json("target/supply-chain/image.cdx.json"); loadApplication(); loadManifest(); }
    private void loadHardening() throws IOException { if (hardening == null) hardening = Ep8EvidenceSupport.json("target/supply-chain/hardening-evidence.json"); loadManifest(); assertThat(hardening.path("imageId").asText()).isEqualTo(manifest.at("/image/id").asText()); }

    private void assertIdentityProperties(JsonNode component, boolean imageComponent) throws IOException {
        loadManifest();
        JsonNode properties = component.path("properties");
        assertThat(property(properties, "magrathea:evidence:revision")).isEqualTo(manifest.at("/source/revision").asText());
        assertThat(property(properties, "magrathea:evidence:application-version")).isEqualTo(manifest.at("/application/version").asText());
        assertThat(property(properties, "magrathea:evidence:source-date-epoch")).isEqualTo(manifest.at("/source/sourceDateEpoch").asText());
        assertThat(property(properties, "magrathea:evidence:timestamp")).isEqualTo(manifest.at("/source/timestamp").asText());
        if (imageComponent) assertThat(property(properties, "magrathea:evidence:image-id")).isEqualTo(manifest.at("/image/id").asText());
    }

    private void assertManifestHash(String relativePath) throws IOException {
        loadManifest();
        JsonNode artifact = StreamSupport.stream(manifest.path("artifacts").spliterator(), false)
                .filter(item -> relativePath.equals(item.path("path").asText())).findFirst()
                .orElseThrow(() -> new AssertionError("Manifest does not record " + relativePath));
        assertThat(artifact.path("sha256").asText()).isEqualTo(Ep8EvidenceSupport.sha256(Ep8EvidenceSupport.requiredFile(relativePath)));
        assertThat(artifact.path("bytes").asLong()).isEqualTo(Files.size(Ep8EvidenceSupport.path(relativePath)));
    }

    private void assertConfigurationHash(String relativePath) throws IOException {
        loadManifest();
        JsonNode item = StreamSupport.stream(manifest.path("configuration").spliterator(), false)
                .filter(node -> relativePath.equals(node.path("path").asText())).findFirst()
                .orElseThrow(() -> new AssertionError("Manifest does not record configuration " + relativePath));
        assertThat(item.path("sha256").asText()).isEqualTo(Ep8EvidenceSupport.sha256(Ep8EvidenceSupport.path(relativePath)));
    }

    private static String property(JsonNode properties, String name) {
        return StreamSupport.stream(properties.spliterator(), false).filter(item -> name.equals(item.path("name").asText()))
                .map(item -> item.path("value").asText()).findFirst().orElse("");
    }

    private static Set<String> values(JsonNode array, String field) {
        Set<String> result = new HashSet<>();
        array.forEach(item -> result.add(field == null ? item.asText() : item.path(field).asText()));
        return result;
    }

    private static Set<String> attributes(NodeList nodes, String attribute) {
        Set<String> result = new HashSet<>();
        for (int i = 0; i < nodes.getLength(); i++) {
            if (nodes.item(i) instanceof Element element && element.hasAttribute(attribute)) result.add(element.getAttribute(attribute));
        }
        return result;
    }

    private static String command(String... command) throws Exception {
        Process process = new ProcessBuilder(command).directory(Ep8EvidenceSupport.ROOT.toFile()).redirectErrorStream(true).start();
        String output = new String(process.getInputStream().readAllBytes());
        assertThat(process.waitFor()).as(String.join(" ", command) + "\n" + output).isZero();
        return output;
    }
}
