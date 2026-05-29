package com.example.magrathea.objectstore.domain.aggregate;

import com.example.magrathea.objectstore.domain.event.ObjectStoreEvent;
import com.example.magrathea.objectstore.domain.valueobject.ObjectKey;

import java.util.List;
import java.util.Map;

/**
 * Terminal state — object has been deleted.
 * No valid transitions — this is a final state.
 */
public final class DeletedS3Object extends S3Object {

    DeletedS3Object(S3Object.Id id, Bucket.Id bucketId, ObjectKey key, String storageClass,
                    Map<String, String> userMetadata,
                    List<ObjectStoreEvent> events) {
        super(id, bucketId, key, storageClass, userMetadata, null,
            null, null, null, events);
    }

    @Override
    public S3Object clearEvents() {
        return new DeletedS3Object(id(), bucketId(), key(), storageClass(), userMetadata(), List.of());
    }

    @Override
    public String toString() {
        return "DeletedS3Object[id=" + id() + ", key=" + key() + "]";
    }
}
