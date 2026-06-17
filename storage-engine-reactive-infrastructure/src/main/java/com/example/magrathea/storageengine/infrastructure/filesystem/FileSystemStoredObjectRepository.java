package com.example.magrathea.storageengine.infrastructure.filesystem;

import com.example.magrathea.storageengine.application.port.StoredObjectRepository;
import com.example.magrathea.storageengine.domain.aggregate.ObjectState;
import com.example.magrathea.storageengine.domain.aggregate.StoredObject;
import com.example.magrathea.storageengine.domain.valueobject.BucketId;
import com.example.magrathea.storageengine.domain.valueobject.BucketRef;
import com.example.magrathea.storageengine.domain.valueobject.EffectiveStoragePolicy;
import com.example.magrathea.storageengine.domain.valueobject.ManifestId;
import com.example.magrathea.storageengine.domain.valueobject.ObjectId;
import com.example.magrathea.storageengine.domain.valueobject.ReplicationConfig;
import com.example.magrathea.storageengine.domain.valueobject.StorageClassId;
import com.example.magrathea.storageengine.domain.valueobject.VersionId;
import com.example.magrathea.storageengine.domain.valueobject.VirtualDevice;
import reactor.core.publisher.Mono;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.time.ZonedDateTime;
import java.util.Optional;

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
        return BlockingFileSystemOperation.fromRunnable(() -> {
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
        return BlockingFileSystemOperation.fromCallable(() -> {
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

    private String serializeToJson(StoredObject obj) {
        StringBuilder sb = new StringBuilder();
        sb.append("{\n");
        appendJsonField(sb, "objectId", obj.objectId().value(), true);
        appendJsonField(sb, "versionId", obj.versionId().value(), true);
        appendJsonField(sb, "bucketId", obj.bucketRef().bucketId().value(), true);
        appendJsonField(sb, "bucketName", obj.bucketRef().bucketName(), true);
        appendJsonField(sb, "storageClassId", obj.storageClassId().value(), true);
        if (obj.manifestId() != null) {
            appendJsonField(sb, "manifestId", obj.manifestId().value().toString(), true);
        }
        appendJsonField(sb, "state", obj.state().name(), true);
        appendJsonField(sb, "createdAt", obj.createdAt().toString(), true);
        appendJsonField(sb, "lastModified", obj.lastModified().toString(), false);
        sb.append("}\n");
        return sb.toString();
    }

    private StoredObject deserializeFromJson(String json) {
        ObjectId objectId = ObjectId.of(requiredJsonString(json, "objectId"));
        VersionId versionId = VersionId.of(requiredJsonString(json, "versionId"));
        BucketRef bucketRef = BucketRef.of(
                BucketId.of(requiredJsonString(json, "bucketId")),
                requiredJsonString(json, "bucketName"));
        StorageClassId storageClassId = StorageClassId.of(requiredJsonString(json, "storageClassId"));
        ManifestId manifestId = optionalJsonString(json, "manifestId")
                .map(value -> ManifestId.of(java.util.UUID.fromString(value)))
                .orElse(null);
        ObjectState state = ObjectState.valueOf(requiredJsonString(json, "state"));
        ZonedDateTime createdAt = ZonedDateTime.parse(requiredJsonString(json, "createdAt"));
        ZonedDateTime lastModified = ZonedDateTime.parse(requiredJsonString(json, "lastModified"));
        return StoredObject.restore(
                objectId,
                versionId,
                bucketRef,
                storageClassId,
                manifestId,
                reconstructTargetDevice(bucketRef, storageClassId),
                state,
                createdAt,
                lastModified);
    }

    private VirtualDevice reconstructTargetDevice(BucketRef bucketRef, StorageClassId storageClassId) {
        EffectiveStoragePolicy policy = EffectiveStoragePolicy.of(
                storageClassId,
                bucketRef,
                Optional.empty(),
                Optional.empty(),
                Optional.empty(),
                Optional.empty(),
                ReplicationConfig.of(1));
        return new VirtualDevice.BucketDevice(bucketRef, policy);
    }

    private void appendJsonField(StringBuilder sb, String name, String value, boolean comma) {
        sb.append("  \"").append(name).append("\": \"")
                .append(escapeJson(value)).append("\"");
        if (comma) {
            sb.append(',');
        }
        sb.append('\n');
    }

    private String requiredJsonString(String json, String field) {
        return optionalJsonString(json, field)
                .filter(value -> !value.isBlank())
                .orElseThrow(() -> new IllegalArgumentException(
                        "StoredObject JSON is missing required field: " + field));
    }

    private Optional<String> optionalJsonString(String json, String field) {
        String needle = "\"" + field + "\"";
        int fieldStart = json.indexOf(needle);
        if (fieldStart < 0) {
            return Optional.empty();
        }
        int colon = json.indexOf(':', fieldStart + needle.length());
        if (colon < 0) {
            return Optional.empty();
        }
        int quoteStart = json.indexOf('"', colon + 1);
        if (quoteStart < 0) {
            return Optional.empty();
        }
        StringBuilder value = new StringBuilder();
        boolean escaping = false;
        for (int index = quoteStart + 1; index < json.length(); index++) {
            char current = json.charAt(index);
            if (escaping) {
                value.append(unescapeJsonChar(current));
                escaping = false;
            } else if (current == '\\') {
                escaping = true;
            } else if (current == '"') {
                return Optional.of(value.toString());
            } else {
                value.append(current);
            }
        }
        return Optional.empty();
    }

    private char unescapeJsonChar(char escaped) {
        return switch (escaped) {
            case 'n' -> '\n';
            case 'r' -> '\r';
            case 't' -> '\t';
            case '"' -> '"';
            case '\\' -> '\\';
            default -> escaped;
        };
    }

    private String escapeJson(String value) {
        return value.replace("\\", "\\\\")
                .replace("\"", "\\\"")
                .replace("\n", "\\n")
                .replace("\r", "\\r")
                .replace("\t", "\\t");
    }
}
