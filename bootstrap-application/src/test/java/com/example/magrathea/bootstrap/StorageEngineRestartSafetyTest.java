package com.example.magrathea.bootstrap;

import org.junit.jupiter.api.Test;
import org.springframework.boot.builder.SpringApplicationBuilder;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.core.env.Environment;
import org.springframework.core.io.buffer.DataBuffer;
import org.springframework.core.io.buffer.DataBufferUtils;
import org.springframework.http.MediaType;
import org.springframework.test.web.reactive.server.WebTestClient;

import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * REQ-UPLOAD-001: Object body survives a full Spring Boot context lifecycle restart.
 *
 * <p>Uses {@link SpringApplicationBuilder} to programmatically start and stop two
 * independent application contexts sharing the same storage root, proving that a
 * previously PUT object is retrievable via HTTP GET in the second context.
 *
 * <p>The test extracts the bundled storage-engine YAML catalog files (storage policies,
 * storage devices, disk sets) from the classpath to a temp directory so that the
 * {@code YamlStoragePolicyCatalog} can load them from the filesystem. This is necessary
 * because the classpath scanner used by the catalog only handles {@code file:} protocol
 * URLs, not {@code jar:} URLs that arise when running against installed module JARs.
 */
class StorageEngineRestartSafetyTest {

    private static final String BUCKET = "req-upload-001-restart-safety-bucket";
    private static final String OBJECT_KEY = "documents/2026/restart-safety/object.txt";
    private static final String FIXTURE_CONTENT = "Hello durable Magrathea!";

    @Test
    void REQ_UPLOAD_001_objectBodySurvivesSpringContextRestart() throws IOException {
        Path tempRoot = Files.createTempDirectory("magrathea-req-upload-001-");
        Path catalogRoot = Files.createTempDirectory("magrathea-req-upload-001-catalog-");

        Path policiesDir = extractCatalogDir(catalogRoot, "storage-policies",
            List.of("minio-standard.yaml"));
        Path devicesDir = extractCatalogDir(catalogRoot, "storage-devices",
            List.of("local-disk-0.yaml"));
        Path disksetsDir = extractCatalogDir(catalogRoot, "disk-sets",
            List.of("default-diskset.yaml"));

        ConfigurableApplicationContext firstContext = null;
        ConfigurableApplicationContext secondContext = null;
        try {
            // ── First context: PUT bucket + object ──────────────────────────────
            firstContext = new SpringApplicationBuilder(MagratheaApplication.class)
                .run(
                    "--spring.profiles.active=storage-engine",
                    "--server.port=0",
                    "--admin.server.port=0",
                    "--storage.engine.filesystem.root=" + tempRoot,
                    "--storage.engine.filesystem.node-count=1",
                    "--storage.engine.policies.dir=" + policiesDir,
                    "--storage.engine.devices.dir=" + devicesDir,
                    "--storage.engine.disksets.dir=" + disksetsDir
                );

            int firstPort = firstContext.getBean(Environment.class)
                .getRequiredProperty("local.server.port", Integer.class);

            WebTestClient firstClient = WebTestClient.bindToServer()
                .baseUrl("http://127.0.0.1:" + firstPort)
                .build();

            firstClient.put()
                .uri("/" + BUCKET)
                .exchange()
                .expectStatus().isOk();

            firstClient.put()
                .uri("/" + BUCKET + "/" + OBJECT_KEY)
                .contentType(MediaType.TEXT_PLAIN)
                .header("x-amz-storage-class", "STANDARD")
                .bodyValue(FIXTURE_CONTENT)
                .exchange()
                .expectStatus().isOk();

            firstContext.close();
            firstContext = null;

            // ── Second context: GET object from same storage root ────────────────
            secondContext = new SpringApplicationBuilder(MagratheaApplication.class)
                .run(
                    "--spring.profiles.active=storage-engine",
                    "--server.port=0",
                    "--admin.server.port=0",
                    "--storage.engine.filesystem.root=" + tempRoot,
                    "--storage.engine.filesystem.node-count=1",
                    "--storage.engine.policies.dir=" + policiesDir,
                    "--storage.engine.devices.dir=" + devicesDir,
                    "--storage.engine.disksets.dir=" + disksetsDir
                );

            int secondPort = secondContext.getBean(Environment.class)
                .getRequiredProperty("local.server.port", Integer.class);

            WebTestClient secondClient = WebTestClient.bindToServer()
                .baseUrl("http://127.0.0.1:" + secondPort)
                .build();

            // Bucket namespace is not persisted across restarts; re-create it so
            // the service layer can resolve bucket -> object references from the
            // durable filesystem manifest store.
            secondClient.put()
                .uri("/" + BUCKET)
                .exchange()
                .expectStatus().isOk();

            String recovered = drain(secondClient.get()
                .uri("/" + BUCKET + "/" + OBJECT_KEY)
                .exchange()
                .expectStatus().isOk()
                .returnResult(DataBuffer.class)
                .getResponseBody());

            assertThat(recovered).isEqualTo(FIXTURE_CONTENT);

        } finally {
            if (firstContext != null) {
                firstContext.close();
            }
            if (secondContext != null) {
                secondContext.close();
            }
        }
    }

    /**
     * Extracts named YAML files from the classpath subdirectory {@code classpathDir}
     * into a temp directory under {@code catalogRoot}, and returns the temp directory path.
     *
     * <p>This workaround is needed because {@code YamlStoragePolicyCatalog} only handles
     * {@code file:} protocol URLs when scanning classpath directories. When the module
     * resources are inside a JAR (as happens for installed Maven dependencies), the scanner
     * silently skips them. By extracting resources to the filesystem first and pointing the
     * catalog at the filesystem directory, loading works regardless of packaging.
     */
    private static Path extractCatalogDir(Path catalogRoot, String classpathDir,
                                          List<String> fileNames) throws IOException {
        Path dir = catalogRoot.resolve(classpathDir);
        Files.createDirectories(dir);
        ClassLoader cl = Thread.currentThread().getContextClassLoader();
        for (String fileName : fileNames) {
            String resourcePath = classpathDir + "/" + fileName;
            try (InputStream in = cl.getResourceAsStream(resourcePath)) {
                if (in == null) {
                    throw new UncheckedIOException(new IOException(
                        "Classpath resource not found: " + resourcePath));
                }
                Files.write(dir.resolve(fileName), in.readAllBytes());
            }
        }
        return dir;
    }

    private static String drain(reactor.core.publisher.Flux<DataBuffer> content) {
        return content
            .map(buffer -> {
                try {
                    return buffer.toString(StandardCharsets.UTF_8);
                } finally {
                    DataBufferUtils.release(buffer);
                }
            })
            .collectList()
            .map(parts -> String.join("", parts))
            .block();
    }
}
