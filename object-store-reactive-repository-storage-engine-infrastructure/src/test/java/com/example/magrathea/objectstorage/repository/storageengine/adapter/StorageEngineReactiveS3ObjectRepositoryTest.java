package com.example.magrathea.objectstorage.repository.storageengine.adapter;

import com.example.magrathea.objectstore.domain.aggregate.S3Object;
import com.example.magrathea.objectstore.domain.valueobject.ChecksumAlgorithm;
import com.example.magrathea.objectstore.domain.valueobject.ChecksumValue;
import com.example.magrathea.objectstore.domain.valueobject.ObjectChecksum;
import com.example.magrathea.objectstore.domain.valueobject.ObjectKey;
import com.example.magrathea.objectstorage.repository.storageengine.acl.ObjectStoreToStorageEngineTranslator;
import com.example.magrathea.storageengine.application.pipeline.StorageEventPublisher;
import com.example.magrathea.storageengine.application.port.StoragePolicyCatalog;
import com.example.magrathea.storageengine.application.service.ReactiveStorageOrchestrator;
import com.example.magrathea.storageengine.infrastructure.pipeline.DataProcessingPipelineFactory;
import com.example.magrathea.storageengine.infrastructure.pipeline.FileSystemStorePort;
import com.example.magrathea.storageengine.infrastructure.pipeline.NoOpCompressionStep;
import com.example.magrathea.storageengine.infrastructure.pipeline.NoOpDeduplicationStep;
import com.example.magrathea.storageengine.infrastructure.pipeline.NoOpEncryptionStep;
import com.example.magrathea.storageengine.infrastructure.pipeline.NoOpErasureCodingStep;
import com.example.magrathea.storageengine.domain.aggregate.StoredObject;
import com.example.magrathea.storageengine.domain.service.CompleteUploadService;
import com.example.magrathea.storageengine.domain.service.EffectivePolicyResolver;
import com.example.magrathea.storageengine.domain.service.PersistencePlanner;
import com.example.magrathea.storageengine.domain.service.VirtualDeviceResolver;
import com.example.magrathea.storageengine.domain.valueobject.CompleteUploadCommand;
import com.example.magrathea.storageengine.domain.valueobject.ManifestId;
import com.example.magrathea.storageengine.domain.valueobject.ObjectId;
import com.example.magrathea.storageengine.domain.valueobject.ReplicationConfig;
import com.example.magrathea.storageengine.domain.valueobject.StorageClassId;
import com.example.magrathea.storageengine.domain.valueobject.StoragePolicy;
import com.example.magrathea.storageengine.domain.valueobject.VersionId;
import com.example.magrathea.storageengine.infrastructure.filesystem.AesGcmEncryptionAdapter;
import com.example.magrathea.storageengine.infrastructure.filesystem.FileSystemChunkStorePort;
import com.example.magrathea.storageengine.infrastructure.filesystem.FileSystemDataTransformPort;
import com.example.magrathea.storageengine.infrastructure.filesystem.FileSystemStorageCluster;
import com.example.magrathea.storageengine.infrastructure.filesystem.NoOpAlterationPort;
import com.example.magrathea.storageengine.infrastructure.filesystem.ReedSolomonECAdapter;
import com.example.magrathea.storageengine.infrastructure.filesystem.Sha256ChecksumPort;
import com.example.magrathea.storageengine.infrastructure.filesystem.SimpleReplicationAdapter;
import com.example.magrathea.storageengine.infrastructure.filesystem.ZstdCompressionAdapter;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.core.io.buffer.DataBuffer;
import org.springframework.core.io.buffer.DataBufferUtils;
import org.springframework.core.io.buffer.DefaultDataBufferFactory;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;

import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;

class StorageEngineReactiveS3ObjectRepositoryTest {

    private static final DefaultDataBufferFactory BUFFER_FACTORY = new DefaultDataBufferFactory();

    @TempDir
    Path tempDir;

    @Test
    void saveWithContentDefaultsMissingStorageClassToStandard() {
        FakeOrchestrator orchestrator = new FakeOrchestrator();
        StorageEngineReactiveS3ObjectRepository repository = new StorageEngineReactiveS3ObjectRepository(
                new ObjectStoreToStorageEngineTranslator(), orchestrator);
        ObjectKey key = ObjectKey.of("bucket", "missing-storage-class");
        byte[] content = "default storage class".getBytes(StandardCharsets.UTF_8);
        S3Object object = S3Object.createPending(key, null, Map.of(), null);

        StepVerifier.create(repository.saveWithContent(object, Flux.just(BUFFER_FACTORY.wrap(content)), null))
                .assertNext(result -> assertEquals("STANDARD", result.aggregate().storageClass()))
                .verifyComplete();

        assertEquals("STANDARD", orchestrator.lastStorageClassId);
    }

    @Test
    void saveWithContentDefaultsBlankStorageClassToStandard() {
        FakeOrchestrator orchestrator = new FakeOrchestrator();
        StorageEngineReactiveS3ObjectRepository repository = new StorageEngineReactiveS3ObjectRepository(
                new ObjectStoreToStorageEngineTranslator(), orchestrator);
        ObjectKey key = ObjectKey.of("bucket", "blank-storage-class");
        byte[] content = "blank storage class".getBytes(StandardCharsets.UTF_8);
        S3Object object = S3Object.createPending(key, "   ", Map.of(), null);

        StepVerifier.create(repository.saveWithContent(object, Flux.just(BUFFER_FACTORY.wrap(content)), "  "))
                .assertNext(result -> assertEquals("STANDARD", result.aggregate().storageClass()))
                .verifyComplete();

        assertEquals("STANDARD", orchestrator.lastStorageClassId);
    }

    @Test
    void saveWithContentPreservesExplicitStorageClass() {
        FakeOrchestrator orchestrator = new FakeOrchestrator();
        StorageEngineReactiveS3ObjectRepository repository = new StorageEngineReactiveS3ObjectRepository(
                new ObjectStoreToStorageEngineTranslator(), orchestrator);
        ObjectKey key = ObjectKey.of("bucket", "explicit-storage-class");
        byte[] content = "explicit storage class".getBytes(StandardCharsets.UTF_8);
        S3Object object = S3Object.createPending(key, null, Map.of(), null);

        StepVerifier.create(repository.saveWithContent(object, Flux.just(BUFFER_FACTORY.wrap(content)), "STANDARD_IA"))
                .assertNext(result -> assertEquals("STANDARD_IA", result.aggregate().storageClass()))
                .verifyComplete();

        assertEquals("STANDARD_IA", orchestrator.lastStorageClassId);
    }

    @Test
    void storageEngineRepositoryReadAfterWriteSurvivesNewRepositoryAndPreservesSlashContainingKey() {
        Path storageRoot = tempDir.resolve("storage-engine");
        StorageEngineReactiveS3ObjectRepository writer = realFilesystemRepository(storageRoot);
        ObjectKey key = ObjectKey.of("bucket", "reports/2026/06/summary.txt");
        byte[] content = "storage-engine adapter read-through across repository restart"
                .getBytes(StandardCharsets.UTF_8);
        S3Object object = S3Object.create(
                key,
                "TEST",
                Map.of("purpose", "read-after-write"),
                null,
                ObjectChecksum.of(Set.of(new ChecksumValue(ChecksumAlgorithm.SHA256, "declared-test-checksum"))),
                content.length);

        StepVerifier.create(writer.saveWithContent(object, Flux.just(BUFFER_FACTORY.wrap(content)), "TEST"))
                .assertNext(result -> {
                    assertEquals(content.length, result.aggregate().size());
                    assertEquals("reports/2026/06/summary.txt", result.aggregate().key().key());
                    assertEquals("read-after-write", result.aggregate().userMetadata().get("purpose"));
                })
                .verifyComplete();

        StorageEngineReactiveS3ObjectRepository reader = realFilesystemRepository(storageRoot);

        StepVerifier.create(reader.findByBucketAndKey(key))
                .assertNext(restored -> {
                    assertEquals(content.length, restored.size());
                    assertEquals("reports/2026/06/summary.txt", restored.key().key());
                    assertEquals("read-after-write", restored.userMetadata().get("purpose"));
                })
                .verifyComplete();

        StepVerifier.create(reader.getContent(key).reduce(new byte[0], this::append))
                .assertNext(actual -> assertArrayEquals(content, actual))
                .verifyComplete();
    }

    @Test
    void saveWithContentThenGetContentReturnsOriginalBytes() {
        FakeOrchestrator orchestrator = new FakeOrchestrator();
        StorageEngineReactiveS3ObjectRepository repository = new StorageEngineReactiveS3ObjectRepository(
                new ObjectStoreToStorageEngineTranslator(), orchestrator);
        ObjectKey key = ObjectKey.of("bucket", "key");
        byte[] content = "adapter read path".getBytes(StandardCharsets.UTF_8);
        S3Object object = S3Object.createPending(key, "TEST", Map.of(), null);

        StepVerifier.create(repository.saveWithContent(object, Flux.just(BUFFER_FACTORY.wrap(content)), "TEST"))
                .expectNextCount(1)
                .verifyComplete();

        StepVerifier.create(repository.getContent(key).reduce(new byte[0], this::append))
                .assertNext(actual -> assertArrayEquals(content, actual))
                .verifyComplete();
    }

    private StorageEngineReactiveS3ObjectRepository realFilesystemRepository(Path storageRoot) {
        FileSystemStorageCluster cluster = new FileSystemStorageCluster(storageRoot, 1);
        Path chunksDir = storageRoot.resolve("nodes/node-001/chunks");
        FileSystemStorePort storePort = new FileSystemStorePort(chunksDir, chunksDir);
        DataProcessingPipelineFactory pipelineFactory = new DataProcessingPipelineFactory(
                new NoOpDeduplicationStep(),
                new NoOpCompressionStep(),
                new NoOpEncryptionStep(),
                new NoOpErasureCodingStep(),
                storePort);
        ReactiveStorageOrchestrator orchestrator = new ReactiveStorageOrchestrator(
                new CompleteUploadService(),
                new SingleStoragePolicyCatalog("TEST"),
                new EffectivePolicyResolver(),
                new VirtualDeviceResolver(),
                new PersistencePlanner(),
                new Sha256ChecksumPort(),
                cluster.addressIndex(),
                new FileSystemChunkStorePort(cluster),
                cluster.storedObjectRepository(),
                cluster.manifestRepository(),
                7,
                StorageEventPublisher.noop(),
                pipelineFactory);
        return new StorageEngineReactiveS3ObjectRepository(
                new ObjectStoreToStorageEngineTranslator(), orchestrator, storageRoot.toString());
    }

    private byte[] append(byte[] accumulated, DataBuffer buffer) {
        byte[] next = new byte[buffer.readableByteCount()];
        buffer.read(next);
        DataBufferUtils.release(buffer);
        byte[] combined = new byte[accumulated.length + next.length];
        System.arraycopy(accumulated, 0, combined, 0, accumulated.length);
        System.arraycopy(next, 0, combined, accumulated.length, next.length);
        return combined;
    }

    private record SingleStoragePolicyCatalog(String storageClass) implements StoragePolicyCatalog {
        @Override
        public Mono<StoragePolicy> findById(String policyId) {
            return storageClass.equals(policyId) ? Mono.just(policy()) : Mono.empty();
        }

        @Override
        public Mono<StoragePolicy> findBy(StorageClassId id) {
            return storageClass.equals(id.value()) ? Mono.just(policy()) : Mono.empty();
        }

        @Override
        public Flux<StoragePolicy> findAll() {
            return Flux.just(policy());
        }

        private StoragePolicy policy() {
            return StoragePolicy.of(
                    StorageClassId.of(storageClass),
                    Optional.empty(),
                    Optional.empty(),
                    Optional.empty(),
                    Optional.empty(),
                    ReplicationConfig.of(1));
        }
    }

    private static final class FakeOrchestrator extends ReactiveStorageOrchestrator {
        private final Map<ManifestId, byte[]> contentByManifestId = new ConcurrentHashMap<>();
        private volatile String lastStorageClassId;

        private FakeOrchestrator() {
            super(null, null, null, null, null, null, null, null, null, null, 65536,
                    StorageEventPublisher.noop(),
                    spec -> { throw new UnsupportedOperationException("FakeOrchestrator does not use the pipeline"); });
        }

        @Override
        public Mono<StoredObject> store(CompleteUploadCommand command, Flux<DataBuffer> data) {
            lastStorageClassId = command.context().storageClassId().value();
            return data.reduce(new byte[0], (accumulated, buffer) -> {
                        byte[] next = new byte[buffer.readableByteCount()];
                        buffer.read(next);
                        DataBufferUtils.release(buffer);
                        byte[] combined = new byte[accumulated.length + next.length];
                        System.arraycopy(accumulated, 0, combined, 0, accumulated.length);
                        System.arraycopy(next, 0, combined, accumulated.length, next.length);
                        return combined;
                    })
                    .map(bytes -> {
                        ManifestId manifestId = ManifestId.generate();
                        contentByManifestId.put(manifestId, bytes);
                        StoredObject storedObject = StoredObject.create(
                                ObjectId.of(command.context().objectKey().bucket() + "/" + command.context().objectKey().key()),
                                VersionId.of("version-1"),
                                command.context().bucket(),
                                command.context().storageClassId(),
                                new com.example.magrathea.storageengine.domain.valueobject.VirtualDevice.BucketDevice(
                                        command.context().bucket(),
                                        com.example.magrathea.storageengine.domain.valueobject.EffectiveStoragePolicy.of(
                                                command.context().storageClassId(),
                                                command.context().bucket(),
                                                java.util.Optional.empty(),
                                                java.util.Optional.empty(),
                                                java.util.Optional.empty(),
                                                java.util.Optional.empty(),
                                                com.example.magrathea.storageengine.domain.valueobject.ReplicationConfig.of(1))));
                        storedObject.attachManifest(manifestId);
                        return storedObject;
                    });
        }

        @Override
        public Flux<byte[]> read(ManifestId manifestId) {
            return Flux.just(contentByManifestId.get(manifestId));
        }
    }
}
