package com.example.magrathea.storageengine.application.port;

import com.example.magrathea.storageengine.domain.valueobject.StorageDevice;
import com.example.magrathea.storageengine.domain.valueobject.StorageDeviceId;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

/**
 * Application port — reactive catalog for looking up physical storage devices.
 *
 * <p>Implementations may load device descriptors from YAML files, a database,
 * or any other source. The catalog is treated as an immutable startup snapshot;
 * live reload semantics (if needed) are delegated to the implementation.
 */
public interface StorageDeviceCatalog {

    /**
     * Finds a storage device by its stable identifier.
     *
     * @param deviceId the device identifier
     * @return the device, or empty if not found
     */
    Mono<StorageDevice> findById(StorageDeviceId deviceId);

    /**
     * Returns all loaded storage devices.
     *
     * @return all devices in the catalog
     */
    Flux<StorageDevice> findAll();

    /**
     * Returns all storage devices eligible for write operations.
     * Only {@code HEALTHY} devices are considered write-eligible.
     *
     * @return write-eligible devices
     */
    Flux<StorageDevice> findEligibleForWrite();
}
