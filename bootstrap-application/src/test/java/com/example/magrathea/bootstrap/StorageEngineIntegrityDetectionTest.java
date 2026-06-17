package com.example.magrathea.bootstrap;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
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
import java.nio.file.StandardOpenOption;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * REQ-UPLOAD-006: Corrupted chunk data must be detected on read and must not be
 * served to the client as if it were valid object content.
 *
 * <p>After a successful PUT, overwrites a committed chunk file with garbage bytes
 * that do not match the stored SHA-256 sidecar checksum. The subsequent GET must
 * return an error response instead of the corrupted bytes.
 *
 * <p>{@code FileSystemStorageNode.read()} recomputes the SHA-256 of every chunk on
 * each read and compares it against the {@code .sha256} sidecar file written during
 * the atomic commit. When the computed hex does not match the stored hex it throws
 * {@code ChunkIntegrityException}, which the reactive ACL adapter propagates as a
 * non-200 error response.
 */
@SpringBootTest(
    webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
    properties = {
        "admin.server.port=0",
        "storage.engine.filesystem.node-count=1"
    }
)
@ActiveProfiles("storage-engine")
class StorageEngineIntegrityDetectionTest {

    private static final String BUCKET     = "req-upload-006-integrity-bucket";
    private static final String OBJECT_KEY = "integrity/2026/corruption-test.bin";
    private static final String FIXTURE_CONTENT = "integrity-check-payload-content";

    private static final Path STORAGE_ROOT = createStorageRoot();

    @DynamicPropertySource
    static void storageEngineProperties(DynamicPropertyRegistry registry) {
        registry.add("storage.engine.filesystem.root", () -> STORAGE_ROOT.toString());
        try {
            Path catalogRoot = Files.createTempDirectory("magrathea-req-upload-006-catalog-");
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
    void REQ_UPLOAD_006_corruptedChunkIsDetectedOnRead() throws IOException {
        WebTestClient client = WebTestClient.bindToServer()
            .baseUrl("http://127.0.0.1:" + port)
            .build();

        // 1. Create the bucket
        client.put()
            .uri("/" + BUCKET)
            .exchange()
            .expectStatus().isOk();

        // 2. Upload the object successfully
        client.put()
            .uri("/" + BUCKET + "/" + OBJECT_KEY)
            .contentType(MediaType.TEXT_PLAIN)
            .header("x-amz-storage-class", "STANDARD")
            .bodyValue(FIXTURE_CONTENT)
            .exchange()
            .expectStatus().isOk();

        // 3. Find the first committed chunk file on disk.
        //    Skip .sha256 sidecar files and .tmp. in-progress files.
        Path chunksDir = STORAGE_ROOT.resolve("nodes").resolve("node-001").resolve("chunks");
        assertThat(chunksDir)
            .as("chunks directory must exist under the storage root after a successful PUT")
            .isDirectory();

        Path chunkToCorrupt;
        try (var paths = Files.walk(chunksDir)) {
            chunkToCorrupt = paths
                .filter(Files::isRegularFile)
                .filter(p -> !p.getFileName().toString().endsWith(".sha256"))
                .filter(p -> !p.getFileName().toString().contains(".tmp."))
                .findFirst()
                .orElseThrow(() -> new AssertionError(
                    "No committed chunk file found under " + chunksDir
                    + " — the successful PUT must have written at least one chunk"));
        }

        // 4. Overwrite the chunk data with garbage bytes.
        //    The .sha256 sidecar still holds the original checksum, so any read will detect
        //    the mismatch: computed SHA-256 of {0x00, 0x01, 0x02} != stored SHA-256.
        Files.write(chunkToCorrupt, new byte[]{0x00, 0x01, 0x02}, StandardOpenOption.TRUNCATE_EXISTING);

        // 5. GET the object — the storage engine must detect the integrity violation and
        //    return a non-200 error response instead of the corrupted bytes.
        client.get()
            .uri("/" + BUCKET + "/" + OBJECT_KEY)
            .exchange()
            .expectStatus().value(status ->
                assertThat(status)
                    .as("GET after chunk corruption must not return 200 OK")
                    .isNotEqualTo(200));
    }

    // ── helpers ─────────────────────────────────────────────────────────────

    private static Path createStorageRoot() {
        try {
            return Files.createTempDirectory("magrathea-req-upload-006-");
        } catch (IOException e) {
            throw new UncheckedIOException("Failed to create REQ-UPLOAD-006 storage root", e);
        }
    }

    /**
     * Extracts named YAML catalog files from the classpath subdirectory to a temp
     * filesystem directory. Required because {@code YamlStoragePolicyCatalog} only
     * handles {@code file:} protocol URLs when scanning; it silently skips {@code jar:}
     * URLs from installed module JARs.
     */
    private static Path extractCatalogDir(Path catalogRoot, String classpathDir,
                                          List<String> fileNames) {
        try {
            Path dir = catalogRoot.resolve(classpathDir);
            Files.createDirectories(dir);
            ClassLoader cl = Thread.currentThread().getContextClassLoader();
            for (String fileName : fileNames) {
                String resourcePath = classpathDir + "/" + fileName;
                try (InputStream in = cl.getResourceAsStream(resourcePath)) {
                    if (in == null) {
                        throw new IOException("Classpath resource not found: " + resourcePath);
                    }
                    Files.write(dir.resolve(fileName), in.readAllBytes());
                }
            }
            return dir;
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }
}
