package com.example.magrathea.s3api.cucumber.steps;

import com.example.magrathea.objectstorage.infrastructure.adapter.persistence.InMemoryBucketRepository;
import com.example.magrathea.objectstorage.infrastructure.adapter.persistence.InMemoryObjectRepository;
import io.cucumber.java.Before;
import org.springframework.beans.factory.annotation.Autowired;

/**
 * Resets in-memory repositories and shared step state before each Cucumber scenario.
 * Cucumber scenarios share a Spring context — state must be cleared.
 */
public class RepositoryResetSteps {

    @Autowired
    private InMemoryBucketRepository bucketRepository;

    @Autowired
    private InMemoryObjectRepository objectRepository;

    @Autowired
    private CommonSteps commonSteps;

    @Before
    public void resetRepositories() {
        bucketRepository.reset();
        objectRepository.reset();
        commonSteps.reset();
    }
}
