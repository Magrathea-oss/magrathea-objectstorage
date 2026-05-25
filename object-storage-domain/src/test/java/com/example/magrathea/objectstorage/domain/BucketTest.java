package com.example.magrathea.objectstorage.domain;

import com.example.magrathea.objectstorage.domain.aggregate.Bucket;
import com.example.magrathea.objectstorage.domain.aggregate.Bucket.Configuration.CorsRule;
import com.example.magrathea.objectstorage.domain.event.ObjectStorageEvent;
import com.example.magrathea.objectstorage.domain.valueobject.Region;
import com.example.magrathea.objectstorage.domain.valueobject.StorageClass;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Pure JUnit test for Bucket aggregate — NO Spring context.
 */
class BucketTest {

    @Test
    void create_validBucket() {
        var id = Bucket.Id.generate();
        var bucket = Bucket.create(id, "my-bucket", Region.US_EAST_1, StorageClass.STANDARD);
        assertEquals("my-bucket", bucket.name());
        assertEquals(Region.US_EAST_1, bucket.region());
        assertEquals(StorageClass.STANDARD, bucket.storageClass());
        assertFalse(bucket.versioningEnabled());
        assertFalse(bucket.encryptionEnabled());
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

    @Test
    void withVersioningEnabled_returnsNewInstance() {
        var id = Bucket.Id.generate();
        var bucket = Bucket.create(id, "my-bucket", Region.US_EAST_1, StorageClass.STANDARD);
        var versioned = bucket.withVersioningEnabled();
        assertTrue(versioned.versioningEnabled());
        assertNotEquals(bucket, versioned);
    }

    @Test
    void withEncryptionEnabled_returnsNewInstance() {
        var id = Bucket.Id.generate();
        var bucket = Bucket.create(id, "my-bucket", Region.US_EAST_1, StorageClass.STANDARD);
        var encrypted = bucket.withEncryptionEnabled();
        assertTrue(encrypted.encryptionEnabled());
        assertNotEquals(bucket, encrypted);
    }

    @Test
    void create_withConfiguration() {
        var id = Bucket.Id.generate();
        var rule = new CorsRule(List.of("*", "http://example.com"), List.of("GET", "PUT"),
            List.of("x-amz-request-id"), 3600, List.of("x-amz-request-id"), "my-rule");
        var config = new Bucket.Configuration(List.of(rule));
        var bucket = Bucket.create(id, "my-bucket", Region.US_EAST_1, StorageClass.STANDARD, config);
        assertEquals("my-bucket", bucket.name());
        assertNotNull(bucket.configuration());
        assertTrue(bucket.configuration().hasCors());
        assertEquals(1, bucket.configuration().corsRules().size());
    }

    @Test
    void create_withoutConfiguration() {
        var id = Bucket.Id.generate();
        var bucket = Bucket.create(id, "my-bucket", Region.US_EAST_1, StorageClass.STANDARD);
        assertNull(bucket.configuration());
    }

    @Test
    void withConfiguration_returnsNewInstance() {
        var id = Bucket.Id.generate();
        var bucket = Bucket.create(id, "my-bucket", Region.US_EAST_1, StorageClass.STANDARD);
        assertNull(bucket.configuration());

        var rule = new CorsRule(List.of("*"), List.of("GET"), null, 0, null, null);
        var config = new Bucket.Configuration(List.of(rule));
        var configured = bucket.withConfiguration(config);

        assertNotNull(configured.configuration());
        assertTrue(configured.configuration().hasCors());
        assertNotEquals(bucket, configured);
        // Original unchanged
        assertNull(bucket.configuration());
    }

    @Test
    void withConfiguration_recordsEvent() {
        var id = Bucket.Id.generate();
        var bucket = Bucket.create(id, "my-bucket", Region.US_EAST_1, StorageClass.STANDARD);
        var config = new Bucket.Configuration(List.of(
            new CorsRule(List.of("*"), List.of("GET"), null, 0, null, null)));
        var configured = bucket.withConfiguration(config);
        var events = configured.domainEvents();
        assertEquals(2, events.size()); // BucketCreated + BucketConfigurationChanged
        var changeEvent = events.get(1);
        assertInstanceOf(ObjectStorageEvent.BucketConfigurationChanged.class, changeEvent);
        var cast = (ObjectStorageEvent.BucketConfigurationChanged) changeEvent;
        assertEquals(id, cast.id());
        assertTrue(cast.config().hasCors());
    }

    @Test
    void withConfiguration_null_throws() {
        var id = Bucket.Id.generate();
        var bucket = Bucket.create(id, "my-bucket", Region.US_EAST_1, StorageClass.STANDARD);
        assertThrows(NullPointerException.class, () -> bucket.withConfiguration(null));
    }

    @Test
    void configuration_preservedAcrossTransitions() {
        var id = Bucket.Id.generate();
        var config = new Bucket.Configuration(List.of(
            new CorsRule(List.of("*"), List.of("GET"), null, 0, null, null)));
        var bucket = Bucket.create(id, "my-bucket", Region.US_EAST_1, StorageClass.STANDARD, config);
        var versioned = bucket.withVersioningEnabled();
        assertTrue(versioned.versioningEnabled());
        assertNotNull(versioned.configuration());
        assertTrue(versioned.configuration().hasCors());
    }

    @Test
    void restore_withConfiguration() {
        var id = Bucket.Id.generate();
        var config = new Bucket.Configuration(List.of(
            new CorsRule(List.of("*"), List.of("GET"), null, 0, null, null)));
        var bucket = Bucket.restore(id, "my-bucket", Region.US_EAST_1, StorageClass.STANDARD,
            true, true, config);
        assertTrue(bucket.versioningEnabled());
        assertTrue(bucket.encryptionEnabled());
        assertNotNull(bucket.configuration());
        assertTrue(bucket.configuration().hasCors());
        assertTrue(bucket.domainEvents().isEmpty());
    }

    @Test
    void restore_withoutConfiguration() {
        var id = Bucket.Id.generate();
        var bucket = Bucket.restore(id, "my-bucket", Region.US_EAST_1, StorageClass.STANDARD,
            false, false);
        assertNull(bucket.configuration());
    }

    @Test
    void clearEvents_preservesConfiguration() {
        var id = Bucket.Id.generate();
        var config = new Bucket.Configuration(List.of(
            new CorsRule(List.of("*"), List.of("GET"), null, 0, null, null)));
        var bucket = Bucket.create(id, "my-bucket", Region.US_EAST_1, StorageClass.STANDARD, config);
        var cleared = bucket.clearEvents();
        assertTrue(cleared.domainEvents().isEmpty());
        assertNotNull(cleared.configuration());
        assertTrue(cleared.configuration().hasCors());
    }

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
}
