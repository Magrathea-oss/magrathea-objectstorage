package com.example.magrathea.storageengine.application.port;

import com.example.magrathea.storageengine.domain.valueobject.NodeId;
import com.example.magrathea.storageengine.domain.valueobject.PersistencePlan;
import reactor.core.publisher.Mono;

import java.util.List;

/**
 * Application port — persists chunk data to storage nodes according to a plan.
 */
public interface ChunkStorePort {
    Mono<List<NodeId>> store(byte[] data, PersistencePlan plan);
}
