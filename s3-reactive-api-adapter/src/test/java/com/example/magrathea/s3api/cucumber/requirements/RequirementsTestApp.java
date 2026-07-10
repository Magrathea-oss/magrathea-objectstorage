package com.example.magrathea.s3api.cucumber.requirements;

import com.example.magrathea.s3api.config.JacksonXmlCodecConfig;
import com.example.magrathea.storageengine.domain.service.EffectivePolicyResolver;
import com.example.magrathea.storageengine.domain.valueobject.EffectiveStoragePolicy;
import com.example.magrathea.storageengine.domain.valueobject.StoragePolicy;
import com.example.magrathea.storageengine.domain.valueobject.UploadRequestContext;
import com.example.magrathea.storageengine.infrastructure.filesystem.FileSystemStorageCluster;
import com.example.magrathea.storageengine.infrastructure.filesystem.FileSystemWriteFaultInjector;
import com.example.magrathea.storageengine.infrastructure.filesystem.FileSystemWriteInterruptedException;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
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
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.time.Duration;
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

        public void interruptAfterChunkTempWrite(boolean leavePartialTemporaryArtifacts) {
            this.interruptAfterChunkTempWrite.set(true);
            this.interruptAfterManifestTempWrite.set(false);
            this.leavePartialTemporaryArtifacts.set(leavePartialTemporaryArtifacts);
        }

        public void interruptAfterManifestTempWrite(boolean leavePartialTemporaryArtifacts) {
            this.interruptAfterChunkTempWrite.set(false);
            this.interruptAfterManifestTempWrite.set(true);
            this.leavePartialTemporaryArtifacts.set(leavePartialTemporaryArtifacts);
        }

        public void disable() {
            interruptAfterChunkTempWrite.set(false);
            interruptAfterManifestTempWrite.set(false);
            leavePartialTemporaryArtifacts.set(true);
        }

        @Override
        public void afterChunkTempFileWritten(ChunkWriteContext context) {
            if (!interruptAfterChunkTempWrite.get()) {
                return;
            }
            interrupt(context.tempFile(), context.expectedBytes(),
                "Injected interrupted chunk write before atomic rename for chunk: " + context.chunkId().value());
        }

        @Override
        public void afterManifestTempFileWritten(ManifestWriteContext context) {
            if (!interruptAfterManifestTempWrite.get()) {
                return;
            }
            interrupt(context.tempFile(), context.expectedBytes(),
                "Injected interrupted manifest write before atomic rename for manifest: " + context.manifestId().value());
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
