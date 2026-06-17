package com.example.magrathea.storageengine.infrastructure.filesystem;

import com.example.magrathea.storageengine.application.exception.ChunkIntegrityException;
import com.example.magrathea.storageengine.domain.valueobject.ChunkId;
import com.example.magrathea.storageengine.domain.valueobject.NodeId;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import reactor.test.StepVerifier;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Unit tests for atomic chunk write and checksum verification in {@link FileSystemStorageNode}.
 *
 * Covers:
 * <ul>
 *   <li>Write → read → checksum OK (happy path)</li>
 *   <li>No .tmp.* file remains after a successful write</li>
 *   <li>Corrupting the committed chunk file → read → {@link ChunkIntegrityException}</li>
 *   <li>Missing .sha256 sidecar → read → {@link ChunkIntegrityException}</li>
 * </ul>
 */
class FileSystemStorageNodeAtomicWriteTest {

    @TempDir
    Path tempDir;

    private FileSystemStorageNode node() {
        return new FileSystemStorageNode(tempDir.resolve("node"), NodeId.of("test-node"));
    }

    // ─────────────────────────────────────────────────────
    //  Happy-path: write → read → checksum OK
    // ─────────────────────────────────────────────────────

    @Test
    void writeThenReadReturnsSameBytesAndChecksumIsValid() {
        FileSystemStorageNode node = node();
        ChunkId chunkId = ChunkId.generate();
        byte[] content = "Hello atomic filesystem!".getBytes(StandardCharsets.UTF_8);

        StepVerifier.create(node.write(chunkId, content))
                .assertNext(result -> {
                    assertThat(result.chunkId()).isEqualTo(chunkId);
                    assertThat(result.bytesWritten()).isEqualTo(content.length);
                })
                .verifyComplete();

        StepVerifier.create(node.read(chunkId))
                .assertNext(bytes -> assertThat(bytes).isEqualTo(content))
                .verifyComplete();
    }

    @Test
    void writeCreatesChecksumSidecarFile() throws IOException {
        FileSystemStorageNode node = node();
        ChunkId chunkId = ChunkId.generate();
        byte[] content = "checksum sidecar test".getBytes(StandardCharsets.UTF_8);

        node.write(chunkId, content).block();

        Path sidecar = node.chunksDir().resolve(chunkId.value().toString() + ".sha256");
        assertThat(Files.exists(sidecar)).isTrue();

        String storedHex = Files.readString(sidecar).trim();
        String expectedHex = FileSystemStorageNode.sha256Hex(content);
        assertThat(storedHex).isEqualTo(expectedHex);
    }

    // ─────────────────────────────────────────────────────
    //  No temp files remain after successful write
    // ─────────────────────────────────────────────────────

    @Test
    void noTempFilesRemainAfterSuccessfulWrite() throws IOException {
        FileSystemStorageNode node = node();
        ChunkId chunkId = ChunkId.generate();
        byte[] content = "no-temp-files test".getBytes(StandardCharsets.UTF_8);

        node.write(chunkId, content).block();

        List<Path> allFiles;
        try (var stream = Files.list(node.chunksDir())) {
            allFiles = stream.toList();
        }

        List<Path> tempFiles = allFiles.stream()
                .filter(f -> f.getFileName().toString().contains(".tmp."))
                .toList();

        assertThat(tempFiles).isEmpty();
    }

    @Test
    void exactlyTwoFilesPerChunkAfterWrite() throws IOException {
        FileSystemStorageNode node = node();
        ChunkId chunkId = ChunkId.generate();
        byte[] content = "two-files test".getBytes(StandardCharsets.UTF_8);

        node.write(chunkId, content).block();

        List<Path> allFiles;
        try (var stream = Files.list(node.chunksDir())) {
            allFiles = stream.toList();
        }

        assertThat(allFiles).hasSize(2);
        assertThat(allFiles).anyMatch(f -> f.getFileName().toString().equals(chunkId.value().toString()));
        assertThat(allFiles).anyMatch(f -> f.getFileName().toString().equals(chunkId.value().toString() + ".sha256"));
    }

    // ─────────────────────────────────────────────────────
    //  Corruption detection
    // ─────────────────────────────────────────────────────

    @Test
    void corruptingChunkBytesThrowsChunkIntegrityException() throws IOException {
        FileSystemStorageNode node = node();
        ChunkId chunkId = ChunkId.generate();
        byte[] content = "data to be corrupted".getBytes(StandardCharsets.UTF_8);

        node.write(chunkId, content).block();

        // Corrupt the committed chunk file by overwriting with different bytes
        Path chunkFile = node.chunksDir().resolve(chunkId.value().toString());
        Files.writeString(chunkFile, "CORRUPTED BYTES INJECTED BY TEST");

        StepVerifier.create(node.read(chunkId))
                .expectErrorSatisfies(error -> {
                    assertThat(error).isInstanceOf(ChunkIntegrityException.class);
                    assertThat(error.getMessage()).contains("checksum mismatch");
                })
                .verify();
    }

    @Test
    void missingChecksumSidecarThrowsChunkIntegrityException() throws IOException {
        FileSystemStorageNode node = node();
        ChunkId chunkId = ChunkId.generate();
        byte[] content = "no sidecar test".getBytes(StandardCharsets.UTF_8);

        node.write(chunkId, content).block();

        // Remove the .sha256 sidecar to simulate a missing checksum file
        Path sidecar = node.chunksDir().resolve(chunkId.value().toString() + ".sha256");
        Files.delete(sidecar);

        StepVerifier.create(node.read(chunkId))
                .expectErrorSatisfies(error -> {
                    assertThat(error).isInstanceOf(ChunkIntegrityException.class);
                    assertThat(error.getMessage()).contains("checksum sidecar missing");
                })
                .verify();
    }

    // ─────────────────────────────────────────────────────
    //  Multiple chunks are independent
    // ─────────────────────────────────────────────────────

    @Test
    void multipleChunksAreStoredAndReadIndependently() {
        FileSystemStorageNode node = node();
        ChunkId chunkA = ChunkId.generate();
        ChunkId chunkB = ChunkId.generate();
        byte[] contentA = "chunk-A bytes".getBytes(StandardCharsets.UTF_8);
        byte[] contentB = "chunk-B bytes with more content".getBytes(StandardCharsets.UTF_8);

        node.write(chunkA, contentA).block();
        node.write(chunkB, contentB).block();

        StepVerifier.create(node.read(chunkA))
                .assertNext(bytes -> assertThat(bytes).isEqualTo(contentA))
                .verifyComplete();

        StepVerifier.create(node.read(chunkB))
                .assertNext(bytes -> assertThat(bytes).isEqualTo(contentB))
                .verifyComplete();
    }
}
