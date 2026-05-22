package com.example.magrathea.s3api.cucumber.steps;

import com.example.magrathea.objectstorage.application.service.MultipartUploadService;
import io.cucumber.java.en.Given;
import io.cucumber.java.en.When;
import io.cucumber.java.en.Then;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.test.web.reactive.server.WebTestClient;

import java.util.regex.Pattern;

import static org.junit.jupiter.api.Assertions.*;

public class MultipartSteps {

    @Autowired
    private WebTestClient webTestClient;

    @Autowired
    private CommonSteps commonSteps;

    @Autowired
    private BucketSteps bucketSteps;

    @Autowired
    private MultipartUploadService multipartUploadService;

    private String uploadId;
    private String partEtag;

    @Given("an object key {string}")
    public void anObjectKey(String key) {
        commonSteps.set("objectKey", key);
    }

    @Given("a multipart upload is initiated for the bucket")
    public void multipartUploadIsInitiated() {
        var bucket = bucketSteps.getBucketName();
        var key = commonSteps.getString("objectKey");
        var result = webTestClient.post()
            .uri("/{bucket}/{key}?uploads", bucket, key)
            .exchange()
            .expectStatus().isOk()
            .expectBody(String.class)
            .returnResult();
        var body = result.getResponseBody();
        assertNotNull(body, "Initiate multipart upload response body is null");
        var matcher = Pattern.compile("<UploadId>([^<]+)</UploadId>").matcher(body);
        assertTrue(matcher.find(), "UploadId not found in response");
        uploadId = matcher.group(1);
        commonSteps.set("uploadId", uploadId);
        commonSteps.setResponseBody(body);
        commonSteps.setResponseStatus(200);
    }

    @When("a multipart upload is initiated via API")
    public void whenMultipartUploadInitiated() {
        multipartUploadIsInitiated();
    }

    @When("a multipart upload is initiated for nonexistent bucket")
    public void whenMultipartUploadNonexistentBucket() {
        var bucket = commonSteps.getString("bucketName");
        var key = commonSteps.getString("objectKey");
        webTestClient.post()
            .uri("/{bucket}/{key}?uploads", bucket, key)
            .exchange()
            .expectStatus().isNotFound();
        commonSteps.setResponseStatus(404);
    }

    @When("a part number {int} is uploaded with content {string}")
    public void whenPartUploaded(int partNumber, String content) {
        var bucket = bucketSteps.getBucketName();
        var key = commonSteps.getString("objectKey");
        var uid = commonSteps.getString("uploadId");
        var result = webTestClient.put()
            .uri("/{bucket}/{key}?uploadId={uid}&partNumber={pn}", bucket, key, uid, partNumber)
            .bodyValue(content)
            .exchange()
            .expectBody(String.class)
            .returnResult();
        var body = result.getResponseBody();
        assertNotNull(body, "Upload part response body is null");
        var matcher = Pattern.compile("<ETag>([^<]+)</ETag>").matcher(body);
        assertTrue(matcher.find(), "ETag not found in UploadPart response");
        partEtag = matcher.group(1);
        commonSteps.setResponseBody(body);
        commonSteps.setResponseStatus(200);
    }

    @Given("part number {int} is uploaded")
    public void partNumberIsUploaded(int partNumber) {
        var bucket = bucketSteps.getBucketName();
        var key = commonSteps.getString("objectKey");
        var uid = commonSteps.getString("uploadId");
        var response = webTestClient.put()
            .uri("/{bucket}/{key}?uploadId={uid}&partNumber={pn}", bucket, key, uid, partNumber)
            .bodyValue("test content")
            .exchange()
            .expectBody(String.class)
            .returnResult()
            .getResponseBody();
        assertNotNull(response);
        var matcher = Pattern.compile("<ETag>([^<]+)</ETag>").matcher(response);
        assertTrue(matcher.find(), "ETag not found");
        commonSteps.set("partEtag", matcher.group(1));
    }

    @When("the parts are listed")
    public void whenPartsListed() {
        var bucket = bucketSteps.getBucketName();
        var key = commonSteps.getString("objectKey");
        var uid = commonSteps.getString("uploadId");
        var result = webTestClient.get()
            .uri("/{bucket}/{key}?uploadId={uid}", bucket, key, uid)
            .exchange()
            .expectBody(String.class)
            .returnResult();
        var body = result.getResponseBody();
        assertNotNull(body, "List parts response body is null");
        commonSteps.setResponseBody(body);
        commonSteps.setResponseStatus(200);
    }

    @Then("the list parts response contains {int} part")
    public void thenListPartsContains(int count) {
        var body = commonSteps.getResponseBody();
        assertNotNull(body);
        var matcher = Pattern.compile("<Part>").matcher(body);
        int actual = (int) matcher.results().count();
        assertEquals(count, actual, "Expected " + count + " parts but found " + actual);
    }

    @When("the multipart upload is completed")
    public void whenMultipartUploadCompleted() {
        var bucket = bucketSteps.getBucketName();
        var key = commonSteps.getString("objectKey");
        var uid = commonSteps.getString("uploadId");
        var result = webTestClient.post()
            .uri("/{bucket}/{key}?uploadId={uid}", bucket, key, uid)
            .exchange()
            .expectBody(String.class)
            .returnResult();
        var body = result.getResponseBody();
        assertNotNull(body, "Complete multipart upload response body is null");
        commonSteps.setResponseBody(body);
        commonSteps.setResponseStatus(200);
    }

    @Then("the complete response contains an ETag")
    public void thenCompleteContainsEtag() {
        var body = commonSteps.getResponseBody();
        assertNotNull(body);
        assertTrue(body.contains("<ETag>"), "Complete response should contain ETag");
    }

    @Then("the initiate response contains an upload ID")
    public void thenInitiateContainsUploadId() {
        var body = commonSteps.getResponseBody();
        assertNotNull(body);
        assertTrue(body.contains("<UploadId>"), "Initiate response should contain UploadId");
    }

    @Then("the upload part response contains an ETag")
    public void thenUploadPartContainsEtag() {
        var body = commonSteps.getResponseBody();
        assertNotNull(body);
        assertTrue(body.contains("<ETag>"), "Upload part response should contain ETag");
    }

    @When("the multipart uploads are listed")
    public void whenMultipartUploadsListed() {
        var bucket = bucketSteps.getBucketName();
        var result = webTestClient.get()
            .uri("/{bucket}?uploads", bucket)
            .exchange()
            .expectBody(String.class)
            .returnResult();
        var body = result.getResponseBody();
        assertNotNull(body, "List uploads response body is null");
        commonSteps.setResponseBody(body);
        commonSteps.setResponseStatus(200);
    }

    @Then("the list uploads response contains {int} upload")
    public void thenListUploadsContains(int count) {
        var body = commonSteps.getResponseBody();
        assertNotNull(body);
        var matcher = Pattern.compile("<Upload>").matcher(body);
        int actual = (int) matcher.results().count();
        assertEquals(count, actual, "Expected " + count + " uploads but found " + actual);
    }

    @When("the multipart upload is aborted")
    public void whenMultipartUploadAborted() {
        var bucket = bucketSteps.getBucketName();
        var key = commonSteps.getString("objectKey");
        var uid = commonSteps.getString("uploadId");
        webTestClient.delete()
            .uri("/{bucket}/{key}?uploadId={uid}", bucket, key, uid)
            .exchange()
            .expectStatus().isNoContent();
        commonSteps.setResponseStatus(204);
    }

    @When("an invalid part is uploaded with uploadId {string}")
    public void whenInvalidPartUploaded(String uid) {
        var bucket = bucketSteps.getBucketName();
        var key = commonSteps.getString("objectKey");
        webTestClient.put()
            .uri("/{bucket}/{key}?uploadId={uid}&partNumber=1", bucket, key, uid)
            .bodyValue("content")
            .exchange()
            .expectStatus().isNotFound();
        commonSteps.setResponseStatus(404);
    }

    @When("a multipart upload is completed with uploadId {string}")
    public void whenCompleteWithInvalidUploadId(String uid) {
        var bucket = bucketSteps.getBucketName();
        var key = commonSteps.getString("objectKey");
        webTestClient.post()
            .uri("/{bucket}/{key}?uploadId={uid}", bucket, key, uid)
            .exchange()
            .expectStatus().isNotFound();
        commonSteps.setResponseStatus(404);
    }
}
