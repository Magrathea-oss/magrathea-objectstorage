package com.example.magrathea.objectstore.domain.aggregate;

import com.example.magrathea.objectstore.domain.event.ObjectStoreEvent;
import com.example.magrathea.objectstore.domain.valueobject.ObjectKey;

import java.time.ZonedDateTime;
import java.util.List;
import java.util.Map;

/**
 * Terminal state — object has been deleted.
 * No valid transitions — this is a final state.
 */
public final class DeletedS3Object extends S3Object {

    DeletedS3Object(ObjectKey key, String storageClass,
                    Map<String, String> userMetadata,
                    ZonedDateTime createdAt,
                    WriteState writeState, List<ObjectStoreEvent> events) {
        super(key, storageClass, userMetadata, null, null, 0L, createdAt, writeState, events);
    }

    @Override
    public S3Object clearEvents() {
        return new DeletedS3Object(key(), storageClass(), userMetadata(),
            createdAt(), writeState(), List.of());
    }

    @Override
    protected S3Object withWriteState(WriteState newState) {
        return new DeletedS3Object(key(), storageClass(), userMetadata(),
            createdAt(), newState, domainEvents());
    }

    @Override
    public String toString() {
        return "DeletedS3Object[key=" + key() + ",writeState=" + writeState() + "]";
    }
}
