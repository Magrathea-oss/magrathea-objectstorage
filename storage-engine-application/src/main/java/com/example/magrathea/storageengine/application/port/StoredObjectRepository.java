package com.example.magrathea.storageengine.application.port;

import com.example.magrathea.storageengine.domain.aggregate.StoredObject;
import com.example.magrathea.storageengine.domain.valueobject.ObjectId;
import com.example.magrathea.storageengine.domain.valueobject.VersionId;
import reactor.core.publisher.Mono;

/**
 * Application port — reactive repository for StoredObject aggregates.
 */
public interface StoredObjectRepository {
    Mono<Void> save(StoredObject storedObject);
    Mono<StoredObject> findBy(ObjectId objectId, VersionId versionId);
}
