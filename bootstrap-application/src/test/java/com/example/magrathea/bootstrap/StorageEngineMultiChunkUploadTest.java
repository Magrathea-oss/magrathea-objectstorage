package com.example.magrathea.bootstrap;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.core.io.buffer.DataBuffer;
import org.springframework.core.io.buffer.DataBufferUtils;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.reactive.server.WebTestClient;

import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * REQ-UPLOAD-003 — Bounded-memory streaming: large objects must be written
 * as multiple ordered durable chunks and read back byte-for-byte exactly,
 * without materializing the full object in memory.
 */
@SpringBootTest(
    webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
    properties = {
        "admin.server.port=0",
        "storage.engine.filesystem.node-count=1",
        "storage.engine.chunk-size-bytes=32768"
    }
)
@ActiveProfiles("storage-engine")
class StorageEngineMultiChunkUploadTest {

    private static final String BUCKET     = "req-upload-003-multi-chunk-bucket";
    private static final String OBJECT_KEY = "large-objects/2026/streaming/128kb-object.bin";

    /** 128 KB deterministic payload: cycling 0x00..0xFF byte pattern. */
    private static final byte[] FIXTURE_128KB = buildCyclicPayload(128 * 1024);

    private static final Path STORAGE_ROOT = createStorageRoot();

    @DynamicPropertySource
    static void storageEngineProperties(DynamicPropertyRegistry registry) {
        registry.add("storage.engine.filesystem.root", () -> STORAGE_ROOT.toString());
        try {
            Path catalogRoot = Files.createTempDirectory("magrathea-req-upload-003-catalog-");
            registry.add("storage.engine.policies.dir",
                () -> extractCatalogDir(catalogRoot, "storage-policies",
                    List.of("minio-standard.yaml")).toString());
            registry.add("storage.engine.devices.dir",
                () -> extractCatalogDir(catalogRoot, "storage-devices",
                    List.of("local-disk-0.yaml")).toString());
            registry.add("storage.engine.disksets.dir",
                () -> extractCatalogDir(catalogRoot, "disk-sets",
                    List.of("default-diskset.yaml")).toString());
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    @LocalServerPort
    private int port;

    @Test
    void REQ_UPLOAD_003_largeObjectIsWrittenAsMultipleChunksAndReadBackExactly() throws IOException {
        WebTestClient client = WebTestClient.bindToServer()
            .baseUrl("http://127.0.0.1:" + port)
            .responseTimeout(java.time.Duration.ofSeconds(30))
            .build();

        // 1 — Create bucket
        client.put()
            .uri(slashPreservingPath(BUCKET))
            .exchange()
            .expectStatus().isOk();

        // 2 — Upload 128 KB object
        client.put()
            .uri(slashPreservingPath(BUCKET, OBJECT_KEY))
            .contentType(MediaType.APPLICATION_OCTET_STREAM)
            .header("x-amz-storage-class", "STANDARD")
            .bodyValue(FIXTURE_128KB)
            .exchange()
            .expectStatus().isOk();

        // 3 — Assert that the chunks directory contains ≥ 2 chunk files
        //     (128 KB / 32 KB = 4 chunks; assert ≥ 2 to be storage-policy tolerant)
        Path chunksDir = STORAGE_ROOT.resolve("nodes").resolve("node-001").resolve("chunks");
        assertThat(chunksDir).isDirectory();
        long chunkFileCount;
        try (var paths = Files.walk(chunksDir)) {
            chunkFileCount = paths.filter(Files::isRegularFile).count();
        }
        assertThat(chunkFileCount)
            .as("expected ≥ 2 chunk files under %s for a 128 KB object with 32 KB chunk size", chunksDir)
            .isGreaterThanOrEqualTo(2);

        // 4 — Download the object and verify byte-for-byte equality with the original
        byte[] downloaded = drainBytes(
            client.get()
                .uri(slashPreservingPath(BUCKET, OBJECT_KEY))
                .exchange()
                .expectStatus().isOk()
                .returnResult(DataBuffer.class)
                .getResponseBody()
        );

        assertThat(downloaded)
            .as("downloaded bytes must equal the original 128 KB fixture exactly")
            .isEqualTo(FIXTURE_128KB);
    }

    // ── helpers ─────────────────────────────────────────────────────────────

    private static byte[] drainBytes(reactor.core.publisher.Flux<DataBuffer> flux) {
        return flux
            .map(buffer -> {
                try {
                    byte[] bytes = new byte[buffer.readableByteCount()];
                    buffer.read(bytes);
                    return bytes;
                } finally {
                    DataBufferUtils.release(buffer);
                }
            })
            .collectList()
            .map(parts -> {
                int total = parts.stream().mapToInt(b -> b.length).sum();
                byte[] result = new byte[total];
                int offset = 0;
                for (byte[] part : parts) {
                    System.arraycopy(part, 0, result, offset, part.length);
                    offset += part.length;
                }
                return result;
            })
            .block();
    }

    private static byte[] buildCyclicPayload(int size) {
        byte[] payload = new byte[size];
        for (int i = 0; i < size; i++) {
            payload[i] = (byte) (i & 0xFF);
        }
        return payload;
    }

    private static String slashPreservingPath(String bucket) {
        return "/" + bucket;
    }

    private static String slashPreservingPath(String bucket, String key) {
        return "/" + bucket + "/" + key;
    }

    private static Path createStorageRoot() {
        try {
            return Files.createTempDirectory("magrathea-req-upload-003-");
        } catch (IOException e) {
            throw new UncheckedIOException("Failed to create REQ-UPLOAD-003 storage root", e);
        }
    }

    private static Path extractCatalogDir(Path catalogRoot, String classpathDir, List<String> fileNames) {
        try {
            Path dir = catalogRoot.resolve(classpathDir);
            Files.createDirectories(dir);
            ClassLoader cl = Thread.currentThread().getContextClassLoader();
            for (String fileName : fileNames) {
                String resourcePath = classpathDir + "/" + fileName;
                try (InputStream in = cl.getResourceAsStream(resourcePath)) {
                    if (in == null) throw new IOException("Classpath resource not found: " + resourcePath);
                    Files.write(dir.resolve(fileName), in.readAllBytes());
                }
            }
            return dir;
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }
}
