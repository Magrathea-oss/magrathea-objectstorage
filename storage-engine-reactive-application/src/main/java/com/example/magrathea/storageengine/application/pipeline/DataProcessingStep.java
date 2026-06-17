package com.example.magrathea.storageengine.application.pipeline;

import org.reactivestreams.Publisher;
import reactor.core.publisher.Mono;

/**
 * Application-layer contract for a single step in the data processing pipeline.
 *
 * All steps share this interface regardless of cardinality:
 *   - Transform steps (1:1): compress, encrypt — return Mono<StorageUnit>
 *   - Structural steps (1:N): dedup, EC — return Flux<StorageUnit>
 *
 * Implementations declare a more specific return type (Mono or Flux) via Java's
 * covariant return type support; both satisfy Publisher<StorageUnit>.
 *
 * Publisher<StorageUnit> from org.reactivestreams is intentionally kept out of the
 * domain layer — it belongs here in the application port boundary.
 *
 * Lifecycle hooks allow each step to:
 *   before()  — validate or enrich the unit before apply() (default: pass-through)
 *   apply()   — core transformation (required)
 *   after()   — observe or verify the result stream, e.g. metrics (default: pass-through)
 *   onError() — notified on failure with original unit context (default: no-op)
 *
 * TODO (criticality 5): Cross-cutting observability (metrics, tracing) should be
 * injectable via the after()/onError() hooks rather than hardcoded per implementation.
 */
public interface DataProcessingStep {

    Publisher<StorageUnit> apply(StorageUnit unit);

    default Mono<StorageUnit> before(StorageUnit unit) {
        return Mono.just(unit);
    }

    default Publisher<StorageUnit> after(StorageUnit input, Publisher<StorageUnit> result) {
        return result;
    }

    default Mono<Void> onError(StorageUnit input, Throwable error) {
        return Mono.empty();
    }
}
