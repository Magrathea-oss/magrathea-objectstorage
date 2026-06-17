package com.example.magrathea.storageengine.infrastructure.pipeline;

import com.example.magrathea.storageengine.application.pipeline.CompressionStep;
import com.example.magrathea.storageengine.application.pipeline.DataProcessingPipeline;
import com.example.magrathea.storageengine.application.pipeline.DataProcessingPipelinePort;
import com.example.magrathea.storageengine.application.pipeline.DataProcessingStep;
import com.example.magrathea.storageengine.application.pipeline.DeduplicationStep;
import com.example.magrathea.storageengine.application.pipeline.EncryptionStep;
import com.example.magrathea.storageengine.application.pipeline.ErasureCodingStep;
import com.example.magrathea.storageengine.application.pipeline.StorePort;
import com.example.magrathea.storageengine.application.port.ContentAddressIndex;
import com.example.magrathea.storageengine.domain.pipeline.DataProcessingSpec;
import com.example.magrathea.storageengine.domain.pipeline.StepSpec;

import java.util.ArrayList;
import java.util.List;

/**
 * Builds a DataProcessingPipeline from a DataProcessingSpec and the configured step beans.
 *
 * When a {@link ContentAddressIndex} is provided, the deduplication step is created fresh
 * on each {@link #build(DataProcessingSpec)} call using the chunk size declared in
 * {@link com.example.magrathea.storageengine.domain.valueobject.DedupConfig#chunkSize()}.
 * This ensures that per-policy chunk sizes are honoured rather than using a single
 * hardcoded default.
 *
 * When no {@code ContentAddressIndex} is available (e.g. in tests or non-dedup contexts)
 * the legacy constructor accepts a pre-created {@link DeduplicationStep} that is used
 * as a fallback for any {@link com.example.magrathea.storageengine.domain.pipeline.StepSpec.Dedup}
 * encountered in the spec.
 *
 * Steps absent from the spec are not included in the pipeline.
 */
public class DataProcessingPipelineFactory implements DataProcessingPipelinePort {

    /**
     * Content address index used to construct a spec-configured {@link FixedWindowDedupStep}
     * on each {@link #build} call. {@code null} when falling back to the legacy constructor.
     */
    private final ContentAddressIndex contentAddressIndex;

    /**
     * Fallback step used when {@link #contentAddressIndex} is {@code null} or when the
     * spec contains no dedup entry (e.g. {@link NoOpDeduplicationStep} for non-dedup policies).
     */
    private final DeduplicationStep fallbackDeduplicationStep;
    private final CompressionStep compressionStep;
    private final EncryptionStep encryptionStep;
    private final ErasureCodingStep erasureCodingStep;
    private final StorePort storePort;

    /**
     * Primary constructor: creates a {@link FixedWindowDedupStep} per spec invocation,
     * using the chunk size from the spec's {@link com.example.magrathea.storageengine.domain.valueobject.DedupConfig}.
     *
     * @param contentAddressIndex the content address index; may be {@code null}, in which
     *                             case any dedup spec entry falls back to a NoOp step.
     */
    public DataProcessingPipelineFactory(
            ContentAddressIndex contentAddressIndex,
            CompressionStep compressionStep,
            EncryptionStep encryptionStep,
            ErasureCodingStep erasureCodingStep,
            StorePort storePort) {
        this.contentAddressIndex = contentAddressIndex;
        this.fallbackDeduplicationStep = new NoOpDeduplicationStep();
        this.compressionStep = compressionStep;
        this.encryptionStep = encryptionStep;
        this.erasureCodingStep = erasureCodingStep;
        this.storePort = storePort;
    }

    /**
     * Legacy constructor: uses the supplied pre-created {@link DeduplicationStep} for any
     * dedup spec entry, regardless of the spec's configured chunk size.
     * Retained for test and backward-compatibility usage where a {@link ContentAddressIndex}
     * is not available or not relevant (e.g. {@link NoOpDeduplicationStep} scenarios).
     */
    public DataProcessingPipelineFactory(
            DeduplicationStep deduplicationStep,
            CompressionStep compressionStep,
            EncryptionStep encryptionStep,
            ErasureCodingStep erasureCodingStep,
            StorePort storePort) {
        this.contentAddressIndex = null;
        this.fallbackDeduplicationStep = deduplicationStep;
        this.compressionStep = compressionStep;
        this.encryptionStep = encryptionStep;
        this.erasureCodingStep = erasureCodingStep;
        this.storePort = storePort;
    }

    public DataProcessingPipeline build(DataProcessingSpec spec) {
        List<DataProcessingStep> steps = new ArrayList<>();
        for (StepSpec stepSpec : spec.steps()) {
            DataProcessingStep step = switch (stepSpec) {
                case StepSpec.Dedup d -> {
                    if (contentAddressIndex != null) {
                        // Create a fresh step per spec so the configured chunk size is honoured.
                        int chunkSize = Math.toIntExact(d.config().chunkSize());
                        yield new FixedWindowDedupStep(contentAddressIndex, chunkSize);
                    } else {
                        yield fallbackDeduplicationStep;
                    }
                }
                case StepSpec.Compress ignored -> compressionStep;
                case StepSpec.Encrypt ignored  -> encryptionStep;
                case StepSpec.EC ignored       -> erasureCodingStep;
            };
            steps.add(step);
        }
        return new DataProcessingPipeline(steps, storePort);
    }
}
