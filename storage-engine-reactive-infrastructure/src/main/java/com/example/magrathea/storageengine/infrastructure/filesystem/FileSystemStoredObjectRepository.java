package com.example.magrathea.storageengine.infrastructure.filesystem;

import com.example.magrathea.storageengine.application.port.StoredObjectRepository;
import com.example.magrathea.storageengine.domain.aggregate.StoredObject;
import com.example.magrathea.storageengine.domain.valueobject.ObjectId;
import com.example.magrathea.storageengine.domain.valueobject.VersionId;
import reactor.core.publisher.Mono;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;

/**
 * Filesystem-backed StoredObject repository.
 * Layout: metadataRoot/objects/{objectId}/{versionId}.json
 */
public class FileSystemStoredObjectRepository implements StoredObjectRepository {

    private final Path metadataRoot;

    public FileSystemStoredObjectRepository(Path metadataRoot) {
        this.metadataRoot = java.util.Objects.requireNonNull(metadataRoot, "metadataRoot must not be null");
        try {
            Files.createDirectories(metadataRoot.resolve("objects"));
        } catch (IOException e) {
            throw new UncheckedIOException("Failed to create objects directory", e);
        }
    }

    @Override
    public Mono<Void> save(StoredObject storedObject) {
        return Mono.fromRunnable(() -> {
            try {
                Path objectDir = metadataRoot.resolve("objects")
                        .resolve(storedObject.objectId().value().replace("/", "_"));
                Files.createDirectories(objectDir);
                Path versionFile = objectDir.resolve(storedObject.versionId().value() + ".json");
                String json = serializeToJson(storedObject);
                Files.writeString(versionFile, json,
                        StandardOpenOption.CREATE, StandardOpenOption.TRUNCATE_EXISTING);
            } catch (IOException e) {
                throw new UncheckedIOException("Failed to save StoredObject", e);
            }
        });
    }

    @Override
    public Mono<StoredObject> findBy(ObjectId objectId, VersionId versionId) {
        return Mono.fromCallable(() -> {
            Path objectDir = metadataRoot.resolve("objects")
                    .resolve(objectId.value().replace("/", "_"));
            Path versionFile = objectDir.resolve(versionId.value() + ".json");
            if (!Files.exists(versionFile)) {
                throw new java.util.NoSuchElementException(
                        "StoredObject not found: " + objectId.value() + ":" + versionId.value());
            }
            String json = Files.readString(versionFile);
            return deserializeFromJson(json);
        });
    }

    /**
     * Simple JSON serialization using StringBuilder.
     */
    private String serializeToJson(StoredObject obj) {
        StringBuilder sb = new StringBuilder();
        sb.append("{\n");
        sb.append("  \"objectId\": \"").append(escapeJson(obj.objectId().value())).append("\",\n");
        sb.append("  \"versionId\": \"").append(escapeJson(obj.versionId().value())).append("\",\n");
        sb.append("  \"bucketId\": \"").append(escapeJson(obj.bucketRef().bucketId().value())).append("\",\n");
        sb.append("  \"bucketName\": \"").append(escapeJson(obj.bucketRef().bucketName())).append("\",\n");
        sb.append("  \"storageClassId\": \"").append(escapeJson(obj.storageClassId().value())).append("\",\n");
        sb.append("  \"state\": \"").append(obj.state().name()).append("\",\n");
        sb.append("  \"createdAt\": \"").append(obj.createdAt().toString()).append("\",\n");
        sb.append("  \"lastModified\": \"").append(obj.lastModified().toString()).append("\"\n");
        sb.append("}\n");
        return sb.toString();
    }

    private StoredObject deserializeFromJson(String json) {
        // Simplified deserialization — in production, use Jackson
        throw new UnsupportedOperationException(
                "JSON deserialization requires a proper JSON library.");
    }

    private String escapeJson(String value) {
        return value.replace("\\", "\\\\")
                .replace("\"", "\\\"")
                .replace("\n", "\\n")
                .replace("\r", "\\r")
                .replace("\t", "\\t");
    }
}
