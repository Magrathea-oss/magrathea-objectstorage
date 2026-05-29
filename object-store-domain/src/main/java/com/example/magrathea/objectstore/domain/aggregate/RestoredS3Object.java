package com.example.magrathea.objectstore.domain.aggregate;

import com.example.magrathea.objectstore.domain.event.ObjectStoreEvent;
import com.example.magrathea.objectstore.domain.valueobject.ContentDescriptor;
import com.example.magrathea.objectstore.domain.valueobject.EncryptionConfiguration;
import com.example.magrathea.objectstore.domain.valueobject.ObjectKey;
import com.example.magrathea.objectstore.domain.valueobject.ObjectLockConfiguration;
import com.example.magrathea.objectstore.domain.valueobject.RestoreConfiguration;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * Restored state — object has been restored from archive.
 * Valid transitions:
 * <ul>
 *   <li>{@link #applyLock(ObjectLockConfiguration)} → {@code LockedS3Object}</li>
 *   <li>{@link #delete()} → {@code DeletedS3Object}</li>
 * </ul>
 */
public final class RestoredS3Object extends S3Object {

    private final RestoreConfiguration restoreConfiguration;

    RestoredS3Object(S3Object.Id id, Bucket.Id bucketId, ObjectKey key, String storageClass,
                     Map<String, String> userMetadata, ContentDescriptor contentDescriptor,
                     EncryptionConfiguration encryption, String etag, String versionId,
                     RestoreConfiguration restoreConfiguration,
                     List<ObjectStoreEvent> events) {
        super(id, bucketId, key, storageClass, userMetadata, contentDescriptor,
            encryption, etag, versionId, events);
        this.restoreConfiguration = Objects.requireNonNull(restoreConfiguration,
            "restoreConfiguration must not be null");
    }

    /** Returns the restore configuration. */
    public RestoreConfiguration restoreConfiguration() { return restoreConfiguration; }

    /**
     * Apply an object lock configuration to this restored object,
     * transitioning to {@code LockedS3Object}.
     *
     * @param lockConfiguration the lock configuration to apply
     * @return a new {@code LockedS3Object} with an {@code ObjectLockConfigured} event
     */
    public LockedS3Object applyLock(ObjectLockConfiguration lockConfiguration) {
        Objects.requireNonNull(lockConfiguration, "lockConfiguration must not be null");
        var newEvents = appendEvent(domainEvents(),
            new ObjectStoreEvent.ObjectLockConfigured(id(), lockConfiguration.mode(),
                lockConfiguration.retention().duration(), Instant.now()));
        return new LockedS3Object(id(), bucketId(), key(), storageClass(), userMetadata(),
            contentDescriptor(), encryption(), etag(), versionId(),
            lockConfiguration, newEvents);
    }

    /**
     * Delete this restored object, transitioning to terminal {@code DeletedS3Object}.
     *
     * @return a new {@code DeletedS3Object} with an {@code ObjectDeleted} event
     */
    public DeletedS3Object delete() {
        var newEvents = appendEvent(domainEvents(),
            new ObjectStoreEvent.ObjectDeleted(id(), bucketId(), Instant.now()));
        return new DeletedS3Object(id(), bucketId(), key(), storageClass(), userMetadata(), newEvents);
    }

    @Override
    public S3Object clearEvents() {
        return new RestoredS3Object(id(), bucketId(), key(), storageClass(), userMetadata(),
            contentDescriptor(), encryption(), etag(), versionId(),
            restoreConfiguration, List.of());
    }

    @Override
    public String toString() {
        return "RestoredS3Object[id=" + id() + ", key=" + key() + "]";
    }
}
