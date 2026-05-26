package com.example.magrathea.objectstore.domain;

import com.example.magrathea.objectstore.domain.aggregate.Bucket;
import com.example.magrathea.objectstore.domain.event.ObjectStoreEvent;
import com.example.magrathea.objectstore.domain.valueobject.BucketConfig;
import com.example.magrathea.objectstore.domain.valueobject.CorsConfiguration;
import com.example.magrathea.objectstore.domain.valueobject.Region;
import com.example.magrathea.objectstore.domain.valueobject.StorageClass;
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
}
