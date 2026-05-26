package com.example.magrathea.reactive.application.service;

import com.example.magrathea.objectstore.domain.aggregate.Bucket;
import com.example.magrathea.objectstore.domain.event.ObjectStoreEvent;
import com.example.magrathea.objectstore.domain.valueobject.BucketConfig;
import com.example.magrathea.objectstore.domain.valueobject.CorsConfiguration;
import com.example.magrathea.objectstore.domain.valueobject.Region;
import com.example.magrathea.objectstore.domain.valueobject.StorageClass;
import com.example.magrathea.objectstore.reactive.repository.application.CommandResult;
import com.example.magrathea.reactive.infrastructure.adapter.persistence.BucketAlreadyExistsException;
import com.example.magrathea.reactive.infrastructure.adapter.persistence.BucketNotFoundException;
import com.example.magrathea.reactive.infrastructure.adapter.persistence.InMemoryReactiveBucketRepository;
import io.cucumber.java.Before;
import io.cucumber.java.en.Given;
import io.cucumber.java.en.When;
import io.cucumber.java.en.Then;
import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

public class BucketStepDefinitions {

    private InMemoryReactiveBucketRepository repository;
    private ReactiveBucketService service;
    private Mono<?> result;

    @Before
    public void reset() {
        repository = new InMemoryReactiveBucketRepository();
        service = new ReactiveBucketService(repository, repository);
        result = null;
    }

    @Given("bucket {string} does not exist")
    public void bucketDoesNotExist(String bucketName) {
        var existing = repository.findByName(bucketName).block();
        assertNull(existing, "Bucket should not exist: " + bucketName);
    }

    @Given("bucket {string} exists")
    public void bucketExists(String bucketName) {
        Bucket.Id id = Bucket.Id.generate();
        Bucket bucket = Bucket.create(id, bucketName, Region.EU_WEST_1, StorageClass.STANDARD);
        repository.save(bucket).block();
    }

    @When("I create bucket {string}")
    public void createBucket(String bucketName) {
        Bucket.Id id = Bucket.Id.generate();
        Bucket bucket = Bucket.create(id, bucketName, Region.EU_WEST_1, StorageClass.STANDARD);
        result = service.createBucket(bucket);
    }

    @Then("the result is a Created<Bucket> with version {long}")
    public void resultIsCreatedBucketWithVersion(long version) {
        assertNotNull(result);
        StepVerifier.create(result)
            .expectNextMatches(cr -> {
                if (cr instanceof CommandResult cmdResult) {
                    return switch (cmdResult) {
                        case CommandResult.Created created ->
                            created.version() == version;
                        default -> false;
                    };
                }
                return false;
            })
            .verifyComplete();
    }

    @Then("the event BucketCreated is recorded")
    public void eventBucketCreatedRecorded() {
        assertNotNull(result);
        StepVerifier.create(result)
            .expectNextMatches(cr -> {
                if (cr instanceof CommandResult cmdResult) {
                    List<ObjectStoreEvent> events = cmdResult.events();
                    return events.stream().anyMatch(e -> e instanceof ObjectStoreEvent.BucketCreated);
                }
                return false;
            })
            .verifyComplete();
    }

    @Then("the result is Mono.error with BucketAlreadyExistsException")
    public void resultIsErrorBucketAlreadyExists() {
        assertNotNull(result);
        StepVerifier.create(result)
            .expectErrorMatches(t ->
                t instanceof BucketAlreadyExistsException &&
                t.getMessage().contains("my-bucket")
            )
            .verify();
    }

    @Then("the result is a Bucket named {string}")
    public void resultIsBucketNamed(String bucketName) {
        assertNotNull(result);
        StepVerifier.create(result)
            .expectNextMatches(b -> {
                if (b instanceof Bucket bucket) {
                    return bucket.name().equals(bucketName);
                }
                return false;
            })
            .verifyComplete();
    }

    @Then("the result is Mono.empty (not found)")
    public void resultIsMonoEmpty() {
        assertNotNull(result);
        StepVerifier.create(result)
            .verifyComplete();
    }

    @When("I find bucket by name {string}")
    public void findBucketByName(String bucketName) {
        result = service.findByName(bucketName);
    }

    @When("I delete bucket {string}")
    public void deleteBucket(String bucketName) {
        Bucket found = repository.findByName(bucketName).block();
        if (found != null) {
            result = service.deleteBucket(found);
        } else {
            result = service.deleteBucket(
                Bucket.create(Bucket.Id.generate(), bucketName, Region.EU_WEST_1, StorageClass.STANDARD)
            );
        }
    }

    @Then("the result is a Deleted<Bucket> with version {long}")
    public void resultIsDeletedBucketWithVersion(long version) {
        assertNotNull(result);
        StepVerifier.create(result)
            .expectNextMatches(cr -> {
                if (cr instanceof CommandResult cmdResult) {
                    return switch (cmdResult) {
                        case CommandResult.Deleted deleted ->
                            deleted.version() == version;
                        default -> false;
                    };
                }
                return false;
            })
            .verifyComplete();
    }

    @Then("the event BucketDeleted is recorded")
    public void eventBucketDeletedRecorded() {
        assertNotNull(result);
        StepVerifier.create(result)
            .expectNextMatches(cr -> {
                if (cr instanceof CommandResult cmdResult) {
                    List<ObjectStoreEvent> events = cmdResult.events();
                    return events.stream().anyMatch(e -> e instanceof ObjectStoreEvent.BucketDeleted);
                }
                return false;
            })
            .verifyComplete();
    }

    @Then("the result is Mono.error with BucketNotFoundException")
    public void resultIsErrorBucketNotFound() {
        assertNotNull(result);
        StepVerifier.create(result)
            .expectErrorMatches(t ->
                t instanceof BucketNotFoundException
            )
            .verify();
    }

    @When("I set CORS configuration on bucket {string} with origins [\"*\"]")
    public void setCorsConfiguration(String bucketName) {
        Bucket found = repository.findByName(bucketName).block();
        assertNotNull(found);
        CorsConfiguration corsConfig = new CorsConfiguration(
            List.of(new CorsConfiguration.CorsRule(
                List.of("*"), List.of("GET"), List.of("*"), 3600, List.of(), "cors1"
            ))
        );
        Bucket updated = found.withBucketConfig(BucketConfig.EMPTY.withCorsConfiguration(corsConfig));
        result = service.createBucket(updated);
    }

    @Then("the result is an Updated<Bucket> with version {long}")
    public void resultIsUpdatedBucketWithVersion(long version) {
        assertNotNull(result);
        StepVerifier.create(result)
            .expectNextMatches(cr -> {
                if (cr instanceof CommandResult cmdResult) {
                    return switch (cmdResult) {
                        case CommandResult.Updated updated ->
                            updated.version() == version;
                        default -> false;
                    };
                }
                return false;
            })
            .verifyComplete();
    }

    @Then("the event BucketConfigurationChanged is recorded")
    public void eventBucketConfigurationChangedRecorded() {
        assertNotNull(result);
        StepVerifier.create(result)
            .expectNextMatches(cr -> {
                if (cr instanceof CommandResult cmdResult) {
                    List<ObjectStoreEvent> events = cmdResult.events();
                    return events.stream().anyMatch(e -> e instanceof ObjectStoreEvent.BucketConfigChanged);
                }
                return false;
            })
            .verifyComplete();
    }

    @Then("the bucket has CORS configuration with origins [\"*\"]")
    public void bucketHasCorsWithOrigins() {
        assertNotNull(result);
        StepVerifier.create(result)
            .expectNextMatches(cr -> {
                if (cr instanceof CommandResult cmdResult) {
                    Bucket b = (Bucket) cmdResult.aggregate();
                    return b.bucketConfig() != null &&
                           b.bucketConfig().getCorsConfiguration().get().corsRules() != null &&
                           b.bucketConfig().getCorsConfiguration().get().corsRules().stream()
                               .anyMatch(r -> r.allowedOrigins().contains("*"));
                }
                return false;
            })
            .verifyComplete();
    }
}
