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
                   List<ObjectStoreEvent> events) {
        super(key, storageClass, userMetadata, encryption, checksum, size, createdAt, events);
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
            lockConfiguration, newEvents);
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
            false, null, newEvents);
    }

    /**
     * Delete this object, transitioning to terminal {@code DeletedS3Object}.
     *
     * @return a new {@code DeletedS3Object} with an {@code ObjectDeleted} event
     */
    public DeletedS3Object delete() {
        var newEvents = appendEvent(domainEvents(),
            new ObjectStoreEvent.ObjectDeleted(key(), ZonedDateTime.now()));
        return new DeletedS3Object(key(), storageClass(), userMetadata(),
            createdAt(), newEvents);
    }

    @Override
    public S3Object clearEvents() {
        return new ActiveS3Object(key(), storageClass(), userMetadata(),
            encryption(), checksum(), size(), createdAt(), List.of());
    }

    @Override
    public String toString() {
        return "ActiveS3Object[key=" + key() + "]";
    }
}
