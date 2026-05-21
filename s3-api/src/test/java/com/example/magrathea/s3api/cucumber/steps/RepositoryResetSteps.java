package com.example.magrathea.s3api.cucumber.steps;

import com.example.magrathea.objectstorage.infrastructure.adapter.persistence.BucketRepositoryImpl;
import com.example.magrathea.objectstorage.infrastructure.adapter.persistence.InMemoryObjectRepository;
import io.cucumber.java.Before;
import org.springframework.beans.factory.annotation.Autowired;

/**
 * Resets in-memory repositories before each Cucumber scenario.
 * Cucumber scenarios share a Spring context — state must be cleared.
 */
public class RepositoryResetSteps {

    @Autowired
    private BucketRepositoryImpl bucketRepository;

    @Autowired
    private InMemoryObjectRepository objectRepository;

    @Before
    public void resetRepositories() {
        bucketRepository.reset();
        objectRepository.reset();
    }
}
