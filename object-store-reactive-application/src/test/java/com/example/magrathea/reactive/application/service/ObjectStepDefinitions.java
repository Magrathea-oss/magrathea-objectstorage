package com.example.magrathea.reactive.application.service;

import com.example.magrathea.objectstore.domain.aggregate.ActiveS3Object;
import com.example.magrathea.objectstore.domain.aggregate.Bucket;
import com.example.magrathea.objectstore.domain.aggregate.S3Object;
import com.example.magrathea.objectstore.domain.event.ObjectStoreEvent;
import com.example.magrathea.objectstore.domain.valueobject.ObjectChecksum;
import com.example.magrathea.objectstore.domain.valueobject.ObjectKey;
import com.example.magrathea.objectstore.reactive.repository.application.CommandResult;
import com.example.magrathea.reactive.infrastructure.adapter.persistence.InMemoryReactiveBucketRepository;
import com.example.magrathea.reactive.infrastructure.adapter.persistence.InMemoryReactiveS3ObjectRepository;
import com.example.magrathea.objectstore.reactive.repository.application.S3ObjectNotFoundException;
import io.cucumber.java.Before;
import io.cucumber.java.en.Given;
import io.cucumber.java.en.When;
import io.cucumber.java.en.Then;
import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;

import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;

public class ObjectStepDefinitions {

    private InMemoryReactiveBucketRepository bucketRepository;
    private InMemoryReactiveS3ObjectRepository objectRepository;
    private ReactiveObjectService service;
    private Mono<?> result;
    private Bucket.Id testBucketId;
    private String testBucketName;

    @Before
    public void reset() {
        bucketRepository = new InMemoryReactiveBucketRepository();
        objectRepository = new InMemoryReactiveS3ObjectRepository();
        service = new ReactiveObjectService(objectRepository, objectRepository, bucketRepository);
        result = null;
        testBucketId = null;
        testBucketName = null;
    }

    @Given("object store bucket {string} exists")
    public void bucketExists(String bucketName) {
        Bucket.Id id = Bucket.Id.generate();
        Bucket bucket = Bucket.create(id, bucketName,
            com.example.magrathea.objectstore.domain.valueobject.Region.EU_WEST_1,
            com.example.magrathea.objectstore.domain.valueobject.StorageClass.STANDARD);
        bucketRepository.save(bucket).block();
        testBucketId = id;
        testBucketName = bucketName;
    }

    @Given("object {string} does not exist")
    public void objectDoesNotExist(String objectKey) {
        assertNotNull(testBucketId);
        var existing = objectRepository.findByBucketAndKey(testBucketId, ObjectKey.of(testBucketName, objectKey)).block();
        assertNull(existing, "Object should not exist: " + objectKey);
    }

    @Given("object {string} exists")
    public void objectExists(String objectKey) {
        assertNotNull(testBucketId);
        var checksum = ObjectChecksum.of(Set.of(
            new com.example.magrathea.objectstore.domain.valueobject.ChecksumValue(
                com.example.magrathea.objectstore.domain.valueobject.ChecksumAlgorithm.MD5, "abc123")), null);
        ActiveS3Object object = ActiveS3Object.create(
            ObjectKey.of(testBucketName, objectKey), null,
            Map.of(), null, checksum, 1024);
        objectRepository.save(object).block();
    }

    @When("I create object {string} with content descriptor of size {long}")
    public void createObjectWithContentDescriptor(String objectKey, long size) {
        assertNotNull(testBucketId);
        var checksum = ObjectChecksum.of(Set.of(
            new com.example.magrathea.objectstore.domain.valueobject.ChecksumValue(
                com.example.magrathea.objectstore.domain.valueobject.ChecksumAlgorithm.MD5, "abc123")), null);
        ActiveS3Object active = ActiveS3Object.create(
            ObjectKey.of(testBucketName, objectKey), null,
            Map.of(), null, checksum, size);
        result = service.saveObject(active).cache();
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
                    return events.stream().anyMatch(e -> e instanceof ObjectStoreEvent.ObjectCreated);
                }
                return false;
            })
            .verifyComplete();
    }


    @Then("the event ObjectCreated is recorded")
    public void eventObjectCreatedRecorded() {
        assertNotNull(result);
        StepVerifier.create(result)
            .expectNextMatches(cr -> {
                if (cr instanceof CommandResult cmdResult) {
                    List<ObjectStoreEvent> events = cmdResult.events();
                    return events.stream().anyMatch(e -> e instanceof ObjectStoreEvent.ObjectCreated);
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
                    return obj.size() == size;
                }
                return false;
            })
            .verifyComplete();
    }

    @When("I find object {string} in bucket {string}")
    public void findObjectInBucket(String objectKey, String bucketName) {
        assertNotNull(testBucketId);
        result = service.findByBucketAndKey(testBucketId, ObjectKey.of(bucketName, objectKey));
    }

    @Then("the result is an S3Object with key {string}")
    public void resultIsS3ObjectWithKey(String objectKey) {
        assertNotNull(result);
        StepVerifier.create(result)
            .expectNextMatches(obj -> {
                if (obj instanceof S3Object s3) {
                    return s3.key().equals(ObjectKey.of(testBucketName, objectKey));
                }
                return false;
            })
            .verifyComplete();
    }

    @When("I delete object {string}")
    public void deleteObject(String objectKey) {
        assertNotNull(testBucketId);
        S3Object found = objectRepository.findByBucketAndKey(testBucketId, ObjectKey.of(testBucketName, objectKey)).block();
        if (found != null) {
            result = service.deleteObject(found).cache();
        } else {
            var checksum = ObjectChecksum.of(Set.of(
                new com.example.magrathea.objectstore.domain.valueobject.ChecksumValue(
                    com.example.magrathea.objectstore.domain.valueobject.ChecksumAlgorithm.MD5, "abc123")), null);
            ActiveS3Object dummy = ActiveS3Object.create(
                ObjectKey.of(testBucketName, objectKey), null,
                Map.of(), null, checksum, 0);
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
