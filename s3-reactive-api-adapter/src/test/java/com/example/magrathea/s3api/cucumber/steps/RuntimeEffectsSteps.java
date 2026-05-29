package com.example.magrathea.s3api.cucumber.steps;

import io.cucumber.java.en.Given;
import io.cucumber.java.en.Then;
import io.cucumber.java.en.When;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.test.web.reactive.server.WebTestClient;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

public class RuntimeEffectsSteps {

    @Autowired
    private WebTestClient webTestClient;

    @Autowired
    private CommonSteps commonSteps;

    @Given("object {string} exists in bucket {string} with content {string}")
    public void objectExistsInBucketWithContent(String key, String bucket, String content) {
        webTestClient.put()
            .uri("/{bucket}/{key}", bucket, key)
            .contentType(MediaType.TEXT_PLAIN)
            .bodyValue(content)
            .exchange()
            .expectStatus().isOk();
    }

    @When("object {string} in bucket {string} is retrieved with Origin {string}")
    public void objectRetrievedWithOrigin(String key, String bucket, String origin) {
        var result = webTestClient.get()
            .uri("/{bucket}/{key}", bucket, key)
            .header("Origin", origin)
            .exchange()
            .expectBody(String.class)
            .returnResult();
        commonSteps.setResponseStatus(result.getStatus());
        commonSteps.setResponseBody(result.getResponseBody());
        commonSteps.setResponseHeaders(result.getResponseHeaders());
    }

    @When("CORS preflight is sent to bucket {string} for origin {string} and method {string}")
    public void corsPreflightSentToBucket(String bucket, String origin, String method) {
        var result = webTestClient.options()
            .uri("/{bucket}", bucket)
            .header("Origin", origin)
            .header("Access-Control-Request-Method", method)
            .exchange()
            .expectBody(String.class)
            .returnResult();
        commonSteps.setResponseStatus(result.getStatus());
        commonSteps.setResponseBody(result.getResponseBody());
        commonSteps.setResponseHeaders(result.getResponseHeaders());
    }

    @When("bucket website is configured for {string} with index {string} and error document {string}")
    public void bucketWebsiteConfiguredWithIndexAndError(String bucket, String index, String errorDocument) {
        var body = "<WebsiteConfiguration>" +
            "<IndexDocument>" + index + "</IndexDocument>" +
            "<ErrorDocument>" + errorDocument + "</ErrorDocument>" +
            "</WebsiteConfiguration>";
        var result = webTestClient.put()
            .uri("/{bucket}?website", bucket)
            .contentType(MediaType.APPLICATION_XML)
            .bodyValue(body)
            .exchange()
            .returnResult();
        commonSteps.setResponseStatus(result.getStatus());
        commonSteps.setResponseHeaders(result.getResponseHeaders());
    }

    @When("bucket website redirects all requests for {string} to host {string} using protocol {string}")
    public void bucketWebsiteRedirectsAllRequests(String bucket, String host, String protocol) {
        var body = "<WebsiteConfiguration>" +
            "<RedirectAllRequestsTo>" + host + "</RedirectAllRequestsTo>" +
            "<Protocol>" + protocol + "</Protocol>" +
            "</WebsiteConfiguration>";
        var result = webTestClient.put()
            .uri("/{bucket}?website", bucket)
            .contentType(MediaType.APPLICATION_XML)
            .bodyValue(body)
            .exchange()
            .returnResult();
        commonSteps.setResponseStatus(result.getStatus());
        commonSteps.setResponseHeaders(result.getResponseHeaders());
    }

    @When("bucket root {string} is requested")
    public void bucketRootRequested(String bucket) {
        var result = webTestClient.get()
            .uri("/{bucket}", bucket)
            .exchange()
            .expectBody(String.class)
            .returnResult();
        commonSteps.setResponseStatus(result.getStatus());
        commonSteps.setResponseBody(result.getResponseBody());
        commonSteps.setResponseHeaders(result.getResponseHeaders());
    }

    @When("missing object {string} in bucket {string} is requested")
    public void missingObjectRequested(String key, String bucket) {
        var result = webTestClient.get()
            .uri("/{bucket}/{key}", bucket, key)
            .exchange()
            .expectBody(String.class)
            .returnResult();
        commonSteps.setResponseStatus(result.getStatus());
        commonSteps.setResponseBody(result.getResponseBody());
        commonSteps.setResponseHeaders(result.getResponseHeaders());
    }

    @When("bucket ownership controls are set directly for {string} to {string}")
    public void bucketOwnershipControlsSetDirectly(String bucket, String ownership) {
        var body = "<OwnershipControls><Ownership>" + ownership + "</Ownership></OwnershipControls>";
        var result = webTestClient.put()
            .uri("/{bucket}?ownershipControls", bucket)
            .contentType(MediaType.APPLICATION_XML)
            .bodyValue(body)
            .exchange()
            .expectBody(String.class)
            .returnResult();
        commonSteps.setResponseStatus(result.getStatus());
        commonSteps.setResponseBody(result.getResponseBody());
        commonSteps.setResponseHeaders(result.getResponseHeaders());
    }

    @When("bucket public access block is configured for {string} with blockPublicAcls {string}, ignorePublicAcls {string}, blockPublicPolicy {string}, restrictPublicBuckets {string}")
    public void bucketPublicAccessBlockConfigured(String bucket, String blockPublicAcls, String ignorePublicAcls,
                                                  String blockPublicPolicy, String restrictPublicBuckets) {
        var body = "<PublicAccessBlockConfiguration>" +
            "<BlockPublicAcls>" + blockPublicAcls + "</BlockPublicAcls>" +
            "<IgnorePublicAcls>" + ignorePublicAcls + "</IgnorePublicAcls>" +
            "<BlockPublicPolicy>" + blockPublicPolicy + "</BlockPublicPolicy>" +
            "<RestrictPublicBuckets>" + restrictPublicBuckets + "</RestrictPublicBuckets>" +
            "</PublicAccessBlockConfiguration>";
        var result = webTestClient.put()
            .uri("/{bucket}?publicAccessBlock", bucket)
            .contentType(MediaType.APPLICATION_XML)
            .bodyValue(body)
            .exchange()
            .returnResult();
        commonSteps.setResponseStatus(result.getStatus());
        commonSteps.setResponseHeaders(result.getResponseHeaders());
    }

    @When("bucket policy is set for {string} to a public read policy")
    public void publicReadPolicySet(String bucket) {
        var body = "{\"Version\":\"2012-10-17\",\"Statement\":[{\"Effect\":\"Allow\",\"Principal\":\"*\",\"Action\":\"s3:GetObject\"}]}";
        var result = webTestClient.put()
            .uri("/{bucket}?policy", bucket)
            .contentType(MediaType.APPLICATION_JSON)
            .bodyValue(body)
            .exchange()
            .expectBody(String.class)
            .returnResult();
        commonSteps.setResponseStatus(result.getStatus());
        commonSteps.setResponseBody(result.getResponseBody());
        commonSteps.setResponseHeaders(result.getResponseHeaders());
    }

    @When("bucket {string} is listed without authorization")
    public void bucketListedWithoutAuthorization(String bucket) {
        var result = webTestClient.get()
            .uri("/{bucket}", bucket)
            .accept(MediaType.APPLICATION_XML)
            .exchange()
            .expectBody(String.class)
            .returnResult();
        commonSteps.setResponseStatus(result.getStatus());
        commonSteps.setResponseBody(result.getResponseBody());
        commonSteps.setResponseHeaders(result.getResponseHeaders());
    }

    @When("bucket {string} is listed with requester pays header")
    public void bucketListedWithRequesterPaysHeader(String bucket) {
        var result = webTestClient.get()
            .uri("/{bucket}", bucket)
            .accept(MediaType.APPLICATION_XML)
            .header("x-amz-request-payer", "requester")
            .exchange()
            .expectBody(String.class)
            .returnResult();
        commonSteps.setResponseStatus(result.getStatus());
        commonSteps.setResponseBody(result.getResponseBody());
        commonSteps.setResponseHeaders(result.getResponseHeaders());
    }

    @Then("the response header {string} is {string}")
    public void responseHeaderIs(String name, String expected) {
        assertEquals(expected, commonSteps.getResponseHeader(name));
    }

    @Then("the response Location header is {string}")
    public void responseLocationHeaderIs(String expected) {
        assertEquals(expected, commonSteps.getResponseHeader("Location"));
    }

    @Then("the response body contains {string}")
    public void responseBodyContains(String expected) {
        var body = commonSteps.getResponseBody();
        assertNotNull(body);
        assertTrue(body.contains(expected), () -> "Expected response body to contain " + expected + " but was: " + body);
    }
}
