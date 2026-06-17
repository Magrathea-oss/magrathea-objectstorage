package com.example.magrathea.bootstrap;

import com.example.magrathea.storageengine.application.pipeline.StorageEvent;
import com.example.magrathea.storageengine.application.pipeline.StorageEventListener;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import io.opentelemetry.api.trace.Tracer;
import io.opentelemetry.sdk.testing.exporter.InMemorySpanExporter;
import io.opentelemetry.sdk.trace.SdkTracerProvider;
import io.opentelemetry.sdk.trace.data.SpanData;
import io.opentelemetry.sdk.trace.export.SimpleSpanProcessor;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.core.io.buffer.DataBuffer;
import org.springframework.core.io.buffer.DataBufferUtils;
import org.springframework.test.context.DynamicPropertyRegistry;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.Comparator;
import java.util.HexFormat;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;

final class Phase4ObservabilityTestSupport {

    private Phase4ObservabilityTestSupport() {
    }

    static Path createStorageRoot(String prefix) {
        try {
            return Files.createTempDirectory(prefix);
        } catch (IOException e) {
            throw new UncheckedIOException("Failed to create storage root", e);
        }
    }

    static void registerStorageEngineProperties(DynamicPropertyRegistry registry, Path storageRoot, String catalogPrefix) {
        registry.add("storage.engine.filesystem.root", () -> storageRoot.toString());
        try {
            Path catalogRoot = Files.createTempDirectory(catalogPrefix);
            registry.add("storage.engine.policies.dir",
                    () -> extractCatalogDir(catalogRoot, "storage-policies", List.of("minio-standard.yaml")).toString());
            registry.add("storage.engine.devices.dir",
                    () -> extractCatalogDir(catalogRoot, "storage-devices", List.of("local-disk-0.yaml")).toString());
            registry.add("storage.engine.disksets.dir",
                    () -> extractCatalogDir(catalogRoot, "disk-sets", List.of("default-diskset.yaml")).toString());
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    static void cleanStorageRoot(Path storageRoot) {
        try {
            if (Files.exists(storageRoot)) {
                try (var paths = Files.walk(storageRoot)) {
                    paths.sorted(Comparator.reverseOrder())
                            .filter(path -> !path.equals(storageRoot))
                            .forEach(path -> {
                                try {
                                    Files.deleteIfExists(path);
                                } catch (IOException e) {
                                    throw new UncheckedIOException(e);
                                }
                            });
                }
            }
            Files.createDirectories(storageRoot.resolve("nodes/node-001/chunks"));
            Files.createDirectories(storageRoot.resolve("metadata/manifests"));
            Files.createDirectories(storageRoot.resolve("metadata/objects"));
            Files.createDirectories(storageRoot.resolve("metadata/s3-object-references"));
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    static String drain(Flux<DataBuffer> content) {
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

    static String path(String bucket) {
        return "/" + bucket;
    }

    static String path(String bucket, String key) {
        return "/" + bucket + "/" + key;
    }

    static Path firstCommittedChunk(Path storageRoot) {
        Path chunksRoot = storageRoot.resolve("nodes");
        try (var paths = Files.walk(chunksRoot)) {
            return paths.filter(Files::isRegularFile)
                    .filter(path -> !path.getFileName().toString().endsWith(".sha256"))
                    .filter(path -> !path.getFileName().toString().contains(".tmp."))
                    .findFirst()
                    .orElseThrow(() -> new AssertionError("No committed chunk found under " + chunksRoot));
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    static String sha256Hex(String content) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            return HexFormat.of().formatHex(digest.digest(content.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 unavailable", e);
        }
    }

    static List<SpanData> finishedSpans(InMemorySpanExporter exporter) {
        return exporter.getFinishedSpanItems();
    }

    private static Path extractCatalogDir(Path catalogRoot, String classpathDir, List<String> fileNames) {
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

    static final class RecordingStorageEventListener implements StorageEventListener {
        private final CopyOnWriteArrayList<StorageEvent> events = new CopyOnWriteArrayList<>();

        @Override
        public Mono<Void> onEvent(StorageEvent event) {
            events.add(event);
            return Mono.empty();
        }

        List<StorageEvent> events() {
            return List.copyOf(events);
        }

        void clear() {
            events.clear();
        }
    }

    @TestConfiguration
    static class ObservabilityProbeConfig {
        @Bean
        SimpleMeterRegistry meterRegistry() {
            return new SimpleMeterRegistry();
        }

        @Bean
        RecordingStorageEventListener recordingStorageEventListener() {
            return new RecordingStorageEventListener();
        }

        @Bean
        InMemorySpanExporter inMemorySpanExporter() {
            return InMemorySpanExporter.create();
        }

        @Bean
        SdkTracerProvider sdkTracerProvider(InMemorySpanExporter exporter) {
            return SdkTracerProvider.builder()
                    .addSpanProcessor(SimpleSpanProcessor.create(exporter))
                    .build();
        }

        @Bean
        Tracer storageEngineTracer(SdkTracerProvider tracerProvider) {
            return tracerProvider.get("magrathea-storage-engine-test");
        }
    }
}
