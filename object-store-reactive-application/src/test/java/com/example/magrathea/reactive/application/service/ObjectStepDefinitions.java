package com.example.magrathea.reactive.application.service;

import com.example.magrathea.objectstore.domain.aggregate.Bucket;
import com.example.magrathea.objectstore.domain.aggregate.S3Object;
import com.example.magrathea.objectstore.domain.event.ObjectStoreEvent;
import com.example.magrathea.objectstore.domain.valueobject.ContentDescriptor;
import com.example.magrathea.objectstore.domain.valueobject.ObjectKey;
import com.example.magrathea.objectstore.reactive.repository.application.CommandResult;
import com.example.magrathea.reactive.infrastructure.adapter.persistence.InMemoryReactiveBucketRepository;
import com.example.magrathea.reactive.infrastructure.adapter.persistence.InMemoryReactiveS3ObjectRepository;
import com.example.magrathea.reactive.infrastructure.adapter.persistence.S3ObjectNotFoundException;
import io.cucumber.java.Before;
import io.cucumber.java.en.Given;
import io.cucumber.java.en.When;
import io.cucumber.java.en.Then;
import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

public class ObjectStepDefinitions {

    private InMemoryReactiveBucketRepository bucketRepository;
    private InMemoryReactiveS3ObjectRepository objectRepository;
    private ReactiveObjectService service;
    private Mono<?> result;
    private Bucket.Id testBucketId;

    @Before
    public void reset() {
        bucketRepository = new InMemoryReactiveBucketRepository();
        objectRepository = new InMemoryReactiveS3ObjectRepository();
        service = new ReactiveObjectService(objectRepository, objectRepository, bucketRepository);
        result = null;
        testBucketId = null;
    }

    @Given("object store bucket {string} exists")
    public void bucketExists(String bucketName) {
        Bucket.Id id = Bucket.Id.generate();
        Bucket bucket = Bucket.create(id, bucketName,
            com.example.magrathea.objectstore.domain.valueobject.Region.EU_WEST_1,
            com.example.magrathea.objectstore.domain.valueobject.StorageClass.STANDARD);
        bucketRepository.save(bucket).block();
        testBucketId = id;
    }

    @Given("object {string} does not exist")
    public void objectDoesNotExist(String objectKey) {
        assertNotNull(testBucketId);
        var existing = objectRepository.findByBucketAndKey(testBucketId, ObjectKey.of(objectKey)).block();
        assertNull(existing, "Object should not exist: " + objectKey);
    }

    @Given("object {string} exists")
    public void objectExists(String objectKey) {
        assertNotNull(testBucketId);
        S3Object.Id id = S3Object.Id.generate();
        S3Object object = S3Object.create(id, testBucketId, ObjectKey.of(objectKey), "text/plain", null, null, 1024, Map.of());
        objectRepository.save(object).block();
    }

    @When("I create object {string} with content descriptor of size {long}")
    public void createObjectWithContentDescriptor(String objectKey, long size) {
        assertNotNull(testBucketId);
        S3Object.Id id = S3Object.Id.generate();
        S3Object object = S3Object.create(id, testBucketId, ObjectKey.of(objectKey), "text/plain", null, null, size, Map.of());
        ContentDescriptor descriptor = ContentDescriptor.of(size, "abc123", "content-" + id.value());
        S3Object withContent = object.withContent(descriptor);
        result = service.saveObject(withContent).cache();
    }

    @Then("the result is a Created<S3Object> with version {long}")
    public void resultIsCreatedS3ObjectWithVersion(long version) {
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

    @Then("the event ContentDescriptorCreated is recorded")
    public void eventContentDescriptorCreatedRecorded() {
        assertNotNull(result);
        StepVerifier.create(result)
            .expectNextMatches(cr -> {
                if (cr instanceof CommandResult cmdResult) {
                    List<ObjectStoreEvent> events = cmdResult.events();
                    return events.stream().anyMatch(e -> e instanceof ObjectStoreEvent.ContentDescriptorCreated);
                }
                return false;
            })
            .verifyComplete();
    }

    @Then("the object has content descriptor with size {long}")
    public void objectHasContentDescriptorWithSize(long size) {
        assertNotNull(result);
        StepVerifier.create(result)
            .expectNextMatches(cr -> {
                if (cr instanceof CommandResult cmdResult) {
                    S3Object obj = (S3Object) cmdResult.aggregate();
                    return obj.contentDescriptor() != null && obj.contentDescriptor().size() == size;
                }
                return false;
            })
            .verifyComplete();
    }

    @When("I find object {string} in bucket {string}")
    public void findObjectInBucket(String objectKey, String bucketName) {
        assertNotNull(testBucketId);
        result = service.findByBucketAndKey(testBucketId, ObjectKey.of(objectKey));
    }

    @Then("the result is an S3Object with key {string}")
    public void resultIsS3ObjectWithKey(String objectKey) {
        assertNotNull(result);
        StepVerifier.create(result)
            .expectNextMatches(obj -> {
                if (obj instanceof S3Object s3) {
                    return s3.key().equals(ObjectKey.of(objectKey));
                }
                return false;
            })
            .verifyComplete();
    }

    @When("I delete object {string}")
    public void deleteObject(String objectKey) {
        assertNotNull(testBucketId);
        S3Object found = objectRepository.findByBucketAndKey(testBucketId, ObjectKey.of(objectKey)).block();
        if (found != null) {
            result = service.deleteObject(found).cache();
        } else {
            S3Object dummy = S3Object.create(S3Object.Id.generate(), testBucketId, ObjectKey.of(objectKey), null, null, null, 0, Map.of());
            result = service.deleteObject(dummy).cache();
        }
    }

    @Then("the result is a Deleted<S3Object> with version {long}")
    public void resultIsDeletedS3ObjectWithVersion(long version) {
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

    @Then("the event ObjectDeleted is recorded")
    public void eventObjectDeletedRecorded() {
        assertNotNull(result);
        StepVerifier.create(result)
            .expectNextMatches(cr -> {
                if (cr instanceof CommandResult cmdResult) {
                    List<ObjectStoreEvent> events = cmdResult.events();
                    return events.stream().anyMatch(e -> e instanceof ObjectStoreEvent.ObjectDeleted);
                }
                return false;
            })
            .verifyComplete();
    }

    @Then("the result is Mono.error with S3ObjectNotFoundException")
    public void resultIsErrorS3ObjectNotFound() {
        assertNotNull(result);
        StepVerifier.create(result)
            .expectErrorMatches(t ->
                t instanceof S3ObjectNotFoundException
            )
            .verify();
    }

    @Then("the object result is Mono.empty (not found)")
    public void resultIsMonoEmpty() {
        assertNotNull(result);
        StepVerifier.create(result)
            .verifyComplete();
    }
}
