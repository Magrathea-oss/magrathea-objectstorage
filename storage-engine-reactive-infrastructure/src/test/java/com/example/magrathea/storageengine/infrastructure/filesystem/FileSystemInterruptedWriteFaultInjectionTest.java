package com.example.magrathea.storageengine.infrastructure.filesystem;

import com.example.magrathea.storageengine.application.pipeline.StorageEventPublisher;
import com.example.magrathea.storageengine.application.port.StoragePolicyCatalog;
import com.example.magrathea.storageengine.application.service.ReactiveStorageOrchestrator;
import com.example.magrathea.storageengine.infrastructure.pipeline.DataProcessingPipelineFactory;
import com.example.magrathea.storageengine.infrastructure.pipeline.FileSystemStorePort;
import com.example.magrathea.storageengine.infrastructure.pipeline.NoOpCompressionStep;
import com.example.magrathea.storageengine.infrastructure.pipeline.NoOpDeduplicationStep;
import com.example.magrathea.storageengine.infrastructure.pipeline.NoOpEncryptionStep;
import com.example.magrathea.storageengine.infrastructure.pipeline.NoOpErasureCodingStep;
import com.example.magrathea.storageengine.domain.service.CompleteUploadService;
import com.example.magrathea.storageengine.domain.service.EffectivePolicyResolver;
import com.example.magrathea.storageengine.domain.service.PersistencePlanner;
import com.example.magrathea.storageengine.domain.service.VirtualDeviceResolver;
import com.example.magrathea.storageengine.domain.valueobject.BucketId;
import com.example.magrathea.storageengine.domain.valueobject.BucketRef;
import com.example.magrathea.storageengine.domain.valueobject.ChunkId;
import com.example.magrathea.storageengine.domain.valueobject.CompleteUploadCommand;
import com.example.magrathea.storageengine.domain.valueobject.EncryptionRequest;
import com.example.magrathea.storageengine.domain.valueobject.ManifestId;
import com.example.magrathea.storageengine.domain.valueobject.ObjectContentDescriptor;
import com.example.magrathea.storageengine.domain.valueobject.ObjectKey;
import com.example.magrathea.storageengine.domain.valueobject.ObjectMetadataDescriptor;
import com.example.magrathea.storageengine.domain.valueobject.StorageClassId;
import com.example.magrathea.storageengine.domain.valueobject.StoragePolicy;
import com.example.magrathea.storageengine.domain.valueobject.UploadMode;
import com.example.magrathea.storageengine.domain.valueobject.UploadRequestContext;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.core.io.buffer.DataBuffer;
import org.springframework.core.io.buffer.DefaultDataBufferFactory;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.NoSuchFileException;
import java.nio.file.Path;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Focused coverage for REQ-FS-001 and REQ-FS-002 interrupted filesystem writes.
 */
class FileSystemInterruptedWriteFaultInjectionTest {

    private static final DefaultDataBufferFactory BUFFER_FACTORY = new DefaultDataBufferFactory();
    private static final StorageClassId STORAGE_CLASS = StorageClassId.STANDARD;

    @TempDir
    Path storageRoot;

    @Test
    void interruptedChunkWriteLeavesNoCommittedChunkOrObjectAndRecoveryCanQuarantineTempArtifact()
            throws IOException {
        byte[] content = "Hello interrupted chunk write".getBytes(StandardCharsets.UTF_8);
        FileSystemStorageCluster cluster = new FileSystemStorageCluster(
                storageRoot,
                1,
                new PropertyControlledFileSystemWriteFaultInjector(true, false, true));
        ReactiveStorageOrchestrator orchestrator = orchestrator(cluster);

        StepVerifier.create(orchestrator.store(
                        command("fs-atomicity-chunk-bucket", "chunks/2026/atomic-write/chunk-object.bin", content.length),
                        body(content)))
                .expectError(FileSystemWriteInterruptedException.class)
                .verify();

        List<Path> tempChunks = tempFiles(storageRoot.resolve("nodes"));
        assertThat(tempChunks).hasSize(1);
        assertThat(committedChunkFiles()).isEmpty();
        assertNoCommittedObjectVisibility();

        ChunkId attemptedChunkId = chunkIdFromTempFile(tempChunks.getFirst());
        StepVerifier.create(cluster.nodes().getFirst().read(attemptedChunkId))
                .expectError(NoSuchFileException.class)
                .verify();

        FileSystemRecoveryScanner scanner = new FileSystemRecoveryScanner();
        FileSystemRecoveryScanner.ScanReport report = scanner.scan(storageRoot);
        assertThat(report.findings())
                .anySatisfy(finding -> {
                    assertThat(finding.artifactType()).isEqualTo("orphaned-chunk");
                    assertThat(finding.artifactPath()).isEqualTo(tempChunks.getFirst().toString());
                });

        scanner.quarantine(storageRoot, report);
        assertThat(Files.exists(tempChunks.getFirst())).isFalse();
        assertThat(tempFiles(storageRoot.resolve("nodes"))).isEmpty();
    }

    @Test
    void interruptedManifestWriteLeavesNoCommittedManifestOrObjectAndRecoveryCanQuarantineTempArtifact()
            throws IOException {
        byte[] content = "Hello interrupted manifest write".getBytes(StandardCharsets.UTF_8);
        FileSystemStorageCluster cluster = new FileSystemStorageCluster(
                storageRoot,
                1,
                new PropertyControlledFileSystemWriteFaultInjector(false, true, true));
        ReactiveStorageOrchestrator orchestrator = orchestrator(cluster);

        StepVerifier.create(orchestrator.store(
                        command("fs-atomicity-manifest-bucket", "manifests/2026/atomic-write/manifest-object.bin", content.length),
                        body(content)))
                .expectError(FileSystemWriteInterruptedException.class)
                .verify();

        List<Path> tempManifests = tempFiles(storageRoot.resolve("metadata").resolve("manifests"));
        assertThat(tempManifests).hasSize(1);
        assertThat(committedManifestFiles()).isEmpty();
        assertThat(committedChunkFiles()).isEmpty();
        assertThat(regularFiles(storageRoot.resolve("metadata/content-address-index"))).isEmpty();
        assertNoCommittedObjectVisibility();

        ManifestId attemptedManifestId = manifestIdFromTempFile(tempManifests.getFirst());
        StepVerifier.create(orchestrator.read(attemptedManifestId))
                .expectError(java.util.NoSuchElementException.class)
                .verify();

        FileSystemRecoveryScanner scanner = new FileSystemRecoveryScanner();
        FileSystemRecoveryScanner.ScanReport report = scanner.scan(storageRoot);
        assertThat(report.findings())
                .anySatisfy(finding -> {
                    assertThat(finding.artifactType()).isEqualTo("incomplete-manifest");
                    assertThat(finding.artifactPath()).isEqualTo(tempManifests.getFirst().toString());
                });

        scanner.quarantine(storageRoot, report);
        assertThat(Files.exists(tempManifests.getFirst())).isFalse();
        assertThat(tempFiles(storageRoot.resolve("metadata").resolve("manifests"))).isEmpty();
    }

    private ReactiveStorageOrchestrator orchestrator(FileSystemStorageCluster cluster) {
        Path chunksDir = storageRoot.resolve("nodes/node-001/chunks");
        FileSystemStorePort storePort = new FileSystemStorePort(chunksDir, chunksDir, cluster.faultInjector());
        DataProcessingPipelineFactory pipelineFactory = new DataProcessingPipelineFactory(
                new NoOpDeduplicationStep(),
                new NoOpCompressionStep(),
                new NoOpEncryptionStep(),
                new NoOpErasureCodingStep(),
                storePort);
        return new ReactiveStorageOrchestrator(
                new CompleteUploadService(),
                new SinglePolicyCatalog(StoragePolicy.minimal(STORAGE_CLASS)),
                new EffectivePolicyResolver(),
                new VirtualDeviceResolver(),
                new PersistencePlanner(),
                cluster.addressIndex(),
                new FileSystemChunkStorePort(cluster),
                cluster.storedObjectRepository(),
                cluster.manifestRepository(),
                65536,
                StorageEventPublisher.noop(),
                pipelineFactory);
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

    private Flux<DataBuffer> body(byte[] content) {
        return Flux.just(BUFFER_FACTORY.wrap(content));
    }

    private List<Path> committedChunkFiles() throws IOException {
        Path nodes = storageRoot.resolve("nodes");
        if (!Files.isDirectory(nodes)) {
            return List.of();
        }
        try (Stream<Path> stream = Files.walk(nodes)) {
            return stream.filter(Files::isRegularFile)
                    .filter(path -> {
                        String name = path.getFileName().toString();
                        return !name.contains(".tmp.") && !name.endsWith(".sha256");
                    })
                    .toList();
        }
    }

    private List<Path> committedManifestFiles() throws IOException {
        Path manifests = storageRoot.resolve("metadata").resolve("manifests");
        if (!Files.isDirectory(manifests)) {
            return List.of();
        }
        try (Stream<Path> stream = Files.list(manifests)) {
            return stream.filter(Files::isRegularFile)
                    .filter(path -> path.getFileName().toString().endsWith(".properties"))
                    .filter(path -> !path.getFileName().toString().contains(".tmp."))
                    .toList();
        }
    }

    private List<Path> tempFiles(Path root) throws IOException {
        if (!Files.isDirectory(root)) {
            return List.of();
        }
        try (Stream<Path> stream = Files.walk(root)) {
            return stream.filter(Files::isRegularFile)
                    .filter(path -> path.getFileName().toString().contains(".tmp."))
                    .toList();
        }
    }

    private void assertNoCommittedObjectVisibility() throws IOException {
        Path objects = storageRoot.resolve("metadata").resolve("objects");
        assertThat(regularFiles(objects)).isEmpty();
        Path s3References = storageRoot.resolve("metadata").resolve("s3-object-references");
        assertThat(regularFiles(s3References)).isEmpty();
    }

    private List<Path> regularFiles(Path root) throws IOException {
        if (!Files.isDirectory(root)) {
            return List.of();
        }
        try (Stream<Path> stream = Files.walk(root)) {
            return stream.filter(Files::isRegularFile).toList();
        }
    }

    private ChunkId chunkIdFromTempFile(Path tempChunk) {
        String name = tempChunk.getFileName().toString();
        return ChunkId.of(UUID.fromString(name.substring(0, name.indexOf(".tmp."))));
    }

    private ManifestId manifestIdFromTempFile(Path tempManifest) {
        String name = tempManifest.getFileName().toString();
        return ManifestId.of(UUID.fromString(name.substring(0, name.indexOf(".properties.tmp."))));
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
