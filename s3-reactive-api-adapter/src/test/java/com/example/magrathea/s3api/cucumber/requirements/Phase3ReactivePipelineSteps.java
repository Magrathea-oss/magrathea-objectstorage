package com.example.magrathea.s3api.cucumber.requirements;

import com.example.magrathea.objectstorage.repository.storageengine.adapter.StorageEngineReactiveS3ObjectRepository;
import com.example.magrathea.storageengine.application.pipeline.StorageEvent;
import com.example.magrathea.storageengine.application.pipeline.StorageEventType;
import com.example.magrathea.storageengine.application.pipeline.StorageOperation;
import com.example.magrathea.storageengine.application.service.ReactiveStorageOrchestrator;
import com.example.magrathea.storageengine.domain.valueobject.ChunkId;
import com.example.magrathea.storageengine.domain.valueobject.NodeId;
import com.example.magrathea.storageengine.infrastructure.filesystem.FileSystemRecoveryScanner;
import com.example.magrathea.storageengine.infrastructure.filesystem.FileSystemStorageNode;
import io.cucumber.datatable.DataTable;
import io.cucumber.java.After;
import io.cucumber.java.Before;
import io.cucumber.java.PendingException;
import io.cucumber.java.en.Given;
import io.cucumber.java.en.Then;
import io.cucumber.java.en.When;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.ApplicationContext;
import io.netty.buffer.PooledByteBufAllocator;
import org.springframework.core.io.buffer.DataBuffer;
import org.springframework.core.io.buffer.DataBufferUtils;
import org.springframework.core.io.buffer.NettyDataBuffer;
import org.springframework.core.io.buffer.NettyDataBufferFactory;
import org.springframework.http.MediaType;
import org.springframework.test.web.reactive.server.WebTestClient;
import reactor.core.publisher.Flux;

import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.ByteBuffer;
import java.nio.channels.FileChannel;
import java.nio.file.StandardOpenOption;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.HexFormat;
import java.util.List;
import java.util.Properties;
import java.util.Queue;
import java.util.UUID;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.atomic.AtomicLong;

import static org.junit.jupiter.api.Assertions.*;

/**
 * WebTestClient step definitions for Phase 3 reactive pipeline requirements.
 *
 * <p>This class implements the {@code @webclient} Examples rows from
 * {@code phase-3-reactive-pipeline.feature}. Steps that map to externally
 * observable S3 HTTP behavior are implemented using {@link WebTestClient}.
 * The runner combines client-driven request/response streams with test-only
 * StorageEvent and filesystem inspection where the shared Ability requires
 * internal evidence. Unsupported client-cancellation steps remain explicit
 * {@link PendingException} cases rather than being inferred from HTTP status.
 *
 * <p>Shared setup steps (bucket creation, filesystem root setup, storage
 * engine profile configuration) are reused from
 * {@link Phase2FilesystemReliabilitySteps} in the same glue package.
 */
public class Phase3ReactivePipelineSteps {

    private static final Path PROJECT_ROOT = Path.of("").toAbsolutePath().normalize();

    private static final int WEBCLIENT_FIXTURE_SIZE_BYTES = 1024 * 1024;
    private static final long LARGE_OBJECT_SIZE = 256L * 1024 * 1024;
    private static final int LARGE_UPLOAD_BUFFER_SIZE = 64 * 1024;

    @Autowired
    private WebTestClient webTestClient;

    /** Shared state managed by Phase2FilesystemReliabilitySteps (same glue package). */
    @Autowired
    private Phase2FilesystemReliabilitySteps.State state;

    @Autowired
    private RequirementsTestApp.MutableFileSystemWriteFaultInjector faultInjector;

    @Autowired
    private Phase3StorageEventRecorder eventRecorder;

    @Autowired
    private ReactiveStorageOrchestrator orchestrator;

    @Autowired
    private FileSystemRecoveryScanner recoveryScanner;

    @Autowired
    private ApplicationContext applicationContext;

    /** Phase 3–specific last HTTP response from the pipeline upload or read step. */
    private Response lastPipelineResponse;
    private List<String> manifestChunkIds = List.of();
    private LargeWebClientUpload largeUpload;
    private int configuredChunkSizeBytes;
    private int configuredInFlightChunks;
    private String largeUploadStorageClass;

    @Before
    public void resetPhase3State() {
        lastPipelineResponse = null;
        manifestChunkIds = List.of();
        largeUpload = null;
        configuredChunkSizeBytes = 0;
        configuredInFlightChunks = 0;
        largeUploadStorageClass = null;
        faultInjector.disable();
    }

    @After
    public void stopPhase3EventCapture() {
        eventRecorder.reset();
    }

    // ── Background ────────────────────────────────────────────────────────────────

    @Given("reactive pipeline event capture is enabled for the selected validation mode")
    public void reactivePipelineEventCaptureIsEnabled() {
        eventRecorder.reset();
    }

    // ── REQ-PIPELINE-001 ─────────────────────────────────────────────────────────

    @When("the selected validation runner submits the fixture stream to the staged PutObject pipeline")
    public void selectedRunnerSubmitsFixtureToStagedPutObject() {
        assertInstanceOf(StorageEngineReactiveS3ObjectRepository.class,
            applicationContext.getBean(com.example.magrathea.objectstore.reactive.repository.application.S3ObjectCommandRepository.class));
        assertSame(orchestrator, applicationContext.getBean(ReactiveStorageOrchestrator.class));
        eventRecorder.observe(state.storageRoot);
        byte[] bytes = state.fixtureBytes.computeIfAbsent(state.fixtureFile, this::readFixture);
        lastPipelineResponse = putObject(state.bucket, state.objectKey, bytes);
        assertEquals(200, lastPipelineResponse.status(), lastPipelineResponse.bodyAsString());
    }

    @Then("the write pipeline is assembled from StorageStage instances named in the required write pipeline stage order")
    public void writePipelineUsesRequiredStorageStages() {
        assertEquals(requiredWriteOrder(), stageNames(
            capturedWriteEvents(state.bucket, state.objectKey), StorageEventType.STAGE_STARTED));
    }

    @Then("a single StorageContext carries bucket {string}, key {string}, request metadata, chunk decisions, manifest identifier, and cleanup handles across those stages")
    public void capturedWriteUsesSingleStorageContext(String bucket, String key) {
        List<StorageEvent> events = capturedWriteEvents(bucket, key);
        assertEquals(1, events.stream().map(StorageEvent::correlationId).distinct().count());
        assertTrue(events.stream().allMatch(event -> event.bucket().filter(bucket::equals).isPresent()));
        assertTrue(events.stream().allMatch(event -> event.objectKey().filter(key::equals).isPresent()));
        assertTrue(events.stream().anyMatch(event -> event.manifestId().isPresent()));
        StorageEvent.StageSucceeded chunks = succeeded(events, "chunk-persistence");
        assertTrue(chunks.measurements().chunks() > 0, "chunk decisions must reach persistence");
        assertEquals(1, succeeded(events, "manifest-persistence").measurements().manifests());
    }

    @Then("no manifest is committed before chunk-persistence succeeds for every referenced chunk")
    public void manifestPublicationWaitsForChunks() {
        var chunksSucceeded = eventRecorder.snapshot(StorageEventType.STAGE_SUCCEEDED, "chunk-persistence");
        var manifestStarted = eventRecorder.snapshot(StorageEventType.STAGE_STARTED, "manifest-persistence");
        assertTrue(chunksSucceeded.chunkFiles() > 0);
        assertEquals(0, chunksSucceeded.manifestFiles());
        assertEquals(0, manifestStarted.manifestFiles());
    }

    @Then("no object reference is committed before manifest-persistence succeeds")
    public void objectPublicationWaitsForManifest() {
        var manifestSucceeded = eventRecorder.snapshot(StorageEventType.STAGE_SUCCEEDED, "manifest-persistence");
        var objectStarted = eventRecorder.snapshot(StorageEventType.STAGE_STARTED, "object-index-persistence");
        assertEquals(1, manifestSucceeded.manifestFiles());
        assertEquals(0, manifestSucceeded.objectFiles());
        assertEquals(1, objectStarted.manifestFiles());
        assertEquals(0, objectStarted.objectFiles());
        assertEquals(1, eventRecorder.snapshot(StorageEventType.STAGE_SUCCEEDED,
            "object-index-persistence").objectFiles());
    }

    @Then("content-address entries for new chunks are published only after object-index-persistence commits the owning object")
    public void contentAddressEntriesFollowObjectCommit() {
        var chunksSucceeded = eventRecorder.snapshot(
                StorageEventType.STAGE_SUCCEEDED, "chunk-persistence");
        var manifestSucceeded = eventRecorder.snapshot(
                StorageEventType.STAGE_SUCCEEDED, "manifest-persistence");
        var objectStarted = eventRecorder.snapshot(
                StorageEventType.STAGE_STARTED, "object-index-persistence");
        var objectSucceeded = eventRecorder.snapshot(
                StorageEventType.STAGE_SUCCEEDED, "object-index-persistence");
        assertEquals(0, chunksSucceeded.contentAddressFiles());
        assertEquals(0, manifestSucceeded.contentAddressFiles());
        assertEquals(0, objectStarted.contentAddressFiles());
        assertTrue(objectSucceeded.contentAddressFiles() > 0,
                "committed object must publish at least one content-address entry");
    }

    @Then("every committed manifest chunk reference uses a canonical UUID filename with a matching SHA-256 sidecar readable by the canonical filesystem node")
    public void committedChunksUseCanonicalFilesystemLayout() {
        Properties manifest = loadProperties(onlyFile(state.storageRoot.resolve("metadata/manifests"), ".properties"));
        int chunkCount = Integer.parseInt(manifest.getProperty("chunkCount"));
        assertTrue(chunkCount > 0, "committed manifest must reference at least one chunk");
        FileSystemStorageNode node = new FileSystemStorageNode(
                state.storageRoot.resolve("nodes/node-001"), NodeId.of("node-001"));
        for (int ordinal = 0; ordinal < chunkCount; ordinal++) {
            UUID reference = UUID.fromString(manifest.getProperty("chunk." + ordinal + ".chunkId"));
            Path data = state.storageRoot.resolve("nodes/node-001/chunks").resolve(reference.toString());
            Path sidecar = data.resolveSibling(data.getFileName() + ".sha256");
            assertTrue(Files.isRegularFile(data), "missing canonical chunk " + data);
            assertTrue(Files.isRegularFile(sidecar), "missing checksum sidecar " + sidecar);
            assertEquals(sha256Hex(readBytes(data)), readString(sidecar).trim());
            assertArrayEquals(readBytes(data), node.read(ChunkId.of(reference)).block());
        }
    }

    @Then("a recovery scan of filesystem root {string} reports no incomplete chunk artifacts after publication")
    public void recoveryScanReportsNoIncompleteChunks(String storageRoot) {
        assertEquals(state.storageRoot, PROJECT_ROOT.resolve(storageRoot).normalize());
        assertTrue(recoveryScanner.scan(state.storageRoot).findings().isEmpty());
    }

    @Then("after object-index-persistence succeeds, the selected validation runner reads the committed object through its declared production read entry point and receives the exact bytes from fixture file {string}")
    public void webTestClientReadsCommittedBytes(String fixturePath) {
        Response response = getObject(state.bucket, state.objectKey);
        assertEquals(200, response.status(), response.bodyAsString());
        assertArrayEquals(state.fixtureBytes.computeIfAbsent(fixturePath, this::readFixture), response.body());
        assertTrue(countRegularFiles(state.storageRoot.resolve("nodes")) > 0, "committed chunks must exist");
        assertEquals(1, countRegularFiles(state.storageRoot.resolve("metadata/manifests")),
            "one committed manifest artifact must exist");
        assertEquals(1, countRegularFiles(state.storageRoot.resolve("metadata/objects")),
            "one committed storage-engine object artifact must exist");
        assertEquals(1, countRegularFiles(state.storageRoot.resolve("metadata/s3-object-references")),
            "one committed S3-to-manifest reference must exist");
    }

    // ── REQ-PIPELINE-002 ─────────────────────────────────────────────────────────

    @Given("fixture file {string} is a deterministic 256 MiB object")
    public void fixtureFileIsADeterministic256MiBObject(String fixturePath) throws IOException {
        Path file = PROJECT_ROOT.resolve(fixturePath).normalize();
        Files.createDirectories(file.getParent());
        try (FileChannel channel = FileChannel.open(file,
                StandardOpenOption.CREATE, StandardOpenOption.TRUNCATE_EXISTING,
                StandardOpenOption.WRITE)) {
            channel.position(LARGE_OBJECT_SIZE - 1);
            channel.write(ByteBuffer.wrap(new byte[] {0}));
        }
        assertEquals(LARGE_OBJECT_SIZE, Files.size(file));
        state.fixtureFile = fixturePath;
    }

    @Given("storage class {string} selects the bounded streaming policy for this upload")
    public void storageClassSelectsBoundedStreamingPolicy(String storageClass) {
        assertEquals("PIPELINE", storageClass);
        largeUploadStorageClass = storageClass;
    }

    @Given("the write pipeline chunk size is {string} with at most {string} in-flight chunks")
    public void writePipelineChunkSizeWithAtMostInFlightChunks(String chunkSize, String maxInFlight) {
        assertEquals("1 MiB", chunkSize);
        configuredChunkSizeBytes = 1024 * 1024;
        configuredInFlightChunks = Integer.parseInt(maxInFlight);
        assertEquals(4, configuredInFlightChunks);
    }

    @Given("the upload body is supplied as a demand-controlled stream for bucket {string} and key {string}")
    public void uploadBodyIsSuppliedAsDemandControlledStream(String bucket, String key) {
        assertEquals(state.bucket, bucket);
        state.objectKey = key;
        largeUpload = new LargeWebClientUpload();
    }

    @When("the selected validation runner uploads fixture file {string} through the staged PutObject pipeline")
    public void selectedValidationRunnerUploadsFixture(String fixturePath) {
        assertEquals(state.fixtureFile, fixturePath);
        if (largeUpload == null) {
            eventRecorder.observe(state.storageRoot);
            byte[] bytes = state.fixtureBytes.computeIfAbsent(fixturePath, this::readFixture);
            lastPipelineResponse = putObject(state.bucket, state.objectKey, bytes);
            return;
        }
        eventRecorder.observe(state.storageRoot);
        var result = webTestClient.put()
                .uri(URI.create("/" + state.bucket + "/" + state.objectKey))
                .contentType(MediaType.APPLICATION_OCTET_STREAM)
                .header("x-amz-storage-class", largeUploadStorageClass)
                .contentLength(LARGE_OBJECT_SIZE)
                .body(largeUpload.flux(), DataBuffer.class)
                .exchange()
                .expectBody(byte[].class)
                .returnResult();
        lastPipelineResponse = new Response(result.getStatus().value(),
                result.getResponseBody() == null ? new byte[0] : result.getResponseBody());
        assertEquals(200, lastPipelineResponse.status(), lastPipelineResponse.bodyAsString());
    }

    @Then("chunking emits ordered chunks no larger than the configured chunk size")
    public void chunkingEmitsOrderedChunksNoLargerThanConfiguredChunkSize() {
        Properties manifest = largeObjectManifest();
        int chunkCount = Integer.parseInt(manifest.getProperty("chunkCount"));
        assertEquals(LARGE_OBJECT_SIZE / configuredChunkSizeBytes, chunkCount);
        for (int ordinal = 0; ordinal < chunkCount; ordinal++) {
            assertTrue(Long.parseLong(manifest.getProperty("chunk." + ordinal + ".originalSize"))
                    <= configuredChunkSizeBytes);
            UUID.fromString(manifest.getProperty("chunk." + ordinal + ".chunkId"));
        }
    }

    @Then("chunk-persistence requests more chunks only as downstream capacity becomes available")
    public void chunkPersistenceRequestsMoreChunksOnlyAsCapacityBecomesAvailable() {
        assertTrue(largeUpload.maxSingleRequest() > 0);
        assertTrue(largeUpload.maxSingleRequest() <= configuredInFlightChunks,
                "maximum observed request was " + largeUpload.maxSingleRequest());
        assertEquals(LARGE_OBJECT_SIZE / LARGE_UPLOAD_BUFFER_SIZE, largeUpload.emittedBuffers());
    }

    @Then("the number of payload buffers retained in memory never exceeds the configured in-flight chunk limit")
    public void retainedPayloadChunksNeverExceedInFlightChunkLimit() {
        assertTrue(largeUpload.maxLivePayloadBuffers() <= configuredInFlightChunks,
                "maximum live payload buffers was " + largeUpload.maxLivePayloadBuffers());
        assertTrue(largeUpload.allPayloadBuffersReleased(), "all request payload buffers must be released");
    }

    @Then("the measured payload memory retained by the pipeline remains bounded by the configured chunk window plus codec overhead, not by total object size")
    public void measuredPayloadMemoryRemainsBuffered() {
        long measuredLivePayloadBytes = largeUpload.maxLivePayloadBuffers() * LARGE_UPLOAD_BUFFER_SIZE;
        long chunkWindowAndCopyBound = 2L * configuredChunkSizeBytes;
        assertTrue(measuredLivePayloadBytes + chunkWindowAndCopyBound
                <= (long) configuredInFlightChunks * configuredChunkSizeBytes);
        assertTrue(measuredLivePayloadBytes + chunkWindowAndCopyBound < LARGE_OBJECT_SIZE);
    }

    @Then("the committed manifest references all chunks in write order with the correct total object length")
    public void committedManifestReferencesAllChunksInWriteOrder() {
        Properties manifest = largeObjectManifest();
        int chunkCount = Integer.parseInt(manifest.getProperty("chunkCount"));
        long totalLength = 0;
        List<String> orderedChunkIds = new ArrayList<>();
        for (int ordinal = 0; ordinal < chunkCount; ordinal++) {
            totalLength += Long.parseLong(manifest.getProperty("chunk." + ordinal + ".originalSize"));
            orderedChunkIds.add(manifest.getProperty("chunk." + ordinal + ".chunkId"));
        }
        assertEquals(LARGE_OBJECT_SIZE, totalLength);
        assertEquals(chunkCount, orderedChunkIds.stream().distinct().count());
    }

    @Then("production object-content stages do not perform a global reduce, collectList, or whole-object byte-array assembly over the 256 MiB body")
    public void productionStagesDoNotPerformGlobalReduce() throws IOException {
        String dedup = Files.readString(repositoryRoot().resolve(
                "storage-engine-reactive-infrastructure/src/main/java/com/example/magrathea/storageengine/infrastructure/pipeline/FixedWindowDedupStep.java"));
        String store = Files.readString(repositoryRoot().resolve(
                "storage-engine-reactive-infrastructure/src/main/java/com/example/magrathea/storageengine/infrastructure/pipeline/FileSystemStorePort.java"));
        assertFalse(dedup.contains("DataBufferUtils.join"));
        assertFalse(dedup.contains("readAllBytes("));
        assertFalse(store.contains("DataBufferUtils.join"));
        assertFalse(store.contains("readAllBytes("));
    }

    @Then("the S3 client can read bucket {string} and key {string} and receive the exact bytes from fixture file {string}")
    public void s3ClientCanReadBucketAndKeyAndReceiveExactBytes(String bucket, String key, String fixturePath)
            throws NoSuchAlgorithmException, IOException {
        MessageDigest expected = digestFile(PROJECT_ROOT.resolve(fixturePath));
        MessageDigest actual = MessageDigest.getInstance("SHA-256");
        AtomicLong actualLength = new AtomicLong();
        var result = webTestClient.get()
                .uri(URI.create("/" + bucket + "/" + key))
                .exchange()
                .expectStatus().isOk()
                .returnResult(DataBuffer.class);
        result.getResponseBody().doOnNext(buffer -> {
            byte[] bytes = new byte[buffer.readableByteCount()];
            try {
                buffer.read(bytes);
                actual.update(bytes);
                actualLength.addAndGet(bytes.length);
            } finally {
                DataBufferUtils.release(buffer);
            }
        }).then().block();
        assertEquals(LARGE_OBJECT_SIZE, actualLength.get());
        assertArrayEquals(expected.digest(), actual.digest());
    }

    // ── REQ-PIPELINE-003 ─────────────────────────────────────────────────────────

    @Given("a committed object exists in bucket {string} at key {string} uploaded from fixture file {string}")
    public void aCommittedObjectExistsInBucket(String bucket, String key, String fixturePath) {
        // Upload through the real S3 route and observe the production storage-engine pipeline.
        eventRecorder.observe(state.storageRoot);
        Path fixture = PROJECT_ROOT.resolve(fixturePath).normalize();
        try {
            Files.createDirectories(fixture.getParent());
            byte[] generated = new byte[WEBCLIENT_FIXTURE_SIZE_BYTES];
            for (int index = 0; index < generated.length; index++)
                generated[index] = (byte) ((index * 31) ^ (index / 65536));
            Files.write(fixture, generated);
            state.fixtureBytes.put(fixturePath, generated);
        } catch (IOException error) {
            throw new UncheckedIOException(error);
        }
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
        Path manifest = onlyFile(state.storageRoot.resolve("metadata/manifests"), ".properties");
        Properties properties = new Properties();
        try (InputStream input = Files.newInputStream(manifest)) {
            properties.load(input);
        } catch (IOException error) {
            throw new UncheckedIOException(error);
        }
        int count = Integer.parseInt(properties.getProperty("chunkCount"));
        assertTrue(count > 1, "committed manifest must contain multiple ordered chunks");
        List<String> ids = new ArrayList<>();
        for (int ordinal = 0; ordinal < count; ordinal++) {
            String prefix = "chunk." + ordinal + ".";
            ids.add(properties.getProperty(prefix + "chunkId"));
            assertNotNull(properties.getProperty(prefix + "finalChecksum.algorithm"));
            assertFalse(properties.getProperty(prefix + "finalChecksum.value").isBlank());
        }
        manifestChunkIds = List.copyOf(ids);
    }

    @When("the selected validation runner reads bucket {string} and key {string} through the staged GetObject pipeline")
    public void selectedValidationRunnerReadsThroughPipeline(String bucket, String key) {
        // For webclient validation mode: issue an HTTP GET to the S3 RouterFunction.
        // The pipeline-unit runner validates StorageEvent stage sequencing and chunk ordering.
        lastPipelineResponse = getObject(bucket, key);
    }

    @Then("StorageEvent records show start and success for the write stages in this exact order:")
    public void storageEventRecordsShowWriteStagesInOrder(DataTable stages) {
        List<String> expected = stages.asLists().stream().skip(1).map(List::getFirst).toList();
        List<StorageEvent> events = capturedWriteEvents(state.bucket, state.objectKey);
        assertEquals(expected, stageNames(events, StorageEventType.STAGE_STARTED));
        assertEquals(expected, stageNames(events, StorageEventType.STAGE_SUCCEEDED));
    }

    @Then("StorageEvent records show start and success for the read stages in this exact order:")
    public void storageEventRecordsShowReadStagesInOrder(DataTable stages) {
        List<String> expected = stages.asLists().stream().skip(1).map(List::getFirst).toList();
        List<StorageEvent> reads = eventRecorder.events().stream()
                .filter(event -> event.operation() == StorageOperation.READ).toList();
        assertEquals(expected, stageNames(reads, StorageEventType.STAGE_STARTED));
        assertEquals(expected, stageNames(reads, StorageEventType.STAGE_SUCCEEDED));
    }

    @Then("chunk-reading emits chunks in the same ordinal order recorded in the committed manifest")
    public void chunkReadingEmitsChunksInManifestOrdinalOrder() {
        List<Phase3StorageEventRecorder.ReadObservation> emitted = eventRecorder.readObservations().stream()
                .filter(observation -> observation.kind().equals("emitted")).toList();
        assertEquals(java.util.stream.IntStream.range(0, manifestChunkIds.size()).boxed().toList(),
                emitted.stream().map(Phase3StorageEventRecorder.ReadObservation::ordinal).toList());
        assertEquals(manifestChunkIds, emitted.stream()
                .map(Phase3StorageEventRecorder.ReadObservation::chunkId).toList());
    }

    @Then("response-streaming begins after the first verified chunk is available, without waiting for every chunk to be read")
    public void responseStreamingBeginsAfterFirstVerifiedChunk() {
        List<Phase3StorageEventRecorder.ReadObservation> observations = eventRecorder.readObservations();
        int firstVerified = firstObservation(observations, "verified");
        int firstEmitted = firstObservation(observations, "emitted");
        assertTrue(firstVerified >= 0 && firstVerified < firstEmitted,
                "the first response chunk must follow verification");
        long readsBeforeFirstEmission = observations.subList(0, firstEmitted).stream()
                .filter(value -> value.kind().equals("requested")).count();
        assertTrue(readsBeforeFirstEmission < manifestChunkIds.size(),
                "response must begin before all manifest chunks are read");
    }

    @Then("downstream response demand controls how many chunks are read ahead")
    public void downstreamResponseDemandControlsChunksReadAhead() {
        int verified = 0;
        int emitted = 0;
        int maxAhead = 0;
        for (var observation : eventRecorder.readObservations()) {
            if (observation.kind().equals("verified")) verified++;
            if (observation.kind().equals("emitted")) emitted++;
            maxAhead = Math.max(maxAhead, verified - emitted);
        }
        assertTrue(eventRecorder.readObservations().stream().anyMatch(value -> value.kind().equals("demand")));
        assertTrue(maxAhead <= 1, "production read path must retain at most one verified chunk ahead");
    }

    @Then("the HTTP adapter performs a bounded integrity preflight before response commitment without retaining object bytes")
    public void httpAdapterPerformsBoundedIntegrityPreflight() throws IOException {
        Path repositoryRoot = Files.exists(PROJECT_ROOT.resolve("s3-reactive-api-adapter/pom.xml"))
                ? PROJECT_ROOT : PROJECT_ROOT.getParent();
        String handler = Files.readString(repositoryRoot.resolve(
                "s3-reactive-api-adapter/src/main/java/com/example/magrathea/s3api/adapter/web/S3ObjectOperationsHandler.java"));
        String service = Files.readString(repositoryRoot.resolve(
                "object-store-reactive-application/src/main/java/com/example/magrathea/reactive/application/service/ReactiveObjectService.java"));
        String orchestratorSource = Files.readString(repositoryRoot.resolve(
                "storage-engine-reactive-application/src/main/java/com/example/magrathea/storageengine/application/service/ReactiveStorageOrchestrator.java"));
        assertTrue(handler.contains("getIntegrityVerifiedObjectWithContent(objectKey)"));
        assertTrue(service.contains("validateContentIntegrity(objectWithContent.object().key())"));
        String preflight = orchestratorSource.substring(
                orchestratorSource.indexOf("public Mono<Void> validateReadable"),
                orchestratorSource.indexOf("List<StorageStage> writePipelineStages"));
        assertTrue(preflight.contains("concatMap(chunk -> chunkStorePort.read(chunk.chunkId()).then(), 1)"));
        assertTrue(preflight.contains(".then()"));
        assertFalse(preflight.contains("collectList("));
        assertFalse(preflight.contains("byte[]"));
    }

    @Then("production read stages do not perform a global reduce, collectList, or whole-object byte-array assembly over the object content")
    public void productionReadStagesDoNotPerformGlobalReduce() {
        assertReadPathHasNoAggregation(
                "storage-engine-reactive-application/src/main/java/com/example/magrathea/storageengine/application/service/ReactiveStorageOrchestrator.java",
                "private Mono<StorageContext> planRead", "private static Optional<String> manifestIdValue");
        assertReadPathHasNoAggregation(
                "object-store-reactive-repository-storage-engine-infrastructure/src/main/java/com/example/magrathea/objectstorage/repository/storageengine/adapter/StorageEngineReactiveS3ObjectRepository.java",
                "public Flux<DataBuffer> getContent", "// ── Phase F object config queries");
        assertReadPathHasNoAggregation(
                "s3-reactive-api-adapter/src/main/java/com/example/magrathea/s3api/adapter/web/S3ObjectOperationsHandler.java",
                "public Mono<ServerResponse> getObject", "private Mono<ServerResponse> serveRange");
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
            case "chunk-persistence" -> faultInjector.interruptAfterChunkTempWrite(false, failureReason);
            case "manifest-persistence" -> faultInjector.interruptAfterManifestTempWrite(false, failureReason);
            default -> throw new PendingException(
                    "REQ-PIPELINE-004: no fault injector is wired for pipeline stage '" + failingStage
                    + "'. Only chunk-persistence and manifest-persistence faults are available via"
                    + " MutableFileSystemWriteFaultInjector. Other stage faults require StorageStage"
                    + "-level injection.");
        }
    }

    @Then("the pipeline emits exactly one StorageEvent failure for stage {string} with reason {string}")
    public void pipelineEmitsExactlyOneStorageEventFailure(String failingStage, String failureReason) {
        List<StorageEvent.StageFailed> failures = capturedWriteEvents(state.bucket, state.objectKey).stream()
                .filter(StorageEvent.StageFailed.class::isInstance)
                .map(StorageEvent.StageFailed.class::cast)
                .toList();
        assertEquals(1, failures.size(), "the failed write must publish one typed StageFailed event");
        assertEquals(failingStage, failures.getFirst().stageName());
        assertEquals(failureReason, failures.getFirst().reason());
    }

    @Then("no stage after {string} emits a success event for this StorageContext")
    public void noStageAfterEmitsSuccessEvent(String failingStage) {
        int failedIndex = requiredWriteOrder().indexOf(failingStage);
        assertTrue(failedIndex >= 0);
        List<String> succeeded = stageNames(capturedWriteEvents(state.bucket, state.objectKey),
                StorageEventType.STAGE_SUCCEEDED);
        assertTrue(succeeded.stream().noneMatch(stage -> requiredWriteOrder().indexOf(stage) > failedIndex),
                "no later stage may succeed after " + failingStage + ": " + succeeded);
    }

    @Then("cleanup events run for every completed stage that owns temporary files, open buffers, or object publication handles")
    public void cleanupEventsRunForEveryCompletedStage() {
        List<String> succeeded = stageNames(capturedWriteEvents(state.bucket, state.objectKey),
                StorageEventType.STAGE_SUCCEEDED);
        List<String> expectedCleanup = new ArrayList<>();
        if (succeeded.contains("chunk-persistence")) expectedCleanup.add("chunk-persistence");
        if (succeeded.contains("chunking")) expectedCleanup.add("chunking");
        List<String> actualCleanup = capturedWriteEvents(state.bucket, state.objectKey).stream()
                .filter(StorageEvent.CleanupCompleted.class::isInstance)
                .map(event -> event.outcome().orElseThrow()).toList();
        assertEquals(expectedCleanup, actualCleanup);
    }

    @Then("all temporary files created for the failed write are removed or quarantined in filesystem root {string}")
    public void allTemporaryFilesCreatedForFailedWriteAreRemovedOrQuarantined(String storageRoot) {
        assertEquals(state.storageRoot, PROJECT_ROOT.resolve(storageRoot).normalize());
        assertEquals(0, countFilesMatching(state.storageRoot,
                path -> path.getFileName().toString().contains(".tmp.")),
                "failed staged writes must leave no temporary artifacts");
        assertEquals(0, countFilesMatching(state.storageRoot.resolve("nodes"), path -> true),
                "failed staged writes must remove unpublished chunk data and checksum sidecars");
        assertEquals(0, countFilesMatching(
                state.storageRoot.resolve("metadata/content-address-index"), path -> true),
                "failed staged writes must remove unpublished content-address references");
    }

    @Then("no committed manifest or object reference is published for bucket {string} and key {string}")
    public void noCommittedManifestOrObjectReferenceIsPublished(String bucket, String key) {
        assertEquals(state.bucket, bucket);
        assertEquals(state.objectKey, key);
        assertEquals(0, countFilesMatching(state.storageRoot.resolve("metadata/manifests"), path -> true));
        assertEquals(0, countFilesMatching(state.storageRoot.resolve("metadata/objects"), path -> true));
        assertEquals(0, countFilesMatching(state.storageRoot.resolve("metadata/s3-object-references"), path -> true));
    }

    @Then("the S3 PutObject response exposes a deterministic storage failure rather than a partial success")
    public void s3PutObjectResponseExposesDeterministicStorageFailure() {
        // HTTP-observable: the PutObject response must signal an error status.
        assertNotNull(lastPipelineResponse, "a pipeline PUT response should have been captured");
        assertEquals(500, lastPipelineResponse.status(), lastPipelineResponse.bodyAsString());
        assertTrue(lastPipelineResponse.bodyAsString().contains("<Code>InternalError</Code>"),
                "the S3 error code must be deterministic: " + lastPipelineResponse.bodyAsString());
        assertTrue(lastPipelineResponse.bodyAsString().contains("simulated chunk write fault"),
                "the declared fault reason must be exposed: " + lastPipelineResponse.bodyAsString());
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

    private List<StorageEvent> capturedWriteEvents(String bucket, String key) {
        return eventRecorder.events().stream()
            .filter(event -> event.operation() == StorageOperation.WRITE)
            .filter(event -> event.bucket().filter(bucket::equals).isPresent())
            .filter(event -> event.objectKey().filter(key::equals).isPresent())
            .toList();
    }

    private static StorageEvent.StageSucceeded succeeded(List<StorageEvent> events, String stage) {
        return events.stream()
            .filter(event -> event.type() == StorageEventType.STAGE_SUCCEEDED && event.stageName().equals(stage))
            .map(StorageEvent.StageSucceeded.class::cast)
            .findFirst()
            .orElseThrow(() -> new AssertionError("Missing successful stage event: " + stage));
    }

    private static List<String> stageNames(List<StorageEvent> events, StorageEventType type) {
        return events.stream().filter(event -> event.type() == type).map(StorageEvent::stageName).toList();
    }

    private static List<String> requiredWriteOrder() {
        return List.of("validation", "policy-resolution", "chunking", "dedup-lookup", "chunk-persistence",
            "manifest-persistence", "object-index-persistence");
    }

    private static Properties loadProperties(Path path) {
        Properties properties = new Properties();
        try (InputStream input = Files.newInputStream(path)) {
            properties.load(input);
            return properties;
        } catch (IOException error) {
            throw new UncheckedIOException(error);
        }
    }

    private static byte[] readBytes(Path path) {
        try {
            return Files.readAllBytes(path);
        } catch (IOException error) {
            throw new UncheckedIOException(error);
        }
    }

    private static String readString(Path path) {
        try {
            return Files.readString(path);
        } catch (IOException error) {
            throw new UncheckedIOException(error);
        }
    }

    private static String sha256Hex(byte[] bytes) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(bytes));
        } catch (NoSuchAlgorithmException error) {
            throw new IllegalStateException("SHA-256 unavailable", error);
        }
    }

    private static Path onlyFile(Path root, String suffix) {
        try (var paths = Files.list(root)) {
            List<Path> matches = paths.filter(Files::isRegularFile)
                    .filter(path -> path.getFileName().toString().endsWith(suffix)).toList();
            assertEquals(1, matches.size(), "expected exactly one " + suffix + " file in " + root);
            return matches.getFirst();
        } catch (IOException error) {
            throw new UncheckedIOException(error);
        }
    }

    private static int firstObservation(
            List<Phase3StorageEventRecorder.ReadObservation> observations, String kind) {
        for (int index = 0; index < observations.size(); index++) {
            if (observations.get(index).kind().equals(kind)) return index;
        }
        return -1;
    }

    private static void assertReadPathHasNoAggregation(String relativePath, String start, String end) {
        try {
            Path repositoryRoot = Files.exists(PROJECT_ROOT.resolve("s3-reactive-api-adapter/pom.xml"))
                    ? PROJECT_ROOT : PROJECT_ROOT.getParent();
            String source = Files.readString(repositoryRoot.resolve(relativePath));
            String readPath = source.substring(source.indexOf(start), source.indexOf(end));
            assertFalse(readPath.contains("collectList("));
            assertFalse(readPath.contains(".reduce("));
            assertFalse(readPath.contains("toByteArray("));
            assertFalse(readPath.contains("readAllBytes("));
        } catch (IOException error) {
            throw new UncheckedIOException(error);
        }
    }

    private static long countFilesMatching(Path root, java.util.function.Predicate<Path> predicate) {
        if (!Files.isDirectory(root)) {
            return 0;
        }
        try (var paths = Files.walk(root)) {
            return paths.filter(Files::isRegularFile).filter(predicate).count();
        } catch (IOException error) {
            throw new UncheckedIOException(error);
        }
    }

    private static long countRegularFiles(Path root) {
        if (!Files.isDirectory(root)) {
            return 0;
        }
        try (var paths = Files.walk(root)) {
            return paths.filter(Files::isRegularFile)
                .filter(path -> !path.getFileName().toString().contains(".tmp."))
                .filter(path -> !path.getFileName().toString().endsWith(".sha256"))
                .count();
        } catch (IOException error) {
            throw new UncheckedIOException(error);
        }
    }

    private Properties largeObjectManifest() {
        return loadProperties(onlyFile(state.storageRoot.resolve("metadata/manifests"), ".properties"));
    }

    private static MessageDigest digestFile(Path path) throws NoSuchAlgorithmException, IOException {
        MessageDigest digest = MessageDigest.getInstance("SHA-256");
        try (InputStream input = Files.newInputStream(path)) {
            byte[] bytes = new byte[LARGE_UPLOAD_BUFFER_SIZE];
            int read;
            while ((read = input.read(bytes)) >= 0) {
                digest.update(bytes, 0, read);
            }
        }
        return digest;
    }

    private static Path repositoryRoot() {
        return Files.exists(PROJECT_ROOT.resolve("pom.xml"))
                && Files.exists(PROJECT_ROOT.resolve("storage-engine-reactive-infrastructure"))
                ? PROJECT_ROOT : PROJECT_ROOT.getParent();
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

    private static final class LargeWebClientUpload {
        private final NettyDataBufferFactory factory =
                new NettyDataBufferFactory(PooledByteBufAllocator.DEFAULT);
        private final Queue<NettyDataBuffer> liveBuffers = new ConcurrentLinkedQueue<>();
        private final AtomicLong maxSingleRequest = new AtomicLong();
        private final AtomicLong maxLivePayloadBuffers = new AtomicLong();
        private final AtomicLong emittedBuffers = new AtomicLong();
        private final AtomicLong releasedBuffers = new AtomicLong();

        private Flux<DataBuffer> flux() {
            long totalBuffers = LARGE_OBJECT_SIZE / LARGE_UPLOAD_BUFFER_SIZE;
            return Flux.<DataBuffer, Long>generate(() -> 0L, (index, sink) -> {
                        if (index >= totalBuffers) {
                            sink.complete();
                            return index;
                        }
                        long liveBeforeEmission = pruneReleasedBuffers();
                        NettyDataBuffer buffer = (NettyDataBuffer) factory.wrap(
                                new byte[LARGE_UPLOAD_BUFFER_SIZE]);
                        liveBuffers.add(buffer);
                        maxLivePayloadBuffers.accumulateAndGet(liveBeforeEmission + 1, Math::max);
                        emittedBuffers.incrementAndGet();
                        sink.next(buffer);
                        return index + 1;
                    })
                    .doOnRequest(requested -> maxSingleRequest.accumulateAndGet(requested, Math::max));
        }

        private long maxSingleRequest() {
            return maxSingleRequest.get();
        }

        private long maxLivePayloadBuffers() {
            return maxLivePayloadBuffers.get();
        }

        private boolean allPayloadBuffersReleased() {
            pruneReleasedBuffers();
            return liveBuffers.isEmpty() && releasedBuffers.get() == emittedBuffers.get();
        }

        private long pruneReleasedBuffers() {
            liveBuffers.removeIf(buffer -> {
                if (buffer.getNativeBuffer().refCnt() == 0) {
                    releasedBuffers.incrementAndGet();
                    return true;
                }
                return false;
            });
            return liveBuffers.size();
        }

        private long emittedBuffers() {
            return emittedBuffers.get();
        }
    }

    private record Response(int status, byte[] body) {
        String bodyAsString() {
            return new String(body, StandardCharsets.UTF_8);
        }
    }
}
