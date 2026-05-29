package com.example.magrathea.objectstore.domain.aggregate;

import com.example.magrathea.objectstore.domain.event.ObjectStoreEvent;
import com.example.magrathea.objectstore.domain.valueobject.ContentDescriptor;
import com.example.magrathea.objectstore.domain.valueobject.EncryptionConfiguration;
import com.example.magrathea.objectstore.domain.valueobject.ObjectKey;
import com.example.magrathea.objectstore.domain.valueobject.RestoreConfiguration;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * Archived state — object has been archived to Glacier/Deep Archive.
 * Valid transitions:
 * <ul>
 *   <li>{@link #restore(RestoreConfiguration)} → {@code RestoredS3Object}</li>
 * </ul>
 */
public final class ArchivedS3Object extends S3Object {

    private final RestoreConfiguration restoreConfiguration;

    ArchivedS3Object(S3Object.Id id, Bucket.Id bucketId, ObjectKey key, String storageClass,
                     Map<String, String> userMetadata, ContentDescriptor contentDescriptor,
                     EncryptionConfiguration encryption, String etag, String versionId,
                     RestoreConfiguration restoreConfiguration,
                     List<ObjectStoreEvent> events) {
        super(id, bucketId, key, storageClass, userMetadata, contentDescriptor,
            encryption, etag, versionId, events);
        this.restoreConfiguration = restoreConfiguration;
    }

    /** Returns the restore configuration, or {@code null} if not yet set. */
    public RestoreConfiguration restoreConfiguration() { return restoreConfiguration; }

    /**
     * Restore this archived object, transitioning to {@code RestoredS3Object}.
     *
     * @param restoreConfig the restore configuration
     * @return a new {@code RestoredS3Object} with an {@code ObjectRestored} event
     */
    public RestoredS3Object restore(RestoreConfiguration restoreConfig) {
        Objects.requireNonNull(restoreConfig, "restoreConfig must not be null");
        var newEvents = appendEvent(domainEvents(),
            new ObjectStoreEvent.ObjectRestored(id(), bucketId(), restoreConfig.tier(), Instant.now()));
        return new RestoredS3Object(id(), bucketId(), key(), storageClass(), userMetadata(),
            contentDescriptor(), encryption(), etag(), versionId(),
            restoreConfig, newEvents);
    }

    @Override
    public S3Object clearEvents() {
        return new ArchivedS3Object(id(), bucketId(), key(), storageClass(), userMetadata(),
            contentDescriptor(), encryption(), etag(), versionId(),
            restoreConfiguration, List.of());
    }

    @Override
    public String toString() {
        return "ArchivedS3Object[id=" + id() + ", key=" + key() + "]";
    }
}
