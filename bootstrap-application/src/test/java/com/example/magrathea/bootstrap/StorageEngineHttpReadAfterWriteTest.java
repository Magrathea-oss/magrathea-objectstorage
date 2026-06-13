package com.example.magrathea.bootstrap;

import com.example.magrathea.bootstrap.ObjectStoreBackendStatus.Backend;
import com.example.magrathea.objectstore.reactive.repository.application.BucketCommandRepository;
import com.example.magrathea.objectstore.reactive.repository.application.BucketQueryRepository;
import com.example.magrathea.objectstore.reactive.repository.application.S3ObjectCommandRepository;
import com.example.magrathea.objectstore.reactive.repository.application.S3ObjectQueryRepository;
import com.example.magrathea.objectstorage.repository.storageengine.adapter.StorageEngineReactiveBucketRepository;
import com.example.magrathea.objectstorage.repository.storageengine.adapter.StorageEngineReactiveS3ObjectRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.context.ApplicationContext;
import org.springframework.core.io.buffer.DataBuffer;
import org.springframework.core.io.buffer.DataBufferUtils;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.reactive.server.WebTestClient;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest(
    webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
    properties = {
        "admin.server.port=0",
        "storage.engine.filesystem.node-count=1"
    }
)
@ActiveProfiles("storage-engine")
class StorageEngineHttpReadAfterWriteTest {

    private static final String BUCKET = "req-upload-read-after-write-bucket";
    private static final String OBJECT_KEY = "documents/2026/read-after-write/object.txt";
    private static final String FIXTURE_CONTENT = "Hello durable Magrathea!";
    private static final Path STORAGE_ROOT = createStorageRoot();

    @DynamicPropertySource
    static void storageEngineProperties(DynamicPropertyRegistry registry) {
        registry.add("storage.engine.filesystem.root", () -> STORAGE_ROOT.toString());
    }

    @LocalServerPort
    private int port;

    @Autowired
    private ApplicationContext context;

    @Autowired
    private ObjectStoreBackendStatus backendStatus;

    @Test
    void REQ_UPLOAD_005_successfulPutObjectIsImmediatelyReadableFromFilesystemBackedStorage() {
        assertThat(backendStatus.backend()).isEqualTo(Backend.STORAGE_ENGINE);
        assertSingleBean(S3ObjectCommandRepository.class, StorageEngineReactiveS3ObjectRepository.class);
        assertSingleBean(S3ObjectQueryRepository.class, StorageEngineReactiveS3ObjectRepository.class);
        assertSingleBean(BucketCommandRepository.class, StorageEngineReactiveBucketRepository.class);
        assertSingleBean(BucketQueryRepository.class, StorageEngineReactiveBucketRepository.class);

        WebTestClient client = WebTestClient.bindToServer()
            .baseUrl("http://127.0.0.1:" + port)
            .build();

        client.put()
            .uri(slashPreservingPath(BUCKET))
            .exchange()
            .expectStatus().isOk();

        client.put()
            .uri(slashPreservingPath(BUCKET, OBJECT_KEY))
            .contentType(MediaType.TEXT_PLAIN)
            .header("x-amz-storage-class", "STANDARD")
            .bodyValue(FIXTURE_CONTENT)
            .exchange()
            .expectStatus().isOk();

        String downloaded = client.get()
            .uri(slashPreservingPath(BUCKET, OBJECT_KEY))
            .exchange()
            .expectStatus().isOk()
            .returnResult(DataBuffer.class)
            .getResponseBody()
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

        assertThat(downloaded).isEqualTo(FIXTURE_CONTENT);
        Path manifestsRoot = STORAGE_ROOT.resolve("metadata").resolve("manifests");
        Path objectsRoot = STORAGE_ROOT.resolve("metadata").resolve("objects");
        Path chunksRoot = STORAGE_ROOT.resolve("nodes").resolve("node-001").resolve("chunks");
        assertThat(manifestsRoot).isDirectory();
        assertThat(objectsRoot).isDirectory();
        assertThat(chunksRoot).isDirectory();
        assertThat(hasRegularFileEnding(manifestsRoot, ".properties")).isTrue();
        assertThat(hasRegularFileEnding(objectsRoot, ".json")).isTrue();
        assertThat(hasAnyRegularFile(chunksRoot)).isTrue();
    }

    private static String slashPreservingPath(String bucket) {
        return "/" + bucket;
    }

    private static String slashPreservingPath(String bucket, String key) {
        return "/" + bucket + "/" + key;
    }

    private static Path createStorageRoot() {
        try {
            return Files.createTempDirectory("magrathea-req-upload-005-");
        } catch (IOException e) {
            throw new UncheckedIOException("Failed to create REQ-UPLOAD-005 storage root", e);
        }
    }

    private static boolean hasRegularFileEnding(Path root, String suffix) {
        try (var paths = Files.walk(root)) {
            return paths.anyMatch(path -> Files.isRegularFile(path)
                && path.getFileName().toString().endsWith(suffix));
        } catch (IOException e) {
            throw new UncheckedIOException("Failed to inspect storage-engine filesystem artifacts", e);
        }
    }

    private static boolean hasAnyRegularFile(Path root) {
        try (var paths = Files.walk(root)) {
            return paths.anyMatch(Files::isRegularFile);
        } catch (IOException e) {
            throw new UncheckedIOException("Failed to inspect storage-engine chunk artifacts", e);
        }
    }

    private <T> void assertSingleBean(Class<T> portType, Class<?> implementationType) {
        var beans = context.getBeansOfType(portType);
        assertThat(beans).hasSize(1);
        assertThat(beans.values().iterator().next()).isInstanceOf(implementationType);
    }
}
