package com.example.magrathea.s3api.cucumber.steps;

import com.example.magrathea.reactive.infrastructure.adapter.persistence.InMemoryReactiveBucketRepository;
import com.example.magrathea.reactive.infrastructure.adapter.persistence.InMemoryReactiveMultipartUploadRepository;
import com.example.magrathea.reactive.infrastructure.adapter.persistence.InMemoryReactiveS3ObjectRepository;
import com.example.magrathea.s3api.adapter.web.S3BucketConfigHandler;
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

    @Autowired
    private S3BucketConfigHandler bucketConfigHandler;

    @Before
    public void resetRepositories() {
        bucketRepository.reset();
        objectRepository.reset();
        multipartUploadRepository.reset();
        bucketConfigHandler.resetInMemoryConfigurations();
        commonSteps.reset();
    }
}
