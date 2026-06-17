package com.example.magrathea.storageengine.application.pipeline;

import com.example.magrathea.storageengine.domain.pipeline.DataProcessingSpec;

/**
 * Application-layer port for building a DataProcessingPipeline from a DataProcessingSpec.
 * Infrastructure implementations (e.g. DataProcessingPipelineFactory) are injected at
 * Spring Boot startup and passed to ReactiveStorageOrchestrator via the constructor.
 *
 * Keeping this interface in the application layer prevents a direct dependency from
 * the application module onto the infrastructure module.
 */
@FunctionalInterface
public interface DataProcessingPipelinePort {

    /**
     * Builds a DataProcessingPipeline configured for the given spec.
     *
     * @param spec the ordered list of step specifications derived from the effective policy
     * @return a ready-to-execute pipeline
     */
    DataProcessingPipeline build(DataProcessingSpec spec);
}
