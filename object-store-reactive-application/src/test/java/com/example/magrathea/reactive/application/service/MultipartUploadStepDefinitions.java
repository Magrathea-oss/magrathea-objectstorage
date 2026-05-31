package com.example.magrathea.reactive.application.service;

import com.example.magrathea.objectstore.domain.aggregate.Bucket;
import com.example.magrathea.objectstore.domain.aggregate.MultipartUpload;
import com.example.magrathea.objectstore.domain.event.ObjectStoreEvent;
import com.example.magrathea.objectstore.domain.valueobject.ObjectKey;
import com.example.magrathea.objectstore.domain.valueobject.PartNumber;
import com.example.magrathea.objectstore.domain.valueobject.UploadId;
import com.example.magrathea.objectstore.domain.valueobject.UploadPart;
import com.example.magrathea.objectstore.reactive.repository.application.CommandResult;
import com.example.magrathea.reactive.infrastructure.adapter.persistence.InMemoryReactiveBucketRepository;
import com.example.magrathea.reactive.infrastructure.adapter.persistence.InMemoryReactiveMultipartUploadRepository;
import com.example.magrathea.reactive.infrastructure.adapter.persistence.MultipartUploadNotFoundException;
import io.cucumber.java.Before;
import io.cucumber.java.en.Given;
import io.cucumber.java.en.When;
import io.cucumber.java.en.Then;
import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

public class MultipartUploadStepDefinitions {

    private InMemoryReactiveBucketRepository bucketRepository;
    private InMemoryReactiveMultipartUploadRepository uploadRepository;
    private ReactiveMultipartUploadService service;
    private Mono<?> result;
    private Bucket.Id testBucketId;
    private MultipartUpload activeUpload;
    private String testBucketName;

    @Before
    public void reset() {
        bucketRepository = new InMemoryReactiveBucketRepository();
        uploadRepository = new InMemoryReactiveMultipartUploadRepository();
        service = new ReactiveMultipartUploadService(uploadRepository, uploadRepository);
        result = null;
        testBucketId = null;
        activeUpload = null;
        testBucketName = null;
    }

    @Given("multipart bucket {string} exists")
    public void bucketExists(String bucketName) {
        Bucket.Id id = Bucket.Id.generate();
        Bucket bucket = Bucket.create(id, bucketName,
            com.example.magrathea.objectstore.domain.valueobject.Region.EU_WEST_1,
            com.example.magrathea.objectstore.domain.valueobject.StorageClass.STANDARD);
        bucketRepository.save(bucket).block();
        testBucketId = id;
        testBucketName = bucketName;
    }

    @Given("multipart upload {string} exists with {int} part(s)")
    public void multipartUploadExistsWithParts(String uploadName, int partCount) {
        assertNotNull(testBucketId);
        UploadId uploadId = UploadId.of(uploadName);
        MultipartUpload.Id id = MultipartUpload.Id.generate();
        MultipartUpload upload = MultipartUpload.create(id, testBucketId, ObjectKey.of(testBucketName, "large-file.zip"), uploadId);
        for (int i = 1; i <= partCount; i++) {
            UploadPart part = UploadPart.create(PartNumber.of(i), "etag-" + i, 1024);
            upload = upload.withPart(part);
        }
        MultipartUpload clean = upload.clearEvents();
        uploadRepository.save(clean).block();
        activeUpload = upload;
    }

    @Given("multipart upload {string} does not exist")
    public void multipartUploadDoesNotExist(String uploadName) {
        UploadId uploadId = UploadId.of(uploadName);
        var existing = uploadRepository.findById(uploadId).block();
        assertNull(existing, "Upload should not exist: " + uploadName);
    }

    @When("I initiate multipart upload with key {string}")
    public void initiateMultipartUpload(String objectKey) {
        assertNotNull(testBucketId);
        MultipartUpload.Id id = MultipartUpload.Id.generate();
        UploadId uploadId = UploadId.generate();
        MultipartUpload upload = MultipartUpload.create(id, testBucketId, ObjectKey.of(testBucketName, objectKey), uploadId);
        result = service.saveUpload(upload).cache();
    }

    @Then("the result is a Created<MultipartUpload> with version {long}")
    public void resultIsCreatedMultipartUploadWithVersion(long version) {
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

    @Then("the upload has {int} part(s)")
    public void uploadHasParts(int partCount) {
        assertNotNull(result);
        StepVerifier.create(result)
            .expectNextMatches(cr -> {
                if (cr instanceof CommandResult cmdResult) {
                    MultipartUpload upload = (MultipartUpload) cmdResult.aggregate();
                    return upload.partCount() == partCount;
                }
                return false;
            })
            .verifyComplete();
    }

    @Then("the event MultipartUploadCreated is recorded")
    public void eventMultipartUploadCreatedRecorded() {
        assertNotNull(result);
        StepVerifier.create(result)
            .expectNextMatches(cr -> {
                if (cr instanceof CommandResult cmdResult) {
                    List<ObjectStoreEvent> events = cmdResult.events();
                    return events.stream().anyMatch(e -> e instanceof ObjectStoreEvent.MultipartUploadCreated);
                }
                return false;
            })
            .verifyComplete();
    }

    @When("I add part number {int} to upload {string}")
    public void addPartToUpload(int partNumber, String uploadName) {
        assertNotNull(testBucketId);
        UploadId uploadId = UploadId.of(uploadName);
        MultipartUpload existing = uploadRepository.findById(uploadId).block();
        assertNotNull(existing);
        UploadPart part = UploadPart.create(PartNumber.of(partNumber), "etag-" + partNumber, 1024);
        MultipartUpload updated = existing.withPart(part);
        result = service.saveUpload(updated).cache();
    }

    @Then("the result is an Updated<MultipartUpload> with version {long}")
    public void resultIsUpdatedMultipartUploadWithVersion(long version) {
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

    @Then("the event PartUploaded is recorded")
    public void eventPartUploadedRecorded() {
        assertNotNull(result);
        StepVerifier.create(result)
            .expectNextMatches(cr -> {
                if (cr instanceof CommandResult cmdResult) {
                    List<ObjectStoreEvent> events = cmdResult.events();
                    return events.stream().anyMatch(e -> e instanceof ObjectStoreEvent.PartUploaded);
                }
                return false;
            })
            .verifyComplete();
    }

    @When("I complete upload {string}")
    public void completeUpload(String uploadName) {
        assertNotNull(testBucketId);
        UploadId uploadId = UploadId.of(uploadName);
        MultipartUpload existing = uploadRepository.findById(uploadId).block();
        assertNotNull(existing);
        MultipartUpload completed = existing.withCompleted();
        result = service.saveUpload(completed).cache();
    }

    @Then("the event MultipartUploadCompleted is recorded")
    public void eventMultipartUploadCompletedRecorded() {
        assertNotNull(result);
        StepVerifier.create(result)
            .expectNextMatches(cr -> {
                if (cr instanceof CommandResult cmdResult) {
                    List<ObjectStoreEvent> events = cmdResult.events();
                    return events.stream().anyMatch(e -> e instanceof ObjectStoreEvent.MultipartUploadCompleted);
                }
                return false;
            })
            .verifyComplete();
    }

    @When("I abort upload {string}")
    public void abortUpload(String uploadName) {
        assertNotNull(testBucketId);
        UploadId uploadId = UploadId.of(uploadName);
        MultipartUpload existing = uploadRepository.findById(uploadId).block();
        assertNotNull(existing);
        MultipartUpload aborted = existing.withAborted();
        result = service.saveUpload(aborted).cache();
    }

    @Then("the event MultipartUploadAborted is recorded")
    public void eventMultipartUploadAbortedRecorded() {
        assertNotNull(result);
        StepVerifier.create(result)
            .expectNextMatches(cr -> {
                if (cr instanceof CommandResult cmdResult) {
                    List<ObjectStoreEvent> events = cmdResult.events();
                    return events.stream().anyMatch(e -> e instanceof ObjectStoreEvent.MultipartUploadAborted);
                }
                return false;
            })
            .verifyComplete();
    }

    @When("I find upload by ID {string}")
    public void findUploadById(String uploadName) {
        UploadId uploadId = UploadId.of(uploadName);
        result = service.findById(uploadId);
    }

    @Then("the multipart result is Mono.empty (not found)")
    public void multipartResultIsMonoEmpty() {
        assertNotNull(result);
        StepVerifier.create(result)
            .verifyComplete();
    }

    @Then("the result is a MultipartUpload with {int} parts")
    public void resultIsMultipartUploadWithParts(int partCount) {
        assertNotNull(result);
        StepVerifier.create(result)
            .expectNextMatches(upload -> {
                if (upload instanceof MultipartUpload mu) {
                    return mu.partCount() == partCount;
                }
                return false;
            })
            .verifyComplete();
    }
}
