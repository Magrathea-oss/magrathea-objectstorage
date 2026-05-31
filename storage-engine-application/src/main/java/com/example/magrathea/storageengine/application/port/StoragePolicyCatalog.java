package com.example.magrathea.storageengine.application.port;

import com.example.magrathea.storageengine.domain.valueobject.StorageClassId;
import com.example.magrathea.storageengine.domain.valueobject.StoragePolicy;
import reactor.core.publisher.Mono;

/**
 * Application port — reactive catalog for looking up storage policies.
 */
public interface StoragePolicyCatalog {
    Mono<StoragePolicy> findBy(StorageClassId id);
}
