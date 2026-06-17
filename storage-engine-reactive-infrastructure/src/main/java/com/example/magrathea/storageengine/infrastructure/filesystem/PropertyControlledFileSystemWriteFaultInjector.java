package com.example.magrathea.storageengine.infrastructure.filesystem;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.channels.FileChannel;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;

/**
 * Property-driven filesystem write fault injector.
 *
 * <p>It is safe by default because both interruption flags default to {@code false}
 * in Spring configuration. When a test profile enables one of the flags, the injector
 * truncates the temporary artifact to simulate a partial interrupted write and throws
 * before the atomic rename can publish the committed path.</p>
 */
public final class PropertyControlledFileSystemWriteFaultInjector implements FileSystemWriteFaultInjector {

    private final boolean interruptAfterChunkTempWrite;
    private final boolean interruptAfterManifestTempWrite;
    private final boolean leavePartialTemporaryArtifacts;

    public PropertyControlledFileSystemWriteFaultInjector(
            boolean interruptAfterChunkTempWrite,
            boolean interruptAfterManifestTempWrite,
            boolean leavePartialTemporaryArtifacts) {
        this.interruptAfterChunkTempWrite = interruptAfterChunkTempWrite;
        this.interruptAfterManifestTempWrite = interruptAfterManifestTempWrite;
        this.leavePartialTemporaryArtifacts = leavePartialTemporaryArtifacts;
    }

    @Override
    public void afterChunkTempFileWritten(ChunkWriteContext context) {
        if (!interruptAfterChunkTempWrite) {
            return;
        }
        if (leavePartialTemporaryArtifacts) {
            truncateToPartialLength(context.tempFile(), context.expectedBytes());
            throw FileSystemWriteInterruptedException.preservingTemporaryArtifacts(
                    "Injected interrupted chunk write before atomic rename for chunk: "
                            + context.chunkId().value());
        }
        throw FileSystemWriteInterruptedException.cleaningTemporaryArtifacts(
                "Injected interrupted chunk write before atomic rename for chunk: "
                        + context.chunkId().value());
    }

    @Override
    public void afterManifestTempFileWritten(ManifestWriteContext context) {
        if (!interruptAfterManifestTempWrite) {
            return;
        }
        if (leavePartialTemporaryArtifacts) {
            truncateToPartialLength(context.tempFile(), context.expectedBytes());
            throw FileSystemWriteInterruptedException.preservingTemporaryArtifacts(
                    "Injected interrupted manifest write before atomic rename for manifest: "
                            + context.manifestId().value());
        }
        throw FileSystemWriteInterruptedException.cleaningTemporaryArtifacts(
                "Injected interrupted manifest write before atomic rename for manifest: "
                        + context.manifestId().value());
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
