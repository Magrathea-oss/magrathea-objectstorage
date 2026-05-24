package com.example.magrathea.objectstorage.domain.repository;

import com.example.magrathea.objectstorage.domain.aggregate.Bucket;
import com.example.magrathea.objectstorage.domain.valueobject.BucketAccelerateConfiguration;
import com.example.magrathea.objectstorage.domain.valueobject.BucketConfiguration;
import com.example.magrathea.objectstorage.domain.valueobject.BucketEncryptionConfiguration;
import com.example.magrathea.objectstorage.domain.valueobject.BucketLifecycleConfiguration;
import com.example.magrathea.objectstorage.domain.valueobject.BucketLoggingConfiguration;
import com.example.magrathea.objectstorage.domain.valueobject.BucketNotificationConfiguration;
import com.example.magrathea.objectstorage.domain.valueobject.BucketOwnershipControls;
import com.example.magrathea.objectstorage.domain.valueobject.BucketPolicy;
import com.example.magrathea.objectstorage.domain.valueobject.BucketReplicationConfiguration;
import com.example.magrathea.objectstorage.domain.valueobject.BucketRequestPaymentConfiguration;
import com.example.magrathea.objectstorage.domain.valueobject.BucketWebsiteConfiguration;
import com.example.magrathea.objectstorage.domain.valueobject.PublicAccessBlockConfiguration;
import com.example.magrathea.objectstorage.domain.valueobject.BucketAnalyticsConfiguration;
import com.example.magrathea.objectstorage.domain.valueobject.BucketInventoryConfiguration;
import com.example.magrathea.objectstorage.domain.valueobject.BucketMetricsConfiguration;
import com.example.magrathea.objectstorage.domain.valueobject.BucketIntelligentTieringConfiguration;

import java.util.List;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;

/**
 * Repository interface for Bucket aggregate.
 * Defined in domain — NO implementation here.
 * Uses CompletableFuture for async operations (Java SE standard, no framework).
 */
public interface BucketRepository {
    CompletableFuture<Optional<Bucket>> findById(Bucket.Id id);
    CompletableFuture<Optional<Bucket>> findByName(String name);
    CompletableFuture<List<Bucket>> findAll();
    CompletableFuture<Void> save(Bucket bucket);
    CompletableFuture<Void> delete(Bucket.Id id);

    // ── CORS ──
    CompletableFuture<Optional<BucketConfiguration>> findConfiguration(String bucketName);
    CompletableFuture<Void> saveConfiguration(BucketConfiguration configuration);
    CompletableFuture<Void> deleteConfiguration(String bucketName);

    // ── Lifecycle ──
    CompletableFuture<Optional<BucketLifecycleConfiguration>> findLifecycleConfiguration(String bucketName);
    CompletableFuture<Void> saveLifecycleConfiguration(BucketLifecycleConfiguration configuration);
    CompletableFuture<Void> deleteLifecycleConfiguration(String bucketName);

    // ── Policy ──
    CompletableFuture<Optional<BucketPolicy>> findPolicy(String bucketName);
    CompletableFuture<Void> savePolicy(BucketPolicy policy);
    CompletableFuture<Void> deletePolicy(String bucketName);

    // ── Encryption ──
    CompletableFuture<Optional<BucketEncryptionConfiguration>> findEncryptionConfiguration(String bucketName);
    CompletableFuture<Void> saveEncryptionConfiguration(BucketEncryptionConfiguration configuration);
    CompletableFuture<Void> deleteEncryptionConfiguration(String bucketName);

    // ── Logging ──
    CompletableFuture<Optional<BucketLoggingConfiguration>> findLoggingConfiguration(String bucketName);
    CompletableFuture<Void> saveLoggingConfiguration(BucketLoggingConfiguration configuration);
    CompletableFuture<Void> deleteLoggingConfiguration(String bucketName);

    // ── Website ──
    CompletableFuture<Optional<BucketWebsiteConfiguration>> findWebsiteConfiguration(String bucketName);
    CompletableFuture<Void> saveWebsiteConfiguration(BucketWebsiteConfiguration configuration);
    CompletableFuture<Void> deleteWebsiteConfiguration(String bucketName);

    // ── Notification ──
    CompletableFuture<Optional<BucketNotificationConfiguration>> findNotificationConfiguration(String bucketName);
    CompletableFuture<Void> saveNotificationConfiguration(BucketNotificationConfiguration configuration);
    CompletableFuture<Void> deleteNotificationConfiguration(String bucketName);

    // ── Replication ──
    CompletableFuture<Optional<BucketReplicationConfiguration>> findReplicationConfiguration(String bucketName);
    CompletableFuture<Void> saveReplicationConfiguration(BucketReplicationConfiguration configuration);
    CompletableFuture<Void> deleteReplicationConfiguration(String bucketName);

    // ── Request Payment ──
    CompletableFuture<Optional<BucketRequestPaymentConfiguration>> findRequestPaymentConfiguration(String bucketName);
    CompletableFuture<Void> saveRequestPaymentConfiguration(BucketRequestPaymentConfiguration configuration);
    CompletableFuture<Void> deleteRequestPaymentConfiguration(String bucketName);

    // ── Ownership Controls ──
    CompletableFuture<Optional<BucketOwnershipControls>> findOwnershipControls(String bucketName);
    CompletableFuture<Void> saveOwnershipControls(BucketOwnershipControls controls);
    CompletableFuture<Void> deleteOwnershipControls(String bucketName);

    // ── Public Access Block ──
    CompletableFuture<Optional<PublicAccessBlockConfiguration>> findPublicAccessBlockConfiguration(String bucketName);
    CompletableFuture<Void> savePublicAccessBlockConfiguration(PublicAccessBlockConfiguration configuration);
    CompletableFuture<Void> deletePublicAccessBlockConfiguration(String bucketName);

    // ── Accelerate ──
    CompletableFuture<Optional<BucketAccelerateConfiguration>> findAccelerateConfiguration(String bucketName);
    CompletableFuture<Void> saveAccelerateConfiguration(BucketAccelerateConfiguration configuration);
    CompletableFuture<Void> deleteAccelerateConfiguration(String bucketName);

    // ── Analytics ──
    CompletableFuture<Optional<BucketAnalyticsConfiguration>> findAnalyticsConfiguration(String bucketName, String analyticsId);
    CompletableFuture<Void> saveAnalyticsConfiguration(BucketAnalyticsConfiguration configuration);
    CompletableFuture<Void> deleteAnalyticsConfiguration(String bucketName, String analyticsId);
    CompletableFuture<List<BucketAnalyticsConfiguration>> listAnalyticsConfigurations(String bucketName);

    // ── Inventory ──
    CompletableFuture<Optional<BucketInventoryConfiguration>> findInventoryConfiguration(String bucketName, String inventoryId);
    CompletableFuture<Void> saveInventoryConfiguration(BucketInventoryConfiguration configuration);
    CompletableFuture<Void> deleteInventoryConfiguration(String bucketName, String inventoryId);
    CompletableFuture<List<BucketInventoryConfiguration>> listInventoryConfigurations(String bucketName);

    // ── Metrics ──
    CompletableFuture<Optional<BucketMetricsConfiguration>> findMetricsConfiguration(String bucketName);
    CompletableFuture<Void> saveMetricsConfiguration(BucketMetricsConfiguration configuration);
    CompletableFuture<Void> deleteMetricsConfiguration(String bucketName);

    // ── Intelligent-Tiering ──
    CompletableFuture<Optional<BucketIntelligentTieringConfiguration>> findIntelligentTieringConfiguration(String bucketName);
    CompletableFuture<Void> saveIntelligentTieringConfiguration(BucketIntelligentTieringConfiguration configuration);
    CompletableFuture<Void> deleteIntelligentTieringConfiguration(String bucketName);
}
