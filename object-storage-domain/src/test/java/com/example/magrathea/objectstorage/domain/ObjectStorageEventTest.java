package com.example.magrathea.objectstorage.domain;

import com.example.magrathea.objectstorage.domain.aggregate.Bucket;
import com.example.magrathea.objectstorage.domain.aggregate.S3Object;
import com.example.magrathea.objectstorage.domain.event.ObjectStorageEvent;
import com.example.magrathea.objectstorage.domain.valueobject.ObjectKey;
import org.junit.jupiter.api.Test;

import java.time.Instant;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Pure JUnit test for ObjectStorageEvent sealed interface.
 */
class ObjectStorageEventTest {

    @Test
    void bucketCreated() {
        var id = Bucket.Id.generate();
        var event = new ObjectStorageEvent.BucketCreated(id, "my-bucket", Instant.now());
        assertEquals("my-bucket", event.name());
        assertEquals(id, event.id());
        assertNotNull(event.occurredOn());
    }

    @Test
    void bucketDeleted() {
        var id = Bucket.Id.generate();
        var event = new ObjectStorageEvent.BucketDeleted(id, Instant.now());
        assertEquals(id, event.id());
        assertNotNull(event.occurredOn());
    }

    @Test
    void objectCreated() {
        var bucketId = Bucket.Id.generate();
        var id = S3Object.Id.generate();
        var key = ObjectKey.of("test.txt");
        var event = new ObjectStorageEvent.ObjectCreated(id, bucketId, key, Instant.now());
        assertEquals(id, event.id());
        assertEquals(bucketId, event.bucketId());
        assertEquals("test.txt", event.key().value());
    }

    @Test
    void objectDeleted() {
        var bucketId = Bucket.Id.generate();
        var id = S3Object.Id.generate();
        var event = new ObjectStorageEvent.ObjectDeleted(id, bucketId, Instant.now());
        assertEquals(id, event.id());
        assertEquals(bucketId, event.bucketId());
    }

    @Test
    void sealedInterface_permitsOnlyKnownTypes() {
        // ObjectStorageEvent permits only BucketCreated, BucketDeleted, ObjectCreated, ObjectDeleted
        assertTrue(ObjectStorageEvent.class.isSealed());
    }

    @Test
    void patternMatching() {
        var event = (ObjectStorageEvent) new ObjectStorageEvent.BucketCreated(
            Bucket.Id.generate(), "test", Instant.now());
        switch (event) {
            case ObjectStorageEvent.BucketCreated c -> assertEquals("test", c.name());
            default -> fail("unexpected event type");
        }
    }
}
