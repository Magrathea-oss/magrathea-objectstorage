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
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.time.ZonedDateTime;
import java.util.Base64;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Properties;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.locks.ReentrantLock;
import java.util.function.UnaryOperator;

/**
 * Internal filesystem index from S3 bucket/key to the latest storage-engine manifest.
 * This is repository-local durable state, not an external storage-engine API.
 *
 * <p>Concurrency contract: every mutation of the latest-reference record for a given
 * bucket/key is serialized through a striped per-key lock (see {@link #commitLatest}).
 * A committed reference is always written as one complete, self-consistent record
 * (manifestId + versionId + metadata from a single upload) via a temp-file write
 * followed by an atomic rename, so readers and crash recovery never observe a torn
 * or partially written reference.</p>
 */
final class S3ObjectManifestReferenceStore {

    private static final String SCHEMA_VERSION_PROPERTY = "reference.schemaVersion";
    private static final int CURRENT_SCHEMA_VERSION = 1;
    private static final int LEGACY_SCHEMA_VERSION = 0;
    private static final String LATEST_MARKER = "true";
    private static final int LOCK_STRIPES = 64;

    private final Path referencesRoot;
    private final ReentrantLock[] keyLocks;

    S3ObjectManifestReferenceStore(Path referencesRoot) {
        this.referencesRoot = java.util.Objects.requireNonNull(
            referencesRoot, "referencesRoot must not be null");
        this.keyLocks = new ReentrantLock[LOCK_STRIPES];
        for (int i = 0; i < LOCK_STRIPES; i++) {
            keyLocks[i] = new ReentrantLock();
        }
        try {
            Files.createDirectories(referencesRoot);
        } catch (IOException e) {
            throw new UncheckedIOException("Failed to create S3 object reference directory", e);
        }
    }

    Mono<Void> save(S3Object object, ManifestId manifestId, VersionId versionId) {
        return commitLatest(object.key().bucket(), object.key().key(),
            current -> Optional.of(Reference.from(object, manifestId, versionId)));
    }

    /**
     * Atomically reads, composes, and commits the latest reference for one bucket/key.
     *
     * <p>The whole read-compose-write cycle runs under the per-key lock, so concurrent
     * same-key commits are serialized and can never interleave between the read and the
     * write. Unrelated keys use independent lock stripes and are not serialized against
     * each other. The mutation receives the currently committed reference (if any) and
     * returns the reference to commit: returning a value equal to the current one skips
     * the write, returning {@link Optional#empty()} removes the reference.</p>
     */
    Mono<Void> commitLatest(String bucket, String key,
                            UnaryOperator<Optional<Reference>> mutation) {
        return Mono.fromRunnable(() -> {
                ReentrantLock lock = lockFor(bucket, key);
                lock.lock();
                try {
                    Optional<Reference> current = readReference(bucket, key);
                    Optional<Reference> next = mutation.apply(current);
                    if (next.equals(current)) {
                        return;
                    }
                    if (next.isEmpty()) {
                        deleteReferenceFile(bucket, key);
                    } else {
                        writeReference(next.get());
                    }
                } finally {
                    lock.unlock();
                }
            })
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

    Mono<Set<UUID>> liveManifestIds() {
        return Mono.fromCallable(this::liveManifestIdsBlocking)
            .subscribeOn(Schedulers.boundedElastic());
    }

    Set<UUID> liveManifestIdsBlocking() {
        if (!Files.isDirectory(referencesRoot)) {
            return Set.of();
        }
        try (var paths = Files.walk(referencesRoot)) {
            return paths.filter(Files::isRegularFile)
                .filter(path -> path.getFileName().toString().endsWith(".properties"))
                .map(path -> {
                    try {
                        Properties properties = new Properties();
                        properties.load(new StringReader(Files.readString(path)));
                        String manifestId = properties.getProperty("manifestId");
                        if (manifestId == null || manifestId.isBlank()) {
                            throw new IllegalArgumentException(
                                "Object reference is missing manifestId: " + path);
                        }
                        return UUID.fromString(manifestId);
                    } catch (IOException error) {
                        throw new UncheckedIOException("Failed to read S3 object reference", error);
                    }
                })
                .collect(java.util.stream.Collectors.toUnmodifiableSet());
        } catch (IOException error) {
            throw new UncheckedIOException("Failed to list S3 object references", error);
        }
    }

    Mono<Void> delete(String bucket, String key) {
        return commitLatest(bucket, key, current -> Optional.empty());
    }

    private void deleteReferenceFile(String bucket, String key) {
        try {
            Files.deleteIfExists(referencePath(bucket, key));
        } catch (IOException e) {
            throw new UncheckedIOException("Failed to delete S3 object reference", e);
        }
    }

    /**
     * Durable, crash-safe reference write: the record is written to a temp file in the
     * same directory (same filesystem) and then atomically moved over the target, so a
     * reader or a crash never observes a half-written reference.
     */
    private void writeReference(Reference reference) {
        try {
            Path path = referencePath(reference.bucket(), reference.key());
            Files.createDirectories(path.getParent());
            Path temp = Files.createTempFile(
                path.getParent(), path.getFileName().toString() + ".", ".tmp");
            try {
                Files.writeString(temp, serialize(reference));
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
            throw new UncheckedIOException("Failed to save S3 object reference", e);
        }
    }

    private ReentrantLock lockFor(String bucket, String key) {
        int hash = (bucket + "\u0000" + key).hashCode();
        return keyLocks[Math.floorMod(hash, LOCK_STRIPES)];
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
        properties.setProperty(SCHEMA_VERSION_PROPERTY, Integer.toString(CURRENT_SCHEMA_VERSION));
        properties.setProperty("bucket", reference.bucket());
        properties.setProperty("key", reference.key());
        if (reference.storageClass() != null) {
            properties.setProperty("storageClass", reference.storageClass());
        }
        if (reference.etag() != null) {
            properties.setProperty("etag", reference.etag());
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
        properties.setProperty("objectTags.count", Integer.toString(reference.objectTags().size()));
        int tagIndex = 0;
        for (Map.Entry<String, String> entry : reference.objectTags().entrySet()) {
            String prefix = "objectTags." + tagIndex + ".";
            properties.setProperty(prefix + "key", entry.getKey());
            properties.setProperty(prefix + "value", entry.getValue());
            tagIndex++;
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

        int schemaVersion = Integer.parseInt(
            properties.getProperty(SCHEMA_VERSION_PROPERTY, Integer.toString(LEGACY_SCHEMA_VERSION)));
        if (schemaVersion != LEGACY_SCHEMA_VERSION && schemaVersion != CURRENT_SCHEMA_VERSION) {
            throw new IllegalArgumentException(
                "Unsupported object manifest reference schema version: " + schemaVersion);
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

        int tagCount = Integer.parseInt(properties.getProperty("objectTags.count", "0"));
        java.util.LinkedHashMap<String, String> tags = new java.util.LinkedHashMap<>();
        for (int index = 0; index < tagCount; index++) {
            String prefix = "objectTags." + index + ".";
            String tagKey = properties.getProperty(prefix + "key");
            String tagValue = properties.getProperty(prefix + "value");
            if (tagKey != null && tagValue != null) {
                tags.put(tagKey, tagValue);
            }
        }

        return new Reference(
            required(properties, "bucket"),
            required(properties, "key"),
            properties.getProperty("storageClass"),
            properties.getProperty("etag"),
            Map.copyOf(metadata),
            Map.copyOf(tags),
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
        String etag,
        Map<String, String> userMetadata,
        Map<String, String> objectTags,
        long size,
        ManifestId manifestId,
        VersionId versionId,
        ZonedDateTime createdAt) {

        Reference {
            objectTags = objectTags == null ? Map.of() : Map.copyOf(objectTags);
        }

        static Reference from(S3Object object, ManifestId manifestId, VersionId versionId) {
            return new Reference(
                object.key().bucket(),
                object.key().key(),
                object.storageClass(),
                object.etag(),
                object.userMetadata(),
                object.objectTags(),
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
                    List.of())
                .withEtag(etag)
                .withObjectTags(objectTags);
        }
    }
}
