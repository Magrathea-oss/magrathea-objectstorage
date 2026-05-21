package com.example.magrathea.s3api.cucumber.steps;

import io.cucumber.java.en.Given;
import io.cucumber.java.en.When;
import io.cucumber.java.en.Then;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.test.web.reactive.server.WebTestClient;

import static org.junit.jupiter.api.Assertions.*;

public class ObjectSteps {

    @Autowired
    private WebTestClient webTestClient;

    @Autowired
    private CommonSteps commonSteps;

    private String objectKey;
    private String objectContent;
    private String retrievedContent;

    @Given("an object with key {string} and content {string}")
    public void anObjectWithKeyAndContent(String key, String content) {
        objectKey = key;
        objectContent = content;
    }

    @Given("object {string} exists with content {string}")
    public void objectExistsWithContent(String key, String content) {
        objectKey = key;
        objectContent = content;
        webTestClient.put()
            .uri("/test-bucket/{key}", key)
            .bodyValue(content)
            .exchange()
            .expectStatus().isOk();
    }

    @Given("object {string} exists")
    public void objectExists(String key) {
        objectKey = key;
        webTestClient.put()
            .uri("/test-bucket/{key}", key)
            .bodyValue("dummy content")
            .exchange()
            .expectStatus().isOk();
    }

    @Given("object {string} does not exist")
    public void objectDoesNotExist(String key) {
        objectKey = key;
        webTestClient.head()
            .uri("/test-bucket/{key}", key)
            .exchange()
            .expectStatus().isEqualTo(HttpStatus.NOT_FOUND);
    }

    @When("the object is stored via S3 API")
    public void objectStored() {
        var status = webTestClient.put()
            .uri("/test-bucket/{key}", objectKey)
            .bodyValue(objectContent)
            .exchange()
            .returnResult()
            .getStatus();
        commonSteps.setResponseStatus(status);
    }

    @When("the object is retrieved via S3 API")
    public void objectRetrieved() {
        var result = webTestClient.get()
            .uri("/test-bucket/{key}", objectKey)
            .exchange()
            .expectBody(String.class)
            .returnResult();
        retrievedContent = result.getResponseBody();
        commonSteps.setResponseStatus(result.getStatus());
    }

    @When("HEAD request is sent for object {string}")
    public void headObject(String key) {
        var status = webTestClient.head()
            .uri("/test-bucket/{key}", key)
            .exchange()
            .returnResult()
            .getStatus();
        commonSteps.setResponseStatus(status);
    }

    @When("the object is deleted via S3 API")
    public void objectDeleted() {
        webTestClient.delete()
            .uri("/test-bucket/{key}", objectKey)
            .exchange()
            .expectStatus().isNoContent();
    }

    @Then("the object appears in the object list")
    public void objectAppearsInList() {
        var body = webTestClient.get()
            .uri("/test-bucket")
            .accept(MediaType.APPLICATION_XML)
            .exchange()
            .expectBody(String.class)
            .returnResult()
            .getResponseBody();
        assertNotNull(body);
        assertTrue(body.contains(objectKey));
    }

    @Then("the content is {string}")
    public void contentIs(String expectedContent) {
        assertEquals(expectedContent, retrievedContent);
    }

    @Then("the object no longer appears in the object list")
    public void objectNoLongerAppears() {
        var body = webTestClient.get()
            .uri("/test-bucket")
            .accept(MediaType.APPLICATION_XML)
            .exchange()
            .expectBody(String.class)
            .returnResult()
            .getResponseBody();
        assertNotNull(body);
        assertFalse(body.contains(objectKey));
    }
}
