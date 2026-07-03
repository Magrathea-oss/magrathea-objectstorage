package com.example.magrathea.objectstorage.repository.storageengine.adapter;

import com.example.magrathea.objectstore.domain.aggregate.Bucket;
import com.example.magrathea.objectstore.domain.valueobject.BucketConfig;
import com.example.magrathea.objectstore.domain.valueobject.Region;
import com.example.magrathea.objectstore.domain.valueobject.StorageClass;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.locks.ReentrantLock;

/**
 * Durable bucket registry for the storage-engine backend.
 *
 * <p>Every bucket aggregate (including its full {@link BucketConfig}) is
 * persisted as a JSON document under
 * {@code {storageRoot}/metadata/buckets/{encoded-bucket-name}.json} using a
 * crash-safe temp-file + atomic-rename commit. All previously persisted
 * buckets are reloaded at construction time, so the bucket namespace and its
 * configuration families survive a process restart.</p>
 */
final class BucketStore {

    private static final int LOCK_STRIPES = 64;

    private final Path bucketsRoot;
    private final ReentrantLock[] locks;
    private final ConcurrentHashMap<String, Bucket> byName = new ConcurrentHashMap<>();

    BucketStore(Path bucketsRoot) {
        this.bucketsRoot = java.util.Objects.requireNonNull(bucketsRoot);
        this.locks = new ReentrantLock[LOCK_STRIPES];
        for (int i = 0; i < LOCK_STRIPES; i++) {
            locks[i] = new ReentrantLock();
        }
        try {
            Files.createDirectories(bucketsRoot);
        } catch (IOException e) {
            throw new UncheckedIOException("Failed to create bucket registry directory", e);
        }
        loadAll();
    }

    private void loadAll() {
        if (!Files.isDirectory(bucketsRoot)) {
            // Fresh storage root (or re-pointed symlink): nothing persisted yet.
            return;
        }
        try (var paths = Files.list(bucketsRoot)) {
            paths.filter(Files::isRegularFile)
                .filter(path -> path.getFileName().toString().endsWith(".json"))
                .forEach(path -> DurableJson.read(path, StoredBucket.class)
                    .map(StoredBucket::toBucket)
                    .ifPresent(bucket -> byName.put(bucket.name(), bucket)));
        } catch (IOException e) {
            throw new UncheckedIOException(
                "Failed to reload bucket registry from " + bucketsRoot, e);
        }
    }

    Optional<Bucket> findByName(String bucketName) {
        return Optional.ofNullable(byName.get(bucketName));
    }

    Optional<Bucket> findById(Bucket.Id id) {
        return byName.values().stream()
            .filter(bucket -> bucket.id().equals(id))
            .findFirst();
    }

    List<Bucket> findAll() {
        return List.copyOf(byName.values());
    }

    boolean exists(Bucket.Id id) {
        return findById(id).isPresent();
    }

    /** Durably commits the bucket (clean of events) and updates the in-memory view. */
    Bucket save(Bucket bucket) {
        ReentrantLock lock = lockFor(bucket.name());
        lock.lock();
        try {
            Bucket clean = bucket.clearEvents();
            DurableJson.writeAtomic(bucketPath(bucket.name()), StoredBucket.from(clean));
            byName.put(clean.name(), clean);
            return clean;
        } finally {
            lock.unlock();
        }
    }

    /** Removes the bucket durably; returns the removed aggregate if it existed. */
    Optional<Bucket> delete(String bucketName) {
        ReentrantLock lock = lockFor(bucketName);
        lock.lock();
        try {
            Bucket removed = byName.remove(bucketName);
            DurableJson.delete(bucketPath(bucketName));
            return Optional.ofNullable(removed);
        } finally {
            lock.unlock();
        }
    }

    /** Clears the in-memory view AND the persisted registry (test reset). */
    void wipe() {
        byName.clear();
        DurableJson.wipeDirectory(bucketsRoot);
    }

    /**
     * Discards the in-memory view and reloads it from the durable registry
     * (restart simulation / storage-root re-pointing). Persisted files are kept.
     */
    void reload() {
        byName.clear();
        loadAll();
    }

    private Path bucketPath(String bucketName) {
        return bucketsRoot.resolve(DurableJson.encode(bucketName) + ".json");
    }

    private ReentrantLock lockFor(String bucketName) {
        return locks[Math.floorMod(bucketName.hashCode(), LOCK_STRIPES)];
    }

    /**
     * Persistence envelope: the durable form of a bucket aggregate without
     * transient domain events. {@link BucketConfig} is stored verbatim so every
     * bucket configuration family survives restarts.
     */
    record StoredBucket(
        String id,
        String name,
        Region region,
        StorageClass storageClass,
        boolean versioningEnabled,
        boolean encryptionEnabled,
        boolean directoryBucket,
        BucketConfig config) {

        static StoredBucket from(Bucket bucket) {
            return new StoredBucket(
                bucket.id().value(),
                bucket.name(),
                bucket.region(),
                bucket.storageClass(),
                bucket.versioningEnabled(),
                bucket.encryptionEnabled(),
                bucket.directoryBucket(),
                bucket.bucketConfig());
        }

        Bucket toBucket() {
            return Bucket.restore(
                Bucket.Id.of(id),
                name,
                region,
                storageClass,
                versioningEnabled,
                encryptionEnabled,
                directoryBucket,
                config != null ? config : BucketConfig.EMPTY);
        }
    }
}
