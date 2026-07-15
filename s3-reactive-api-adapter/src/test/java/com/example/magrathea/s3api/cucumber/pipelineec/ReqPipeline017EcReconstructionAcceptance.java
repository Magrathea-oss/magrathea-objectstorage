package com.example.magrathea.s3api.cucumber.pipelineec;

import com.example.magrathea.storageengine.application.exception.EcReconstructionException;
import com.example.magrathea.storageengine.application.pipeline.BoundedEcReconstructionPort;
import com.example.magrathea.storageengine.application.pipeline.BoundedEcReconstructionPort.ReconstructionRequest;
import com.example.magrathea.storageengine.application.pipeline.BoundedEcReconstructionPort.ReconstructionResult;
import com.example.magrathea.storageengine.application.pipeline.BoundedEcReconstructionPort.RegeneratedShard;
import com.example.magrathea.storageengine.application.pipeline.BoundedEcReconstructionPort.ShardSurvivor;
import com.example.magrathea.storageengine.application.pipeline.BoundedEcReconstructionPort.WorkspaceUsage;
import com.example.magrathea.storageengine.application.pipeline.DataProcessingPipelinePort;
import com.example.magrathea.storageengine.application.pipeline.StorageEvent;
import com.example.magrathea.storageengine.application.pipeline.StorageTrace;
import com.example.magrathea.storageengine.application.pipeline.StorePort;
import com.example.magrathea.storageengine.application.pipeline.StorageEventPublisher;
import com.example.magrathea.storageengine.application.port.StoragePolicyCatalog;
import com.example.magrathea.storageengine.application.service.ReactiveStorageOrchestrator;
import com.example.magrathea.storageengine.domain.aggregate.StoredObject;
import com.example.magrathea.storageengine.domain.service.CompleteUploadService;
import com.example.magrathea.storageengine.domain.service.EffectivePolicyResolver;
import com.example.magrathea.storageengine.domain.service.PersistencePlanner;
import com.example.magrathea.storageengine.domain.service.VirtualDeviceResolver;
import com.example.magrathea.storageengine.domain.valueobject.BucketId;
import com.example.magrathea.storageengine.domain.valueobject.BucketRef;
import com.example.magrathea.storageengine.domain.valueobject.CompleteUploadCommand;
import com.example.magrathea.storageengine.domain.valueobject.EcShardLayout;
import com.example.magrathea.storageengine.domain.valueobject.EncryptionRequest;
import com.example.magrathea.storageengine.domain.valueobject.ErasureCodingConfig;
import com.example.magrathea.storageengine.domain.valueobject.ManifestId;
import com.example.magrathea.storageengine.domain.valueobject.NodeId;
import com.example.magrathea.storageengine.domain.valueobject.ObjectContentDescriptor;
import com.example.magrathea.storageengine.domain.valueobject.ObjectKey;
import com.example.magrathea.storageengine.domain.valueobject.ObjectManifest;
import com.example.magrathea.storageengine.domain.valueobject.ObjectMetadataDescriptor;
import com.example.magrathea.storageengine.domain.valueobject.ReplicationConfig;
import com.example.magrathea.storageengine.domain.valueobject.StorageArtifactKind;
import com.example.magrathea.storageengine.domain.valueobject.StorageArtifactReferenceDescriptor;
import com.example.magrathea.storageengine.domain.valueobject.StorageClassId;
import com.example.magrathea.storageengine.domain.valueobject.StoragePolicy;
import com.example.magrathea.storageengine.domain.valueobject.UploadMode;
import com.example.magrathea.storageengine.domain.valueobject.UploadRequestContext;
import com.example.magrathea.storageengine.infrastructure.filesystem.FileSystemChunkStorePort;
import com.example.magrathea.storageengine.infrastructure.filesystem.FileSystemManifestRepository;
import com.example.magrathea.storageengine.infrastructure.filesystem.FileSystemStorageCluster;
import com.example.magrathea.storageengine.infrastructure.pipeline.DataProcessingPipelineFactory;
import com.example.magrathea.storageengine.infrastructure.pipeline.FileSystemStorePort;
import com.example.magrathea.storageengine.infrastructure.pipeline.NoOpCompressionStep;
import com.example.magrathea.storageengine.infrastructure.pipeline.NoOpDeduplicationStep;
import com.example.magrathea.storageengine.infrastructure.pipeline.NoOpEncryptionStep;
import com.example.magrathea.storageengine.infrastructure.pipeline.NoOpErasureCodingStep;
import org.springframework.core.io.buffer.DataBuffer;
import org.springframework.core.io.buffer.DataBufferUtils;
import org.springframework.core.io.buffer.DefaultDataBufferFactory;
import reactor.core.Exceptions;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import reactor.core.scheduler.Scheduler;
import reactor.core.scheduler.Schedulers;

import java.io.IOException;
import java.io.StringReader;
import java.io.StringWriter;
import java.io.UncheckedIOException;
import java.nio.ByteBuffer;
import java.nio.channels.FileChannel;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.nio.file.StandardOpenOption;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HexFormat;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Properties;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Function;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;

/** Scenario state and production orchestration behind the thin REQ-PIPELINE-017 glue. */
final class ReqPipeline017EcReconstructionAcceptance {

    private static final int DATA_BLOCKS = 4;
    private static final int PARITY_BLOCKS = 2;
    private static final int TOTAL_BLOCKS = DATA_BLOCKS + PARITY_BLOCKS;
    private static final int SHARD_SIZE = 1024 * 1024;
    private static final int STRIPE_SIZE = DATA_BLOCKS * SHARD_SIZE;
    private static final long FULL_OBJECT_SIZE = 8L * 1024 * 1024;
    private static final long SHORT_OBJECT_SIZE = 6_291_593L;
    private static final long SHORT_FINAL_STRIPE_SIZE = 2_097_289L;
    private static final int IO_BUFFER_SIZE = 64 * 1024;
    private static final String REQUIREMENT = "REQ-PIPELINE-017";
    private static final String STORAGE_ROOT =
            "target/storage-engine-it/REQ-PIPELINE-017-unit";
    private static final String FIXTURE =
            "target/test-fixtures/pipeline/ec-object-8m.bin";
    private static final String LOCAL_NODE_ID = "node-001";
    private static final String RECONSTRUCTION_THREAD_PREFIX =
            "req-pipeline-017-reconstruction";
    private static final StorageClassId EC_STORAGE_CLASS = StorageClassId.of("EC_4_2");
    private static final DefaultDataBufferFactory BUFFER_FACTORY =
            new DefaultDataBufferFactory();

    private final BoundedEcReconstructionPort decoder;
    private final Path repositoryRoot = findRepositoryRoot();
    private final List<StorageEvent> events = new CopyOnWriteArrayList<>();

    private Path storageRoot;
    private Path fixturePath;
    private FileSystemStorageCluster cluster;
    private FileSystemManifestRepository manifestRepository;
    private FileSystemChunkStorePort chunkStore;
    private ReactiveStorageOrchestrator orchestrator;
    private ObjectManifest committedManifest;
    private Path committedManifestFile;
    private final Map<Integer, ManifestProbe> compatibilityProbes = new HashMap<>();
    private ScenarioMode mode = ScenarioMode.NONE;
    private List<SurvivorCase> survivorCases = List.of();
    private List<CaseResult> caseResults = List.of();
    private List<CombinationResult> exhaustiveCombinationResults = List.of();
    private ReconstructionResult schemaThreeResult;
    private Throwable schemaTwoFailure;
    private ReconstructionResult shortStripeResult;
    private ReconstructionRequest shortStripeRequest;
    private List<Throwable> invalidFailures = List.of();
    private ReconstructionResult planningResult;
    private String reconstructionCallerThread;
    private String reconstructionExecutionThread;
    private Map<String, String> beforeSnapshot = Map.of();
    private Map<String, String> afterSnapshot = Map.of();

    ReqPipeline017EcReconstructionAcceptance(BoundedEcReconstructionPort decoder) {
        this.decoder = java.util.Objects.requireNonNull(decoder, "decoder must not be null");
    }

    void verifyProfileAndBackend(String profile, String backend) {
        assertThat(profile).isEqualTo("storage-engine-it");
        assertThat(backend).isEqualTo("storage-engine");
    }

    void verifyRealFilesystem() {
        assertThat(repositoryRoot).isDirectory();
        assertThat(repositoryRoot.getFileSystem().provider().getScheme()).isEqualTo("file");
    }

    void verifyRootPattern(String rootPattern) {
        assertThat(rootPattern).isEqualTo("target/storage-engine-it/<scenario-id>");
    }

    void enablePipelineEventCapture() {
        events.clear();
    }

    void selectValidationMode(String validationMode, String requirement) {
        assertThat(validationMode).isEqualTo("pipeline-unit");
        assertThat(requirement).isEqualTo(REQUIREMENT);
    }

    void selectStorageRoot(String relativeRoot) {
        assertThat(relativeRoot).isEqualTo(STORAGE_ROOT);
        storageRoot = repositoryRoot.resolve(relativeRoot).normalize();
        deleteRecursively(storageRoot);
        try {
            Files.createDirectories(storageRoot);
        } catch (IOException error) {
            throw new UncheckedIOException(error);
        }
    }

    void selectFixture(String fixture) {
        assertThat(fixture).isEqualTo(FIXTURE);
        fixturePath = repositoryRoot.resolve(fixture).normalize();
        ensureDeterministicFixture(fixturePath, FULL_OBJECT_SIZE);
    }

    void selectEcPolicy(String storageClass) {
        assertThat(storageClass).isEqualTo(EC_STORAGE_CLASS.value());
    }

    void declareCompatibilityVersions(List<Map<String, String>> rows) {
        assertThat(rows).extracting(row -> row.get("schema"))
                .containsExactly("0", "1", "2", "3");
        mode = ScenarioMode.SCHEMA_COMPATIBILITY;
        prepareEcWrite(FULL_OBJECT_SIZE);
        prepareCompatibilityProbes();
    }

    void declareSurvivorCases(List<Map<String, String>> rows) {
        mode = ScenarioMode.SURVIVOR_CASES;
        prepareEcWrite(FULL_OBJECT_SIZE);
        survivorCases = rows.stream().map(row -> {
            Set<Integer> unavailable = new LinkedHashSet<>();
            unavailable.addAll(parseIndices(row.get("missing shard indices")));
            unavailable.addAll(parseIndices(row.get("corrupt shard indices")));
            Set<Integer> expected = parseIndices(row.get("expected regenerated indices"));
            assertThat(unavailable).isEqualTo(expected);
            List<Integer> survivors = new ArrayList<>(
                    parseIndices(row.get("checksum-valid survivor indices")));
            assertThat(survivors).hasSize(DATA_BLOCKS);
            return new SurvivorCase(row.get("case"), survivors, unavailable, expected);
        }).toList();
        assertThat(survivorCases).hasSize(4);
    }

    void declareShortLogicalView() {
        assertThat(SHORT_OBJECT_SIZE).isEqualTo((long) STRIPE_SIZE + SHORT_FINAL_STRIPE_SIZE);
        mode = ScenarioMode.SHORT_FINAL_STRIPE;
        prepareEcWrite(SHORT_OBJECT_SIZE);
    }

    void verifyShortStripeMetadataPrepared() {
        List<StorageArtifactReferenceDescriptor> stripe = stripeArtifacts(committedManifest, 1);
        assertThat(stripe).hasSize(TOTAL_BLOCKS);
        assertThat(stripe).allSatisfy(artifact -> {
            EcShardLayout layout = artifact.ecShardLayout().orElseThrow();
            assertThat(layout.stripeLogicalLength()).isEqualTo(SHORT_FINAL_STRIPE_SIZE);
            assertThat(artifact.storedSize()).isEqualTo(SHARD_SIZE);
            assertThat(artifact.finalChecksum().value()).matches("[0-9a-f]{64}");
            assertThat(artifact.locations()).isNotEmpty();
        });
    }

    void selectShortStripeSurvivors() {
        assertThat(mode).isEqualTo(ScenarioMode.SHORT_FINAL_STRIPE);
    }

    void declareInvalidRequests(List<Map<String, String>> rows) {
        mode = ScenarioMode.INVALID_REQUESTS;
        prepareEcWrite(FULL_OBJECT_SIZE);
        assertThat(rows).extracting(row -> row.get("invalid request")).containsExactlyInAnyOrder(
                "fewer than k valid survivors",
                "duplicate shard index",
                "out-of-range shard index",
                "inconsistent k and m",
                "unsupported EC geometry",
                "inconsistent stripe metadata",
                "wrong shard size",
                "wrong shard checksum",
                "unsupported future schema");
    }

    void selectPlanningOnlyInput() {
        mode = ScenarioMode.PLANNING_ONLY;
        prepareEcWrite(FULL_OBJECT_SIZE);
    }

    void submitPreparedReconstruction() {
        assertThat(mode).isNotEqualTo(ScenarioMode.NONE);
        beforeSnapshot = filesystemSnapshot(storageRoot);
        switch (mode) {
            case SCHEMA_COMPATIBILITY -> executeSchemaCompatibility();
            case SURVIVOR_CASES -> executeSurvivorCases();
            case SHORT_FINAL_STRIPE -> executeShortFinalStripe();
            case INVALID_REQUESTS -> executeInvalidRequests();
            case PLANNING_ONLY -> executePlanningOnly();
            case NONE -> throw new IllegalStateException("No REQ-PIPELINE-017 scenario input prepared");
        }
        afterSnapshot = filesystemSnapshot(storageRoot);
    }

    void assertSchemaThreeFacts() {
        assertThat(schemaThreeResult).isNotNull();
        assertThat(readProperties(committedManifestFile).getProperty("manifest.schemaVersion"))
                .isEqualTo("3");
        assertThat(committedManifest.artifacts()).hasSize(12);

        Map<Long, List<Integer>> shardIndicesByStripe = committedManifest.artifacts().stream()
                .collect(Collectors.groupingBy(
                        artifact -> artifact.ecShardLayout().orElseThrow().stripeIndex(),
                        Collectors.mapping(
                                artifact -> artifact.ecShardLayout().orElseThrow().shardIndex(),
                                Collectors.toList())));
        assertThat(shardIndicesByStripe).containsOnlyKeys(0L, 1L);
        shardIndicesByStripe.values().forEach(indices -> assertThat(indices)
                .containsExactlyInAnyOrder(0, 1, 2, 3, 4, 5));

        for (StorageArtifactReferenceDescriptor artifact : committedManifest.artifacts()) {
            EcShardLayout layout = artifact.ecShardLayout().orElseThrow();
            boolean parity = layout.shardIndex() >= DATA_BLOCKS;
            assertThat(layout.stripeIndex()).isBetween(0L, 1L);
            assertThat(layout.shardIndex()).isBetween(0, TOTAL_BLOCKS - 1);
            assertThat(layout.dataBlocks()).isEqualTo(DATA_BLOCKS);
            assertThat(layout.parityBlocks()).isEqualTo(PARITY_BLOCKS);
            assertThat(layout.parity()).isEqualTo(parity);
            assertThat(layout.stripeLogicalLength()).isEqualTo(STRIPE_SIZE);
            assertThat(artifact.artifactKind()).isEqualTo(parity
                    ? StorageArtifactKind.EC_PARITY_SHARD
                    : StorageArtifactKind.EC_DATA_SHARD);
            assertThat(artifact.originalSize()).isEqualTo(parity ? 0L : SHARD_SIZE);
            assertThat(artifact.storedSize()).isEqualTo(SHARD_SIZE);
            assertThat(artifact.finalChecksum().value()).matches("[0-9a-f]{64}");
            byte[] persisted = chunkStore.read(artifact).block();
            assertThat(persisted).isNotNull().hasSize(SHARD_SIZE);
            assertThat(sha256Hex(persisted)).isEqualTo(artifact.finalChecksum().value());
            assertThat(artifact.locations()).containsExactly(NodeId.of(LOCAL_NODE_ID));
        }
    }

    void assertChunkFilesInspectedSeparately(String relativeChunksRoot) {
        Path expectedChunksRoot = repositoryRoot.resolve(relativeChunksRoot).normalize();
        assertThat(expectedChunksRoot)
                .isEqualTo(storageRoot.resolve("nodes").resolve(LOCAL_NODE_ID).resolve("chunks"));
        assertThat(expectedChunksRoot).isDirectory();
        committedManifest.artifacts().forEach(artifact -> {
            Path dataFile = expectedChunksRoot.resolve(artifact.chunkId().value().toString());
            assertThat(dataFile).isRegularFile();
            assertThat(dataFile.normalize()).startsWith(storageRoot.normalize());
            assertThat(sha256Hex(dataFile)).isEqualTo(artifact.finalChecksum().value());
        });
    }

    void assertInvalidStorageTraceLayoutsRejected() {
        Throwable missing = executeInvalidStorageTraceProbe(
                "missing", ignored -> Optional.empty());
        assertThat(missing)
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("missing explicit layout")
                .hasMessageContaining("emission order is not inferred");

        Throwable contradictory = executeInvalidStorageTraceProbe(
                "contradictory-km",
                layout -> Optional.of(new EcShardLayout(
                        layout.stripeIndex(),
                        layout.shardIndex(),
                        layout.dataBlocks(),
                        layout.parityBlocks() + 1,
                        layout.parity(),
                        layout.stripeLogicalLength())));
        assertThat(contradictory)
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("contradicts the effective k+m policy");
    }

    void assertCompatibilityPathsReadable() {
        assertThat(compatibilityProbes.keySet()).containsExactlyInAnyOrder(0, 1, 2, 3);
        compatibilityProbes.forEach((schema, probe) -> {
            ObjectManifest reloaded = manifestRepository.findBy(probe.manifestId()).block();
            assertThat(reloaded).isNotNull();
            assertThat(reloaded.artifactCount()).isPositive();
            assertReadMatchesFixture(probe.manifestId(), FULL_OBJECT_SIZE);
        });
        assertThat(compatibilityProbes.get(0).manifest().artifacts())
                .allMatch(artifact -> artifact.artifactKind() == StorageArtifactKind.LEGACY_CHUNK);
        assertThat(compatibilityProbes.get(1).manifest().artifacts())
                .allMatch(artifact -> artifact.artifactKind() == StorageArtifactKind.LEGACY_CHUNK);
        assertThat(compatibilityProbes.get(2).manifest().artifacts())
                .allMatch(artifact -> artifact.ecShardLayout().isEmpty());
        assertThat(compatibilityProbes.get(3).manifest().artifacts())
                .allMatch(artifact -> artifact.ecShardLayout().isPresent());
    }

    void assertAmbiguousSchemaTwoRejected() {
        assertThat(schemaTwoFailure).isInstanceOf(EcReconstructionException.class);
        assertThat(((EcReconstructionException) schemaTwoFailure).reason())
                .isEqualTo(EcReconstructionException.Reason.AMBIGUOUS_LAYOUT);
        assertThat(schemaTwoFailure.getMessage()).contains("artifact order is not inferred");
    }

    void assertExpectedShardIndicesReconstructed() {
        assertThat(caseResults).hasSize(4);
        caseResults.forEach(caseResult -> assertThat(caseResult.result().regeneratedShards())
                .extracting(RegeneratedShard::shardIndex)
                .containsExactlyElementsOf(caseResult.scenario().expected().stream().sorted().toList()));
    }

    void assertEveryFourOfSixCombinationReconstructs() {
        assertThat(exhaustiveCombinationResults).hasSize(15);
        List<StorageArtifactReferenceDescriptor> stripe = stripeArtifacts(committedManifest, 0);
        boolean observedTwoDataOutputs = false;
        boolean observedDataAndParityOutputs = false;
        boolean observedTwoParityOutputs = false;
        byte[] expectedLogicalStripe = readFixtureRange(0, STRIPE_SIZE);

        for (CombinationResult combination : exhaustiveCombinationResults) {
            List<Integer> expectedIndices = combination.unavailable().stream().sorted().toList();
            assertThat(combination.result().regeneratedShards())
                    .extracting(RegeneratedShard::shardIndex)
                    .containsExactlyElementsOf(expectedIndices);
            for (RegeneratedShard regenerated : combination.result().regeneratedShards()) {
                byte[] expectedShard = chunkStore.read(stripe.get(regenerated.shardIndex())).block();
                assertThat(expectedShard).isNotNull();
                assertThat(regenerated.bytes()).isEqualTo(expectedShard);
            }
            assertThat(combination.result().verifiedStripe().logicalBytes())
                    .isEqualTo(expectedLogicalStripe);

            long dataOutputs = combination.unavailable().stream()
                    .filter(index -> index < DATA_BLOCKS)
                    .count();
            observedTwoDataOutputs |= dataOutputs == 2;
            observedDataAndParityOutputs |= dataOutputs == 1;
            observedTwoParityOutputs |= dataOutputs == 0;
        }
        assertThat(observedTwoDataOutputs).isTrue();
        assertThat(observedDataAndParityOutputs).isTrue();
        assertThat(observedTwoParityOutputs).isTrue();
    }

    void assertRegeneratedShardsVerified() {
        List<StorageArtifactReferenceDescriptor> stripe = stripeArtifacts(committedManifest, 0);
        caseResults.forEach(caseResult -> caseResult.result().regeneratedShards().forEach(shard -> {
            StorageArtifactReferenceDescriptor committed = stripe.get(shard.shardIndex());
            assertThat(shard.bytes()).hasSize(Math.toIntExact(committed.storedSize()));
            assertThat(shard.checksum()).isEqualTo(committed.finalChecksum());
            assertThat(sha256Hex(shard.bytes())).isEqualTo(committed.finalChecksum().value());
        }));
    }

    void assertReconstructedStripeMatchesFixture(String fixture) {
        assertThat(fixture).isEqualTo(FIXTURE);
        byte[] expected = readFixtureRange(0, STRIPE_SIZE);
        caseResults.forEach(caseResult -> assertThat(
                caseResult.result().verifiedStripe().logicalBytes()).isEqualTo(expected));
    }

    void assertShortLogicalOutput() {
        assertThat(shortStripeResult).isNotNull();
        assertThat(shortStripeResult.verifiedStripe().stripeLogicalLength())
                .isEqualTo(SHORT_FINAL_STRIPE_SIZE);
        assertThat(shortStripeResult.verifiedStripe().logicalBytes())
                .hasSize(Math.toIntExact(SHORT_FINAL_STRIPE_SIZE))
                .isEqualTo(readFixtureRange(STRIPE_SIZE, Math.toIntExact(SHORT_FINAL_STRIPE_SIZE)));
        assertThat((long) STRIPE_SIZE + shortStripeResult.verifiedStripe().logicalBytes().length)
                .isEqualTo(SHORT_OBJECT_SIZE);
    }

    void assertWorkspaceBounds() {
        WorkspaceUsage usage = shortStripeResult.verifiedStripe().workspaceUsage();
        assertThat(usage.decoderOwnedLogicalAssemblyBytes()).isLessThanOrEqualTo(STRIPE_SIZE);
        assertThat(usage.decoderOwnedShardBufferCount()).isBetween(DATA_BLOCKS, TOTAL_BLOCKS);
        assertThat(usage.decoderOwnedShardBufferSizeBytes()).isEqualTo(SHARD_SIZE);
        assertThat(usage.matrixRows()).isEqualTo(DATA_BLOCKS);
        assertThat(usage.matrixColumns()).isEqualTo(DATA_BLOCKS);
        assertThat(usage.requestBoundarySnapshotBytes())
                .isEqualTo((long) DATA_BLOCKS * SHARD_SIZE);
        assertThat(usage.resultBoundarySnapshotBytes())
                .isEqualTo(SHORT_FINAL_STRIPE_SIZE + SHARD_SIZE);
        assertThat(usage.earlierStripeBytesRetained()).isZero();
        assertThat(usage.wholeObjectBytesRetained()).isZero();
    }

    void assertDefensiveAccessorCopiesRemainBoundaryOwned() {
        WorkspaceUsage usage = shortStripeResult.verifiedStripe().workspaceUsage();
        byte[] logicalCopy = shortStripeResult.verifiedStripe().logicalBytes();
        byte expectedLogicalByte = logicalCopy[0];
        logicalCopy[0] ^= 0x5a;
        assertThat(shortStripeResult.verifiedStripe().logicalBytes()[0])
                .isEqualTo(expectedLogicalByte);

        RegeneratedShard regenerated = shortStripeResult.regeneratedShards().get(0);
        byte[] regeneratedCopy = regenerated.bytes();
        byte expectedShardByte = regeneratedCopy[0];
        regeneratedCopy[0] ^= 0x5a;
        assertThat(regenerated.bytes()[0]).isEqualTo(expectedShardByte);

        ShardSurvivor survivor = shortStripeRequest.survivors().get(0);
        byte[] survivorCopy = survivor.bytes();
        byte expectedSurvivorByte = survivorCopy[0];
        survivorCopy[0] ^= 0x5a;
        assertThat(survivor.bytes()[0]).isEqualTo(expectedSurvivorByte);
        assertThat(usage.requestBoundarySnapshotBytes())
                .isEqualTo((long) DATA_BLOCKS * SHARD_SIZE);
    }

    void assertNoWholeObjectMaterialization() {
        assertThat(shortStripeRequest).isNotNull();
        assertThat(shortStripeRequest.stripeIndex()).isEqualTo(1);
        assertThat(shortStripeRequest.stripeArtifacts()).hasSize(TOTAL_BLOCKS)
                .allSatisfy(artifact -> assertThat(
                        artifact.ecShardLayout().orElseThrow().stripeIndex()).isEqualTo(1));
        long suppliedBytes = shortStripeRequest.survivors().stream()
                .mapToLong(survivor -> survivor.bytes().length)
                .sum();
        assertThat(suppliedBytes).isEqualTo((long) DATA_BLOCKS * SHARD_SIZE)
                .isLessThan(FULL_OBJECT_SIZE);
    }

    void assertInvalidRequestsFailClosed() {
        assertThat(invalidFailures).hasSize(9)
                .allSatisfy(error -> assertThat(error).isInstanceOf(EcReconstructionException.class));
        assertThat(((EcReconstructionException) invalidFailures.get(4)).reason())
                .isEqualTo(EcReconstructionException.Reason.UNSUPPORTED_GEOMETRY);
        assertThat(invalidFailures.stream()
                .map(error -> ((EcReconstructionException) error).reason())
                .collect(Collectors.toSet()))
                .contains(
                        EcReconstructionException.Reason.INSUFFICIENT_SURVIVORS,
                        EcReconstructionException.Reason.INVALID_SURVIVOR_SET,
                        EcReconstructionException.Reason.INCONSISTENT_METADATA,
                        EcReconstructionException.Reason.INTEGRITY_MISMATCH,
                        EcReconstructionException.Reason.UNSUPPORTED_SCHEMA);
    }

    void assertNoPublication(String relativeRoot) {
        assertThat(relativeRoot).isEqualTo(STORAGE_ROOT);
        assertThat(afterSnapshot).isEqualTo(beforeSnapshot);
    }

    void assertPlanningOnlyOutput() {
        assertThat(planningResult).isNotNull();
        assertThat(planningResult.regeneratedShards())
                .extracting(RegeneratedShard::shardIndex)
                .containsExactly(2);
        assertThat(Arrays.stream(ReconstructionResult.class.getRecordComponents())
                .map(component -> component.getName()).toList())
                .containsExactly("verifiedStripe", "regeneratedShards");
        assertThat(afterSnapshot).isEqualTo(beforeSnapshot);
    }

    void assertBoundedExecutionScheduler() {
        assertThat(reconstructionCallerThread)
                .startsWith("req-pipeline-017-reactor-caller");
        assertThat(reconstructionExecutionThread)
                .startsWith(RECONSTRUCTION_THREAD_PREFIX)
                .isNotEqualTo(reconstructionCallerThread);
    }

    void assertExcludedClaims(List<String> claims) {
        assertThat(claims).containsExactlyInAnyOrder(
                "self-healing scanner or daemon",
                "filesystem shard replacement",
                "cluster shard placement or transfer",
                "Ratis repair job, claim, or result",
                "rebalance execution",
                "orphan cleanup",
                "distributed or ADR 0030 chaos");
        assertThat(afterSnapshot).isEqualTo(beforeSnapshot);
    }

    private Throwable executeInvalidStorageTraceProbe(
            String suffix,
            Function<EcShardLayout, Optional<EcShardLayout>> layoutMutation) {
        Path probeRoot = storageRoot.resolveSibling(
                "REQ-PIPELINE-017-trace-probe-" + suffix);
        Path probeFixture = repositoryRoot.resolve(
                "target/test-fixtures/pipeline/ec-trace-probe-" + suffix + ".bin");
        deleteRecursively(probeRoot);
        copyPrefix(fixturePath, probeFixture, 1L);

        Throwable failure = null;
        try {
            FileSystemStorageCluster probeCluster = new FileSystemStorageCluster(probeRoot, 1);
            FileSystemStorePort delegate = new FileSystemStorePort(
                    probeRoot.resolve("nodes/node-001/whole-objects"),
                    probeRoot.resolve("nodes/node-001/chunks"),
                    probeCluster.faultInjector());
            StorePort invalidTraceStore = unit -> delegate.write(unit).map(trace -> {
                if (trace.ecShardLayout().isEmpty()) {
                    return trace;
                }
                return new StorageTrace(
                        trace.info(),
                        trace.unitKind(),
                        trace.fingerprint(),
                        trace.storageRef(),
                        trace.deduplicatedReuse(),
                        trace.originalSize(),
                        trace.storedSize(),
                        trace.locations(),
                        layoutMutation.apply(trace.ecShardLayout().orElseThrow()));
            });
            StoragePolicy policy = StoragePolicy.of(
                    EC_STORAGE_CLASS,
                    Optional.empty(),
                    Optional.empty(),
                    Optional.empty(),
                    Optional.of(ErasureCodingConfig.of(DATA_BLOCKS, PARITY_BLOCKS)),
                    ReplicationConfig.of(1));
            DataProcessingPipelinePort pipelineFactory = new DataProcessingPipelineFactory(
                    new NoOpDeduplicationStep(),
                    new NoOpCompressionStep(),
                    new NoOpEncryptionStep(),
                    new NoOpErasureCodingStep(),
                    invalidTraceStore);
            ReactiveStorageOrchestrator probeOrchestrator = new ReactiveStorageOrchestrator(
                    new CompleteUploadService(),
                    new SinglePolicyCatalog(policy),
                    new EffectivePolicyResolver(),
                    new VirtualDeviceResolver(),
                    new PersistencePlanner(),
                    probeCluster.addressIndex(),
                    new FileSystemChunkStorePort(probeCluster),
                    probeCluster.storedObjectRepository(),
                    probeCluster.manifestRepository(),
                    IO_BUFFER_SIZE,
                    event -> Mono.empty(),
                    pipelineFactory);

            Flux<DataBuffer> content = DataBufferUtils.read(
                    probeFixture, BUFFER_FACTORY, IO_BUFFER_SIZE);
            probeOrchestrator.store(uploadCommand(1L), content).block();
        } catch (RuntimeException error) {
            failure = Exceptions.unwrap(error);
            while (failure.getCause() != null) {
                failure = failure.getCause();
            }
        } finally {
            deleteRecursively(probeRoot);
            try {
                Files.deleteIfExists(probeFixture);
            } catch (IOException error) {
                throw new UncheckedIOException(error);
            }
        }
        if (failure == null) {
            throw new AssertionError(
                    "Invalid EC storage trace unexpectedly produced a committed manifest");
        }
        return failure;
    }

    private void executeSchemaCompatibility() {
        List<StorageArtifactReferenceDescriptor> schemaThreeStripe =
                stripeArtifacts(compatibilityProbes.get(3).manifest(), 0);
        schemaThreeResult = decoder.reconstruct(request(
                3, 0, schemaThreeStripe, List.of(0, 2, 3, 4), Set.of(1))).block();
        assertThat(schemaThreeResult).isNotNull();

        List<StorageArtifactReferenceDescriptor> schemaTwoStripe =
                compatibilityProbes.get(2).manifest().artifacts().subList(0, TOTAL_BLOCKS);
        ReconstructionRequest schemaTwoRequest = request(
                2, 0, schemaTwoStripe, List.of(0, 2, 3, 4), Set.of(1));
        schemaTwoFailure = captureFailure(schemaTwoRequest);
    }

    private void executeSurvivorCases() {
        List<StorageArtifactReferenceDescriptor> stripe = stripeArtifacts(committedManifest, 0);
        List<CaseResult> results = new ArrayList<>();
        for (SurvivorCase scenario : survivorCases) {
            ReconstructionRequest request = request(
                    3, 0, stripe, scenario.survivors(), scenario.unavailable());
            ReconstructionResult result = decoder.reconstruct(request).block();
            assertThat(result).as(scenario.name()).isNotNull();
            results.add(new CaseResult(scenario, result));
        }
        caseResults = List.copyOf(results);

        List<CombinationResult> exhaustive = new ArrayList<>();
        for (int first = 0; first < TOTAL_BLOCKS - 3; first++) {
            for (int second = first + 1; second < TOTAL_BLOCKS - 2; second++) {
                for (int third = second + 1; third < TOTAL_BLOCKS - 1; third++) {
                    for (int fourth = third + 1; fourth < TOTAL_BLOCKS; fourth++) {
                        List<Integer> selected = List.of(first, second, third, fourth);
                        Set<Integer> unavailable = new LinkedHashSet<>();
                        for (int shardIndex = 0; shardIndex < TOTAL_BLOCKS; shardIndex++) {
                            if (!selected.contains(shardIndex)) {
                                unavailable.add(shardIndex);
                            }
                        }
                        ReconstructionResult result = decoder.reconstruct(
                                request(3, 0, stripe, selected, unavailable)).block();
                        assertThat(result).isNotNull();
                        exhaustive.add(new CombinationResult(
                                selected, Set.copyOf(unavailable), result));
                    }
                }
            }
        }
        exhaustiveCombinationResults = List.copyOf(exhaustive);
    }

    private void executeShortFinalStripe() {
        List<StorageArtifactReferenceDescriptor> stripe = stripeArtifacts(committedManifest, 1);
        shortStripeRequest = request(3, 1, stripe, List.of(0, 1, 3, 4), Set.of(2));
        shortStripeResult = decoder.reconstruct(shortStripeRequest).block();
        assertThat(shortStripeResult).isNotNull();
    }

    private void executeInvalidRequests() {
        List<StorageArtifactReferenceDescriptor> stripe = stripeArtifacts(committedManifest, 0);
        List<Throwable> failures = new ArrayList<>();

        failures.add(captureFailure(request(3, 0, stripe, List.of(0, 1, 4), Set.of(2))));

        List<ShardSurvivor> duplicate = new ArrayList<>();
        duplicate.add(survivor(stripe, 0));
        duplicate.add(survivor(stripe, 1));
        duplicate.add(survivor(stripe, 1));
        duplicate.add(survivor(stripe, 4));
        failures.add(captureFailure(new ReconstructionRequest(
                3, 0, stripe, duplicate, Set.of(2))));

        List<ShardSurvivor> outOfRange = new ArrayList<>();
        outOfRange.add(survivor(stripe, 0));
        outOfRange.add(survivor(stripe, 1));
        outOfRange.add(survivor(stripe, 2));
        outOfRange.add(new ShardSurvivor(6, survivor(stripe, 3).bytes()));
        failures.add(captureFailure(new ReconstructionRequest(
                3, 0, stripe, outOfRange, Set.of(3))));

        List<StorageArtifactReferenceDescriptor> inconsistentKm = new ArrayList<>(stripe);
        inconsistentKm.set(1, withLayout(stripe.get(1),
                new EcShardLayout(0, 1, 4, 3, false, STRIPE_SIZE)));
        failures.add(captureFailure(request(
                3, 0, inconsistentKm, List.of(0, 1, 2, 3), Set.of(5))));

        List<StorageArtifactReferenceDescriptor> unsupportedGeometry = List.of(
                withLayout(stripe.get(0), new EcShardLayout(0, 0, 3, 2, false, 3L * SHARD_SIZE)),
                withLayout(stripe.get(1), new EcShardLayout(0, 1, 3, 2, false, 3L * SHARD_SIZE)),
                withLayout(stripe.get(2), new EcShardLayout(0, 2, 3, 2, false, 3L * SHARD_SIZE)),
                withLayout(stripe.get(4), new EcShardLayout(0, 3, 3, 2, true, 3L * SHARD_SIZE)),
                withLayout(stripe.get(5), new EcShardLayout(0, 4, 3, 2, true, 3L * SHARD_SIZE)));
        failures.add(captureFailure(request(
                3, 0, unsupportedGeometry, List.of(0, 1, 2), Set.of(4))));

        List<StorageArtifactReferenceDescriptor> inconsistentStripe = new ArrayList<>(stripe);
        inconsistentStripe.set(1, withLayout(stripe.get(1),
                new EcShardLayout(1, 1, 4, 2, false, STRIPE_SIZE)));
        failures.add(captureFailure(request(
                3, 0, inconsistentStripe, List.of(0, 1, 2, 3), Set.of(5))));

        List<ShardSurvivor> wrongSize = survivors(stripe, List.of(0, 1, 2, 3));
        byte[] truncated = Arrays.copyOf(wrongSize.get(1).bytes(), SHARD_SIZE - 1);
        wrongSize.set(1, new ShardSurvivor(1, truncated));
        failures.add(captureFailure(new ReconstructionRequest(
                3, 0, stripe, wrongSize, Set.of(5))));

        List<ShardSurvivor> wrongChecksum = survivors(stripe, List.of(0, 1, 2, 3));
        byte[] corrupted = wrongChecksum.get(1).bytes();
        corrupted[17] ^= 0x5a;
        wrongChecksum.set(1, new ShardSurvivor(1, corrupted));
        failures.add(captureFailure(new ReconstructionRequest(
                3, 0, stripe, wrongChecksum, Set.of(5))));

        failures.add(captureFailure(request(
                4, 0, stripe, List.of(0, 1, 2, 3), Set.of(5))));
        invalidFailures = List.copyOf(failures);
    }

    private void executePlanningOnly() {
        List<StorageArtifactReferenceDescriptor> stripe = stripeArtifacts(committedManifest, 1);
        ReconstructionRequest request = request(
                3, 1, stripe, List.of(0, 1, 3, 5), Set.of(2));
        AtomicReference<String> callerThread = new AtomicReference<>();
        AtomicReference<String> executionThread = new AtomicReference<>();
        Scheduler simulatedReactorCaller = Schedulers.newSingle(
                "req-pipeline-017-reactor-caller");
        try {
            planningResult = Mono.defer(() -> {
                        callerThread.set(Thread.currentThread().getName());
                        return decoder.reconstruct(request)
                                .doOnNext(ignored -> executionThread.set(
                                        Thread.currentThread().getName()));
                    })
                    .subscribeOn(simulatedReactorCaller)
                    .block();
        } finally {
            simulatedReactorCaller.dispose();
        }
        reconstructionCallerThread = callerThread.get();
        reconstructionExecutionThread = executionThread.get();
        assertThat(planningResult).isNotNull();
    }

    private ReconstructionRequest request(
            int schema,
            long stripeIndex,
            List<StorageArtifactReferenceDescriptor> artifacts,
            List<Integer> survivorIndices,
            Set<Integer> unavailable) {
        return new ReconstructionRequest(
                schema,
                stripeIndex,
                artifacts,
                survivors(artifacts, survivorIndices),
                unavailable);
    }

    private List<ShardSurvivor> survivors(
            List<StorageArtifactReferenceDescriptor> artifacts,
            List<Integer> indices) {
        List<ShardSurvivor> survivors = new ArrayList<>();
        for (int index : indices) {
            survivors.add(survivor(artifacts, index));
        }
        return survivors;
    }

    private ShardSurvivor survivor(
            List<StorageArtifactReferenceDescriptor> artifacts,
            int shardIndex) {
        StorageArtifactReferenceDescriptor artifact = artifacts.get(shardIndex);
        byte[] bytes = chunkStore.read(artifact).block();
        assertThat(bytes).isNotNull();
        return new ShardSurvivor(shardIndex, bytes);
    }

    private Throwable captureFailure(ReconstructionRequest request) {
        try {
            ReconstructionResult result = decoder.reconstruct(request).block();
            throw new AssertionError("Invalid reconstruction request unexpectedly produced: " + result);
        } catch (RuntimeException error) {
            Throwable unwrapped = Exceptions.unwrap(error);
            assertThat(unwrapped).isInstanceOf(EcReconstructionException.class);
            return unwrapped;
        }
    }

    private void prepareEcWrite(long logicalSize) {
        assertThat(storageRoot).as("scenario filesystem root").isNotNull();
        assertThat(fixturePath).as("EC source fixture").isNotNull();
        if (committedManifest != null) {
            return;
        }

        Path uploadPath = fixturePath;
        if (logicalSize == SHORT_OBJECT_SIZE) {
            uploadPath = repositoryRoot.resolve(
                    "target/test-fixtures/pipeline/ec-object-6291593.bin").normalize();
            copyPrefix(fixturePath, uploadPath, SHORT_OBJECT_SIZE);
        }

        cluster = new FileSystemStorageCluster(storageRoot, 1);
        manifestRepository = cluster.manifestRepository();
        chunkStore = new FileSystemChunkStorePort(cluster);
        StoragePolicy policy = StoragePolicy.of(
                EC_STORAGE_CLASS,
                Optional.empty(),
                Optional.empty(),
                Optional.empty(),
                Optional.of(ErasureCodingConfig.of(DATA_BLOCKS, PARITY_BLOCKS)),
                ReplicationConfig.of(1));
        FileSystemStorePort storePort = new FileSystemStorePort(
                storageRoot.resolve("nodes/node-001/whole-objects"),
                storageRoot.resolve("nodes/node-001/chunks"),
                cluster.faultInjector());
        DataProcessingPipelinePort pipelineFactory = new DataProcessingPipelineFactory(
                new NoOpDeduplicationStep(),
                new NoOpCompressionStep(),
                new NoOpEncryptionStep(),
                new NoOpErasureCodingStep(),
                storePort);
        StorageEventPublisher eventPublisher = event -> {
            events.add(event);
            return Mono.empty();
        };
        orchestrator = new ReactiveStorageOrchestrator(
                new CompleteUploadService(),
                new SinglePolicyCatalog(policy),
                new EffectivePolicyResolver(),
                new VirtualDeviceResolver(),
                new PersistencePlanner(),
                cluster.addressIndex(),
                chunkStore,
                cluster.storedObjectRepository(),
                manifestRepository,
                IO_BUFFER_SIZE,
                eventPublisher,
                pipelineFactory);

        Flux<DataBuffer> content = DataBufferUtils.read(uploadPath, BUFFER_FACTORY, IO_BUFFER_SIZE);
        StoredObject stored = orchestrator.store(
                uploadCommand(logicalSize), content).block();
        assertThat(stored).isNotNull();
        committedManifest = manifestRepository.findBy(stored.manifestId()).block();
        assertThat(committedManifest).isNotNull();
        committedManifestFile = storageRoot.resolve("metadata/manifests")
                .resolve(stored.manifestId().value() + ".properties");
        assertThat(committedManifestFile).isRegularFile();
        assertThat(events).isNotEmpty();
    }

    private CompleteUploadCommand uploadCommand(long logicalSize) {
        String bucketName = "ec-storage-unit-bucket";
        String objectKey = logicalSize == FULL_OBJECT_SIZE
                ? "pipeline/2026/ec/reconstruction-source.bin"
                : "pipeline/2026/ec/reconstruction-short-source.bin";
        BucketRef bucket = BucketRef.of(BucketId.of(bucketName), bucketName);
        UploadRequestContext context = UploadRequestContext.of(
                ObjectKey.of(bucketName, objectKey),
                bucket,
                EC_STORAGE_CLASS,
                ObjectContentDescriptor.of("application/octet-stream", logicalSize),
                ObjectMetadataDescriptor.empty(),
                EncryptionRequest.none(),
                Optional.empty());
        return new CompleteUploadCommand(context, UploadMode.SINGLE_OBJECT, Optional.empty());
    }

    private void prepareCompatibilityProbes() {
        ManifestId schemaThreeId = committedManifest.manifestId();
        compatibilityProbes.put(3, new ManifestProbe(
                schemaThreeId, committedManifest, committedManifestFile));

        Properties schemaThree = readProperties(committedManifestFile);
        Properties schemaTwo = copyProperties(schemaThree);
        schemaTwo.setProperty("manifest.schemaVersion", "2");
        removeKeysMatching(schemaTwo, "artifact\\.\\d+\\.ecLayout\\..*");
        ManifestProbe schemaTwoProbe = writeProbe(schemaTwo);
        compatibilityProbes.put(2, schemaTwoProbe);

        LegacyArtifact legacy = prepareLegacyWholeObjectArtifact();
        Properties schemaOne = legacyManifestProperties(schemaThree, legacy, true);
        ManifestProbe schemaOneProbe = writeProbe(schemaOne);
        compatibilityProbes.put(1, schemaOneProbe);

        Properties schemaZero = legacyManifestProperties(schemaThree, legacy, false);
        ManifestProbe schemaZeroProbe = writeProbe(schemaZero);
        compatibilityProbes.put(0, schemaZeroProbe);
    }

    private LegacyArtifact prepareLegacyWholeObjectArtifact() {
        try {
            UUID artifactId = UUID.randomUUID();
            Path chunks = storageRoot.resolve("nodes/node-001/chunks");
            Files.createDirectories(chunks);
            Path data = chunks.resolve(artifactId.toString());
            Files.copy(fixturePath, data, StandardCopyOption.REPLACE_EXISTING);
            String checksum = sha256Hex(data);
            Files.writeString(chunks.resolve(artifactId + ".sha256"), checksum + "\n");
            return new LegacyArtifact(artifactId, checksum);
        } catch (IOException error) {
            throw new UncheckedIOException(error);
        }
    }

    private Properties legacyManifestProperties(
            Properties source,
            LegacyArtifact artifact,
            boolean schemaOne) {
        Properties legacy = copyProperties(source);
        removeKeysMatching(legacy, "artifact\\..*");
        legacy.remove("artifactCount");
        if (schemaOne) {
            legacy.setProperty("manifest.schemaVersion", "1");
        } else {
            legacy.remove("manifest.schemaVersion");
        }
        legacy.setProperty("chunkCount", "1");
        legacy.setProperty("totalOriginalSize", Long.toString(FULL_OBJECT_SIZE));
        legacy.setProperty("totalStoredSize", Long.toString(FULL_OBJECT_SIZE));
        legacy.setProperty("chunk.0.chunkId", artifact.id().toString());
        legacy.setProperty("chunk.0.fingerprint.algorithm", "SHA256");
        legacy.setProperty("chunk.0.fingerprint.value", artifact.checksum());
        legacy.setProperty("chunk.0.originalSize", Long.toString(FULL_OBJECT_SIZE));
        legacy.setProperty("chunk.0.storedSize", Long.toString(FULL_OBJECT_SIZE));
        legacy.setProperty("chunk.0.finalChecksum.algorithm", "SHA256");
        legacy.setProperty("chunk.0.finalChecksum.value", artifact.checksum());
        legacy.setProperty("chunk.0.locations", LOCAL_NODE_ID);
        legacy.setProperty("chunk.0.stepChecksumCount", "0");
        return legacy;
    }

    private ManifestProbe writeProbe(Properties properties) {
        ManifestId manifestId = ManifestId.generate();
        properties.setProperty("manifestId", manifestId.value().toString());
        properties.remove("manifest.checksum");
        Path path = storageRoot.resolve("metadata/manifests")
                .resolve(manifestId.value() + ".properties");
        try (StringWriter writer = new StringWriter()) {
            properties.store(writer, "REQ-PIPELINE-017 compatibility probe");
            String normalized = writer.toString().endsWith("\n")
                    ? writer.toString()
                    : writer + "\n";
            String content = normalized + "manifest.checksum=" + sha256Hex(normalized) + "\n";
            Files.writeString(path, content);
        } catch (IOException error) {
            throw new UncheckedIOException(error);
        }
        ObjectManifest manifest = manifestRepository.findBy(manifestId).block();
        assertThat(manifest).isNotNull();
        return new ManifestProbe(manifestId, manifest, path);
    }

    private void assertReadMatchesFixture(ManifestId manifestId, long expectedLength) {
        MessageDigest actual = sha256();
        AtomicLong length = new AtomicLong();
        orchestrator.read(manifestId)
                .doOnNext(bytes -> {
                    actual.update(bytes);
                    length.addAndGet(bytes.length);
                })
                .then()
                .block();
        assertThat(length.get()).isEqualTo(expectedLength);
        assertThat(HexFormat.of().formatHex(actual.digest())).isEqualTo(sha256Hex(fixturePath));
    }

    private static StorageArtifactReferenceDescriptor withLayout(
            StorageArtifactReferenceDescriptor source,
            EcShardLayout layout) {
        return new StorageArtifactReferenceDescriptor(
                source.artifactKind(),
                source.chunkId(),
                source.fingerprint(),
                source.originalSize(),
                source.storedSize(),
                source.stepChecksums(),
                source.finalChecksum(),
                source.locations(),
                Optional.of(layout));
    }

    private static List<StorageArtifactReferenceDescriptor> stripeArtifacts(
            ObjectManifest manifest, long stripeIndex) {
        return manifest.artifacts().stream()
                .filter(artifact -> artifact.ecShardLayout().isPresent())
                .filter(artifact -> artifact.ecShardLayout().orElseThrow().stripeIndex() == stripeIndex)
                .sorted(Comparator.comparingInt(
                        artifact -> artifact.ecShardLayout().orElseThrow().shardIndex()))
                .toList();
    }

    private byte[] readFixtureRange(long offset, int length) {
        try (FileChannel channel = FileChannel.open(fixturePath, StandardOpenOption.READ)) {
            ByteBuffer buffer = ByteBuffer.allocate(length);
            channel.position(offset);
            while (buffer.hasRemaining()) {
                int read = channel.read(buffer);
                if (read < 0) {
                    throw new IllegalStateException("Fixture ended before requested stripe range");
                }
            }
            return buffer.array();
        } catch (IOException error) {
            throw new UncheckedIOException(error);
        }
    }

    private static Set<Integer> parseIndices(String raw) {
        if (raw == null || raw.isBlank() || "none".equals(raw.trim())) {
            return Set.of();
        }
        return Arrays.stream(raw.split(","))
                .map(String::trim)
                .map(Integer::parseInt)
                .collect(Collectors.toCollection(LinkedHashSet::new));
    }

    private static Properties readProperties(Path path) {
        Properties properties = new Properties();
        try {
            properties.load(new StringReader(Files.readString(path)));
        } catch (IOException error) {
            throw new UncheckedIOException(error);
        }
        properties.remove("manifest.checksum");
        return properties;
    }

    private static Properties copyProperties(Properties source) {
        Properties copy = new Properties();
        source.forEach(copy::put);
        return copy;
    }

    private static void removeKeysMatching(Properties properties, String regex) {
        properties.stringPropertyNames().stream()
                .filter(key -> key.matches(regex))
                .toList()
                .forEach(properties::remove);
    }

    private static Map<String, String> filesystemSnapshot(Path root) {
        if (root == null || !Files.exists(root)) {
            return Map.of();
        }
        Map<String, String> snapshot = new LinkedHashMap<>();
        try (Stream<Path> paths = Files.walk(root)) {
            for (Path path : paths.filter(Files::isRegularFile).sorted().toList()) {
                snapshot.put(root.relativize(path).toString(), sha256Hex(path));
            }
        } catch (IOException error) {
            throw new UncheckedIOException(error);
        }
        return Map.copyOf(snapshot);
    }

    private static void ensureDeterministicFixture(Path path, long size) {
        try {
            if (Files.isRegularFile(path) && Files.size(path) == size) {
                return;
            }
            Files.createDirectories(path.getParent());
            try (var output = Files.newOutputStream(
                    path,
                    StandardOpenOption.CREATE,
                    StandardOpenOption.TRUNCATE_EXISTING,
                    StandardOpenOption.WRITE)) {
                byte[] block = new byte[IO_BUFFER_SIZE];
                long written = 0;
                while (written < size) {
                    int count = Math.toIntExact(Math.min(block.length, size - written));
                    for (int index = 0; index < count; index++) {
                        long absolute = written + index;
                        block[index] = (byte) ((absolute * 31 + 17) & 0xff);
                    }
                    output.write(block, 0, count);
                    written += count;
                }
            }
        } catch (IOException error) {
            throw new UncheckedIOException(error);
        }
    }

    private static void copyPrefix(Path source, Path target, long length) {
        try {
            Files.createDirectories(target.getParent());
            try (var input = Files.newInputStream(source);
                    var output = Files.newOutputStream(
                            target,
                            StandardOpenOption.CREATE,
                            StandardOpenOption.TRUNCATE_EXISTING,
                            StandardOpenOption.WRITE)) {
                byte[] block = new byte[IO_BUFFER_SIZE];
                long remaining = length;
                while (remaining > 0) {
                    int expected = Math.toIntExact(Math.min(block.length, remaining));
                    int read = input.read(block, 0, expected);
                    if (read < 0) {
                        throw new IllegalStateException("Source fixture ended before short logical view");
                    }
                    output.write(block, 0, read);
                    remaining -= read;
                }
            }
        } catch (IOException error) {
            throw new UncheckedIOException(error);
        }
    }

    private static String sha256Hex(Path path) {
        MessageDigest digest = sha256();
        try (var input = Files.newInputStream(path)) {
            byte[] block = new byte[IO_BUFFER_SIZE];
            int read;
            while ((read = input.read(block)) >= 0) {
                digest.update(block, 0, read);
            }
        } catch (IOException error) {
            throw new UncheckedIOException(error);
        }
        return HexFormat.of().formatHex(digest.digest());
    }

    private static String sha256Hex(byte[] bytes) {
        return HexFormat.of().formatHex(sha256().digest(bytes));
    }

    private static String sha256Hex(String value) {
        return sha256Hex(value.getBytes(StandardCharsets.UTF_8));
    }

    private static MessageDigest sha256() {
        try {
            return MessageDigest.getInstance("SHA-256");
        } catch (NoSuchAlgorithmException error) {
            throw new IllegalStateException("SHA-256 unavailable", error);
        }
    }

    private static Path findRepositoryRoot() {
        Path current = Path.of("").toAbsolutePath().normalize();
        while (current != null) {
            if (Files.exists(current.resolve("s3-reactive-api-adapter/pom.xml"))) {
                return current;
            }
            current = current.getParent();
        }
        throw new IllegalStateException("Repository root not found");
    }

    private static void deleteRecursively(Path root) {
        if (!Files.exists(root)) {
            return;
        }
        try (Stream<Path> paths = Files.walk(root)) {
            for (Path path : paths.sorted(Comparator.reverseOrder()).toList()) {
                Files.delete(path);
            }
        } catch (IOException error) {
            throw new UncheckedIOException(error);
        }
    }

    private enum ScenarioMode {
        NONE,
        SCHEMA_COMPATIBILITY,
        SURVIVOR_CASES,
        SHORT_FINAL_STRIPE,
        INVALID_REQUESTS,
        PLANNING_ONLY
    }

    private record SurvivorCase(
            String name,
            List<Integer> survivors,
            Set<Integer> unavailable,
            Set<Integer> expected) {
        private SurvivorCase {
            survivors = List.copyOf(survivors);
            unavailable = Set.copyOf(unavailable);
            expected = Set.copyOf(expected);
        }
    }

    private record CaseResult(SurvivorCase scenario, ReconstructionResult result) {
    }

    private record CombinationResult(
            List<Integer> survivors,
            Set<Integer> unavailable,
            ReconstructionResult result) {
        private CombinationResult {
            survivors = List.copyOf(survivors);
            unavailable = Set.copyOf(unavailable);
        }
    }

    private record ManifestProbe(
            ManifestId manifestId,
            ObjectManifest manifest,
            Path path) {
    }

    private record LegacyArtifact(UUID id, String checksum) {
    }

    private record SinglePolicyCatalog(StoragePolicy policy) implements StoragePolicyCatalog {
        @Override
        public Mono<StoragePolicy> findById(String policyId) {
            return Mono.justOrEmpty(policyId.equals(policy.id().value())
                    ? policy : null);
        }

        @Override
        public Mono<StoragePolicy> findBy(StorageClassId id) {
            return Mono.justOrEmpty(id.equals(policy.id()) ? policy : null);
        }

        @Override
        public Flux<StoragePolicy> findAll() {
            return Flux.just(policy);
        }
    }
}
