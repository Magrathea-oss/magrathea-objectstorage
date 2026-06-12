package com.example.magrathea.storageengine.domain.aggregate;

import com.example.magrathea.storageengine.domain.TestFixtures;
import com.example.magrathea.storageengine.domain.valueobject.BucketRef;
import com.example.magrathea.storageengine.domain.valueobject.ManifestId;
import com.example.magrathea.storageengine.domain.valueobject.ObjectId;
import com.example.magrathea.storageengine.domain.valueobject.VersionId;
import com.example.magrathea.storageengine.domain.valueobject.VirtualDevice;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.ZonedDateTime;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Unit tests for {@link StoredObject} aggregate lifecycle.
 * Pure JUnit 5 — no Spring, no Mockito, no reactive imports.
 */
class StoredObjectTest {

    private ObjectId objectId;
    private VersionId versionId;
    private BucketRef bucketRef;
    private VirtualDevice targetDevice;

    @BeforeEach
    void setUp() {
        objectId = TestFixtures.anObjectId();
        versionId = TestFixtures.aVersionId();
        bucketRef = TestFixtures.aBucketRef();
        targetDevice = TestFixtures.aBucketDevice(bucketRef);
    }

    // -------------------------------------------------------------------------
    // Factory: create()
    // -------------------------------------------------------------------------

    @Test
    void create_setsStateToCreating() {
        StoredObject obj = StoredObject.create(objectId, versionId, bucketRef,
                TestFixtures.aStorageClassId(), targetDevice);
        assertEquals(ObjectState.CREATING, obj.state());
    }

    @Test
    void create_manifestIdIsNull() {
        StoredObject obj = StoredObject.create(objectId, versionId, bucketRef,
                TestFixtures.aStorageClassId(), targetDevice);
        assertNull(obj.manifestId(), "manifestId must be null immediately after create()");
    }

    @Test
    void create_timestampsAreSet() {
        ZonedDateTime before = ZonedDateTime.now();
        StoredObject obj = StoredObject.create(objectId, versionId, bucketRef,
                TestFixtures.aStorageClassId(), targetDevice);
        ZonedDateTime after = ZonedDateTime.now();

        assertNotNull(obj.createdAt());
        assertNotNull(obj.lastModified());
        // createdAt must be within the test window
        assertFalse(obj.createdAt().isBefore(before),
                "createdAt must not be before test start");
        assertFalse(obj.createdAt().isAfter(after),
                "createdAt must not be after test end");
    }

    // -------------------------------------------------------------------------
    // Lifecycle: attachManifest()
    // -------------------------------------------------------------------------

    @Test
    void attachManifest_fromCreating_transitionsToStored() {
        StoredObject obj = StoredObject.create(objectId, versionId, bucketRef,
                TestFixtures.aStorageClassId(), targetDevice);
        obj.attachManifest(TestFixtures.aManifestId());
        assertEquals(ObjectState.STORED, obj.state());
    }

    @Test
    void attachManifest_fromCreating_setsManifestId() {
        ManifestId manifestId = TestFixtures.aManifestId();
        StoredObject obj = StoredObject.create(objectId, versionId, bucketRef,
                TestFixtures.aStorageClassId(), targetDevice);
        obj.attachManifest(manifestId);
        assertEquals(manifestId, obj.manifestId());
    }

    @Test
    void attachManifest_fromStored_throwsIllegalStateException() {
        StoredObject obj = StoredObject.create(objectId, versionId, bucketRef,
                TestFixtures.aStorageClassId(), targetDevice);
        obj.attachManifest(TestFixtures.aManifestId());
        // Now in STORED — attaching again must fail
        assertThrows(IllegalStateException.class, () -> obj.attachManifest(TestFixtures.aManifestId()));
    }

    @Test
    void attachManifest_fromDeleted_throwsIllegalStateException() {
        StoredObject obj = storedObject(); // CREATING → STORED
        obj.markDeleted();                  // STORED → DELETED
        assertThrows(IllegalStateException.class, () -> obj.attachManifest(TestFixtures.aManifestId()));
    }

    @Test
    void attachManifest_nullManifestId_throwsNullPointerException() {
        StoredObject obj = StoredObject.create(objectId, versionId, bucketRef,
                TestFixtures.aStorageClassId(), targetDevice);
        assertThrows(NullPointerException.class, () -> obj.attachManifest(null));
    }

    // -------------------------------------------------------------------------
    // Lifecycle: markDeleted()
    // -------------------------------------------------------------------------

    @Test
    void markDeleted_fromStored_transitionsToDeleted() {
        StoredObject obj = storedObject();
        obj.markDeleted();
        assertEquals(ObjectState.DELETED, obj.state());
    }

    @Test
    void markDeleted_fromCreating_throwsIllegalStateException() {
        StoredObject obj = StoredObject.create(objectId, versionId, bucketRef,
                TestFixtures.aStorageClassId(), targetDevice);
        assertThrows(IllegalStateException.class, obj::markDeleted);
    }

    @Test
    void markDeleted_fromDeleted_throwsIllegalStateException() {
        StoredObject obj = storedObject();
        obj.markDeleted(); // STORED → DELETED
        assertThrows(IllegalStateException.class, obj::markDeleted); // second call must fail
    }

    // -------------------------------------------------------------------------
    // Factory: restore()
    // -------------------------------------------------------------------------

    @Test
    void restore_setsAllFields() {
        ManifestId manifestId = TestFixtures.aManifestId();
        ZonedDateTime created = ZonedDateTime.now().minusDays(1);
        ZonedDateTime modified = ZonedDateTime.now();

        StoredObject obj = StoredObject.restore(
                objectId, versionId, bucketRef,
                TestFixtures.aStorageClassId(),
                manifestId, targetDevice,
                ObjectState.STORED, created, modified);

        assertEquals(objectId, obj.objectId());
        assertEquals(versionId, obj.versionId());
        assertEquals(bucketRef, obj.bucketRef());
        assertEquals(manifestId, obj.manifestId());
        assertEquals(ObjectState.STORED, obj.state());
        assertEquals(created, obj.createdAt());
        assertEquals(modified, obj.lastModified());
    }

    // -------------------------------------------------------------------------
    // equals() — based on objectId + versionId + bucketRef only
    // -------------------------------------------------------------------------

    @Test
    void equals_sameObjectIdVersionIdBucketRef_isEqual() {
        StoredObject a = StoredObject.create(objectId, versionId, bucketRef,
                TestFixtures.aStorageClassId(), targetDevice);
        StoredObject b = StoredObject.restore(
                objectId, versionId, bucketRef,
                TestFixtures.aStorageClassId(),
                TestFixtures.aManifestId(), targetDevice,
                ObjectState.STORED,
                ZonedDateTime.now().minusDays(1), ZonedDateTime.now());
        assertEquals(a, b, "Objects with same objectId, versionId and bucketRef must be equal");
    }

    @Test
    void equals_differentObjectId_isNotEqual() {
        StoredObject a = StoredObject.create(objectId, versionId, bucketRef,
                TestFixtures.aStorageClassId(), targetDevice);
        StoredObject b = StoredObject.create(TestFixtures.anObjectId("other"),
                versionId, bucketRef, TestFixtures.aStorageClassId(), targetDevice);
        assertNotEquals(a, b);
    }

    // -------------------------------------------------------------------------
    // Helpers
    // -------------------------------------------------------------------------

    /** Creates a StoredObject that has been transitioned to STORED state. */
    private StoredObject storedObject() {
        StoredObject obj = StoredObject.create(objectId, versionId, bucketRef,
                TestFixtures.aStorageClassId(), targetDevice);
        obj.attachManifest(TestFixtures.aManifestId());
        return obj;
    }
}
