package com.example.magrathea.objectstore.domain.aggregate;

import com.example.magrathea.objectstore.domain.event.ObjectStoreEvent;
import com.example.magrathea.objectstore.domain.valueobject.ContentDescriptor;
import com.example.magrathea.objectstore.domain.valueobject.EncryptionConfiguration;
import com.example.magrathea.objectstore.domain.valueobject.ObjectKey;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * Initial state — object has been created but no content is attached yet.
 * Valid transitions:
 * <ul>
 *   <li>{@link #attachContent(ContentDescriptor)} → {@code ActiveS3Object}</li>
 * </ul>
 */
public final class CreatingS3Object extends S3Object {

    CreatingS3Object(S3Object.Id id, Bucket.Id bucketId, ObjectKey key, String storageClass,
                     Map<String, String> userMetadata, ContentDescriptor contentDescriptor,
                     EncryptionConfiguration encryption, String etag, String versionId,
                     List<ObjectStoreEvent> events) {
        super(id, bucketId, key, storageClass, userMetadata, contentDescriptor,
            encryption, etag, versionId, events);
    }

    /**
     * Attach content metadata to this object, transitioning to {@code ActiveS3Object}.
     *
     * @param descriptor the content descriptor (size, md5Hash, contentId, checksums)
     * @return a new {@code ActiveS3Object} with a {@code ContentDescriptorCreated} event
     */
    public ActiveS3Object attachContent(ContentDescriptor descriptor) {
        Objects.requireNonNull(descriptor, "descriptor must not be null");
        var newEvents = appendEvent(domainEvents(),
            new ObjectStoreEvent.ContentDescriptorCreated(id(), descriptor, Instant.now()));
        return new ActiveS3Object(id(), bucketId(), key(), storageClass(), userMetadata(),
            descriptor, encryption(), etag(), versionId(), newEvents);
    }

    /**
     * Delete this object before content is attached, transitioning to terminal {@code DeletedS3Object}.
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
        return new CreatingS3Object(id(), bucketId(), key(), storageClass(), userMetadata(),
            contentDescriptor(), encryption(), etag(), versionId(), List.of());
    }

    @Override
    public String toString() {
        return "CreatingS3Object[id=" + id() + ", key=" + key() + "]";
    }
}
