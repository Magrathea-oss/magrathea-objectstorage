package com.example.magrathea.storageengine.application.port;

import com.example.magrathea.storageengine.domain.valueobject.ManifestId;
import com.example.magrathea.storageengine.domain.valueobject.ObjectManifest;
import reactor.core.publisher.Mono;

/**
 * Application port — reactive repository for ObjectManifests.
 */
public interface ObjectManifestRepository {
    Mono<Void> save(ObjectManifest manifest);
    Mono<ObjectManifest> findBy(ManifestId manifestId);
}
