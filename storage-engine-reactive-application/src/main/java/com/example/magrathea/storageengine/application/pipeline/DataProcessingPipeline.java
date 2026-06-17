package com.example.magrathea.storageengine.application.pipeline;

import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.util.List;

/**
 * Executes an ordered sequence of DataProcessingStep instances over StorageUnit instances.
 *
 * The pipeline starts with a single FileUnit wrapping the raw upload stream.
 * Transform steps (compress, encrypt) map 1:1, keeping the same number of units.
 * Structural steps (dedup, EC) flatMap 1:N, increasing the unit count.
 *
 * Processing is sequential per unit (concatMap) to preserve backpressure and order.
 * Each unit's Flux<DataBuffer> is consumed exactly once.
 *
 * Lifecycle per unit per step: before() → apply() → after(), with onError() on failure.
 *
 * TODO (criticality 4): No cleanup mechanism for partial disk writes on mid-pipeline
 * failure. Infrastructure implementations must handle their own temp-file cleanup
 * until a cross-cutting cleanup port is added.
 */
public class DataProcessingPipeline {

    private final List<DataProcessingStep> steps;
    private final StorePort storePort;

    public DataProcessingPipeline(List<DataProcessingStep> steps, StorePort storePort) {
        this.steps = List.copyOf(steps);
        this.storePort = storePort;
    }

    /**
     * Processes the initial FileUnit through all configured steps, writes each resulting
     * StorageUnit to the store, and returns the ordered list of StorageTraces.
     *
     * @param initial the entry unit — always a FileUnit wrapping the raw upload Flux
     * @return a Flux of StorageTrace, one per written unit, in pipeline order
     */
    public Flux<StorageTrace> execute(StorageUnit.FileUnit initial) {
        Flux<StorageUnit> current = Flux.just(initial);

        for (DataProcessingStep step : steps) {
            current = current.concatMap(unit ->
                step.before(unit)
                    .flatMapMany(prepared ->
                        Flux.from(step.after(prepared, step.apply(prepared))))
                    .onErrorResume(e ->
                        step.onError(unit, e).then(Mono.error(e)))
            );
        }

        return current.concatMap(storePort::write);
    }
}
