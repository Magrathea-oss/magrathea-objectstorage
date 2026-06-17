package com.example.magrathea.storageengine.infrastructure.pipeline;

import com.example.magrathea.storageengine.application.pipeline.DeduplicationStep;
import com.example.magrathea.storageengine.application.pipeline.StorageUnit;
import org.reactivestreams.Publisher;
import reactor.core.publisher.Mono;

/**
 * Pass-through deduplication step — used when dedup is disabled.
 * FileUnit passes through unchanged; StorePort handles FileUnit directly.
 */
public class NoOpDeduplicationStep implements DeduplicationStep {
    @Override
    public Publisher<StorageUnit> apply(StorageUnit unit) {
        return Mono.just(unit);
    }
}
