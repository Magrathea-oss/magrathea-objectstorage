package com.example.magrathea.objectstorage.repository.storageengine.adapter;

import com.example.magrathea.objectstore.domain.aggregate.Bucket;
import com.example.magrathea.objectstore.domain.aggregate.MultipartUpload;
import com.example.magrathea.objectstore.domain.valueobject.ObjectKey;
import com.example.magrathea.objectstore.domain.valueobject.PartNumber;
import com.example.magrathea.objectstore.domain.valueobject.UploadId;
import com.example.magrathea.objectstore.domain.valueobject.UploadPart;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.locks.ReentrantLock;

/**
 * Durable multipart upload session state for the storage-engine backend.
 *
 * <p>Each in-progress multipart upload (upload id, bucket, key, initiated
 * timestamp and the full recorded part list with part numbers, ETags and
 * sizes) is persisted as a JSON document under
 * {@code {storageRoot}/metadata/multipart-uploads/{encoded-upload-id}.json}
 * using a crash-safe temp-file + atomic-rename commit. Previously persisted
 * sessions are reloaded at construction time, so multipart state survives a
 * process restart and uploads can be listed, continued, completed or aborted
 * after recovery.</p>
 */
final class MultipartUploadStateStore {

    private static final int LOCK_STRIPES = 64;
    private static final int CURRENT_SCHEMA_VERSION = 1;
    private static final int LEGACY_SCHEMA_VERSION = 0;

    private final Path uploadsRoot;
    private final ReentrantLock[] locks;
    private final ConcurrentHashMap<String, MultipartUpload> byUploadId = new ConcurrentHashMap<>();

    MultipartUploadStateStore(Path uploadsRoot) {
        this.uploadsRoot = java.util.Objects.requireNonNull(uploadsRoot);
        this.locks = new ReentrantLock[LOCK_STRIPES];
        for (int i = 0; i < LOCK_STRIPES; i++) {
            locks[i] = new ReentrantLock();
        }
        try {
            Files.createDirectories(uploadsRoot);
        } catch (IOException e) {
            throw new UncheckedIOException("Failed to create multipart upload state directory", e);
        }
        loadAll();
    }

    private void loadAll() {
        if (!Files.isDirectory(uploadsRoot)) {
            // Fresh storage root (or re-pointed symlink): nothing persisted yet.
            return;
        }
        try (var paths = Files.list(uploadsRoot)) {
            paths.filter(Files::isRegularFile)
                .filter(path -> path.getFileName().toString().endsWith(".json"))
                .forEach(path -> DurableJson.read(path, StoredUpload.class)
                    .map(StoredUpload::toUpload)
                    .ifPresent(upload -> byUploadId.put(upload.uploadId().value(), upload)));
        } catch (IOException e) {
            throw new UncheckedIOException(
                "Failed to reload multipart upload state from " + uploadsRoot, e);
        }
    }

    Optional<MultipartUpload> findById(UploadId uploadId) {
        return Optional.ofNullable(byUploadId.get(uploadId.value()));
    }

    List<MultipartUpload> findAll() {
        return List.copyOf(byUploadId.values());
    }

    boolean exists(UploadId uploadId) {
        return byUploadId.containsKey(uploadId.value());
    }

    /** Durably commits the upload session (clean of events) and updates the in-memory view. */
    MultipartUpload save(MultipartUpload upload) {
        ReentrantLock lock = lockFor(upload.uploadId().value());
        lock.lock();
        try {
            MultipartUpload clean = upload.clearEvents();
            DurableJson.writeAtomic(uploadPath(clean.uploadId().value()), StoredUpload.from(clean));
            byUploadId.put(clean.uploadId().value(), clean);
            return clean;
        } finally {
            lock.unlock();
        }
    }

    /** Removes the upload session durably; returns the removed aggregate if it existed. */
    Optional<MultipartUpload> delete(UploadId uploadId) {
        ReentrantLock lock = lockFor(uploadId.value());
        lock.lock();
        try {
            MultipartUpload removed = byUploadId.remove(uploadId.value());
            DurableJson.delete(uploadPath(uploadId.value()));
            return Optional.ofNullable(removed);
        } finally {
            lock.unlock();
        }
    }

    /** Clears the in-memory view AND the persisted state (test reset). */
    void wipe() {
        byUploadId.clear();
        DurableJson.wipeDirectory(uploadsRoot);
    }

    /**
     * Discards the in-memory view and reloads it from the durable state
     * (restart simulation / storage-root re-pointing). Persisted files are kept.
     */
    void reload() {
        byUploadId.clear();
        loadAll();
    }

    private Path uploadPath(String uploadId) {
        return uploadsRoot.resolve(DurableJson.encode(uploadId) + ".json");
    }

    private ReentrantLock lockFor(String uploadId) {
        return locks[Math.floorMod(uploadId.hashCode(), LOCK_STRIPES)];
    }

    /** Persistence envelope: the durable form of a multipart upload session. */
    record StoredUpload(
        Integer schemaVersion,
        String id,
        String bucketId,
        String bucket,
        String key,
        String uploadId,
        Instant initiated,
        List<StoredPart> parts,
        boolean completed,
        boolean aborted) {

        static StoredUpload from(MultipartUpload upload) {
            return new StoredUpload(
                CURRENT_SCHEMA_VERSION,
                upload.id().value(),
                upload.bucketId().value(),
                upload.key().bucket(),
                upload.key().key(),
                upload.uploadId().value(),
                upload.initiated(),
                upload.parts().stream().map(StoredPart::from).toList(),
                upload.completed(),
                upload.aborted());
        }

        MultipartUpload toUpload() {
            int effectiveSchemaVersion = schemaVersion == null ? LEGACY_SCHEMA_VERSION : schemaVersion;
            if (effectiveSchemaVersion != LEGACY_SCHEMA_VERSION
                    && effectiveSchemaVersion != CURRENT_SCHEMA_VERSION) {
                throw new IllegalArgumentException(
                    "Unsupported multipart upload schema version: " + effectiveSchemaVersion);
            }
            return MultipartUpload.restore(
                MultipartUpload.Id.of(id),
                Bucket.Id.of(bucketId),
                ObjectKey.of(bucket, key),
                UploadId.of(uploadId),
                initiated,
                parts == null ? List.of() : parts.stream().map(StoredPart::toPart).toList(),
                completed,
                aborted);
        }
    }

    /** Persistence envelope for a single recorded upload part. */
    record StoredPart(int partNumber, String etag, long size, Instant lastModified) {

        static StoredPart from(UploadPart part) {
            return new StoredPart(
                part.partNumber().value(), part.etag(), part.size(), part.lastModified());
        }

        UploadPart toPart() {
            return UploadPart.of(PartNumber.of(partNumber), etag, size, lastModified);
        }
    }
}
