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

/**
 * Section E Admin API integration test.
 *
 * <p>Proves that the Admin API endpoints ({@code /admin/storage-policies},
 * {@code /admin/storage-devices}, {@code /admin/disk-sets}) serve real YAML-backed
 * catalog data when the {@code storage-engine} profile is active, rather than returning
 * 503 stub responses as they would without the profile.
 *
 * <p>When the {@code storage-engine} profile is active, {@code StorageEngineYamlCatalogConfig}
 * registers the real {@code YamlStoragePolicyCatalog}, {@code YamlStorageDeviceCatalog},
 * and {@code YamlDiskSetCatalog} beans. The {@code AdminRouter} injects those catalogs via
 * {@code ObjectProvider} and serves their content through the {@code /admin/**} routes,
 * which are auto-registered as Spring WebFlux {@code RouterFunction} beans on the main
 * server port.
 *
 * <p>The catalog YAML files are extracted from the classpath to a temp directory before
 * the Spring context starts because {@code YamlStoragePolicyCatalog} only handles
 * {@code file:} protocol URLs; it silently skips {@code jar:} URLs from installed JARs.
 */
@SpringBootTest(
    webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
    properties = {
        "admin.server.port=0",
        "storage.engine.filesystem.node-count=1"
    }
)
@ActiveProfiles("storage-engine")
class StorageEngineAdminApiIntegrationTest {

    private static final Path STORAGE_ROOT = createStorageRoot();

    @DynamicPropertySource
    static void storageEngineProperties(DynamicPropertyRegistry registry) {
        registry.add("storage.engine.filesystem.root", () -> STORAGE_ROOT.toString());
        try {
            Path catalogRoot = Files.createTempDirectory("magrathea-admin-api-catalog-");
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
    void adminApiServesRealYamlCatalogData() {
        WebTestClient client = WebTestClient.bindToServer()
            .baseUrl("http://127.0.0.1:" + port)
            .build();

        // 1. Health endpoint confirms Admin API is running
        client.get().uri("/admin/health")
            .exchange()
            .expectStatus().isOk()
            .expectHeader().contentType(MediaType.APPLICATION_JSON)
            .expectBody()
            .jsonPath("$.status").isEqualTo("ok");

        // 2. List storage policies — real YAML: count=1, storageClassId=STANDARD, EC 4+2
        client.get().uri("/admin/storage-policies")
            .exchange()
            .expectStatus().isOk()
            .expectHeader().contentType(MediaType.APPLICATION_JSON)
            .expectBody()
            .jsonPath("$.count").isEqualTo(1)
            .jsonPath("$.storagePolicies[0].storageClassId").isEqualTo("STANDARD")
            .jsonPath("$.storagePolicies[0].erasureCoding.dataBlocks").isEqualTo(4)
            .jsonPath("$.storagePolicies[0].erasureCoding.parityBlocks").isEqualTo(2);

        // 3. Get single storage policy by catalog ID
        client.get().uri("/admin/storage-policies/minio-standard")
            .exchange()
            .expectStatus().isOk()
            .expectHeader().contentType(MediaType.APPLICATION_JSON)
            .expectBody()
            .jsonPath("$.storageClassId").isEqualTo("STANDARD")
            .jsonPath("$.erasureCoding.dataBlocks").isEqualTo(4);

        // 4. List storage devices — real YAML: local-disk-0 at /data/local/disk-0
        client.get().uri("/admin/storage-devices")
            .exchange()
            .expectStatus().isOk()
            .expectHeader().contentType(MediaType.APPLICATION_JSON)
            .expectBody()
            .jsonPath("$.count").isEqualTo(1)
            .jsonPath("$.storageDevices[0].id").isEqualTo("local-disk-0")
            .jsonPath("$.storageDevices[0].storagePath").isEqualTo("/data/local/disk-0");

        // 5. List disk sets — real YAML: default-diskset
        client.get().uri("/admin/disk-sets")
            .exchange()
            .expectStatus().isOk()
            .expectHeader().contentType(MediaType.APPLICATION_JSON)
            .expectBody()
            .jsonPath("$.count").isEqualTo(1)
            .jsonPath("$.diskSets[0].name").isEqualTo("default-diskset");

        // 6. Validate a storage policy — MINIO_STANDARD is a valid StorageClassId string
        client.post().uri("/admin/storage-policies/validate")
            .contentType(MediaType.APPLICATION_JSON)
            .bodyValue("""
                {"storageClassId":"MINIO_STANDARD",\
                "erasureCoding":{"dataBlocks":4,"parityBlocks":2},\
                "replication":{"factor":1}}
                """)
            .exchange()
            .expectStatus().isOk()
            .expectBody()
            .jsonPath("$.valid").isEqualTo(true);

        // 7. POST to /admin/storage-policies — catalog is read-only: must return 405
        client.post().uri("/admin/storage-policies")
            .contentType(MediaType.APPLICATION_JSON)
            .bodyValue("{}")
            .exchange()
            .expectStatus().isEqualTo(405);
    }

    // ── helpers ─────────────────────────────────────────────────────────────

    private static Path createStorageRoot() {
        try {
            return Files.createTempDirectory("magrathea-admin-api-");
        } catch (IOException e) {
            throw new UncheckedIOException("Failed to create Admin API integration test storage root", e);
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
