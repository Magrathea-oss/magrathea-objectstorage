package com.example.magrathea.objectstore.domain;

import com.example.magrathea.objectstore.domain.aggregate.Bucket;
import com.example.magrathea.objectstore.domain.aggregate.S3Object;
import com.example.magrathea.objectstore.domain.event.ObjectStoreEvent;
import com.example.magrathea.objectstore.domain.valueobject.BucketAccelerateConfiguration;
import com.example.magrathea.objectstore.domain.valueobject.BucketAnalyticsConfiguration;
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
import com.example.magrathea.objectstore.domain.valueobject.ContentDescriptor;
import com.example.magrathea.objectstore.domain.valueobject.CorsConfiguration;
import com.example.magrathea.objectstore.domain.valueobject.ObjectKey;
import com.example.magrathea.objectstore.domain.valueobject.PublicAccessBlockConfiguration;

import java.util.List;
import org.junit.jupiter.api.Test;

import java.time.Instant;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Pure JUnit test for ObjectStoreEvent sealed interface.
 */
class ObjectStoreEventTest {

    @Test
    void bucketCreated() {
        var id = Bucket.Id.generate();
        var event = new ObjectStoreEvent.BucketCreated(id, "my-bucket", Instant.now());
        assertEquals("my-bucket", event.name());
        assertEquals(id, event.id());
        assertNotNull(event.occurredOn());
    }

    @Test
    void bucketDeleted() {
        var id = Bucket.Id.generate();
        var event = new ObjectStoreEvent.BucketDeleted(id, Instant.now());
        assertEquals(id, event.id());
        assertNotNull(event.occurredOn());
    }

    @Test
    void objectCreated() {
        var bucketId = Bucket.Id.generate();
        var id = S3Object.Id.generate();
        var key = ObjectKey.of("bucket", "test.txt");
        var event = new ObjectStoreEvent.ObjectCreated(id, bucketId, key, Instant.now());
        assertEquals(id, event.id());
        assertEquals(bucketId, event.bucketId());
        assertEquals("bucket", event.key().bucket());
        assertEquals("test.txt", event.key().key());
    }

    @Test
    void objectDeleted() {
        var bucketId = Bucket.Id.generate();
        var id = S3Object.Id.generate();
        var event = new ObjectStoreEvent.ObjectDeleted(id, bucketId, Instant.now());
        assertEquals(id, event.id());
        assertEquals(bucketId, event.bucketId());
    }

    @Test
    void sealedInterface_permitsOnlyKnownTypes() {
        // ObjectStoreEvent permits only BucketCreated, BucketDeleted, ObjectCreated, ObjectDeleted
        assertTrue(ObjectStoreEvent.class.isSealed());
    }

    @Test
    void contentDescriptorCreated() {
        var id = S3Object.Id.generate();
        var descriptor = ContentDescriptor.of(100, "abc123", "content-id-1");
        var event = new ObjectStoreEvent.ContentDescriptorCreated(id, descriptor, Instant.now());
        assertEquals(id, event.id());
        assertEquals(100, event.descriptor().size());
        assertEquals("abc123", event.descriptor().md5Hash());
        assertEquals("content-id-1", event.descriptor().contentId());
        assertNotNull(event.occurredOn());
    }

    @Test
    void patternMatching() {
        var event = (ObjectStoreEvent) new ObjectStoreEvent.BucketCreated(
            Bucket.Id.generate(), "test", Instant.now());
        switch (event) {
            case ObjectStoreEvent.BucketCreated c -> assertEquals("test", c.name());
            default -> fail("unexpected event type");
        }
    }

    // ── Bucket configuration events ──

    @Test
    void bucketCorsConfigured() {
        var id = Bucket.Id.generate();
        var cors = new CorsConfiguration(List.of(
            new CorsConfiguration.CorsRule(List.of("*"), List.of("GET"), null, 3600, null, null)));
        var event = new ObjectStoreEvent.BucketCorsConfigured(id, cors, Instant.now());
        assertEquals(id, event.id());
        assertEquals(1, event.config().corsRules().size());
    }

    @Test
    void bucketCorsDeleted() {
        var id = Bucket.Id.generate();
        var event = new ObjectStoreEvent.BucketCorsDeleted(id, Instant.now());
        assertEquals(id, event.id());
        assertNotNull(event.occurredOn());
    }

    @Test
    void bucketLifecycleConfigured() {
        var id = Bucket.Id.generate();
        var lifecycle = new BucketLifecycleConfiguration("my-bucket", List.of(
            new BucketLifecycleConfiguration.LifecycleRule("rule1", "Enabled", null, null, null, null)));
        var event = new ObjectStoreEvent.BucketLifecycleConfigured(id, lifecycle, Instant.now());
        assertEquals(id, event.id());
        assertTrue(event.config().hasRules());
    }

    @Test
    void bucketLifecycleDeleted() {
        var id = Bucket.Id.generate();
        var event = new ObjectStoreEvent.BucketLifecycleDeleted(id, Instant.now());
        assertEquals(id, event.id());
        assertNotNull(event.occurredOn());
    }

    @Test
    void bucketLoggingConfigured() {
        var id = Bucket.Id.generate();
        var logging = new BucketLoggingConfiguration("my-bucket", "target-bucket", "prefix");
        var event = new ObjectStoreEvent.BucketLoggingConfigured(id, logging, Instant.now());
        assertEquals(id, event.id());
        assertTrue(event.config().hasLogging());
    }

    @Test
    void bucketLoggingDeleted() {
        var id = Bucket.Id.generate();
        var event = new ObjectStoreEvent.BucketLoggingDeleted(id, Instant.now());
        assertEquals(id, event.id());
        assertNotNull(event.occurredOn());
    }

    @Test
    void bucketNotificationConfigured() {
        var id = Bucket.Id.generate();
        var notification = new BucketNotificationConfiguration("my-bucket", List.of(
            new BucketNotificationConfiguration.NotificationEvent("s3:ObjectCreated:*", null, null, null, null)));
        var event = new ObjectStoreEvent.BucketNotificationConfigured(id, notification, Instant.now());
        assertEquals(id, event.id());
        assertTrue(event.config().hasNotification());
    }

    @Test
    void bucketNotificationDeleted() {
        var id = Bucket.Id.generate();
        var event = new ObjectStoreEvent.BucketNotificationDeleted(id, Instant.now());
        assertEquals(id, event.id());
        assertNotNull(event.occurredOn());
    }

    @Test
    void bucketWebsiteConfigured() {
        var id = Bucket.Id.generate();
        var website = new BucketWebsiteConfiguration("my-bucket", "index.html", null, null, null, null);
        var event = new ObjectStoreEvent.BucketWebsiteConfigured(id, website, Instant.now());
        assertEquals(id, event.id());
        assertTrue(event.config().hasWebsite());
    }

    @Test
    void bucketWebsiteDeleted() {
        var id = Bucket.Id.generate();
        var event = new ObjectStoreEvent.BucketWebsiteDeleted(id, Instant.now());
        assertEquals(id, event.id());
        assertNotNull(event.occurredOn());
    }

    @Test
    void bucketPolicyConfigured() {
        var id = Bucket.Id.generate();
        var event = new ObjectStoreEvent.BucketPolicyConfigured(id, "{\"Version\":\"2012-10-17\"}", Instant.now());
        assertEquals(id, event.id());
        assertEquals("{\"Version\":\"2012-10-17\"}", event.policy());
    }

    @Test
    void bucketPolicyDeleted() {
        var id = Bucket.Id.generate();
        var event = new ObjectStoreEvent.BucketPolicyDeleted(id, Instant.now());
        assertEquals(id, event.id());
        assertNotNull(event.occurredOn());
    }

    @Test
    void bucketEncryptionConfigured() {
        var id = Bucket.Id.generate();
        var encryption = new BucketEncryptionConfiguration("my-bucket", null, "AES256", null);
        var event = new ObjectStoreEvent.BucketEncryptionConfigured(id, encryption, Instant.now());
        assertEquals(id, event.id());
        assertTrue(event.config().hasEncryption());
    }

    @Test
    void bucketEncryptionDeleted() {
        var id = Bucket.Id.generate();
        var event = new ObjectStoreEvent.BucketEncryptionDeleted(id, Instant.now());
        assertEquals(id, event.id());
        assertNotNull(event.occurredOn());
    }

    @Test
    void bucketReplicationConfigured() {
        var id = Bucket.Id.generate();
        var replication = new BucketReplicationConfiguration("my-bucket", "arn:aws:iam::role", List.of(
            new BucketReplicationConfiguration.ReplicationRule("r1", "Enabled", "/", "arn:aws:s3:::dest", null, false, false)));
        var event = new ObjectStoreEvent.BucketReplicationConfigured(id, replication, Instant.now());
        assertEquals(id, event.id());
        assertTrue(event.config().hasReplication());
    }

    @Test
    void bucketReplicationDeleted() {
        var id = Bucket.Id.generate();
        var event = new ObjectStoreEvent.BucketReplicationDeleted(id, Instant.now());
        assertEquals(id, event.id());
        assertNotNull(event.occurredOn());
    }

    @Test
    void bucketRequestPaymentConfigured() {
        var id = Bucket.Id.generate();
        var payment = new BucketRequestPaymentConfiguration("my-bucket", "Requester");
        var event = new ObjectStoreEvent.BucketRequestPaymentConfigured(id, payment, Instant.now());
        assertEquals(id, event.id());
        assertEquals("Requester", event.config().payer());
    }

    @Test
    void bucketRequestPaymentDeleted() {
        var id = Bucket.Id.generate();
        var event = new ObjectStoreEvent.BucketRequestPaymentDeleted(id, Instant.now());
        assertEquals(id, event.id());
        assertNotNull(event.occurredOn());
    }

    @Test
    void bucketOwnershipControlsConfigured() {
        var id = Bucket.Id.generate();
        var ownership = new BucketOwnershipControls("my-bucket", null, "BucketOwnerPreferred");
        var event = new ObjectStoreEvent.BucketOwnershipControlsConfigured(id, ownership, Instant.now());
        assertEquals(id, event.id());
        assertEquals("BucketOwnerPreferred", event.config().ownership());
    }

    @Test
    void bucketOwnershipControlsDeleted() {
        var id = Bucket.Id.generate();
        var event = new ObjectStoreEvent.BucketOwnershipControlsDeleted(id, Instant.now());
        assertEquals(id, event.id());
        assertNotNull(event.occurredOn());
    }

    @Test
    void bucketPublicAccessBlockConfigured() {
        var id = Bucket.Id.generate();
        var pab = new PublicAccessBlockConfiguration("my-bucket", true, true, false, true);
        var event = new ObjectStoreEvent.BucketPublicAccessBlockConfigured(id, pab, Instant.now());
        assertEquals(id, event.id());
        assertTrue(event.config().blockPublicAcls());
    }

    @Test
    void bucketPublicAccessBlockDeleted() {
        var id = Bucket.Id.generate();
        var event = new ObjectStoreEvent.BucketPublicAccessBlockDeleted(id, Instant.now());
        assertEquals(id, event.id());
        assertNotNull(event.occurredOn());
    }

    @Test
    void bucketAccelerateConfigured() {
        var id = Bucket.Id.generate();
        var accelerate = new BucketAccelerateConfiguration("my-bucket", "Enabled");
        var event = new ObjectStoreEvent.BucketAccelerateConfigured(id, accelerate, Instant.now());
        assertEquals(id, event.id());
        assertEquals("Enabled", event.config().status());
    }

    @Test
    void bucketAccelerateDeleted() {
        var id = Bucket.Id.generate();
        var event = new ObjectStoreEvent.BucketAccelerateDeleted(id, Instant.now());
        assertEquals(id, event.id());
        assertNotNull(event.occurredOn());
    }

    @Test
    void bucketAnalyticsConfigured() {
        var id = Bucket.Id.generate();
        var analytics = new BucketAnalyticsConfiguration("my-bucket", "analytics-id", null);
        var event = new ObjectStoreEvent.BucketAnalyticsConfigured(id, analytics, Instant.now());
        assertEquals(id, event.id());
        assertEquals("analytics-id", event.config().analyticsId());
    }

    @Test
    void bucketAnalyticsDeleted() {
        var id = Bucket.Id.generate();
        var event = new ObjectStoreEvent.BucketAnalyticsDeleted(id, Instant.now());
        assertEquals(id, event.id());
        assertNotNull(event.occurredOn());
    }

    @Test
    void bucketInventoryConfigured() {
        var id = Bucket.Id.generate();
        var inventory = new BucketInventoryConfiguration("my-bucket", "inv-id", "CSV", "Daily", true);
        var event = new ObjectStoreEvent.BucketInventoryConfigured(id, inventory, Instant.now());
        assertEquals(id, event.id());
        assertTrue(event.config().enabled());
    }

    @Test
    void bucketInventoryDeleted() {
        var id = Bucket.Id.generate();
        var event = new ObjectStoreEvent.BucketInventoryDeleted(id, Instant.now());
        assertEquals(id, event.id());
        assertNotNull(event.occurredOn());
    }

    @Test
    void bucketMetricsConfigured() {
        var id = Bucket.Id.generate();
        var metrics = new BucketMetricsConfiguration("my-bucket", "metrics-id", null);
        var event = new ObjectStoreEvent.BucketMetricsConfigured(id, metrics, Instant.now());
        assertEquals(id, event.id());
        assertEquals("metrics-id", event.config().metricsId());
    }

    @Test
    void bucketMetricsDeleted() {
        var id = Bucket.Id.generate();
        var event = new ObjectStoreEvent.BucketMetricsDeleted(id, Instant.now());
        assertEquals(id, event.id());
        assertNotNull(event.occurredOn());
    }

    @Test
    void bucketIntelligentTieringConfigured() {
        var id = Bucket.Id.generate();
        var tiering = new BucketIntelligentTieringConfiguration("my-bucket", "tiering-id", null, null);
        var event = new ObjectStoreEvent.BucketIntelligentTieringConfigured(id, tiering, Instant.now());
        assertEquals(id, event.id());
        assertEquals("tiering-id", event.config().tieringId());
    }

    @Test
    void bucketIntelligentTieringDeleted() {
        var id = Bucket.Id.generate();
        var event = new ObjectStoreEvent.BucketIntelligentTieringDeleted(id, Instant.now());
        assertEquals(id, event.id());
        assertNotNull(event.occurredOn());
    }
}
