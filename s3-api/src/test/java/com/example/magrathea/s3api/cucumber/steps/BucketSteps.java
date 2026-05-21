package com.example.magrathea.s3api.cucumber.steps;

import com.example.magrathea.objectstorage.application.service.BucketService;
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
    private BucketService bucketService;

    @Autowired
    private CommonSteps commonSteps;

    private String bucketName;

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

    @When("the bucket is deleted via S3 API")
    public void bucketDeleted() {
        webTestClient.delete()
            .uri("/{bucket}", bucketName)
            .exchange()
            .expectStatus().isNoContent();
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

    @Then("the bucket no longer appears in the bucket list")
    public void bucketNoLongerAppears() {
        var buckets = bucketService.findAll();
        assertFalse(buckets.stream().anyMatch(b -> b.name().equals(bucketName)));
    }
}
