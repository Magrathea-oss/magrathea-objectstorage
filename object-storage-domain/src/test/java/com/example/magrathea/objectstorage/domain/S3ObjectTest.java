package com.example.magrathea.objectstorage.domain;

import com.example.magrathea.objectstorage.domain.aggregate.Bucket;
import com.example.magrathea.objectstorage.domain.aggregate.S3Object;
import com.example.magrathea.objectstorage.domain.valueobject.ContentDescriptor;
import com.example.magrathea.objectstorage.domain.valueobject.ObjectKey;
import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Pure JUnit test for S3Object aggregate — NO Spring context.
 * Uses ONLY AWS S3 native terminology.
 */
class S3ObjectTest {

    @Test
    void create_validObject() {
        var bucketId = Bucket.Id.generate();
        var id = S3Object.Id.generate();
        var key = ObjectKey.of("test-key");
        var object = S3Object.create(id, bucketId, key,
            "text/plain", null, null, 100, Map.of("description", "test"));
        assertEquals("test-key", object.key().value());
        assertEquals("text/plain", object.contentType());
        assertEquals(100, object.size());
        assertTrue(object.hasMetadata());
        assertFalse(object.hasEtag());
    }

    @Test
    void create_sizeNegative_throws() {
        var bucketId = Bucket.Id.generate();
        var id = S3Object.Id.generate();
        var key = ObjectKey.of("test");
        assertThrows(IllegalArgumentException.class,
            () -> S3Object.create(id, bucketId, key, "text/plain", null, null, -1, Map.of()));
    }

    @Test
    void create_nullKey_throws() {
        var bucketId = Bucket.Id.generate();
        var id = S3Object.Id.generate();
        assertThrows(NullPointerException.class,
            () -> S3Object.create(id, bucketId, null, "text/plain", null, null, 100, Map.of()));
    }

    @Test
    void withEtag_returnsNewInstance() {
        var bucketId = Bucket.Id.generate();
        var id = S3Object.Id.generate();
        var key = ObjectKey.of("test");
        var object = S3Object.create(id, bucketId, key, "text/plain", null, null, 100, Map.of());
        var withEtag = object.withEtag("\"abc123\"");
        assertTrue(withEtag.hasEtag());
        assertEquals("\"abc123\"", withEtag.etag());
        assertNotEquals(object, withEtag);
    }

    @Test
    void withStorageClass_returnsNewInstance() {
        var bucketId = Bucket.Id.generate();
        var id = S3Object.Id.generate();
        var key = ObjectKey.of("test");
        var object = S3Object.create(id, bucketId, key, "text/plain", null, null, 100, Map.of());
        var withSc = object.withStorageClass("STANDARD_IA");
        assertTrue(withSc.hasStorageClass());
        assertEquals("STANDARD_IA", withSc.storageClass());
        assertNotEquals(object, withSc);
    }

    @Test
    void restore_fromPersistence() {
        var bucketId = Bucket.Id.generate();
        var id = S3Object.Id.generate();
        var key = ObjectKey.of("test");
        var now = java.time.Instant.now();
        var contentDescriptor = ContentDescriptor.of(100, "abc123", "content-id-1");
        var object = S3Object.restore(id, bucketId, key,
            "\"etag\"", 100, "STANDARD", now,
            "text/plain", null, null, Map.of(), contentDescriptor);
        assertEquals(100, object.size());
        assertEquals("\"etag\"", object.etag());
        assertEquals("STANDARD", object.storageClass());
        assertEquals("text/plain", object.contentType());
        assertNotNull(object.lastModified());
        assertTrue(object.hasContentDescriptor());
        assertEquals("abc123", object.content().md5Hash());
        assertEquals("content-id-1", object.content().contentId());
    }

    @Test
    void id_generatesUniqueValues() {
        var id1 = S3Object.Id.generate();
        var id2 = S3Object.Id.generate();
        assertNotNull(id1.value());
        assertNotNull(id2.value());
        assertNotEquals(id1.value(), id2.value());
    }

    @Test
    void withContent_returnsNewInstance() {
        var bucketId = Bucket.Id.generate();
        var id = S3Object.Id.generate();
        var key = ObjectKey.of("test");
        var object = S3Object.create(id, bucketId, key, "text/plain", null, null, 100, Map.of());
        var descriptor = ContentDescriptor.of(100, "d41d8cd98f00b204e9800998ecf8427e", "content-ref-1");
        var withContent = object.withContent(descriptor);
        assertTrue(withContent.hasContentDescriptor());
        assertEquals("d41d8cd98f00b204e9800998ecf8427e", withContent.content().md5Hash());
        assertEquals("content-ref-1", withContent.content().contentId());
        assertEquals(100, withContent.content().size());
        assertEquals(100, withContent.size());
        assertNotEquals(object, withContent);
    }

    @Test
    void withContent_nullDescriptor_throws() {
        var bucketId = Bucket.Id.generate();
        var id = S3Object.Id.generate();
        var key = ObjectKey.of("test");
        var object = S3Object.create(id, bucketId, key, "text/plain", null, null, 100, Map.of());
        assertThrows(NullPointerException.class, () -> object.withContent(null));
    }

    @Test
    void contentDescriptor_initialNull() {
        var bucketId = Bucket.Id.generate();
        var id = S3Object.Id.generate();
        var key = ObjectKey.of("test");
        var object = S3Object.create(id, bucketId, key, "text/plain", null, null, 100, Map.of());
        assertFalse(object.hasContentDescriptor());
        assertNull(object.content());
    }

    @Test
    void id_null_throws() {
        assertThrows(NullPointerException.class, () -> new S3Object.Id(null));
    }
}
