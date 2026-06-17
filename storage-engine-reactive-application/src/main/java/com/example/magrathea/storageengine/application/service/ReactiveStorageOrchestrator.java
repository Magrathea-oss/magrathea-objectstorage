package com.example.magrathea.storageengine.application.service;

import com.example.magrathea.storageengine.application.port.ChecksumPort;
import com.example.magrathea.storageengine.application.port.ChunkStorePort;
import com.example.magrathea.storageengine.application.port.ContentAddressIndex;
import com.example.magrathea.storageengine.application.port.ObjectManifestRepository;
import com.example.magrathea.storageengine.application.port.StoragePolicyCatalog;
import com.example.magrathea.storageengine.application.port.StoredObjectRepository;
import com.example.magrathea.storageengine.application.pipeline.DataProcessingPipeline;
import com.example.magrathea.storageengine.application.pipeline.DataProcessingPipelinePort;
import com.example.magrathea.storageengine.application.pipeline.StorageCleanupHandle;
import com.example.magrathea.storageengine.application.pipeline.StorageContext;
import com.example.magrathea.storageengine.application.pipeline.StorageEventMeasurements;
import com.example.magrathea.storageengine.application.pipeline.StorageEventPublisher;
import com.example.magrathea.storageengine.application.pipeline.StorageOperation;
import com.example.magrathea.storageengine.application.pipeline.StoragePipelineExecutor;
import com.example.magrathea.storageengine.application.pipeline.StorageStage;
import com.example.magrathea.storageengine.application.pipeline.StorageTrace;
import com.example.magrathea.storageengine.application.pipeline.StorageUnit;
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
import com.example.magrathea.storageengine.domain.pipeline.DataProcessingSpec;
import com.example.magrathea.storageengine.domain.pipeline.StepSpec;
import com.example.magrathea.storageengine.domain.valueobject.DedupConfig;
import com.example.magrathea.storageengine.domain.valueobject.EffectiveStoragePolicy;
import com.example.magrathea.storageengine.domain.valueobject.EncryptionConfig;
import com.example.magrathea.storageengine.domain.valueobject.Fingerprint;
import com.example.magrathea.storageengine.domain.valueobject.FingerprintAlgorithm;
import com.example.magrathea.storageengine.domain.valueobject.ManifestId;
import com.example.magrathea.storageengine.domain.valueobject.ObjectId;
import com.example.magrathea.storageengine.domain.valueobject.ObjectManifest;
import com.example.magrathea.storageengine.domain.valueobject.PersistencePlan;
import com.example.magrathea.storageengine.domain.valueobject.PolicyDecision;
import com.example.magrathea.storageengine.domain.valueobject.PolicyDecisionReason;
import com.example.magrathea.storageengine.domain.valueobject.PolicyDecisionStatus;
import com.example.magrathea.storageengine.domain.valueobject.StepChecksumDescriptor;
import com.example.magrathea.storageengine.domain.valueobject.StepExecutionRecord;
import com.example.magrathea.storageengine.domain.valueobject.StepExecutionStatus;
import com.example.magrathea.storageengine.domain.valueobject.StepConfig;
import com.example.magrathea.storageengine.domain.valueobject.StepPlan;
import com.example.magrathea.storageengine.domain.valueobject.StepId;
import com.example.magrathea.storageengine.domain.valueobject.StepOutcome;
import com.example.magrathea.storageengine.domain.valueobject.UploadCompletionTrace;
import com.example.magrathea.storageengine.domain.valueobject.VersionId;
import com.example.magrathea.storageengine.domain.valueobject.VirtualDevice;
import com.example.magrathea.storageengine.domain.valueobject.StorageUnitInfo;
import org.springframework.core.io.buffer.DataBuffer;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.function.Function;

/**
 * Reactive application service orchestrating the full object storage pipeline.
 * <p>
 * All uploads are routed through the DataProcessingPipeline which handles both
 * dedup-enabled and non-dedup policies via configured DataProcessingStep implementations.
 * <p>
 * Pipeline steps per chunk (executed by DataProcessingPipeline):
 * 1. DEDUP — fingerprint + content-address lookup (when enabled)
 * 2. COMPRESS — compress data (when enabled)
 * 3. CRYPT — encrypt data (when enabled)
 * 4. ERASURE_CODING — erasure encode data (when enabled)
 * 5. REPLICATION — replicate data across nodes (when enabled)
 * 6. STORE — persist data to storage nodes (always)
 */
public class ReactiveStorageOrchestrator {

    private final CompleteUploadService completeUploadService;
    private final StoragePolicyCatalog storagePolicyCatalog;
    private final EffectivePolicyResolver effectivePolicyResolver;
    private final VirtualDeviceResolver virtualDeviceResolver;
    private final PersistencePlanner persistencePlanner;
    private final ChecksumPort checksumPort;
    private final ContentAddressIndex contentAddressIndex;
    private final ChunkStorePort chunkStorePort;
    private final StoredObjectRepository storedObjectRepository;
    private final ObjectManifestRepository objectManifestRepository;
    private final StorageEventPublisher storageEventPublisher;
    private final StoragePipelineExecutor pipelineExecutor;
    /** Required pipeline port for the DataProcessingPipeline. */
    private final DataProcessingPipelinePort dataPipelinePort;

    private final int defaultChunkSizeBytes;

    public ReactiveStorageOrchestrator(
            CompleteUploadService completeUploadService,
            StoragePolicyCatalog storagePolicyCatalog,
            EffectivePolicyResolver effectivePolicyResolver,
            VirtualDeviceResolver virtualDeviceResolver,
            PersistencePlanner persistencePlanner,
            ChecksumPort checksumPort,
            ContentAddressIndex contentAddressIndex,
            ChunkStorePort chunkStorePort,
            StoredObjectRepository storedObjectRepository,
            ObjectManifestRepository objectManifestRepository,
            int defaultChunkSizeBytes,
            StorageEventPublisher eventPublisher,
            DataProcessingPipelinePort dataPipelinePort) {
        this.completeUploadService = completeUploadService;
        this.storagePolicyCatalog = storagePolicyCatalog;
        this.effectivePolicyResolver = effectivePolicyResolver;
        this.virtualDeviceResolver = virtualDeviceResolver;
        this.persistencePlanner = persistencePlanner;
        this.checksumPort = checksumPort;
        this.contentAddressIndex = contentAddressIndex;
        this.chunkStorePort = chunkStorePort;
        this.storedObjectRepository = storedObjectRepository;
        this.objectManifestRepository = objectManifestRepository;
        this.storageEventPublisher = eventPublisher;
        this.defaultChunkSizeBytes = defaultChunkSizeBytes;
        this.dataPipelinePort = dataPipelinePort;
        this.pipelineExecutor = new StoragePipelineExecutor(eventPublisher);
    }

    /**
     * Stores an object by completing the upload, resolving policy, planning persistence,
     * chunking the data, running the per-chunk persistence pipeline, building the manifest,
     * and publishing the aggregate through explicit reactive stages.
     *
     * @param command the complete upload command
     * @param data    the raw object data as a flux of DataBuffers
     * @return the stored StoredObject
     */
    public Mono<StoredObject> store(CompleteUploadCommand command, Flux<DataBuffer> data) {
        return pipelineExecutor.execute(StorageContext.write(command, data), writePipelineStages())
                .map(context -> context.storedObject()
                        .orElseThrow(() -> new IllegalStateException("Write pipeline completed without a stored object")));
    }

    /**
     * Reads the content for a persisted manifest by assembling a staged read pipeline and
     * streaming referenced chunks in manifest order.
     */
    public Flux<byte[]> read(ManifestId manifestId) {
        return pipelineExecutor.execute(StorageContext.read(manifestId), readPipelineStages())
                .flatMapMany(context -> context.responseContent()
                        .orElseThrow(() -> new IllegalStateException("Read pipeline completed without response content")));
    }

    List<StorageStage> writePipelineStages() {
        return List.of(
                stage("validation", StorageOperation.WRITE, this::validateWrite),
                stage("policy-resolution", StorageOperation.WRITE, this::resolveWritePolicy),
                stage("chunking", StorageOperation.WRITE, this::prepareWriteChunks),
                stage("dedup-lookup", StorageOperation.WRITE, this::prepareDedupLookup),
                stage("chunk-persistence", StorageOperation.WRITE, this::persistChunks),
                stage("manifest-persistence", StorageOperation.WRITE, this::persistManifest),
                stage("object-index-persistence", StorageOperation.WRITE, this::persistObjectIndex));
    }

    List<StorageStage> readPipelineStages() {
        return List.of(
                stage("validation", StorageOperation.READ, this::validateRead),
                stage("policy-resolution", StorageOperation.READ, this::resolveReadPolicy),
                stage("read-planning", StorageOperation.READ, this::planRead),
                stage("chunk-reading", StorageOperation.READ, this::prepareChunkReading),
                stage("response-streaming", StorageOperation.READ, this::prepareResponseStreaming));
    }

    private StorageStage stage(
            String name,
            StorageOperation operation,
            Function<StorageContext, Mono<StorageContext>> action) {
        return new NamedStorageStage(name, operation, action);
    }

    private Mono<StorageContext> validateWrite(StorageContext context) {
        return Mono.fromCallable(() -> context
                .withUploadTrace(completeUploadService.complete(context.command().orElseThrow()))
                .withStageDecision("validation", "complete-upload-accepted"));
    }

    private Mono<StorageContext> resolveWritePolicy(StorageContext context) {
        CompleteUploadCommand command = context.command().orElseThrow();
        return storagePolicyCatalog.findBy(command.context().storageClassId())
                .map(policy -> {
                    var effectivePolicy = effectivePolicyResolver.resolve(policy, command.context());
                    VirtualDevice device = virtualDeviceResolver.resolve(effectivePolicy, command.context().bucket());
                    PersistencePlan plan = persistencePlanner.createPlan(effectivePolicy, device);
                    boolean dedupEnabled = plan.steps().get(0).expectedStatus() == StepExecutionStatus.EXECUTED;
                    long chunkSize = dedupEnabled
                            ? plan.effectivePolicy().dedup().map(DedupConfig::chunkSize).orElse((long) defaultChunkSizeBytes)
                            : defaultChunkSizeBytes;
                    return context.withPolicyDecision(effectivePolicy, device, plan, Math.toIntExact(chunkSize))
                            .withStageDecision("policy-resolution", "chunk-size-bytes=" + chunkSize);
                });
    }

    private Mono<StorageContext> prepareWriteChunks(StorageContext context) {
        // All uploads are routed through DataProcessingPipeline.
        // The pipeline handles both dedup-enabled and non-dedup policies via
        // configured DataProcessingStep implementations (DeduplicationStep, etc.).
        return Mono.just(context
                .withStageDecision("chunking", "streaming-pipeline")
                .addCleanupHandle(StorageCleanupHandle.named("chunking", Mono.empty())));
    }

    private Mono<StorageContext> prepareDedupLookup(StorageContext context) {
        return Mono.just(context.withStageDecision(
                "dedup-lookup",
                "per-chunk lookup is evaluated lazily during chunk-persistence to preserve streaming demand"));
    }

    private Mono<StorageContext> persistChunks(StorageContext context) {
        PersistencePlan plan = context.plan().orElseThrow();
        // All uploads go through DataProcessingPipeline (both dedup and non-dedup).
        return persistChunksViaPipeline(context, plan);
    }

    /**
     * Chunk persistence via DataProcessingPipeline.
     * The object is streamed as a single FileUnit through the configured processing steps
     * (including DeduplicationStep when dedup is enabled) and written to the store.
     * <p>
     * For dedup-enabled policies, the pipeline produces multiple ChunkUnit instances —
     * one per window — each resulting in a separate StorageTrace. For non-dedup policies,
     * the pipeline produces a single StorageTrace for the whole FileUnit.
     * <p>
     * New fingerprints are recorded in the content address index after storing.
     */
    private Mono<StorageContext> persistChunksViaPipeline(
            StorageContext context, PersistencePlan plan) {
        EffectiveStoragePolicy effectivePolicy = plan.effectivePolicy();
        DataProcessingSpec spec = buildDataProcessingSpec(effectivePolicy);
        DataProcessingPipeline pipeline = dataPipelinePort.build(spec);

        CompleteUploadCommand command = context.command().orElseThrow();
        long contentLength = command.context().contentDescriptor().objectSize();
        StorageUnitInfo info = StorageUnitInfo.of(
                command.context(), contentLength, plan.deviceHash());
        StorageUnit.FileUnit fileUnit = new StorageUnit.FileUnit(
                context.uploadData().orElseThrow(), info);

        return pipeline.execute(fileUnit)
                .concatMap(trace -> {
                    // For dedup reuse traces, retrieve the existing chunkId from the index
                    if (trace.deduplicatedReuse() && trace.fingerprint().isPresent()) {
                        Fingerprint fp = trace.fingerprint().get();
                        return contentAddressIndex.find(plan.deviceHash(), fp)
                                .map(optDescriptor -> {
                                    ChunkReferenceDescriptor existing = optDescriptor
                                            .orElseThrow(() -> new IllegalStateException(
                                                    "Dedup reuse but fingerprint not found in index"));
                                    ChunkPersistenceTrace chunkTrace = toChunkPersistenceTrace(
                                            trace, plan, existing.chunkId());
                                    return chunkTrace;
                                });
                    }
                    // For new chunks, convert trace and record in content address index
                    ChunkPersistenceTrace chunkTrace = toChunkPersistenceTrace(trace, plan);
                    if (trace.fingerprint().isPresent() && trace.storageRef().isPresent()) {
                        Fingerprint fp = trace.fingerprint().get();
                        ChunkId chunkId = ChunkId.of(
                                UUID.fromString(trace.storageRef().get()));
                        return contentAddressIndex.record(plan.deviceHash(), fp, chunkId)
                                .then(Mono.just(chunkTrace));
                    }
                    return Mono.just(chunkTrace);
                })
                .collectList()
                .map(traces -> context
                        .withChunkTraces(traces)
                        .withStageDecision("chunk-persistence",
                                "chunk-trace-count=" + traces.size())
                        .addCleanupHandle(
                                StorageCleanupHandle.named("chunk-persistence", Mono.empty())));
    }

    /**
     * Builds a DataProcessingSpec from the effective policy.
     * Includes all processing steps that are enabled in the policy.
     * Fixed order: Dedup → Compress → Encrypt → EC.
     */
    private static DataProcessingSpec buildDataProcessingSpec(EffectiveStoragePolicy effectivePolicy) {
        List<StepSpec> steps = new ArrayList<>();
        effectivePolicy.dedup().ifPresent(d ->
                steps.add(new StepSpec.Dedup(d)));
        effectivePolicy.compression().ifPresent(c ->
                steps.add(new StepSpec.Compress(c)));
        effectivePolicy.encryption().ifPresent(ep ->
                steps.add(new StepSpec.Encrypt(
                        new EncryptionConfig(ep.algorithm(), ep.defaultKeyReference()))));
        effectivePolicy.erasureCoding().ifPresent(ec ->
                steps.add(new StepSpec.EC(ec)));
        return new DataProcessingSpec(steps);
    }

    /**
     * Converts a StorageTrace produced by the DataProcessingPipeline into a
     * ChunkPersistenceTrace compatible with buildDescriptors() and persistManifest().
     * <p>
     * For FileUnit traces (non-dedup path), the trace carries a fingerprint set by
     * StorePort. For ChunkUnit traces (dedup path), the fingerprint is set by the
     * DeduplicationStep and passed through by StorePort.
     * <p>
     * All NoOp transform steps share the same checksum (data is unmodified).
     * REPLICATION and STORE are always EXECUTED per PersistencePlan.create().
     */
    /**
     * Converts a StorageTrace to ChunkPersistenceTrace, using the given chunkId
     * for dedup reuse traces (retrieved from the content address index).
     */
    private ChunkPersistenceTrace toChunkPersistenceTrace(
            StorageTrace storageTrace, PersistencePlan plan) {
        return toChunkPersistenceTrace(storageTrace, plan, null);
    }

    private ChunkPersistenceTrace toChunkPersistenceTrace(
            StorageTrace storageTrace, PersistencePlan plan, ChunkId existingChunkId) {
        // Extract fingerprint from trace — present for both FileUnit and ChunkUnit
        Fingerprint fingerprint = storageTrace.fingerprint()
                .orElseGet(() -> Fingerprint.of(FingerprintAlgorithm.SHA256,
                        checksumPort.calculate(new byte[0], ChecksumAlgorithm.SHA256).value()));
        ContentHash contentHash = ContentHash.of(ChecksumAlgorithm.SHA256, fingerprint.value());

        // Build chunkId: use existingChunkId for dedup reuse, or storageRef for new chunks
        ChunkId chunkId;
        if (existingChunkId != null) {
            chunkId = existingChunkId;
        } else if (storageTrace.storageRef().isPresent()) {
            chunkId = ChunkId.of(UUID.fromString(storageTrace.storageRef().get()));
        } else {
            chunkId = ChunkId.generate();
        }

        List<StepExecutionRecord> steps = new ArrayList<>();
        for (StepPlan stepPlan : plan.steps()) {
            StepId stepId = stepPlan.stepId();
            StepExecutionStatus expectedStatus = stepPlan.expectedStatus();

            if (storageTrace.deduplicatedReuse() && stepId == StepId.DEDUP) {
                // Dedup hit: emit DedupOutcome with matched=true
                steps.add(new StepExecutionRecord(
                        StepId.DEDUP, StepExecutionStatus.EXECUTED,
                        Optional.of(new StepOutcome.DedupOutcome(true, Optional.of(chunkId))),
                        Optional.of(ChecksumCalculationResult.of(contentHash)),
                        Optional.of(AlterationResult.notApplied()),
                        Optional.of(ChecksumVerificationResult.verified()),
                        Optional.empty(),
                        Optional.of(contentHash),
                        Optional.of(contentHash)));
                continue;
            }

            if (storageTrace.deduplicatedReuse()) {
                // For deduplicated chunks, all subsequent steps are synthesised
                // without actual data transformation.
                if (expectedStatus == StepExecutionStatus.EXECUTED) {
                    steps.add(buildExecutedStep(stepId,
                            noOpOutcomeForDedupReuse(stepPlan, plan),
                            contentHash, contentHash));
                } else {
                    steps.add(buildSkippedStep(stepId, contentHash));
                }
                continue;
            }

            if (stepId == StepId.STORE) {
                // STORE is always EXECUTED; storedSize from the trace.
                steps.add(buildExecutedStep(
                        StepId.STORE,
                        new StepOutcome.StoreOutcome(
                                plan.targetDevice(),
                                List.of(),
                                storageTrace.storedSize()),
                        contentHash, contentHash));
            } else if (expectedStatus == StepExecutionStatus.EXECUTED) {
                // EXECUTED step: synthesise a NoOp outcome (pipeline handles real transforms).
                steps.add(buildExecutedStep(stepId,
                        noOpPipelineOutcomeFor(stepPlan, plan),
                        contentHash, contentHash));
            } else {
                // SKIPPED step (DEDUP, COMPRESS, CRYPT, ERASURE_CODING for a minimal policy).
                steps.add(buildSkippedStep(stepId, contentHash));
            }
        }

        return new ChunkPersistenceTrace(chunkId, fingerprint, storageTrace.originalSize(), steps);
    }

    /**
     * Synthesises a NoOp StepOutcome for EXECUTED steps in the pipeline path.
     * Data is unmodified (all pipeline steps are currently NoOp), so sizes and locations
     * carry zero/empty values.
     */
    private StepOutcome noOpPipelineOutcomeFor(StepPlan stepPlan, PersistencePlan plan) {
        return switch (stepPlan.stepId()) {
            case REPLICATION -> {
                int factor = stepPlan.config()
                        .map(c -> ((StepConfig.ReplicationStepConfig) c).config().factor())
                        .orElse(1);
                yield new StepOutcome.ReplicationOutcome(factor, List.of());
            }
            case COMPRESS -> new StepOutcome.CompressOutcome(
                    com.example.magrathea.storageengine.domain.valueobject.CompressionAlgorithm.GZIP, 0, 0);
            case CRYPT -> new StepOutcome.CryptOutcome(
                    com.example.magrathea.storageengine.domain.valueobject.EncryptionAlgorithm.SSE_S3,
                    Optional.empty());
            case ERASURE_CODING -> {
                var ecConfig = stepPlan.config()
                        .map(c -> ((StepConfig.ECStepConfig) c).config())
                        .orElseThrow();
                yield new StepOutcome.ErasureCodingOutcome(
                        ecConfig.dataBlocks(), ecConfig.parityBlocks(), List.of(), List.of());
            }
            case DEDUP -> new StepOutcome.DedupOutcome(false, Optional.empty());
            case STORE -> new StepOutcome.StoreOutcome(plan.targetDevice(), List.of(), 0);
        };
    }

    /**
     * Synthesises a NoOp StepOutcome for EXECUTED steps when a chunk is deduplicated.
     * Locations from the existing chunk are reused for REPLICATION and STORE.
     */
    private StepOutcome noOpOutcomeForDedupReuse(StepPlan stepPlan, PersistencePlan plan) {
        return switch (stepPlan.stepId()) {
            case COMPRESS -> new StepOutcome.CompressOutcome(
                    com.example.magrathea.storageengine.domain.valueobject.CompressionAlgorithm.GZIP, 0, 0);
            case CRYPT -> new StepOutcome.CryptOutcome(
                    com.example.magrathea.storageengine.domain.valueobject.EncryptionAlgorithm.SSE_S3,
                    Optional.empty());
            case ERASURE_CODING -> {
                var ecConfig = stepPlan.config()
                        .map(c -> ((StepConfig.ECStepConfig) c).config())
                        .orElseThrow();
                yield new StepOutcome.ErasureCodingOutcome(
                        ecConfig.dataBlocks(), ecConfig.parityBlocks(), List.of(), List.of());
            }
            case REPLICATION -> {
                int factor = stepPlan.config()
                        .map(c -> ((StepConfig.ReplicationStepConfig) c).config())
                        .orElseThrow().factor();
                yield new StepOutcome.ReplicationOutcome(factor, List.of());
            }
            case STORE -> new StepOutcome.StoreOutcome(plan.targetDevice(), List.of(), 0);
            case DEDUP -> new StepOutcome.DedupOutcome(true, Optional.empty());
        };
    }

    private Mono<StorageContext> persistManifest(StorageContext context) {
        return Mono.fromCallable(() -> {
                    List<ChunkReferenceDescriptor> descriptors = buildDescriptors(context.chunkTraces());
                    long totalOriginalSize = descriptors.stream()
                            .mapToLong(ChunkReferenceDescriptor::originalSize)
                            .sum();
                    long totalStoredSize = descriptors.stream()
                            .mapToLong(ChunkReferenceDescriptor::storedSize)
                            .sum();

                    List<PolicyDecision> policyDecisions = buildPolicyDecisions(context.plan().orElseThrow());
                    ManifestId manifestId = ManifestId.generate();
                    CompleteUploadCommand command = context.command().orElseThrow();
                    ObjectId objectId = ObjectId.of(
                            command.context().objectKey().bucket() + "/" + command.context().objectKey().key());
                    VersionId versionId = VersionId.of(java.util.UUID.randomUUID().toString());
                    ObjectManifest manifest = new ObjectManifest(
                            manifestId,
                            objectId,
                            versionId,
                            command.context().storageClassId(),
                            context.device().orElseThrow(),
                            context.plan().orElseThrow().deviceHash(),
                            context.uploadTrace().orElseThrow(),
                            policyDecisions,
                            descriptors.size(),
                            totalOriginalSize,
                            totalStoredSize,
                            descriptors);
                    persistencePlanner.validateManifest(manifest, context.plan().orElseThrow());

                    StoredObject storedObject = StoredObject.create(
                            objectId,
                            versionId,
                            command.context().bucket(),
                            command.context().storageClassId(),
                            context.device().orElseThrow());
                    storedObject.attachManifest(manifestId);

                    return context
                            .withChunkDescriptors(descriptors)
                            .withManifestIdentity(manifestId, objectId, versionId)
                            .withManifest(manifest)
                            .withStoredObject(storedObject);
                })
                .flatMap(prepared -> objectManifestRepository.save(prepared.manifest().orElseThrow())
                        .thenReturn(prepared
                                .withStageDecision("manifest-persistence", "manifest-committed")
                                .addCleanupHandle(StorageCleanupHandle.named("manifest-persistence", Mono.empty()))));
    }

    private Mono<StorageContext> persistObjectIndex(StorageContext context) {
        return storedObjectRepository.save(context.storedObject().orElseThrow())
                .thenReturn(context.withStageDecision("object-index-persistence", "object-reference-committed"));
    }

    private Mono<StorageContext> validateRead(StorageContext context) {
        return Mono.just(context.withStageDecision("validation", "manifest-id-accepted"));
    }

    private Mono<StorageContext> resolveReadPolicy(StorageContext context) {
        return Mono.just(context.withStageDecision(
                "policy-resolution",
                "read policy is derived from committed manifest during read-planning"));
    }

    private Mono<StorageContext> planRead(StorageContext context) {
        return objectManifestRepository.findBy(context.requestedManifestId().orElseThrow())
                .map(manifest -> context
                        .withManifest(manifest)
                        .withChunkDescriptors(manifest.chunks())
                        .withStageDecision("read-planning", "manifest-chunk-count=" + manifest.chunks().size()));
    }

    private Mono<StorageContext> prepareChunkReading(StorageContext context) {
        Flux<byte[]> readableChunks = Flux.fromIterable(context.chunkDescriptors())
                .concatMap(chunk -> chunkStorePort.read(chunk.chunkId())
                        .map(storedData -> restoreReadableChunk(storedData, chunk)), 1);
        return Mono.just(context
                .withResponseContent(readableChunks)
                .withStageDecision("chunk-reading", "manifest-order"));
    }

    private Mono<StorageContext> prepareResponseStreaming(StorageContext context) {
        Flux<byte[]> observedResponse = context.responseContent().orElseThrow()
                .onErrorResume(error -> storageEventPublisher.publish(lazyReadFailed(context, error))
                        .thenMany(Flux.error(error)));
        return Mono.just(context
                .withResponseContent(observedResponse)
                .withStageDecision("response-streaming", "deferred-flux"));
    }

    private static Optional<String> manifestIdValue(StorageContext context) {
        return context.manifestId().map(manifestId -> manifestId.value().toString());
    }

    private static com.example.magrathea.storageengine.application.pipeline.StorageEvent.StageFailed lazyReadFailed(
            StorageContext context,
            Throwable error) {
        return new com.example.magrathea.storageengine.application.pipeline.StorageEvent.StageFailed(
                context.operation(),
                context.correlationId(),
                "chunk-reading",
                context.bucketName(),
                context.objectKey(),
                manifestIdValue(context),
                java.time.Instant.now(),
                java.time.Duration.ZERO,
                error.getMessage() == null ? error.getClass().getSimpleName() : error.getMessage(),
                StorageEventMeasurements.failure());
    }

    private record NamedStorageStage(
            String name,
            StorageOperation operation,
            Function<StorageContext, Mono<StorageContext>> action) implements StorageStage {
        @Override
        public Mono<StorageContext> execute(StorageContext context) {
            return action.apply(context);
        }
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
