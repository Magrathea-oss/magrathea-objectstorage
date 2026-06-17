package com.example.magrathea.storageengine.infrastructure.filesystem;

import com.example.magrathea.storageengine.application.exception.ChunkIntegrityException;
import com.example.magrathea.storageengine.domain.valueobject.ChunkId;
import com.example.magrathea.storageengine.domain.valueobject.NodeId;
import reactor.core.publisher.Mono;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.channels.FileChannel;
import java.nio.file.Files;
import java.nio.file.NoSuchFileException;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.nio.file.StandardOpenOption;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import java.util.UUID;

/**
 * A storage node backed by a single directory on the filesystem.
 * Each node writes chunk data to nodePath/chunks/{chunkId}.
 *
 * <h2>Atomic write protocol (REQ-FS-001, REQ-FS-003)</h2>
 * <ol>
 *   <li>Compute SHA-256 of the data bytes.</li>
 *   <li>Write data to a uniquely-named temp file: {@code chunksDir/{chunkId}.tmp.{UUID}}.</li>
 *   <li>Fsync the temp file channel.</li>
 *   <li>Write the hex checksum to {@code chunksDir/{chunkId}.sha256.tmp.{UUID}}.</li>
 *   <li>Fsync the checksum temp file.</li>
 *   <li>Atomically rename the checksum temp to the final {@code {chunkId}.sha256} path.</li>
 *   <li>Atomically rename the data temp to the final {@code {chunkId}} path.</li>
 *   <li>On any failure, delete both temp files (best effort).</li>
 * </ol>
 *
 * <h2>Read-time checksum verification (REQ-FS-003)</h2>
 * On every read, the stored {@code .sha256} sidecar is loaded and the bytes' SHA-256 is
 * compared. A mismatch throws {@link ChunkIntegrityException}.
 */
public class FileSystemStorageNode {

    private static final String SHA256_EXTENSION = ".sha256";
    private static final String TMP_PREFIX = ".tmp.";

    private final Path nodePath;
    private final NodeId nodeId;
    private final Path chunksDir;
    private final FileSystemWriteFaultInjector faultInjector;

    public FileSystemStorageNode(Path nodePath, NodeId nodeId) {
        this(nodePath, nodeId, FileSystemWriteFaultInjector.disabled());
    }

    public FileSystemStorageNode(
            Path nodePath,
            NodeId nodeId,
            FileSystemWriteFaultInjector faultInjector) {
        this.nodePath = java.util.Objects.requireNonNull(nodePath, "nodePath must not be null");
        this.nodeId = java.util.Objects.requireNonNull(nodeId, "nodeId must not be null");
        this.faultInjector = java.util.Objects.requireNonNull(faultInjector, "faultInjector must not be null");
        this.chunksDir = nodePath.resolve("chunks");
        try {
            Files.createDirectories(chunksDir);
        } catch (IOException e) {
            throw new UncheckedIOException("Failed to create chunks directory: " + chunksDir, e);
        }
    }

    public NodeId nodeId() {
        return nodeId;
    }

    public Path nodePath() {
        return nodePath;
    }

    public Path chunksDir() {
        return chunksDir;
    }

    /**
     * Atomically writes chunk data to nodePath/chunks/{chunkId}.
     * Computes and persists a SHA-256 sidecar checksum file alongside the chunk.
     */
    public Mono<WriteResult> write(ChunkId chunkId, byte[] data) {
        return BlockingFileSystemOperation.fromCallable(() -> {
            String chunkFileName = chunkId.value().toString();
            String uuid = UUID.randomUUID().toString();

            Path finalFile = chunksDir.resolve(chunkFileName);
            Path finalChecksumFile = chunksDir.resolve(chunkFileName + SHA256_EXTENSION);
            Path tempFile = chunksDir.resolve(chunkFileName + TMP_PREFIX + uuid);
            Path tempChecksumFile = chunksDir.resolve(chunkFileName + SHA256_EXTENSION + TMP_PREFIX + uuid);

            String checksumHex = sha256Hex(data);

            try {
                // Step 1: write data to temp file and fsync
                Files.write(tempFile, data, StandardOpenOption.CREATE_NEW);
                try (FileChannel channel = FileChannel.open(tempFile, StandardOpenOption.WRITE)) {
                    channel.force(true);
                }
                faultInjector.afterChunkTempFileWritten(
                        new FileSystemWriteFaultInjector.ChunkWriteContext(
                                nodeId, chunkId, tempFile, finalFile, data.length));

                // Step 2: write checksum to temp checksum file and fsync
                Files.writeString(tempChecksumFile, checksumHex, StandardOpenOption.CREATE_NEW);
                try (FileChannel channel = FileChannel.open(tempChecksumFile, StandardOpenOption.WRITE)) {
                    channel.force(true);
                }

                // Step 3: atomic rename — checksum sidecar first, then data
                Files.move(tempChecksumFile, finalChecksumFile,
                        StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING);
                Files.move(tempFile, finalFile,
                        StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING);

            } catch (Exception e) {
                if (!preserveTemporaryArtifacts(e)) {
                    // Best-effort cleanup of temp files on failure
                    try { Files.deleteIfExists(tempFile); } catch (Exception ignored) { /* best effort */ }
                    try { Files.deleteIfExists(tempChecksumFile); } catch (Exception ignored) { /* best effort */ }
                }
                if (e instanceof IOException ioe) {
                    throw new UncheckedIOException("Atomic chunk write failed for: " + chunkId.value(), ioe);
                }
                throw e;
            }

            return new WriteResult(nodeId, chunkId, data.length);
        });
    }

    /**
     * Reads chunk data from nodePath/chunks/{chunkId} and verifies its SHA-256 checksum.
     *
     * @throws ChunkIntegrityException if the checksum sidecar is missing or the bytes do not match.
     * @throws NoSuchFileException if the chunk file does not exist (propagated from Files.readAllBytes).
     */
    public Mono<byte[]> read(ChunkId chunkId) {
        return BlockingFileSystemOperation.fromCallable(() -> {
            String chunkFileName = chunkId.value().toString();
            Path chunkFile = chunksDir.resolve(chunkFileName);
            Path checksumFile = chunksDir.resolve(chunkFileName + SHA256_EXTENSION);

            byte[] data = Files.readAllBytes(chunkFile);

            if (!Files.exists(checksumFile)) {
                throw new ChunkIntegrityException(
                        "Chunk checksum sidecar missing for chunk: " + chunkId.value()
                        + " — cannot verify integrity");
            }

            String storedHex = Files.readString(checksumFile).trim();
            String computedHex = sha256Hex(data);

            if (!computedHex.equals(storedHex)) {
                throw new ChunkIntegrityException(
                        "Chunk checksum mismatch for chunk: " + chunkId.value()
                        + " — stored=" + storedHex + " computed=" + computedHex);
            }

            return data;
        });
    }

    // ─────────────────────────────────────────────────────
    //  Checksum helper
    // ─────────────────────────────────────────────────────

    private static boolean preserveTemporaryArtifacts(Exception e) {
        return e instanceof FileSystemWriteInterruptedException interrupted
                && interrupted.preserveTemporaryArtifacts();
    }

    static String sha256Hex(byte[] data) {
        try {
            MessageDigest md = MessageDigest.getInstance("SHA-256");
            return HexFormat.of().formatHex(md.digest(data));
        } catch (NoSuchAlgorithmException e) {
            throw new RuntimeException("SHA-256 not available", e);
        }
    }

    // ─────────────────────────────────────────────────────
    //  Result type
    // ─────────────────────────────────────────────────────

    /**
     * Result of a write operation.
     */
    public record WriteResult(NodeId nodeId, ChunkId chunkId, long bytesWritten) {
        public WriteResult {
            java.util.Objects.requireNonNull(nodeId, "nodeId must not be null");
            java.util.Objects.requireNonNull(chunkId, "chunkId must not be null");
            if (bytesWritten < 0) {
                throw new IllegalArgumentException("bytesWritten must be >= 0: " + bytesWritten);
            }
        }
    }
}
