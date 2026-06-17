package com.example.magrathea.storageengine.infrastructure.pipeline;

import com.example.magrathea.storageengine.application.pipeline.EncryptionStep;
import com.example.magrathea.storageengine.application.pipeline.StorageUnit;
import org.reactivestreams.Publisher;
import reactor.core.publisher.Mono;

/** Pass-through encryption step — used when encryption is disabled in the storage policy. */
public class NoOpEncryptionStep implements EncryptionStep {
    @Override
    public Publisher<StorageUnit> apply(StorageUnit unit) {
        return Mono.just(unit);
    }
}
