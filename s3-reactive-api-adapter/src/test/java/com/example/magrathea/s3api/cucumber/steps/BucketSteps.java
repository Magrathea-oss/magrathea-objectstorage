package com.example.magrathea.s3api.cucumber.steps;

import com.example.magrathea.reactive.application.service.ReactiveBucketService;
import io.cucumber.java.en.Given;
import io.cucumber.java.en.When;
import io.cucumber.java.en.Then;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.test.web.reactive.server.WebTestClient;

import static org.junit.jupiter.api.Assertions.*;

public class BucketSteps {

    @Autowired
    private WebTestClient webTestClient;

    @Autowired
    private ReactiveBucketService bucketService;

    @Autowired
    private CommonSteps commonSteps;

    private String bucketName;

    public String getBucketName() { return bucketName; }

    @Given("a bucket name {string}")
    public void aBucketName(String name) {
        bucketName = name;
    }

    @Given("bucket {string} exists")
    public void bucketExists(String name) {
        bucketName = name;
        webTestClient.put()
            .uri("/{bucket}", name)
            .exchange()
            .expectStatus().isOk();
    }

    @Given("bucket {string} does not exist")
    public void bucketDoesNotExist(String name) {
        bucketName = name;
        webTestClient.head()
            .uri("/{bucket}", name)
            .exchange()
            .expectStatus().isEqualTo(HttpStatus.NOT_FOUND);
    }

    @When("the bucket is created via S3 API")
    public void bucketCreated() {
        var status = webTestClient.put()
            .uri("/{bucket}", bucketName)
            .exchange()
            .returnResult()
            .getStatus();
        commonSteps.setResponseStatus(status);
    }

    @When("HEAD request is sent for bucket {string}")
    public void headBucket(String name) {
        var status = webTestClient.head()
            .uri("/{bucket}", name)
            .exchange()
            .returnResult()
            .getStatus();
        commonSteps.setResponseStatus(status);
    }

    @When("bucket location is requested for {string}")
    public void bucketLocationRequested(String name) {
        var result = webTestClient.get()
            .uri("/{bucket}?location", name)
            .accept(MediaType.APPLICATION_XML)
            .exchange()
            .expectBody(String.class)
            .returnResult();
        commonSteps.setResponseBody(result.getResponseBody());
        commonSteps.setResponseStatus(result.getStatus());
    }

    @When("bucket versioning is requested for {string}")
    public void bucketVersioningRequested(String name) {
        var result = webTestClient.get()
            .uri("/{bucket}?versioning", name)
            .accept(MediaType.APPLICATION_XML)
            .exchange()
            .expectBody(String.class)
            .returnResult();
        commonSteps.setResponseBody(result.getResponseBody());
        commonSteps.setResponseStatus(result.getStatus());
    }

    @When("bucket versioning is enabled for {string}")
    public void bucketVersioningEnabled(String name) {
        var body = "<VersioningConfiguration><Status>Enabled</Status></VersioningConfiguration>";
        var status = webTestClient.put()
            .uri("/{bucket}?versioning", name)
            .contentType(MediaType.APPLICATION_XML)
            .bodyValue(body)
            .exchange()
            .returnResult()
            .getStatus();
        commonSteps.setResponseStatus(status);
    }

    @When("bucket ACL {string} is applied to {string}")
    public void bucketAclApplied(String acl, String name) {
        var status = webTestClient.put()
            .uri("/{bucket}?acl", name)
            .header("x-amz-acl", acl)
            .exchange()
            .returnResult()
            .getStatus();
        commonSteps.setResponseStatus(status);
    }

    @When("bucket ACL is requested for {string}")
    public void bucketAclRequested(String name) {
        var result = webTestClient.get()
            .uri("/{bucket}?acl", name)
            .accept(MediaType.APPLICATION_XML)
            .exchange()
            .expectBody(String.class)
            .returnResult();
        commonSteps.setResponseBody(result.getResponseBody());
        commonSteps.setResponseStatus(result.getStatus());
    }

    @When("bucket tag {string} = {string} is applied to {string}")
    public void bucketTagApplied(String key, String value, String name) {
        var body = "<Tagging><TagSet><Tag><Key>" + key + "</Key><Value>" + value + "</Value></Tag></TagSet></Tagging>";
        var status = webTestClient.put()
            .uri("/{bucket}?tagging", name)
            .contentType(MediaType.APPLICATION_XML)
            .bodyValue(body)
            .exchange()
            .returnResult()
            .getStatus();
        commonSteps.setResponseStatus(status);
    }

    @When("bucket tags are requested for {string}")
    public void bucketTagsRequested(String name) {
        var result = webTestClient.get()
            .uri("/{bucket}?tagging", name)
            .accept(MediaType.APPLICATION_XML)
            .exchange()
            .expectBody(String.class)
            .returnResult();
        commonSteps.setResponseBody(result.getResponseBody());
        commonSteps.setResponseStatus(result.getStatus());
    }

    @When("bucket tags are deleted for {string}")
    public void bucketTagsDeleted(String name) {
        var status = webTestClient.delete()
            .uri("/{bucket}?tagging", name)
            .exchange()
            .returnResult()
            .getStatus();
        commonSteps.setResponseStatus(status);
    }

    @When("the buckets are listed")
    public void bucketsListed() {
        var result = webTestClient.get()
            .uri("/")
            .accept(MediaType.APPLICATION_XML)
            .exchange()
            .expectBody(String.class)
            .returnResult();
        commonSteps.setResponseBody(result.getResponseBody());
        commonSteps.setResponseStatus(result.getStatus());
    }

    @When("the bucket is deleted via S3 API")
    public void bucketDeleted() {
        var status = webTestClient.delete()
            .uri("/{bucket}", bucketName)
            .exchange()
            .returnResult()
            .getStatus();
        commonSteps.setResponseStatus(status);
    }

    @Then("the response status is {int}")
    public void responseStatusIs(int status) {
        assertEquals(status, commonSteps.getResponseStatus().value());
    }

    @Then("the bucket appears in the bucket list")
    public void bucketAppearsInList() {
        var body = webTestClient.get()
            .uri("/")
            .accept(MediaType.APPLICATION_XML)
            .exchange()
            .expectBody(String.class)
            .returnResult()
            .getResponseBody();
        assertNotNull(body);
        assertTrue(body.contains(bucketName));
    }
    @Then("the metadata response contains {string}")
    public void metadataResponseContains(String expected) {
        var body = commonSteps.getResponseBody();
        assertNotNull(body);
        assertTrue(body.contains(expected));
    }

    @Then("the bucket location response contains {string}")
    public void bucketLocationResponseContains(String expected) {
        var body = commonSteps.getResponseBody();
        assertNotNull(body);
        assertTrue(body.contains(expected));
    }

    @Then("the bucket versioning response contains {string}")
    public void bucketVersioningResponseContains(String expected) {
        var body = commonSteps.getResponseBody();
        assertNotNull(body);
        assertTrue(body.contains(expected));
    }

    @Then("bucket versioning for {string} is {string}")
    public void bucketVersioningIs(String name, String expected) {
        var body = webTestClient.get()
            .uri("/{bucket}?versioning", name)
            .accept(MediaType.APPLICATION_XML)
            .exchange()
            .expectBody(String.class)
            .returnResult()
            .getResponseBody();
        assertNotNull(body);
        assertTrue(body.contains(expected));
    }

    @Then("the bucket no longer appears in the bucket list")
    public void bucketNoLongerAppears() {
        var buckets = bucketService.findAllBuckets().collectList().block();
        assertFalse(buckets.stream().anyMatch(b -> b.name().equals(bucketName)));
    }
}
