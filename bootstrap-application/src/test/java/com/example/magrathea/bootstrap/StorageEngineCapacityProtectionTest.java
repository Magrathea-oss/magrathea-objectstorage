package com.example.magrathea.bootstrap;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.reactive.server.WebTestClient;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;

import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/** WebTestClient end-to-end evidence for REQ-QUOTA-001 and REQ-QUOTA-002. */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
    properties = {"admin.server.port=0", "storage.engine.filesystem.node-count=1"})
@ActiveProfiles("storage-engine")
class StorageEngineCapacityProtectionTest {
    private static final String BUCKET = "capacity-protection-bucket";
    private static final Path ROOT = createRoot("quota");

    @DynamicPropertySource
    static void properties(DynamicPropertyRegistry registry) {
        configure(registry, ROOT);
    }

    @LocalServerPort int port;

    @Test
    void REQ_QUOTA_001_and_REQ_QUOTA_002_enforceDurableAtomicLogicalReservations() {
        WebTestClient client = client();
        byte[] existing = "existing-object".getBytes(java.nio.charset.StandardCharsets.UTF_8);
        byte[] payload = new byte[2 * 1024 * 1024];
        java.util.Arrays.fill(payload, (byte) 0x5a);

        client.put().uri("/" + BUCKET).exchange().expectStatus().isOk();
        put(client, "capacity/2026/existing.bin", existing).expectStatus().isOk();

        long quota = existing.length + payload.length;
        client.put().uri("/admin/buckets/" + BUCKET + "/quota")
            .bodyValue(java.util.Map.of("quotaBytes", quota))
            .exchange().expectStatus().isOk()
            .expectBody().jsonPath("$.usedBytes").isEqualTo(existing.length)
            .jsonPath("$.reservedBytes").isEqualTo(0);

        WebClient concurrent = WebClient.builder().baseUrl("http://127.0.0.1:" + port).build();
        Mono<Integer> first = upload(concurrent, "capacity/2026/concurrent-a.bin", payload);
        Mono<Integer> second = upload(concurrent, "capacity/2026/concurrent-b.bin", payload);
        StepVerifier.create(Mono.zip(first, second))
            .assertNext(statuses -> assertThat(List.of(statuses.getT1(), statuses.getT2()))
                .containsExactlyInAnyOrder(200, 507))
            .verifyComplete();

        client.get().uri("/admin/buckets/" + BUCKET + "/capacity")
            .exchange().expectStatus().isOk()
            .expectBody()
            .jsonPath("$.usedBytes").isEqualTo(quota)
            .jsonPath("$.reservedBytes").isEqualTo(0)
            .jsonPath("$.quotaBytes").isEqualTo(quota)
            .jsonPath("$.rejectedReservations").isEqualTo(1)
            .jsonPath("$.lastRejectedBytes").isEqualTo(payload.length);

        client.get().uri("/" + BUCKET + "/capacity/2026/existing.bin")
            .exchange().expectStatus().isOk().expectBody()
            .consumeWith(response -> assertThat(response.getResponseBody()).isEqualTo(existing));

        var restarted = new com.example.magrathea.storageengine.infrastructure.filesystem.FileSystemBucketCapacityStore(ROOT);
        StepVerifier.create(restarted.capacity(BUCKET))
            .assertNext(state -> {
                assertThat(state.usedBytes()).isEqualTo(quota);
                assertThat(state.reservedBytes()).isZero();
                assertThat(state.quotaBytes()).isEqualTo(quota);
            }).verifyComplete();
    }

    private WebTestClient client() {
        return WebTestClient.bindToServer().baseUrl("http://127.0.0.1:" + port).build();
    }

    private static WebTestClient.ResponseSpec put(WebTestClient client, String key, byte[] content) {
        return client.put().uri("/" + BUCKET + "/" + key)
            .header("x-amz-storage-class", "PLAIN")
            .header("Content-Length", Long.toString(content.length))
            .bodyValue(content).exchange();
    }

    private static Mono<Integer> upload(WebClient client, String key, byte[] content) {
        return client.put().uri("/" + BUCKET + "/" + key)
            .header("x-amz-storage-class", "PLAIN")
            .header("Content-Length", Long.toString(content.length))
            .bodyValue(content).exchangeToMono(response -> Mono.just(response.statusCode().value()));
    }

    static Path createRoot(String name) {
        try { return Files.createTempDirectory("magrathea-ep4-" + name + "-"); }
        catch (IOException error) { throw new UncheckedIOException(error); }
    }

    static void configure(DynamicPropertyRegistry registry, Path root) {
        registry.add("storage.engine.filesystem.root", root::toString);
        try {
            Path catalog = Files.createTempDirectory("magrathea-ep4-catalog-");
            registry.add("storage.engine.policies.dir", () -> extract(catalog, "storage-policies", "plain-streaming.yaml").toString());
            registry.add("storage.engine.devices.dir", () -> extract(catalog, "storage-devices", "local-disk-0.yaml").toString());
            registry.add("storage.engine.disksets.dir", () -> extract(catalog, "disk-sets", "default-diskset.yaml").toString());
        } catch (IOException error) { throw new UncheckedIOException(error); }
    }

    private static Path extract(Path root, String directory, String file) {
        try {
            Path target = root.resolve(directory);
            Files.createDirectories(target);
            InputStream resource = Thread.currentThread().getContextClassLoader()
                .getResourceAsStream(directory + "/" + file);
            if (resource == null && "plain-streaming.yaml".equals(file)) {
                resource = new java.io.ByteArrayInputStream(("""
                    policyId: plain-streaming
                    version: "1.0"
                    storageClassId: PLAIN
                    replication:
                      factor: 1
                    """).getBytes(java.nio.charset.StandardCharsets.UTF_8));
            }
            try (InputStream input = resource) {
                if (input == null) throw new IOException("Missing resource " + directory + "/" + file);
                Files.write(target.resolve(file), input.readAllBytes());
            }
            return target;
        } catch (IOException error) { throw new UncheckedIOException(error); }
    }
}
