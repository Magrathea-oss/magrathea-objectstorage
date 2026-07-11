package com.example.magrathea.storageengine.application.port;

import com.example.magrathea.storageengine.domain.valueobject.ChunkId;
import com.example.magrathea.storageengine.domain.valueobject.StorageArtifactReferenceDescriptor;
import com.example.magrathea.storageengine.domain.valueobject.DeviceConfigurationHash;
import com.example.magrathea.storageengine.domain.valueobject.Fingerprint;
import reactor.core.publisher.Mono;

import java.util.Optional;

/**
 * Application port — reactive content-address index for dedup lookups.
 */
public interface ContentAddressIndex {
    Mono<Optional<StorageArtifactReferenceDescriptor>> find(DeviceConfigurationHash deviceHash, Fingerprint fingerprint);
    Mono<Void> record(DeviceConfigurationHash deviceHash, Fingerprint fingerprint, ChunkId chunkId);
}
