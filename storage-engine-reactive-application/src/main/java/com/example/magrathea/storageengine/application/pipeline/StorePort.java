package com.example.magrathea.storageengine.application.pipeline;

import reactor.core.publisher.Mono;

/**
 * Application port for persisting a processed StorageUnit to durable storage.
 *
 * This is the single point in the pipeline that uses instanceof/pattern matching
 * to distinguish unit subtypes (FileUnit, ChunkUnit, ECStripeUnit, PartUnit).
 * No other step or pipeline component needs to know the concrete type.
 *
 * The Flux<DataBuffer> inside unit.data() is consumed exactly once by the implementation.
 * Implementations must perform atomic writes (temp file + rename) and compute a
 * content hash (SHA-256) incrementally while streaming — no full materialisation.
 */
public interface StorePort {
    Mono<StorageTrace> write(StorageUnit unit);
}
