package com.example.magrathea.storageengine.infrastructure.filesystem;

import com.example.magrathea.storageengine.domain.valueobject.ChunkId;
import com.example.magrathea.storageengine.domain.valueobject.NodeId;
import reactor.core.publisher.Mono;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;

/**
 * A storage node backed by a single directory on the filesystem.
 * Each node writes chunk data to nodePath/chunks/{chunkId}.
 */
public class FileSystemStorageNode {

    private final Path nodePath;
    private final NodeId nodeId;
    private final Path chunksDir;

    public FileSystemStorageNode(Path nodePath, NodeId nodeId) {
        this.nodePath = java.util.Objects.requireNonNull(nodePath, "nodePath must not be null");
        this.nodeId = java.util.Objects.requireNonNull(nodeId, "nodeId must not be null");
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

    /**
     * Writes chunk data to nodePath/chunks/{chunkId}.
     */
    public Mono<WriteResult> write(ChunkId chunkId, byte[] data) {
        return Mono.fromCallable(() -> {
            Path chunkFile = chunksDir.resolve(chunkId.value().toString());
            Files.write(chunkFile, data, StandardOpenOption.CREATE, StandardOpenOption.TRUNCATE_EXISTING);
            return new WriteResult(nodeId, chunkId, data.length);
        });
    }

    /**
     * Reads chunk data from nodePath/chunks/{chunkId}.
     */
    public Mono<byte[]> read(ChunkId chunkId) {
        return Mono.fromCallable(() -> {
            Path chunkFile = chunksDir.resolve(chunkId.value().toString());
            return Files.readAllBytes(chunkFile);
        });
    }

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
