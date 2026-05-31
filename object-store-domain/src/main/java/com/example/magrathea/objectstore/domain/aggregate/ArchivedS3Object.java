package com.example.magrathea.objectstore.domain.aggregate;

import com.example.magrathea.objectstore.domain.event.ObjectStoreEvent;
import com.example.magrathea.objectstore.domain.valueobject.EncryptionConfiguration;
import com.example.magrathea.objectstore.domain.valueobject.ObjectChecksum;
import com.example.magrathea.objectstore.domain.valueobject.ObjectKey;

import java.time.ZonedDateTime;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * Archived state — object has been archived to Glacier/Deep Archive.
 * Valid transitions:
 * <ul>
 *   <li>{@link #delete()} → {@code DeletedS3Object}</li>
 * </ul>
 * NO restore() — restore is handled via event sourcing rebuild.
 */
public final class ArchivedS3Object extends S3Object {

    private final boolean restored;
    private final ZonedDateTime restoreExpiry;

    ArchivedS3Object(ObjectKey key, String storageClass,
                     Map<String, String> userMetadata, EncryptionConfiguration encryption,
                     ObjectChecksum checksum, long size, ZonedDateTime createdAt,
                     boolean restored, ZonedDateTime restoreExpiry,
                     List<ObjectStoreEvent> events) {
        super(key, storageClass, userMetadata, encryption, checksum, size, createdAt, events);
        this.restored = restored;
        this.restoreExpiry = restoreExpiry;
    }

    /** Returns {@code true} if this archived object has been temporarily restored. */
    public boolean restored() { return restored; }

    /** Returns the restore expiry timestamp, or {@code null} if not restored. */
    public ZonedDateTime restoreExpiry() { return restoreExpiry; }

    /**
     * Delete this archived object, transitioning to terminal {@code DeletedS3Object}.
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
        return new ArchivedS3Object(key(), storageClass(), userMetadata(),
            encryption(), checksum(), size(), createdAt(),
            restored, restoreExpiry, List.of());
    }

    @Override
    public String toString() {
        return "ArchivedS3Object[key=" + key() + "]";
    }
}
