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
import java.util.Properties;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * REQ-UPLOAD-006: Upload commit verifies durable bytes and GetObject exposes the
 * committed integrity identity so clients can detect later disk corruption.
 *
 * <p>After a successful PUT, this test changes a committed whole-object artifact
 * without changing its ETag metadata. The single-pass GET returns the stored bytes
 * and original ETag; a client comparison detects that they no longer match. Periodic
 * at-rest detection and repair is owned by EP-4 scrubbing.
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
    void REQ_UPLOAD_006_clientDetectsCorruptionFromCommittedEtag() throws IOException {
        WebTestClient client = WebTestClient.bindToServer()
            .baseUrl("http://127.0.0.1:" + port)
            .build();

        // 1. Create the bucket
        client.put()
            .uri("/" + BUCKET)
            .exchange()
            .expectStatus().isOk();

        // 2. Upload the object successfully
        var putResult = client.put()
            .uri("/" + BUCKET + "/" + OBJECT_KEY)
            .contentType(MediaType.TEXT_PLAIN)
            .header("x-amz-storage-class", "STANDARD")
            .bodyValue(FIXTURE_CONTENT)
            .exchange()
            .expectStatus().isOk()
            .expectBody(byte[].class)
            .returnResult();
        String committedEtag = putResult.getResponseHeaders().getETag();
        assertThat(committedEtag).isNotBlank();

        Path manifestFile;
        try (var paths = Files.list(STORAGE_ROOT.resolve("metadata/manifests"))) {
            manifestFile = paths.filter(path -> path.getFileName().toString().endsWith(".properties"))
                    .findFirst().orElseThrow();
        }
        Properties manifest = new Properties();
        try (var input = Files.newInputStream(manifestFile)) {
            manifest.load(input);
        }
        String kind = manifest.getProperty("artifact.0.kind");
        String artifactId = manifest.getProperty("artifact.0.artifactId");
        Path namespace = STORAGE_ROOT.resolve("nodes/node-001")
                .resolve("WHOLE_OBJECT".equals(kind) ? "whole-objects" : "chunks");
        Path artifactToCorrupt = namespace.resolve(artifactId);
        assertThat(artifactToCorrupt).isRegularFile();

        byte[] corrupted = Files.readAllBytes(artifactToCorrupt);
        corrupted[0] ^= 0x7f;
        Files.write(artifactToCorrupt, corrupted, StandardOpenOption.TRUNCATE_EXISTING);

        var getResult = client.get()
            .uri("/" + BUCKET + "/" + OBJECT_KEY)
            .exchange()
            .expectStatus().isOk()
            .expectBody(byte[].class)
            .returnResult();
        assertThat(getResult.getResponseHeaders().getETag()).isEqualTo(committedEtag);
        assertThat(getResult.getResponseBody())
            .isNotEqualTo(FIXTURE_CONTENT.getBytes(java.nio.charset.StandardCharsets.UTF_8));
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
