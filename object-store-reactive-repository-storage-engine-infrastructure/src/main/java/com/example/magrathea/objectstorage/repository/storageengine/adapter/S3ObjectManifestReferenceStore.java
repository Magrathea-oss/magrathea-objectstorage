package com.example.magrathea.objectstorage.repository.storageengine.adapter;

import com.example.magrathea.objectstore.domain.aggregate.ActiveS3Object;
import com.example.magrathea.objectstore.domain.aggregate.S3Object;
import com.example.magrathea.objectstore.domain.valueobject.ObjectChecksum;
import com.example.magrathea.objectstore.domain.valueobject.ObjectKey;
import com.example.magrathea.storageengine.domain.valueobject.ManifestId;
import com.example.magrathea.storageengine.domain.valueobject.VersionId;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import reactor.core.scheduler.Schedulers;

import java.io.IOException;
import java.io.StringReader;
import java.io.StringWriter;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.time.ZonedDateTime;
import java.util.Base64;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Properties;
import java.util.Set;
import java.util.UUID;

/**
 * Internal filesystem index from S3 bucket/key to the latest storage-engine manifest.
 * This is repository-local durable state, not an external storage-engine API.
 */
final class S3ObjectManifestReferenceStore {

    private static final String LATEST_MARKER = "true";

    private final Path referencesRoot;

    S3ObjectManifestReferenceStore(Path referencesRoot) {
        this.referencesRoot = java.util.Objects.requireNonNull(
            referencesRoot, "referencesRoot must not be null");
        try {
            Files.createDirectories(referencesRoot);
        } catch (IOException e) {
            throw new UncheckedIOException("Failed to create S3 object reference directory", e);
        }
    }

    Mono<Void> save(S3Object object, ManifestId manifestId, VersionId versionId) {
        return Mono.fromRunnable(() -> writeReference(Reference.from(object, manifestId, versionId)))
            .subscribeOn(Schedulers.boundedElastic())
            .then();
    }

    Mono<Optional<Reference>> find(String bucket, String key) {
        return Mono.fromCallable(() -> readReference(bucket, key))
            .subscribeOn(Schedulers.boundedElastic());
    }

    Flux<Reference> findByBucket(String bucket) {
        return Mono.fromCallable(() -> readBucketReferences(bucket))
            .subscribeOn(Schedulers.boundedElastic())
            .flatMapMany(Flux::fromIterable);
    }

    Mono<Void> delete(String bucket, String key) {
        return Mono.fromRunnable(() -> {
                try {
                    Files.deleteIfExists(referencePath(bucket, key));
                } catch (IOException e) {
                    throw new UncheckedIOException("Failed to delete S3 object reference", e);
                }
            })
            .subscribeOn(Schedulers.boundedElastic())
            .then();
    }

    private void writeReference(Reference reference) {
        try {
            Path path = referencePath(reference.bucket(), reference.key());
            Files.createDirectories(path.getParent());
            Files.writeString(path, serialize(reference),
                StandardOpenOption.CREATE, StandardOpenOption.TRUNCATE_EXISTING);
        } catch (IOException e) {
            throw new UncheckedIOException("Failed to save S3 object reference", e);
        }
    }

    private Optional<Reference> readReference(String bucket, String key) {
        Path path = referencePath(bucket, key);
        if (!Files.exists(path)) {
            return Optional.empty();
        }
        try {
            return Optional.of(deserialize(Files.readString(path)));
        } catch (IOException e) {
            throw new UncheckedIOException("Failed to read S3 object reference", e);
        }
    }

    private List<Reference> readBucketReferences(String bucket) {
        Path bucketDir = referencesRoot.resolve(encode(bucket));
        if (!Files.isDirectory(bucketDir)) {
            return List.of();
        }
        try (var paths = Files.list(bucketDir)) {
            return paths
                .filter(Files::isRegularFile)
                .filter(path -> path.getFileName().toString().endsWith(".properties"))
                .map(path -> {
                    try {
                        return deserialize(Files.readString(path));
                    } catch (IOException e) {
                        throw new UncheckedIOException("Failed to read S3 object reference", e);
                    }
                })
                .filter(reference -> reference.bucket().equals(bucket))
                .toList();
        } catch (IOException e) {
            throw new UncheckedIOException("Failed to list S3 object references", e);
        }
    }

    private String serialize(Reference reference) {
        Properties properties = new Properties();
        properties.setProperty("bucket", reference.bucket());
        properties.setProperty("key", reference.key());
        if (reference.storageClass() != null) {
            properties.setProperty("storageClass", reference.storageClass());
        }
        properties.setProperty("size", Long.toString(reference.size()));
        properties.setProperty("manifestId", reference.manifestId().value().toString());
        properties.setProperty("versionId", reference.versionId().value());
        properties.setProperty("latest", LATEST_MARKER);
        properties.setProperty("createdAt", reference.createdAt().toString());
        properties.setProperty("userMetadata.count", Integer.toString(reference.userMetadata().size()));
        int index = 0;
        for (Map.Entry<String, String> entry : reference.userMetadata().entrySet()) {
            String prefix = "userMetadata." + index + ".";
            properties.setProperty(prefix + "key", entry.getKey());
            properties.setProperty(prefix + "value", entry.getValue());
            index++;
        }

        try (StringWriter writer = new StringWriter()) {
            properties.store(writer, "Magrathea storage-engine S3 object manifest reference");
            return writer.toString();
        } catch (IOException e) {
            throw new UncheckedIOException("Failed to serialize S3 object reference", e);
        }
    }

    private Reference deserialize(String data) {
        Properties properties = new Properties();
        try {
            properties.load(new StringReader(data));
        } catch (IOException e) {
            throw new UncheckedIOException("Failed to parse S3 object reference", e);
        }

        int metadataCount = Integer.parseInt(properties.getProperty("userMetadata.count", "0"));
        java.util.LinkedHashMap<String, String> metadata = new java.util.LinkedHashMap<>();
        for (int index = 0; index < metadataCount; index++) {
            String prefix = "userMetadata." + index + ".";
            String metadataKey = properties.getProperty(prefix + "key");
            String metadataValue = properties.getProperty(prefix + "value");
            if (metadataKey != null && metadataValue != null) {
                metadata.put(metadataKey, metadataValue);
            }
        }

        return new Reference(
            required(properties, "bucket"),
            required(properties, "key"),
            properties.getProperty("storageClass"),
            Map.copyOf(metadata),
            Long.parseLong(required(properties, "size")),
            ManifestId.of(UUID.fromString(required(properties, "manifestId"))),
            VersionId.of(required(properties, "versionId")),
            ZonedDateTime.parse(required(properties, "createdAt")));
    }

    private Path referencePath(String bucket, String key) {
        return referencesRoot
            .resolve(encode(bucket))
            .resolve(encode(key) + ".properties");
    }

    private static String required(Properties properties, String key) {
        String value = properties.getProperty(key);
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException("S3 object reference is missing required property: " + key);
        }
        return value;
    }

    private static String encode(String value) {
        return Base64.getUrlEncoder().withoutPadding()
            .encodeToString(value.getBytes(java.nio.charset.StandardCharsets.UTF_8));
    }

    record Reference(
        String bucket,
        String key,
        String storageClass,
        Map<String, String> userMetadata,
        long size,
        ManifestId manifestId,
        VersionId versionId,
        ZonedDateTime createdAt) {

        static Reference from(S3Object object, ManifestId manifestId, VersionId versionId) {
            return new Reference(
                object.key().bucket(),
                object.key().key(),
                object.storageClass(),
                object.userMetadata(),
                object.size(),
                manifestId,
                versionId,
                object.createdAt());
        }

        ActiveS3Object toS3Object() {
            return ActiveS3Object.restoreActive(
                ObjectKey.of(bucket, key),
                storageClass,
                userMetadata,
                null,
                ObjectChecksum.of(Set.of()),
                size,
                createdAt,
                List.of());
        }
    }
}
