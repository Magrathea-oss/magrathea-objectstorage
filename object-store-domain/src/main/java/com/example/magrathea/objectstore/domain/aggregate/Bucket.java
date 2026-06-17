package com.example.magrathea.objectstore.domain.aggregate;

import com.example.magrathea.objectstore.domain.event.ObjectStoreEvent;
import com.example.magrathea.objectstore.domain.valueobject.AbacConfiguration;
import com.example.magrathea.objectstore.domain.valueobject.BucketAccelerateConfiguration;
import com.example.magrathea.objectstore.domain.valueobject.BucketAnalyticsConfiguration;
import com.example.magrathea.objectstore.domain.valueobject.BucketConfig;
import com.example.magrathea.objectstore.domain.valueobject.BucketEncryptionConfiguration;
import com.example.magrathea.objectstore.domain.valueobject.BucketIntelligentTieringConfiguration;
import com.example.magrathea.objectstore.domain.valueobject.BucketInventoryConfiguration;
import com.example.magrathea.objectstore.domain.valueobject.BucketLifecycleConfiguration;
import com.example.magrathea.objectstore.domain.valueobject.BucketLoggingConfiguration;
import com.example.magrathea.objectstore.domain.valueobject.BucketMetadataConfiguration;
import com.example.magrathea.objectstore.domain.valueobject.BucketMetadataTableConfiguration;
import com.example.magrathea.objectstore.domain.valueobject.BucketMetricsConfiguration;
import com.example.magrathea.objectstore.domain.valueobject.BucketNotificationConfiguration;
import com.example.magrathea.objectstore.domain.valueobject.BucketOwnershipControls;
import com.example.magrathea.objectstore.domain.valueobject.BucketReplicationConfiguration;
import com.example.magrathea.objectstore.domain.valueobject.BucketRequestPaymentConfiguration;
import com.example.magrathea.objectstore.domain.valueobject.BucketWebsiteConfiguration;
import com.example.magrathea.objectstore.domain.valueobject.CorsConfiguration;
import com.example.magrathea.objectstore.domain.valueobject.PublicAccessBlockConfiguration;
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
        events = List.copyOf(events);
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

    // ── Per-config-type with* methods ──

    /**
     * Set CORS configuration. Returns new instance with a {@link ObjectStoreEvent.BucketCorsConfigured} event.
     */
    public Bucket withCorsConfiguration(CorsConfiguration cors) {
        Objects.requireNonNull(cors);
        var newConfig = bucketConfig.withCorsConfiguration(cors);
        var newEvents = appendEvent(
            new ObjectStoreEvent.BucketCorsConfigured(id, cors, Instant.now())
        );
        return new Bucket(id, name, region, storageClass, versioningEnabled, encryptionEnabled,
            directoryBucket, newConfig, newEvents);
    }

    /**
     * Remove CORS configuration. Returns new instance with a {@link ObjectStoreEvent.BucketCorsDeleted} event.
     */
    public Bucket withCorsConfigurationDeleted() {
        var newConfig = clearConfigField(0);
        var newEvents = appendEvent(
            new ObjectStoreEvent.BucketCorsDeleted(id, Instant.now())
        );
        return new Bucket(id, name, region, storageClass, versioningEnabled, encryptionEnabled,
            directoryBucket, newConfig, newEvents);
    }

    /**
     * Set lifecycle configuration. Returns new instance with a {@link ObjectStoreEvent.BucketLifecycleConfigured} event.
     */
    public Bucket withLifecycleConfiguration(BucketLifecycleConfiguration lifecycle) {
        Objects.requireNonNull(lifecycle);
        var newConfig = bucketConfig.withLifecycleConfiguration(lifecycle);
        var newEvents = appendEvent(
            new ObjectStoreEvent.BucketLifecycleConfigured(id, lifecycle, Instant.now())
        );
        return new Bucket(id, name, region, storageClass, versioningEnabled, encryptionEnabled,
            directoryBucket, newConfig, newEvents);
    }

    /**
     * Remove lifecycle configuration. Returns new instance with a {@link ObjectStoreEvent.BucketLifecycleDeleted} event.
     */
    public Bucket withLifecycleConfigurationDeleted() {
        var newConfig = clearConfigField(1);
        var newEvents = appendEvent(
            new ObjectStoreEvent.BucketLifecycleDeleted(id, Instant.now())
        );
        return new Bucket(id, name, region, storageClass, versioningEnabled, encryptionEnabled,
            directoryBucket, newConfig, newEvents);
    }

    /**
     * Set logging configuration. Returns new instance with a {@link ObjectStoreEvent.BucketLoggingConfigured} event.
     */
    public Bucket withLoggingConfiguration(BucketLoggingConfiguration logging) {
        Objects.requireNonNull(logging);
        var newConfig = bucketConfig.withLoggingConfiguration(logging);
        var newEvents = appendEvent(
            new ObjectStoreEvent.BucketLoggingConfigured(id, logging, Instant.now())
        );
        return new Bucket(id, name, region, storageClass, versioningEnabled, encryptionEnabled,
            directoryBucket, newConfig, newEvents);
    }

    /**
     * Remove logging configuration. Returns new instance with a {@link ObjectStoreEvent.BucketLoggingDeleted} event.
     */
    public Bucket withLoggingConfigurationDeleted() {
        var newConfig = clearConfigField(2);
        var newEvents = appendEvent(
            new ObjectStoreEvent.BucketLoggingDeleted(id, Instant.now())
        );
        return new Bucket(id, name, region, storageClass, versioningEnabled, encryptionEnabled,
            directoryBucket, newConfig, newEvents);
    }

    /**
     * Set notification configuration. Returns new instance with a {@link ObjectStoreEvent.BucketNotificationConfigured} event.
     */
    public Bucket withNotificationConfiguration(BucketNotificationConfiguration notification) {
        Objects.requireNonNull(notification);
        var newConfig = bucketConfig.withNotificationConfiguration(notification);
        var newEvents = appendEvent(
            new ObjectStoreEvent.BucketNotificationConfigured(id, notification, Instant.now())
        );
        return new Bucket(id, name, region, storageClass, versioningEnabled, encryptionEnabled,
            directoryBucket, newConfig, newEvents);
    }

    /**
     * Remove notification configuration. Returns new instance with a {@link ObjectStoreEvent.BucketNotificationDeleted} event.
     */
    public Bucket withNotificationConfigurationDeleted() {
        var newConfig = clearConfigField(3);
        var newEvents = appendEvent(
            new ObjectStoreEvent.BucketNotificationDeleted(id, Instant.now())
        );
        return new Bucket(id, name, region, storageClass, versioningEnabled, encryptionEnabled,
            directoryBucket, newConfig, newEvents);
    }

    /**
     * Set website configuration. Returns new instance with a {@link ObjectStoreEvent.BucketWebsiteConfigured} event.
     */
    public Bucket withWebsiteConfiguration(BucketWebsiteConfiguration website) {
        Objects.requireNonNull(website);
        var newConfig = bucketConfig.withWebsiteConfiguration(website);
        var newEvents = appendEvent(
            new ObjectStoreEvent.BucketWebsiteConfigured(id, website, Instant.now())
        );
        return new Bucket(id, name, region, storageClass, versioningEnabled, encryptionEnabled,
            directoryBucket, newConfig, newEvents);
    }

    /**
     * Remove website configuration. Returns new instance with a {@link ObjectStoreEvent.BucketWebsiteDeleted} event.
     */
    public Bucket withWebsiteConfigurationDeleted() {
        var newConfig = clearConfigField(4);
        var newEvents = appendEvent(
            new ObjectStoreEvent.BucketWebsiteDeleted(id, Instant.now())
        );
        return new Bucket(id, name, region, storageClass, versioningEnabled, encryptionEnabled,
            directoryBucket, newConfig, newEvents);
    }

    /**
     * Set bucket policy. Returns new instance with a {@link ObjectStoreEvent.BucketPolicyConfigured} event.
     */
    public Bucket withBucketPolicy(String policy) {
        Objects.requireNonNull(policy);
        var newConfig = bucketConfig.withBucketPolicy(policy);
        var newEvents = appendEvent(
            new ObjectStoreEvent.BucketPolicyConfigured(id, policy, Instant.now())
        );
        return new Bucket(id, name, region, storageClass, versioningEnabled, encryptionEnabled,
            directoryBucket, newConfig, newEvents);
    }

    /**
     * Remove bucket policy. Returns new instance with a {@link ObjectStoreEvent.BucketPolicyDeleted} event.
     */
    public Bucket withBucketPolicyDeleted() {
        var newConfig = clearConfigField(5);
        var newEvents = appendEvent(
            new ObjectStoreEvent.BucketPolicyDeleted(id, Instant.now())
        );
        return new Bucket(id, name, region, storageClass, versioningEnabled, encryptionEnabled,
            directoryBucket, newConfig, newEvents);
    }

    /**
     * Set encryption configuration. Returns new instance with a {@link ObjectStoreEvent.BucketEncryptionConfigured} event.
     */
    public Bucket withEncryptionConfiguration(BucketEncryptionConfiguration encryption) {
        Objects.requireNonNull(encryption);
        var newConfig = bucketConfig.withEncryptionConfiguration(encryption);
        var newEvents = appendEvent(
            new ObjectStoreEvent.BucketEncryptionConfigured(id, encryption, Instant.now())
        );
        return new Bucket(id, name, region, storageClass, versioningEnabled, encryptionEnabled,
            directoryBucket, newConfig, newEvents);
    }

    /**
     * Remove encryption configuration. Returns new instance with a {@link ObjectStoreEvent.BucketEncryptionDeleted} event.
     */
    public Bucket withEncryptionConfigurationDeleted() {
        var newConfig = clearConfigField(6);
        var newEvents = appendEvent(
            new ObjectStoreEvent.BucketEncryptionDeleted(id, Instant.now())
        );
        return new Bucket(id, name, region, storageClass, versioningEnabled, encryptionEnabled,
            directoryBucket, newConfig, newEvents);
    }

    /**
     * Set replication configuration. Returns new instance with a {@link ObjectStoreEvent.BucketReplicationConfigured} event.
     */
    public Bucket withReplicationConfiguration(BucketReplicationConfiguration replication) {
        Objects.requireNonNull(replication);
        var newConfig = bucketConfig.withReplicationConfiguration(replication);
        var newEvents = appendEvent(
            new ObjectStoreEvent.BucketReplicationConfigured(id, replication, Instant.now())
        );
        return new Bucket(id, name, region, storageClass, versioningEnabled, encryptionEnabled,
            directoryBucket, newConfig, newEvents);
    }

    /**
     * Remove replication configuration. Returns new instance with a {@link ObjectStoreEvent.BucketReplicationDeleted} event.
     */
    public Bucket withReplicationConfigurationDeleted() {
        var newConfig = clearConfigField(7);
        var newEvents = appendEvent(
            new ObjectStoreEvent.BucketReplicationDeleted(id, Instant.now())
        );
        return new Bucket(id, name, region, storageClass, versioningEnabled, encryptionEnabled,
            directoryBucket, newConfig, newEvents);
    }

    /**
     * Set request payment configuration. Returns new instance with a {@link ObjectStoreEvent.BucketRequestPaymentConfigured} event.
     */
    public Bucket withRequestPaymentConfiguration(BucketRequestPaymentConfiguration payment) {
        Objects.requireNonNull(payment);
        var newConfig = bucketConfig.withRequestPaymentConfiguration(payment);
        var newEvents = appendEvent(
            new ObjectStoreEvent.BucketRequestPaymentConfigured(id, payment, Instant.now())
        );
        return new Bucket(id, name, region, storageClass, versioningEnabled, encryptionEnabled,
            directoryBucket, newConfig, newEvents);
    }

    /**
     * Remove request payment configuration. Returns new instance with a {@link ObjectStoreEvent.BucketRequestPaymentDeleted} event.
     */
    public Bucket withRequestPaymentConfigurationDeleted() {
        var newConfig = clearConfigField(8);
        var newEvents = appendEvent(
            new ObjectStoreEvent.BucketRequestPaymentDeleted(id, Instant.now())
        );
        return new Bucket(id, name, region, storageClass, versioningEnabled, encryptionEnabled,
            directoryBucket, newConfig, newEvents);
    }

    /**
     * Set ownership controls. Returns new instance with a {@link ObjectStoreEvent.BucketOwnershipControlsConfigured} event.
     */
    public Bucket withOwnershipControls(BucketOwnershipControls ownership) {
        Objects.requireNonNull(ownership);
        var newConfig = bucketConfig.withOwnershipControls(ownership);
        var newEvents = appendEvent(
            new ObjectStoreEvent.BucketOwnershipControlsConfigured(id, ownership, Instant.now())
        );
        return new Bucket(id, name, region, storageClass, versioningEnabled, encryptionEnabled,
            directoryBucket, newConfig, newEvents);
    }

    /**
     * Remove ownership controls. Returns new instance with a {@link ObjectStoreEvent.BucketOwnershipControlsDeleted} event.
     */
    public Bucket withOwnershipControlsDeleted() {
        var newConfig = clearConfigField(9);
        var newEvents = appendEvent(
            new ObjectStoreEvent.BucketOwnershipControlsDeleted(id, Instant.now())
        );
        return new Bucket(id, name, region, storageClass, versioningEnabled, encryptionEnabled,
            directoryBucket, newConfig, newEvents);
    }

    /**
     * Set public access block configuration. Returns new instance with a {@link ObjectStoreEvent.BucketPublicAccessBlockConfigured} event.
     */
    public Bucket withPublicAccessBlockConfiguration(PublicAccessBlockConfiguration publicAccessBlock) {
        Objects.requireNonNull(publicAccessBlock);
        var newConfig = bucketConfig.withPublicAccessBlock(publicAccessBlock);
        var newEvents = appendEvent(
            new ObjectStoreEvent.BucketPublicAccessBlockConfigured(id, publicAccessBlock, Instant.now())
        );
        return new Bucket(id, name, region, storageClass, versioningEnabled, encryptionEnabled,
            directoryBucket, newConfig, newEvents);
    }

    /**
     * Remove public access block configuration. Returns new instance with a {@link ObjectStoreEvent.BucketPublicAccessBlockDeleted} event.
     */
    public Bucket withPublicAccessBlockConfigurationDeleted() {
        var newConfig = clearConfigField(10);
        var newEvents = appendEvent(
            new ObjectStoreEvent.BucketPublicAccessBlockDeleted(id, Instant.now())
        );
        return new Bucket(id, name, region, storageClass, versioningEnabled, encryptionEnabled,
            directoryBucket, newConfig, newEvents);
    }

    /**
     * Set accelerate configuration. Returns new instance with a {@link ObjectStoreEvent.BucketAccelerateConfigured} event.
     */
    public Bucket withAccelerateConfiguration(BucketAccelerateConfiguration accelerate) {
        Objects.requireNonNull(accelerate);
        var newConfig = bucketConfig.withAccelerateConfiguration(accelerate);
        var newEvents = appendEvent(
            new ObjectStoreEvent.BucketAccelerateConfigured(id, accelerate, Instant.now())
        );
        return new Bucket(id, name, region, storageClass, versioningEnabled, encryptionEnabled,
            directoryBucket, newConfig, newEvents);
    }

    /**
     * Remove accelerate configuration. Returns new instance with a {@link ObjectStoreEvent.BucketAccelerateDeleted} event.
     */
    public Bucket withAccelerateConfigurationDeleted() {
        var newConfig = clearConfigField(11);
        var newEvents = appendEvent(
            new ObjectStoreEvent.BucketAccelerateDeleted(id, Instant.now())
        );
        return new Bucket(id, name, region, storageClass, versioningEnabled, encryptionEnabled,
            directoryBucket, newConfig, newEvents);
    }

    /**
     * Set analytics configuration. Returns new instance with a {@link ObjectStoreEvent.BucketAnalyticsConfigured} event.
     */
    public Bucket withAnalyticsConfiguration(BucketAnalyticsConfiguration analytics) {
        Objects.requireNonNull(analytics);
        var newConfig = bucketConfig.withAnalyticsConfiguration(analytics);
        var newEvents = appendEvent(
            new ObjectStoreEvent.BucketAnalyticsConfigured(id, analytics, Instant.now())
        );
        return new Bucket(id, name, region, storageClass, versioningEnabled, encryptionEnabled,
            directoryBucket, newConfig, newEvents);
    }

    /**
     * Remove analytics configuration. Returns new instance with a {@link ObjectStoreEvent.BucketAnalyticsDeleted} event.
     */
    public Bucket withAnalyticsConfigurationDeleted() {
        var newConfig = clearConfigField(12);
        var newEvents = appendEvent(
            new ObjectStoreEvent.BucketAnalyticsDeleted(id, Instant.now())
        );
        return new Bucket(id, name, region, storageClass, versioningEnabled, encryptionEnabled,
            directoryBucket, newConfig, newEvents);
    }

    /**
     * Set inventory configuration. Returns new instance with a {@link ObjectStoreEvent.BucketInventoryConfigured} event.
     */
    public Bucket withInventoryConfiguration(BucketInventoryConfiguration inventory) {
        Objects.requireNonNull(inventory);
        var newConfig = bucketConfig.withInventoryConfiguration(inventory);
        var newEvents = appendEvent(
            new ObjectStoreEvent.BucketInventoryConfigured(id, inventory, Instant.now())
        );
        return new Bucket(id, name, region, storageClass, versioningEnabled, encryptionEnabled,
            directoryBucket, newConfig, newEvents);
    }

    /**
     * Remove inventory configuration. Returns new instance with a {@link ObjectStoreEvent.BucketInventoryDeleted} event.
     */
    public Bucket withInventoryConfigurationDeleted() {
        var newConfig = clearConfigField(13);
        var newEvents = appendEvent(
            new ObjectStoreEvent.BucketInventoryDeleted(id, Instant.now())
        );
        return new Bucket(id, name, region, storageClass, versioningEnabled, encryptionEnabled,
            directoryBucket, newConfig, newEvents);
    }

    /**
     * Set metrics configuration. Returns new instance with a {@link ObjectStoreEvent.BucketMetricsConfigured} event.
     */
    public Bucket withMetricsConfiguration(BucketMetricsConfiguration metrics) {
        Objects.requireNonNull(metrics);
        var newConfig = bucketConfig.withMetricsConfiguration(metrics);
        var newEvents = appendEvent(
            new ObjectStoreEvent.BucketMetricsConfigured(id, metrics, Instant.now())
        );
        return new Bucket(id, name, region, storageClass, versioningEnabled, encryptionEnabled,
            directoryBucket, newConfig, newEvents);
    }

    /**
     * Remove metrics configuration. Returns new instance with a {@link ObjectStoreEvent.BucketMetricsDeleted} event.
     */
    public Bucket withMetricsConfigurationDeleted() {
        var newConfig = clearConfigField(14);
        var newEvents = appendEvent(
            new ObjectStoreEvent.BucketMetricsDeleted(id, Instant.now())
        );
        return new Bucket(id, name, region, storageClass, versioningEnabled, encryptionEnabled,
            directoryBucket, newConfig, newEvents);
    }

    /**
     * Set intelligent-tiering configuration. Returns new instance with a {@link ObjectStoreEvent.BucketIntelligentTieringConfigured} event.
     */
    public Bucket withIntelligentTieringConfiguration(BucketIntelligentTieringConfiguration tiering) {
        Objects.requireNonNull(tiering);
        var newConfig = bucketConfig.withIntelligentTieringConfiguration(tiering);
        var newEvents = appendEvent(
            new ObjectStoreEvent.BucketIntelligentTieringConfigured(id, tiering, Instant.now())
        );
        return new Bucket(id, name, region, storageClass, versioningEnabled, encryptionEnabled,
            directoryBucket, newConfig, newEvents);
    }

    /**
     * Remove intelligent-tiering configuration. Returns new instance with a {@link ObjectStoreEvent.BucketIntelligentTieringDeleted} event.
     */
    public Bucket withIntelligentTieringConfigurationDeleted() {
        var newConfig = clearConfigField(15);
        var newEvents = appendEvent(
            new ObjectStoreEvent.BucketIntelligentTieringDeleted(id, Instant.now())
        );
        return new Bucket(id, name, region, storageClass, versioningEnabled, encryptionEnabled,
            directoryBucket, newConfig, newEvents);
    }

    // ── Helper: clear a single config field ──
    // BucketConfig constructor parameter order:
    //   (logging, website, notification, accelerate, analytics, inventory, metrics,
    //    intelligentTiering, encryption, lifecycle, replication, requestPayment,
    //    ownershipControls, publicAccessBlock, corsConfiguration, bucketPolicy,
    //    abacConfiguration, metadataConfiguration, metadataTableConfiguration)
    // Field indices by constructor position:
    //   0=logging, 1=website, 2=notification, 3=accelerate, 4=analytics,
    //   5=inventory, 6=metrics, 7=intelligentTiering, 8=encryption, 9=lifecycle,
    //   10=replication, 11=requestPayment, 12=ownershipControls, 13=publicAccessBlock,
    //   14=cors, 15=policy, 16=abac, 17=metadata, 18=metadataTable
    private BucketConfig clearConfigField(int fieldIndex) {
        var cfg = bucketConfig;
        return switch (fieldIndex) {
            // 0 = CORS → null position 14 (corsConfiguration)
            case 0  -> new BucketConfig(cfg.loggingConfiguration(), cfg.websiteConfiguration(), cfg.notificationConfiguration(), cfg.accelerateConfiguration(), cfg.analyticsConfiguration(), cfg.inventoryConfiguration(), cfg.metricsConfiguration(), cfg.intelligentTieringConfiguration(), cfg.encryptionConfiguration(), cfg.lifecycleConfiguration(), cfg.replicationConfiguration(), cfg.requestPaymentConfiguration(), cfg.ownershipControls(), cfg.publicAccessBlockConfiguration(), null, cfg.bucketPolicy(), cfg.abacConfiguration(), cfg.metadataConfiguration(), cfg.metadataTableConfiguration());
            // 1 = Lifecycle → null position 9 (lifecycleConfiguration)
            case 1  -> new BucketConfig(cfg.loggingConfiguration(), cfg.websiteConfiguration(), cfg.notificationConfiguration(), cfg.accelerateConfiguration(), cfg.analyticsConfiguration(), cfg.inventoryConfiguration(), cfg.metricsConfiguration(), cfg.intelligentTieringConfiguration(), cfg.encryptionConfiguration(), null, cfg.replicationConfiguration(), cfg.requestPaymentConfiguration(), cfg.ownershipControls(), cfg.publicAccessBlockConfiguration(), cfg.corsConfiguration(), cfg.bucketPolicy(), cfg.abacConfiguration(), cfg.metadataConfiguration(), cfg.metadataTableConfiguration());
            // 2 = Logging → null position 0 (loggingConfiguration)
            case 2  -> new BucketConfig(null, cfg.websiteConfiguration(), cfg.notificationConfiguration(), cfg.accelerateConfiguration(), cfg.analyticsConfiguration(), cfg.inventoryConfiguration(), cfg.metricsConfiguration(), cfg.intelligentTieringConfiguration(), cfg.encryptionConfiguration(), cfg.lifecycleConfiguration(), cfg.replicationConfiguration(), cfg.requestPaymentConfiguration(), cfg.ownershipControls(), cfg.publicAccessBlockConfiguration(), cfg.corsConfiguration(), cfg.bucketPolicy(), cfg.abacConfiguration(), cfg.metadataConfiguration(), cfg.metadataTableConfiguration());
            // 3 = Notification → null position 2 (notificationConfiguration)
            case 3  -> new BucketConfig(cfg.loggingConfiguration(), cfg.websiteConfiguration(), null, cfg.accelerateConfiguration(), cfg.analyticsConfiguration(), cfg.inventoryConfiguration(), cfg.metricsConfiguration(), cfg.intelligentTieringConfiguration(), cfg.encryptionConfiguration(), cfg.lifecycleConfiguration(), cfg.replicationConfiguration(), cfg.requestPaymentConfiguration(), cfg.ownershipControls(), cfg.publicAccessBlockConfiguration(), cfg.corsConfiguration(), cfg.bucketPolicy(), cfg.abacConfiguration(), cfg.metadataConfiguration(), cfg.metadataTableConfiguration());
            // 4 = Website → null position 1 (websiteConfiguration)
            case 4  -> new BucketConfig(cfg.loggingConfiguration(), null, cfg.notificationConfiguration(), cfg.accelerateConfiguration(), cfg.analyticsConfiguration(), cfg.inventoryConfiguration(), cfg.metricsConfiguration(), cfg.intelligentTieringConfiguration(), cfg.encryptionConfiguration(), cfg.lifecycleConfiguration(), cfg.replicationConfiguration(), cfg.requestPaymentConfiguration(), cfg.ownershipControls(), cfg.publicAccessBlockConfiguration(), cfg.corsConfiguration(), cfg.bucketPolicy(), cfg.abacConfiguration(), cfg.metadataConfiguration(), cfg.metadataTableConfiguration());
            // 5 = Policy → null position 15 (bucketPolicy)
            case 5  -> new BucketConfig(cfg.loggingConfiguration(), cfg.websiteConfiguration(), cfg.notificationConfiguration(), cfg.accelerateConfiguration(), cfg.analyticsConfiguration(), cfg.inventoryConfiguration(), cfg.metricsConfiguration(), cfg.intelligentTieringConfiguration(), cfg.encryptionConfiguration(), cfg.lifecycleConfiguration(), cfg.replicationConfiguration(), cfg.requestPaymentConfiguration(), cfg.ownershipControls(), cfg.publicAccessBlockConfiguration(), cfg.corsConfiguration(), null, cfg.abacConfiguration(), cfg.metadataConfiguration(), cfg.metadataTableConfiguration());
            // 6 = Encryption → null position 8 (encryptionConfiguration)
            case 6  -> new BucketConfig(cfg.loggingConfiguration(), cfg.websiteConfiguration(), cfg.notificationConfiguration(), cfg.accelerateConfiguration(), cfg.analyticsConfiguration(), cfg.inventoryConfiguration(), cfg.metricsConfiguration(), cfg.intelligentTieringConfiguration(), null, cfg.lifecycleConfiguration(), cfg.replicationConfiguration(), cfg.requestPaymentConfiguration(), cfg.ownershipControls(), cfg.publicAccessBlockConfiguration(), cfg.corsConfiguration(), cfg.bucketPolicy(), cfg.abacConfiguration(), cfg.metadataConfiguration(), cfg.metadataTableConfiguration());
            // 7 = Replication → null position 10 (replicationConfiguration)
            case 7  -> new BucketConfig(cfg.loggingConfiguration(), cfg.websiteConfiguration(), cfg.notificationConfiguration(), cfg.accelerateConfiguration(), cfg.analyticsConfiguration(), cfg.inventoryConfiguration(), cfg.metricsConfiguration(), cfg.intelligentTieringConfiguration(), cfg.encryptionConfiguration(), cfg.lifecycleConfiguration(), null, cfg.requestPaymentConfiguration(), cfg.ownershipControls(), cfg.publicAccessBlockConfiguration(), cfg.corsConfiguration(), cfg.bucketPolicy(), cfg.abacConfiguration(), cfg.metadataConfiguration(), cfg.metadataTableConfiguration());
            // 8 = RequestPayment → null position 11 (requestPaymentConfiguration)
            case 8  -> new BucketConfig(cfg.loggingConfiguration(), cfg.websiteConfiguration(), cfg.notificationConfiguration(), cfg.accelerateConfiguration(), cfg.analyticsConfiguration(), cfg.inventoryConfiguration(), cfg.metricsConfiguration(), cfg.intelligentTieringConfiguration(), cfg.encryptionConfiguration(), cfg.lifecycleConfiguration(), cfg.replicationConfiguration(), null, cfg.ownershipControls(), cfg.publicAccessBlockConfiguration(), cfg.corsConfiguration(), cfg.bucketPolicy(), cfg.abacConfiguration(), cfg.metadataConfiguration(), cfg.metadataTableConfiguration());
            // 9 = OwnershipControls → null position 12 (ownershipControls)
            case 9  -> new BucketConfig(cfg.loggingConfiguration(), cfg.websiteConfiguration(), cfg.notificationConfiguration(), cfg.accelerateConfiguration(), cfg.analyticsConfiguration(), cfg.inventoryConfiguration(), cfg.metricsConfiguration(), cfg.intelligentTieringConfiguration(), cfg.encryptionConfiguration(), cfg.lifecycleConfiguration(), cfg.replicationConfiguration(), cfg.requestPaymentConfiguration(), null, cfg.publicAccessBlockConfiguration(), cfg.corsConfiguration(), cfg.bucketPolicy(), cfg.abacConfiguration(), cfg.metadataConfiguration(), cfg.metadataTableConfiguration());
            // 10 = PublicAccessBlock → null position 13 (publicAccessBlockConfiguration)
            case 10 -> new BucketConfig(cfg.loggingConfiguration(), cfg.websiteConfiguration(), cfg.notificationConfiguration(), cfg.accelerateConfiguration(), cfg.analyticsConfiguration(), cfg.inventoryConfiguration(), cfg.metricsConfiguration(), cfg.intelligentTieringConfiguration(), cfg.encryptionConfiguration(), cfg.lifecycleConfiguration(), cfg.replicationConfiguration(), cfg.requestPaymentConfiguration(), cfg.ownershipControls(), null, cfg.corsConfiguration(), cfg.bucketPolicy(), cfg.abacConfiguration(), cfg.metadataConfiguration(), cfg.metadataTableConfiguration());
            // 11 = Accelerate → null position 3 (accelerateConfiguration)
            case 11 -> new BucketConfig(cfg.loggingConfiguration(), cfg.websiteConfiguration(), cfg.notificationConfiguration(), null, cfg.analyticsConfiguration(), cfg.inventoryConfiguration(), cfg.metricsConfiguration(), cfg.intelligentTieringConfiguration(), cfg.encryptionConfiguration(), cfg.lifecycleConfiguration(), cfg.replicationConfiguration(), cfg.requestPaymentConfiguration(), cfg.ownershipControls(), cfg.publicAccessBlockConfiguration(), cfg.corsConfiguration(), cfg.bucketPolicy(), cfg.abacConfiguration(), cfg.metadataConfiguration(), cfg.metadataTableConfiguration());
            // 12 = Analytics → null position 4 (analyticsConfiguration)
            case 12 -> new BucketConfig(cfg.loggingConfiguration(), cfg.websiteConfiguration(), cfg.notificationConfiguration(), cfg.accelerateConfiguration(), null, cfg.inventoryConfiguration(), cfg.metricsConfiguration(), cfg.intelligentTieringConfiguration(), cfg.encryptionConfiguration(), cfg.lifecycleConfiguration(), cfg.replicationConfiguration(), cfg.requestPaymentConfiguration(), cfg.ownershipControls(), cfg.publicAccessBlockConfiguration(), cfg.corsConfiguration(), cfg.bucketPolicy(), cfg.abacConfiguration(), cfg.metadataConfiguration(), cfg.metadataTableConfiguration());
            // 13 = Inventory → null position 5 (inventoryConfiguration)
            case 13 -> new BucketConfig(cfg.loggingConfiguration(), cfg.websiteConfiguration(), cfg.notificationConfiguration(), cfg.accelerateConfiguration(), cfg.analyticsConfiguration(), null, cfg.metricsConfiguration(), cfg.intelligentTieringConfiguration(), cfg.encryptionConfiguration(), cfg.lifecycleConfiguration(), cfg.replicationConfiguration(), cfg.requestPaymentConfiguration(), cfg.ownershipControls(), cfg.publicAccessBlockConfiguration(), cfg.corsConfiguration(), cfg.bucketPolicy(), cfg.abacConfiguration(), cfg.metadataConfiguration(), cfg.metadataTableConfiguration());
            // 14 = Metrics → null position 6 (metricsConfiguration)
            case 14 -> new BucketConfig(cfg.loggingConfiguration(), cfg.websiteConfiguration(), cfg.notificationConfiguration(), cfg.accelerateConfiguration(), cfg.analyticsConfiguration(), cfg.inventoryConfiguration(), null, cfg.intelligentTieringConfiguration(), cfg.encryptionConfiguration(), cfg.lifecycleConfiguration(), cfg.replicationConfiguration(), cfg.requestPaymentConfiguration(), cfg.ownershipControls(), cfg.publicAccessBlockConfiguration(), cfg.corsConfiguration(), cfg.bucketPolicy(), cfg.abacConfiguration(), cfg.metadataConfiguration(), cfg.metadataTableConfiguration());
            // 15 = IntelligentTiering → null position 7 (intelligentTieringConfiguration)
            case 15 -> new BucketConfig(cfg.loggingConfiguration(), cfg.websiteConfiguration(), cfg.notificationConfiguration(), cfg.accelerateConfiguration(), cfg.analyticsConfiguration(), cfg.inventoryConfiguration(), cfg.metricsConfiguration(), null, cfg.encryptionConfiguration(), cfg.lifecycleConfiguration(), cfg.replicationConfiguration(), cfg.requestPaymentConfiguration(), cfg.ownershipControls(), cfg.publicAccessBlockConfiguration(), cfg.corsConfiguration(), cfg.bucketPolicy(), cfg.abacConfiguration(), cfg.metadataConfiguration(), cfg.metadataTableConfiguration());
            default -> cfg;
        };
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
