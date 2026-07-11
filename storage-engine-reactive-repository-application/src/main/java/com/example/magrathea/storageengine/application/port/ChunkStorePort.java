package com.example.magrathea.storageengine.application.port;

import com.example.magrathea.storageengine.domain.valueobject.ChunkId;
import com.example.magrathea.storageengine.domain.valueobject.NodeId;
import com.example.magrathea.storageengine.domain.valueobject.PersistencePlan;
import com.example.magrathea.storageengine.domain.valueobject.StorageArtifactKind;
import com.example.magrathea.storageengine.domain.valueobject.StorageArtifactReferenceDescriptor;
import reactor.core.publisher.Mono;

import java.util.List;

/**
 * Application port — persists and reads chunk data by stable domain chunk identity.
 *
 * <p><b>Write path:</b> superseded by
 * {@link com.example.magrathea.storageengine.application.pipeline.StorePort} in the reactive
 * pipeline. New chunk writes flow through {@code DataProcessingPipeline} which calls
 * {@code StorePort.write(StorageUnit)} rather than this port.
 *
 * <p><b>Read path:</b> still actively used by
 * {@code ReactiveStorageOrchestrator.prepareChunkReading()} to stream persisted chunk data
 * back to callers by {@link com.example.magrathea.storageengine.domain.valueobject.ChunkId}.
 * This interface must remain until the read path is migrated to a reactive streaming port.
 */
public interface ChunkStorePort {
    Mono<List<NodeId>> store(ChunkId chunkId, byte[] data, PersistencePlan plan);

    Mono<byte[]> read(ChunkId chunkId);

    /** Reads a typed artifact while preserving compatibility with chunk-only adapters. */
    default Mono<byte[]> read(StorageArtifactReferenceDescriptor artifact) {
        return read(artifact.chunkId());
    }

    /** Removes a pipeline-owned unpublished artifact after a failed write. */
    default Mono<Void> delete(StorageArtifactKind artifactKind, ChunkId artifactId) {
        return delete(artifactId);
    }

    /** Removes a legacy chunk after a failed write. */
    default Mono<Void> delete(ChunkId chunkId) {
        return Mono.empty();
    }
}
