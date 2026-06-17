package com.example.magrathea.storageengine.application.service;

import com.example.magrathea.storageengine.application.port.ChecksumPort;
import com.example.magrathea.storageengine.application.port.ChunkStorePort;
import com.example.magrathea.storageengine.application.port.ContentAddressIndex;
import com.example.magrathea.storageengine.application.port.DiskSetCatalog;
import com.example.magrathea.storageengine.application.port.ObjectManifestRepository;
import com.example.magrathea.storageengine.application.port.StoragePolicyCatalog;
import com.example.magrathea.storageengine.application.port.StoredObjectRepository;
import com.example.magrathea.storageengine.application.pipeline.DataProcessingPipeline;
import com.example.magrathea.storageengine.application.pipeline.DataProcessingStep;
import com.example.magrathea.storageengine.application.pipeline.DataProcessingPipelinePort;
import com.example.magrathea.storageengine.application.pipeline.DeduplicationStep;
import com.example.magrathea.storageengine.domain.pipeline.StepSpec;
import com.example.magrathea.storageengine.application.pipeline.StorageEvent;
import com.example.magrathea.storageengine.application.pipeline.StorageEventPublisher;
import com.example.magrathea.storageengine.application.pipeline.StorageEventType;
import com.example.magrathea.storageengine.application.pipeline.StorePort;
import com.example.magrathea.storageengine.application.pipeline.StorageTrace;
import com.example.magrathea.storageengine.application.pipeline.StorageUnit;
import com.example.magrathea.storageengine.domain.aggregate.StoredObject;
import com.example.magrathea.storageengine.domain.service.CompleteUploadService;
import com.example.magrathea.storageengine.domain.service.EffectivePolicyResolver;
import com.example.magrathea.storageengine.domain.service.PersistencePlanner;
import com.example.magrathea.storageengine.domain.service.VirtualDeviceResolver;
import com.example.magrathea.storageengine.domain.valueobject.*;
import org.junit.jupiter.api.Test;
import org.springframework.core.io.buffer.DataBuffer;
import org.springframework.core.io.buffer.DataBufferUtils;
import org.springframework.core.io.buffer.DefaultDataBufferFactory;
import org.reactivestreams.Publisher;
import org.reactivestreams.Subscriber;
import org.reactivestreams.Subscription;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import reactor.core.publisher.Sinks;
import reactor.test.StepVerifier;
import reactor.test.publisher.TestPublisher;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.function.Function;
import java.util.HexFormat;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.BooleanSupplier;

import static org.assertj.core.api.Assertions.assertThat;

class ReactiveStorageOrchestratorTest {

    private static final DefaultDataBufferFactory BUFFER_FACTORY = new DefaultDataBufferFactory();
    private static final StorageClassId STORAGE_CLASS = StorageClassId.of("TEST");

    @Test
    void storesSingleChunkObjectAndManifestReferencesPersistedChunkId() {
        Fixture fixture = new Fixture(StoragePolicy.minimal(STORAGE_CLASS));
        byte[] data = "single chunk payload".getBytes(StandardCharsets.UTF_8);

        StoredObject stored = fixture.orchestrator.store(command("bucket", "object", data.length), body(data)).block();

        ObjectManifest manifest = fixture.manifestRepository.savedById.get(stored.manifestId());
        assertThat(manifest.chunks()).hasSize(1);
        ChunkId chunkId = manifest.chunks().getFirst().chunkId();
        assertThat(fixture.chunkStore.storedData).containsKey(chunkId);

        StepVerifier.create(fixture.orchestrator.read(stored.manifestId()).reduce(this::concat))
                .assertNext(actual -> assertThat(actual).isEqualTo(data))
                .verifyComplete();
    }

    @Test
    void storesMultiChunkObjectWithDeterministicOrderedReferences() {
        Fixture fixture = new Fixture(dedupPolicy(4096));
        byte[] data = repeatedBytes(9000);

        StoredObject stored = fixture.orchestrator.store(command("bucket", "multi", data.length), body(data)).block();

        ObjectManifest manifest = fixture.manifestRepository.savedById.get(stored.manifestId());
        assertThat(manifest.chunks()).extracting(ChunkReferenceDescriptor::originalSize)
                .containsExactly(4096L, 4096L, 808L);
        assertThat(manifest.chunks()).extracting(ChunkReferenceDescriptor::chunkId).doesNotHaveDuplicates();

        StepVerifier.create(fixture.orchestrator.read(stored.manifestId()).reduce(this::concat))
                .assertNext(actual -> assertThat(actual).isEqualTo(data))
                .verifyComplete();
    }

    @Test
    void duplicateContentWithDedupReusesExistingChunkAndSkipsDuplicateWrite() {
        Fixture fixture = new Fixture(dedupPolicy(4096));
        byte[] data = repeatedBytes(4096);

        StoredObject first = fixture.orchestrator.store(command("bucket", "first", data.length), body(data)).block();
        StoredObject second = fixture.orchestrator.store(command("bucket", "second", data.length), body(data)).block();

        ObjectManifest firstManifest = fixture.manifestRepository.savedById.get(first.manifestId());
        ObjectManifest secondManifest = fixture.manifestRepository.savedById.get(second.manifestId());
        assertThat(secondManifest.chunks().getFirst().chunkId()).isEqualTo(firstManifest.chunks().getFirst().chunkId());
        assertThat(fixture.chunkStore.writeCount()).isEqualTo(1);

        StepVerifier.create(fixture.orchestrator.read(second.manifestId()).reduce(this::concat))
                .assertNext(actual -> assertThat(actual).isEqualTo(data))
                .verifyComplete();
    }

    @Test
    void nonDedupPolicyPersistsDuplicatesAndCanReadBack() {
        Fixture fixture = new Fixture(StoragePolicy.minimal(STORAGE_CLASS));
        byte[] data = repeatedBytes(4096);

        StoredObject first = fixture.orchestrator.store(command("bucket", "first", data.length), body(data)).block();
        StoredObject second = fixture.orchestrator.store(command("bucket", "second", data.length), body(data)).block();

        ObjectManifest firstManifest = fixture.manifestRepository.savedById.get(first.manifestId());
        ObjectManifest secondManifest = fixture.manifestRepository.savedById.get(second.manifestId());
        assertThat(secondManifest.chunks().getFirst().chunkId()).isNotEqualTo(firstManifest.chunks().getFirst().chunkId());
        assertThat(fixture.chunkStore.writeCount()).isEqualTo(2);

        StepVerifier.create(fixture.orchestrator.read(second.manifestId()).reduce(this::concat))
                .assertNext(actual -> assertThat(actual).isEqualTo(data))
                .verifyComplete();
    }

    @Test
    void reqPipeline001WritePipelineRecordsRequiredStageOrder() {
        Fixture fixture = new Fixture(dedupPolicy(4096));
        byte[] data = "Hello durable Magrathea!".getBytes(StandardCharsets.UTF_8);

        StoredObject stored = fixture.orchestrator.store(
                command("pipeline-stage-order-bucket", "pipeline/2026/write/stage-order-object.txt", data.length),
                body(data)).block();

        assertThat(fixture.orchestrator.writePipelineStages())
                .extracting(stage -> stage.name())
                .containsExactly(
                        "validation",
                        "policy-resolution",
                        "chunking",
                        "dedup-lookup",
                        "chunk-persistence",
                        "manifest-persistence",
                        "object-index-persistence");
        assertThat(successfulStageNames(fixture.events.events(), "WRITE"))
                .containsExactly(
                        "validation",
                        "policy-resolution",
                        "chunking",
                        "dedup-lookup",
                        "chunk-persistence",
                        "manifest-persistence",
                        "object-index-persistence");
        ObjectManifest manifest = fixture.manifestRepository.savedById.get(stored.manifestId());
        assertThat(manifest.chunks()).isNotEmpty();
        assertThat(fixture.storedObjectRepository.objects).hasSize(1);
    }

    @Test
    void reqPipeline002WritePipelinePersistsChunksWithBoundedDemandAndNoWholeObjectAggregation() {
        int chunkSize = 4096;
        byte[] data = repeatedBytes(chunkSize * 5);
        List<byte[]> sourceBuffers = splitIntoBuffers(data, chunkSize);
        DemandControlledDataBufferPublisher body = new DemandControlledDataBufferPublisher(sourceBuffers);
        GatedInMemoryChunkStore gatedChunkStore = new GatedInMemoryChunkStore();
        Fixture fixture = new Fixture(dedupPolicy(chunkSize), gatedChunkStore);
        AtomicReference<StoredObject> storedRef = new AtomicReference<>();

        StepVerifier.create(fixture.orchestrator.store(
                        command("pipeline-backpressure-bucket", "pipeline/2026/write/large-streamed-object.bin", data.length),
                        Flux.from(body)))
                .then(() -> {
                    awaitUntil(() -> gatedChunkStore.storeInvocations() == 1);
                    assertThat(gatedChunkStore.inFlight()).isEqualTo(1);
                    assertThat(gatedChunkStore.maxInFlight()).isEqualTo(1);
                    assertThat(body.emittedChunks()).isLessThan((long) sourceBuffers.size());

                    for (int completed = 0; completed < sourceBuffers.size(); completed++) {
                        gatedChunkStore.completeStore(completed);
                        if (completed + 1 < sourceBuffers.size()) {
                            int expectedStoreInvocations = completed + 2;
                            awaitUntil(() -> gatedChunkStore.storeInvocations() == expectedStoreInvocations);
                            assertThat(gatedChunkStore.inFlight()).isEqualTo(1);
                            assertThat(gatedChunkStore.maxInFlight()).isEqualTo(1);
                        }
                    }
                })
                .assertNext(stored -> {
                    storedRef.set(stored);
                    ObjectManifest manifest = fixture.manifestRepository.savedById.get(stored.manifestId());
                    assertThat(manifest.chunkCount()).isEqualTo(sourceBuffers.size());
                    assertThat(manifest.totalOriginalSize()).isEqualTo(data.length);
                    assertThat(manifest.chunks()).extracting(ChunkReferenceDescriptor::originalSize)
                            .containsExactly(sourceBuffers.stream().map(chunk -> (long) chunk.length).toArray(Long[]::new));
                    assertThat(gatedChunkStore.storedData.values())
                            .allSatisfy(storedChunk -> assertThat(storedChunk).hasSizeLessThanOrEqualTo(chunkSize));
                    assertThat(gatedChunkStore.storedData.values())
                            .noneSatisfy(storedChunk -> assertThat(storedChunk).isEqualTo(data));
                })
                .verifyComplete();

        StepVerifier.create(fixture.orchestrator.read(storedRef.get().manifestId()).reduce(this::concat))
                .assertNext(actual -> assertThat(actual).isEqualTo(data))
                .verifyComplete();
        assertThat(gatedChunkStore.maxInFlight()).isEqualTo(1);
    }

    @Test
    void reqPipeline003ReadPipelineUsesManifestChunkOrder() {
        Fixture fixture = new Fixture(dedupPolicy(4096));
        byte[] data = repeatedBytes(9000);
        StoredObject stored = fixture.orchestrator.store(command("pipeline-read-order-bucket", "pipeline/2026/read/manifest-ordered-object.bin", data.length), body(data)).block();
        ObjectManifest manifest = fixture.manifestRepository.savedById.get(stored.manifestId());
        fixture.events.clear();
        fixture.chunkStore.readOrder.clear();

        StepVerifier.create(fixture.orchestrator.read(stored.manifestId()).reduce(this::concat))
                .assertNext(actual -> assertThat(actual).isEqualTo(data))
                .verifyComplete();

        assertThat(successfulStageNames(fixture.events.events(), "READ"))
                .containsExactly("validation", "policy-resolution", "read-planning", "chunk-reading", "response-streaming");
        assertThat(fixture.chunkStore.readOrder)
                .containsExactlyElementsOf(manifest.chunks().stream().map(ChunkReferenceDescriptor::chunkId).toList());
    }

    @Test
    void reqPipeline004FailureEmitsFailureAndCleanupEventsAndStopsLaterStages() {
        Fixture fixture = new Fixture(dedupPolicy(4096));
        fixture.manifestRepository.failSavesWith = new IllegalStateException("simulated manifest fault");
        byte[] data = repeatedBytes(4096);

        StepVerifier.create(fixture.orchestrator.store(
                        command("pipeline-failure-bucket", "pipeline/2026/failure/stage-failure-object.bin", data.length),
                        body(data)))
                .expectErrorMessage("simulated manifest fault")
                .verify();

        List<StorageEvent> events = fixture.events.events();
        assertThat(events.stream()
                .filter(event -> event.type() == StorageEventType.STAGE_FAILED)
                .map(StorageEvent::stageName)
                .toList()).containsExactly("manifest-persistence");
        assertThat(successfulStageNames(events, "WRITE")).doesNotContain("object-index-persistence");
        assertThat(events.stream().map(StorageEvent::type).toList()).contains(StorageEventType.CLEANUP_COMPLETED);
        assertThat(fixture.storedObjectRepository.objects).isEmpty();
        assertThat(fixture.manifestRepository.savedById).isEmpty();
    }

    @Test
    void reqPipeline005CancellationEmitsCancelAndCleanupEvents() {
        Fixture fixture = new Fixture(dedupPolicy(4096));
        TestPublisher<org.springframework.core.io.buffer.DataBuffer> publisher = TestPublisher.create();

        StepVerifier.create(fixture.orchestrator.store(
                        command("pipeline-cancellation-bucket", "pipeline/2026/cancel/cancelled-object.bin", 8192),
                        publisher.flux()))
                .then(() -> publisher.next(BUFFER_FACTORY.wrap(repeatedBytes(8192))))
                .thenCancel()
                .verify();

        awaitUntil(() -> fixture.events.events().stream()
                .anyMatch(event -> event.type() == StorageEventType.STAGE_CANCELLED));
        assertThat(fixture.events.events().stream().map(StorageEvent::type).toList())
                .contains(StorageEventType.STAGE_CANCELLED, StorageEventType.CLEANUP_COMPLETED);
        assertThat(fixture.storedObjectRepository.objects).isEmpty();
    }

    @Test
    void phase4SuccessfulPipelineEventsIncludeCorrelationStageTimingAndMeasurements() {
        Fixture fixture = new Fixture(dedupPolicy(4096));
        byte[] data = repeatedBytes(5000);

        fixture.orchestrator.store(
                command("observability-success-bucket", "observability/2026/success-object.bin", data.length),
                body(data)).block();

        StorageEvent chunkPersistence = fixture.events.events().stream()
                .filter(event -> event.type() == StorageEventType.STAGE_SUCCEEDED)
                .filter(event -> event.stageName().equals("chunk-persistence"))
                .findFirst()
                .orElseThrow();
        assertThat(chunkPersistence.correlationId()).isNotBlank();
        assertThat(chunkPersistence.duration()).isPresent();
        assertThat(chunkPersistence.measurements().bytesWritten()).isEqualTo(data.length);
        assertThat(chunkPersistence.measurements().chunks()).isEqualTo(2);
        assertThat(chunkPersistence.measurements().dedupMisses()).isEqualTo(2);

        StorageEvent manifestPersistence = fixture.events.events().stream()
                .filter(event -> event.type() == StorageEventType.STAGE_SUCCEEDED)
                .filter(event -> event.stageName().equals("manifest-persistence"))
                .findFirst()
                .orElseThrow();
        assertThat(manifestPersistence.manifestId()).isPresent();
        assertThat(manifestPersistence.measurements().manifests()).isEqualTo(1);
    }

    @Test
    void phase4FailedPipelineEventsIncludeFailureMeasurementAndStageTiming() {
        Fixture fixture = new Fixture(dedupPolicy(4096));
        fixture.manifestRepository.failSavesWith = new IllegalStateException("simulated manifest fault");

        StepVerifier.create(fixture.orchestrator.store(
                        command("observability-failure-bucket", "observability/2026/failure-object.bin", 4096),
                        body(repeatedBytes(4096))))
                .expectErrorMessage("simulated manifest fault")
                .verify();

        StorageEvent failed = fixture.events.events().stream()
                .filter(event -> event.type() == StorageEventType.STAGE_FAILED)
                .findFirst()
                .orElseThrow();
        assertThat(failed.stageName()).isEqualTo("manifest-persistence");
        assertThat(failed.duration()).isPresent();
        assertThat(failed.measurements().failures()).isEqualTo(1);
        assertThat(failed.correlationId()).isNotBlank();
    }

    @Test
    void reqPipeline006EventsContainStageTypeCorrelationAndObjectIdentity() {
        Fixture fixture = new Fixture(dedupPolicy(4096));
        byte[] data = "Hello durable Magrathea!".getBytes(StandardCharsets.UTF_8);

        fixture.orchestrator.store(
                command("pipeline-instrumentation-bucket", "pipeline/2026/events/instrumented-object.txt", data.length),
                body(data)).block();

        List<StorageEvent> chunkPersistenceEvents = fixture.events.events().stream()
                .filter(event -> event.stageName().equals("chunk-persistence"))
                .toList();
        assertThat(chunkPersistenceEvents).isNotEmpty();
        assertThat(chunkPersistenceEvents)
                .allSatisfy(event -> {
                    assertThat(event.type()).isNotNull();
                    assertThat(event.operation().name()).isEqualTo("WRITE");
                    assertThat(event.correlationId()).isNotBlank();
                    assertThat(event.bucket()).contains("pipeline-instrumentation-bucket");
                    assertThat(event.objectKey()).contains("pipeline/2026/events/instrumented-object.txt");
                    assertThat(event.occurredAt()).isNotNull();
                });
        assertThat(chunkPersistenceEvents.stream().map(StorageEvent::correlationId).distinct().toList()).hasSize(1);
        assertThat(chunkPersistenceEvents.stream().map(event -> event.outcome().orElse("none")).toList())
                .doesNotContain(new String(data, StandardCharsets.UTF_8));
    }

    private List<String> successfulStageNames(List<StorageEvent> events, String operation) {
        return events.stream()
                .filter(event -> event.type() == StorageEventType.STAGE_SUCCEEDED)
                .filter(event -> event.operation().name().equals(operation))
                .map(StorageEvent::stageName)
                .toList();
    }

    private void awaitUntil(BooleanSupplier condition) {
        long deadline = System.nanoTime() + 2_000_000_000L;
        while (!condition.getAsBoolean() && System.nanoTime() < deadline) {
            try {
                Thread.sleep(10);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                throw new AssertionError(e);
            }
        }
        assertThat(condition.getAsBoolean()).isTrue();
    }

    private CompleteUploadCommand command(String bucketName, String key, long size) {
        BucketRef bucket = BucketRef.of(BucketId.of(bucketName), bucketName);
        UploadRequestContext context = UploadRequestContext.of(
                ObjectKey.of(bucketName, key),
                bucket,
                STORAGE_CLASS,
                ObjectContentDescriptor.of("application/octet-stream", size),
                ObjectMetadataDescriptor.empty(),
                EncryptionRequest.none(),
                Optional.empty());
        return new CompleteUploadCommand(context, UploadMode.SINGLE_OBJECT, Optional.empty());
    }

    private Flux<org.springframework.core.io.buffer.DataBuffer> body(byte[] data) {
        return Flux.just(BUFFER_FACTORY.wrap(data));
    }

    private byte[] repeatedBytes(int size) {
        byte[] data = new byte[size];
        for (int i = 0; i < size; i++) {
            data[i] = (byte) ('A' + (i % 26));
        }
        return data;
    }

    private List<byte[]> splitIntoBuffers(byte[] data, int size) {
        List<byte[]> buffers = new ArrayList<>();
        for (int offset = 0; offset < data.length; offset += size) {
            buffers.add(Arrays.copyOfRange(data, offset, Math.min(data.length, offset + size)));
        }
        return buffers;
    }

    private byte[] concat(byte[] left, byte[] right) {
        byte[] combined = new byte[left.length + right.length];
        System.arraycopy(left, 0, combined, 0, left.length);
        System.arraycopy(right, 0, combined, left.length, right.length);
        return combined;
    }

    private StoragePolicy dedupPolicy(long chunkSize) {
        return StoragePolicy.of(
                STORAGE_CLASS,
                Optional.of(DedupConfig.of(DedupScope.BUCKET_LEVEL, FingerprintAlgorithm.SHA256, chunkSize, ChunkAlignment.NONE)),
                Optional.empty(),
                Optional.empty(),
                Optional.empty(),
                ReplicationConfig.of(1));
    }

    private static final class Fixture {
        private final InMemoryChunkStore chunkStore;
        private final InMemoryStoredObjectRepository storedObjectRepository = new InMemoryStoredObjectRepository();
        private final InMemoryManifestRepository manifestRepository = new InMemoryManifestRepository();
        private final CapturingStorageEventPublisher events = new CapturingStorageEventPublisher();
        private final ReactiveStorageOrchestrator orchestrator;

        private Fixture(StoragePolicy policy) {
            this(policy, new InMemoryChunkStore());
        }

        private Fixture(StoragePolicy policy, InMemoryChunkStore chunkStore) {
            this.chunkStore = chunkStore;
            InMemoryContentAddressIndex addressIndex = new InMemoryContentAddressIndex();
            DataProcessingPipelinePort pipelinePort = spec -> {
                List<DataProcessingStep> steps = new ArrayList<>();
                for (var stepSpec : spec.steps()) {
                    DataProcessingStep step = switch (stepSpec) {
                        case StepSpec.Dedup(var config) -> {
                            int cs = Math.toIntExact(config.chunkSize());
                            yield new TestDedupStep(addressIndex, cs);
                        }
                        default -> new NoOpStep();
                    };
                    steps.add(step);
                }
                return new DataProcessingPipeline(steps, chunkStore);
            };
            this.orchestrator = new ReactiveStorageOrchestrator(
                    new CompleteUploadService(),
                    new SinglePolicyCatalog(policy),
                    new EffectivePolicyResolver(),
                    new VirtualDeviceResolver(),
                    new PersistencePlanner(),
                    new DigestChecksumPort(),
                    addressIndex,
                    chunkStore,
                    storedObjectRepository,
                    manifestRepository,
                    65536,
                    events,
                    pipelinePort);
        }
    }

    private static final class CapturingStorageEventPublisher implements StorageEventPublisher {
        private final List<StorageEvent> events = new CopyOnWriteArrayList<>();

        @Override
        public Mono<Void> publish(StorageEvent event) {
            events.add(event);
            return Mono.empty();
        }

        private List<StorageEvent> events() {
            return List.copyOf(events);
        }

        private void clear() {
            events.clear();
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

    private static final class DigestChecksumPort implements ChecksumPort {
        @Override
        public Fingerprint fingerprint(byte[] data, FingerprintAlgorithm algorithm) {
            return Fingerprint.of(algorithm, sha256(data));
        }

        @Override
        public ContentHash calculate(byte[] data, ChecksumAlgorithm algorithm) {
            return ContentHash.of(algorithm, sha256(data));
        }

        @Override
        public boolean verify(byte[] data, ContentHash expected) {
            return calculate(data, expected.algorithm()).equals(expected);
        }

        private static String sha256(byte[] data) {
            try {
                return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(data));
            } catch (Exception e) {
                throw new IllegalStateException(e);
            }
        }
    }



    private static final class InMemoryContentAddressIndex implements ContentAddressIndex {
        private final Map<String, ChunkReferenceDescriptor> index = new ConcurrentHashMap<>();

        @Override
        public Mono<Optional<ChunkReferenceDescriptor>> find(DeviceConfigurationHash deviceHash, Fingerprint fingerprint) {
            return Mono.just(Optional.ofNullable(index.get(key(deviceHash, fingerprint))));
        }

        @Override
        public Mono<Void> record(DeviceConfigurationHash deviceHash, Fingerprint fingerprint, ChunkId chunkId) {
            ContentHash hash = ContentHash.of(ChecksumAlgorithm.SHA256, fingerprint.value());
            index.putIfAbsent(key(deviceHash, fingerprint), new ChunkReferenceDescriptor(
                    chunkId, fingerprint, 0, 0, List.of(), hash, List.of(NodeId.of("node-1"))));
            return Mono.empty();
        }

        private String key(DeviceConfigurationHash deviceHash, Fingerprint fingerprint) {
            return deviceHash.value() + ":" + fingerprint.value();
        }
    }

    private static class InMemoryChunkStore implements ChunkStorePort, StorePort {
        protected final Map<ChunkId, byte[]> storedData = new LinkedHashMap<>();
        private final List<ChunkId> readOrder = new CopyOnWriteArrayList<>();
        private final AtomicInteger writes = new AtomicInteger();

        @Override
        public Mono<List<NodeId>> store(ChunkId chunkId, byte[] data, PersistencePlan plan) {
            storedData.put(chunkId, data.clone());
            writes.incrementAndGet();
            return Mono.just(List.of(NodeId.of("node-1")));
        }

        @Override
        public Mono<byte[]> read(ChunkId chunkId) {
            readOrder.add(chunkId);
            return Mono.just(storedData.get(chunkId).clone());
        }

        /** StorePort implementation used by the DataProcessingPipeline path. */
        @Override
        public Mono<StorageTrace> write(StorageUnit unit) {
            // Handle deduplicated ChunkUnit — no data to write
            if (unit instanceof StorageUnit.ChunkUnit cu && cu.deduplicatedReuse()) {
                Fingerprint fp = cu.fingerprint()
                        .orElseThrow(() -> new IllegalStateException(
                                "ChunkUnit with deduplicatedReuse must have a fingerprint"));
                return Mono.just(new StorageTrace(
                        cu.info(), "chunk",
                        Optional.of(fp),
                        Optional.empty(),
                        true,
                        0L,
                        0L));
            }
            ChunkId chunkId = ChunkId.generate();
            return DataBufferUtils.join(unit.data())
                    .map(buffer -> {
                        byte[] bytes = new byte[buffer.readableByteCount()];
                        buffer.read(bytes);
                        DataBufferUtils.release(buffer);
                        storedData.put(chunkId, bytes.clone());
                        writes.incrementAndGet();
                        try {
                            byte[] hashBytes = MessageDigest.getInstance("SHA-256").digest(bytes);
                            String hexHash = HexFormat.of().formatHex(hashBytes);
                            Fingerprint fingerprint = Fingerprint.of(FingerprintAlgorithm.SHA256, hexHash);
                            return new StorageTrace(
                                    unit.info(), "file", Optional.of(fingerprint),
                                    Optional.of(chunkId.value().toString()),
                                    false, bytes.length, bytes.length);
                        } catch (java.security.NoSuchAlgorithmException e) {
                            throw new RuntimeException(e);
                        }
                    });
        }

        private int writeCount() {
            return writes.get();
        }
    }

    private static final class GatedInMemoryChunkStore extends InMemoryChunkStore {
        private final List<Sinks.Empty<Void>> completions = new CopyOnWriteArrayList<>();
        private final AtomicInteger inFlight = new AtomicInteger();
        private final AtomicInteger maxInFlight = new AtomicInteger();
        private final AtomicInteger storeInvocations = new AtomicInteger();

        @Override
        public Mono<List<NodeId>> store(ChunkId chunkId, byte[] data, PersistencePlan plan) {
            return Mono.defer(() -> {
                int active = inFlight.incrementAndGet();
                maxInFlight.accumulateAndGet(active, Math::max);
                storeInvocations.incrementAndGet();
                Sinks.Empty<Void> completion = Sinks.empty();
                completions.add(completion);
                return super.store(chunkId, data, plan)
                        .then(completion.asMono()
                                .doOnSuccess(ignored -> inFlight.decrementAndGet()))
                        .thenReturn(List.of(NodeId.of("node-1")));
            });
        }

        @Override
        public Mono<StorageTrace> write(StorageUnit unit) {
            // Handle deduplicated ChunkUnit
            if (unit instanceof StorageUnit.ChunkUnit cu && cu.deduplicatedReuse()) {
                Fingerprint fp = cu.fingerprint()
                        .orElseThrow(() -> new IllegalStateException(
                                "ChunkUnit with deduplicatedReuse must have a fingerprint"));
                return Mono.just(new StorageTrace(
                        cu.info(), "chunk",
                        Optional.of(fp),
                        Optional.empty(),
                        true,
                        0L,
                        0L));
            }
            return Mono.defer(() -> {
                int active = inFlight.incrementAndGet();
                maxInFlight.accumulateAndGet(active, Math::max);
                storeInvocations.incrementAndGet();
                Sinks.Empty<Void> completion = Sinks.empty();
                completions.add(completion);
                ChunkId chunkId = ChunkId.generate();
                return DataBufferUtils.join(unit.data())
                        .flatMap(buffer -> {
                            byte[] bytes = new byte[buffer.readableByteCount()];
                            buffer.read(bytes);
                            DataBufferUtils.release(buffer);
                            storedData.put(chunkId, bytes.clone());
                            try {
                                byte[] hashBytes = MessageDigest.getInstance("SHA-256").digest(bytes);
                                String hexHash = HexFormat.of().formatHex(hashBytes);
                                Fingerprint fingerprint = Fingerprint.of(FingerprintAlgorithm.SHA256, hexHash);
                                StorageTrace trace = new StorageTrace(
                                        unit.info(), "file", Optional.of(fingerprint),
                                        Optional.of(chunkId.value().toString()),
                                        false, bytes.length, bytes.length);
                                return completion.asMono()
                                        .doOnSuccess(ignored -> inFlight.decrementAndGet())
                                        .then(Mono.just(trace));
                            } catch (java.security.NoSuchAlgorithmException e) {
                                throw new RuntimeException(e);
                            }
                        });
            });
        }

        private int inFlight() {
            return inFlight.get();
        }

        private int maxInFlight() {
            return maxInFlight.get();
        }

        private int storeInvocations() {
            return storeInvocations.get();
        }

        private void completeStore(int zeroBasedIndex) {
            assertThat(completions).hasSizeGreaterThan(zeroBasedIndex);
            assertThat(completions.get(zeroBasedIndex).tryEmitEmpty()).isEqualTo(Sinks.EmitResult.OK);
        }
    }

    private static final class DemandControlledDataBufferPublisher implements Publisher<org.springframework.core.io.buffer.DataBuffer> {
        private final List<byte[]> buffers;
        private final AtomicLong requested = new AtomicLong();
        private final AtomicLong emitted = new AtomicLong();
        private Subscriber<? super org.springframework.core.io.buffer.DataBuffer> subscriber;
        private boolean completed;
        private boolean cancelled;

        private DemandControlledDataBufferPublisher(List<byte[]> buffers) {
            this.buffers = buffers.stream()
                    .map(byte[]::clone)
                    .toList();
        }

        @Override
        public synchronized void subscribe(Subscriber<? super org.springframework.core.io.buffer.DataBuffer> subscriber) {
            if (this.subscriber != null) {
                subscriber.onError(new IllegalStateException("Only one subscriber is supported"));
                return;
            }
            this.subscriber = subscriber;
            subscriber.onSubscribe(new Subscription() {
                @Override
                public void request(long n) {
                    requestMore(n);
                }

                @Override
                public void cancel() {
                    cancelSubscription();
                }
            });
        }

        private synchronized void requestMore(long n) {
            if (cancelled || completed) {
                return;
            }
            if (n <= 0) {
                completed = true;
                subscriber.onError(new IllegalArgumentException("Demand must be positive"));
                return;
            }
            requested.addAndGet(n);
            drain();
        }

        private void drain() {
            while (!cancelled && !completed && requested.get() > 0 && emitted.get() < buffers.size()) {
                requested.decrementAndGet();
                int index = Math.toIntExact(emitted.getAndIncrement());
                subscriber.onNext(BUFFER_FACTORY.wrap(buffers.get(index).clone()));
            }
            if (!cancelled && !completed && emitted.get() == buffers.size()) {
                completed = true;
                subscriber.onComplete();
            }
        }

        private synchronized void cancelSubscription() {
            cancelled = true;
        }

        private long emittedChunks() {
            return emitted.get();
        }
    }

    private static final class InMemoryStoredObjectRepository implements StoredObjectRepository {
        private final List<StoredObject> objects = new ArrayList<>();

        @Override
        public Mono<Void> save(StoredObject storedObject) {
            objects.add(storedObject);
            return Mono.empty();
        }

        @Override
        public Mono<StoredObject> findBy(ObjectId objectId, VersionId versionId) {
            return Flux.fromIterable(objects)
                    .filter(object -> object.objectId().equals(objectId) && object.versionId().equals(versionId))
                    .next();
        }
    }

    private static final class InMemoryManifestRepository implements ObjectManifestRepository {
        private final Map<ManifestId, ObjectManifest> savedById = new LinkedHashMap<>();
        private RuntimeException failSavesWith;

        @Override
        public Mono<Void> save(ObjectManifest manifest) {
            if (failSavesWith != null) {
                return Mono.error(failSavesWith);
            }
            savedById.put(manifest.manifestId(), manifest);
            return Mono.empty();
        }

        @Override
        public Mono<ObjectManifest> findBy(ManifestId manifestId) {
            return Mono.just(savedById.get(manifestId));
        }
    }

    /**
     * Test deduplication step that windows data into fixed-size chunks,
     * fingerprints each window via SHA-256, and looks up in ContentAddressIndex.
     */
    private static final class TestDedupStep implements DeduplicationStep {
        private static final int DEFAULT_CHUNK_SIZE = 1_048_576;
        private static final DefaultDataBufferFactory BUF_FACTORY = new DefaultDataBufferFactory();

        private final ContentAddressIndex contentAddressIndex;
        private final int chunkSize;

        private TestDedupStep(ContentAddressIndex contentAddressIndex) {
            this(contentAddressIndex, DEFAULT_CHUNK_SIZE);
        }

        private TestDedupStep(ContentAddressIndex contentAddressIndex, int chunkSize) {
            this.contentAddressIndex = contentAddressIndex;
            this.chunkSize = chunkSize;
        }

        @Override
        public Publisher<StorageUnit> apply(StorageUnit unit) {
            if (unit instanceof StorageUnit.FileUnit fileUnit) {
                return windowAndDedup(fileUnit);
            }
            return Mono.just(unit);
        }

        /**
         * Processes each DataBuffer individually: extracts bytes, splits into
         * chunkSize windows, fingerprints each window, and looks up in the index.
         * Windows are emitted in order with sequential index across all buffers.
         */
        private Publisher<StorageUnit> windowAndDedup(StorageUnit.FileUnit fileUnit) {
            DeviceConfigurationHash deviceHash = fileUnit.info().deviceHash()
                    .orElseThrow(() -> new IllegalArgumentException(
                            "deviceHash required for dedup"));
            // Track global window index across all buffers
            AtomicInteger globalIndex = new AtomicInteger(0);
            return Flux.from(fileUnit.data())
                    .concatMap(buffer -> {
                        byte[] bufBytes = new byte[buffer.readableByteCount()];
                        buffer.read(bufBytes);
                        // Don't release buffer — DataBufferUtils.write() may need it.
                        // But we consumed the data, so release is safe.
                        DataBufferUtils.release(buffer);
                        return splitBufferIntoWindows(bufBytes, fileUnit, deviceHash, globalIndex);
                    });
        }

        private Publisher<StorageUnit> splitBufferIntoWindows(
                byte[] bufBytes, StorageUnit.FileUnit fileUnit,
                DeviceConfigurationHash deviceHash, AtomicInteger globalIndex) {
            int offset = 0;
            List<Mono<StorageUnit>> windows = new ArrayList<>();
            while (offset < bufBytes.length) {
                int len = Math.min(chunkSize, bufBytes.length - offset);
                byte[] windowBytes = new byte[len];
                System.arraycopy(bufBytes, offset, windowBytes, 0, len);
                String hex = sha256Hex(windowBytes);
                Fingerprint fingerprint = Fingerprint.of(FingerprintAlgorithm.SHA256, hex);
                int currentIndex = globalIndex.getAndIncrement();
                windows.add(contentAddressIndex.find(deviceHash, fingerprint)
                        .map(optDescriptor -> {
                            if (optDescriptor.isPresent()) {
                                return (StorageUnit) new StorageUnit.ChunkUnit(
                                        Flux.empty(),
                                        fileUnit.info(),
                                        currentIndex,
                                        Optional.of(fingerprint),
                                        true);
                            } else {
                                Flux<DataBuffer> chunkData = Flux.just(
                                        BUF_FACTORY.wrap(windowBytes));
                                return new StorageUnit.ChunkUnit(
                                        chunkData,
                                        fileUnit.info(),
                                        currentIndex,
                                        Optional.of(fingerprint),
                                        false);
                            }
                        }));
                offset += len;
            }
            return Flux.fromIterable(windows).concatMap(Function.identity(), 1);
        }

        private static String sha256Hex(byte[] data) {
            try {
                MessageDigest md = MessageDigest.getInstance("SHA-256");
                return HexFormat.of().formatHex(md.digest(data));
            } catch (NoSuchAlgorithmException e) {
                throw new RuntimeException("SHA-256 not available", e);
            }
        }
    }

    /** Pass-through step for transforms that are not enabled in the policy. */
    private static final class NoOpStep implements DataProcessingStep {
        @Override
        public Publisher<StorageUnit> apply(StorageUnit unit) {
            return Mono.just(unit);
        }
    }
}
