package com.example.magrathea.bootstrap;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.reactive.server.WebTestClient;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.assertj.core.api.Assertions.assertThat;

/** WebTestClient end-to-end evidence for REQ-CAPACITY-001. */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
    properties = {
        "admin.server.port=0",
        "storage.engine.filesystem.node-count=1",
        "storage.engine.filesystem.fault-injection.enospc-on-chunk-write-attempt=2"
    })
@ActiveProfiles("storage-engine")
class StorageEngineEnospcProtectionTest {
    private static final String BUCKET = "capacity-protection-bucket";
    private static final Path ROOT = StorageEngineCapacityProtectionTest.createRoot("enospc");

    @DynamicPropertySource
    static void properties(DynamicPropertyRegistry registry) {
        StorageEngineCapacityProtectionTest.configure(registry, ROOT);
    }

    @LocalServerPort int port;

    @Test
    void REQ_CAPACITY_001_mapsTypedEnospcTo507AndCleansAllUnpublishedState() throws IOException {
        WebTestClient client = WebTestClient.bindToServer()
            .baseUrl("http://127.0.0.1:" + port).build();
        byte[] existing = "existing-object-remains-readable".getBytes(java.nio.charset.StandardCharsets.UTF_8);
        byte[] rejected = new byte[8 * 1024 * 1024];

        client.put().uri("/" + BUCKET).exchange().expectStatus().isOk();
        put(client, "capacity/2026/existing.bin", existing).expectStatus().isOk();
        long committedFilesBefore = committedArtifactCount();

        put(client, "capacity/2026/enospc.bin", rejected)
            .expectStatus().isEqualTo(507)
            .expectBody(String.class)
            .value(xml -> assertThat(xml).contains("<Code>InsufficientStorage</Code>"));

        client.get().uri("/" + BUCKET + "/capacity/2026/enospc.bin")
            .exchange().expectStatus().isNotFound();
        client.get().uri("/" + BUCKET + "/capacity/2026/existing.bin")
            .exchange().expectStatus().isOk().expectBody()
            .consumeWith(response -> assertThat(response.getResponseBody()).isEqualTo(existing));

        assertThat(committedArtifactCount()).isEqualTo(committedFilesBefore);
        try (var paths = Files.walk(ROOT)) {
            assertThat(paths.filter(Files::isRegularFile)
                .map(path -> path.getFileName().toString())
                .filter(name -> name.contains(".tmp.")))
                .as("typed ENOSPC cleanup must remove every temporary artifact")
                .isEmpty();
        }
        Path references = ROOT.resolve("metadata/s3-object-references");
        if (Files.exists(references)) {
            try (var paths = Files.walk(references)) {
                assertThat(paths.filter(Files::isRegularFile)
                    .map(path -> {
                        try { return Files.readString(path); }
                        catch (IOException error) { throw new java.io.UncheckedIOException(error); }
                    }).noneMatch(content -> content.contains("capacity/2026/enospc.bin"))).isTrue();
            }
        }
    }

    private static WebTestClient.ResponseSpec put(WebTestClient client, String key, byte[] content) {
        return client.put().uri("/" + BUCKET + "/" + key)
            .header("x-amz-storage-class", "PLAIN")
            .header("Content-Length", Long.toString(content.length))
            .bodyValue(content).exchange();
    }

    private long committedArtifactCount() throws IOException {
        try (var paths = Files.walk(ROOT)) {
            return paths.filter(Files::isRegularFile)
                .map(path -> path.getFileName().toString())
                .filter(name -> !name.contains(".tmp."))
                .count();
        }
    }
}
