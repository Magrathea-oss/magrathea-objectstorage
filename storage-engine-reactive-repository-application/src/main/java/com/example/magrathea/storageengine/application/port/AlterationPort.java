package com.example.magrathea.storageengine.application.port;

import com.example.magrathea.storageengine.domain.valueobject.AlterationResult;
import com.example.magrathea.storageengine.domain.valueobject.StepPlan;
import reactor.core.publisher.Mono;

/**
 * Application port — intentional data alteration (fault injection, testing).
 */
public interface AlterationPort {
    Mono<AlterationResult> apply(byte[] data, StepPlan step);
}
