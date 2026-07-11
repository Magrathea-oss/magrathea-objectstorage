package com.example.magrathea.storageengine.application.service;

import com.example.magrathea.storageengine.application.pipeline.CompositeStorageEventPublisher;
import com.example.magrathea.storageengine.application.pipeline.DataProcessingPipelinePort;
import com.example.magrathea.storageengine.application.pipeline.StorageContext;
import com.example.magrathea.storageengine.application.pipeline.StorageEvent;
import com.example.magrathea.storageengine.application.pipeline.StorageEventListener;
import com.example.magrathea.storageengine.application.pipeline.StorageEventPublisher;
import com.example.magrathea.storageengine.application.pipeline.StorageEventType;
import com.example.magrathea.storageengine.application.pipeline.StorageOperation;
import com.example.magrathea.storageengine.application.pipeline.ReadPipelineObserver;
import com.example.magrathea.storageengine.application.pipeline.StoragePipelineExecutor;
import com.example.magrathea.storageengine.application.pipeline.StorageStage;
import com.example.magrathea.storageengine.application.port.StoragePolicyCatalog;
import com.example.magrathea.storageengine.domain.service.CompleteUploadService;
import com.example.magrathea.storageengine.domain.service.EffectivePolicyResolver;
import com.example.magrathea.storageengine.domain.service.PersistencePlanner;
import com.example.magrathea.storageengine.domain.service.VirtualDeviceResolver;
import com.example.magrathea.storageengine.domain.valueobject.BucketId;
import com.example.magrathea.storageengine.domain.valueobject.BucketRef;
import com.example.magrathea.storageengine.domain.valueobject.CompleteUploadCommand;
import com.example.magrathea.storageengine.domain.valueobject.EncryptionRequest;
import com.example.magrathea.storageengine.domain.valueobject.DedupConfig;
import com.example.magrathea.storageengine.domain.valueobject.DedupScope;
import com.example.magrathea.storageengine.domain.valueobject.FingerprintAlgorithm;
import com.example.magrathea.storageengine.domain.valueobject.ChunkAlignment;
import com.example.magrathea.storageengine.domain.valueobject.NodeId;
import com.example.magrathea.storageengine.domain.valueobject.ObjectContentDescriptor;
import com.example.magrathea.storageengine.domain.valueobject.ObjectKey;
import com.example.magrathea.storageengine.domain.valueobject.ObjectMetadataDescriptor;
import com.example.magrathea.storageengine.domain.valueobject.StorageClassId;
import com.example.magrathea.storageengine.domain.valueobject.StoragePolicy;
import com.example.magrathea.storageengine.domain.valueobject.UploadMode;
import com.example.magrathea.storageengine.domain.valueobject.UploadRequestContext;
import com.example.magrathea.storageengine.infrastructure.filesystem.FileSystemChunkStorePort;
import com.example.magrathea.storageengine.infrastructure.filesystem.FileSystemRecoveryScanner;
import com.example.magrathea.storageengine.infrastructure.filesystem.FileSystemStorageCluster;
import com.example.magrathea.storageengine.infrastructure.filesystem.FileSystemStorageNode;
import com.example.magrathea.storageengine.infrastructure.filesystem.FileSystemWriteFaultInjector;
import com.example.magrathea.storageengine.infrastructure.filesystem.FileSystemWriteInterruptedException;
import com.example.magrathea.storageengine.infrastructure.pipeline.DataProcessingPipelineFactory;
import com.example.magrathea.storageengine.infrastructure.pipeline.FileSystemStorePort;
import com.example.magrathea.storageengine.infrastructure.pipeline.FixedWindowDedupStep;
import com.example.magrathea.storageengine.infrastructure.pipeline.NoOpCompressionStep;
import com.example.magrathea.storageengine.infrastructure.pipeline.NoOpDeduplicationStep;
import com.example.magrathea.storageengine.infrastructure.pipeline.NoOpEncryptionStep;
import com.example.magrathea.storageengine.infrastructure.pipeline.NoOpErasureCodingStep;
import io.cucumber.datatable.DataTable;
import io.cucumber.java.en.Given;
import io.cucumber.java.en.Then;
import io.cucumber.java.en.When;
import org.springframework.core.io.ClassPathResource;
import io.netty.buffer.PooledByteBufAllocator;
import org.springframework.core.io.buffer.DataBuffer;
import org.springframework.core.io.buffer.NettyDataBuffer;
import org.springframework.core.io.buffer.NettyDataBufferFactory;
import reactor.core.Disposable;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HexFormat;
import java.util.List;
import java.util.Optional;
import java.util.Queue;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;

public class Phase3PipelineUnitSteps {

    private static final StorageClassId STORAGE_CLASS = StorageClassId.of("PIPELINE_UNIT");
    private static final org.springframework.core.io.buffer.DefaultDataBufferFactory BUFFER_FACTORY =
            new org.springframework.core.io.buffer.DefaultDataBufferFactory();
    private static final int CANCELLATION_BUFFER_SIZE = 65_536;
    private static final long LARGE_OBJECT_SIZE = 256L * 1024 * 1024;
    private static final long PLAIN_OBJECT_SIZE = 8L * 1024 * 1024;

    private String validationMode;
    private String requirementId;
    private Path storageRoot;
    private String bucket;
    private String objectKey;
    private String fixtureFile;
    private byte[] fixtureBytes;
    private ReactiveStorageOrchestrator orchestrator;
    private CapturingPublisher publisher;
    private StorageEventPublisher eventPublisher;
    private StorageContext completedContext;
    private List<StorageStage> assembledStages;
    private MessageDigest streamedReadDigest;
    private long streamedReadLength;
    private boolean firstChunkArrivedBeforeAllReads;
    private String failingStage;
    private String failureReason;
    private Throwable pipelineFailure;
    private Flux<DataBuffer> failingUploadBody;
    private DemandControlledUpload cancellationUpload;
    private LargeDemandUpload largeDemandUpload;
    private int configuredChunkSizeBytes;
    private int configuredInFlightChunks;
    private Disposable cancellationSubscription;
    private int expectedPersistedChunks;
    private long demandAtCancellation;
    private String observedStage;
    private StageObserver primaryObserver;
    private StageObserver independentObserver;
    private DemandProbe baselineDemand;
    private DemandProbe instrumentedDemand;
    private byte[] instrumentedReadBytes;
    private boolean instrumentationObserversEnabled = true;

    @Given("the S3 API is configured with profile {string} and backend {string}")
    public void configuredProfileAndBackend(String profile, String backend) {
        assertThat(profile).isEqualTo("storage-engine-it");
        assertThat(backend).isEqualTo("storage-engine");
    }

    @Given("the storage engine stores bytes, manifests, and object references on a real filesystem")
    public void realFilesystemStorage() {
        // The scenario builds only production filesystem outbound adapters below.
    }

    @Given("each scenario uses a clean storage-engine filesystem root {string}")
    public void cleanScenarioRootPattern(String rootPattern) {
        assertThat(rootPattern).contains("<scenario-id>");
    }

    @Given("reactive pipeline event capture is enabled for the selected validation mode")
    public void eventCaptureEnabled() {
        // The publisher is attached when the scenario-specific filesystem root is known.
    }

    @Given("validation mode {string} is selected for requirement {string}")
    public void validationModeSelected(String mode, String id) {
        validationMode = mode;
        requirementId = id;
        assertThat(validationMode).isEqualTo("pipeline-unit");
        assertThat(requirementId).isIn(
                "REQ-PIPELINE-001", "REQ-PIPELINE-002", "REQ-PIPELINE-003", "REQ-PIPELINE-004", "REQ-PIPELINE-005",
                "REQ-PIPELINE-006", "REQ-PIPELINE-014");
    }

    @Given("the storage engine operator uses filesystem root {string}")
    public void storageRootSelected(String relativeRoot) throws IOException {
        storageRoot = repositoryRoot().resolve(relativeRoot).normalize();
        deleteRecursively(storageRoot);
        Files.createDirectories(storageRoot);
    }

    @Given("bucket {string} exists")
    public void bucketExists(String bucketName) {
        bucket = bucketName;
    }

    @Given("no object exists in bucket {string} for key {string}")
    public void noObjectExists(String bucketName, String key) {
        assertThat(bucketName).isEqualTo(bucket);
        objectKey = key;
        assertThat(countFiles(storageRoot.resolve("metadata/objects"), Files::isRegularFile)).isZero();
    }

    @Given("an instrumentation observer subscribes only to stage {string}")
    public void observerSubscribesToStage(String stage) {
        assertThat(stage).isIn("chunk-persistence", "response-streaming");
        observedStage = stage;
        primaryObserver = new StageObserver(stage);
        independentObserver = new StageObserver(
                "chunk-persistence".equals(stage) ? "response-streaming" : "chunk-persistence");
    }

    @Given("an S3 client has object content from fixture file {string} for bucket {string} and key {string}")
    public void fixturePrepared(String resource, String bucketName, String key) throws IOException {
        assertThat(bucketName).isEqualTo(bucket);
        fixtureFile = resource;
        objectKey = key;
        fixtureBytes = new ClassPathResource(resource).getContentAsByteArray();
    }

    @Given("fixture file {string} is a deterministic 256 MiB object")
    public void deterministicLargeFixture(String relativePath) throws IOException {
        fixtureFile = relativePath;
        Path fixture = repositoryRoot().resolve(relativePath).normalize();
        Files.createDirectories(fixture.getParent());
        if (!Files.exists(fixture) || Files.size(fixture) != LARGE_OBJECT_SIZE) {
            try (var channel = java.nio.channels.FileChannel.open(fixture,
                    java.nio.file.StandardOpenOption.CREATE,
                    java.nio.file.StandardOpenOption.WRITE,
                    java.nio.file.StandardOpenOption.TRUNCATE_EXISTING)) {
                channel.position(LARGE_OBJECT_SIZE - 1);
                channel.write(java.nio.ByteBuffer.wrap(new byte[] {0}));
            }
        }
        assertThat(Files.size(fixture)).isEqualTo(LARGE_OBJECT_SIZE);
    }

    @Given("storage class {string} disables multipart, deduplication, and erasure coding")
    public void plainStorageClassDisablesChunkProducers(String storageClass) {
        assertThat(storageClass).isEqualTo("PLAIN");
    }

    @Given("fixture file {string} is a deterministic 8 MiB object")
    public void deterministicPlainFixture(String relativePath) throws IOException {
        fixtureFile = relativePath;
        Path fixture = repositoryRoot().resolve(relativePath).normalize();
        Files.createDirectories(fixture.getParent());
        try (var channel = java.nio.channels.FileChannel.open(fixture,
                java.nio.file.StandardOpenOption.CREATE,
                java.nio.file.StandardOpenOption.WRITE,
                java.nio.file.StandardOpenOption.TRUNCATE_EXISTING)) {
            channel.position(PLAIN_OBJECT_SIZE - 1);
            channel.write(java.nio.ByteBuffer.wrap(new byte[] {0}));
        }
        assertThat(Files.size(fixture)).isEqualTo(PLAIN_OBJECT_SIZE);
    }

    @Given("storage class {string} selects the bounded streaming policy for this upload")
    public void boundedStreamingStorageClass(String storageClass) {
        assertThat(storageClass).isEqualTo("PIPELINE");
    }

    @Given("the write pipeline chunk size is {string} with at most {string} in-flight chunks")
    public void writePipelineChunkSize(String chunkSize, String maxInFlight) {
        assertThat(chunkSize).isEqualTo("1 MiB");
        configuredChunkSizeBytes = 1024 * 1024;
        configuredInFlightChunks = Integer.parseInt(maxInFlight);
        assertThat(configuredInFlightChunks).isEqualTo(4);
    }

    @Given("the upload body is supplied as a demand-controlled stream for bucket {string} and key {string}")
    public void demandControlledLargeUpload(String expectedBucket, String expectedKey) {
        assertThat(expectedBucket).isEqualTo(bucket);
        objectKey = expectedKey;
        largeDemandUpload = new LargeDemandUpload();
    }

    @When("the pipeline unit runner uploads the plain fixture to key {string}")
    public void uploadPlainFixture(String key) {
        objectKey = key;
        StorageClassId plain = StorageClassId.of("PLAIN");
        configureOrchestrator(StoragePolicy.minimal(plain));
        int bufferCount = Math.toIntExact(PLAIN_OBJECT_SIZE / CANCELLATION_BUFFER_SIZE);
        Flux<DataBuffer> body = Flux.range(0, bufferCount)
                .map(ignored -> BUFFER_FACTORY.wrap(new byte[CANCELLATION_BUFFER_SIZE]));
        completedContext = new StoragePipelineExecutor(eventPublisher)
                .execute(StorageContext.write(command(bucket, objectKey, PLAIN_OBJECT_SIZE, plain), body),
                        orchestrator.writePipelineStages())
                .block();
        assertThat(completedContext).isNotNull();
    }

    @Then("the chunking stage records a whole-object pass-through decision")
    public void chunkingRecordsWholeObjectPassThrough() {
        assertThat(completedContext.stageDecisions())
                .containsEntry("chunking", "whole-object-pass-through");
    }

    @Then("persistence receives one FileUnit for the complete 8 MiB stream rather than fixed-size ChunkUnit windows")
    public void persistenceReceivesOneWholeFileUnit() throws IOException {
        var artifacts = completedContext.manifest().orElseThrow().chunks();
        assertThat(artifacts).singleElement()
                .satisfies(artifact -> assertThat(artifact.originalSize()).isEqualTo(PLAIN_OBJECT_SIZE));
        assertThat(committedChunkDataFiles()).isEqualTo(1);
        String orchestratorSource = Files.readString(repositoryRoot().resolve(
                "storage-engine-reactive-application/src/main/java/com/example/magrathea/storageengine/application/service/ReactiveStorageOrchestrator.java"));
        assertThat(orchestratorSource).contains("new StorageUnit.FileUnit(");
    }

    @Then("no dedup content-address entry, EC shard, or multipart part is created")
    public void noChunkProducingArtifactsAreCreated() {
        assertThat(countFiles(storageRoot.resolve("metadata/content-address-index"), Files::isRegularFile)).isZero();
        assertThat(countFiles(storageRoot, path -> Files.isRegularFile(path)
                && (path.toString().contains("ec-shard") || path.toString().contains("multipart")))).isZero();
    }

    @Then("the committed manifest uses schema version 2 and typed artifact properties")
    public void committedManifestUsesTypedSchemaVersion() throws IOException {
        Path manifestFile = storageRoot.resolve("metadata/manifests")
                .resolve(completedContext.manifestId().orElseThrow().value() + ".properties");
        String manifest = Files.readString(manifestFile);
        assertThat(manifest).contains("manifest.schemaVersion=2");
        assertThat(manifest).contains("artifactCount=1");
        assertThat(manifest).contains("artifact.0.kind=WHOLE_OBJECT");
        assertThat(manifest).contains("artifact.0.artifactId=");
    }

    @Then("the manifest references one artifact of kind {string} and zero chunk properties")
    public void manifestReferencesTypedArtifactOnly(String expectedKind) throws IOException {
        var manifest = completedContext.manifest().orElseThrow();
        assertThat(manifest.artifactCount()).isOne();
        assertThat(manifest.artifacts()).singleElement()
                .satisfies(artifact -> assertThat(artifact.artifactKind().name()).isEqualTo(expectedKind));
        String serialized = Files.readString(storageRoot.resolve("metadata/manifests")
                .resolve(completedContext.manifestId().orElseThrow().value() + ".properties"));
        assertThat(serialized.lines()
                .filter(line -> !line.startsWith("#"))
                .filter(line -> line.startsWith("chunk.") || line.startsWith("chunkCount=")))
                .isEmpty();
    }

    @Then("the typed manifest remains readable through the production read path")
    public void typedManifestRemainsReadable() throws NoSuchAlgorithmException, IOException {
        exactPlainStreamedReadback();
    }

    @Then("exact streamed readback matches the 8 MiB fixture")
    public void exactPlainStreamedReadback() throws NoSuchAlgorithmException, IOException {
        MessageDigest actual = MessageDigest.getInstance("SHA-256");
        AtomicLong length = new AtomicLong();
        orchestrator.read(completedContext.manifestId().orElseThrow())
                .doOnNext(bytes -> {
                    actual.update(bytes);
                    length.addAndGet(bytes.length);
                })
                .then()
                .block();
        assertThat(length.get()).isEqualTo(PLAIN_OBJECT_SIZE);
        assertThat(actual.digest()).isEqualTo(digestFile(repositoryRoot().resolve(fixtureFile)));
    }

    @Given("the staged PutObject pipeline has persisted at least {string} unpublished chunks for bucket {string} and key {string}")
    public void stagedPipelineHasUnpublishedChunks(String count, String expectedBucket, String expectedKey) {
        assertThat(expectedBucket).isEqualTo(bucket);
        objectKey = expectedKey;
        expectedPersistedChunks = Integer.parseInt(count);
        configureOrchestrator(dedupPolicy(configuredChunkSizeBytes, StorageClassId.of("PIPELINE")));
        cancellationUpload = new DemandControlledUpload();
        cancellationSubscription = orchestrator.store(
                        command(bucket, objectKey, LARGE_OBJECT_SIZE, StorageClassId.of("PIPELINE")),
                        cancellationUpload.flux())
                .subscribe(ignored -> { }, error -> pipelineFailure = error);
        await(() -> committedChunkDataFiles() >= expectedPersistedChunks,
                "persisted unpublished chunks");
        assertThat(countFiles(storageRoot.resolve("metadata/manifests"), Files::isRegularFile)).isZero();
    }

    @When("the selected validation runner cancels the upload subscription before manifest-persistence starts")
    public void cancelBeforeManifestPersistence() {
        assertThat(cancellationSubscription).isNotNull();
        assertThat(cancellationSubscription.isDisposed()).isFalse();
        demandAtCancellation = cancellationUpload.requested();
        cancellationSubscription.dispose();
        await(() -> publisher.events.stream().anyMatch(
                event -> event.type() == StorageEventType.STAGE_CANCELLED), "cancellation event");
        await(() -> countFiles(storageRoot.resolve("nodes"), Files::isRegularFile) == 0,
                "cancelled chunk cleanup");
    }

    @Then("the pipeline emits a cancellation StorageEvent for the active StorageContext")
    public void cancellationEventIsEmitted() {
        assertThat(writeEvents().stream().filter(StorageEvent.StageCancelled.class::isInstance))
                .singleElement()
                .satisfies(event -> assertThat(event.stageName()).isEqualTo("chunk-persistence"));
    }

    @Then("cancellation cleanup is owned by the reactive pipeline lifecycle rather than a detached subscription")
    public void cancellationCleanupUsesReactiveLifecycle() throws IOException {
        String executor = Files.readString(repositoryRoot().resolve(
                "storage-engine-reactive-application/src/main/java/com/example/magrathea/storageengine/application/pipeline/StoragePipelineExecutor.java"));
        String orchestrator = Files.readString(repositoryRoot().resolve(
                "storage-engine-reactive-application/src/main/java/com/example/magrathea/storageengine/application/service/ReactiveStorageOrchestrator.java"));
        assertThat(executor).contains("Mono.usingWhen(").doesNotContain(".subscribe(");
        assertThat(orchestrator).contains("Mono.usingWhen(").doesNotContain(".subscribe(");
    }

    @Then("active upstream publishers stop receiving additional demand after cancellation")
    public void demandStopsAfterCancellation() {
        await(() -> cancellationUpload.cancelled(), "upstream cancellation");
        long settledDemand = cancellationUpload.requested();
        sleep(100);
        assertThat(cancellationUpload.requested()).isEqualTo(settledDemand);
        assertThat(settledDemand).isGreaterThanOrEqualTo(demandAtCancellation);
        assertThat(cancellationUpload.emitted()).isLessThan(LARGE_OBJECT_SIZE / CANCELLATION_BUFFER_SIZE);
    }

    @Then("all retained DataBuffer instances and open file handles owned by the cancelled pipeline are released")
    public void buffersAndFilesAreReleased() {
        await(cancellationUpload::allReleased, "pooled DataBuffer release");
        assertThat(cancellationUpload.allReleased()).isTrue();
        assertThat(countFiles(storageRoot, path -> Files.isRegularFile(path)
                && path.getFileName().toString().contains(".tmp."))).isZero();
    }

    @Then("cleanup events remove or quarantine unpublished chunks and temporary files in filesystem root {string}")
    public void cancellationCleanupRemovesArtifacts(String relativeRoot) {
        assertThat(storageRoot).isEqualTo(repositoryRoot().resolve(relativeRoot).normalize());
        assertThat(publisher.events.stream()
                .filter(StorageEvent.CleanupCompleted.class::isInstance)
                .map(event -> event.outcome().orElseThrow()))
                .contains("chunk-persistence", "chunking");
        assertThat(countFiles(storageRoot.resolve("nodes"), Files::isRegularFile)).isZero();
        assertThat(countFiles(storageRoot.resolve("metadata/content-address-index"), Files::isRegularFile)).isZero();
    }

    @Then("no manifest is committed for the cancelled upload")
    public void cancelledUploadHasNoManifest() {
        assertThat(countFiles(storageRoot.resolve("metadata/manifests"), Files::isRegularFile)).isZero();
    }

    @Then("no object reference is committed for bucket {string} and key {string}")
    public void cancelledUploadHasNoObjectReference(String expectedBucket, String expectedKey) {
        noManifestOrReference(expectedBucket, expectedKey);
    }

    @Given("the pipeline failure injector causes stage {string} to fail with reason {string}")
    public void injectPipelineFailure(String stage, String reason) {
        assertThat(stage).isIn("chunk-persistence", "manifest-persistence");
        failingStage = stage;
        failureReason = reason;
    }

    @Given("an upload body emits one DataBuffer and then fails with reason {string}")
    public void uploadBodyFailsAfterOneBuffer(String reason) {
        fixtureBytes = "partial upload before upstream failure".getBytes(StandardCharsets.UTF_8);
        failingStage = "chunk-persistence";
        failureReason = reason;
        failingUploadBody = Flux.concat(
                Flux.just(BUFFER_FACTORY.wrap(fixtureBytes)),
                Flux.error(new IllegalStateException(reason)));
    }

    @When("the pipeline unit runner submits the failing upload body to the staged PutObject pipeline")
    public void submitFailingUploadBody() {
        configureOrchestrator(StoragePolicy.minimal(STORAGE_CLASS));
        assembledStages = orchestrator.writePipelineStages();
        try {
            new StoragePipelineExecutor(eventPublisher)
                    .execute(StorageContext.write(command(bucket, objectKey, fixtureBytes.length * 2L),
                            failingUploadBody), assembledStages)
                    .block();
        } catch (Throwable error) {
            pipelineFailure = reactor.core.Exceptions.unwrap(error);
        }
        assertThat(pipelineFailure).isInstanceOf(IllegalStateException.class)
                .hasMessage(failureReason);
    }

    @When("the selected validation runner uploads fixture file {string} through the staged PutObject pipeline")
    public void uploadThroughStagedPipeline(String expectedFixture) {
        assertThat(expectedFixture).isEqualTo(fixtureFile);
        if ("REQ-PIPELINE-002".equals(requirementId)) {
            configureOrchestrator(dedupPolicy(configuredChunkSizeBytes, StorageClassId.of("PIPELINE")));
            assembledStages = orchestrator.writePipelineStages();
            completedContext = new StoragePipelineExecutor(eventPublisher)
                    .execute(StorageContext.write(command(bucket, objectKey, LARGE_OBJECT_SIZE, StorageClassId.of("PIPELINE")),
                            largeDemandUpload.flux()), assembledStages)
                    .block();
            assertThat(completedContext).isNotNull();
            return;
        }

        configureOrchestrator(StoragePolicy.minimal(STORAGE_CLASS));
        assembledStages = orchestrator.writePipelineStages();
        try {
            new StoragePipelineExecutor(eventPublisher)
                    .execute(StorageContext.write(command(bucket, objectKey, fixtureBytes.length),
                            Flux.just(BUFFER_FACTORY.wrap(fixtureBytes))), assembledStages)
                    .block();
        } catch (Throwable error) {
            pipelineFailure = reactor.core.Exceptions.unwrap(error);
        }
        assertThat(pipelineFailure).isInstanceOf(FileSystemWriteInterruptedException.class);
    }

    @Then("chunking emits ordered chunks no larger than the configured chunk size")
    public void chunkingEmitsBoundedOrderedChunks() {
        var chunks = completedContext.manifest().orElseThrow().chunks();
        assertThat(chunks).hasSize(Math.toIntExact(largeDemandUpload.emittedBuffers()));
        assertThat(chunks).allSatisfy(chunk ->
                assertThat(chunk.originalSize()).isLessThanOrEqualTo(configuredChunkSizeBytes));
    }

    @Then("chunk-persistence requests more chunks only as downstream capacity becomes available")
    public void persistenceDemandFollowsCapacity() {
        assertThat(largeDemandUpload.maxSingleRequest()).isBetween(1L, (long) configuredInFlightChunks);
        assertThat(largeDemandUpload.emittedBuffers()).isEqualTo(
                LARGE_OBJECT_SIZE / LargeDemandUpload.BUFFER_SIZE);
    }

    @Then("the number of payload buffers retained in memory never exceeds the configured in-flight chunk limit")
    public void retainedPayloadChunksStayBounded() {
        assertThat(largeDemandUpload.maxLivePayloadBuffers()).isLessThanOrEqualTo(configuredInFlightChunks);
        assertThat(largeDemandUpload.allPayloadBuffersReleased()).isTrue();
    }

    @Then("the measured payload memory retained by the pipeline remains bounded by the configured chunk window plus codec overhead, not by total object size")
    public void measuredPayloadMemoryIsWindowBounded() {
        long measuredLivePayloadBytes = largeDemandUpload.maxLivePayloadBuffers()
                * LargeDemandUpload.BUFFER_SIZE;
        long windowAndCopyBound = 2L * configuredChunkSizeBytes;
        assertThat(measuredLivePayloadBytes + windowAndCopyBound)
                .isLessThanOrEqualTo((long) configuredInFlightChunks * configuredChunkSizeBytes)
                .isLessThan(LARGE_OBJECT_SIZE);
    }

    @Then("the committed manifest references all chunks in write order with the correct total object length")
    public void manifestReferencesAllLargeObjectChunks() {
        var chunks = completedContext.manifest().orElseThrow().chunks();
        assertThat(chunks.stream().mapToLong(chunk -> chunk.originalSize()).sum())
                .isEqualTo(LARGE_OBJECT_SIZE);
        assertThat(chunks).extracting(chunk -> chunk.chunkId()).doesNotHaveDuplicates();
    }

    @Then("production object-content stages do not perform a global reduce, collectList, or whole-object byte-array assembly over the 256 MiB body")
    public void productionWriteStagesDoNotAggregateLargeBody() throws IOException {
        String dedup = Files.readString(repositoryRoot().resolve(
                "storage-engine-reactive-infrastructure/src/main/java/com/example/magrathea/storageengine/infrastructure/pipeline/FixedWindowDedupStep.java"));
        String store = Files.readString(repositoryRoot().resolve(
                "storage-engine-reactive-infrastructure/src/main/java/com/example/magrathea/storageengine/infrastructure/pipeline/FileSystemStorePort.java"));
        assertThat(dedup).doesNotContain("DataBufferUtils.join", "readAllBytes(", "byte[] allBytes");
        assertThat(store).doesNotContain("DataBufferUtils.join", "readAllBytes(", "byte[] allBytes");
    }

    @Then("the S3 client can read bucket {string} and key {string} and receive the exact bytes from fixture file {string}")
    public void largeObjectReadsExactFixture(String expectedBucket, String expectedKey, String expectedFixture)
            throws IOException, NoSuchAlgorithmException {
        assertThat(expectedBucket).isEqualTo(bucket);
        assertThat(expectedKey).isEqualTo(objectKey);
        assertThat(expectedFixture).isEqualTo(fixtureFile);

        MessageDigest expectedDigest = MessageDigest.getInstance("SHA-256");
        try (var input = Files.newInputStream(repositoryRoot().resolve(expectedFixture))) {
            byte[] block = new byte[64 * 1024];
            int read;
            while ((read = input.read(block)) >= 0) {
                expectedDigest.update(block, 0, read);
            }
        }
        MessageDigest actualDigest = MessageDigest.getInstance("SHA-256");
        AtomicLong actualLength = new AtomicLong();
        orchestrator.read(completedContext.manifestId().orElseThrow())
                .doOnNext(bytes -> {
                    actualDigest.update(bytes);
                    actualLength.addAndGet(bytes.length);
                })
                .then()
                .block();
        assertThat(actualLength.get()).isEqualTo(LARGE_OBJECT_SIZE);
        assertThat(actualDigest.digest()).isEqualTo(expectedDigest.digest());
    }

    @Then("the pipeline emits exactly one StorageEvent failure for stage {string} with reason {string}")
    public void exactlyOneTypedFailure(String stage, String reason) {
        assertThat(stage).isEqualTo(failingStage);
        assertThat(reason).isEqualTo(failureReason);
        assertThat(publisher.events.stream()
                .filter(event -> event instanceof StorageEvent.StageFailed failed
                        && failed.stageName().equals(stage) && failed.reason().equals(reason)))
                .hasSize(1);
    }

    @Then("no stage after {string} emits a success event for this StorageContext")
    public void noLaterStageSucceeds(String stage) {
        int failedIndex = requiredWriteOrder().indexOf(stage);
        assertThat(stageNames(StorageEventType.STAGE_SUCCEEDED))
                .noneMatch(name -> requiredWriteOrder().indexOf(name) > failedIndex);
    }

    @Then("cleanup events run for every completed stage that owns temporary files, open buffers, or object publication handles")
    public void cleanupEventsCoverOwnedResources() {
        List<String> expected = "manifest-persistence".equals(failingStage)
                ? List.of("chunk-persistence", "chunking") : List.of("chunking");
        assertThat(publisher.events.stream()
                .filter(event -> event instanceof StorageEvent.CleanupCompleted)
                .map(event -> event.outcome().orElseThrow()).toList())
                .containsExactlyElementsOf(expected);
    }

    @Then("all temporary files created for the failed write are removed or quarantined in filesystem root {string}")
    public void noTemporaryArtifacts(String relativeRoot) {
        assertThat(storageRoot).isEqualTo(repositoryRoot().resolve(relativeRoot).normalize());
        assertThat(countFiles(storageRoot, path -> Files.isRegularFile(path)
                && path.getFileName().toString().contains(".tmp."))).isZero();
        assertThat(countFiles(storageRoot.resolve("nodes"), Files::isRegularFile)).isZero();
        assertThat(countFiles(storageRoot.resolve("metadata/content-address-index"), Files::isRegularFile)).isZero();
    }

    @Then("no committed manifest or object reference is published for bucket {string} and key {string}")
    public void noManifestOrReference(String expectedBucket, String expectedKey) {
        assertThat(expectedBucket).isEqualTo(bucket);
        assertThat(expectedKey).isEqualTo(objectKey);
        assertThat(countFiles(storageRoot.resolve("metadata/manifests"), Files::isRegularFile)).isZero();
        assertThat(countFiles(storageRoot.resolve("metadata/objects"), Files::isRegularFile)).isZero();
        assertThat(countFiles(storageRoot.resolve("metadata/s3-object-references"), Files::isRegularFile)).isZero();
    }

    @Then("the S3 PutObject response exposes a deterministic storage failure rather than a partial success")
    public void deterministicFailureIsAvailableToS3Adapter() {
        assertThat(pipelineFailure).isInstanceOf(FileSystemWriteInterruptedException.class)
                .hasMessage(failureReason);
    }

    @Then("a later S3 GetObject for bucket {string} and key {string} reports that the object is absent")
    public void laterReadIsAbsent(String expectedBucket, String expectedKey) {
        noManifestOrReference(expectedBucket, expectedKey);
    }

    @When("the pipeline unit runner writes and then reads bucket {string} and key {string} through the staged pipelines")
    public void writeAndReadWithInstrumentation(String expectedBucket, String expectedKey) throws IOException {
        assertThat(expectedBucket).isEqualTo(bucket);
        assertThat(expectedKey).isEqualTo(objectKey);
        StoragePolicy policy = StoragePolicy.minimal(STORAGE_CLASS);

        instrumentationObserversEnabled = false;
        configureOrchestrator(policy);
        baselineDemand = new DemandProbe();
        new StoragePipelineExecutor(eventPublisher)
                .execute(StorageContext.write(command(bucket, objectKey, fixtureBytes.length),
                        baselineDemand.flux(fixtureBytes)), orchestrator.writePipelineStages())
                .block();

        deleteRecursively(storageRoot);
        Files.createDirectories(storageRoot);
        instrumentationObserversEnabled = true;
        configureOrchestrator(policy);
        instrumentedDemand = new DemandProbe();
        completedContext = new StoragePipelineExecutor(eventPublisher)
                .execute(StorageContext.write(command(bucket, objectKey, fixtureBytes.length),
                        instrumentedDemand.flux(fixtureBytes)), orchestrator.writePipelineStages())
                .block();
        assertThat(completedContext).isNotNull();
        instrumentedReadBytes = orchestrator.read(completedContext.manifestId().orElseThrow())
                .reduce(new byte[0], Phase3PipelineUnitSteps::concat)
                .block();
    }

    @Then("the observer receives StorageEvent records only for stage {string} and the matching StorageContext correlation identifier")
    public void observerReceivesOnlySelectedStage(String stage) {
        assertThat(stage).isEqualTo(observedStage);
        assertThat(primaryObserver.events).isNotEmpty()
                .allSatisfy(event -> assertThat(event.stageName()).isEqualTo(stage));
        StorageOperation operation = "chunk-persistence".equals(stage)
                ? StorageOperation.WRITE : StorageOperation.READ;
        List<StorageEvent> matchingPublishedEvents = publisher.events.stream()
                .filter(event -> event.operation() == operation && event.stageName().equals(stage))
                .toList();
        assertThat(primaryObserver.events).containsExactlyElementsOf(matchingPublishedEvents);
        assertThat(primaryObserver.events.stream().map(StorageEvent::correlationId).distinct())
                .containsExactly(matchingPublishedEvents.getFirst().correlationId());
    }

    @Then("each observed StorageEvent includes event type, operation, stage name, bucket, key, timing information, and outcome")
    public void observedEventsContainRequiredFields() {
        assertThat(primaryObserver.events).allSatisfy(event -> {
            assertThat(event.type()).isNotNull();
            assertThat(event.operation()).isIn(StorageOperation.WRITE, StorageOperation.READ);
            assertThat(event.stageName()).isEqualTo(observedStage);
            assertThat(event.bucket()).contains(bucket);
            assertThat(event.objectKey()).contains(objectKey);
            assertThat(event.occurredAt()).isNotNull();
            assertThat(event.outcome()).isPresent();
            if (event instanceof StorageEvent.StageSucceeded) {
                assertThat(event.duration()).hasValueSatisfying(
                        duration -> assertThat(duration).isGreaterThanOrEqualTo(java.time.Duration.ZERO));
                if ("chunk-persistence".equals(observedStage)) {
                    assertThat(event.measurements().bytesWritten()).isEqualTo(fixtureBytes.length);
                } else {
                    assertThat(event.measurements().bytesRead()).isEqualTo(fixtureBytes.length);
                }
            }
        });
    }

    @Then("observed StorageEvent records do not include object payload bytes or complete chunk contents")
    public void observedEventsArePayloadFree() {
        String fixtureText = new String(fixtureBytes, StandardCharsets.UTF_8);
        assertThat(primaryObserver.events).allSatisfy(event -> {
            assertThat(Arrays.stream(event.getClass().getRecordComponents())
                    .map(java.lang.reflect.RecordComponent::getType))
                    .noneMatch(type -> type == byte[].class
                            || DataBuffer.class.isAssignableFrom(type)
                            || java.nio.ByteBuffer.class.isAssignableFrom(type)
                            || Flux.class.isAssignableFrom(type));
            assertThat(event.toString()).doesNotContain(fixtureText);
        });
    }

    @Then("enabling the observer does not increase requested upstream payload demand beyond the demand requested by the storage stages")
    public void observerDoesNotChangePayloadDemand() {
        assertThat(baselineDemand.subscriptions()).isOne();
        assertThat(instrumentedDemand.subscriptions()).isOne();
        assertThat(instrumentedDemand.requests()).containsExactlyElementsOf(baselineDemand.requests());
    }

    @Then("other stages can be observed by adding their own observers without changing the stage implementation or the S3 API behavior")
    public void observersAreIndependentlyAttachable() {
        assertThat(independentObserver).isNotSameAs(primaryObserver);
        assertThat(independentObserver.events).isNotEmpty()
                .allSatisfy(event -> assertThat(event.stageName()).isEqualTo(independentObserver.stage));
        assertThat(primaryObserver.events).noneMatch(independentObserver.events::contains);
    }

    @Then("the S3 client reads bucket {string} and key {string} and receives the exact bytes from fixture file {string}")
    public void instrumentedReadMatchesFixture(String expectedBucket, String expectedKey, String expectedFixture) {
        assertThat(expectedBucket).isEqualTo(bucket);
        assertThat(expectedKey).isEqualTo(objectKey);
        assertThat(expectedFixture).isEqualTo(fixtureFile);
        assertThat(instrumentedReadBytes).isEqualTo(fixtureBytes);
    }

    @When("the selected validation runner submits the fixture stream to the staged PutObject pipeline")
    public void submitToStagedWritePipeline() {
        configureOrchestrator(StoragePolicy.minimal(STORAGE_CLASS));
        assembledStages = orchestrator.writePipelineStages();
        CompleteUploadCommand command = command(bucket, objectKey, fixtureBytes.length);
        completedContext = new StoragePipelineExecutor(eventPublisher)
                .execute(StorageContext.write(command, Flux.just(BUFFER_FACTORY.wrap(fixtureBytes))), assembledStages)
                .block();
        assertThat(completedContext).isNotNull();
    }

    @Then("the write pipeline is assembled from StorageStage instances named in the required write pipeline stage order")
    public void assembledFromStorageStages() {
        assertThat(assembledStages).allSatisfy(stage -> assertThat(stage).isInstanceOf(StorageStage.class));
        assertThat(assembledStages).extracting(StorageStage::name).containsExactlyElementsOf(requiredWriteOrder());
    }

    @Then("a single StorageContext carries bucket {string}, key {string}, request metadata, chunk decisions, manifest identifier, and cleanup handles across those stages")
    public void singleContextCarriesWriteState(String expectedBucket, String expectedKey) {
        assertThat(completedContext.bucketName()).contains(expectedBucket);
        assertThat(completedContext.objectKey()).contains(expectedKey);
        assertThat(completedContext.command()).isPresent();
        assertThat(completedContext.command().orElseThrow().context().metadata()).isNotNull();
        assertThat(completedContext.chunkSizeBytes()).isPresent();
        assertThat(completedContext.chunkDescriptors()).isNotEmpty();
        assertThat(completedContext.manifestId()).isPresent();
        assertThat(completedContext.cleanupHandles()).isNotEmpty();
        assertThat(writeEvents().stream().map(StorageEvent::correlationId).distinct()).containsExactly(completedContext.correlationId());
    }

    @Then("StorageEvent records show start and success for the write stages in this exact order:")
    public void eventsShowExactOrder(DataTable table) {
        List<String> expected = table.asLists().stream().skip(1).map(row -> row.getFirst()).toList();
        assertThat(stageNames(StorageEventType.STAGE_STARTED)).containsExactlyElementsOf(expected);
        assertThat(stageNames(StorageEventType.STAGE_SUCCEEDED)).containsExactlyElementsOf(expected);
    }

    @Then("no manifest is committed before chunk-persistence succeeds for every referenced chunk")
    public void manifestWaitsForChunks() {
        PublicationSnapshot chunksSucceeded = publisher.snapshot(StorageEventType.STAGE_SUCCEEDED, "chunk-persistence");
        PublicationSnapshot manifestStarted = publisher.snapshot(StorageEventType.STAGE_STARTED, "manifest-persistence");
        assertThat(chunksSucceeded.chunkFiles()).isEqualTo(completedContext.chunkDescriptors().size());
        assertThat(chunksSucceeded.manifestFiles()).isZero();
        assertThat(manifestStarted.manifestFiles()).isZero();
    }

    @Then("no object reference is committed before manifest-persistence succeeds")
    public void objectReferenceWaitsForManifest() {
        PublicationSnapshot manifestSucceeded = publisher.snapshot(StorageEventType.STAGE_SUCCEEDED, "manifest-persistence");
        PublicationSnapshot objectStarted = publisher.snapshot(StorageEventType.STAGE_STARTED, "object-index-persistence");
        assertThat(manifestSucceeded.manifestFiles()).isOne();
        assertThat(manifestSucceeded.objectFiles()).isZero();
        assertThat(objectStarted.manifestFiles()).isOne();
        assertThat(objectStarted.objectFiles()).isZero();
        assertThat(publisher.snapshot(StorageEventType.STAGE_SUCCEEDED, "object-index-persistence").objectFiles()).isOne();
    }

    @Then("no content-address entry is published when the selected policy produces no dedup chunks")
    public void noContentAddressEntryForPlainPolicy() {
        assertThat(publisher.snapshot(StorageEventType.STAGE_SUCCEEDED, "chunk-persistence")
                .contentAddressFiles()).isZero();
        assertThat(publisher.snapshot(StorageEventType.STAGE_SUCCEEDED, "manifest-persistence")
                .contentAddressFiles()).isZero();
        assertThat(publisher.snapshot(StorageEventType.STAGE_STARTED, "object-index-persistence")
                .contentAddressFiles()).isZero();
        assertThat(publisher.snapshot(StorageEventType.STAGE_SUCCEEDED, "object-index-persistence")
                .contentAddressFiles()).isZero();
    }

    @Then("every committed manifest chunk reference uses a canonical UUID filename with a matching SHA-256 sidecar readable by the canonical filesystem node")
    public void committedChunksUseCanonicalFilesystemLayout() throws IOException {
        FileSystemStorageNode node = new FileSystemStorageNode(
                storageRoot.resolve("nodes/node-001"), NodeId.of("node-001"));
        assertThat(completedContext.chunkDescriptors()).isNotEmpty().allSatisfy(chunk -> {
            Path data = storageRoot.resolve("nodes/node-001/chunks")
                    .resolve(chunk.chunkId().value().toString());
            Path sidecar = data.resolveSibling(data.getFileName() + ".sha256");
            assertThat(data).isRegularFile();
            assertThat(sidecar).isRegularFile();
            try {
                assertThat(Files.readString(sidecar).trim()).isEqualTo(sha256Hex(Files.readAllBytes(data)));
            } catch (IOException error) {
                throw new UncheckedIOException(error);
            }
            assertThat(node.read(chunk.chunkId()).block()).isEqualTo(readBytes(data));
        });
    }

    @Then("a recovery scan of filesystem root {string} reports no incomplete chunk artifacts after publication")
    public void recoveryScanReportsNoIncompleteChunks(String relativeRoot) {
        assertThat(storageRoot).isEqualTo(repositoryRoot().resolve(relativeRoot).normalize());
        assertThat(new FileSystemRecoveryScanner().scan(storageRoot).findings()).isEmpty();
    }

    @Given("a committed object exists in bucket {string} at key {string} uploaded from fixture file {string}")
    public void committedMultiChunkObject(String bucketName, String key, String fixturePath) throws IOException {
        bucket = bucketName;
        objectKey = key;
        deterministicLargeFixture(fixturePath);
        largeDemandUpload = new LargeDemandUpload();
        configureOrchestrator(dedupPolicy(configuredChunkSizeBytes, StorageClassId.of("PIPELINE")));
        StorageContext writeContext = StorageContext.write(
                command(bucket, objectKey, LARGE_OBJECT_SIZE, StorageClassId.of("PIPELINE")),
                largeDemandUpload.flux());
        completedContext = new StoragePipelineExecutor(eventPublisher)
                .execute(writeContext, orchestrator.writePipelineStages())
                .block();
        assertThat(completedContext).isNotNull();
    }

    @Then("the committed manifest lists multiple chunks with stable ordinal positions and checksums")
    public void manifestHasOrderedChecksummedChunks() {
        var chunks = completedContext.manifest().orElseThrow().chunks();
        assertThat(chunks).hasSizeGreaterThan(1);
        assertThat(chunks).allSatisfy(chunk -> {
            assertThat(chunk.finalChecksum().value()).isNotBlank();
            assertThat(chunk.stepChecksums()).isNotEmpty();
        });
    }

    @When("the selected validation runner reads bucket {string} and key {string} through the staged GetObject pipeline")
    public void readWithControlledDemand(String expectedBucket, String expectedKey)
            throws NoSuchAlgorithmException {
        assertThat(expectedBucket).isEqualTo(bucket);
        assertThat(expectedKey).isEqualTo(objectKey);
        streamedReadDigest = MessageDigest.getInstance("SHA-256");
        streamedReadLength = 0;
        int totalChunks = completedContext.manifest().orElseThrow().chunks().size();
        StepVerifier.create(orchestrator.read(completedContext.manifestId().orElseThrow()), 0)
                .expectSubscription()
                .then(() -> assertThat(publisher.readObservations).isEmpty())
                .thenRequest(1)
                .assertNext(bytes -> {
                    recordStreamedRead(bytes);
                    firstChunkArrivedBeforeAllReads = publisher.count("requested") < totalChunks;
                })
                .thenRequest(Long.MAX_VALUE)
                .thenConsumeWhile(bytes -> {
                    recordStreamedRead(bytes);
                    return true;
                })
                .verifyComplete();
    }

    @Then("StorageEvent records show start and success for the read stages in this exact order:")
    public void readEventsShowExactOrder(DataTable table) {
        List<String> expected = table.asLists().stream().skip(1).map(row -> row.getFirst()).toList();
        assertThat(stageNames(StorageOperation.READ, StorageEventType.STAGE_STARTED)).containsExactlyElementsOf(expected);
        assertThat(stageNames(StorageOperation.READ, StorageEventType.STAGE_SUCCEEDED)).containsExactlyElementsOf(expected);
    }

    @Then("chunk-reading emits chunks in the same ordinal order recorded in the committed manifest")
    public void chunksFollowManifestOrder() {
        assertThat(publisher.ordinals("emitted")).containsExactlyElementsOf(
                java.util.stream.IntStream.range(0, completedContext.chunkDescriptors().size()).boxed().toList());
    }

    @Then("response-streaming begins after the first verified chunk is available, without waiting for every chunk to be read")
    public void firstChunkStreamsBeforeAllReads() {
        assertThat(firstChunkArrivedBeforeAllReads).isTrue();
        assertThat(publisher.firstIndex("verified")).isLessThan(publisher.firstIndex("emitted") + 1);
    }

    @Then("downstream response demand controls how many chunks are read ahead")
    public void demandControlsReadAhead() {
        assertThat(publisher.maxVerifiedAhead()).isLessThanOrEqualTo(1);
        assertThat(publisher.count("demand")).isGreaterThan(0);
    }

    @Then("production read stages do not perform a global reduce, collectList, or whole-object byte-array assembly over the object content")
    public void noWholeObjectAggregation() throws IOException {
        String source = Files.readString(repositoryRoot().resolve(
                "storage-engine-reactive-application/src/main/java/com/example/magrathea/storageengine/application/service/ReactiveStorageOrchestrator.java"));
        String readPath = source.substring(source.indexOf("private Mono<StorageContext> planRead"),
                source.indexOf("private static Optional<String> manifestIdValue"));
        assertThat(readPath).doesNotContain("collectList(", ".reduce(", "toByteArray(", "readAllBytes(");
    }

    @Then("the streamed S3 response bytes exactly match fixture file {string}")
    public void streamedBytesMatch(String expectedFixture) throws IOException, NoSuchAlgorithmException {
        assertThat(expectedFixture).isEqualTo(fixtureFile);
        assertThat(streamedReadLength).isEqualTo(LARGE_OBJECT_SIZE);
        assertThat(streamedReadDigest.digest()).isEqualTo(digestFile(repositoryRoot().resolve(expectedFixture)));
    }

    @Then("after object-index-persistence succeeds, the selected validation runner reads the committed object through its declared production read entry point and receives the exact bytes from fixture file {string}")
    public void committedObjectCanBeRead(String expectedFixture) {
        assertThat(expectedFixture).isEqualTo(fixtureFile);
        byte[] read = orchestrator.read(completedContext.manifestId().orElseThrow())
                .reduce(new byte[0], Phase3PipelineUnitSteps::concat)
                .block();
        assertThat(read).isEqualTo(fixtureBytes);
    }

    private long committedChunkDataFiles() {
        try {
            return countFiles(storageRoot.resolve("nodes"), path -> Files.isRegularFile(path)
                    && !path.getFileName().toString().endsWith(".sha256")
                    && !path.getFileName().toString().contains(".tmp."));
        } catch (UncheckedIOException transientRenameRace) {
            return 0;
        }
    }

    private static void await(java.util.function.BooleanSupplier condition, String description) {
        long deadline = System.nanoTime() + java.time.Duration.ofSeconds(15).toNanos();
        while (!condition.getAsBoolean() && System.nanoTime() < deadline) {
            sleep(10);
        }
        assertThat(condition.getAsBoolean()).as(description).isTrue();
    }

    private static void sleep(long millis) {
        try {
            Thread.sleep(millis);
        } catch (InterruptedException error) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException(error);
        }
    }

    private List<StorageEvent> writeEvents() {
        return publisher.events.stream().filter(event -> event.operation() == StorageOperation.WRITE).toList();
    }

    private List<String> stageNames(StorageEventType type) {
        return stageNames(StorageOperation.WRITE, type);
    }

    private List<String> stageNames(StorageOperation operation, StorageEventType type) {
        return publisher.events.stream().filter(event -> event.operation() == operation && event.type() == type)
                .map(StorageEvent::stageName).toList();
    }

    private void configureOrchestrator(StoragePolicy policy) {
        FileSystemWriteFaultInjector injector = failureReason == null
                ? FileSystemWriteFaultInjector.disabled()
                : new ScenarioFaultInjector(failingStage, failureReason);
        FileSystemStorageCluster cluster = new FileSystemStorageCluster(storageRoot, 1, injector);
        publisher = new CapturingPublisher(storageRoot);
        List<StorageEventListener> listeners = new ArrayList<>();
        listeners.add(publisher);
        if (instrumentationObserversEnabled && primaryObserver != null) {
            listeners.add(primaryObserver);
            listeners.add(independentObserver);
        }
        eventPublisher = new CompositeStorageEventPublisher(listeners);
        Path chunks = storageRoot.resolve("nodes/node-001/chunks");
        FileSystemStorePort storePort = new FileSystemStorePort(chunks, chunks, cluster.faultInjector());
        DataProcessingPipelinePort pipelineFactory = new DataProcessingPipelineFactory(
                policy.dedup().isPresent() ? new FixedWindowDedupStep(cluster.addressIndex(), 65536)
                        : new NoOpDeduplicationStep(),
                new NoOpCompressionStep(), new NoOpEncryptionStep(),
                new NoOpErasureCodingStep(), storePort);
        orchestrator = new ReactiveStorageOrchestrator(
                new CompleteUploadService(), new SinglePolicyCatalog(policy), new EffectivePolicyResolver(),
                new VirtualDeviceResolver(), new PersistencePlanner(), cluster.addressIndex(),
                new FileSystemChunkStorePort(cluster), cluster.storedObjectRepository(), cluster.manifestRepository(),
                65536, eventPublisher, pipelineFactory, publisher);
    }

    private static StoragePolicy dedupPolicy(long chunkSize) {
        return dedupPolicy(chunkSize, STORAGE_CLASS);
    }

    private static StoragePolicy dedupPolicy(long chunkSize, StorageClassId storageClass) {
        return StoragePolicy.of(storageClass,
                Optional.of(DedupConfig.of(DedupScope.BUCKET_LEVEL, FingerprintAlgorithm.SHA256,
                        chunkSize, ChunkAlignment.NONE)),
                Optional.empty(), Optional.empty(), Optional.empty(),
                com.example.magrathea.storageengine.domain.valueobject.ReplicationConfig.of(1));
    }

    private static List<String> requiredWriteOrder() {
        return List.of("validation", "policy-resolution", "chunking", "dedup-lookup", "chunk-persistence",
                "manifest-persistence", "object-index-persistence");
    }

    private static CompleteUploadCommand command(String bucketName, String key, long size) {
        return command(bucketName, key, size, STORAGE_CLASS);
    }

    private static CompleteUploadCommand command(
            String bucketName, String key, long size, StorageClassId storageClass) {
        BucketRef bucketRef = BucketRef.of(BucketId.of(bucketName), bucketName);
        UploadRequestContext context = UploadRequestContext.of(
                ObjectKey.of(bucketName, key),
                bucketRef,
                storageClass,
                ObjectContentDescriptor.of("text/plain", size),
                ObjectMetadataDescriptor.empty(),
                EncryptionRequest.none(),
                Optional.empty());
        return new CompleteUploadCommand(context, UploadMode.SINGLE_OBJECT, Optional.empty());
    }

    private static byte[] readBytes(Path path) {
        try {
            return Files.readAllBytes(path);
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

    private void recordStreamedRead(byte[] bytes) {
        streamedReadDigest.update(bytes);
        streamedReadLength += bytes.length;
    }

    private static byte[] digestFile(Path path) throws IOException, NoSuchAlgorithmException {
        MessageDigest digest = MessageDigest.getInstance("SHA-256");
        try (var input = Files.newInputStream(path)) {
            byte[] block = new byte[64 * 1024];
            int read;
            while ((read = input.read(block)) >= 0) {
                digest.update(block, 0, read);
            }
        }
        return digest.digest();
    }

    private static byte[] concat(byte[] left, byte[] right) {
        byte[] result = new byte[left.length + right.length];
        System.arraycopy(left, 0, result, 0, left.length);
        System.arraycopy(right, 0, result, left.length, right.length);
        return result;
    }

    private static Path repositoryRoot() {
        Path current = Path.of("").toAbsolutePath().normalize();
        while (current != null) {
            if (Files.exists(current.resolve("s3-reactive-api-adapter/pom.xml"))) {
                return current;
            }
            current = current.getParent();
        }
        throw new IllegalStateException("Repository root not found");
    }

    private static void deleteRecursively(Path root) throws IOException {
        if (!Files.exists(root)) {
            return;
        }
        try (Stream<Path> paths = Files.walk(root)) {
            for (Path path : paths.sorted(java.util.Comparator.reverseOrder()).toList()) {
                Files.delete(path);
            }
        }
    }

    private static long countFiles(Path root, java.util.function.Predicate<Path> predicate) {
        if (!Files.isDirectory(root)) {
            return 0;
        }
        try (Stream<Path> paths = Files.walk(root)) {
            return paths.filter(Files::isRegularFile).filter(predicate).count();
        } catch (IOException error) {
            throw new UncheckedIOException(error);
        }
    }

    private record PublicationSnapshot(StorageEventType type, String stage, long chunkFiles,
                                       long manifestFiles, long objectFiles, long contentAddressFiles) {
    }

    private static final class CapturingPublisher implements StorageEventPublisher, StorageEventListener, ReadPipelineObserver {
        private final Path root;
        private final List<StorageEvent> events = new CopyOnWriteArrayList<>();
        private final List<PublicationSnapshot> snapshots = new CopyOnWriteArrayList<>();
        private final List<ReadObservation> readObservations = new CopyOnWriteArrayList<>();

        private CapturingPublisher(Path root) {
            this.root = root;
        }

        @Override
        public Mono<Void> publish(StorageEvent event) {
            return capture(event);
        }

        @Override
        public Mono<Void> onEvent(StorageEvent event) {
            return capture(event);
        }

        private Mono<Void> capture(StorageEvent event) {
            events.add(event);
            snapshots.add(new PublicationSnapshot(
                    event.type(),
                    event.stageName(),
                    countFiles(root.resolve("nodes"), path -> !path.getFileName().toString().contains(".tmp.")
                            && !path.getFileName().toString().endsWith(".sha256")),
                    countFiles(root.resolve("metadata/manifests"), path -> path.getFileName().toString().endsWith(".properties")
                            && !path.getFileName().toString().contains(".tmp.")),
                    countFiles(root.resolve("metadata/objects"), path -> path.getFileName().toString().endsWith(".json")),
                    countFiles(root.resolve("metadata/content-address-index"), Files::isRegularFile)));
            return Mono.empty();
        }

        @Override
        public void chunkReadRequested(String correlationId, int ordinal,
                                       com.example.magrathea.storageengine.domain.valueobject.ChunkId chunkId) {
            readObservations.add(new ReadObservation("requested", ordinal));
        }

        @Override
        public void chunkVerified(String correlationId, int ordinal,
                                  com.example.magrathea.storageengine.domain.valueobject.ChunkId chunkId) {
            readObservations.add(new ReadObservation("verified", ordinal));
        }

        @Override
        public void responseChunkEmitted(String correlationId, int ordinal,
                                         com.example.magrathea.storageengine.domain.valueobject.ChunkId chunkId) {
            readObservations.add(new ReadObservation("emitted", ordinal));
        }

        @Override
        public void downstreamRequested(String correlationId, long requested) {
            readObservations.add(new ReadObservation("demand", -1));
        }

        private long count(String kind) {
            return readObservations.stream().filter(value -> value.kind().equals(kind)).count();
        }

        private List<Integer> ordinals(String kind) {
            return readObservations.stream().filter(value -> value.kind().equals(kind))
                    .map(ReadObservation::ordinal).toList();
        }

        private int firstIndex(String kind) {
            for (int i = 0; i < readObservations.size(); i++) {
                if (readObservations.get(i).kind().equals(kind)) return i;
            }
            return -1;
        }

        private int maxVerifiedAhead() {
            int verified = 0;
            int emitted = 0;
            int max = 0;
            for (ReadObservation observation : readObservations) {
                if (observation.kind().equals("verified")) verified++;
                if (observation.kind().equals("emitted")) emitted++;
                max = Math.max(max, verified - emitted);
            }
            return max;
        }

        private PublicationSnapshot snapshot(StorageEventType type, String stage) {
            return snapshots.stream()
                    .filter(snapshot -> snapshot.type() == type && snapshot.stage().equals(stage))
                    .findFirst()
                    .orElseThrow(() -> new AssertionError("Missing snapshot for " + type + " " + stage));
        }
    }

    private static final class StageObserver implements StorageEventListener {
        private final String stage;
        private final List<StorageEvent> events = new CopyOnWriteArrayList<>();

        private StageObserver(String stage) {
            this.stage = stage;
        }

        @Override
        public Mono<Void> onEvent(StorageEvent event) {
            if (stage.equals(event.stageName())) {
                events.add(event);
            }
            return Mono.empty();
        }
    }

    private static final class DemandProbe {
        private final AtomicInteger subscriptions = new AtomicInteger();
        private final List<Long> requests = new CopyOnWriteArrayList<>();

        private Flux<DataBuffer> flux(byte[] bytes) {
            return Flux.defer(() -> {
                subscriptions.incrementAndGet();
                return Flux.<DataBuffer>just(BUFFER_FACTORY.wrap(Arrays.copyOf(bytes, bytes.length)))
                        .doOnRequest(requests::add);
            });
        }

        private int subscriptions() {
            return subscriptions.get();
        }

        private List<Long> requests() {
            return List.copyOf(requests);
        }
    }

    private static final class LargeDemandUpload {
        private static final int BUFFER_SIZE = 65_536;
        private final NettyDataBufferFactory factory =
                new NettyDataBufferFactory(PooledByteBufAllocator.DEFAULT);
        private final Queue<NettyDataBuffer> liveBuffers = new ConcurrentLinkedQueue<>();
        private final AtomicLong maxSingleRequest = new AtomicLong();
        private final AtomicLong maxLivePayloadBuffers = new AtomicLong();
        private final AtomicLong emittedBuffers = new AtomicLong();
        private final AtomicLong releasedBuffers = new AtomicLong();

        private Flux<DataBuffer> flux() {
            long totalBuffers = LARGE_OBJECT_SIZE / BUFFER_SIZE;
            return Flux.<DataBuffer, Long>generate(() -> 0L, (index, sink) -> {
                        if (index >= totalBuffers) {
                            sink.complete();
                            return index;
                        }
                        long liveBeforeEmission = pruneReleasedBuffers();
                        NettyDataBuffer buffer = (NettyDataBuffer) factory.wrap(new byte[BUFFER_SIZE]);
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

    private static final class DemandControlledUpload {
        private final NettyDataBufferFactory factory =
                new NettyDataBufferFactory(PooledByteBufAllocator.DEFAULT);
        private final List<NettyDataBuffer> allocated = new CopyOnWriteArrayList<>();
        private final AtomicLong requested = new AtomicLong();
        private final AtomicLong emitted = new AtomicLong();
        private volatile boolean cancelled;

        Flux<DataBuffer> flux() {
            long buffers = LARGE_OBJECT_SIZE / CANCELLATION_BUFFER_SIZE;
            return Flux.<DataBuffer, Long>generate(() -> 0L, (index, sink) -> {
                        if (index >= buffers) {
                            sink.complete();
                            return index;
                        }
                        byte[] bytes = new byte[CANCELLATION_BUFFER_SIZE];
                        java.util.Arrays.fill(bytes, (byte) (index & 0xff));
                        NettyDataBuffer buffer = (NettyDataBuffer) factory.wrap(bytes);
                        allocated.add(buffer);
                        emitted.incrementAndGet();
                        sink.next(buffer);
                        return index + 1;
                    })
                    .doOnRequest(requested::addAndGet)
                    .doOnCancel(() -> cancelled = true);
        }

        long requested() { return requested.get(); }
        long emitted() { return emitted.get(); }
        boolean cancelled() { return cancelled; }
        boolean allReleased() {
            return allocated.stream().allMatch(buffer -> buffer.getNativeBuffer().refCnt() == 0);
        }
    }

    private record ReadObservation(String kind, int ordinal) { }

    private record ScenarioFaultInjector(String stage, String reason) implements FileSystemWriteFaultInjector {
        @Override
        public void afterChunkTempFileWritten(ChunkWriteContext context) {
            if (stage.equals("chunk-persistence")) {
                throw FileSystemWriteInterruptedException.cleaningTemporaryArtifacts(reason);
            }
        }

        @Override
        public void afterManifestTempFileWritten(ManifestWriteContext context) {
            if (stage.equals("manifest-persistence")) {
                throw FileSystemWriteInterruptedException.cleaningTemporaryArtifacts(reason);
            }
        }
    }

    private record SinglePolicyCatalog(StoragePolicy policy) implements StoragePolicyCatalog {
        @Override
        public Mono<StoragePolicy> findById(String policyId) {
            return Mono.just(policy);
        }

        @Override
        public Mono<StoragePolicy> findBy(StorageClassId id) {
            return Mono.just(policy);
        }

        @Override
        public Flux<StoragePolicy> findAll() {
            return Flux.just(policy);
        }
    }
}
