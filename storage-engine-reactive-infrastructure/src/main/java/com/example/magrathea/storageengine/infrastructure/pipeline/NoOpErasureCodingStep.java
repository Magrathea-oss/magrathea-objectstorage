package com.example.magrathea.storageengine.infrastructure.pipeline;

import com.example.magrathea.storageengine.application.pipeline.ErasureCodingStep;
import com.example.magrathea.storageengine.application.pipeline.StorageUnit;
import org.reactivestreams.Publisher;
import reactor.core.publisher.Mono;

/** Pass-through erasure coding step — used when EC is disabled in the storage policy. */
public class NoOpErasureCodingStep implements ErasureCodingStep {
    @Override
    public Publisher<StorageUnit> apply(StorageUnit unit) {
        return Mono.just(unit);
    }
}
