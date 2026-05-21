package com.example.magrathea.objectstorage.domain.aggregate;

import com.example.magrathea.objectstorage.domain.valueobject.Region;
import com.example.magrathea.objectstorage.domain.valueobject.StorageClass;

import java.util.Objects;
import java.util.UUID;

/**
 * AWS S3 Bucket — aggregate root as a Java 17+ record.
 * Immutable, transparent, zero boilerplate.
 * A bucket is a container for objects stored in Amazon S3.
 *
 * Pure domain — NO framework dependencies.
 */
public record Bucket(
    Id id,
    String name,
    Region region,
    StorageClass storageClass,
    boolean versioningEnabled,
    boolean encryptionEnabled
) {

    public record Id(String value) {
        public Id { Objects.requireNonNull(value); }
        public static Id generate() { return new Id(UUID.randomUUID().toString()); }
        public static Id of(String value) { return new Id(value); }
    }

    /**
     * Compact constructor with validation.
     */
    public Bucket {
        Objects.requireNonNull(id);
        Objects.requireNonNull(name);
        Objects.requireNonNull(region);
        Objects.requireNonNull(storageClass);
        if (name.isBlank()) throw new IllegalArgumentException("Bucket name must not be blank");
        if (name.length() < 3 || name.length() > 63)
            throw new IllegalArgumentException("Bucket name must be 3-63 characters");
        if (!name.matches("^[a-z0-9][a-z0-9.-]*[a-z0-9]$"))
            throw new IllegalArgumentException("Bucket name must be lowercase, no underscores");
    }

    public Bucket withVersioningEnabled() {
        return new Bucket(id, name, region, storageClass, true, encryptionEnabled);
    }

    public Bucket withVersioningSuspended() {
        return new Bucket(id, name, region, storageClass, false, encryptionEnabled);
    }

    public Bucket withEncryptionEnabled() {
        return new Bucket(id, name, region, storageClass, versioningEnabled, true);
    }

    public static Bucket create(Id id, String name, Region region, StorageClass storageClass) {
        return new Bucket(id, name, region, storageClass, false, false);
    }
}
