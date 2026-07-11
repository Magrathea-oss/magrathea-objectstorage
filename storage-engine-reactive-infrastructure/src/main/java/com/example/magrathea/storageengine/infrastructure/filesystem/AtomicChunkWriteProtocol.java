package com.example.magrathea.storageengine.infrastructure.filesystem;

import com.example.magrathea.storageengine.domain.valueobject.ChunkId;

import java.io.IOException;
import java.nio.channels.FileChannel;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.nio.file.StandardOpenOption;
import java.util.UUID;

/**
 * Shared filesystem protocol for publishing a chunk and its checksum sidecar atomically.
 *
 * <p>Data is written and forced at {@link PendingWrite#tempData()}, then the checksum is
 * written and forced at {@link PendingWrite#tempChecksum()}. The checksum is atomically
 * renamed first and the data is atomically renamed last, making the data rename the commit
 * point. A recovery scan can therefore distinguish committed chunks from interrupted temp
 * files and a sidecar left in the narrow interval before the data commit.</p>
 */
public final class AtomicChunkWriteProtocol {

    private static final String SHA256_EXTENSION = ".sha256";
    private static final String TEMP_MARKER = ".tmp.";

    private AtomicChunkWriteProtocol() {
    }

    public static PendingWrite prepare(Path chunksDir, ChunkId chunkId) {
        String fileName = chunkId.value().toString();
        String tempId = UUID.randomUUID().toString();
        return new PendingWrite(
                chunksDir.resolve(fileName + TEMP_MARKER + tempId),
                chunksDir.resolve(fileName + SHA256_EXTENSION + TEMP_MARKER + tempId),
                chunksDir.resolve(fileName),
                chunksDir.resolve(fileName + SHA256_EXTENSION),
                chunkId);
    }

    public static void force(Path file) throws IOException {
        try (FileChannel channel = FileChannel.open(file, StandardOpenOption.WRITE)) {
            channel.force(true);
        }
    }

    public static void commit(PendingWrite pending, String checksumHex) throws IOException {
        try {
            Files.writeString(pending.tempChecksum(), checksumHex, StandardOpenOption.CREATE_NEW);
            force(pending.tempChecksum());
            Files.move(pending.tempChecksum(), pending.finalChecksum(),
                    StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING);
            Files.move(pending.tempData(), pending.finalData(),
                    StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING);
        } catch (IOException | RuntimeException failure) {
            cleanupUncommitted(pending, false);
            throw failure;
        }
    }

    /**
     * Best-effort cleanup after error or cancellation.
     *
     * @param preserveTempData whether an intentionally interrupted data temp file remains
     *                         available to the recovery scanner
     */
    public static void cleanupUncommitted(PendingWrite pending, boolean preserveTempData) {
        if (!preserveTempData) {
            deleteIfExists(pending.tempData());
        }
        deleteIfExists(pending.tempChecksum());
        if (!Files.exists(pending.finalData())) {
            deleteIfExists(pending.finalChecksum());
        }
    }

    private static void deleteIfExists(Path path) {
        try {
            Files.deleteIfExists(path);
        } catch (IOException ignored) {
            // Best effort: recovery scanning handles artifacts that survive cleanup.
        }
    }

    public record PendingWrite(
            Path tempData,
            Path tempChecksum,
            Path finalData,
            Path finalChecksum,
            ChunkId chunkId) {
    }
}
