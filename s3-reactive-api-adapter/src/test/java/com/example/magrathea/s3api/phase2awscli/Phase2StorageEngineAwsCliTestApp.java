package com.example.magrathea.s3api.phase2awscli;

import com.example.magrathea.storageengine.domain.service.EffectivePolicyResolver;
import com.example.magrathea.storageengine.domain.valueobject.EffectiveStoragePolicy;
import com.example.magrathea.storageengine.domain.valueobject.StoragePolicy;
import com.example.magrathea.storageengine.domain.valueobject.UploadRequestContext;
import com.example.magrathea.storageengine.infrastructure.filesystem.FileSystemStorageCluster;
import com.example.magrathea.storageengine.infrastructure.filesystem.FileSystemWriteFaultInjector;
import com.example.magrathea.storageengine.infrastructure.filesystem.FileSystemWriteInterruptedException;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.context.annotation.Primary;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.channels.FileChannel;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * Real HTTP test application for Phase 2 storage-engine AWS CLI Ability specs.
 */
@SpringBootApplication
@ComponentScan({
    "com.example.magrathea.objectstore",
    "com.example.magrathea.reactive",
    "com.example.magrathea.objectstorage.repository.storageengine",
    "com.example.magrathea.storageengine"
})
public class Phase2StorageEngineAwsCliTestApp {

    @Bean
    public AwsCliSharedContext awsCliSharedContext() {
        return new AwsCliSharedContext();
    }

    @Bean
    public MutableFileSystemWriteFaultInjector mutableFileSystemWriteFaultInjector() {
        return new MutableFileSystemWriteFaultInjector();
    }

    @Bean
    @Primary
    public FileSystemStorageCluster phase1FileSystemStorageCluster(
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
     * workspace mount path) may leave the configured storage root behind as a dangling
     * symlink. {@code FileSystemStorageCluster} creates its directory layout with
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

    public static final class MutableFileSystemWriteFaultInjector implements FileSystemWriteFaultInjector {
        private final AtomicBoolean interruptAfterChunkTempWrite = new AtomicBoolean(false);
        private final AtomicBoolean leavePartialTemporaryArtifacts = new AtomicBoolean(true);

        public void interruptAfterChunkTempWrite(boolean leavePartialTemporaryArtifacts) {
            this.interruptAfterChunkTempWrite.set(true);
            this.leavePartialTemporaryArtifacts.set(leavePartialTemporaryArtifacts);
        }

        public void disable() {
            interruptAfterChunkTempWrite.set(false);
            leavePartialTemporaryArtifacts.set(true);
        }

        @Override
        public void afterChunkTempFileWritten(ChunkWriteContext context) {
            if (!interruptAfterChunkTempWrite.get()) {
                return;
            }
            if (leavePartialTemporaryArtifacts.get()) {
                truncateToPartialLength(context.tempFile(), context.expectedBytes());
                throw FileSystemWriteInterruptedException.preservingTemporaryArtifacts(
                    "Injected interrupted chunk write before atomic rename for chunk: " + context.chunkId().value());
            }
            throw FileSystemWriteInterruptedException.cleaningTemporaryArtifacts(
                "Injected interrupted chunk write before atomic rename for chunk: " + context.chunkId().value());
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
