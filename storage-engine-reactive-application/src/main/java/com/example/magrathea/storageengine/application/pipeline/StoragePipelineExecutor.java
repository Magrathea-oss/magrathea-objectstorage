package com.example.magrathea.storageengine.application.pipeline;

import com.example.magrathea.storageengine.domain.valueobject.StepOutcome;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import reactor.core.publisher.SignalType;

import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;

public final class StoragePipelineExecutor {

    private final StorageEventPublisher eventPublisher;

    public StoragePipelineExecutor(StorageEventPublisher eventPublisher) {
        this.eventPublisher = eventPublisher;
    }

    public Mono<StorageContext> execute(StorageContext initialContext, List<StorageStage> stages) {
        AtomicReference<StorageContext> lastContext = new AtomicReference<>(initialContext);
        AtomicReference<String> activeStageName = new AtomicReference<>(stages.isEmpty() ? "pipeline" : stages.getFirst().name());
        AtomicBoolean terminalCleanupStarted = new AtomicBoolean(false);

        return executeNext(initialContext, stages, 0, lastContext, activeStageName)
                .doFinally(signal -> {
                    if (signal == SignalType.CANCEL && terminalCleanupStarted.compareAndSet(false, true)) {
                        StorageContext context = lastContext.get();
                        String stageName = activeStageName.get();
                        publish(cancelled(context, stageName))
                                .then(cleanup(context))
                                .subscribe();
                    }
                });
    }

    private Mono<StorageContext> executeNext(
            StorageContext context,
            List<StorageStage> stages,
            int index,
            AtomicReference<StorageContext> lastContext,
            AtomicReference<String> activeStageName) {
        if (index >= stages.size()) {
            return Mono.just(context);
        }
        StorageStage stage = stages.get(index);
        activeStageName.set(stage.name());
        Instant startedAt = Instant.now();
        return publish(started(context, stage.name(), startedAt))
                .then(stage.execute(context)
                        .onErrorResume(error -> publish(failed(context, stage.name(), startedAt, error))
                                .then(cleanup(lastContext.get()))
                                .then(Mono.error(error))))
                .flatMap(updated -> {
                    lastContext.set(updated);
                    return publish(succeeded(updated, stage.name(), startedAt))
                            .then(executeNext(updated, stages, index + 1, lastContext, activeStageName));
                });
    }

    private Mono<Void> cleanup(StorageContext context) {
        List<StorageCleanupHandle> handles = new ArrayList<>(context.cleanupHandles());
        Collections.reverse(handles);
        return Flux.fromIterable(handles)
                .concatMap(handle -> {
                    Instant startedAt = Instant.now();
                    return handle.cleanup()
                            .then(publish(new StorageEvent.CleanupCompleted(
                                    context.operation(),
                                    context.correlationId(),
                                    "cleanup",
                                    context.bucketName(),
                                    context.objectKey(),
                                    manifestIdValue(context),
                                    Instant.now(),
                                    Duration.between(startedAt, Instant.now()),
                                    handle.name())));
                })
                .then();
    }

    private Mono<Void> publish(StorageEvent event) {
        return eventPublisher.publish(event);
    }

    private static StorageEvent.StageStarted started(StorageContext context, String stageName, Instant startedAt) {
        return new StorageEvent.StageStarted(
                context.operation(),
                context.correlationId(),
                stageName,
                context.bucketName(),
                context.objectKey(),
                manifestIdValue(context),
                startedAt);
    }

    private static StorageEvent.StageSucceeded succeeded(StorageContext context, String stageName, Instant startedAt) {
        return new StorageEvent.StageSucceeded(
                context.operation(),
                context.correlationId(),
                stageName,
                context.bucketName(),
                context.objectKey(),
                manifestIdValue(context),
                Instant.now(),
                Duration.between(startedAt, Instant.now()),
                measurements(context, stageName));
    }

    private static StorageEvent.StageFailed failed(StorageContext context, String stageName, Instant startedAt, Throwable error) {
        return new StorageEvent.StageFailed(
                context.operation(),
                context.correlationId(),
                stageName,
                context.bucketName(),
                context.objectKey(),
                manifestIdValue(context),
                Instant.now(),
                Duration.between(startedAt, Instant.now()),
                error.getMessage() == null ? error.getClass().getSimpleName() : error.getMessage(),
                StorageEventMeasurements.failure());
    }

    private static StorageEvent.StageCancelled cancelled(StorageContext context, String stageName) {
        return new StorageEvent.StageCancelled(
                context.operation(),
                context.correlationId(),
                stageName,
                context.bucketName(),
                context.objectKey(),
                manifestIdValue(context),
                Instant.now());
    }

    private static Optional<String> manifestIdValue(StorageContext context) {
        return context.manifestId().map(ManifestId -> ManifestId.value().toString());
    }

    private static StorageEventMeasurements measurements(StorageContext context, String stageName) {
        if (context.operation() == StorageOperation.WRITE && "chunk-persistence".equals(stageName)) {
            long bytes = context.chunkTraces().stream()
                    .mapToLong(trace -> trace.originalSize())
                    .sum();
            long chunks = context.chunkTraces().size();
            long dedupHits = context.chunkTraces().stream()
                    .filter(StoragePipelineExecutor::hasDedupHit)
                    .count();
            long dedupMisses = context.chunkTraces().stream()
                    .filter(StoragePipelineExecutor::hasDedupMiss)
                    .count();
            return StorageEventMeasurements.writeChunkStage(bytes, chunks, dedupHits, dedupMisses);
        }
        if (context.operation() == StorageOperation.WRITE && "manifest-persistence".equals(stageName)
                && context.manifest().isPresent()) {
            return StorageEventMeasurements.manifestStage(1);
        }
        if (context.operation() == StorageOperation.READ && "response-streaming".equals(stageName)) {
            long bytes = context.chunkDescriptors().stream()
                    .mapToLong(descriptor -> descriptor.originalSize())
                    .sum();
            return StorageEventMeasurements.readChunkStage(bytes, context.chunkDescriptors().size());
        }
        return StorageEventMeasurements.empty();
    }

    private static boolean hasDedupHit(com.example.magrathea.storageengine.domain.valueobject.ChunkPersistenceTrace trace) {
        return trace.steps().stream()
                .flatMap(step -> step.operationOutcome().stream())
                .filter(StepOutcome.DedupOutcome.class::isInstance)
                .map(StepOutcome.DedupOutcome.class::cast)
                .anyMatch(StepOutcome.DedupOutcome::matched);
    }

    private static boolean hasDedupMiss(com.example.magrathea.storageengine.domain.valueobject.ChunkPersistenceTrace trace) {
        return trace.steps().stream()
                .flatMap(step -> step.operationOutcome().stream())
                .filter(StepOutcome.DedupOutcome.class::isInstance)
                .map(StepOutcome.DedupOutcome.class::cast)
                .anyMatch(outcome -> !outcome.matched());
    }
}
