package com.example.magrathea.objectstore.domain.aggregate;

import com.example.magrathea.objectstore.domain.event.ObjectStoreEvent;
import com.example.magrathea.objectstore.domain.valueobject.ContentDescriptor;
import com.example.magrathea.objectstore.domain.valueobject.EncryptionConfiguration;
import com.example.magrathea.objectstore.domain.valueobject.ObjectKey;
import com.example.magrathea.objectstore.domain.valueobject.ObjectLockConfiguration;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * Active state — object has content and is fully operational.
 * Valid transitions:
 * <ul>
 *   <li>{@link #applyLock(ObjectLockConfiguration)} → {@code LockedS3Object}</li>
 *   <li>{@link #archive()} → {@code ArchivedS3Object}</li>
 *   <li>{@link #delete()} → {@code DeletedS3Object}</li>
 * </ul>
 */
public final class ActiveS3Object extends S3Object {

    ActiveS3Object(S3Object.Id id, Bucket.Id bucketId, ObjectKey key, String storageClass,
                   Map<String, String> userMetadata, ContentDescriptor contentDescriptor,
                   EncryptionConfiguration encryption, String etag, String versionId,
                   List<ObjectStoreEvent> events) {
        super(id, bucketId, key, storageClass, userMetadata, contentDescriptor,
            encryption, etag, versionId, events);
    }

    /**
     * Apply an object lock configuration, transitioning to {@code LockedS3Object}.
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
     * Archive this object, transitioning to {@code ArchivedS3Object}.
     *
     * @return a new {@code ArchivedS3Object} with an {@code ObjectArchived} event
     */
    public ArchivedS3Object archive() {
        var newEvents = appendEvent(domainEvents(),
            new ObjectStoreEvent.ObjectArchived(id(), bucketId(), storageClass(), Instant.now()));
        return new ArchivedS3Object(id(), bucketId(), key(), storageClass(), userMetadata(),
            contentDescriptor(), encryption(), etag(), versionId(),
            null, newEvents);
    }

    /**
     * Delete this object, transitioning to terminal {@code DeletedS3Object}.
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
        return new ActiveS3Object(id(), bucketId(), key(), storageClass(), userMetadata(),
            contentDescriptor(), encryption(), etag(), versionId(), List.of());
    }

    @Override
    public String toString() {
        return "ActiveS3Object[id=" + id() + ", key=" + key() + "]";
    }
}
