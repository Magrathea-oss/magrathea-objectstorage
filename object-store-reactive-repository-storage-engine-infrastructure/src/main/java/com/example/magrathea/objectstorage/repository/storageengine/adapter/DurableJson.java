package com.example.magrathea.objectstorage.repository.storageengine.adapter;

import com.fasterxml.jackson.annotation.JsonAutoDetect;
import tools.jackson.databind.DeserializationFeature;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.json.JsonMapper;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.Base64;
import java.util.Comparator;
import java.util.Optional;

/**
 * Shared JSON persistence helpers for the storage-engine repository adapters.
 *
 * <p>All durable metadata written by this package (bucket registry, multipart
 * upload state, per-object configuration) uses the same crash-safe commit
 * discipline as {@link S3ObjectManifestReferenceStore}: the record is written
 * to a temp file in the target directory and atomically renamed over the final
 * path, so readers and restart recovery never observe a torn document.</p>
 */
final class DurableJson {

    private static final ObjectMapper MAPPER = JsonMapper.builder()
        .disable(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES)
        .changeDefaultVisibility(vc -> vc
            .withFieldVisibility(JsonAutoDetect.Visibility.ANY)
            .withGetterVisibility(JsonAutoDetect.Visibility.NONE)
            .withIsGetterVisibility(JsonAutoDetect.Visibility.NONE)
            .withSetterVisibility(JsonAutoDetect.Visibility.NONE)
            .withCreatorVisibility(JsonAutoDetect.Visibility.ANY))
        .build();

    private DurableJson() {
    }

    static ObjectMapper mapper() {
        return MAPPER;
    }

    /** Crash-safe write: temp file in the same directory + atomic rename. */
    static void writeAtomic(Path path, Object value) {
        try {
            Files.createDirectories(path.getParent());
            Path temp = Files.createTempFile(
                path.getParent(), path.getFileName().toString() + ".", ".tmp");
            try {
                Files.write(temp, MAPPER.writeValueAsBytes(value));
                try {
                    Files.move(temp, path,
                        StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING);
                } catch (AtomicMoveNotSupportedException e) {
                    Files.move(temp, path, StandardCopyOption.REPLACE_EXISTING);
                }
            } finally {
                Files.deleteIfExists(temp);
            }
        } catch (IOException e) {
            throw new UncheckedIOException("Failed to write durable metadata: " + path, e);
        }
    }

    static <T> Optional<T> read(Path path, Class<T> type) {
        if (!Files.exists(path)) {
            return Optional.empty();
        }
        try {
            return Optional.of(MAPPER.readValue(Files.readAllBytes(path), type));
        } catch (IOException e) {
            throw new UncheckedIOException("Failed to read durable metadata: " + path, e);
        }
    }

    static void delete(Path path) {
        try {
            Files.deleteIfExists(path);
        } catch (IOException e) {
            throw new UncheckedIOException("Failed to delete durable metadata: " + path, e);
        }
    }

    /** Recursively deletes all persisted documents below the given root (test reset). */
    static void wipeDirectory(Path root) {
        if (!Files.isDirectory(root)) {
            return;
        }
        try (var paths = Files.walk(root)) {
            paths.sorted(Comparator.reverseOrder())
                .filter(path -> !path.equals(root))
                .forEach(path -> {
                    try {
                        Files.deleteIfExists(path);
                    } catch (IOException e) {
                        throw new UncheckedIOException("Failed to wipe durable metadata: " + path, e);
                    }
                });
        } catch (IOException e) {
            throw new UncheckedIOException("Failed to wipe durable metadata under " + root, e);
        }
    }

    static String encode(String value) {
        return Base64.getUrlEncoder().withoutPadding()
            .encodeToString(value.getBytes(StandardCharsets.UTF_8));
    }

    static Path resolveStorageRoot(String configuredRoot) {
        if (configuredRoot == null || configuredRoot.isBlank()) {
            return Path.of(System.getProperty("java.io.tmpdir"),
                "magrathea-objectstorage", "storage-engine");
        }
        return Path.of(configuredRoot);
    }
}
