package com.example.magrathea.s3api.cucumber.steps;

import com.example.magrathea.reactive.infrastructure.adapter.persistence.InMemoryReactiveBucketRepository;
import com.example.magrathea.reactive.infrastructure.adapter.persistence.InMemoryReactiveMultipartUploadRepository;
import com.example.magrathea.reactive.infrastructure.adapter.persistence.InMemoryReactiveS3ObjectRepository;
import io.cucumber.java.Before;
import org.springframework.beans.factory.annotation.Autowired;

/**
 * Resets in-memory repositories and shared step state before each Cucumber scenario.
 * Cucumber scenarios share a Spring context — state must be cleared.
 */
public class RepositoryResetSteps {

    @Autowired
    private InMemoryReactiveBucketRepository bucketRepository;

    @Autowired
    private InMemoryReactiveS3ObjectRepository objectRepository;

    @Autowired
    private InMemoryReactiveMultipartUploadRepository multipartUploadRepository;

    @Autowired
    private CommonSteps commonSteps;

    @Before
    public void resetRepositories() {
        bucketRepository.reset();
        objectRepository.reset();
        multipartUploadRepository.reset();
        commonSteps.reset();
    }
}
