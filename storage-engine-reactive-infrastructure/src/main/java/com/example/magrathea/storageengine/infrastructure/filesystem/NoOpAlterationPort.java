package com.example.magrathea.storageengine.infrastructure.filesystem;

import com.example.magrathea.storageengine.application.port.AlterationPort;
import com.example.magrathea.storageengine.domain.valueobject.AlterationResult;
import com.example.magrathea.storageengine.domain.valueobject.StepPlan;
import reactor.core.publisher.Mono;

/**
 * No-op implementation of AlterationPort.
 * Returns AlterationResult.notApplied() — no alteration applied.
 */
public class NoOpAlterationPort implements AlterationPort {

    @Override
    public Mono<AlterationResult> apply(byte[] data, StepPlan step) {
        return Mono.just(AlterationResult.notApplied());
    }
}
