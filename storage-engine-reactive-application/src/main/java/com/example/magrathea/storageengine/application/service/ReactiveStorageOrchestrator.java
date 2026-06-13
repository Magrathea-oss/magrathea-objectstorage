package com.example.magrathea.storageengine.application.service;

import com.example.magrathea.storageengine.application.port.AlterationPort;
import com.example.magrathea.storageengine.application.port.ChecksumPort;
import com.example.magrathea.storageengine.application.port.ChunkStorePort;
import com.example.magrathea.storageengine.application.port.ContentAddressIndex;
import com.example.magrathea.storageengine.application.port.DataTransformPort;
import com.example.magrathea.storageengine.application.port.ECOutcome;
import com.example.magrathea.storageengine.application.port.ObjectManifestRepository;
import com.example.magrathea.storageengine.application.port.StoragePolicyCatalog;
import com.example.magrathea.storageengine.application.port.StoredObjectRepository;
import com.example.magrathea.storageengine.domain.aggregate.ObjectState;
import com.example.magrathea.storageengine.domain.aggregate.StoredObject;
import com.example.magrathea.storageengine.domain.service.CompleteUploadService;
import com.example.magrathea.storageengine.domain.service.EffectivePolicyResolver;
import com.example.magrathea.storageengine.domain.service.PersistencePlanner;
import com.example.magrathea.storageengine.domain.service.VirtualDeviceResolver;
import com.example.magrathea.storageengine.domain.valueobject.AlterationResult;
import com.example.magrathea.storageengine.domain.valueobject.ChecksumCalculationResult;
import com.example.magrathea.storageengine.domain.valueobject.ChecksumVerificationResult;
import com.example.magrathea.storageengine.domain.valueobject.ChunkId;
import com.example.magrathea.storageengine.domain.valueobject.ChunkPersistenceTrace;
import com.example.magrathea.storageengine.domain.valueobject.ChunkReferenceDescriptor;
import com.example.magrathea.storageengine.domain.valueobject.CompleteUploadCommand;
import com.example.magrathea.storageengine.domain.valueobject.ChecksumAlgorithm;
import com.example.magrathea.storageengine.domain.valueobject.ContentHash;
import com.example.magrathea.storageengine.domain.valueobject.DedupConfig;
import com.example.magrathea.storageengine.domain.valueobject.Fingerprint;
import com.example.magrathea.storageengine.domain.valueobject.FingerprintAlgorithm;
import com.example.magrathea.storageengine.domain.valueobject.ManifestId;
import com.example.magrathea.storageengine.domain.valueobject.ObjectManifest;
import com.example.magrathea.storageengine.domain.valueobject.PersistencePlan;
import com.example.magrathea.storageengine.domain.valueobject.PolicyDecision;
import com.example.magrathea.storageengine.domain.valueobject.PolicyDecisionReason;
import com.example.magrathea.storageengine.domain.valueobject.PolicyDecisionStatus;
import com.example.magrathea.storageengine.domain.valueobject.StepChecksumDescriptor;
import com.example.magrathea.storageengine.domain.valueobject.StepExecutionRecord;
import com.example.magrathea.storageengine.domain.valueobject.StepExecutionStatus;
import com.example.magrathea.storageengine.domain.valueobject.StepPlan;
import com.example.magrathea.storageengine.domain.valueobject.StepId;
import com.example.magrathea.storageengine.domain.valueobject.StepOutcome;
import com.example.magrathea.storageengine.domain.valueobject.UploadCompletionTrace;
import com.example.magrathea.storageengine.domain.valueobject.VirtualDevice;
import org.springframework.core.io.buffer.DataBuffer;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import reactor.core.scheduler.Schedulers;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Optional;

/**
 * Reactive application service orchestrating the full object storage pipeline.
 * <p>
 * Pipeline steps per chunk:
 * 1. DEDUP — fingerprint + content-address lookup
 * 2. COMPRESS — compress data
 * 3. CRYPT — encrypt data
 * 4. ERASURE_CODING — erasure encode data
 * 5. REPLICATION — replicate data across nodes
 * 6. STORE — persist data to storage nodes
 */
public class ReactiveStorageOrchestrator {

    private final CompleteUploadService completeUploadService;
    private final StoragePolicyCatalog storagePolicyCatalog;
    private final EffectivePolicyResolver effectivePolicyResolver;
    private final VirtualDeviceResolver virtualDeviceResolver;
    private final PersistencePlanner persistencePlanner;
    private final ChecksumPort checksumPort;
    private final DataTransformPort dataTransformPort;
    private final ContentAddressIndex contentAddressIndex;
    private final AlterationPort alterationPort;
    private final ChunkStorePort chunkStorePort;
    private final StoredObjectRepository storedObjectRepository;
    private final ObjectManifestRepository objectManifestRepository;

    private final int defaultChunkSizeBytes;

    public ReactiveStorageOrchestrator(
            CompleteUploadService completeUploadService,
            StoragePolicyCatalog storagePolicyCatalog,
            EffectivePolicyResolver effectivePolicyResolver,
            VirtualDeviceResolver virtualDeviceResolver,
            PersistencePlanner persistencePlanner,
            ChecksumPort checksumPort,
            DataTransformPort dataTransformPort,
            ContentAddressIndex contentAddressIndex,
            AlterationPort alterationPort,
            ChunkStorePort chunkStorePort,
            StoredObjectRepository storedObjectRepository,
            ObjectManifestRepository objectManifestRepository,
            int defaultChunkSizeBytes) {
        this.completeUploadService = completeUploadService;
        this.storagePolicyCatalog = storagePolicyCatalog;
        this.effectivePolicyResolver = effectivePolicyResolver;
        this.virtualDeviceResolver = virtualDeviceResolver;
        this.persistencePlanner = persistencePlanner;
        this.checksumPort = checksumPort;
        this.dataTransformPort = dataTransformPort;
        this.contentAddressIndex = contentAddressIndex;
        this.alterationPort = alterationPort;
        this.chunkStorePort = chunkStorePort;
        this.storedObjectRepository = storedObjectRepository;
        this.objectManifestRepository = objectManifestRepository;
        this.defaultChunkSizeBytes = defaultChunkSizeBytes;
    }

    /**
     * Stores an object by completing the upload, resolving policy, planning persistence,
     * chunking the data, running the 6-step pipeline per chunk, building the manifest,
     * and persisting the aggregate.
     *
     * @param command the complete upload command
     * @param data    the raw object data as a flux of DataBuffers
     * @return the stored StoredObject
     */
    public Mono<StoredObject> store(CompleteUploadCommand command, Flux<DataBuffer> data) {
        // Step 1: Complete upload — validate and produce trace
        UploadCompletionTrace trace = completeUploadService.complete(command);

        // Step 2: Look up storage policy
        return storagePolicyCatalog.findBy(command.context().storageClassId())
                .flatMap(policy -> {
                    // Step 3: Resolve effective policy
                    var effectivePolicy = effectivePolicyResolver.resolve(policy, command.context());

                    // Step 4: Resolve virtual device
                    VirtualDevice device = virtualDeviceResolver.resolve(effectivePolicy, command.context().bucket());

                    // Step 5: Create persistence plan
                    PersistencePlan plan = persistencePlanner.createPlan(effectivePolicy, device);

                    // Step 6: Stream-chunk data without full-object materialization (REQ-UPLOAD-003)
                    boolean dedupEnabled = plan.steps().get(0).expectedStatus() == StepExecutionStatus.EXECUTED;
                    long chunkSize = dedupEnabled
                            ? plan.effectivePolicy().dedup().map(DedupConfig::chunkSize).orElse((long) defaultChunkSizeBytes)
                            : defaultChunkSizeBytes;
                    Chunker chunker = new Chunker((int) chunkSize);
                    return chunker.chunk(data)
                            .concatMap(chunkPayload -> processChunk(chunkPayload, plan))
                            .collectList()
                            .flatMap(chunkTraces -> {
                                // Step 9: Build ChunkReferenceDescriptor list
                                List<ChunkReferenceDescriptor> descriptors = buildDescriptors(chunkTraces);

                                // Step 10: Build ObjectManifest
                                long totalOriginalSize = descriptors.stream()
                                        .mapToLong(ChunkReferenceDescriptor::originalSize)
                                        .sum();
                                long totalStoredSize = descriptors.stream()
                                        .mapToLong(ChunkReferenceDescriptor::storedSize)
                                        .sum();

                                List<PolicyDecision> policyDecisions = buildPolicyDecisions(plan);

                                ManifestId manifestId = ManifestId.generate();
                                com.example.magrathea.storageengine.domain.valueobject.ObjectId objectId =
                                        com.example.magrathea.storageengine.domain.valueobject.ObjectId.of(
                                                command.context().objectKey().bucket() + "/" + command.context().objectKey().key());
                                com.example.magrathea.storageengine.domain.valueobject.VersionId versionId =
                                        com.example.magrathea.storageengine.domain.valueobject.VersionId.of(
                                                java.util.UUID.randomUUID().toString());
                                ObjectManifest manifest = new ObjectManifest(
                                        manifestId,
                                        objectId,
                                        versionId,
                                        command.context().storageClassId(),
                                        device,
                                        plan.deviceHash(),
                                        trace,
                                        policyDecisions,
                                        descriptors.size(),
                                        totalOriginalSize,
                                        totalStoredSize,
                                        descriptors);

                                // Validate manifest
                                persistencePlanner.validateManifest(manifest, plan);

                                // Step 11: Save StoredObject and ObjectManifest
                                StoredObject storedObject = StoredObject.create(
                                        objectId,
                                        versionId,
                                        command.context().bucket(),
                                        command.context().storageClassId(),
                                        device);
                                storedObject.attachManifest(manifestId);

                                return storedObjectRepository.save(storedObject)
                                        .then(objectManifestRepository.save(manifest))
                                        .then(Mono.just(storedObject));
                            });
                });
    }

    /**
     * Reads the content for a persisted manifest by fetching referenced chunks in manifest order.
     */
    public Flux<byte[]> read(ManifestId manifestId) {
        return objectManifestRepository.findBy(manifestId)
                .flatMapMany(manifest -> Flux.fromIterable(manifest.chunks()))
                .concatMap(chunk -> chunkStorePort.read(chunk.chunkId())
                        .map(storedData -> restoreReadableChunk(storedData, chunk)));
    }

    private static byte[] restoreReadableChunk(byte[] storedData, ChunkReferenceDescriptor chunk) {
        if (!hasAppliedErasureCoding(chunk) || storedData.length <= chunk.originalSize()) {
            return storedData;
        }
        return Arrays.copyOf(storedData, Math.toIntExact(chunk.originalSize()));
    }

    private static boolean hasAppliedErasureCoding(ChunkReferenceDescriptor chunk) {
        return chunk.stepChecksums().stream()
                .anyMatch(checksum -> checksum.stepId() == StepId.ERASURE_CODING
                        && !checksum.inputChecksum().equals(checksum.outputChecksum()));
    }

    /**
     * Runs the 6-step persistence pipeline for a single chunk.
     */
    private Mono<ChunkPersistenceTrace> processChunk(ApplicationChunkPayload payload, PersistencePlan plan) {
        byte[] currentData = payload.data();
        ChunkId requestedChunkId = payload.chunkId();
        List<StepExecutionRecord> steps = new ArrayList<>();

        // Calculate initial fingerprint for dedup
        Fingerprint fingerprint = checksumPort.fingerprint(
                currentData, plan.effectivePolicy().dedup()
                        .map(com.example.magrathea.storageengine.domain.valueobject.DedupConfig::algorithm)
                        .orElse(FingerprintAlgorithm.SHA256));
        long originalSize = currentData.length;

        // Use a scheduler for CPU-bound work
        return Mono.fromRunnable(() -> {
                    // We'll build steps sequentially inside a flatMap chain
                })
                .then(Mono.defer(() -> {
                    // STEP 1: DEDUP
                    StepPlan dedupPlan = plan.steps().get(0);
                    if (dedupPlan.expectedStatus() == StepExecutionStatus.EXECUTED) {
                        return processDedupStep(currentData, fingerprint, plan)
                                .flatMap(result -> {
                                    steps.add(result.record());
                                    if (result.reused()) {
                                        appendDedupReuseShortCircuitSteps(
                                                steps, plan, currentData, result.locations());
                                        return Mono.just(new ChunkProcessingState(
                                                result.chunkId(), currentData, true));
                                    }
                                    return Mono.just(new ChunkProcessingState(
                                            requestedChunkId, currentData, false));
                                });
                    } else {
                        ContentHash inputHash = checksumPort.calculate(currentData, ChecksumAlgorithm.SHA256);
                        steps.add(buildSkippedStep(StepId.DEDUP, inputHash));
                        return Mono.just(new ChunkProcessingState(requestedChunkId, currentData, false));
                    }
                }))
                .flatMap(stateAfterDedup -> {
                    if (stateAfterDedup.reused()) {
                        return Mono.just(stateAfterDedup);
                    }
                    byte[] dataAfterDedup = stateAfterDedup.data();
                    // STEP 2: COMPRESS
                    StepPlan compressPlan = plan.steps().get(1);
                    if (compressPlan.expectedStatus() == StepExecutionStatus.EXECUTED) {
                        byte[] compressed = dataTransformPort.compress(
                                dataAfterDedup,
                                compressPlan.config()
                                        .map(c -> ((com.example.magrathea.storageengine.domain.valueobject.StepConfig.CompressStepConfig) c).config())
                                        .orElseThrow());
                        ContentHash inputHash = checksumPort.calculate(dataAfterDedup, ChecksumAlgorithm.SHA256);
                        ContentHash outputHash = checksumPort.calculate(compressed, ChecksumAlgorithm.SHA256);
                        steps.add(buildExecutedStep(
                                StepId.COMPRESS,
                                new StepOutcome.CompressOutcome(
                                        com.example.magrathea.storageengine.domain.valueobject.CompressionAlgorithm.GZIP,
                                        dataAfterDedup.length, compressed.length),
                                inputHash, outputHash));
                        return Mono.just(stateAfterDedup.withData(compressed));
                    } else {
                        ContentHash inputHash = checksumPort.calculate(dataAfterDedup, ChecksumAlgorithm.SHA256);
                        steps.add(buildSkippedStep(StepId.COMPRESS, inputHash));
                        return Mono.just(stateAfterDedup);
                    }
                })
                .flatMap(stateAfterCompress -> {
                    if (stateAfterCompress.reused()) {
                        return Mono.just(stateAfterCompress);
                    }
                    byte[] dataAfterCompress = stateAfterCompress.data();
                    // STEP 3: CRYPT
                    StepPlan cryptPlan = plan.steps().get(2);
                    if (cryptPlan.expectedStatus() == StepExecutionStatus.EXECUTED) {
                        byte[] encrypted = dataTransformPort.encrypt(
                                dataAfterCompress,
                                cryptPlan.config()
                                        .map(c -> ((com.example.magrathea.storageengine.domain.valueobject.StepConfig.CryptStepConfig) c).config())
                                        .orElseThrow());
                        ContentHash inputHash = checksumPort.calculate(dataAfterCompress, ChecksumAlgorithm.SHA256);
                        ContentHash outputHash = checksumPort.calculate(encrypted, ChecksumAlgorithm.SHA256);
                        steps.add(buildExecutedStep(
                                StepId.CRYPT,
                                new StepOutcome.CryptOutcome(
                                        com.example.magrathea.storageengine.domain.valueobject.EncryptionAlgorithm.SSE_S3,
                                        Optional.empty()),
                                inputHash, outputHash));
                        return Mono.just(stateAfterCompress.withData(encrypted));
                    } else {
                        ContentHash inputHash = checksumPort.calculate(dataAfterCompress, ChecksumAlgorithm.SHA256);
                        steps.add(buildSkippedStep(StepId.CRYPT, inputHash));
                        return Mono.just(stateAfterCompress);
                    }
                })
                .flatMap(stateAfterCrypt -> {
                    if (stateAfterCrypt.reused()) {
                        return Mono.just(stateAfterCrypt);
                    }
                    byte[] dataAfterCrypt = stateAfterCrypt.data();
                    // STEP 4: ERASURE_CODING
                    StepPlan ecPlan = plan.steps().get(3);
                    if (ecPlan.expectedStatus() == StepExecutionStatus.EXECUTED) {
                        return dataTransformPort.erasureEncode(
                                dataAfterCrypt,
                                ecPlan.config()
                                        .map(c -> ((com.example.magrathea.storageengine.domain.valueobject.StepConfig.ECStepConfig) c).config())
                                        .orElseThrow())
                                .flatMap(ecOutcome -> {
                                    ContentHash inputHash = checksumPort.calculate(dataAfterCrypt, ChecksumAlgorithm.SHA256);
                                    ContentHash outputHash = checksumPort.calculate(
                                            ecOutcome.encodedData(), ChecksumAlgorithm.SHA256);
                                    steps.add(buildExecutedStep(
                                            StepId.ERASURE_CODING,
                                            new StepOutcome.ErasureCodingOutcome(
                                                    ecPlan.config()
                                                            .map(c -> ((com.example.magrathea.storageengine.domain.valueobject.StepConfig.ECStepConfig) c).config())
                                                            .orElseThrow().dataBlocks(),
                                                    ecPlan.config()
                                                            .map(c -> ((com.example.magrathea.storageengine.domain.valueobject.StepConfig.ECStepConfig) c).config())
                                                            .orElseThrow().parityBlocks(),
                                                    ecOutcome.dataNodes(),
                                                    ecOutcome.parityNodes()),
                                            inputHash, outputHash));
                                    return Mono.just(stateAfterCrypt.withData(ecOutcome.encodedData()));
                                });
                    } else {
                        ContentHash inputHash = checksumPort.calculate(dataAfterCrypt, ChecksumAlgorithm.SHA256);
                        steps.add(buildSkippedStep(StepId.ERASURE_CODING, inputHash));
                        return Mono.just(stateAfterCrypt);
                    }
                })
                .flatMap(stateAfterEC -> {
                    if (stateAfterEC.reused()) {
                        return Mono.just(stateAfterEC);
                    }
                    byte[] dataAfterEC = stateAfterEC.data();
                    // STEP 5: REPLICATION
                    StepPlan replPlan = plan.steps().get(4);
                    if (replPlan.expectedStatus() == StepExecutionStatus.EXECUTED) {
                        int factor = replPlan.config()
                                .map(c -> ((com.example.magrathea.storageengine.domain.valueobject.StepConfig.ReplicationStepConfig) c).config())
                                .orElseThrow().factor();
                        return dataTransformPort.replicate(dataAfterEC, factor)
                                .flatMap(nodes -> {
                                    ContentHash inputHash = checksumPort.calculate(dataAfterEC, ChecksumAlgorithm.SHA256);
                                    ContentHash outputHash = checksumPort.calculate(dataAfterEC, ChecksumAlgorithm.SHA256);
                                    steps.add(buildExecutedStep(
                                            StepId.REPLICATION,
                                            new StepOutcome.ReplicationOutcome(factor, nodes),
                                            inputHash, outputHash));
                                    return Mono.just(stateAfterEC);
                                });
                    } else {
                        ContentHash inputHash = checksumPort.calculate(dataAfterEC, ChecksumAlgorithm.SHA256);
                        steps.add(buildSkippedStep(StepId.REPLICATION, inputHash));
                        return Mono.just(stateAfterEC);
                    }
                })
                .flatMap(stateAfterRepl -> {
                    if (stateAfterRepl.reused()) {
                        return Mono.just(stateAfterRepl);
                    }
                    byte[] dataAfterRepl = stateAfterRepl.data();
                    // STEP 6: STORE
                    return chunkStorePort.store(stateAfterRepl.chunkId(), dataAfterRepl, plan)
                            .flatMap(nodes -> {
                                ContentHash inputHash = checksumPort.calculate(dataAfterRepl, ChecksumAlgorithm.SHA256);
                                ContentHash outputHash = checksumPort.calculate(dataAfterRepl, ChecksumAlgorithm.SHA256);
                                StepOutcome.StoreOutcome storeOutcome = new StepOutcome.StoreOutcome(
                                        plan.targetDevice(), nodes, dataAfterRepl.length);
                                steps.add(buildExecutedStep(StepId.STORE, storeOutcome, inputHash, outputHash));
                                Mono<Void> recordNewChunk = plan.steps().get(0).expectedStatus() == StepExecutionStatus.EXECUTED
                                        ? contentAddressIndex.record(plan.deviceHash(), fingerprint, stateAfterRepl.chunkId())
                                        : Mono.empty();
                                return recordNewChunk.thenReturn(stateAfterRepl);
                            });
                })
                .flatMap(finalState -> Mono.fromCallable(() -> {
                    // Build ChunkPersistenceTrace
                    // Validate trace against plan
                    ChunkPersistenceTrace trace = new ChunkPersistenceTrace(
                            finalState.chunkId(), fingerprint, originalSize, List.copyOf(steps));
                    persistencePlanner.validateTrace(trace, plan);
                    return trace;
                }));
    }

    private Mono<DedupResult> processDedupStep(
            byte[] data, Fingerprint fingerprint, PersistencePlan plan) {
        ContentHash inputHash = checksumPort.calculate(data, ChecksumAlgorithm.SHA256);
        return contentAddressIndex.find(plan.deviceHash(), fingerprint)
                .map(optDescriptor -> {
                    if (optDescriptor.isPresent()) {
                        ChunkReferenceDescriptor existing = optDescriptor.get();
                        StepExecutionRecord record = new StepExecutionRecord(
                                StepId.DEDUP, StepExecutionStatus.EXECUTED,
                                Optional.of(new StepOutcome.DedupOutcome(true, Optional.of(existing.chunkId()))),
                                Optional.of(ChecksumCalculationResult.of(inputHash)),
                                Optional.of(AlterationResult.notApplied()),
                                Optional.of(ChecksumVerificationResult.verified()),
                                Optional.empty(),
                                Optional.of(inputHash),
                                Optional.of(inputHash));
                        return new DedupResult(record, existing.chunkId(), true, existing.locations());
                    }
                    StepExecutionRecord record = new StepExecutionRecord(
                            StepId.DEDUP, StepExecutionStatus.EXECUTED,
                            Optional.of(new StepOutcome.DedupOutcome(false, Optional.empty())),
                            Optional.of(ChecksumCalculationResult.of(inputHash)),
                            Optional.of(AlterationResult.notApplied()),
                            Optional.of(ChecksumVerificationResult.verified()),
                            Optional.empty(),
                            Optional.of(inputHash),
                            Optional.of(inputHash));
                    return new DedupResult(record, null, false, List.of());
                });
    }

    private StepExecutionRecord buildExecutedStep(
            StepId stepId, StepOutcome outcome,
            ContentHash inputHash, ContentHash outputHash) {
        return new StepExecutionRecord(
                stepId, StepExecutionStatus.EXECUTED,
                Optional.of(outcome),
                Optional.of(ChecksumCalculationResult.of(inputHash)),
                Optional.of(AlterationResult.notApplied()),
                Optional.of(ChecksumVerificationResult.verified()),
                Optional.empty(),
                Optional.of(inputHash),
                Optional.of(outputHash));
    }

    private StepExecutionRecord buildSkippedStep(StepId stepId, ContentHash inputHash) {
        return new StepExecutionRecord(
                stepId, StepExecutionStatus.SKIPPED,
                Optional.empty(),
                Optional.of(ChecksumCalculationResult.of(inputHash)),
                Optional.of(AlterationResult.notApplied()),
                Optional.of(ChecksumVerificationResult.verified()),
                Optional.empty(),
                Optional.of(inputHash),
                Optional.of(inputHash));
    }

    private void appendDedupReuseShortCircuitSteps(
            List<StepExecutionRecord> steps,
            PersistencePlan plan,
            byte[] originalData,
            List<com.example.magrathea.storageengine.domain.valueobject.NodeId> existingLocations) {
        ContentHash currentHash = checksumPort.calculate(originalData, ChecksumAlgorithm.SHA256);
        for (int index = 1; index < plan.steps().size(); index++) {
            StepPlan stepPlan = plan.steps().get(index);
            if (stepPlan.expectedStatus() == StepExecutionStatus.SKIPPED) {
                steps.add(buildSkippedStep(stepPlan.stepId(), currentHash));
            } else {
                steps.add(buildExecutedStep(
                        stepPlan.stepId(),
                        noOpOutcomeForDedupReuse(stepPlan, plan, existingLocations),
                        currentHash,
                        currentHash));
            }
        }
    }

    private StepOutcome noOpOutcomeForDedupReuse(
            StepPlan stepPlan,
            PersistencePlan plan,
            List<com.example.magrathea.storageengine.domain.valueobject.NodeId> existingLocations) {
        return switch (stepPlan.stepId()) {
            case COMPRESS -> new StepOutcome.CompressOutcome(
                    com.example.magrathea.storageengine.domain.valueobject.CompressionAlgorithm.GZIP, 0, 0);
            case CRYPT -> new StepOutcome.CryptOutcome(
                    com.example.magrathea.storageengine.domain.valueobject.EncryptionAlgorithm.SSE_S3,
                    Optional.empty());
            case ERASURE_CODING -> {
                var ecConfig = stepPlan.config()
                        .map(c -> ((com.example.magrathea.storageengine.domain.valueobject.StepConfig.ECStepConfig) c).config())
                        .orElseThrow();
                yield new StepOutcome.ErasureCodingOutcome(
                        ecConfig.dataBlocks(), ecConfig.parityBlocks(), List.of(), List.of());
            }
            case REPLICATION -> {
                int factor = stepPlan.config()
                        .map(c -> ((com.example.magrathea.storageengine.domain.valueobject.StepConfig.ReplicationStepConfig) c).config())
                        .orElseThrow().factor();
                yield new StepOutcome.ReplicationOutcome(factor, existingLocations);
            }
            case STORE -> new StepOutcome.StoreOutcome(plan.targetDevice(), existingLocations, 0);
            case DEDUP -> new StepOutcome.DedupOutcome(true, Optional.empty());
        };
    }

    private record DedupResult(
            StepExecutionRecord record,
            ChunkId chunkId,
            boolean reused,
            List<com.example.magrathea.storageengine.domain.valueobject.NodeId> locations) {
    }

    private record ChunkProcessingState(ChunkId chunkId, byte[] data, boolean reused) {
        ChunkProcessingState withData(byte[] newData) {
            return new ChunkProcessingState(chunkId, newData, reused);
        }
    }

    private List<ChunkReferenceDescriptor> buildDescriptors(List<ChunkPersistenceTrace> traces) {
        List<ChunkReferenceDescriptor> descriptors = new ArrayList<>();
        for (ChunkPersistenceTrace trace : traces) {
            List<StepChecksumDescriptor> stepChecksums = new ArrayList<>();
            for (StepExecutionRecord step : trace.steps()) {
                stepChecksums.add(StepChecksumDescriptor.of(
                        step.stepId(),
                        step.inputChecksum().orElseThrow(),
                        step.outputChecksum().orElseThrow()));
            }
            ContentHash finalChecksum = trace.finalChecksum();
            // Collect locations from the STORE step
            List<com.example.magrathea.storageengine.domain.valueobject.NodeId> locations = trace.steps().get(5)
                    .operationOutcome()
                    .filter(o -> o instanceof StepOutcome.StoreOutcome)
                    .map(o -> ((StepOutcome.StoreOutcome) o).locations())
                    .orElse(List.of());

            long storedSize = trace.steps().get(5)
                    .operationOutcome()
                    .filter(o -> o instanceof StepOutcome.StoreOutcome)
                    .map(o -> ((StepOutcome.StoreOutcome) o).storedSize())
                    .orElse(trace.originalSize());

            descriptors.add(new ChunkReferenceDescriptor(
                    trace.chunkId(),
                    trace.fingerprint(),
                    trace.originalSize(),
                    storedSize,
                    stepChecksums,
                    finalChecksum,
                    locations));
        }
        return descriptors;
    }

    private List<PolicyDecision> buildPolicyDecisions(PersistencePlan plan) {
        List<PolicyDecision> decisions = new ArrayList<>();
        for (var step : plan.steps()) {
            PolicyDecisionStatus status;
            PolicyDecisionReason reason;
            switch (step.expectedStatus()) {
                case EXECUTED -> {
                    status = PolicyDecisionStatus.ENABLED;
                    reason = PolicyDecisionReason.of("ENABLED", "Feature is enabled in policy");
                }
                case SKIPPED -> {
                    status = PolicyDecisionStatus.DISABLED;
                    reason = PolicyDecisionReason.of("DISABLED", "Feature is disabled in policy");
                }
                case BYPASSED -> {
                    status = PolicyDecisionStatus.BYPASSED;
                    reason = PolicyDecisionReason.of("BYPASSED", "Feature bypassed by request context");
                }
                default -> throw new IllegalStateException("Unknown status: " + step.expectedStatus());
            }
            decisions.add(PolicyDecision.of(step.stepId(), status, reason));
        }
        return decisions;
    }

}
