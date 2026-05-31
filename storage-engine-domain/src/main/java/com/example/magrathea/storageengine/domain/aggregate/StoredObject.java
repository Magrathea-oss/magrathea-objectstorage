package com.example.magrathea.storageengine.domain.aggregate;

import com.example.magrathea.storageengine.domain.valueobject.BucketRef;
import com.example.magrathea.storageengine.domain.valueobject.ManifestId;
import com.example.magrathea.storageengine.domain.valueobject.ObjectId;
import com.example.magrathea.storageengine.domain.valueobject.StorageClassId;
import com.example.magrathea.storageengine.domain.valueobject.VersionId;
import com.example.magrathea.storageengine.domain.valueobject.VirtualDevice;

import java.time.ZonedDateTime;
import java.util.Objects;

public class StoredObject {
    private final ObjectId objectId;
    private final VersionId versionId;
    private final BucketRef bucketRef;
    private final StorageClassId storageClassId;
    private ManifestId manifestId;
    private VirtualDevice targetDevice;
    private ObjectState state;
    private ZonedDateTime createdAt;
    private ZonedDateTime lastModified;

    private StoredObject(
            ObjectId objectId,
            VersionId versionId,
            BucketRef bucketRef,
            StorageClassId storageClassId,
            ManifestId manifestId,
            VirtualDevice targetDevice,
            ObjectState state,
            ZonedDateTime createdAt,
            ZonedDateTime lastModified) {
        this.objectId = Objects.requireNonNull(objectId, "objectId must not be null");
        this.versionId = Objects.requireNonNull(versionId, "versionId must not be null");
        this.bucketRef = Objects.requireNonNull(bucketRef, "bucketRef must not be null");
        this.storageClassId = Objects.requireNonNull(storageClassId, "storageClassId must not be null");
        this.manifestId = manifestId; // nullable
        this.targetDevice = Objects.requireNonNull(targetDevice, "targetDevice must not be null");
        this.state = Objects.requireNonNull(state, "state must not be null");
        this.createdAt = Objects.requireNonNull(createdAt, "createdAt must not be null");
        this.lastModified = Objects.requireNonNull(lastModified, "lastModified must not be null");
    }

    public static StoredObject create(
            ObjectId objectId,
            VersionId versionId,
            BucketRef bucketRef,
            StorageClassId storageClassId,
            VirtualDevice targetDevice) {
        ZonedDateTime now = ZonedDateTime.now();
        return new StoredObject(
                objectId,
                versionId,
                bucketRef,
                storageClassId,
                null, // manifestId initially null
                targetDevice,
                ObjectState.CREATING,
                now,
                now);
    }

    public static StoredObject restore(
            ObjectId objectId,
            VersionId versionId,
            BucketRef bucketRef,
            StorageClassId storageClassId,
            ManifestId manifestId,
            VirtualDevice targetDevice,
            ObjectState state,
            ZonedDateTime createdAt,
            ZonedDateTime lastModified) {
        return new StoredObject(
                objectId, versionId, bucketRef, storageClassId,
                manifestId, targetDevice, state, createdAt, lastModified);
    }

    public void attachManifest(ManifestId manifestId) {
        Objects.requireNonNull(manifestId, "manifestId must not be null");
        if (state != ObjectState.CREATING) {
            throw new IllegalStateException(
                    "Cannot attach manifest in state " + state + "; expected CREATING");
        }
        this.manifestId = manifestId;
        this.state = ObjectState.STORED;
        this.lastModified = ZonedDateTime.now();
    }

    public ObjectId objectId() { return objectId; }
    public VersionId versionId() { return versionId; }
    public BucketRef bucketRef() { return bucketRef; }
    public StorageClassId storageClassId() { return storageClassId; }
    public ManifestId manifestId() { return manifestId; }
    public VirtualDevice targetDevice() { return targetDevice; }
    public ObjectState state() { return state; }
    public ZonedDateTime createdAt() { return createdAt; }
    public ZonedDateTime lastModified() { return lastModified; }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof StoredObject that)) return false;
        return objectId.equals(that.objectId)
                && versionId.equals(that.versionId)
                && bucketRef.equals(that.bucketRef);
    }

    @Override
    public int hashCode() {
        return 31 * objectId.hashCode() + 31 * versionId.hashCode() + bucketRef.hashCode();
    }

    @Override
    public String toString() {
        return "StoredObject[" + objectId.value() + ":" + versionId.value() + "@" + bucketRef.bucketId().value() + "]";
    }
}
