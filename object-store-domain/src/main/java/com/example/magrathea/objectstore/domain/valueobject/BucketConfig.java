package com.example.magrathea.objectstore.domain.valueobject;

import java.util.Objects;
import java.util.Optional;

/**
 * BucketConfig — wrapper record holding all 18 specialized config types as nullable fields,
 * plus a bucketPolicy (String) field.
 *
 * Each config type has a getter returning {@link Optional} and a with* method returning a new
 * {@link BucketConfig} with that field set.
 *
 * Pure domain — NO framework dependencies.
 */
public record BucketConfig(
    BucketLoggingConfiguration loggingConfiguration,
    BucketWebsiteConfiguration websiteConfiguration,
    BucketNotificationConfiguration notificationConfiguration,
    BucketAccelerateConfiguration accelerateConfiguration,
    BucketAnalyticsConfiguration analyticsConfiguration,
    BucketInventoryConfiguration inventoryConfiguration,
    BucketMetricsConfiguration metricsConfiguration,
    BucketIntelligentTieringConfiguration intelligentTieringConfiguration,
    BucketEncryptionConfiguration encryptionConfiguration,
    BucketLifecycleConfiguration lifecycleConfiguration,
    BucketReplicationConfiguration replicationConfiguration,
    BucketRequestPaymentConfiguration requestPaymentConfiguration,
    BucketOwnershipControls ownershipControls,
    PublicAccessBlockConfiguration publicAccessBlockConfiguration,
    CorsConfiguration corsConfiguration,
    String bucketPolicy,
    AbacConfiguration abacConfiguration,
    BucketMetadataConfiguration metadataConfiguration,
    BucketMetadataTableConfiguration metadataTableConfiguration,
    BucketObjectLockConfiguration objectLockConfiguration,
    BucketInventoryTableConfiguration inventoryTableConfiguration,
    BucketJournalTableConfiguration journalTableConfiguration
) {
    /**
     * EMPTY constant — all fields null.
     */
    public static final BucketConfig EMPTY = new BucketConfig(null, null, null, null, null, null,
        null, null, null, null, null, null, null, null, null, null, null, null, null,
        null, null, null);

    // ── Getter methods returning Optional ──

    public Optional<BucketLoggingConfiguration> getLoggingConfiguration() {
        return Optional.ofNullable(loggingConfiguration);
    }

    public Optional<BucketWebsiteConfiguration> getWebsiteConfiguration() {
        return Optional.ofNullable(websiteConfiguration);
    }

    public Optional<BucketNotificationConfiguration> getNotificationConfiguration() {
        return Optional.ofNullable(notificationConfiguration);
    }

    public Optional<BucketAccelerateConfiguration> getAccelerateConfiguration() {
        return Optional.ofNullable(accelerateConfiguration);
    }

    public Optional<BucketAnalyticsConfiguration> getAnalyticsConfiguration() {
        return Optional.ofNullable(analyticsConfiguration);
    }

    public Optional<BucketInventoryConfiguration> getInventoryConfiguration() {
        return Optional.ofNullable(inventoryConfiguration);
    }

    public Optional<BucketMetricsConfiguration> getMetricsConfiguration() {
        return Optional.ofNullable(metricsConfiguration);
    }

    public Optional<BucketIntelligentTieringConfiguration> getIntelligentTieringConfiguration() {
        return Optional.ofNullable(intelligentTieringConfiguration);
    }

    public Optional<BucketEncryptionConfiguration> getEncryptionConfiguration() {
        return Optional.ofNullable(encryptionConfiguration);
    }

    public Optional<BucketLifecycleConfiguration> getLifecycleConfiguration() {
        return Optional.ofNullable(lifecycleConfiguration);
    }

    public Optional<BucketReplicationConfiguration> getReplicationConfiguration() {
        return Optional.ofNullable(replicationConfiguration);
    }

    public Optional<BucketRequestPaymentConfiguration> getRequestPaymentConfiguration() {
        return Optional.ofNullable(requestPaymentConfiguration);
    }

    public Optional<BucketOwnershipControls> getOwnershipControls() {
        return Optional.ofNullable(ownershipControls);
    }

    public Optional<PublicAccessBlockConfiguration> getPublicAccessBlockConfiguration() {
        return Optional.ofNullable(publicAccessBlockConfiguration);
    }

    public Optional<CorsConfiguration> getCorsConfiguration() {
        return Optional.ofNullable(corsConfiguration);
    }

    public Optional<String> getBucketPolicy() {
        return Optional.ofNullable(bucketPolicy);
    }

    public Optional<AbacConfiguration> getAbacConfiguration() {
        return Optional.ofNullable(abacConfiguration);
    }

    public Optional<BucketMetadataConfiguration> getMetadataConfiguration() {
        return Optional.ofNullable(metadataConfiguration);
    }

    public Optional<BucketMetadataTableConfiguration> getMetadataTableConfiguration() {
        return Optional.ofNullable(metadataTableConfiguration);
    }

    public Optional<BucketObjectLockConfiguration> getObjectLockConfiguration() {
        return Optional.ofNullable(objectLockConfiguration);
    }

    public Optional<BucketInventoryTableConfiguration> getInventoryTableConfiguration() {
        return Optional.ofNullable(inventoryTableConfiguration);
    }

    public Optional<BucketJournalTableConfiguration> getJournalTableConfiguration() {
        return Optional.ofNullable(journalTableConfiguration);
    }

    // ── with* methods ──

    public BucketConfig withLoggingConfiguration(BucketLoggingConfiguration logging) {
        return new BucketConfig(logging, websiteConfiguration, notificationConfiguration,
            accelerateConfiguration, analyticsConfiguration, inventoryConfiguration,
            metricsConfiguration, intelligentTieringConfiguration, encryptionConfiguration,
            lifecycleConfiguration, replicationConfiguration, requestPaymentConfiguration,
            ownershipControls, publicAccessBlockConfiguration, corsConfiguration, bucketPolicy,
            abacConfiguration, metadataConfiguration, metadataTableConfiguration, objectLockConfiguration, inventoryTableConfiguration, journalTableConfiguration);
    }

    public BucketConfig withWebsiteConfiguration(BucketWebsiteConfiguration website) {
        return new BucketConfig(loggingConfiguration, website, notificationConfiguration,
            accelerateConfiguration, analyticsConfiguration, inventoryConfiguration,
            metricsConfiguration, intelligentTieringConfiguration, encryptionConfiguration,
            lifecycleConfiguration, replicationConfiguration, requestPaymentConfiguration,
            ownershipControls, publicAccessBlockConfiguration, corsConfiguration, bucketPolicy,
            abacConfiguration, metadataConfiguration, metadataTableConfiguration, objectLockConfiguration, inventoryTableConfiguration, journalTableConfiguration);
    }

    public BucketConfig withNotificationConfiguration(BucketNotificationConfiguration notification) {
        return new BucketConfig(loggingConfiguration, websiteConfiguration, notification,
            accelerateConfiguration, analyticsConfiguration, inventoryConfiguration,
            metricsConfiguration, intelligentTieringConfiguration, encryptionConfiguration,
            lifecycleConfiguration, replicationConfiguration, requestPaymentConfiguration,
            ownershipControls, publicAccessBlockConfiguration, corsConfiguration, bucketPolicy,
            abacConfiguration, metadataConfiguration, metadataTableConfiguration, objectLockConfiguration, inventoryTableConfiguration, journalTableConfiguration);
    }

    public BucketConfig withAccelerateConfiguration(BucketAccelerateConfiguration accelerate) {
        return new BucketConfig(loggingConfiguration, websiteConfiguration, notificationConfiguration,
            accelerate, analyticsConfiguration, inventoryConfiguration,
            metricsConfiguration, intelligentTieringConfiguration, encryptionConfiguration,
            lifecycleConfiguration, replicationConfiguration, requestPaymentConfiguration,
            ownershipControls, publicAccessBlockConfiguration, corsConfiguration, bucketPolicy,
            abacConfiguration, metadataConfiguration, metadataTableConfiguration, objectLockConfiguration, inventoryTableConfiguration, journalTableConfiguration);
    }

    public BucketConfig withAnalyticsConfiguration(BucketAnalyticsConfiguration analytics) {
        return new BucketConfig(loggingConfiguration, websiteConfiguration, notificationConfiguration,
            accelerateConfiguration, analytics, inventoryConfiguration,
            metricsConfiguration, intelligentTieringConfiguration, encryptionConfiguration,
            lifecycleConfiguration, replicationConfiguration, requestPaymentConfiguration,
            ownershipControls, publicAccessBlockConfiguration, corsConfiguration, bucketPolicy,
            abacConfiguration, metadataConfiguration, metadataTableConfiguration, objectLockConfiguration, inventoryTableConfiguration, journalTableConfiguration);
    }

    public BucketConfig withInventoryConfiguration(BucketInventoryConfiguration inventory) {
        return new BucketConfig(loggingConfiguration, websiteConfiguration, notificationConfiguration,
            accelerateConfiguration, analyticsConfiguration, inventory,
            metricsConfiguration, intelligentTieringConfiguration, encryptionConfiguration,
            lifecycleConfiguration, replicationConfiguration, requestPaymentConfiguration,
            ownershipControls, publicAccessBlockConfiguration, corsConfiguration, bucketPolicy,
            abacConfiguration, metadataConfiguration, metadataTableConfiguration, objectLockConfiguration, inventoryTableConfiguration, journalTableConfiguration);
    }

    public BucketConfig withMetricsConfiguration(BucketMetricsConfiguration metrics) {
        return new BucketConfig(loggingConfiguration, websiteConfiguration, notificationConfiguration,
            accelerateConfiguration, analyticsConfiguration, inventoryConfiguration,
            metrics, intelligentTieringConfiguration, encryptionConfiguration,
            lifecycleConfiguration, replicationConfiguration, requestPaymentConfiguration,
            ownershipControls, publicAccessBlockConfiguration, corsConfiguration, bucketPolicy,
            abacConfiguration, metadataConfiguration, metadataTableConfiguration, objectLockConfiguration, inventoryTableConfiguration, journalTableConfiguration);
    }

    public BucketConfig withIntelligentTieringConfiguration(BucketIntelligentTieringConfiguration tiering) {
        return new BucketConfig(loggingConfiguration, websiteConfiguration, notificationConfiguration,
            accelerateConfiguration, analyticsConfiguration, inventoryConfiguration,
            metricsConfiguration, tiering, encryptionConfiguration,
            lifecycleConfiguration, replicationConfiguration, requestPaymentConfiguration,
            ownershipControls, publicAccessBlockConfiguration, corsConfiguration, bucketPolicy,
            abacConfiguration, metadataConfiguration, metadataTableConfiguration, objectLockConfiguration, inventoryTableConfiguration, journalTableConfiguration);
    }

    public BucketConfig withEncryptionConfiguration(BucketEncryptionConfiguration encryption) {
        return new BucketConfig(loggingConfiguration, websiteConfiguration, notificationConfiguration,
            accelerateConfiguration, analyticsConfiguration, inventoryConfiguration,
            metricsConfiguration, intelligentTieringConfiguration, encryption,
            lifecycleConfiguration, replicationConfiguration, requestPaymentConfiguration,
            ownershipControls, publicAccessBlockConfiguration, corsConfiguration, bucketPolicy,
            abacConfiguration, metadataConfiguration, metadataTableConfiguration, objectLockConfiguration, inventoryTableConfiguration, journalTableConfiguration);
    }

    public BucketConfig withLifecycleConfiguration(BucketLifecycleConfiguration lifecycle) {
        return new BucketConfig(loggingConfiguration, websiteConfiguration, notificationConfiguration,
            accelerateConfiguration, analyticsConfiguration, inventoryConfiguration,
            metricsConfiguration, intelligentTieringConfiguration, encryptionConfiguration,
            lifecycle, replicationConfiguration, requestPaymentConfiguration,
            ownershipControls, publicAccessBlockConfiguration, corsConfiguration, bucketPolicy,
            abacConfiguration, metadataConfiguration, metadataTableConfiguration, objectLockConfiguration, inventoryTableConfiguration, journalTableConfiguration);
    }

    public BucketConfig withReplicationConfiguration(BucketReplicationConfiguration replication) {
        return new BucketConfig(loggingConfiguration, websiteConfiguration, notificationConfiguration,
            accelerateConfiguration, analyticsConfiguration, inventoryConfiguration,
            metricsConfiguration, intelligentTieringConfiguration, encryptionConfiguration,
            lifecycleConfiguration, replication, requestPaymentConfiguration,
            ownershipControls, publicAccessBlockConfiguration, corsConfiguration, bucketPolicy,
            abacConfiguration, metadataConfiguration, metadataTableConfiguration, objectLockConfiguration, inventoryTableConfiguration, journalTableConfiguration);
    }

    public BucketConfig withRequestPaymentConfiguration(BucketRequestPaymentConfiguration payment) {
        return new BucketConfig(loggingConfiguration, websiteConfiguration, notificationConfiguration,
            accelerateConfiguration, analyticsConfiguration, inventoryConfiguration,
            metricsConfiguration, intelligentTieringConfiguration, encryptionConfiguration,
            lifecycleConfiguration, replicationConfiguration, payment,
            ownershipControls, publicAccessBlockConfiguration, corsConfiguration, bucketPolicy,
            abacConfiguration, metadataConfiguration, metadataTableConfiguration, objectLockConfiguration, inventoryTableConfiguration, journalTableConfiguration);
    }

    public BucketConfig withOwnershipControls(BucketOwnershipControls ownership) {
        return new BucketConfig(loggingConfiguration, websiteConfiguration, notificationConfiguration,
            accelerateConfiguration, analyticsConfiguration, inventoryConfiguration,
            metricsConfiguration, intelligentTieringConfiguration, encryptionConfiguration,
            lifecycleConfiguration, replicationConfiguration, requestPaymentConfiguration,
            ownership, publicAccessBlockConfiguration, corsConfiguration, bucketPolicy,
            abacConfiguration, metadataConfiguration, metadataTableConfiguration, objectLockConfiguration, inventoryTableConfiguration, journalTableConfiguration);
    }

    public BucketConfig withPublicAccessBlock(PublicAccessBlockConfiguration publicAccessBlock) {
        return new BucketConfig(loggingConfiguration, websiteConfiguration, notificationConfiguration,
            accelerateConfiguration, analyticsConfiguration, inventoryConfiguration,
            metricsConfiguration, intelligentTieringConfiguration, encryptionConfiguration,
            lifecycleConfiguration, replicationConfiguration, requestPaymentConfiguration,
            ownershipControls, publicAccessBlock, corsConfiguration, bucketPolicy,
            abacConfiguration, metadataConfiguration, metadataTableConfiguration, objectLockConfiguration, inventoryTableConfiguration, journalTableConfiguration);
    }

    public BucketConfig withCorsConfiguration(CorsConfiguration cors) {
        Objects.requireNonNull(cors);
        return new BucketConfig(loggingConfiguration, websiteConfiguration, notificationConfiguration,
            accelerateConfiguration, analyticsConfiguration, inventoryConfiguration,
            metricsConfiguration, intelligentTieringConfiguration, encryptionConfiguration,
            lifecycleConfiguration, replicationConfiguration, requestPaymentConfiguration,
            ownershipControls, publicAccessBlockConfiguration, cors, bucketPolicy,
            abacConfiguration, metadataConfiguration, metadataTableConfiguration, objectLockConfiguration, inventoryTableConfiguration, journalTableConfiguration);
    }

    public BucketConfig withBucketPolicy(String policy) {
        Objects.requireNonNull(policy);
        return new BucketConfig(loggingConfiguration, websiteConfiguration, notificationConfiguration,
            accelerateConfiguration, analyticsConfiguration, inventoryConfiguration,
            metricsConfiguration, intelligentTieringConfiguration, encryptionConfiguration,
            lifecycleConfiguration, replicationConfiguration, requestPaymentConfiguration,
            ownershipControls, publicAccessBlockConfiguration, corsConfiguration, policy,
            abacConfiguration, metadataConfiguration, metadataTableConfiguration, objectLockConfiguration, inventoryTableConfiguration, journalTableConfiguration);
    }

    public BucketConfig withAbacConfiguration(AbacConfiguration abac) {
        Objects.requireNonNull(abac);
        return new BucketConfig(loggingConfiguration, websiteConfiguration, notificationConfiguration,
            accelerateConfiguration, analyticsConfiguration, inventoryConfiguration,
            metricsConfiguration, intelligentTieringConfiguration, encryptionConfiguration,
            lifecycleConfiguration, replicationConfiguration, requestPaymentConfiguration,
            ownershipControls, publicAccessBlockConfiguration, corsConfiguration, bucketPolicy,
            abac, metadataConfiguration, metadataTableConfiguration, objectLockConfiguration, inventoryTableConfiguration, journalTableConfiguration);
    }

    public BucketConfig withMetadataConfiguration(BucketMetadataConfiguration metadata) {
        Objects.requireNonNull(metadata);
        return new BucketConfig(loggingConfiguration, websiteConfiguration, notificationConfiguration,
            accelerateConfiguration, analyticsConfiguration, inventoryConfiguration,
            metricsConfiguration, intelligentTieringConfiguration, encryptionConfiguration,
            lifecycleConfiguration, replicationConfiguration, requestPaymentConfiguration,
            ownershipControls, publicAccessBlockConfiguration, corsConfiguration, bucketPolicy,
            abacConfiguration, metadata, metadataTableConfiguration, objectLockConfiguration, inventoryTableConfiguration, journalTableConfiguration);
    }

    public BucketConfig withMetadataTableConfiguration(BucketMetadataTableConfiguration metadataTable) {
        Objects.requireNonNull(metadataTable);
        return new BucketConfig(loggingConfiguration, websiteConfiguration, notificationConfiguration,
            accelerateConfiguration, analyticsConfiguration, inventoryConfiguration,
            metricsConfiguration, intelligentTieringConfiguration, encryptionConfiguration,
            lifecycleConfiguration, replicationConfiguration, requestPaymentConfiguration,
            ownershipControls, publicAccessBlockConfiguration, corsConfiguration, bucketPolicy,
            abacConfiguration, metadataConfiguration, metadataTable, objectLockConfiguration, inventoryTableConfiguration, journalTableConfiguration);
    }

    public BucketConfig withObjectLockConfiguration(BucketObjectLockConfiguration objectLock) {
        Objects.requireNonNull(objectLock);
        return new BucketConfig(loggingConfiguration, websiteConfiguration, notificationConfiguration,
            accelerateConfiguration, analyticsConfiguration, inventoryConfiguration,
            metricsConfiguration, intelligentTieringConfiguration, encryptionConfiguration,
            lifecycleConfiguration, replicationConfiguration, requestPaymentConfiguration,
            ownershipControls, publicAccessBlockConfiguration, corsConfiguration, bucketPolicy,
            abacConfiguration, metadataConfiguration, metadataTableConfiguration, objectLock, inventoryTableConfiguration, journalTableConfiguration);
    }

    public BucketConfig withInventoryTableConfiguration(BucketInventoryTableConfiguration inventoryTable) {
        Objects.requireNonNull(inventoryTable);
        return new BucketConfig(loggingConfiguration, websiteConfiguration, notificationConfiguration,
            accelerateConfiguration, analyticsConfiguration, inventoryConfiguration,
            metricsConfiguration, intelligentTieringConfiguration, encryptionConfiguration,
            lifecycleConfiguration, replicationConfiguration, requestPaymentConfiguration,
            ownershipControls, publicAccessBlockConfiguration, corsConfiguration, bucketPolicy,
            abacConfiguration, metadataConfiguration, metadataTableConfiguration, objectLockConfiguration, inventoryTable, journalTableConfiguration);
    }

    public BucketConfig withJournalTableConfiguration(BucketJournalTableConfiguration journalTable) {
        Objects.requireNonNull(journalTable);
        return new BucketConfig(loggingConfiguration, websiteConfiguration, notificationConfiguration,
            accelerateConfiguration, analyticsConfiguration, inventoryConfiguration,
            metricsConfiguration, intelligentTieringConfiguration, encryptionConfiguration,
            lifecycleConfiguration, replicationConfiguration, requestPaymentConfiguration,
            ownershipControls, publicAccessBlockConfiguration, corsConfiguration, bucketPolicy,
            abacConfiguration, metadataConfiguration, metadataTableConfiguration, objectLockConfiguration, inventoryTableConfiguration, journalTable);
    }

    /**
     * Returns EMPTY (all nulls) — useful for clearing all config.
     */
    public BucketConfig clear() {
        return EMPTY;
    }
}
