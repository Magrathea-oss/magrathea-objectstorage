package com.example.magrathea.objectstore.domain;

import com.example.magrathea.objectstore.domain.aggregate.Bucket;
import com.example.magrathea.objectstore.domain.event.ObjectStoreEvent;
import com.example.magrathea.objectstore.domain.valueobject.BucketAccelerateConfiguration;
import com.example.magrathea.objectstore.domain.valueobject.BucketAnalyticsConfiguration;
import com.example.magrathea.objectstore.domain.valueobject.BucketConfig;
import com.example.magrathea.objectstore.domain.valueobject.BucketEncryptionConfiguration;
import com.example.magrathea.objectstore.domain.valueobject.BucketIntelligentTieringConfiguration;
import com.example.magrathea.objectstore.domain.valueobject.BucketInventoryConfiguration;
import com.example.magrathea.objectstore.domain.valueobject.BucketLifecycleConfiguration;
import com.example.magrathea.objectstore.domain.valueobject.BucketLoggingConfiguration;
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

import java.util.List;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Pure JUnit test for Bucket aggregate — NO Spring context.
 */
class BucketTest {

    // ── Basic creation ──

    @Test
    void create_validBucket() {
        var id = Bucket.Id.generate();
        var bucket = Bucket.create(id, "my-bucket", Region.US_EAST_1, StorageClass.STANDARD);
        assertEquals("my-bucket", bucket.name());
        assertEquals(Region.US_EAST_1, bucket.region());
        assertEquals(StorageClass.STANDARD, bucket.storageClass());
        assertFalse(bucket.versioningEnabled());
        assertFalse(bucket.encryptionEnabled());
        assertEquals(BucketConfig.EMPTY, bucket.bucketConfig());
    }

    @Test
    void create_bucketNameTooShort_throws() {
        var id = Bucket.Id.generate();
        assertThrows(IllegalArgumentException.class,
            () -> Bucket.create(id, "ab", Region.US_EAST_1, StorageClass.STANDARD));
    }

    @Test
    void create_bucketNameTooLong_throws() {
        var id = Bucket.Id.generate();
        var longName = "a" + "b".repeat(63);
        assertThrows(IllegalArgumentException.class,
            () -> Bucket.create(id, longName, Region.US_EAST_1, StorageClass.STANDARD));
    }

    @Test
    void create_bucketNameBlank_throws() {
        var id = Bucket.Id.generate();
        assertThrows(IllegalArgumentException.class,
            () -> Bucket.create(id, "   ", Region.US_EAST_1, StorageClass.STANDARD));
    }

    @Test
    void create_bucketNameWithUnderscore_throws() {
        var id = Bucket.Id.generate();
        assertThrows(IllegalArgumentException.class,
            () -> Bucket.create(id, "my_bucket", Region.US_EAST_1, StorageClass.STANDARD));
    }

    @Test
    void create_bucketNameUppercase_throws() {
        var id = Bucket.Id.generate();
        assertThrows(IllegalArgumentException.class,
            () -> Bucket.create(id, "MyBucket", Region.US_EAST_1, StorageClass.STANDARD));
    }

    // ── Versioning ──

    @Test
    void withVersioningEnabled_returnsNewInstance() {
        var id = Bucket.Id.generate();
        var bucket = Bucket.create(id, "my-bucket", Region.US_EAST_1, StorageClass.STANDARD);
        var versioned = bucket.withVersioningEnabled();
        assertTrue(versioned.versioningEnabled());
        assertNotEquals(bucket, versioned);
    }

    // ── Encryption ──

    @Test
    void withEncryptionEnabled_returnsNewInstance() {
        var id = Bucket.Id.generate();
        var bucket = Bucket.create(id, "my-bucket", Region.US_EAST_1, StorageClass.STANDARD);
        var encrypted = bucket.withEncryptionEnabled();
        assertTrue(encrypted.encryptionEnabled());
        assertNotEquals(bucket, encrypted);
    }

    // ── BucketConfig ──

    @Test
    void create_withBucketConfig() {
        var id = Bucket.Id.generate();
        var corsConfig = new CorsConfiguration(List.of(
            new CorsConfiguration.CorsRule(List.of("*"), List.of("GET"), null, 3600, null, null)));
        var bucketConfig = BucketConfig.EMPTY.withCorsConfiguration(corsConfig);
        var bucket = Bucket.create(id, "my-bucket", Region.US_EAST_1, StorageClass.STANDARD, bucketConfig);
        assertEquals("my-bucket", bucket.name());
        assertNotNull(bucket.bucketConfig());
        assertTrue(bucket.bucketConfig().getCorsConfiguration().isPresent());
    }

    @Test
    void create_withoutBucketConfig_usesEMPTY() {
        var id = Bucket.Id.generate();
        var bucket = Bucket.create(id, "my-bucket", Region.US_EAST_1, StorageClass.STANDARD);
        assertEquals(BucketConfig.EMPTY, bucket.bucketConfig());
    }

    @Test
    void withBucketConfig_returnsNewInstance() {
        var id = Bucket.Id.generate();
        var bucket = Bucket.create(id, "my-bucket", Region.US_EAST_1, StorageClass.STANDARD);
        assertEquals(BucketConfig.EMPTY, bucket.bucketConfig());

        var corsConfig = new CorsConfiguration(List.of(
            new CorsConfiguration.CorsRule(List.of("*"), List.of("GET"), null, 0, null, null)));
        var config = BucketConfig.EMPTY.withCorsConfiguration(corsConfig);
        var configured = bucket.withBucketConfig(config);

        assertNotNull(configured.bucketConfig());
        assertTrue(configured.bucketConfig().getCorsConfiguration().isPresent());
        assertNotEquals(bucket, configured);
        // Original unchanged
        assertEquals(BucketConfig.EMPTY, bucket.bucketConfig());
    }

    @Test
    void withBucketConfig_recordsEvent() {
        var id = Bucket.Id.generate();
        var bucket = Bucket.create(id, "my-bucket", Region.US_EAST_1, StorageClass.STANDARD);
        var config = BucketConfig.EMPTY.withCorsConfiguration(new CorsConfiguration(List.of(
            new CorsConfiguration.CorsRule(List.of("*"), List.of("GET"), null, 0, null, null))));
        var configured = bucket.withBucketConfig(config);
        var events = configured.domainEvents();
        assertEquals(2, events.size()); // BucketCreated + BucketConfigChanged
        var changeEvent = events.get(1);
        assertInstanceOf(ObjectStoreEvent.BucketConfigChanged.class, changeEvent);
        var cast = (ObjectStoreEvent.BucketConfigChanged) changeEvent;
        assertEquals(id, cast.id());
        assertTrue(cast.config().getCorsConfiguration().isPresent());
    }

    @Test
    void withBucketConfig_null_throws() {
        var id = Bucket.Id.generate();
        var bucket = Bucket.create(id, "my-bucket", Region.US_EAST_1, StorageClass.STANDARD);
        assertThrows(NullPointerException.class, () -> bucket.withBucketConfig(null));
    }

    @Test
    void bucketConfig_preservedAcrossTransitions() {
        var id = Bucket.Id.generate();
        var corsConfig = new CorsConfiguration(List.of(
            new CorsConfiguration.CorsRule(List.of("*"), List.of("GET"), null, 0, null, null)));
        var config = BucketConfig.EMPTY.withCorsConfiguration(corsConfig);
        var bucket = Bucket.create(id, "my-bucket", Region.US_EAST_1, StorageClass.STANDARD, config);
        var versioned = bucket.withVersioningEnabled();
        assertTrue(versioned.versioningEnabled());
        assertNotNull(versioned.bucketConfig());
        assertTrue(versioned.bucketConfig().getCorsConfiguration().isPresent());
    }

    @Test
    void restore_withBucketConfig() {
        var id = Bucket.Id.generate();
        var corsConfig = new CorsConfiguration(List.of(
            new CorsConfiguration.CorsRule(List.of("*"), List.of("GET"), null, 0, null, null)));
        var bucketConfig = BucketConfig.EMPTY.withCorsConfiguration(corsConfig);
        var bucket = Bucket.restore(id, "my-bucket", Region.US_EAST_1, StorageClass.STANDARD,
            true, true, bucketConfig);
        assertTrue(bucket.versioningEnabled());
        assertTrue(bucket.encryptionEnabled());
        assertNotNull(bucket.bucketConfig());
        assertTrue(bucket.bucketConfig().getCorsConfiguration().isPresent());
        assertTrue(bucket.domainEvents().isEmpty());
    }

    @Test
    void restore_withoutBucketConfig() {
        var id = Bucket.Id.generate();
        var bucket = Bucket.restore(id, "my-bucket", Region.US_EAST_1, StorageClass.STANDARD,
            false, false);
        assertEquals(BucketConfig.EMPTY, bucket.bucketConfig());
    }

    @Test
    void clearEvents_preservesBucketConfig() {
        var id = Bucket.Id.generate();
        var corsConfig = new CorsConfiguration(List.of(
            new CorsConfiguration.CorsRule(List.of("*"), List.of("GET"), null, 0, null, null)));
        var config = BucketConfig.EMPTY.withCorsConfiguration(corsConfig);
        var bucket = Bucket.create(id, "my-bucket", Region.US_EAST_1, StorageClass.STANDARD, config);
        var cleared = bucket.clearEvents();
        assertTrue(cleared.domainEvents().isEmpty());
        assertNotNull(cleared.bucketConfig());
        assertTrue(cleared.bucketConfig().getCorsConfiguration().isPresent());
    }

    // ── Id ──

    @Test
    void id_generatesUniqueValues() {
        var id1 = Bucket.Id.generate();
        var id2 = Bucket.Id.generate();
        assertNotNull(id1.value());
        assertNotNull(id2.value());
        assertNotEquals(id1.value(), id2.value());
    }

    @Test
    void id_of_returnsCorrectId() {
        var id = Bucket.Id.of("test-id");
        assertEquals("test-id", id.value());
    }

    @Test
    void id_null_throws() {
        assertThrows(NullPointerException.class, () -> new Bucket.Id(null));
    }

    // ── Versioning transitions preserve bucketConfig ──

    @Test
    void versioningTransitionsPreserveBucketConfig() {
        var id = Bucket.Id.generate();
        var bucket = Bucket.create(id, "my-bucket", Region.US_EAST_1, StorageClass.STANDARD);

        var corsConfig = new CorsConfiguration(List.of(
            new CorsConfiguration.CorsRule(List.of("*"), List.of("GET"), null, 0, null, null)));
        var config = BucketConfig.EMPTY.withCorsConfiguration(corsConfig);
        var withConfig = bucket.withBucketConfig(config);

        var versioned = withConfig.withVersioningEnabled();
        assertTrue(versioned.bucketConfig().getCorsConfiguration().isPresent());
        assertTrue(versioned.versioningEnabled());

        var suspended = versioned.withVersioningSuspended();
        assertTrue(suspended.bucketConfig().getCorsConfiguration().isPresent());
        assertFalse(suspended.versioningEnabled());
    }

    // ── Encryption transition preserves bucketConfig ──

    @Test
    void encryptionTransitionPreservesBucketConfig() {
        var id = Bucket.Id.generate();
        var bucket = Bucket.create(id, "my-bucket", Region.US_EAST_1, StorageClass.STANDARD);

        var corsConfig = new CorsConfiguration(List.of(
            new CorsConfiguration.CorsRule(List.of("*"), List.of("GET"), null, 0, null, null)));
        var config = BucketConfig.EMPTY.withCorsConfiguration(corsConfig);
        var withConfig = bucket.withBucketConfig(config);

        var encrypted = withConfig.withEncryptionEnabled();
        assertTrue(encrypted.bucketConfig().getCorsConfiguration().isPresent());
        assertTrue(encrypted.encryptionEnabled());
    }

    // ── Per-config-type with* methods ──

    @Test
    void withCorsConfiguration_setsConfigAndRecordsEvent() {
        var id = Bucket.Id.generate();
        var bucket = Bucket.create(id, "my-bucket", Region.US_EAST_1, StorageClass.STANDARD);
        var cors = new CorsConfiguration(List.of(
            new CorsConfiguration.CorsRule(List.of("*"), List.of("GET", "PUT"), null, 3600, null, null)));
        var updated = bucket.withCorsConfiguration(cors);
        assertTrue(updated.bucketConfig().getCorsConfiguration().isPresent());
        var events = updated.domainEvents();
        assertEquals(2, events.size());
        assertInstanceOf(ObjectStoreEvent.BucketCorsConfigured.class, events.get(1));
    }

    @Test
    void withCorsConfigurationDeleted_clearsConfigAndRecordsEvent() {
        var id = Bucket.Id.generate();
        var cors = new CorsConfiguration(List.of(
            new CorsConfiguration.CorsRule(List.of("*"), List.of("GET"), null, 3600, null, null)));
        var config = BucketConfig.EMPTY.withCorsConfiguration(cors);
        var bucket = Bucket.create(id, "my-bucket", Region.US_EAST_1, StorageClass.STANDARD, config);
        var updated = bucket.withCorsConfigurationDeleted();
        assertTrue(updated.bucketConfig().getCorsConfiguration().isEmpty());
        var events = updated.domainEvents();
        assertEquals(2, events.size());
        assertInstanceOf(ObjectStoreEvent.BucketCorsDeleted.class, events.get(1));
    }

    @Test
    void withLifecycleConfiguration_setsConfigAndRecordsEvent() {
        var id = Bucket.Id.generate();
        var bucket = Bucket.create(id, "my-bucket", Region.US_EAST_1, StorageClass.STANDARD);
        var lifecycle = new BucketLifecycleConfiguration("my-bucket", List.of(
            new BucketLifecycleConfiguration.LifecycleRule("rule1", "Enabled", null, null, null, null)));
        var updated = bucket.withLifecycleConfiguration(lifecycle);
        assertTrue(updated.bucketConfig().getLifecycleConfiguration().isPresent());
        assertInstanceOf(ObjectStoreEvent.BucketLifecycleConfigured.class, updated.domainEvents().get(1));
    }

    @Test
    void withLifecycleConfigurationDeleted_clearsConfig() {
        var id = Bucket.Id.generate();
        var lifecycle = new BucketLifecycleConfiguration("my-bucket", List.of(
            new BucketLifecycleConfiguration.LifecycleRule("rule1", "Enabled", null, null, null, null)));
        var config = BucketConfig.EMPTY.withLifecycleConfiguration(lifecycle);
        var bucket = Bucket.create(id, "my-bucket", Region.US_EAST_1, StorageClass.STANDARD, config);
        var updated = bucket.withLifecycleConfigurationDeleted();
        assertTrue(updated.bucketConfig().getLifecycleConfiguration().isEmpty());
        assertInstanceOf(ObjectStoreEvent.BucketLifecycleDeleted.class, updated.domainEvents().get(1));
    }

    @Test
    void withLoggingConfiguration_setsConfigAndRecordsEvent() {
        var id = Bucket.Id.generate();
        var bucket = Bucket.create(id, "my-bucket", Region.US_EAST_1, StorageClass.STANDARD);
        var logging = new BucketLoggingConfiguration("my-bucket", "target-bucket", "prefix");
        var updated = bucket.withLoggingConfiguration(logging);
        assertTrue(updated.bucketConfig().getLoggingConfiguration().isPresent());
        assertInstanceOf(ObjectStoreEvent.BucketLoggingConfigured.class, updated.domainEvents().get(1));
    }

    @Test
    void withLoggingConfigurationDeleted_clearsConfig() {
        var id = Bucket.Id.generate();
        var logging = new BucketLoggingConfiguration("my-bucket", "target-bucket", "prefix");
        var config = BucketConfig.EMPTY.withLoggingConfiguration(logging);
        var bucket = Bucket.create(id, "my-bucket", Region.US_EAST_1, StorageClass.STANDARD, config);
        var updated = bucket.withLoggingConfigurationDeleted();
        assertTrue(updated.bucketConfig().getLoggingConfiguration().isEmpty());
        assertInstanceOf(ObjectStoreEvent.BucketLoggingDeleted.class, updated.domainEvents().get(1));
    }

    @Test
    void withNotificationConfiguration_setsConfigAndRecordsEvent() {
        var id = Bucket.Id.generate();
        var bucket = Bucket.create(id, "my-bucket", Region.US_EAST_1, StorageClass.STANDARD);
        var notification = new BucketNotificationConfiguration("my-bucket", List.of(
            new BucketNotificationConfiguration.NotificationEvent("s3:ObjectCreated:*", null, null, null, null)));
        var updated = bucket.withNotificationConfiguration(notification);
        assertTrue(updated.bucketConfig().getNotificationConfiguration().isPresent());
        assertInstanceOf(ObjectStoreEvent.BucketNotificationConfigured.class, updated.domainEvents().get(1));
    }

    @Test
    void withNotificationConfigurationDeleted_clearsConfig() {
        var id = Bucket.Id.generate();
        var notification = new BucketNotificationConfiguration("my-bucket", List.of(
            new BucketNotificationConfiguration.NotificationEvent("s3:ObjectCreated:*", null, null, null, null)));
        var config = BucketConfig.EMPTY.withNotificationConfiguration(notification);
        var bucket = Bucket.create(id, "my-bucket", Region.US_EAST_1, StorageClass.STANDARD, config);
        var updated = bucket.withNotificationConfigurationDeleted();
        assertTrue(updated.bucketConfig().getNotificationConfiguration().isEmpty());
        assertInstanceOf(ObjectStoreEvent.BucketNotificationDeleted.class, updated.domainEvents().get(1));
    }

    @Test
    void withWebsiteConfiguration_setsConfigAndRecordsEvent() {
        var id = Bucket.Id.generate();
        var bucket = Bucket.create(id, "my-bucket", Region.US_EAST_1, StorageClass.STANDARD);
        var website = new BucketWebsiteConfiguration("my-bucket", "index.html", null, null, null, null);
        var updated = bucket.withWebsiteConfiguration(website);
        assertTrue(updated.bucketConfig().getWebsiteConfiguration().isPresent());
        assertInstanceOf(ObjectStoreEvent.BucketWebsiteConfigured.class, updated.domainEvents().get(1));
    }

    @Test
    void withWebsiteConfigurationDeleted_clearsConfig() {
        var id = Bucket.Id.generate();
        var website = new BucketWebsiteConfiguration("my-bucket", "index.html", null, null, null, null);
        var config = BucketConfig.EMPTY.withWebsiteConfiguration(website);
        var bucket = Bucket.create(id, "my-bucket", Region.US_EAST_1, StorageClass.STANDARD, config);
        var updated = bucket.withWebsiteConfigurationDeleted();
        assertTrue(updated.bucketConfig().getWebsiteConfiguration().isEmpty());
        assertInstanceOf(ObjectStoreEvent.BucketWebsiteDeleted.class, updated.domainEvents().get(1));
    }

    @Test
    void withBucketPolicy_setsConfigAndRecordsEvent() {
        var id = Bucket.Id.generate();
        var bucket = Bucket.create(id, "my-bucket", Region.US_EAST_1, StorageClass.STANDARD);
        var policy = "{\"Version\":\"2012-10-17\"}";
        var updated = bucket.withBucketPolicy(policy);
        assertTrue(updated.bucketConfig().getBucketPolicy().isPresent());
        assertEquals(policy, updated.bucketConfig().getBucketPolicy().get());
        assertInstanceOf(ObjectStoreEvent.BucketPolicyConfigured.class, updated.domainEvents().get(1));
    }

    @Test
    void withBucketPolicyDeleted_clearsConfig() {
        var id = Bucket.Id.generate();
        var config = BucketConfig.EMPTY.withBucketPolicy("{\"Version\":\"2012-10-17\"}");
        var bucket = Bucket.create(id, "my-bucket", Region.US_EAST_1, StorageClass.STANDARD, config);
        var updated = bucket.withBucketPolicyDeleted();
        assertTrue(updated.bucketConfig().getBucketPolicy().isEmpty());
        assertInstanceOf(ObjectStoreEvent.BucketPolicyDeleted.class, updated.domainEvents().get(1));
    }

    @Test
    void withEncryptionConfiguration_setsConfigAndRecordsEvent() {
        var id = Bucket.Id.generate();
        var bucket = Bucket.create(id, "my-bucket", Region.US_EAST_1, StorageClass.STANDARD);
        var encryption = new BucketEncryptionConfiguration("my-bucket", null, "AES256", null);
        var updated = bucket.withEncryptionConfiguration(encryption);
        assertTrue(updated.bucketConfig().getEncryptionConfiguration().isPresent());
        assertInstanceOf(ObjectStoreEvent.BucketEncryptionConfigured.class, updated.domainEvents().get(1));
    }

    @Test
    void withEncryptionConfigurationDeleted_clearsConfig() {
        var id = Bucket.Id.generate();
        var encryption = new BucketEncryptionConfiguration("my-bucket", null, "AES256", null);
        var config = BucketConfig.EMPTY.withEncryptionConfiguration(encryption);
        var bucket = Bucket.create(id, "my-bucket", Region.US_EAST_1, StorageClass.STANDARD, config);
        var updated = bucket.withEncryptionConfigurationDeleted();
        assertTrue(updated.bucketConfig().getEncryptionConfiguration().isEmpty());
        assertInstanceOf(ObjectStoreEvent.BucketEncryptionDeleted.class, updated.domainEvents().get(1));
    }

    @Test
    void withReplicationConfiguration_setsConfigAndRecordsEvent() {
        var id = Bucket.Id.generate();
        var bucket = Bucket.create(id, "my-bucket", Region.US_EAST_1, StorageClass.STANDARD);
        var replication = new BucketReplicationConfiguration("my-bucket", "arn:aws:iam::role", List.of(
            new BucketReplicationConfiguration.ReplicationRule("r1", "Enabled", "/", "arn:aws:s3:::dest", null, false, false)));
        var updated = bucket.withReplicationConfiguration(replication);
        assertTrue(updated.bucketConfig().getReplicationConfiguration().isPresent());
        assertInstanceOf(ObjectStoreEvent.BucketReplicationConfigured.class, updated.domainEvents().get(1));
    }

    @Test
    void withReplicationConfigurationDeleted_clearsConfig() {
        var id = Bucket.Id.generate();
        var replication = new BucketReplicationConfiguration("my-bucket", "arn:aws:iam::role", List.of(
            new BucketReplicationConfiguration.ReplicationRule("r1", "Enabled", "/", "arn:aws:s3:::dest", null, false, false)));
        var config = BucketConfig.EMPTY.withReplicationConfiguration(replication);
        var bucket = Bucket.create(id, "my-bucket", Region.US_EAST_1, StorageClass.STANDARD, config);
        var updated = bucket.withReplicationConfigurationDeleted();
        assertTrue(updated.bucketConfig().getReplicationConfiguration().isEmpty());
        assertInstanceOf(ObjectStoreEvent.BucketReplicationDeleted.class, updated.domainEvents().get(1));
    }

    @Test
    void withRequestPaymentConfiguration_setsConfigAndRecordsEvent() {
        var id = Bucket.Id.generate();
        var bucket = Bucket.create(id, "my-bucket", Region.US_EAST_1, StorageClass.STANDARD);
        var payment = new BucketRequestPaymentConfiguration("my-bucket", "Requester");
        var updated = bucket.withRequestPaymentConfiguration(payment);
        assertTrue(updated.bucketConfig().getRequestPaymentConfiguration().isPresent());
        assertInstanceOf(ObjectStoreEvent.BucketRequestPaymentConfigured.class, updated.domainEvents().get(1));
    }

    @Test
    void withRequestPaymentConfigurationDeleted_clearsConfig() {
        var id = Bucket.Id.generate();
        var payment = new BucketRequestPaymentConfiguration("my-bucket", "Requester");
        var config = BucketConfig.EMPTY.withRequestPaymentConfiguration(payment);
        var bucket = Bucket.create(id, "my-bucket", Region.US_EAST_1, StorageClass.STANDARD, config);
        var updated = bucket.withRequestPaymentConfigurationDeleted();
        assertTrue(updated.bucketConfig().getRequestPaymentConfiguration().isEmpty());
        assertInstanceOf(ObjectStoreEvent.BucketRequestPaymentDeleted.class, updated.domainEvents().get(1));
    }

    @Test
    void withOwnershipControls_setsConfigAndRecordsEvent() {
        var id = Bucket.Id.generate();
        var bucket = Bucket.create(id, "my-bucket", Region.US_EAST_1, StorageClass.STANDARD);
        var ownership = new BucketOwnershipControls("my-bucket", null, "BucketOwnerPreferred");
        var updated = bucket.withOwnershipControls(ownership);
        assertTrue(updated.bucketConfig().getOwnershipControls().isPresent());
        assertInstanceOf(ObjectStoreEvent.BucketOwnershipControlsConfigured.class, updated.domainEvents().get(1));
    }

    @Test
    void withOwnershipControlsDeleted_clearsConfig() {
        var id = Bucket.Id.generate();
        var ownership = new BucketOwnershipControls("my-bucket", null, "BucketOwnerPreferred");
        var config = BucketConfig.EMPTY.withOwnershipControls(ownership);
        var bucket = Bucket.create(id, "my-bucket", Region.US_EAST_1, StorageClass.STANDARD, config);
        var updated = bucket.withOwnershipControlsDeleted();
        assertTrue(updated.bucketConfig().getOwnershipControls().isEmpty());
        assertInstanceOf(ObjectStoreEvent.BucketOwnershipControlsDeleted.class, updated.domainEvents().get(1));
    }

    @Test
    void withPublicAccessBlockConfiguration_setsConfigAndRecordsEvent() {
        var id = Bucket.Id.generate();
        var bucket = Bucket.create(id, "my-bucket", Region.US_EAST_1, StorageClass.STANDARD);
        var pab = new PublicAccessBlockConfiguration("my-bucket", true, true, false, true);
        var updated = bucket.withPublicAccessBlockConfiguration(pab);
        assertTrue(updated.bucketConfig().getPublicAccessBlockConfiguration().isPresent());
        assertInstanceOf(ObjectStoreEvent.BucketPublicAccessBlockConfigured.class, updated.domainEvents().get(1));
    }

    @Test
    void withPublicAccessBlockConfigurationDeleted_clearsConfig() {
        var id = Bucket.Id.generate();
        var pab = new PublicAccessBlockConfiguration("my-bucket", true, true, false, true);
        var config = BucketConfig.EMPTY.withPublicAccessBlock(pab);
        var bucket = Bucket.create(id, "my-bucket", Region.US_EAST_1, StorageClass.STANDARD, config);
        var updated = bucket.withPublicAccessBlockConfigurationDeleted();
        assertTrue(updated.bucketConfig().getPublicAccessBlockConfiguration().isEmpty());
        assertInstanceOf(ObjectStoreEvent.BucketPublicAccessBlockDeleted.class, updated.domainEvents().get(1));
    }

    @Test
    void withAccelerateConfiguration_setsConfigAndRecordsEvent() {
        var id = Bucket.Id.generate();
        var bucket = Bucket.create(id, "my-bucket", Region.US_EAST_1, StorageClass.STANDARD);
        var accelerate = new BucketAccelerateConfiguration("my-bucket", "Enabled");
        var updated = bucket.withAccelerateConfiguration(accelerate);
        assertTrue(updated.bucketConfig().getAccelerateConfiguration().isPresent());
        assertInstanceOf(ObjectStoreEvent.BucketAccelerateConfigured.class, updated.domainEvents().get(1));
    }

    @Test
    void withAccelerateConfigurationDeleted_clearsConfig() {
        var id = Bucket.Id.generate();
        var accelerate = new BucketAccelerateConfiguration("my-bucket", "Enabled");
        var config = BucketConfig.EMPTY.withAccelerateConfiguration(accelerate);
        var bucket = Bucket.create(id, "my-bucket", Region.US_EAST_1, StorageClass.STANDARD, config);
        var updated = bucket.withAccelerateConfigurationDeleted();
        assertTrue(updated.bucketConfig().getAccelerateConfiguration().isEmpty());
        assertInstanceOf(ObjectStoreEvent.BucketAccelerateDeleted.class, updated.domainEvents().get(1));
    }

    @Test
    void withAnalyticsConfiguration_setsConfigAndRecordsEvent() {
        var id = Bucket.Id.generate();
        var bucket = Bucket.create(id, "my-bucket", Region.US_EAST_1, StorageClass.STANDARD);
        var analytics = new BucketAnalyticsConfiguration("my-bucket", "analytics-id", null);
        var updated = bucket.withAnalyticsConfiguration(analytics);
        assertTrue(updated.bucketConfig().getAnalyticsConfiguration().isPresent());
        assertInstanceOf(ObjectStoreEvent.BucketAnalyticsConfigured.class, updated.domainEvents().get(1));
    }

    @Test
    void withAnalyticsConfigurationDeleted_clearsConfig() {
        var id = Bucket.Id.generate();
        var analytics = new BucketAnalyticsConfiguration("my-bucket", "analytics-id", null);
        var config = BucketConfig.EMPTY.withAnalyticsConfiguration(analytics);
        var bucket = Bucket.create(id, "my-bucket", Region.US_EAST_1, StorageClass.STANDARD, config);
        var updated = bucket.withAnalyticsConfigurationDeleted();
        assertTrue(updated.bucketConfig().getAnalyticsConfiguration().isEmpty());
        assertInstanceOf(ObjectStoreEvent.BucketAnalyticsDeleted.class, updated.domainEvents().get(1));
    }

    @Test
    void withInventoryConfiguration_setsConfigAndRecordsEvent() {
        var id = Bucket.Id.generate();
        var bucket = Bucket.create(id, "my-bucket", Region.US_EAST_1, StorageClass.STANDARD);
        var inventory = new BucketInventoryConfiguration("my-bucket", "inv-id", "CSV", "Daily", true);
        var updated = bucket.withInventoryConfiguration(inventory);
        assertTrue(updated.bucketConfig().getInventoryConfiguration().isPresent());
        assertInstanceOf(ObjectStoreEvent.BucketInventoryConfigured.class, updated.domainEvents().get(1));
    }

    @Test
    void withInventoryConfigurationDeleted_clearsConfig() {
        var id = Bucket.Id.generate();
        var inventory = new BucketInventoryConfiguration("my-bucket", "inv-id", "CSV", "Daily", true);
        var config = BucketConfig.EMPTY.withInventoryConfiguration(inventory);
        var bucket = Bucket.create(id, "my-bucket", Region.US_EAST_1, StorageClass.STANDARD, config);
        var updated = bucket.withInventoryConfigurationDeleted();
        assertTrue(updated.bucketConfig().getInventoryConfiguration().isEmpty());
        assertInstanceOf(ObjectStoreEvent.BucketInventoryDeleted.class, updated.domainEvents().get(1));
    }

    @Test
    void withMetricsConfiguration_setsConfigAndRecordsEvent() {
        var id = Bucket.Id.generate();
        var bucket = Bucket.create(id, "my-bucket", Region.US_EAST_1, StorageClass.STANDARD);
        var metrics = new BucketMetricsConfiguration("my-bucket", "metrics-id", null);
        var updated = bucket.withMetricsConfiguration(metrics);
        assertTrue(updated.bucketConfig().getMetricsConfiguration().isPresent());
        assertInstanceOf(ObjectStoreEvent.BucketMetricsConfigured.class, updated.domainEvents().get(1));
    }

    @Test
    void withMetricsConfigurationDeleted_clearsConfig() {
        var id = Bucket.Id.generate();
        var metrics = new BucketMetricsConfiguration("my-bucket", "metrics-id", null);
        var config = BucketConfig.EMPTY.withMetricsConfiguration(metrics);
        var bucket = Bucket.create(id, "my-bucket", Region.US_EAST_1, StorageClass.STANDARD, config);
        var updated = bucket.withMetricsConfigurationDeleted();
        assertTrue(updated.bucketConfig().getMetricsConfiguration().isEmpty());
        assertInstanceOf(ObjectStoreEvent.BucketMetricsDeleted.class, updated.domainEvents().get(1));
    }

    @Test
    void withIntelligentTieringConfiguration_setsConfigAndRecordsEvent() {
        var id = Bucket.Id.generate();
        var bucket = Bucket.create(id, "my-bucket", Region.US_EAST_1, StorageClass.STANDARD);
        var tiering = new BucketIntelligentTieringConfiguration("my-bucket", "tiering-id", null, null);
        var updated = bucket.withIntelligentTieringConfiguration(tiering);
        assertTrue(updated.bucketConfig().getIntelligentTieringConfiguration().isPresent());
        assertInstanceOf(ObjectStoreEvent.BucketIntelligentTieringConfigured.class, updated.domainEvents().get(1));
    }

    @Test
    void withIntelligentTieringConfigurationDeleted_clearsConfig() {
        var id = Bucket.Id.generate();
        var tiering = new BucketIntelligentTieringConfiguration("my-bucket", "tiering-id", null, null);
        var config = BucketConfig.EMPTY.withIntelligentTieringConfiguration(tiering);
        var bucket = Bucket.create(id, "my-bucket", Region.US_EAST_1, StorageClass.STANDARD, config);
        var updated = bucket.withIntelligentTieringConfigurationDeleted();
        assertTrue(updated.bucketConfig().getIntelligentTieringConfiguration().isEmpty());
        assertInstanceOf(ObjectStoreEvent.BucketIntelligentTieringDeleted.class, updated.domainEvents().get(1));
    }
}
