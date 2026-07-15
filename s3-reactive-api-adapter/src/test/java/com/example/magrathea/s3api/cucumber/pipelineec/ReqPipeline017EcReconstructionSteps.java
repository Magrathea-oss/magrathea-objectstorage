package com.example.magrathea.s3api.cucumber.pipelineec;

import com.example.magrathea.storageengine.application.pipeline.BoundedEcReconstructionPort;
import io.cucumber.datatable.DataTable;
import io.cucumber.java.en.Given;
import io.cucumber.java.en.Then;
import io.cucumber.java.en.When;
import org.springframework.beans.factory.annotation.Autowired;

import java.util.List;
import java.util.Set;

/** Thin Cucumber adapter over production pipeline, filesystem, manifest, and decoder behavior. */
public final class ReqPipeline017EcReconstructionSteps {

    @Autowired
    private BoundedEcReconstructionPort decoder;
    private ReqPipeline017EcReconstructionAcceptance acceptance;

    @Given("the S3 API is configured with profile {string} and backend {string}")
    public void configuredProfileAndBackend(String profile, String backend) {
        acceptance().verifyProfileAndBackend(profile, backend);
    }

    @Given("the storage engine stores bytes, manifests, and object references on a real filesystem")
    public void realFilesystemStorageIsRequired() {
        acceptance().verifyRealFilesystem();
    }

    @Given("each scenario uses a clean storage-engine filesystem root {string}")
    public void cleanRootPatternIsDeclared(String rootPattern) {
        acceptance().verifyRootPattern(rootPattern);
    }

    @Given("reactive pipeline event capture is enabled for the selected validation mode")
    public void pipelineEventCaptureIsDeclared() {
        acceptance().enablePipelineEventCapture();
    }

    @Given("validation mode {string} is selected for requirement {string}")
    public void validationModeIsSelected(String mode, String requirement) {
        acceptance().selectValidationMode(mode, requirement);
    }

    @Given("the storage engine operator uses filesystem root {string}")
    public void storageRootIsSelected(String root) {
        acceptance().selectStorageRoot(root);
    }

    @Given("fixture file {string} is the deterministic 8 MiB EC source from REQ-PIPELINE-015")
    public void ecFixtureIsSelected(String fixture) {
        acceptance().selectFixture(fixture);
    }

    @Given("storage class {string} selects four data shards and two parity shards in each bounded 4 MiB stripe")
    public void ecPolicyIsSelected(String storageClass) {
        acceptance().selectEcPolicy(storageClass);
    }

    @Given("a new EC write and legacy schema compatibility probes prepare these manifest versions:")
    public void schemaCompatibilityInputsAreDeclared(DataTable table) {
        assertHeader(table, List.of("schema", "prepared manifest"));
        acceptance().declareCompatibilityVersions(table.asMaps());
    }

    @When("the pipeline unit runner submits the prepared manifest and survivor set to the transport-neutral bounded EC reconstruction decoder")
    public void submitPreparedReconstruction() {
        acceptance().submitPreparedReconstruction();
    }

    @Then("the new EC write uses manifest schema 3 and every typed EC artifact explicitly binds these committed facts:")
    public void schemaThreeFactsAreBound(DataTable table) {
        assertHeader(table, List.of("committed fact", "required binding"));
        require(table.asMaps().stream().map(row -> row.get("committed fact")).collect(java.util.stream.Collectors.toSet())
                        .equals(Set.of("artifact kind", "stripe index", "shard index", "k", "m",
                                "parity flag and kind", "logical data length", "stored length",
                                "stored checksum", "locations")),
                "schema-3 fact table is incomplete");
        acceptance().assertSchemaThreeFacts();
    }

    @Then("filesystem inspection separately finds every chunk-id data file beneath {string}")
    public void chunkFilesAreInspectedSeparately(String chunksRoot) {
        acceptance().assertChunkFilesInspectedSeparately(chunksRoot);
    }

    @Then("manifest construction rejects an EC storage trace that omits explicit layout or contradicts the selected k and m")
    public void invalidEcStorageTraceLayoutsAreRejected() {
        acceptance().assertInvalidStorageTraceLayoutsRejected();
    }

    @Then("schemas 0, 1, and 2 remain readable through their compatibility paths")
    public void legacySchemasRemainReadable() {
        acceptance().assertCompatibilityPathsReadable();
    }

    @Then("schema-2 EC metadata without an explicit layout is rejected for reconstruction without inferring artifact order")
    public void ambiguousSchemaTwoEcFailsClosed() {
        acceptance().assertAmbiguousSchemaTwoRejected();
    }

    @Given("schema-3 stripe 0 has committed 4+2 shard metadata and these independently evaluated survivor cases:")
    public void survivorCasesAreDeclared(DataTable table) {
        assertHeader(table, List.of("case", "missing shard indices", "corrupt shard indices",
                "checksum-valid survivor indices", "expected regenerated indices"));
        require(table.asMaps().size() == 4, "expected four missing/corrupt survivor cases");
        require(table.asMaps().stream()
                        .allMatch(row -> row.get("checksum-valid survivor indices").split(",").length == 4),
                "every successful case must select exactly four checksum-valid survivors");
        acceptance().declareSurvivorCases(table.asMaps());
    }

    @Then("each case reconstructs exactly its expected one or two unavailable shard indices from the selected four survivors")
    public void expectedShardIndicesAreReconstructed() {
        acceptance().assertExpectedShardIndicesReconstructed();
    }

    @Then("all 15 four-of-six survivor combinations reproduce both omitted shards byte-for-byte across data\\/data, data\\/parity, and parity\\/parity losses")
    public void everyFourOfSixCombinationReconstructs() {
        acceptance().assertEveryFourOfSixCombinationReconstructs();
    }

    @Then("every regenerated shard byte length and SHA-256 match its committed schema-3 manifest facts before output is accepted")
    public void regeneratedShardsMatchCommittedFacts() {
        acceptance().assertRegeneratedShardsVerified();
    }

    @Then("the reconstructed stripe's logical bytes exactly match the corresponding bytes in fixture file {string}")
    public void reconstructedStripeMatchesFixture(String fixture) {
        acceptance().assertReconstructedStripeMatchesFixture(fixture);
    }

    @Given("a 6291593-byte logical view of the fixture has one full 4 MiB stripe and a 2097289-byte final stripe")
    public void shortFinalStripeIsDeclared() {
        acceptance().declareShortLogicalView();
    }

    @Given("schema-3 metadata records the exact logical data length, stored length, checksum, and location for all six final-stripe artifacts")
    public void finalStripeMetadataIsDeclared() {
        acceptance().verifyShortStripeMetadataPrepared();
    }

    @Given("final-stripe data shard 2 is missing while checksum-valid survivor indices 0,1,3,4 are supplied")
    public void finalStripeSurvivorsAreDeclared() {
        acceptance().selectShortStripeSurvivors();
    }

    @Then("reconstruction emits exactly 6291593 logical bytes without exposing final-stripe padding")
    public void shortLogicalOutputIsExact() {
        acceptance().assertShortLogicalOutput();
    }

    @Then("retained reconstruction state never exceeds these object-size-independent bounds:")
    public void reconstructionMemoryIsBounded(DataTable table) {
        assertHeader(table, List.of("retained state", "bound"));
        require(table.asMaps().size() == 4,
                "expected logical, shard, boundary-snapshot, and matrix workspace bounds");
        acceptance().assertWorkspaceBounds();
    }

    @Then("caller-owned source arrays and copies returned by defensive accessors are outside the decoder-owned workspace measurement")
    public void defensiveCopiesRemainBoundaryOwned() {
        acceptance().assertDefensiveAccessorCopiesRemainBoundaryOwned();
    }

    @Then("no earlier stripe or whole 8 MiB fixture is materialized while the final stripe is reconstructed")
    public void noWholeObjectMaterializationOccurs() {
        acceptance().assertNoWholeObjectMaterialization();
    }

    @Given("the following invalid reconstruction requests are evaluated independently:")
    public void invalidRequestsAreDeclared(DataTable table) {
        assertHeader(table, List.of("invalid request", "prepared defect"));
        Set<String> names = table.asMaps().stream()
                .map(row -> row.get("invalid request"))
                .collect(java.util.stream.Collectors.toSet());
        require(names.equals(Set.of(
                        "fewer than k valid survivors", "duplicate shard index",
                        "out-of-range shard index", "inconsistent k and m",
                        "inconsistent stripe metadata", "wrong shard size",
                        "wrong shard checksum", "unsupported future schema")),
                "fail-closed request table is incomplete");
        acceptance().declareInvalidRequests(table.asMaps());
    }

    @Then("every invalid request fails closed with no accepted reconstructed shard or logical stripe")
    public void invalidRequestsFailClosed() {
        acceptance().assertInvalidRequestsFailClosed();
    }

    @Then("no artifact, manifest, object reference, or replacement location is published in filesystem root {string}")
    public void invalidRequestsPublishNothing(String root) {
        acceptance().assertNoPublication(root);
    }

    @Given("schema-3 stripe 1 is prepared with missing shard index 2 and checksum-valid survivor indices 0,1,3,5")
    public void planningOnlyInputIsDeclared() {
        acceptance().selectPlanningOnlyInput();
    }

    @Then("accepted output contains only verified stripe and regenerated-shard reconstruction results")
    public void outputIsReconstructionOnly() {
        acceptance().assertPlanningOnlyOutput();
    }

    @Then("reconstruction work leaves a Reactor caller thread and runs on a dedicated scheduler bounded to one worker and 16 queued tasks")
    public void reconstructionRunsOnBoundedScheduler() {
        acceptance().assertBoundedExecutionScheduler();
    }

    @Then("this requirement makes none of these later-slice claims:")
    public void laterSliceClaimsAreExcluded(DataTable table) {
        assertHeader(table, List.of("excluded claim"));
        require(table.asMaps().size() == 7, "expected seven explicitly excluded later-slice claims");
        acceptance().assertExcludedClaims(table.asMaps().stream()
                .map(row -> row.get("excluded claim"))
                .toList());
    }

    private static void assertHeader(DataTable table, List<String> expected) {
        require(!table.cells().isEmpty() && table.cells().get(0).equals(expected),
                "unexpected table header: " + table.cells());
    }

    private ReqPipeline017EcReconstructionAcceptance acceptance() {
        if (acceptance == null) {
            acceptance = new ReqPipeline017EcReconstructionAcceptance(decoder);
        }
        return acceptance;
    }

    private static void require(boolean condition, String message) {
        if (!condition) throw new AssertionError(message);
    }
}
