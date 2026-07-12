package com.example.magrathea.storageengine.infrastructure.filesystem;

import com.example.magrathea.storageengine.application.exception.ChunkIntegrityException;
import com.example.magrathea.storageengine.domain.valueobject.ChunkId;
import com.example.magrathea.storageengine.domain.valueobject.NodeId;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import reactor.core.scheduler.Schedulers;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.NoSuchFileException;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;

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

    private final Path nodePath;
    private final NodeId nodeId;
    private final Path chunksDir;
    private final Path wholeObjectsDir;
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
        this.wholeObjectsDir = nodePath.resolve("whole-objects");
        try {
            Files.createDirectories(chunksDir);
            Files.createDirectories(wholeObjectsDir);
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

    public Path wholeObjectsDir() {
        return wholeObjectsDir;
    }

    /**
     * Atomically writes chunk data to nodePath/chunks/{chunkId}.
     * Computes and persists a SHA-256 sidecar checksum file alongside the chunk.
     */
    public Mono<WriteResult> write(ChunkId chunkId, byte[] data) {
        return BlockingFileSystemOperation.fromCallable(() -> {
            AtomicChunkWriteProtocol.PendingWrite pending =
                    AtomicChunkWriteProtocol.prepare(chunksDir, chunkId);
            String checksumHex = sha256Hex(data);

            try {
                Files.write(pending.tempData(), data, StandardOpenOption.CREATE_NEW);
                AtomicChunkWriteProtocol.force(pending.tempData());
                faultInjector.afterChunkTempFileWritten(
                        new FileSystemWriteFaultInjector.ChunkWriteContext(
                                nodeId, chunkId, pending.tempData(), pending.finalData(), data.length));
                String persistedChecksum = sha256Hex(pending.tempData());
                if (!checksumHex.equals(persistedChecksum)) {
                    throw new ChunkIntegrityException(
                            "Persisted temporary chunk checksum mismatch before commit: " + chunkId.value());
                }
                AtomicChunkWriteProtocol.commit(pending, checksumHex);
            } catch (Exception e) {
                AtomicChunkWriteProtocol.cleanupUncommitted(pending, preserveTemporaryArtifacts(e));
                if (e instanceof IOException ioe) {
                    throw FileSystemCapacityErrors.translate(ioe, nodePath.getParent().getParent(),
                            data.length, "Atomic chunk write failed for: " + chunkId.value());
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
        return readFrom(chunksDir, chunkId, "chunk");
    }

    /** Reads a schema-2 whole-object artifact from its dedicated namespace. */
    public Mono<byte[]> readWholeObject(ChunkId artifactId) {
        return readFrom(wholeObjectsDir, artifactId, "whole-object artifact");
    }

    public Flux<byte[]> streamChunk(ChunkId artifactId) {
        return streamFrom(chunksDir, artifactId);
    }

    public Flux<byte[]> streamWholeObject(ChunkId artifactId) {
        return streamFrom(wholeObjectsDir, artifactId);
    }

    public Mono<Void> validateChunkMetadata(ChunkId artifactId) {
        return validateMetadata(chunksDir, artifactId);
    }

    public Mono<Void> validateWholeObjectMetadata(ChunkId artifactId) {
        return validateMetadata(wholeObjectsDir, artifactId);
    }

    private Flux<byte[]> streamFrom(Path root, ChunkId artifactId) {
        Path dataFile = root.resolve(artifactId.value().toString());
        return Flux.using(
                () -> Files.newInputStream(dataFile),
                input -> Flux.generate(sink -> {
                    try {
                        byte[] block = new byte[64 * 1024];
                        int read = input.read(block);
                        if (read < 0) {
                            sink.complete();
                        } else if (read == block.length) {
                            sink.next(block);
                        } else {
                            sink.next(java.util.Arrays.copyOf(block, read));
                        }
                    } catch (IOException error) {
                        sink.error(error);
                    }
                }),
                input -> {
                    try {
                        input.close();
                    } catch (IOException ignored) {
                        // Closing after cancellation is best effort.
                    }
                })
                .cast(byte[].class)
                .subscribeOn(Schedulers.boundedElastic());
    }

    private Mono<Void> validateMetadata(Path root, ChunkId artifactId) {
        return BlockingFileSystemOperation.fromRunnable(() -> {
            Path dataFile = root.resolve(artifactId.value().toString());
            Path checksumFile = root.resolve(artifactId.value() + SHA256_EXTENSION);
            if (!Files.isRegularFile(dataFile)) {
                throw new UncheckedIOException(new NoSuchFileException(dataFile.toString()));
            }
            if (!Files.isRegularFile(checksumFile)) {
                throw new ChunkIntegrityException(
                        "Checksum sidecar missing for storage artifact: " + artifactId.value());
            }
        });
    }

    private Mono<byte[]> readFrom(Path root, ChunkId artifactId, String artifactType) {
        return BlockingFileSystemOperation.fromCallable(() -> {
            String fileName = artifactId.value().toString();
            Path dataFile = root.resolve(fileName);
            Path checksumFile = root.resolve(fileName + SHA256_EXTENSION);
            byte[] data = Files.readAllBytes(dataFile);
            String checksumSubject = artifactType.equals("chunk")
                    ? "Chunk checksum" : "Whole-object artifact checksum";
            if (!Files.exists(checksumFile)) {
                throw new ChunkIntegrityException(
                        checksumSubject + " sidecar missing for " + artifactType + ": " + artifactId.value()
                                + " — cannot verify integrity");
            }
            String storedHex = Files.readString(checksumFile).trim();
            String computedHex = sha256Hex(data);
            if (!computedHex.equals(storedHex)) {
                throw new ChunkIntegrityException(
                        checksumSubject + " mismatch for " + artifactType + ": " + artifactId.value()
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

    private static String sha256Hex(Path path) throws IOException {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            try (var input = Files.newInputStream(path)) {
                byte[] block = new byte[64 * 1024];
                int read;
                while ((read = input.read(block)) >= 0) {
                    digest.update(block, 0, read);
                }
            }
            return HexFormat.of().formatHex(digest.digest());
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 not available", e);
        }
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
