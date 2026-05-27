package com.example.magrathea.objectstore.domain.aggregate;

import com.example.magrathea.objectstore.domain.event.ObjectStoreEvent;
import com.example.magrathea.objectstore.domain.valueobject.AbacConfiguration;
import com.example.magrathea.objectstore.domain.valueobject.BucketConfig;
import com.example.magrathea.objectstore.domain.valueobject.BucketMetadataConfiguration;
import com.example.magrathea.objectstore.domain.valueobject.BucketMetadataTableConfiguration;
import com.example.magrathea.objectstore.domain.valueobject.Region;
import com.example.magrathea.objectstore.domain.valueobject.StorageClass;

import java.time.Instant;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Objects;
import java.util.UUID;

/**
 * AWS S3 Bucket — aggregate root as a Java 17+ record.
 * Immutable, transparent, zero boilerplate.
 * A bucket is a container for objects stored in Amazon S3.
 *
 * Pure domain — NO framework dependencies.
 *
 * Transitions return NEW instances with accumulated domain events.
 * Call {@link #domainEvents()} to retrieve events after a transition.
 * Call {@link #clearEvents()} to obtain a clean instance for persistence.
 *
 * Configuration is stored via {@link BucketConfig} — a single value object holding
 * all 15+ specialized config types as nullable fields.
 * Use {@link #withBucketConfig(BucketConfig)} to set all config at once.
 * Individual config types are accessed via {@code bucket.bucketConfig().getXxxConfiguration()}.
 */
public record Bucket(
    Id id,
    String name,
    Region region,
    StorageClass storageClass,
    boolean versioningEnabled,
    boolean encryptionEnabled,
    boolean directoryBucket,
    BucketConfig bucketConfig,
    List<ObjectStoreEvent> events
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
        Objects.requireNonNull(bucketConfig);
        Objects.requireNonNull(events);
        if (name.isBlank()) throw new IllegalArgumentException("Bucket name must not be blank");
        if (name.length() < 3 || name.length() > 63)
            throw new IllegalArgumentException("Bucket name must be 3-63 characters");
        if (!name.matches("^[a-z0-9][a-z0-9.-]*[a-z0-9]$"))
            throw new IllegalArgumentException("Bucket name must be lowercase, no underscores");
    }

    // ── Factory methods ──

    /**
     * Factory method — create a new bucket.
     * Records a {@link ObjectStoreEvent.BucketCreated} event.
     */
    public static Bucket create(Id id, String name, Region region, StorageClass storageClass) {
        return create(id, name, region, storageClass, BucketConfig.EMPTY);
    }

    /**
     * Factory method — create a new bucket with an initial {@link BucketConfig}.
     * Records a {@link ObjectStoreEvent.BucketCreated} event.
     */
    public static Bucket create(Id id, String name, Region region, StorageClass storageClass,
                                BucketConfig bucketConfig) {
        return create(id, name, region, storageClass, false, bucketConfig);
    }

    /**
     * Factory method — create a new bucket with directory bucket flag and config.
     * Records a {@link ObjectStoreEvent.BucketCreated} event.
     */
    public static Bucket create(Id id, String name, Region region, StorageClass storageClass,
                                boolean directoryBucket, BucketConfig bucketConfig) {
        Objects.requireNonNull(id);
        Objects.requireNonNull(name);
        Objects.requireNonNull(region);
        Objects.requireNonNull(storageClass);
        Objects.requireNonNull(bucketConfig);
        var now = Instant.now();
        var events = List.<ObjectStoreEvent>of(
            new ObjectStoreEvent.BucketCreated(id, name, now)
        );
        return new Bucket(id, name, region, storageClass, false, false, directoryBucket, bucketConfig, events);
    }

    /**
     * Factory method — restore from persistence (no events).
     */
    public static Bucket restore(Id id, String name, Region region, StorageClass storageClass,
                                  boolean versioningEnabled, boolean encryptionEnabled,
                                  boolean directoryBucket, BucketConfig bucketConfig) {
        Objects.requireNonNull(id);
        Objects.requireNonNull(name);
        Objects.requireNonNull(region);
        Objects.requireNonNull(storageClass);
        Objects.requireNonNull(bucketConfig);
        return new Bucket(id, name, region, storageClass, versioningEnabled, encryptionEnabled,
            directoryBucket, bucketConfig, List.of());
    }

    /**
     * Legacy restore without config — defaults to BucketConfig.EMPTY and non-directory.
     */
    public static Bucket restore(Id id, String name, Region region, StorageClass storageClass,
                                  boolean versioningEnabled, boolean encryptionEnabled) {
        return restore(id, name, region, storageClass, versioningEnabled, encryptionEnabled,
            false, BucketConfig.EMPTY);
    }

    /**
     * Legacy restore with config but without directoryBucket flag — defaults to non-directory.
     */
    public static Bucket restore(Id id, String name, Region region, StorageClass storageClass,
                                  boolean versioningEnabled, boolean encryptionEnabled,
                                  BucketConfig bucketConfig) {
        return restore(id, name, region, storageClass, versioningEnabled, encryptionEnabled,
            false, bucketConfig);
    }

    // ── State transitions ──

    /**
     * Enable versioning. Returns new instance with a {@link ObjectStoreEvent.BucketVersioningEnabled} event.
     */
    public Bucket withVersioningEnabled() {
        var newEvents = appendEvent(
            new ObjectStoreEvent.BucketVersioningEnabled(id, Instant.now())
        );
        return new Bucket(id, name, region, storageClass, true, encryptionEnabled, directoryBucket, bucketConfig, newEvents);
    }

    /**
     * Suspend versioning. Returns new instance with a {@link ObjectStoreEvent.BucketVersioningSuspended} event.
     */
    public Bucket withVersioningSuspended() {
        var newEvents = appendEvent(
            new ObjectStoreEvent.BucketVersioningSuspended(id, Instant.now())
        );
        return new Bucket(id, name, region, storageClass, false, encryptionEnabled, directoryBucket, bucketConfig, newEvents);
    }

    /**
     * Enable encryption. Returns new instance with a {@link ObjectStoreEvent.BucketEncryptionEnabled} event.
     */
    public Bucket withEncryptionEnabled() {
        var newEvents = appendEvent(
            new ObjectStoreEvent.BucketEncryptionEnabled(id, Instant.now())
        );
        return new Bucket(id, name, region, storageClass, versioningEnabled, true, directoryBucket, bucketConfig, newEvents);
    }

    /**
     * Delete this bucket. Returns new instance with a {@link ObjectStoreEvent.BucketDeleted} event.
     */
    public Bucket withDeleted() {
        var newEvents = appendEvent(
            new ObjectStoreEvent.BucketDeleted(id, Instant.now())
        );
        return new Bucket(id, name, region, storageClass, versioningEnabled, encryptionEnabled,
            directoryBucket, bucketConfig, newEvents);
    }

    // ── Directory bucket transition ──

    /**
     * Mark this bucket as a directory bucket. Returns new instance with a
     * {@link ObjectStoreEvent.DirectoryBucketConfigured} event.
     */
    public Bucket withDirectoryBucket() {
        var newEvents = appendEvent(
            new ObjectStoreEvent.DirectoryBucketConfigured(id, Instant.now())
        );
        return new Bucket(id, name, region, storageClass, versioningEnabled, encryptionEnabled,
            true, bucketConfig, newEvents);
    }

    // ── Config transition ──

    /**
     * Set the full {@link BucketConfig}. Returns new instance with a
     * {@link ObjectStoreEvent.BucketConfigChanged} event.
     */
    public Bucket withBucketConfig(BucketConfig config) {
        Objects.requireNonNull(config);
        var newEvents = appendEvent(
            new ObjectStoreEvent.BucketConfigChanged(id, config, Instant.now())
        );
        return new Bucket(id, name, region, storageClass, versioningEnabled, encryptionEnabled,
            directoryBucket, config, newEvents);
    }

    // ── Phase F config transitions ──

    /**
     * Set ABAC configuration. Returns new instance with an {@link ObjectStoreEvent.AbacConfigChanged} event.
     */
    public Bucket withAbacConfiguration(AbacConfiguration abac) {
        Objects.requireNonNull(abac);
        var newConfig = bucketConfig.withAbacConfiguration(abac);
        var newEvents = appendEvent(
            new ObjectStoreEvent.AbacConfigChanged(id, abac, Instant.now())
        );
        return new Bucket(id, name, region, storageClass, versioningEnabled, encryptionEnabled,
            directoryBucket, newConfig, newEvents);
    }

    /**
     * Set metadata configuration. Returns new instance with a
     * {@link ObjectStoreEvent.MetadataConfigChanged} event.
     */
    public Bucket withMetadataConfiguration(BucketMetadataConfiguration metadata) {
        Objects.requireNonNull(metadata);
        var newConfig = bucketConfig.withMetadataConfiguration(metadata);
        var newEvents = appendEvent(
            new ObjectStoreEvent.MetadataConfigChanged(id, metadata, Instant.now())
        );
        return new Bucket(id, name, region, storageClass, versioningEnabled, encryptionEnabled,
            directoryBucket, newConfig, newEvents);
    }

    /**
     * Set metadata table configuration. Returns new instance with a
     * {@link ObjectStoreEvent.MetadataTableConfigChanged} event.
     */
    public Bucket withMetadataTableConfiguration(BucketMetadataTableConfiguration metadataTable) {
        Objects.requireNonNull(metadataTable);
        var newConfig = bucketConfig.withMetadataTableConfiguration(metadataTable);
        var newEvents = appendEvent(
            new ObjectStoreEvent.MetadataTableConfigChanged(id, metadataTable, Instant.now())
        );
        return new Bucket(id, name, region, storageClass, versioningEnabled, encryptionEnabled,
            directoryBucket, newConfig, newEvents);
    }

    // ── Event management ──

    /**
     * Returns the accumulated domain events since the last {@link #clearEvents()}.
     */
    public List<ObjectStoreEvent> domainEvents() {
        return events;
    }

    /**
     * Returns a new Bucket with all events cleared — suitable for persistence.
     */
    public Bucket clearEvents() {
        return new Bucket(id, name, region, storageClass, versioningEnabled, encryptionEnabled,
            directoryBucket, bucketConfig, List.of());
    }

    private List<ObjectStoreEvent> appendEvent(ObjectStoreEvent event) {
        var newEvents = new ArrayList<ObjectStoreEvent>(events.size() + 1);
        newEvents.addAll(events);
        newEvents.add(event);
        return Collections.unmodifiableList(newEvents);
    }
}
