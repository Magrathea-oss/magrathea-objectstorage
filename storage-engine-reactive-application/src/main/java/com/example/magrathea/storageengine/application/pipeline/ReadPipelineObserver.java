package com.example.magrathea.storageengine.application.pipeline;

import com.example.magrathea.storageengine.domain.valueobject.ChunkId;

/**
 * Payload-free observability hook for proving lazy, ordered read-pipeline behavior.
 * Implementations must not block or alter demand.
 */
public interface ReadPipelineObserver {

    ReadPipelineObserver NO_OP = new ReadPipelineObserver() { };

    default void chunkReadRequested(String correlationId, int ordinal, ChunkId chunkId) { }

    default void chunkVerified(String correlationId, int ordinal, ChunkId chunkId) { }

    default void responseChunkEmitted(String correlationId, int ordinal, ChunkId chunkId) { }

    default void downstreamRequested(String correlationId, long requested) { }
}
