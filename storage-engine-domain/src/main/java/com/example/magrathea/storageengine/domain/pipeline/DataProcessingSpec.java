package com.example.magrathea.storageengine.domain.pipeline;

import java.util.List;

/**
 * Ordered specification of all processing steps to apply to an upload.
 * Steps are optional but their relative order is fixed:
 *   Dedup → Compress → Encrypt → EC
 *
 * The application layer maps each StepSpec to the appropriate DataProcessingStep
 * implementation configured in the infrastructure.
 */
public record DataProcessingSpec(List<StepSpec> steps) {

    public DataProcessingSpec {
        steps = List.copyOf(steps);
    }

    public static DataProcessingSpec empty() {
        return new DataProcessingSpec(List.of());
    }

    public static DataProcessingSpec of(StepSpec... steps) {
        return new DataProcessingSpec(List.of(steps));
    }
}
