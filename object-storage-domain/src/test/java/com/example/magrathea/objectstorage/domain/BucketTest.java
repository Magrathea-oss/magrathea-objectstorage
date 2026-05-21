package com.example.magrathea.objectstorage.domain;

import com.example.magrathea.objectstorage.domain.aggregate.Bucket;
import com.example.magrathea.objectstorage.domain.valueobject.Region;
import com.example.magrathea.objectstorage.domain.valueobject.StorageClass;
import org.junit.jupiter.api.Test;

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
