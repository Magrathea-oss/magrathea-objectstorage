package com.example.magrathea.storageengine.application.port;

import com.example.magrathea.storageengine.domain.valueobject.ChunkId;
import com.example.magrathea.storageengine.domain.valueobject.NodeId;
import com.example.magrathea.storageengine.domain.valueobject.PersistencePlan;
import reactor.core.publisher.Mono;

import java.util.List;

/**
 * Application port — persists and reads chunk data by stable domain chunk identity.
 */
public interface ChunkStorePort {
    Mono<List<NodeId>> store(ChunkId chunkId, byte[] data, PersistencePlan plan);

    Mono<byte[]> read(ChunkId chunkId);
}
