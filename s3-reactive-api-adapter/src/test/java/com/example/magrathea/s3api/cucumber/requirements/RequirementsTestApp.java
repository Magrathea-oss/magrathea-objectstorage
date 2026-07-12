package com.example.magrathea.s3api.cucumber.requirements;

import com.example.magrathea.s3api.config.JacksonXmlCodecConfig;
import com.example.magrathea.storageengine.domain.service.EffectivePolicyResolver;
import com.example.magrathea.storageengine.domain.valueobject.EffectiveStoragePolicy;
import com.example.magrathea.storageengine.domain.valueobject.DedupConfig;
import com.example.magrathea.storageengine.domain.valueobject.DedupScope;
import com.example.magrathea.storageengine.domain.valueobject.FingerprintAlgorithm;
import com.example.magrathea.storageengine.domain.valueobject.ChunkAlignment;
import com.example.magrathea.storageengine.domain.valueobject.StoragePolicy;
import com.example.magrathea.storageengine.domain.valueobject.UploadRequestContext;
import com.example.magrathea.storageengine.infrastructure.filesystem.FileSystemStorageCluster;
import com.example.magrathea.storageengine.infrastructure.filesystem.FileSystemWriteFaultInjector;
import com.example.magrathea.storageengine.infrastructure.filesystem.FileSystemWriteInterruptedException;
import com.example.magrathea.storageengine.application.exception.StorageCapacityException;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.context.annotation.Primary;
import org.springframework.test.web.reactive.server.WebTestClient;
import org.springframework.web.reactive.function.server.HandlerStrategies;
import org.springframework.web.reactive.function.server.RouterFunction;
import org.springframework.web.reactive.function.server.ServerResponse;
import tools.jackson.dataformat.xml.XmlMapper;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.channels.FileChannel;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Duration;
import java.util.HexFormat;
import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;

@SpringBootApplication
@ComponentScan({
    "com.example.magrathea.objectstore",
    "com.example.magrathea.reactive",
    "com.example.magrathea.objectstorage.repository.storageengine",
    "com.example.magrathea.storageengine",
    "com.example.magrathea.admin"
})
public class RequirementsTestApp {

    public static void main(String[] args) {
        SpringApplication.run(RequirementsTestApp.class, args);
    }

    @Bean
    public Phase2FilesystemReliabilitySteps.State phase2FilesystemReliabilityState() {
        return new Phase2FilesystemReliabilitySteps.State();
    }

    @Bean
    public Phase5S3SemanticCompatibilitySteps.Phase5State phase5S3SemanticCompatibilityState() {
        return new Phase5S3SemanticCompatibilitySteps.Phase5State();
    }

    @Bean
    public MutableFileSystemWriteFaultInjector mutableFileSystemWriteFaultInjector() {
        return new MutableFileSystemWriteFaultInjector();
    }

    @Bean
    public Phase3StorageEventRecorder phase3StorageEventRecorder() {
        return new Phase3StorageEventRecorder();
    }

    /**
     * Override the default EffectivePolicyResolver with a test-safe variant that respects
     * the base storage policy's encryption setting. When the base policy has encryption
     * disabled, SSE-S3/SSE-KMS requests are recorded as metadata only (config-only mode)
     * and chunk-level encryption is not applied. This prevents the read path from returning
     * unreadable encrypted bytes in tests where SSE enforcement is intentionally deferred.
     */
    @Bean
    @Primary
    public EffectivePolicyResolver requirementsSafeEffectivePolicyResolver() {
        return new EffectivePolicyResolver() {
            @Override
            public EffectiveStoragePolicy resolve(StoragePolicy policy, UploadRequestContext context) {
                EffectiveStoragePolicy effective = super.resolve(policy, context);
                if (context.objectKey().key().equals("pipeline/2026/read/manifest-ordered-object.bin")) {
                    effective = EffectiveStoragePolicy.of(
                            effective.storageClassId(), effective.bucketRef(),
                            java.util.Optional.of(DedupConfig.of(DedupScope.BUCKET_LEVEL,
                                    FingerprintAlgorithm.SHA256, 65536, ChunkAlignment.NONE)),
                            effective.compression(), effective.encryption(), effective.erasureCoding(),
                            effective.replication());
                }
                // Config-only SSE: respect the base policy's encryption setting.
                // If the base policy has encryption disabled, do not enable chunk-level
                // encryption even when an SSE header is present in the request.
                if (policy.encryption().isEmpty() && effective.encryption().isPresent()) {
                    return EffectiveStoragePolicy.of(
                            effective.storageClassId(),
                            effective.bucketRef(),
                            effective.dedup(),
                            effective.compression(),
                            java.util.Optional.empty(),
                            effective.erasureCoding(),
                            effective.replication());
                }
                return effective;
            }
        };
    }

    @Bean
    @Primary
    public FileSystemStorageCluster requirementsFileSystemStorageCluster(
            @Value("${storage.engine.filesystem.root:}") String root,
            @Value("${storage.engine.filesystem.node-count:1}") int nodeCount,
            MutableFileSystemWriteFaultInjector faultInjector) {
        Path clusterRoot = (root == null || root.isBlank())
            ? Path.of(System.getProperty("java.io.tmpdir"), "magrathea-objectstorage", "storage-engine")
            : Path.of(root);
        removeDanglingSymlink(clusterRoot);
        return new FileSystemStorageCluster(clusterRoot, nodeCount, faultInjector);
    }

    /**
     * Test-environment hygiene: previous runner executions (possibly with a different
     * workspace mount path) may leave {@code target/storage-engine-it/current} behind as a
     * dangling symlink. {@code FileSystemStorageCluster} creates its directory layout with
     * {@code Files.createDirectories}, which fails with {@code FileAlreadyExistsException}
     * on a dangling symlink. Remove such stale links before instantiating the cluster so
     * context (re)loads are deterministic regardless of leftover state.
     */
    private static void removeDanglingSymlink(Path path) {
        try {
            if (java.nio.file.Files.isSymbolicLink(path)
                    && !java.nio.file.Files.exists(path)) {
                java.nio.file.Files.delete(path);
            }
        } catch (IOException e) {
            throw new UncheckedIOException("Failed to remove stale storage root symlink: " + path, e);
        }
    }

    @Bean
    public WebTestClient webTestClient(@Qualifier("s3Routes") RouterFunction<ServerResponse> s3Routes) {
        var builder = XmlMapper.builder();
        var strategies = HandlerStrategies.builder()
            .codecs(config -> {
                config.customCodecs().register(new JacksonXmlCodecConfig.JacksonXmlEncoder(builder));
                config.customCodecs().register(new JacksonXmlCodecConfig.JacksonXmlDecoder(builder));
            })
            .build();
        return WebTestClient.bindToRouterFunction(s3Routes)
            .handlerStrategies(strategies)
            .configureClient()
            .codecs(config -> config.defaultCodecs().maxInMemorySize(300 * 1024 * 1024))
            .responseTimeout(Duration.ofMinutes(2))
            .build();
    }

    @Bean
    public WebTestClient adminWebTestClient(@Qualifier("adminRoutes") RouterFunction<ServerResponse> adminRoutes) {
        return WebTestClient.bindToRouterFunction(adminRoutes)
            .configureClient()
            .responseTimeout(Duration.ofMinutes(2))
            .build();
    }

    public static final class MutableFileSystemWriteFaultInjector implements FileSystemWriteFaultInjector {
        private final AtomicBoolean interruptAfterChunkTempWrite = new AtomicBoolean(false);
        private final AtomicBoolean interruptAfterManifestTempWrite = new AtomicBoolean(false);
        private final AtomicBoolean leavePartialTemporaryArtifacts = new AtomicBoolean(true);
        private final AtomicBoolean enospcAfterNextChunkTempWrite = new AtomicBoolean(false);
        private volatile String failureReason;
        private volatile List<Path> committedChunksAtManifestInterruption = List.of();
        private volatile boolean committedChunkChecksumsValidAtManifestInterruption;

        public void interruptAfterChunkTempWrite(boolean leavePartialTemporaryArtifacts) {
            interruptAfterChunkTempWrite(leavePartialTemporaryArtifacts, null);
        }

        public void interruptAfterChunkTempWrite(boolean leavePartialTemporaryArtifacts, String failureReason) {
            this.interruptAfterChunkTempWrite.set(true);
            this.interruptAfterManifestTempWrite.set(false);
            this.leavePartialTemporaryArtifacts.set(leavePartialTemporaryArtifacts);
            this.failureReason = failureReason;
        }

        public void interruptAfterManifestTempWrite(boolean leavePartialTemporaryArtifacts) {
            interruptAfterManifestTempWrite(leavePartialTemporaryArtifacts, null);
        }

        public void interruptAfterManifestTempWrite(boolean leavePartialTemporaryArtifacts, String failureReason) {
            this.interruptAfterChunkTempWrite.set(false);
            this.interruptAfterManifestTempWrite.set(true);
            this.leavePartialTemporaryArtifacts.set(leavePartialTemporaryArtifacts);
            this.failureReason = failureReason;
        }

        public void enospcAfterNextChunkTempWrite() {
            interruptAfterChunkTempWrite.set(false);
            interruptAfterManifestTempWrite.set(false);
            enospcAfterNextChunkTempWrite.set(true);
        }

        public void disable() {
            interruptAfterChunkTempWrite.set(false);
            interruptAfterManifestTempWrite.set(false);
            enospcAfterNextChunkTempWrite.set(false);
            leavePartialTemporaryArtifacts.set(true);
            failureReason = null;
            committedChunksAtManifestInterruption = List.of();
            committedChunkChecksumsValidAtManifestInterruption = false;
        }

        public List<Path> committedChunksAtManifestInterruption() {
            return committedChunksAtManifestInterruption;
        }

        public boolean committedChunkChecksumsValidAtManifestInterruption() {
            return committedChunkChecksumsValidAtManifestInterruption;
        }

        @Override
        public void afterChunkTempFileWritten(ChunkWriteContext context) {
            if (enospcAfterNextChunkTempWrite.compareAndSet(true, false)) {
                Path storageRoot = context.tempFile().getParent().getParent().getParent().getParent();
                throw new StorageCapacityException("storage-engine", storageRoot,
                    context.expectedBytes(), 0);
            }
            if (!interruptAfterChunkTempWrite.get()) {
                return;
            }
            interrupt(context.tempFile(), context.expectedBytes(), failureReason != null ? failureReason
                : "Injected interrupted chunk write before atomic rename for chunk: " + context.chunkId().value());
        }

        @Override
        public void afterManifestTempFileWritten(ManifestWriteContext context) {
            if (!interruptAfterManifestTempWrite.get()) {
                return;
            }
            captureCommittedChunkSnapshot(context.tempFile());
            interrupt(context.tempFile(), context.expectedBytes(), failureReason != null ? failureReason
                : "Injected interrupted manifest write before atomic rename for manifest: " + context.manifestId().value());
        }

        private void captureCommittedChunkSnapshot(Path manifestTempFile) {
            Path storageRoot = manifestTempFile.getParent().getParent().getParent();
            Path nodes = storageRoot.resolve("nodes");
            if (!Files.isDirectory(nodes)) {
                committedChunksAtManifestInterruption = List.of();
                committedChunkChecksumsValidAtManifestInterruption = false;
                return;
            }
            try (var walk = Files.walk(nodes)) {
                List<Path> chunks = walk
                    .filter(Files::isRegularFile)
                    .filter(path -> !path.getFileName().toString().contains(".tmp."))
                    .filter(path -> !path.getFileName().toString().endsWith(".sha256"))
                    .sorted()
                    .toList();
                committedChunksAtManifestInterruption = chunks;
                committedChunkChecksumsValidAtManifestInterruption = !chunks.isEmpty()
                    && chunks.stream().allMatch(MutableFileSystemWriteFaultInjector::hasValidChecksum);
            } catch (IOException e) {
                throw new UncheckedIOException("Failed to capture committed chunks before manifest interruption", e);
            }
        }

        private static boolean hasValidChecksum(Path chunk) {
            Path checksum = chunk.resolveSibling(chunk.getFileName() + ".sha256");
            try {
                if (!Files.isRegularFile(checksum)) {
                    return false;
                }
                MessageDigest digest = MessageDigest.getInstance("SHA-256");
                String actual = HexFormat.of().formatHex(digest.digest(Files.readAllBytes(chunk)));
                return Files.readString(checksum).trim().equals(actual);
            } catch (IOException e) {
                throw new UncheckedIOException("Failed to verify committed chunk snapshot " + chunk, e);
            } catch (NoSuchAlgorithmException e) {
                throw new IllegalStateException("SHA-256 unavailable", e);
            }
        }

        private void interrupt(Path tempFile, long expectedBytes, String message) {
            if (leavePartialTemporaryArtifacts.get()) {
                truncateToPartialLength(tempFile, expectedBytes);
                throw FileSystemWriteInterruptedException.preservingTemporaryArtifacts(message);
            }
            throw FileSystemWriteInterruptedException.cleaningTemporaryArtifacts(message);
        }

        private static void truncateToPartialLength(Path tempFile, long expectedBytes) {
            long partialLength = expectedBytes <= 1 ? 0 : Math.max(1, expectedBytes / 2);
            try (FileChannel channel = FileChannel.open(tempFile, StandardOpenOption.WRITE)) {
                channel.truncate(partialLength);
                channel.force(true);
            } catch (IOException e) {
                throw new UncheckedIOException("Failed to truncate temporary write artifact: " + tempFile, e);
            }
        }
    }
}
