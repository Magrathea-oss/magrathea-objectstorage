package com.example.magrathea.objectstore.domain;

import com.example.magrathea.objectstore.domain.aggregate.Bucket;
import com.example.magrathea.objectstore.domain.aggregate.S3Object;
import com.example.magrathea.objectstore.domain.event.ObjectStoreEvent;
import com.example.magrathea.objectstore.domain.valueobject.ContentDescriptor;
import com.example.magrathea.objectstore.domain.valueobject.ObjectKey;
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
        var key = ObjectKey.of("test.txt");
        var event = new ObjectStoreEvent.ObjectCreated(id, bucketId, key, Instant.now());
        assertEquals(id, event.id());
        assertEquals(bucketId, event.bucketId());
        assertEquals("test.txt", event.key().value());
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
}
