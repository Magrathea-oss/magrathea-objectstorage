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
 * Locked state — object has an active object lock configuration.
 * Valid transitions:
 * <ul>
 *   <li>{@link #archive()} → {@code ArchivedS3Object}</li>
 *   <li>{@link #delete()} → {@code DeletedS3Object}</li>
 * </ul>
 */
public final class LockedS3Object extends S3Object {

    private final ObjectLockConfiguration lockConfiguration;

    LockedS3Object(S3Object.Id id, Bucket.Id bucketId, ObjectKey key, String storageClass,
                   Map<String, String> userMetadata, ContentDescriptor contentDescriptor,
                   EncryptionConfiguration encryption, String etag, String versionId,
                   ObjectLockConfiguration lockConfiguration,
                   List<ObjectStoreEvent> events) {
        super(id, bucketId, key, storageClass, userMetadata, contentDescriptor,
            encryption, etag, versionId, events);
        this.lockConfiguration = Objects.requireNonNull(lockConfiguration,
            "lockConfiguration must not be null");
    }

    /** Returns the lock configuration. */
    public ObjectLockConfiguration lockConfiguration() { return lockConfiguration; }

    /**
     * Archive this locked object, transitioning to {@code ArchivedS3Object}.
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
     * Delete this locked object, transitioning to terminal {@code DeletedS3Object}.
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
        return new LockedS3Object(id(), bucketId(), key(), storageClass(), userMetadata(),
            contentDescriptor(), encryption(), etag(), versionId(),
            lockConfiguration, List.of());
    }

    @Override
    public String toString() {
        return "LockedS3Object[id=" + id() + ", key=" + key() + "]";
    }
}
