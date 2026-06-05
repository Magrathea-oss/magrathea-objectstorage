package com.example.magrathea.objectstore.domain;

import com.example.magrathea.objectstore.domain.aggregate.ActiveS3Object;
import com.example.magrathea.objectstore.domain.aggregate.ArchivedS3Object;
import com.example.magrathea.objectstore.domain.aggregate.DeletedS3Object;
import com.example.magrathea.objectstore.domain.aggregate.LockedS3Object;
import com.example.magrathea.objectstore.domain.aggregate.S3Object;
import com.example.magrathea.objectstore.domain.aggregate.WriteState;
import com.example.magrathea.objectstore.domain.event.ObjectStoreEvent;
import com.example.magrathea.objectstore.domain.valueobject.EncryptionAlgorithm;
import com.example.magrathea.objectstore.domain.valueobject.EncryptionConfiguration;
import com.example.magrathea.objectstore.domain.valueobject.EncryptionKeyReference;
import com.example.magrathea.objectstore.domain.valueobject.ObjectChecksum;
import com.example.magrathea.objectstore.domain.valueobject.ObjectKey;
import com.example.magrathea.objectstore.domain.valueobject.ObjectLockConfiguration;
import com.example.magrathea.objectstore.domain.valueobject.ChecksumAlgorithm;
import com.example.magrathea.objectstore.domain.valueobject.ChecksumValue;

import java.time.Duration;
import java.time.ZonedDateTime;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Pure JUnit test for S3Object sealed state machine — NO Spring context.
 * Uses ONLY AWS S3 native terminology.
 */
class S3ObjectTest {

    // ── Helper: a minimal checksum ──
    private static ObjectChecksum someChecksum() {
        return ObjectChecksum.of(Set.of(
            new ChecksumValue(ChecksumAlgorithm.SHA256, "abc123def456")));
    }

    private static ObjectKey someKey() {
        return ObjectKey.of("test-bucket", "test-key");
    }

    // ── S3Object.create() tests ──

    @Test
    void create_returnsActiveS3Object() {
        var key = someKey();
        var object = S3Object.create(key, "STANDARD",
            Map.of("description", "test"),
            null, someChecksum(), 100);
        assertInstanceOf(ActiveS3Object.class, object);
        assertEquals("test-bucket", object.key().bucket());
        assertEquals("test-key", object.key().key());
        assertEquals("STANDARD", object.storageClass());
        assertEquals(100, object.size());
        assertFalse(object.hasEncryption());
        assertTrue(object.hasChecksum());
    }

    @Test
    void create_sizeNegative_throws() {
        var key = someKey();
        assertThrows(IllegalArgumentException.class,
            () -> S3Object.create(key, "STANDARD", Map.of(), null, someChecksum(), -1));
    }

    @Test
    void create_nullKey_throws() {
        assertThrows(NullPointerException.class,
            () -> S3Object.create(null, "STANDARD", Map.of(), null, someChecksum(), 100));
    }

    @Test
    void create_nullChecksum_throws() {
        var key = someKey();
        assertThrows(NullPointerException.class,
            () -> S3Object.create(key, "STANDARD", Map.of(), null, null, 100));
    }

    @Test
    void create_producesObjectCreatedEvent() {
        var key = someKey();
        var object = S3Object.create(key, "STANDARD", Map.of(), null, someChecksum(), 100);
        var events = object.domainEvents();
        assertEquals(1, events.size());
        assertInstanceOf(ObjectStoreEvent.ObjectCreated.class, events.get(0));
        var created = (ObjectStoreEvent.ObjectCreated) events.get(0);
        assertEquals(key, created.key());
        assertNotNull(created.occurredOn());
    }

    // ── State machine: Active → Archived ──

    @Test
    void active_archive_transitionsToArchived() {
        var key = someKey();
        var object = S3Object.create(key, "STANDARD", Map.of(), null, someChecksum(), 100);

        var archived = object.archive();
        assertInstanceOf(ArchivedS3Object.class, archived);
        assertFalse(archived.restored());
        assertNull(archived.restoreExpiry());
    }

    @Test
    void active_archive_producesObjectArchivedEvent() {
        var key = someKey();
        var object = S3Object.create(key, "STANDARD", Map.of(), null, someChecksum(), 100);
        var archived = object.archive();
        var events = archived.domainEvents();
        assertEquals(2, events.size());
        assertInstanceOf(ObjectStoreEvent.ObjectArchived.class, events.get(1));
    }

    // ── State machine: Active → Locked ──

    @Test
    void active_applyLock_transitionsToLocked() {
        var key = someKey();
        var object = S3Object.create(key, "STANDARD", Map.of(), null, someChecksum(), 100);

        var lockConfig = ObjectLockConfiguration.of(
            ObjectLockConfiguration.ObjectLockMode.COMPLIANCE,
            ObjectLockConfiguration.RetentionPeriod.startingNow(Duration.ofDays(30)));
        var locked = object.applyLock(lockConfig);
        assertInstanceOf(LockedS3Object.class, locked);
        assertEquals(ObjectLockConfiguration.ObjectLockMode.COMPLIANCE,
            locked.lockConfiguration().mode());
        assertFalse(locked.lockConfiguration().legalHold());
    }

    @Test
    void active_applyLock_null_throws() {
        var key = someKey();
        var object = S3Object.create(key, "STANDARD", Map.of(), null, someChecksum(), 100);
        assertThrows(IllegalStateTransitionException.class,
            () -> object.applyLock(null));
    }

    @Test
    void active_applyLock_producesObjectLockConfiguredEvent() {
        var key = someKey();
        var object = S3Object.create(key, "STANDARD", Map.of(), null, someChecksum(), 100);
        var lockConfig = ObjectLockConfiguration.of(
            ObjectLockConfiguration.ObjectLockMode.GOVERNANCE,
            ObjectLockConfiguration.RetentionPeriod.startingNow(Duration.ofDays(30)));
        var locked = object.applyLock(lockConfig);
        var events = locked.domainEvents();
        assertEquals(2, events.size());
        assertInstanceOf(ObjectStoreEvent.ObjectLockConfigured.class, events.get(1));
    }

    // ── State machine: Active → Deleted ──

    @Test
    void active_delete_transitionsToDeleted() {
        var key = someKey();
        var object = S3Object.create(key, "STANDARD", Map.of(), null, someChecksum(), 100);

        var deleted = object.delete();
        assertInstanceOf(DeletedS3Object.class, deleted);
    }

    @Test
    void active_delete_producesObjectDeletedEvent() {
        var key = someKey();
        var object = S3Object.create(key, "STANDARD", Map.of(), null, someChecksum(), 100);
        var deleted = object.delete();
        var events = deleted.domainEvents();
        assertEquals(2, events.size());
        assertInstanceOf(ObjectStoreEvent.ObjectDeleted.class, events.get(1));
    }

    // ── State machine: Locked → Active (removeLegalHold) ──

    @Test
    void locked_removeLegalHold_transitionsToActive() {
        var key = someKey();
        var object = S3Object.create(key, "STANDARD", Map.of(), null, someChecksum(), 100);

        // Apply lock with legal hold enabled
        var lockConfig = ObjectLockConfiguration.of(
            ObjectLockConfiguration.ObjectLockMode.GOVERNANCE,
            ObjectLockConfiguration.RetentionPeriod.startingNow(Duration.ofDays(30)),
            true);
        var locked = object.applyLock(lockConfig);

        var active = locked.removeLegalHold();
        assertInstanceOf(ActiveS3Object.class, active);
    }

    @Test
    void locked_removeLegalHold_withoutLegalHold_throws() {
        var key = someKey();
        var object = S3Object.create(key, "STANDARD", Map.of(), null, someChecksum(), 100);

        // Apply lock WITHOUT legal hold
        var lockConfig = ObjectLockConfiguration.of(
            ObjectLockConfiguration.ObjectLockMode.COMPLIANCE,
            ObjectLockConfiguration.RetentionPeriod.startingNow(Duration.ofDays(30)));
        var locked = object.applyLock(lockConfig);

        assertThrows(IllegalStateTransitionException.class,
            () -> locked.removeLegalHold());
    }

    @Test
    void locked_removeLegalHold_producesLegalHoldRemovedEvent() {
        var key = someKey();
        var object = S3Object.create(key, "STANDARD", Map.of(), null, someChecksum(), 100);
        var lockConfig = ObjectLockConfiguration.of(
            ObjectLockConfiguration.ObjectLockMode.GOVERNANCE,
            ObjectLockConfiguration.RetentionPeriod.startingNow(Duration.ofDays(30)),
            true);
        var locked = object.applyLock(lockConfig);
        var active = locked.removeLegalHold();
        var events = active.domainEvents();
        assertEquals(3, events.size());
        assertInstanceOf(ObjectStoreEvent.LegalHoldRemoved.class, events.get(2));
    }

    // ── State machine: Locked → Archived ──

    @Test
    void locked_archive_transitionsToArchived() {
        var key = someKey();
        var object = S3Object.create(key, "STANDARD", Map.of(), null, someChecksum(), 100);
        var lockConfig = ObjectLockConfiguration.of(
            ObjectLockConfiguration.ObjectLockMode.COMPLIANCE,
            ObjectLockConfiguration.RetentionPeriod.startingNow(Duration.ofDays(30)));
        var locked = object.applyLock(lockConfig);

        var archived = locked.archive();
        assertInstanceOf(ArchivedS3Object.class, archived);
        assertFalse(archived.restored());
    }

    // ── State machine: Archived → Deleted ──

    @Test
    void archived_delete_transitionsToDeleted() {
        var key = someKey();
        var object = S3Object.create(key, "STANDARD", Map.of(), null, someChecksum(), 100);
        var archived = object.archive();

        var deleted = archived.delete();
        assertInstanceOf(DeletedS3Object.class, deleted);
    }

    @Test
    void archived_delete_producesObjectDeletedEvent() {
        var key = someKey();
        var object = S3Object.create(key, "STANDARD", Map.of(), null, someChecksum(), 100);
        var archived = object.archive();
        var deleted = archived.delete();
        var events = deleted.domainEvents();
        assertEquals(3, events.size());
        assertInstanceOf(ObjectStoreEvent.ObjectDeleted.class, events.get(2));
    }

    // ── clearEvents tests ──

    @Test
    void clearEvents_removesEventsFromActiveS3Object() {
        var key = someKey();
        var object = S3Object.create(key, "STANDARD", Map.of(), null, someChecksum(), 100);
        assertEquals(1, object.domainEvents().size());
        var cleared = object.clearEvents();
        assertInstanceOf(ActiveS3Object.class, cleared);
        assertEquals(0, cleared.domainEvents().size());
    }

    @Test
    void clearEvents_removesEventsFromArchivedS3Object() {
        var key = someKey();
        var object = S3Object.create(key, "STANDARD", Map.of(), null, someChecksum(), 100);
        var archived = object.archive();
        assertEquals(2, archived.domainEvents().size());
        var cleared = archived.clearEvents();
        assertInstanceOf(ArchivedS3Object.class, cleared);
        assertEquals(0, cleared.domainEvents().size());
        assertFalse(((ArchivedS3Object) cleared).restored());
    }

    @Test
    void clearEvents_removesEventsFromLockedS3Object() {
        var key = someKey();
        var object = S3Object.create(key, "STANDARD", Map.of(), null, someChecksum(), 100);
        var lockConfig = ObjectLockConfiguration.of(
            ObjectLockConfiguration.ObjectLockMode.COMPLIANCE,
            ObjectLockConfiguration.RetentionPeriod.startingNow(Duration.ofDays(30)));
        var locked = object.applyLock(lockConfig);
        assertEquals(2, locked.domainEvents().size());
        var cleared = locked.clearEvents();
        assertInstanceOf(LockedS3Object.class, cleared);
        assertEquals(0, cleared.domainEvents().size());
    }

    @Test
    void clearEvents_removesEventsFromDeletedS3Object() {
        var key = someKey();
        var object = S3Object.create(key, "STANDARD", Map.of(), null, someChecksum(), 100);
        var deleted = object.delete();
        assertEquals(2, deleted.domainEvents().size());
        var cleared = deleted.clearEvents();
        assertInstanceOf(DeletedS3Object.class, cleared);
        assertEquals(0, cleared.domainEvents().size());
    }

    // ── Encryption preservation ──

    @Test
    void encryption_preservedThroughTransition() {
        var key = someKey();
        var encryption = EncryptionConfiguration.of(EncryptionAlgorithm.AWS_KMS,
            EncryptionKeyReference.of("kms-key-1"));
        var object = S3Object.create(key, "STANDARD", Map.of(), encryption, someChecksum(), 100);
        assertTrue(object.hasEncryption());
        assertEquals(EncryptionAlgorithm.AWS_KMS, object.encryption().algorithm());
        assertEquals("kms-key-1", object.encryption().keyReference().keyId());

        // Transition to Archived should preserve encryption
        var archived = object.archive();
        assertTrue(archived.hasEncryption());
        assertEquals(EncryptionAlgorithm.AWS_KMS, archived.encryption().algorithm());
        assertEquals("kms-key-1", archived.encryption().keyReference().keyId());
    }

    // ── Restore (event sourcing) tests ──

    @Test
    void restoreActive_returnsActiveS3Object() {
        var key = someKey();
        var now = ZonedDateTime.now();
        var object = S3Object.restoreActive(key, "STANDARD",
            Map.of("description", "test"),
            null, someChecksum(), 100, now, List.of());
        assertInstanceOf(ActiveS3Object.class, object);
        assertEquals("STANDARD", object.storageClass());
        assertEquals(100, object.size());
        assertTrue(object.hasChecksum());
    }

    @Test
    void restoreActive_withEncryption() {
        var key = someKey();
        var now = ZonedDateTime.now();
        var encryption = EncryptionConfiguration.of(EncryptionAlgorithm.AES256);
        var object = S3Object.restoreActive(key, "STANDARD",
            Map.of(), encryption, someChecksum(), 100, now, List.of());
        assertTrue(object.hasEncryption());
        assertEquals(EncryptionAlgorithm.AES256, object.encryption().algorithm());
    }

    @Test
    void restoreLocked_returnsLockedS3Object() {
        var key = someKey();
        var now = ZonedDateTime.now();
        var lockConfig = ObjectLockConfiguration.of(
            ObjectLockConfiguration.ObjectLockMode.COMPLIANCE,
            ObjectLockConfiguration.RetentionPeriod.startingNow(Duration.ofDays(30)));
        var object = S3Object.restoreLocked(key, "STANDARD",
            Map.of(), null, someChecksum(), 100, now,
            lockConfig, List.of());
        assertInstanceOf(LockedS3Object.class, object);
        assertEquals(ObjectLockConfiguration.ObjectLockMode.COMPLIANCE,
            object.lockConfiguration().mode());
    }

    @Test
    void restoreArchived_returnsArchivedS3Object() {
        var key = someKey();
        var now = ZonedDateTime.now();
        var object = S3Object.restoreArchived(key, "GLACIER",
            Map.of(), null, someChecksum(), 100, now,
            true, now.plusDays(3), List.of());
        assertInstanceOf(ArchivedS3Object.class, object);
        assertTrue(object.restored());
        assertNotNull(object.restoreExpiry());
    }

    @Test
    void restoreArchived_notRestored() {
        var key = someKey();
        var now = ZonedDateTime.now();
        var object = S3Object.restoreArchived(key, "GLACIER",
            Map.of(), null, someChecksum(), 100, now,
            false, null, List.of());
        assertInstanceOf(ArchivedS3Object.class, object);
        assertFalse(object.restored());
        assertNull(object.restoreExpiry());
    }

    @Test
    void restoreDeleted_returnsDeletedS3Object() {
        var key = someKey();
        var now = ZonedDateTime.now();
        var object = S3Object.restoreDeleted(key, null,
            Map.of(), now, List.of());
        assertInstanceOf(DeletedS3Object.class, object);
        assertNull(object.storageClass());
    }

    // ── DeletedS3Object — terminal, no transitions ──

    @Test
    void deletedS3Object_isTerminal() {
        var key = someKey();
        var object = S3Object.create(key, "STANDARD", Map.of(), null, someChecksum(), 100);
        var deleted = object.delete();
        // No transition methods — just verify it's a DeletedS3Object
        assertInstanceOf(DeletedS3Object.class, deleted);
        assertEquals(0L, deleted.size());
        assertNull(deleted.encryption());
        assertNull(deleted.checksum());
    }

    // ── User metadata ──

    @Test
    void userMetadata_null_becomesEmpty() {
        var key = someKey();
        var object = S3Object.create(key, "STANDARD", null, null, someChecksum(), 100);
        assertTrue(object.userMetadata().isEmpty());
    }

    @Test
    void userMetadata_preserved() {
        var key = someKey();
        var object = S3Object.create(key, "STANDARD",
            Map.of("description", "test", "project", "magrathea"),
            null, someChecksum(), 100);
        assertEquals(2, object.userMetadata().size());
        assertEquals("test", object.userMetadata().get("description"));
        assertEquals("magrathea", object.userMetadata().get("project"));
    }

    // ── Write state machine: CREATED → WRITING → WRITTEN → DELETED ──

    @Test
    void create_setsWriteStateToWritten() {
        var key = someKey();
        var object = S3Object.create(key, "STANDARD", Map.of(), null, someChecksum(), 100);
        assertEquals(WriteState.WRITTEN, object.writeState());
    }

    @Test
    void createPending_setsWriteStateToCreated() {
        var key = someKey();
        var object = S3Object.createPending(key, "STANDARD", Map.of(), null);
        assertInstanceOf(ActiveS3Object.class, object);
        assertEquals(WriteState.CREATED, object.writeState());
        assertFalse(object.hasChecksum());
        assertEquals(0L, object.size());
        assertTrue(object.domainEvents().isEmpty());
    }

    @Test
    void createPending_nullKey_throws() {
        assertThrows(NullPointerException.class,
            () -> S3Object.createPending(null, "STANDARD", Map.of(), null));
    }

    @Test
    void initiateUpload_fromCreated_transitionsToWriting() {
        var key = someKey();
        var object = S3Object.createPending(key, "STANDARD", Map.of(), null);
        var writing = object.initiateUpload();
        assertInstanceOf(ActiveS3Object.class, writing);
        assertEquals(WriteState.WRITING, writing.writeState());
        // Events should be unchanged (no new event emitted)
        assertTrue(writing.domainEvents().isEmpty());
    }

    @Test
    void initiateUpload_fromWritten_throws() {
        var key = someKey();
        var object = S3Object.create(key, "STANDARD", Map.of(), null, someChecksum(), 100);
        assertThrows(IllegalStateTransitionException.class, object::initiateUpload);
    }

    @Test
    void completeUpload_fromWriting_transitionsToWritten() {
        var key = someKey();
        var object = S3Object.createPending(key, "STANDARD", Map.of(), null);
        var writing = object.initiateUpload();
        var written = writing.completeUpload(someChecksum(), 100);
        assertInstanceOf(ActiveS3Object.class, written);
        assertEquals(WriteState.WRITTEN, written.writeState());
        assertTrue(written.hasChecksum());
        assertEquals(100, written.size());
        // Should emit ObjectCreated event
        assertEquals(1, written.domainEvents().size());
        assertInstanceOf(ObjectStoreEvent.ObjectCreated.class, written.domainEvents().get(0));
    }

    @Test
    void completeUpload_fromCreated_throws() {
        var key = someKey();
        var object = S3Object.createPending(key, "STANDARD", Map.of(), null);
        assertThrows(IllegalStateTransitionException.class,
            () -> object.completeUpload(someChecksum(), 100));
    }

    @Test
    void completeUpload_fromWritten_throws() {
        var key = someKey();
        var object = S3Object.create(key, "STANDARD", Map.of(), null, someChecksum(), 100);
        assertThrows(IllegalStateTransitionException.class,
            () -> object.completeUpload(someChecksum(), 100));
    }

    @Test
    void completeUpload_nullChecksum_throws() {
        var key = someKey();
        var object = S3Object.createPending(key, "STANDARD", Map.of(), null);
        var writing = object.initiateUpload();
        assertThrows(NullPointerException.class,
            () -> writing.completeUpload(null, 100));
    }

    @Test
    void completeUpload_negativeSize_throws() {
        var key = someKey();
        var object = S3Object.createPending(key, "STANDARD", Map.of(), null);
        var writing = object.initiateUpload();
        assertThrows(IllegalArgumentException.class,
            () -> writing.completeUpload(someChecksum(), -1));
    }

    @Test
    void delete_fromWritten_allowed() {
        var key = someKey();
        var object = S3Object.create(key, "STANDARD", Map.of(), null, someChecksum(), 100);
        var deleted = object.delete();
        assertInstanceOf(DeletedS3Object.class, deleted);
        assertEquals(WriteState.DELETED, deleted.writeState());
    }

    @Test
    void delete_fromCreated_allowed() {
        var key = someKey();
        var object = S3Object.createPending(key, "STANDARD", Map.of(), null);
        var deleted = ((ActiveS3Object) object).delete();
        assertInstanceOf(DeletedS3Object.class, deleted);
        assertEquals(WriteState.DELETED, deleted.writeState());
    }

    @Test
    void delete_fromWriting_throws() {
        var key = someKey();
        var object = S3Object.createPending(key, "STANDARD", Map.of(), null);
        var writing = object.initiateUpload();
        assertThrows(IllegalStateTransitionException.class,
            () -> ((ActiveS3Object) writing).delete());
    }

    // ── Write state preservation across state transitions ──

    @Test
    void writeState_preservedThroughArchive() {
        var key = someKey();
        var object = S3Object.create(key, "STANDARD", Map.of(), null, someChecksum(), 100);
        assertEquals(WriteState.WRITTEN, object.writeState());
        var archived = object.archive();
        assertEquals(WriteState.WRITTEN, archived.writeState());
    }

    @Test
    void writeState_preservedThroughApplyLock() {
        var key = someKey();
        var object = S3Object.create(key, "STANDARD", Map.of(), null, someChecksum(), 100);
        var lockConfig = ObjectLockConfiguration.of(
            ObjectLockConfiguration.ObjectLockMode.COMPLIANCE,
            ObjectLockConfiguration.RetentionPeriod.startingNow(Duration.ofDays(30)));
        var locked = object.applyLock(lockConfig);
        assertEquals(WriteState.WRITTEN, locked.writeState());
    }

    @Test
    void writeState_preservedThroughRemoveLegalHold() {
        var key = someKey();
        var object = S3Object.create(key, "STANDARD", Map.of(), null, someChecksum(), 100);
        var lockConfig = ObjectLockConfiguration.of(
            ObjectLockConfiguration.ObjectLockMode.GOVERNANCE,
            ObjectLockConfiguration.RetentionPeriod.startingNow(Duration.ofDays(30)),
            true);
        var locked = object.applyLock(lockConfig);
        var active = locked.removeLegalHold();
        assertEquals(WriteState.WRITTEN, active.writeState());
    }

    @Test
    void writeState_preservedThroughClearEvents() {
        var key = someKey();
        var object = S3Object.create(key, "STANDARD", Map.of(), null, someChecksum(), 100);
        assertEquals(WriteState.WRITTEN, object.writeState());
        var cleared = object.clearEvents();
        assertEquals(WriteState.WRITTEN, cleared.writeState());
    }
}
