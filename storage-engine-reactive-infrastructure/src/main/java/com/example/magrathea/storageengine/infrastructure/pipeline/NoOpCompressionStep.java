package com.example.magrathea.storageengine.infrastructure.pipeline;

import com.example.magrathea.storageengine.application.pipeline.CompressionStep;
import com.example.magrathea.storageengine.application.pipeline.StorageUnit;
import org.reactivestreams.Publisher;
import reactor.core.publisher.Mono;

/** Pass-through compression step — used when compression is disabled in the storage policy. */
public class NoOpCompressionStep implements CompressionStep {
    @Override
    public Publisher<StorageUnit> apply(StorageUnit unit) {
        return Mono.just(unit);
    }
}
