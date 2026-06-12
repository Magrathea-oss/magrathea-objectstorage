package com.example.magrathea.storageengine.application.port;

import com.example.magrathea.storageengine.domain.valueobject.DiskSet;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

/**
 * Application port — reactive catalog for looking up disk sets.
 *
 * <p>A disk set is a named grouping of physical storage devices sharing a common
 * failure domain. Implementations may load disk set descriptors from YAML files
 * or any other source. The catalog is treated as an immutable startup snapshot.
 */
public interface DiskSetCatalog {

    /**
     * Finds a disk set by its string identifier (the {@code diskSetId} YAML field).
     *
     * @param diskSetId the disk set identifier, e.g. {@code "default-diskset"}
     * @return the disk set, or empty if not found
     */
    Mono<DiskSet> findById(String diskSetId);

    /**
     * Returns all loaded disk sets.
     *
     * @return all disk sets in the catalog
     */
    Flux<DiskSet> findAll();
}
