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
 * Locked state — object has an active object lock configuration.
 * Valid transitions:
 * <ul>
 *   <li>{@link #removeLegalHold()} → {@code ActiveS3Object}</li>
 *   <li>{@link #archive()} → {@code ArchivedS3Object}</li>
 * </ul>
 * NOTE: No delete() — locked objects cannot be deleted until the lock expires or legal hold is removed.
 */
public final class LockedS3Object extends S3Object {

    private final ObjectLockConfiguration lockConfiguration;

    LockedS3Object(ObjectKey key, String storageClass,
                   Map<String, String> userMetadata, EncryptionConfiguration encryption,
                   ObjectChecksum checksum, long size, ZonedDateTime createdAt,
                   ObjectLockConfiguration lockConfiguration,
                   WriteState writeState, List<ObjectStoreEvent> events,
                   String etag, Map<String, String> objectTags) {
        super(key, storageClass, userMetadata, encryption, checksum, size, createdAt,
              writeState, events, etag, objectTags);
        this.lockConfiguration = Objects.requireNonNull(lockConfiguration,
            "lockConfiguration must not be null");
    }

    /** Returns the lock configuration. */
    public ObjectLockConfiguration lockConfiguration() { return lockConfiguration; }

    /**
     * Remove the legal hold from this locked object, transitioning to {@code ActiveS3Object}.
     * Only allowed when the lock configuration is a legal hold (not COMPLIANCE retention).
     *
     * @return a new {@code ActiveS3Object} with a {@code LegalHoldRemoved} event
     * @throws IllegalStateTransitionException if legal hold is not active
     */
    public ActiveS3Object removeLegalHold() {
        if (!lockConfiguration.legalHold()) {
            throw new IllegalStateTransitionException(
                "Cannot remove legal hold: lock configuration does not have legal hold enabled");
        }
        var newEvents = appendEvent(domainEvents(),
            new ObjectStoreEvent.LegalHoldRemoved(key(), ZonedDateTime.now()));
        return new ActiveS3Object(key(), storageClass(), userMetadata(),
            encryption(), checksum(), size(), createdAt(),
            writeState(), newEvents, etag(), objectTags());
    }

    /**
     * Archive this locked object, transitioning to {@code ArchivedS3Object}.
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

    @Override
    public S3Object clearEvents() {
        return new LockedS3Object(key(), storageClass(), userMetadata(),
            encryption(), checksum(), size(), createdAt(),
            lockConfiguration, writeState(), List.of(), etag(), objectTags());
    }

    @Override
    protected S3Object withWriteState(WriteState newState) {
        return new LockedS3Object(key(), storageClass(), userMetadata(),
            encryption(), checksum(), size(), createdAt(),
            lockConfiguration, newState, domainEvents(), etag(), objectTags());
    }

    @Override
    protected S3Object withWriteStateAndContent(ObjectChecksum checksum, long size, List<ObjectStoreEvent> events) {
        return new LockedS3Object(key(), storageClass(), userMetadata(),
            encryption(), checksum, size, createdAt(),
            lockConfiguration, WriteState.WRITTEN, events, etag(), objectTags());
    }

    @Override
    public String toString() {
        return "LockedS3Object[key=" + key() + ",writeState=" + writeState() + "]";
    }
}
