package com.example.magrathea.storageengine.application.service;

import com.example.magrathea.storageengine.application.port.AlterationPort;
import com.example.magrathea.storageengine.application.port.ChecksumPort;
import com.example.magrathea.storageengine.application.port.ChunkStorePort;
import com.example.magrathea.storageengine.application.port.ContentAddressIndex;
import com.example.magrathea.storageengine.application.port.DataTransformPort;
import com.example.magrathea.storageengine.application.port.DiskSetCatalog;
import com.example.magrathea.storageengine.application.port.ECOutcome;
import com.example.magrathea.storageengine.application.port.ObjectManifestRepository;
import com.example.magrathea.storageengine.application.port.StoragePolicyCatalog;
import com.example.magrathea.storageengine.application.port.StoredObjectRepository;
import com.example.magrathea.storageengine.domain.aggregate.StoredObject;
import com.example.magrathea.storageengine.domain.service.CompleteUploadService;
import com.example.magrathea.storageengine.domain.service.EffectivePolicyResolver;
import com.example.magrathea.storageengine.domain.service.PersistencePlanner;
import com.example.magrathea.storageengine.domain.service.VirtualDeviceResolver;
import com.example.magrathea.storageengine.domain.valueobject.*;
import org.junit.jupiter.api.Test;
import org.springframework.core.io.buffer.DefaultDataBufferFactory;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.ArrayList;
import java.util.HexFormat;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;

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
        private final InMemoryChunkStore chunkStore = new InMemoryChunkStore();
        private final InMemoryManifestRepository manifestRepository = new InMemoryManifestRepository();
        private final ReactiveStorageOrchestrator orchestrator;

        private Fixture(StoragePolicy policy) {
            this.orchestrator = new ReactiveStorageOrchestrator(
                    new CompleteUploadService(),
                    new SinglePolicyCatalog(policy),
                    new EffectivePolicyResolver(),
                    new VirtualDeviceResolver(),
                    new PersistencePlanner(),
                    new DigestChecksumPort(),
                    new NoOpDataTransformPort(),
                    new InMemoryContentAddressIndex(),
                    null,
                    chunkStore,
                    new InMemoryStoredObjectRepository(),
                    manifestRepository);
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

    private static final class NoOpDataTransformPort implements DataTransformPort {
        @Override
        public byte[] compress(byte[] data, CompressionConfig config) {
            return data;
        }

        @Override
        public byte[] encrypt(byte[] data, EncryptionConfig config) {
            return data;
        }

        @Override
        public Mono<ECOutcome> erasureEncode(byte[] data, ErasureCodingConfig config) {
            return Mono.just(new ECOutcome(List.of(), List.of(), data));
        }

        @Override
        public Mono<List<NodeId>> replicate(byte[] data, int factor) {
            return Mono.just(List.of(NodeId.of("node-1")));
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

    private static final class InMemoryChunkStore implements ChunkStorePort {
        private final Map<ChunkId, byte[]> storedData = new LinkedHashMap<>();
        private final AtomicInteger writes = new AtomicInteger();

        @Override
        public Mono<List<NodeId>> store(ChunkId chunkId, byte[] data, PersistencePlan plan) {
            storedData.put(chunkId, data.clone());
            writes.incrementAndGet();
            return Mono.just(List.of(NodeId.of("node-1")));
        }

        @Override
        public Mono<byte[]> read(ChunkId chunkId) {
            return Mono.just(storedData.get(chunkId).clone());
        }

        private int writeCount() {
            return writes.get();
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

        @Override
        public Mono<Void> save(ObjectManifest manifest) {
            savedById.put(manifest.manifestId(), manifest);
            return Mono.empty();
        }

        @Override
        public Mono<ObjectManifest> findBy(ManifestId manifestId) {
            return Mono.just(savedById.get(manifestId));
        }
    }
}
