package com.example.magrathea.storageengine.application.pipeline;

/** Marker interface for erasure coding step implementations (structural, 1:N).
 *  Receives StorageUnit, emits Flux<ECStripeUnit>. */
public interface ErasureCodingStep extends DataProcessingStep {}
