package com.example.magrathea.storageengine.infrastructure.pipeline;

import com.example.magrathea.storageengine.application.pipeline.CompressionStep;
import com.example.magrathea.storageengine.application.pipeline.DataProcessingPipeline;
import com.example.magrathea.storageengine.application.pipeline.DataProcessingPipelinePort;
import com.example.magrathea.storageengine.application.pipeline.DataProcessingStep;
import com.example.magrathea.storageengine.application.pipeline.DeduplicationStep;
import com.example.magrathea.storageengine.application.pipeline.EncryptionStep;
import com.example.magrathea.storageengine.application.pipeline.ErasureCodingStep;
import com.example.magrathea.storageengine.application.pipeline.StorePort;
import com.example.magrathea.storageengine.domain.pipeline.DataProcessingSpec;
import com.example.magrathea.storageengine.domain.pipeline.StepSpec;

import java.util.ArrayList;
import java.util.List;

/**
 * Builds a DataProcessingPipeline from a DataProcessingSpec and the configured step beans.
 *
 * Each StepSpec in the ordered spec maps to the corresponding injected implementation.
 * Steps absent from the spec are not included in the pipeline.
 */
public class DataProcessingPipelineFactory implements DataProcessingPipelinePort {

    private final DeduplicationStep deduplicationStep;
    private final CompressionStep compressionStep;
    private final EncryptionStep encryptionStep;
    private final ErasureCodingStep erasureCodingStep;
    private final StorePort storePort;

    public DataProcessingPipelineFactory(
            DeduplicationStep deduplicationStep,
            CompressionStep compressionStep,
            EncryptionStep encryptionStep,
            ErasureCodingStep erasureCodingStep,
            StorePort storePort) {
        this.deduplicationStep = deduplicationStep;
        this.compressionStep = compressionStep;
        this.encryptionStep = encryptionStep;
        this.erasureCodingStep = erasureCodingStep;
        this.storePort = storePort;
    }

    public DataProcessingPipeline build(DataProcessingSpec spec) {
        List<DataProcessingStep> steps = new ArrayList<>();
        for (StepSpec stepSpec : spec.steps()) {
            DataProcessingStep step = switch (stepSpec) {
                case StepSpec.Dedup ignored    -> deduplicationStep;
                case StepSpec.Compress ignored -> compressionStep;
                case StepSpec.Encrypt ignored  -> encryptionStep;
                case StepSpec.EC ignored       -> erasureCodingStep;
            };
            steps.add(step);
        }
        return new DataProcessingPipeline(steps, storePort);
    }
}
