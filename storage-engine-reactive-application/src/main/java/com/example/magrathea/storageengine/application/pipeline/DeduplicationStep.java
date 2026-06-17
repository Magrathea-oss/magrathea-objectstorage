package com.example.magrathea.storageengine.application.pipeline;

/** Marker interface for deduplication step implementations (structural, 1:N).
 *  Receives FileUnit, emits Flux<ChunkUnit>. */
public interface DeduplicationStep extends DataProcessingStep {}
