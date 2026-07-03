package com.example.magrathea.objectstorage.repository.storageengine.adapter;

import com.example.magrathea.objectstore.domain.valueobject.EncryptionConfiguration;
import com.example.magrathea.objectstore.domain.valueobject.LegalHold;
import com.example.magrathea.objectstore.domain.valueobject.ObjectLockConfiguration;
import com.example.magrathea.objectstore.domain.valueobject.RestoreConfiguration;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Optional;
import java.util.concurrent.locks.ReentrantLock;
import java.util.function.UnaryOperator;

/**
 * Durable per-object configuration metadata for the storage-engine backend.
 *
 * <p>Legal hold, object-level encryption, object lock configuration, retention
 * period and restore status for one bucket/key are stored together as one JSON
 * document under
 * {@code {storageRoot}/metadata/object-config/{encoded-bucket}/{encoded-key}.json}.
 * Every mutation is a read-modify-write cycle serialized through a striped
 * per-key lock and committed with a crash-safe temp-file + atomic-rename, so a
 * document is always a complete, self-consistent snapshot and survives a
 * process restart.</p>
 */
final class ObjectConfigMetadataStore {

    private static final int LOCK_STRIPES = 64;

    private final Path configRoot;
    private final ReentrantLock[] locks;

    ObjectConfigMetadataStore(Path configRoot) {
        this.configRoot = java.util.Objects.requireNonNull(configRoot);
        this.locks = new ReentrantLock[LOCK_STRIPES];
        for (int i = 0; i < LOCK_STRIPES; i++) {
            locks[i] = new ReentrantLock();
        }
        try {
            Files.createDirectories(configRoot);
        } catch (IOException e) {
            throw new UncheckedIOException("Failed to create object config metadata directory", e);
        }
    }

    Optional<StoredObjectConfig> find(String bucket, String key) {
        return DurableJson.read(configPath(bucket, key), StoredObjectConfig.class);
    }

    /**
     * Atomically reads, mutates and durably commits the config document for one
     * bucket/key. The mutation receives the current document (or the empty
     * document) and returns the document to commit.
     */
    void update(String bucket, String key, UnaryOperator<StoredObjectConfig> mutation) {
        ReentrantLock lock = lockFor(bucket, key);
        lock.lock();
        try {
            StoredObjectConfig current = find(bucket, key).orElse(StoredObjectConfig.EMPTY);
            StoredObjectConfig next = mutation.apply(current);
            if (!next.equals(current)) {
                DurableJson.writeAtomic(configPath(bucket, key), next);
            }
        } finally {
            lock.unlock();
        }
    }

    void delete(String bucket, String key) {
        ReentrantLock lock = lockFor(bucket, key);
        lock.lock();
        try {
            DurableJson.delete(configPath(bucket, key));
        } finally {
            lock.unlock();
        }
    }

    /** Clears all persisted per-object config documents (test reset). */
    void wipe() {
        DurableJson.wipeDirectory(configRoot);
    }

    private Path configPath(String bucket, String key) {
        return configRoot
            .resolve(DurableJson.encode(bucket))
            .resolve(DurableJson.encode(key) + ".json");
    }

    private ReentrantLock lockFor(String bucket, String key) {
        int hash = (bucket + "\u0000" + key).hashCode();
        return locks[Math.floorMod(hash, LOCK_STRIPES)];
    }

    /**
     * Persistence document holding all per-object configuration families.
     * Absent families are {@code null}; the document is always committed as one
     * self-consistent snapshot.
     */
    record StoredObjectConfig(
        LegalHold legalHold,
        EncryptionConfiguration encryption,
        ObjectLockConfiguration lockConfiguration,
        ObjectLockConfiguration.RetentionPeriod retention,
        RestoreConfiguration restore) {

        static final StoredObjectConfig EMPTY =
            new StoredObjectConfig(null, null, null, null, null);

        StoredObjectConfig withLegalHold(LegalHold value) {
            return new StoredObjectConfig(value, encryption, lockConfiguration, retention, restore);
        }

        StoredObjectConfig withEncryption(EncryptionConfiguration value) {
            return new StoredObjectConfig(legalHold, value, lockConfiguration, retention, restore);
        }

        StoredObjectConfig withLockConfiguration(ObjectLockConfiguration value) {
            return new StoredObjectConfig(legalHold, encryption, value, retention, restore);
        }

        StoredObjectConfig withRetention(ObjectLockConfiguration.RetentionPeriod value) {
            return new StoredObjectConfig(legalHold, encryption, lockConfiguration, value, restore);
        }

        StoredObjectConfig withRestore(RestoreConfiguration value) {
            return new StoredObjectConfig(legalHold, encryption, lockConfiguration, retention, value);
        }
    }
}
