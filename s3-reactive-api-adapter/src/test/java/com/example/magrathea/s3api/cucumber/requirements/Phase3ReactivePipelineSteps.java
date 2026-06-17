package com.example.magrathea.s3api.cucumber.requirements;

import io.cucumber.datatable.DataTable;
import io.cucumber.java.Before;
import io.cucumber.java.PendingException;
import io.cucumber.java.en.Given;
import io.cucumber.java.en.Then;
import io.cucumber.java.en.When;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.test.web.reactive.server.WebTestClient;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.io.UncheckedIOException;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;

import static org.junit.jupiter.api.Assertions.*;

/**
 * WebTestClient step definitions for Phase 3 reactive pipeline requirements.
 *
 * <p>This class implements the {@code @webclient} Examples rows from
 * {@code phase-3-reactive-pipeline.feature}. Steps that map to externally
 * observable S3 HTTP behavior are implemented using {@link WebTestClient}.
 * Steps that require internal pipeline observability (StorageStage ordering,
 * StorageEvent records, Reactor backpressure signals, DataBuffer lifecycle)
 * throw {@link PendingException} with a clear note about the required
 * pipeline-unit runner and Phase 3 abstraction status.
 *
 * <p>Shared setup steps (bucket creation, filesystem root setup, storage
 * engine profile configuration) are reused from
 * {@link Phase2FilesystemReliabilitySteps} in the same glue package.
 */
public class Phase3ReactivePipelineSteps {

    private static final Path PROJECT_ROOT = Path.of("").toAbsolutePath().normalize();

    /** Small deterministic fixture size for webclient runner (1 MiB). */
    private static final int WEBCLIENT_FIXTURE_SIZE_BYTES = 1024 * 1024;

    @Autowired
    private WebTestClient webTestClient;

    /** Shared state managed by Phase2FilesystemReliabilitySteps (same glue package). */
    @Autowired
    private Phase2FilesystemReliabilitySteps.State state;

    @Autowired
    private RequirementsTestApp.MutableFileSystemWriteFaultInjector faultInjector;

    /** Phase 3–specific last HTTP response from the pipeline upload or read step. */
    private Response lastPipelineResponse;

    @Before
    public void resetPhase3State() {
        lastPipelineResponse = null;
    }

    // ── Background ────────────────────────────────────────────────────────────────

    @Given("reactive pipeline event capture is enabled for the selected validation mode")
    public void reactivePipelineEventCaptureIsEnabled() {
        // No-op for webclient runner: this runner validates externally observable S3 HTTP
        // behavior only. Reactive pipeline event capture (typed StorageEvent records per
        // StorageStage) is an internal observability concern handled by the pipeline-unit
        // runner once StorageStage, StorageContext, and StorageEvent are fully wired to
        // external test runners.
    }

    // ── REQ-PIPELINE-002 ─────────────────────────────────────────────────────────

    /**
     * Ensures a deterministic fixture file exists at the given project-relative path.
     * For the webclient runner a 1 MiB file is generated; the full 256 MiB file is
     * produced by the pipeline-unit runner for actual backpressure validation.
     */
    @Given("fixture file {string} is a deterministic 256 MiB object")
    public void fixtureFileIsADeterministic256MiBObject(String fixturePath) throws IOException {
        Path file = PROJECT_ROOT.resolve(fixturePath).normalize();
        Files.createDirectories(file.getParent());
        if (!Files.exists(file)) {
            byte[] block = new byte[1024];
            for (int i = 0; i < block.length; i++) block[i] = (byte) (i & 0xff);
            try (OutputStream out = Files.newOutputStream(file,
                    StandardOpenOption.CREATE, StandardOpenOption.TRUNCATE_EXISTING)) {
                for (int i = 0; i < WEBCLIENT_FIXTURE_SIZE_BYTES / block.length; i++) {
                    out.write(block);
                }
            }
        }
        byte[] bytes = Files.readAllBytes(file);
        state.fixtureBytes.put(fixturePath, bytes);
        state.fixtureFile = fixturePath;
    }

    @Given("the write pipeline chunk size is {string} with at most {string} in-flight chunks")
    public void writePipelineChunkSizeWithAtMostInFlightChunks(String chunkSize, String maxInFlight) {
        throw new PendingException(
                "REQ-PIPELINE-002: write pipeline chunk size and in-flight chunk limit are internal"
                + " pipeline-stage configuration concerns (StorageContext policy resolution)."
                + " They are not externally configurable via S3 HTTP API. Requires pipeline-unit"
                + " runner with StorageStage/StorageContext abstractions to validate backpressure.");
    }

    @Given("the upload body is supplied as a demand-controlled stream for bucket {string} and key {string}")
    public void uploadBodyIsSuppliedAsDemandControlledStream(String bucket, String key) {
        throw new PendingException(
                "REQ-PIPELINE-002: demand-controlled reactive stream setup is an internal backpressure"
                + " testing concern. WebTestClient cannot observe or control per-chunk reactive demand"
                + " signals. Requires pipeline-unit runner with StepVerifier-based demand control.");
    }

    @When("the selected validation runner uploads fixture file {string} through the staged PutObject pipeline")
    public void selectedValidationRunnerUploadsFixture(String fixturePath) {
        // For webclient validation mode: issue an HTTP PUT to the S3 RouterFunction.
        // The pipeline-unit runner validates stage ordering and StorageEvent sequences.
        byte[] bytes = state.fixtureBytes.computeIfAbsent(fixturePath, this::readFixture);
        state.fixtureFile = fixturePath;
        lastPipelineResponse = putObject(state.bucket, state.objectKey, bytes);
    }

    @Then("chunking emits ordered chunks no larger than the configured chunk size")
    public void chunkingEmitsOrderedChunksNoLargerThanConfiguredChunkSize() {
        throw new PendingException(
                "REQ-PIPELINE-002: chunk ordering and size bounds are internal pipeline-stage assertions"
                + " on the chunking StorageStage output. Requires pipeline-unit instrumentation via"
                + " StorageEvent records. Not observable externally via HTTP.");
    }

    @Then("chunk-persistence requests more chunks only as downstream capacity becomes available")
    public void chunkPersistenceRequestsMoreChunksOnlyAsCapacityBecomesAvailable() {
        throw new PendingException(
                "REQ-PIPELINE-002: chunk-persistence demand request rate is an internal Reactor"
                + " backpressure signal between pipeline stages. Requires pipeline-unit runner with"
                + " controlled upstream publisher and StorageEvent demand monitoring.");
    }

    @Then("the number of retained uncommitted chunks never exceeds the configured in-flight chunk limit")
    public void retainedUncommittedChunksNeverExceedInFlightChunkLimit() {
        throw new PendingException(
                "REQ-PIPELINE-002: in-flight uncommitted chunk count is an internal pipeline-stage"
                + " metric requiring StorageEvent instrumentation and concurrent demand monitoring."
                + " Not externally observable via HTTP.");
    }

    @Then("the measured payload memory retained by the pipeline remains bounded by the configured chunk window plus codec overhead, not by total object size")
    public void measuredPayloadMemoryRemainsBuffered() {
        throw new PendingException(
                "REQ-PIPELINE-002: JVM heap measurement for pipeline memory bounds requires pipeline-unit"
                + " runner with heap profiling or explicit DataBuffer leak detection. Not observable"
                + " via external HTTP API calls.");
    }

    @Then("the committed manifest references all chunks in write order with the correct total object length")
    public void committedManifestReferencesAllChunksInWriteOrder() {
        throw new PendingException(
                "REQ-PIPELINE-002: committed manifest chunk ordering and total length are internal"
                + " filesystem artifacts. Can be validated via storage-engine filesystem inspection"
                + " in the pipeline-unit runner. This webclient assertion is pending the pipeline"
                + " stage order contract being validated first.");
    }

    @Then("production object-content stages do not perform a global reduce, collectList, or whole-object byte-array assembly over the 256 MiB body")
    public void productionStagesDoNotPerformGlobalReduce() {
        throw new PendingException(
                "REQ-PIPELINE-002: absence of global reduce/collectList is a code-level contract"
                + " verified by the pipeline-unit runner through demand monitoring and StorageEvent"
                + " sequences, not by HTTP-level observation. Requires StorageStage implementation.");
    }

    @Then("the S3 client can read bucket {string} and key {string} and receive the exact bytes from fixture file {string}")
    public void s3ClientCanReadBucketAndKeyAndReceiveExactBytes(String bucket, String key, String fixturePath) {
        // HTTP-observable: verify the object can be retrieved and matches the uploaded fixture.
        Response response = getObject(bucket, key);
        assertEquals(200, response.status(), response.bodyAsString());
        byte[] expected = state.fixtureBytes.computeIfAbsent(fixturePath, this::readFixture);
        assertArrayEquals(expected, response.body(),
                "GetObject response body must exactly match uploaded fixture: " + fixturePath);
    }

    // ── REQ-PIPELINE-003 ─────────────────────────────────────────────────────────

    @Given("a committed object exists in bucket {string} at key {string} uploaded from fixture file {string}")
    public void aCommittedObjectExistsInBucket(String bucket, String key, String fixturePath) {
        // Upload the fixture via HTTP PUT to establish a committed object for read validation.
        byte[] bytes = state.fixtureBytes.computeIfAbsent(fixturePath, this::readFixture);
        state.bucket = bucket;
        state.objectKey = key;
        state.fixtureFile = fixturePath;
        Response response = putObject(bucket, key, bytes);
        assertEquals(200, response.status(),
                "Expected committed object upload to succeed: " + response.bodyAsString());
    }

    @Then("the committed manifest lists multiple chunks with stable ordinal positions and checksums")
    public void committedManifestListsMultipleChunks() {
        throw new PendingException(
                "REQ-PIPELINE-003: manifest chunk list with stable ordinal positions and checksums is"
                + " validated by the pipeline-unit runner via StorageEvent records and filesystem"
                + " inspection of the manifest artifact. The webclient runner can verify the object"
                + " is readable but cannot inspect the internal manifest structure without"
                + " pipeline-unit infrastructure.");
    }

    @When("the selected validation runner reads bucket {string} and key {string} through the staged GetObject pipeline")
    public void selectedValidationRunnerReadsThroughPipeline(String bucket, String key) {
        // For webclient validation mode: issue an HTTP GET to the S3 RouterFunction.
        // The pipeline-unit runner validates StorageEvent stage sequencing and chunk ordering.
        lastPipelineResponse = getObject(bucket, key);
    }

    @Then("StorageEvent records show start and success for the read stages in this exact order:")
    public void storageEventRecordsShowReadStagesInOrder(DataTable stages) {
        throw new PendingException(
                "REQ-PIPELINE-003: StorageEvent records for read pipeline stage ordering require the"
                + " StorageStage, StorageContext, and StorageEvent abstractions from Phase 3."
                + " These are internal instrumentation records not observable via external HTTP API."
                + " Requires pipeline-unit runner.");
    }

    @Then("chunk-reading emits chunks in the same ordinal order recorded in the committed manifest")
    public void chunkReadingEmitsChunksInManifestOrdinalOrder() {
        throw new PendingException(
                "REQ-PIPELINE-003: chunk-reading ordinal order is an internal pipeline-stage assertion"
                + " on chunk-reading StorageStage events. Requires pipeline-unit runner with"
                + " StorageEvent instrumentation.");
    }

    @Then("response-streaming begins after the first verified chunk is available, without waiting for every chunk to be read")
    public void responseStreamingBeginsAfterFirstVerifiedChunk() {
        throw new PendingException(
                "REQ-PIPELINE-003: response-streaming start timing relative to chunk-reading is an"
                + " internal Reactor demand and scheduling concern. Not observable via external HTTP"
                + " response timing. Requires pipeline-unit runner with controlled demand.");
    }

    @Then("downstream response demand controls how many chunks are read ahead")
    public void downstreamResponseDemandControlsChunksReadAhead() {
        throw new PendingException(
                "REQ-PIPELINE-003: downstream response demand control is an internal backpressure"
                + " concern in the read pipeline. Requires pipeline-unit runner with StepVerifier"
                + " demand control.");
    }

    @Then("production read stages do not perform a global reduce, collectList, or whole-object byte-array assembly over the object content")
    public void productionReadStagesDoNotPerformGlobalReduce() {
        throw new PendingException(
                "REQ-PIPELINE-003: absence of global reduce in read path is a code-level contract."
                + " Verified by pipeline-unit runner through demand monitoring and StorageEvent"
                + " sequences. Not observable via external HTTP API.");
    }

    @Then("the streamed S3 response bytes exactly match fixture file {string}")
    public void streamedS3ResponseBytesExactlyMatchFixtureFile(String fixturePath) {
        // HTTP-observable: verify the GET response body matches the uploaded fixture.
        assertNotNull(lastPipelineResponse, "a pipeline GET response should have been captured");
        assertEquals(200, lastPipelineResponse.status(), lastPipelineResponse.bodyAsString());
        byte[] expected = state.fixtureBytes.computeIfAbsent(fixturePath, this::readFixture);
        assertArrayEquals(expected, lastPipelineResponse.body(),
                "GetObject response body must exactly match fixture: " + fixturePath);
    }

    // ── REQ-PIPELINE-004 ─────────────────────────────────────────────────────────

    /**
     * Maps the declared stage name to the available {@link RequirementsTestApp.MutableFileSystemWriteFaultInjector}.
     * Only {@code chunk-persistence} and {@code manifest-persistence} stage faults are supported
     * via the existing injector; other stage faults require StorageStage-level injection.
     */
    @Given("the pipeline failure injector causes stage {string} to fail with reason {string}")
    public void pipelineFailureInjectorCausesStageToFail(String failingStage, String failureReason) {
        switch (failingStage) {
            case "chunk-persistence" -> faultInjector.interruptAfterChunkTempWrite(true);
            case "manifest-persistence" -> faultInjector.interruptAfterManifestTempWrite(true);
            default -> throw new PendingException(
                    "REQ-PIPELINE-004: no fault injector is wired for pipeline stage '" + failingStage
                    + "'. Only chunk-persistence and manifest-persistence faults are available via"
                    + " MutableFileSystemWriteFaultInjector. Other stage faults require StorageStage"
                    + "-level injection.");
        }
    }

    @Then("the pipeline emits exactly one StorageEvent failure for stage {string} with reason {string}")
    public void pipelineEmitsExactlyOneStorageEventFailure(String failingStage, String failureReason) {
        throw new PendingException(
                "REQ-PIPELINE-004: StorageEvent failure records per stage require the StorageEvent"
                + " abstraction from Phase 3. Not observable via external HTTP API."
                + " Requires pipeline-unit runner with StorageEvent instrumentation.");
    }

    @Then("no stage after {string} emits a success event for this StorageContext")
    public void noStageAfterEmitsSuccessEvent(String failingStage) {
        throw new PendingException(
                "REQ-PIPELINE-004: per-stage StorageEvent success records require StorageContext"
                + " and StorageEvent abstractions from Phase 3. Requires pipeline-unit runner.");
    }

    @Then("cleanup events run for every completed stage that owns temporary files, open buffers, or object publication handles")
    public void cleanupEventsRunForEveryCompletedStage() {
        throw new PendingException(
                "REQ-PIPELINE-004: cleanup StorageEvent records per stage require StorageStage"
                + " cleanup lifecycle from Phase 3. Requires pipeline-unit runner.");
    }

    @Then("all temporary files created for the failed write are removed or quarantined in filesystem root {string}")
    public void allTemporaryFilesCreatedForFailedWriteAreRemovedOrQuarantined(String storageRoot) {
        throw new PendingException(
                "REQ-PIPELINE-004: cleanup of temporary files after staged pipeline failure requires"
                + " StorageStage cleanup lifecycle (Phase 3). The existing"
                + " MutableFileSystemWriteFaultInjector does not guarantee cleanup via staged"
                + " pipeline events. Pending pipeline-unit runner validation.");
    }

    @Then("no committed manifest or object reference is published for bucket {string} and key {string}")
    public void noCommittedManifestOrObjectReferenceIsPublished(String bucket, String key) {
        throw new PendingException(
                "REQ-PIPELINE-004: no manifest/object-reference assertion after staged pipeline"
                + " failure requires pipeline-unit runner with StorageStage-level failure propagation."
                + " Use existing Phase 2 fault injection tests for committed state assertions.");
    }

    @Then("the S3 PutObject response exposes a deterministic storage failure rather than a partial success")
    public void s3PutObjectResponseExposesDeterministicStorageFailure() {
        // HTTP-observable: the PutObject response must signal an error status.
        assertNotNull(lastPipelineResponse, "a pipeline PUT response should have been captured");
        assertTrue(lastPipelineResponse.status() >= 400,
                "PutObject with fault-injected stage failure should return an error status but got: "
                + lastPipelineResponse.status() + " " + lastPipelineResponse.bodyAsString());
    }

    @Then("a later S3 GetObject for bucket {string} and key {string} reports that the object is absent")
    public void laterS3GetObjectReportsThatObjectIsAbsent(String bucket, String key) {
        // HTTP-observable: after a failed upload the object must not be accessible.
        Response response = getObject(bucket, key);
        assertEquals(404, response.status(),
                "GetObject after failed upload should return 404 but got: "
                + response.status() + " " + response.bodyAsString());
    }

    // ── REQ-PIPELINE-005 ─────────────────────────────────────────────────────────

    @Given("the staged PutObject pipeline has persisted at least {string} unpublished chunks for bucket {string} and key {string}")
    public void stagedPutObjectPipelineHasPersistedUnpublishedChunks(String chunkCount, String bucket, String key) {
        throw new PendingException(
                "REQ-PIPELINE-005: partial unpublished chunk persistence as a precondition requires"
                + " the staged pipeline to be running in a partially-complete state. This requires"
                + " pipeline-unit runner with demand-controlled stream and explicit subscription"
                + " cancellation before manifest-persistence. Not achievable via synchronous HTTP.");
    }

    @When("the selected validation runner cancels the upload subscription before manifest-persistence starts")
    public void selectedValidationRunnerCancelsUploadBeforeManifestPersistence() {
        throw new PendingException(
                "REQ-PIPELINE-005: mid-stream upload cancellation before manifest-persistence requires"
                + " reactive subscription cancellation in the pipeline-unit runner. WebTestClient"
                + " issues synchronous HTTP requests; mid-stream cancellation is not testable via"
                + " HTTP without pipeline-unit infrastructure.");
    }

    @Then("the pipeline emits a cancellation StorageEvent for the active StorageContext")
    public void pipelineEmitsCancellationStorageEvent() {
        throw new PendingException(
                "REQ-PIPELINE-005: cancellation StorageEvent requires StorageEvent abstraction from"
                + " Phase 3. Requires pipeline-unit runner.");
    }

    @Then("active upstream publishers stop receiving additional demand after cancellation")
    public void activeUpstreamPublishersStopReceivingDemandAfterCancellation() {
        throw new PendingException(
                "REQ-PIPELINE-005: upstream publisher demand termination after cancellation is an"
                + " internal Reactor backpressure assertion. Requires pipeline-unit runner with"
                + " demand monitoring.");
    }

    @Then("all retained DataBuffer instances and open file handles owned by the cancelled pipeline are released")
    public void allRetainedDataBufferInstancesAndFileHandlesAreReleased() {
        throw new PendingException(
                "REQ-PIPELINE-005: DataBuffer and file handle release after cancellation requires"
                + " pipeline-unit runner with resource leak detection (e.g., Netty leak detector)."
                + " Not externally observable via HTTP.");
    }

    @Then("cleanup events remove or quarantine unpublished chunks and temporary files in filesystem root {string}")
    public void cleanupEventsRemoveOrQuarantineUnpublishedChunks(String storageRoot) {
        throw new PendingException(
                "REQ-PIPELINE-005: cleanup events for cancelled pipeline require StorageStage cleanup"
                + " lifecycle from Phase 3. Requires pipeline-unit runner.");
    }

    @Then("no manifest is committed for the cancelled upload")
    public void noManifestIsCommittedForCancelledUpload() {
        throw new PendingException(
                "REQ-PIPELINE-005: no committed manifest after cancelled upload requires StorageStage"
                + " cancellation propagation from Phase 3. Requires pipeline-unit runner.");
    }

    @Then("no object reference is committed for bucket {string} and key {string}")
    public void noObjectReferenceIsCommittedForBucketAndKey(String bucket, String key) {
        throw new PendingException(
                "REQ-PIPELINE-005: no committed object reference after cancelled upload requires"
                + " StorageStage cancellation lifecycle from Phase 3. Requires pipeline-unit runner.");
    }

    // ── Private helpers ──────────────────────────────────────────────────────────

    private Response putObject(String bucket, String key, byte[] bytes) {
        var result = webTestClient.put()
                .uri(URI.create("/" + bucket + "/" + key))
                .contentType(MediaType.APPLICATION_OCTET_STREAM)
                .bodyValue(bytes)
                .exchange()
                .expectBody(byte[].class)
                .returnResult();
        return new Response(
                result.getStatus().value(),
                result.getResponseBody() == null ? new byte[0] : result.getResponseBody());
    }

    private Response getObject(String bucket, String key) {
        var result = webTestClient.get()
                .uri(URI.create("/" + bucket + "/" + key))
                .exchange()
                .expectBody(byte[].class)
                .returnResult();
        return new Response(
                result.getStatus().value(),
                result.getResponseBody() == null ? new byte[0] : result.getResponseBody());
    }

    private byte[] readFixture(String fixturePath) {
        // Try classpath first (for src/test/resources fixtures)
        InputStream classpathStream = Thread.currentThread().getContextClassLoader()
                .getResourceAsStream(fixturePath);
        if (classpathStream != null) {
            try (classpathStream) {
                return classpathStream.readAllBytes();
            } catch (IOException e) {
                throw new UncheckedIOException(e);
            }
        }
        // Fall back to project-relative path (for generated/target fixtures)
        Path file = PROJECT_ROOT.resolve(fixturePath).normalize();
        try {
            return Files.readAllBytes(file);
        } catch (IOException e) {
            throw new UncheckedIOException("Fixture not found on classpath or filesystem: " + fixturePath, e);
        }
    }

    private record Response(int status, byte[] body) {
        String bodyAsString() {
            return new String(body, StandardCharsets.UTF_8);
        }
    }
}
