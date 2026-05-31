package com.example.magrathea.storageengine.infrastructure.filesystem;

import com.example.magrathea.storageengine.application.port.ObjectManifestRepository;
import com.example.magrathea.storageengine.domain.valueobject.ManifestId;
import com.example.magrathea.storageengine.domain.valueobject.ObjectManifest;
import reactor.core.publisher.Mono;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;

/**
 * Filesystem-backed manifest repository.
 * Serializes ObjectManifest to JSON in manifestsRoot/{manifestId}.json.
 */
public class FileSystemManifestRepository implements ObjectManifestRepository {

    private final Path manifestsRoot;

    public FileSystemManifestRepository(Path manifestsRoot) {
        this.manifestsRoot = java.util.Objects.requireNonNull(manifestsRoot, "manifestsRoot must not be null");
        try {
            Files.createDirectories(manifestsRoot);
        } catch (IOException e) {
            throw new UncheckedIOException("Failed to create manifests directory: " + manifestsRoot, e);
        }
    }

    @Override
    public Mono<Void> save(ObjectManifest manifest) {
        return Mono.fromRunnable(() -> {
            try {
                Path manifestFile = manifestsRoot.resolve(manifest.manifestId().value().toString() + ".json");
                String json = serializeToJson(manifest);
                Files.writeString(manifestFile, json,
                        StandardOpenOption.CREATE, StandardOpenOption.TRUNCATE_EXISTING);
            } catch (IOException e) {
                throw new UncheckedIOException("Failed to save manifest", e);
            }
        });
    }

    @Override
    public Mono<ObjectManifest> findBy(ManifestId manifestId) {
        return Mono.fromCallable(() -> {
            Path manifestFile = manifestsRoot.resolve(manifestId.value().toString() + ".json");
            if (!Files.exists(manifestFile)) {
                throw new java.util.NoSuchElementException("Manifest not found: " + manifestId.value());
            }
            String json = Files.readString(manifestFile);
            return deserializeFromJson(json);
        });
    }

    /**
     * Simple JSON serialization using StringBuilder (no Jackson dependency).
     * In production, this would use Jackson or a similar library.
     */
    private String serializeToJson(ObjectManifest manifest) {
        StringBuilder sb = new StringBuilder();
        sb.append("{\n");
        sb.append("  \"manifestId\": \"").append(escapeJson(manifest.manifestId().value().toString())).append("\",\n");
        sb.append("  \"objectId\": \"").append(escapeJson(manifest.objectId().value())).append("\",\n");
        sb.append("  \"versionId\": \"").append(escapeJson(manifest.versionId().value())).append("\",\n");
        sb.append("  \"storageClassId\": \"").append(escapeJson(manifest.storageClassId().value())).append("\",\n");
        sb.append("  \"chunkCount\": ").append(manifest.chunkCount()).append(",\n");
        sb.append("  \"totalOriginalSize\": ").append(manifest.totalOriginalSize()).append(",\n");
        sb.append("  \"totalStoredSize\": ").append(manifest.totalStoredSize()).append("\n");
        sb.append("}\n");
        return sb.toString();
    }

    private ObjectManifest deserializeFromJson(String json) {
        // Simplified deserialization — in production, use Jackson
        throw new UnsupportedOperationException(
                "JSON deserialization requires a proper JSON library. " +
                "For now, this is a placeholder.");
    }

    private String escapeJson(String value) {
        return value.replace("\\", "\\\\")
                .replace("\"", "\\\"")
                .replace("\n", "\\n")
                .replace("\r", "\\r")
                .replace("\t", "\\t");
    }
}
