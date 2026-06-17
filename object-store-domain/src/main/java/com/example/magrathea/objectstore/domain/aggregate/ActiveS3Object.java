package com.example.magrathea.objectstore.domain.aggregate;

import com.example.magrathea.objectstore.domain.IllegalStateTransitionException;
import com.example.magrathea.objectstore.domain.event.ObjectStoreEvent;
import com.example.magrathea.objectstore.domain.valueobject.EncryptionConfiguration;
import com.example.magrathea.objectstore.domain.valueobject.ObjectChecksum;
import com.example.magrathea.objectstore.domain.valueobject.ObjectKey;
import com.example.magrathea.objectstore.domain.valueobject.ObjectLockConfiguration;

import java.time.ZonedDateTime;
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

    ActiveS3Object(ObjectKey key, String storageClass,
                   Map<String, String> userMetadata, EncryptionConfiguration encryption,
                   ObjectChecksum checksum, long size, ZonedDateTime createdAt,
                   WriteState writeState, List<ObjectStoreEvent> events,
                   String etag, Map<String, String> objectTags) {
        super(key, storageClass, userMetadata, encryption, checksum, size, createdAt,
              writeState, events, etag, objectTags);
    }

    /**
     * Apply an object lock configuration, transitioning to {@code LockedS3Object}.
     *
     * @param lockConfiguration the lock configuration to apply
     * @return a new {@code LockedS3Object} with an {@code ObjectLockConfigured} event
     * @throws IllegalStateTransitionException if lockConfiguration is null
     */
    public LockedS3Object applyLock(ObjectLockConfiguration lockConfiguration) {
        if (lockConfiguration == null) {
            throw new IllegalStateTransitionException(
                "Cannot apply lock: lockConfiguration must not be null");
        }
        var newEvents = appendEvent(domainEvents(),
            new ObjectStoreEvent.ObjectLockConfigured(key(), lockConfiguration.mode(),
                lockConfiguration.retention().duration(), ZonedDateTime.now()));
        return new LockedS3Object(key(), storageClass(), userMetadata(),
            encryption(), checksum(), size(), createdAt(),
            lockConfiguration, writeState(), newEvents, etag(), objectTags());
    }

    /**
     * Archive this object, transitioning to {@code ArchivedS3Object}.
     *
     * @return a new {@code ArchivedS3Object} with an {@code ObjectArchived} event
     */
    public ArchivedS3Object archive() {
        var newEvents = appendEvent(domainEvents(),
            new ObjectStoreEvent.ObjectArchived(key(), ZonedDateTime.now()));
        return new ArchivedS3Object(key(), storageClass(), userMetadata(),
            encryption(), checksum(), size(), createdAt(),
            false, null, writeState(), newEvents, etag(), objectTags());
    }

    /**
     * Delete this object, transitioning to terminal {@code DeletedS3Object}.
     *
     * @return a new {@code DeletedS3Object} with an {@code ObjectDeleted} event
     */
    public DeletedS3Object delete() {
        validateWriteStateForDelete();
        var newEvents = appendEvent(domainEvents(),
            new ObjectStoreEvent.ObjectDeleted(key(), ZonedDateTime.now()));
        return new DeletedS3Object(key(), storageClass(), userMetadata(),
            createdAt(), WriteState.DELETED, newEvents, etag(), objectTags());
    }

    /**
     * Returns a new {@code ActiveS3Object} with the given ETag, all other fields unchanged,
     * events preserved.
     *
     * @param etag the ETag value (may be null to indicate not yet computed)
     * @return new instance with the given ETag
     */
    public ActiveS3Object withEtag(String etag) {
        return new ActiveS3Object(key(), storageClass(), userMetadata(),
            encryption(), checksum(), size(), createdAt(),
            writeState(), domainEvents(), etag, objectTags());
    }

    /**
     * Returns a new {@code ActiveS3Object} with the given object tags (copy), all other fields
     * unchanged, events preserved.
     *
     * @param tags the object tags map (may be null; treated as empty)
     * @return new instance with the given tags
     */
    public ActiveS3Object withObjectTags(Map<String, String> tags) {
        var newTags = tags != null ? Map.copyOf(tags) : Map.<String, String>of();
        return new ActiveS3Object(key(), storageClass(), userMetadata(),
            encryption(), checksum(), size(), createdAt(),
            writeState(), domainEvents(), etag(), newTags);
    }

    @Override
    public S3Object clearEvents() {
        return new ActiveS3Object(key(), storageClass(), userMetadata(),
            encryption(), checksum(), size(), createdAt(),
            writeState(), List.of(), etag(), objectTags());
    }

    @Override
    protected S3Object withWriteState(WriteState newState) {
        return new ActiveS3Object(key(), storageClass(), userMetadata(),
            encryption(), checksum(), size(), createdAt(),
            newState, domainEvents(), etag(), objectTags());
    }

    @Override
    protected S3Object withWriteStateAndContent(ObjectChecksum checksum, long size, List<ObjectStoreEvent> events) {
        return new ActiveS3Object(key(), storageClass(), userMetadata(),
            encryption(), checksum, size, createdAt(),
            WriteState.WRITTEN, events, etag(), objectTags());
    }

    @Override
    public String toString() {
        return "ActiveS3Object[key=" + key() + ",writeState=" + writeState() + "]";
    }
}
