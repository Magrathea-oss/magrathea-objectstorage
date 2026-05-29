package com.example.magrathea.objectstore.domain;

import com.example.magrathea.objectstore.domain.aggregate.Bucket;
import com.example.magrathea.objectstore.domain.aggregate.S3Object;
import com.example.magrathea.objectstore.domain.aggregate.ActiveS3Object;
import com.example.magrathea.objectstore.domain.aggregate.ArchivedS3Object;
import com.example.magrathea.objectstore.domain.aggregate.CreatingS3Object;
import com.example.magrathea.objectstore.domain.aggregate.DeletedS3Object;
import com.example.magrathea.objectstore.domain.aggregate.LockedS3Object;
import com.example.magrathea.objectstore.domain.aggregate.RestoredS3Object;
import com.example.magrathea.objectstore.domain.valueobject.ContentDescriptor;
import com.example.magrathea.objectstore.domain.valueobject.EncryptionConfiguration;
import com.example.magrathea.objectstore.domain.valueobject.EncryptionAlgorithm;
import com.example.magrathea.objectstore.domain.valueobject.EncryptionKeyReference;
import com.example.magrathea.objectstore.domain.valueobject.EncryptionContext;
import com.example.magrathea.objectstore.domain.valueobject.ObjectKey;
import com.example.magrathea.objectstore.domain.valueobject.ObjectLockConfiguration;
import com.example.magrathea.objectstore.domain.valueobject.RestoreConfiguration;
import com.example.magrathea.objectstore.domain.event.ObjectStoreEvent;

import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.time.Instant;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Pure JUnit test for S3Object sealed state machine — NO Spring context.
 * Uses ONLY AWS S3 native terminology.
 */
class S3ObjectTest {

    // ── CreatingS3Object tests ──

    @Test
    void create_returnsCreatingS3Object() {
        var bucketId = Bucket.Id.generate();
        var id = S3Object.Id.generate();
        var key = ObjectKey.of("test-bucket", "test-key");
        var object = S3Object.create(id, bucketId, key,
            "text/plain", null, null, 100, Map.of("description", "test"), null);
        assertInstanceOf(CreatingS3Object.class, object);
        assertEquals("test-bucket", object.key().bucket());
        assertEquals("test-key", object.key().key());
        assertFalse(object.hasEtag());
        assertFalse(object.hasEncryption());
    }

    @Test
    void create_sizeNegative_throws() {
        var bucketId = Bucket.Id.generate();
        var id = S3Object.Id.generate();
        var key = ObjectKey.of("test-bucket", "test");
        assertThrows(IllegalArgumentException.class,
            () -> S3Object.create(id, bucketId, key, "text/plain", null, null, -1, Map.of(), null));
    }

    @Test
    void create_nullKey_throws() {
        var bucketId = Bucket.Id.generate();
        var id = S3Object.Id.generate();
        assertThrows(NullPointerException.class,
            () -> S3Object.create(id, bucketId, null, "text/plain", null, null, 100, Map.of(), null));
    }

    // ── State machine: Creating → Active ──

    @Test
    void creating_attachContent_transitionsToActive() {
        var bucketId = Bucket.Id.generate();
        var id = S3Object.Id.generate();
        var key = ObjectKey.of("test-bucket", "test");
        var object = S3Object.create(id, bucketId, key, "text/plain", null, null, 100, Map.of(), null);
        assertInstanceOf(CreatingS3Object.class, object);

        var descriptor = ContentDescriptor.of(100, "d41d8cd98f00b204e9800998ecf8427e", "content-ref-1");
        var active = ((CreatingS3Object) object).attachContent(descriptor);
        assertInstanceOf(ActiveS3Object.class, active);
        assertTrue(active.hasContentDescriptor());
        assertEquals("d41d8cd98f00b204e9800998ecf8427e", active.contentDescriptor().md5Hash());
        assertEquals("content-ref-1", active.contentDescriptor().contentId());
        assertEquals(100, active.contentDescriptor().size());
    }

    @Test
    void creating_attachContent_null_throws() {
        var bucketId = Bucket.Id.generate();
        var id = S3Object.Id.generate();
        var key = ObjectKey.of("test-bucket", "test");
        var object = S3Object.create(id, bucketId, key, "text/plain", null, null, 100, Map.of(), null);
        assertThrows(NullPointerException.class,
            () -> ((CreatingS3Object) object).attachContent(null));
    }

    // ── State machine: Active → Locked ──

    @Test
    void active_applyLock_transitionsToLocked() {
        var bucketId = Bucket.Id.generate();
        var id = S3Object.Id.generate();
        var key = ObjectKey.of("test-bucket", "test");
        var creating = S3Object.create(id, bucketId, key, "text/plain", null, null, 100, Map.of(), null);
        var descriptor = ContentDescriptor.of(100, "abc123", "content-id-1");
        var active = ((CreatingS3Object) creating).attachContent(descriptor);

        var lockConfig = ObjectLockConfiguration.of(
            ObjectLockConfiguration.ObjectLockMode.COMPLIANCE,
            ObjectLockConfiguration.RetentionPeriod.startingNow(Duration.ofDays(30)));
        var locked = active.applyLock(lockConfig);
        assertInstanceOf(LockedS3Object.class, locked);
        assertEquals(ObjectLockConfiguration.ObjectLockMode.COMPLIANCE,
            ((LockedS3Object) locked).lockConfiguration().mode());
    }

    @Test
    void active_applyLock_null_throws() {
        var bucketId = Bucket.Id.generate();
        var id = S3Object.Id.generate();
        var key = ObjectKey.of("test-bucket", "test");
        var creating = S3Object.create(id, bucketId, key, "text/plain", null, null, 100, Map.of(), null);
        var descriptor = ContentDescriptor.of(100, "abc123", "content-id-1");
        var active = ((CreatingS3Object) creating).attachContent(descriptor);
        assertThrows(NullPointerException.class,
            () -> active.applyLock(null));
    }

    // ── State machine: Active → Archived ──

    @Test
    void active_archive_transitionsToArchived() {
        var bucketId = Bucket.Id.generate();
        var id = S3Object.Id.generate();
        var key = ObjectKey.of("test-bucket", "test");
        var creating = S3Object.create(id, bucketId, key, "text/plain", null, null, 100, Map.of(), null);
        var descriptor = ContentDescriptor.of(100, "abc123", "content-id-1");
        var active = ((CreatingS3Object) creating).attachContent(descriptor);

        var archived = active.archive();
        assertInstanceOf(ArchivedS3Object.class, archived);
    }

    // ── State machine: Active → Deleted ──

    @Test
    void active_delete_transitionsToDeleted() {
        var bucketId = Bucket.Id.generate();
        var id = S3Object.Id.generate();
        var key = ObjectKey.of("test-bucket", "test");
        var creating = S3Object.create(id, bucketId, key, "text/plain", null, null, 100, Map.of(), null);
        var descriptor = ContentDescriptor.of(100, "abc123", "content-id-1");
        var active = ((CreatingS3Object) creating).attachContent(descriptor);

        var deleted = active.delete();
        assertInstanceOf(DeletedS3Object.class, deleted);
    }

    // ── State machine: Locked → Archived ──

    @Test
    void locked_archive_transitionsToArchived() {
        var bucketId = Bucket.Id.generate();
        var id = S3Object.Id.generate();
        var key = ObjectKey.of("test-bucket", "test");
        var creating = S3Object.create(id, bucketId, key, "text/plain", null, null, 100, Map.of(), null);
        var descriptor = ContentDescriptor.of(100, "abc123", "content-id-1");
        var active = ((CreatingS3Object) creating).attachContent(descriptor);
        var lockConfig = ObjectLockConfiguration.of(
            ObjectLockConfiguration.ObjectLockMode.COMPLIANCE,
            ObjectLockConfiguration.RetentionPeriod.startingNow(Duration.ofDays(30)));
        var locked = active.applyLock(lockConfig);

        var archived = locked.archive();
        assertInstanceOf(ArchivedS3Object.class, archived);
    }

    // ── State machine: Locked → Deleted ──

    @Test
    void locked_delete_transitionsToDeleted() {
        var bucketId = Bucket.Id.generate();
        var id = S3Object.Id.generate();
        var key = ObjectKey.of("test-bucket", "test");
        var creating = S3Object.create(id, bucketId, key, "text/plain", null, null, 100, Map.of(), null);
        var descriptor = ContentDescriptor.of(100, "abc123", "content-id-1");
        var active = ((CreatingS3Object) creating).attachContent(descriptor);
        var lockConfig = ObjectLockConfiguration.of(
            ObjectLockConfiguration.ObjectLockMode.COMPLIANCE,
            ObjectLockConfiguration.RetentionPeriod.startingNow(Duration.ofDays(30)));
        var locked = active.applyLock(lockConfig);

        var deleted = locked.delete();
        assertInstanceOf(DeletedS3Object.class, deleted);
    }

    // ── State machine: Archived → Restored ──

    @Test
    void archived_restore_transitionsToRestored() {
        var bucketId = Bucket.Id.generate();
        var id = S3Object.Id.generate();
        var key = ObjectKey.of("test-bucket", "test");
        var creating = S3Object.create(id, bucketId, key, "text/plain", null, null, 100, Map.of(), null);
        var descriptor = ContentDescriptor.of(100, "abc123", "content-id-1");
        var active = ((CreatingS3Object) creating).attachContent(descriptor);
        var archived = active.archive();

        var restoreConfig = RestoreConfiguration.of(
            Instant.now(), Instant.now().plus(Duration.ofDays(3)),
            RestoreConfiguration.RestoreTier.STANDARD);
        var restored = archived.restore(restoreConfig);
        assertInstanceOf(RestoredS3Object.class, restored);
    }

    @Test
    void archived_restore_null_throws() {
        var bucketId = Bucket.Id.generate();
        var id = S3Object.Id.generate();
        var key = ObjectKey.of("test-bucket", "test");
        var creating = S3Object.create(id, bucketId, key, "text/plain", null, null, 100, Map.of(), null);
        var descriptor = ContentDescriptor.of(100, "abc123", "content-id-1");
        var active = ((CreatingS3Object) creating).attachContent(descriptor);
        var archived = active.archive();
        assertThrows(NullPointerException.class,
            () -> archived.restore(null));
    }

    // ── State machine: Restored → Locked ──

    @Test
    void restored_applyLock_transitionsToLocked() {
        var bucketId = Bucket.Id.generate();
        var id = S3Object.Id.generate();
        var key = ObjectKey.of("test-bucket", "test");
        var creating = S3Object.create(id, bucketId, key, "text/plain", null, null, 100, Map.of(), null);
        var descriptor = ContentDescriptor.of(100, "abc123", "content-id-1");
        var active = ((CreatingS3Object) creating).attachContent(descriptor);
        var archived = active.archive();
        var restoreConfig = RestoreConfiguration.of(
            Instant.now(), Instant.now().plus(Duration.ofDays(3)),
            RestoreConfiguration.RestoreTier.STANDARD);
        var restored = archived.restore(restoreConfig);

        var lockConfig = ObjectLockConfiguration.of(
            ObjectLockConfiguration.ObjectLockMode.GOVERNANCE,
            ObjectLockConfiguration.RetentionPeriod.startingNow(Duration.ofDays(7)));
        var locked = restored.applyLock(lockConfig);
        assertInstanceOf(LockedS3Object.class, locked);
        assertEquals(ObjectLockConfiguration.ObjectLockMode.GOVERNANCE,
            ((LockedS3Object) locked).lockConfiguration().mode());
    }

    // ── State machine: Restored → Deleted ──

    @Test
    void restored_delete_transitionsToDeleted() {
        var bucketId = Bucket.Id.generate();
        var id = S3Object.Id.generate();
        var key = ObjectKey.of("test-bucket", "test");
        var creating = S3Object.create(id, bucketId, key, "text/plain", null, null, 100, Map.of(), null);
        var descriptor = ContentDescriptor.of(100, "abc123", "content-id-1");
        var active = ((CreatingS3Object) creating).attachContent(descriptor);
        var archived = active.archive();
        var restoreConfig = RestoreConfiguration.of(
            Instant.now(), Instant.now().plus(Duration.ofDays(3)),
            RestoreConfiguration.RestoreTier.STANDARD);
        var restored = archived.restore(restoreConfig);

        var deleted = restored.delete();
        assertInstanceOf(DeletedS3Object.class, deleted);
    }

    // ── Id tests ──

    @Test
    void id_generatesUniqueValues() {
        var id1 = S3Object.Id.generate();
        var id2 = S3Object.Id.generate();
        assertNotNull(id1.value());
        assertNotNull(id2.value());
        assertNotEquals(id1.value(), id2.value());
    }

    @Test
    void id_null_throws() {
        assertThrows(NullPointerException.class, () -> new S3Object.Id(null));
    }

    // ── Restore tests ──

    @Test
    void restoreActive_returnsActiveS3Object() {
        var bucketId = Bucket.Id.generate();
        var id = S3Object.Id.generate();
        var key = ObjectKey.of("test-bucket", "test");
        var now = Instant.now();
        var contentDescriptor = ContentDescriptor.of(100, "abc123", "content-id-1");
        var object = S3Object.restoreActive(id, bucketId, key,
            "\"etag\"", "STANDARD", now,
            "text/plain", null, null, Map.of(), contentDescriptor, null);
        assertInstanceOf(ActiveS3Object.class, object);
        assertEquals("\"etag\"", object.etag());
        assertEquals("STANDARD", object.storageClass());
        assertTrue(object.hasContentDescriptor());
        assertEquals("abc123", object.contentDescriptor().md5Hash());
        assertEquals("content-id-1", object.contentDescriptor().contentId());
        assertFalse(object.hasEncryption());
    }

    @Test
    void restore_withEncryption() {
        var bucketId = Bucket.Id.generate();
        var id = S3Object.Id.generate();
        var key = ObjectKey.of("test-bucket", "test");
        var now = Instant.now();
        var contentDescriptor = ContentDescriptor.of(100, "abc123", "content-id-1");
        var encryption = EncryptionConfiguration.of(EncryptionAlgorithm.AES256);
        var object = S3Object.restoreActive(id, bucketId, key,
            "\"etag\"", "STANDARD", now,
            "text/plain", null, null, Map.of(), contentDescriptor, encryption);
        assertTrue(object.hasEncryption());
        assertEquals(EncryptionAlgorithm.AES256, object.encryption().algorithm());
    }

    @Test
    void restoreLegacy_returnsActiveS3Object() {
        var bucketId = Bucket.Id.generate();
        var id = S3Object.Id.generate();
        var key = ObjectKey.of("test-bucket", "test");
        var now = Instant.now();
        var contentDescriptor = ContentDescriptor.of(100, "abc123", "content-id-1");
        var object = S3Object.restore(id, bucketId, key,
            "\"etag\"", 100, "STANDARD", now,
            "text/plain", null, null, Map.of(), contentDescriptor);
        assertInstanceOf(ActiveS3Object.class, object);
        assertEquals("\"etag\"", object.etag());
        assertEquals("STANDARD", object.storageClass());
        assertTrue(object.hasContentDescriptor());
        assertEquals("abc123", object.contentDescriptor().md5Hash());
        assertEquals("content-id-1", object.contentDescriptor().contentId());
    }

    @Test
    void restore_withLockConfiguration() {
        var bucketId = Bucket.Id.generate();
        var id = S3Object.Id.generate();
        var key = ObjectKey.of("test-bucket", "test");
        var now = Instant.now();
        var contentDescriptor = ContentDescriptor.of(100, "abc123", "content-id-1");
        var lockConfig = ObjectLockConfiguration.of(
            ObjectLockConfiguration.ObjectLockMode.COMPLIANCE,
            ObjectLockConfiguration.RetentionPeriod.startingNow(Duration.ofDays(30)));
        var object = S3Object.restore(id, bucketId, key,
            "\"etag\"", 100, "STANDARD", now,
            "text/plain", null, null, Map.of(), contentDescriptor,
            lockConfig, null, null);
        assertInstanceOf(LockedS3Object.class, object);
        assertEquals(ObjectLockConfiguration.ObjectLockMode.COMPLIANCE,
            ((LockedS3Object) object).lockConfiguration().mode());
    }

    @Test
    void restore_withRestoreConfiguration() {
        var bucketId = Bucket.Id.generate();
        var id = S3Object.Id.generate();
        var key = ObjectKey.of("test-bucket", "test");
        var now = Instant.now();
        var contentDescriptor = ContentDescriptor.of(100, "abc123", "content-id-1");
        var restoreConfig = RestoreConfiguration.of(
            now, now.plus(Duration.ofDays(3)),
            RestoreConfiguration.RestoreTier.STANDARD);
        var object = S3Object.restore(id, bucketId, key,
            "\"etag\"", 100, "STANDARD", now,
            "text/plain", null, null, Map.of(), contentDescriptor,
            null, restoreConfig, null);
        assertInstanceOf(RestoredS3Object.class, object);
        assertEquals(RestoreConfiguration.RestoreTier.STANDARD,
            ((RestoredS3Object) object).restoreConfiguration().tier());
    }

    @Test
    void restoreDeleted_returnsDeletedS3Object() {
        var bucketId = Bucket.Id.generate();
        var id = S3Object.Id.generate();
        var key = ObjectKey.of("test-bucket", "test");
        var object = S3Object.restoreDeleted(id, bucketId, key, null, Map.of());
        assertInstanceOf(DeletedS3Object.class, object);
    }

    @Test
    void restoreArchived_returnsArchivedS3Object() {
        var bucketId = Bucket.Id.generate();
        var id = S3Object.Id.generate();
        var key = ObjectKey.of("test-bucket", "test");
        var now = Instant.now();
        var contentDescriptor = ContentDescriptor.of(100, "abc123", "content-id-1");
        var object = S3Object.restoreArchived(id, bucketId, key,
            "\"etag\"", "GLACIER", now,
            "text/plain", null, null, Map.of(), contentDescriptor, null, null);
        assertInstanceOf(ArchivedS3Object.class, object);
    }

    // ── Domain events tests ──

    @Test
    void create_producesObjectCreatedEvent() {
        var bucketId = Bucket.Id.generate();
        var id = S3Object.Id.generate();
        var key = ObjectKey.of("test-bucket", "test");
        var object = S3Object.create(id, bucketId, key, "text/plain", null, null, 100, Map.of(), null);
        var events = object.domainEvents();
        assertEquals(1, events.size());
        assertInstanceOf(ObjectStoreEvent.ObjectCreated.class, events.get(0));
    }

    @Test
    void attachContent_producesContentDescriptorCreatedEvent() {
        var bucketId = Bucket.Id.generate();
        var id = S3Object.Id.generate();
        var key = ObjectKey.of("test-bucket", "test");
        var object = S3Object.create(id, bucketId, key, "text/plain", null, null, 100, Map.of(), null);
        var descriptor = ContentDescriptor.of(100, "abc123", "content-id-1");
        var active = ((CreatingS3Object) object).attachContent(descriptor);
        var events = active.domainEvents();
        assertEquals(2, events.size());
        assertInstanceOf(ObjectStoreEvent.ObjectCreated.class, events.get(0));
        assertInstanceOf(ObjectStoreEvent.ContentDescriptorCreated.class, events.get(1));
    }

    @Test
    void active_delete_producesObjectDeletedEvent() {
        var bucketId = Bucket.Id.generate();
        var id = S3Object.Id.generate();
        var key = ObjectKey.of("test-bucket", "test");
        var creating = S3Object.create(id, bucketId, key, "text/plain", null, null, 100, Map.of(), null);
        var descriptor = ContentDescriptor.of(100, "abc123", "content-id-1");
        var active = ((CreatingS3Object) creating).attachContent(descriptor);
        var deleted = active.delete();
        var events = deleted.domainEvents();
        assertEquals(3, events.size());
        assertInstanceOf(ObjectStoreEvent.ObjectDeleted.class, events.get(2));
    }

    @Test
    void active_archive_producesObjectArchivedEvent() {
        var bucketId = Bucket.Id.generate();
        var id = S3Object.Id.generate();
        var key = ObjectKey.of("test-bucket", "test");
        var creating = S3Object.create(id, bucketId, key, "text/plain", null, null, 100, Map.of(), null);
        var descriptor = ContentDescriptor.of(100, "abc123", "content-id-1");
        var active = ((CreatingS3Object) creating).attachContent(descriptor);
        var archived = active.archive();
        var events = archived.domainEvents();
        assertEquals(3, events.size());
        assertInstanceOf(ObjectStoreEvent.ObjectArchived.class, events.get(2));
    }

    @Test
    void archived_restore_producesObjectRestoredEvent() {
        var bucketId = Bucket.Id.generate();
        var id = S3Object.Id.generate();
        var key = ObjectKey.of("test-bucket", "test");
        var creating = S3Object.create(id, bucketId, key, "text/plain", null, null, 100, Map.of(), null);
        var descriptor = ContentDescriptor.of(100, "abc123", "content-id-1");
        var active = ((CreatingS3Object) creating).attachContent(descriptor);
        var archived = active.archive();
        var restoreConfig = RestoreConfiguration.of(
            Instant.now(), Instant.now().plus(Duration.ofDays(3)),
            RestoreConfiguration.RestoreTier.STANDARD);
        var restored = archived.restore(restoreConfig);
        var events = restored.domainEvents();
        assertEquals(4, events.size());
        assertInstanceOf(ObjectStoreEvent.ObjectRestored.class, events.get(3));
    }

    // ── clearEvents tests ──

    @Test
    void clearEvents_removesEventsFromCreatingS3Object() {
        var bucketId = Bucket.Id.generate();
        var id = S3Object.Id.generate();
        var key = ObjectKey.of("test-bucket", "test");
        var object = S3Object.create(id, bucketId, key, "text/plain", null, null, 100, Map.of(), null);
        assertEquals(1, object.domainEvents().size());
        var cleared = object.clearEvents();
        assertInstanceOf(CreatingS3Object.class, cleared);
        assertEquals(0, cleared.domainEvents().size());
    }

    @Test
    void clearEvents_removesEventsFromActiveS3Object() {
        var bucketId = Bucket.Id.generate();
        var id = S3Object.Id.generate();
        var key = ObjectKey.of("test-bucket", "test");
        var creating = S3Object.create(id, bucketId, key, "text/plain", null, null, 100, Map.of(), null);
        var descriptor = ContentDescriptor.of(100, "abc123", "content-id-1");
        var active = ((CreatingS3Object) creating).attachContent(descriptor);
        assertEquals(2, active.domainEvents().size());
        var cleared = active.clearEvents();
        assertInstanceOf(ActiveS3Object.class, cleared);
        assertEquals(0, cleared.domainEvents().size());
        assertTrue(cleared.hasContentDescriptor());
    }

    @Test
    void clearEvents_removesEventsFromDeletedS3Object() {
        var bucketId = Bucket.Id.generate();
        var id = S3Object.Id.generate();
        var key = ObjectKey.of("test-bucket", "test");
        var creating = S3Object.create(id, bucketId, key, "text/plain", null, null, 100, Map.of(), null);
        var descriptor = ContentDescriptor.of(100, "abc123", "content-id-1");
        var active = ((CreatingS3Object) creating).attachContent(descriptor);
        var deleted = active.delete();
        assertEquals(3, deleted.domainEvents().size());
        var cleared = deleted.clearEvents();
        assertInstanceOf(DeletedS3Object.class, cleared);
        assertEquals(0, cleared.domainEvents().size());
    }

    // ── EncryptionConfiguration tests ──

    @Test
    void create_withEncryption() {
        var bucketId = Bucket.Id.generate();
        var id = S3Object.Id.generate();
        var key = ObjectKey.of("test-bucket", "test-key");
        var encryption = EncryptionConfiguration.of(EncryptionAlgorithm.AES256);
        var object = S3Object.create(id, bucketId, key,
            "text/plain", null, null, 100, Map.of("description", "test"), encryption);
        assertInstanceOf(CreatingS3Object.class, object);
        assertTrue(object.hasEncryption());
        assertEquals(EncryptionAlgorithm.AES256, object.encryption().algorithm());
    }

    @Test
    void encryption_preservedThroughTransition() {
        var bucketId = Bucket.Id.generate();
        var id = S3Object.Id.generate();
        var key = ObjectKey.of("test-bucket", "test-key");
        var encryption = EncryptionConfiguration.of(EncryptionAlgorithm.AWS_KMS,
            EncryptionKeyReference.of("kms-key-1"));
        var object = S3Object.create(id, bucketId, key,
            "text/plain", null, null, 100, Map.of(), encryption);
        assertTrue(object.hasEncryption());
        assertEquals(EncryptionAlgorithm.AWS_KMS, object.encryption().algorithm());
        assertEquals("kms-key-1", object.encryption().keyReference().keyId());

        // Transition to Active should preserve encryption
        var descriptor = ContentDescriptor.of(100, "abc123", "content-id-1");
        var active = ((CreatingS3Object) object).attachContent(descriptor);
        assertTrue(active.hasEncryption());
        assertEquals(EncryptionAlgorithm.AWS_KMS, active.encryption().algorithm());
        assertEquals("kms-key-1", active.encryption().keyReference().keyId());
    }

    // ── EncryptionConfiguration value object tests ──

    @Test
    void encryptionConfiguration_nullAlgorithm_throws() {
        assertThrows(NullPointerException.class,
            () -> new EncryptionConfiguration(null, null, null));
    }

    @Test
    void encryptionKeyReference_nullKeyId_throws() {
        assertThrows(NullPointerException.class,
            () -> EncryptionKeyReference.of(null));
    }

    @Test
    void encryptionContext_nullContext_throws() {
        assertThrows(NullPointerException.class,
            () -> EncryptionContext.of(null));
    }

    // ── size accessor ──

    @Test
    void size_reflectsContentDescriptorSize() {
        var bucketId = Bucket.Id.generate();
        var id = S3Object.Id.generate();
        var key = ObjectKey.of("test-bucket", "test");
        var creating = S3Object.create(id, bucketId, key, "text/plain", null, null, 100, Map.of(), null);
        var descriptor = ContentDescriptor.of(200, "abc123", "content-id-1");
        var active = ((CreatingS3Object) creating).attachContent(descriptor);
        assertEquals(200, active.contentDescriptor().size());
    }
}
