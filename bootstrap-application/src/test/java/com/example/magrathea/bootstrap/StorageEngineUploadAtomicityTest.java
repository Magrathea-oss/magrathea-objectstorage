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
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * REQ-UPLOAD-004: A failed PUT object must NOT result in a readable object.
 *
 * <p>Proves upload atomicity: when a chunk write is interrupted before the atomic
 * rename (via {@code PropertyControlledFileSystemWriteFaultInjector}), the object
 * is never committed to the manifest store, so a subsequent GET returns 404 and
 * no manifest file appears under {@code metadata/manifests/}.
 *
 * <p>The fault injection property
 * {@code storage.engine.filesystem.fault-injection.interrupt-after-chunk-temp-write=true}
 * causes {@code FileSystemStorageNode.write()} to throw
 * {@code FileSystemWriteInterruptedException} after writing the temp chunk file
 * but before the atomic rename, which propagates as a 5xx response from the PUT.
 */
@SpringBootTest(
    webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
    properties = {
        "admin.server.port=0",
        "storage.engine.filesystem.node-count=1",
        "storage.engine.filesystem.fault-injection.interrupt-after-chunk-temp-write=true",
        "storage.engine.filesystem.fault-injection.leave-partial-temporary-artifacts=true"
    }
)
@ActiveProfiles("storage-engine")
class StorageEngineUploadAtomicityTest {

    private static final String BUCKET     = "req-upload-004-atomicity-bucket";
    private static final String OBJECT_KEY = "uploads/2026/failed/atomicity-test.bin";
    private static final String FIXTURE_CONTENT = "atomicity-test-payload";

    private static final Path STORAGE_ROOT = createStorageRoot();

    @DynamicPropertySource
    static void storageEngineProperties(DynamicPropertyRegistry registry) {
        registry.add("storage.engine.filesystem.root", () -> STORAGE_ROOT.toString());
        try {
            Path catalogRoot = Files.createTempDirectory("magrathea-req-upload-004-catalog-");
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
    void REQ_UPLOAD_004_failedUploadDoesNotPublishReadableObject() throws IOException {
        WebTestClient client = WebTestClient.bindToServer()
            .baseUrl("http://127.0.0.1:" + port)
            .build();

        // 1. Create the bucket
        client.put()
            .uri("/" + BUCKET)
            .exchange()
            .expectStatus().isOk();

        // 2. Attempt PUT object — fault injection interrupts the chunk write before the atomic
        //    rename, so the PUT fails with a 5xx server error
        client.put()
            .uri("/" + BUCKET + "/" + OBJECT_KEY)
            .contentType(MediaType.TEXT_PLAIN)
            .header("x-amz-storage-class", "STANDARD")
            .bodyValue(FIXTURE_CONTENT)
            .exchange()
            .expectStatus().is5xxServerError();

        // 3. GET the same key — must return 404 because the upload was never committed
        client.get()
            .uri("/" + BUCKET + "/" + OBJECT_KEY)
            .exchange()
            .expectStatus().isNotFound();

        // 4. Confirm that no committed manifest file was persisted (temp files are tolerated)
        Path manifestsDir = STORAGE_ROOT.resolve("metadata").resolve("manifests");
        if (Files.exists(manifestsDir)) {
            long committedManifests;
            try (var paths = Files.walk(manifestsDir)) {
                committedManifests = paths
                    .filter(Files::isRegularFile)
                    .filter(p -> !p.getFileName().toString().contains(".tmp."))
                    .count();
            }
            assertThat(committedManifests)
                .as("no committed manifest file must exist after a failed upload under %s", manifestsDir)
                .isZero();
        }
    }

    // ── helpers ─────────────────────────────────────────────────────────────

    private static Path createStorageRoot() {
        try {
            return Files.createTempDirectory("magrathea-req-upload-004-");
        } catch (IOException e) {
            throw new UncheckedIOException("Failed to create REQ-UPLOAD-004 storage root", e);
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
