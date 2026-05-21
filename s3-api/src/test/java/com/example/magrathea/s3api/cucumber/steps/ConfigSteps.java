package com.example.magrathea.s3api.cucumber.steps;

import io.cucumber.java.en.Given;
import io.cucumber.java.en.When;
import io.cucumber.java.en.Then;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.test.web.reactive.server.WebTestClient;

import static org.junit.jupiter.api.Assertions.*;

public class ConfigSteps {

    @Autowired
    private WebTestClient webTestClient;

    @Autowired
    private CommonSteps commonSteps;

    // ── Given ──

    @Given("bucket CORS is preset with origin {string} and methods {string}")
    public void bucketCorsPreset(String origin, String methods) {
        var body = "<CORSConfiguration><CORSRule>" +
            "<AllowedOrigin>" + origin + "</AllowedOrigin>" +
            "<AllowedMethod>GET</AllowedMethod>" +
            "</CORSRule></CORSConfiguration>";
        webTestClient.put()
            .uri("/test-bucket?cors")
            .contentType(MediaType.APPLICATION_XML)
            .bodyValue(body)
            .exchange()
            .expectStatus().isOk();
    }

    // ── When ──

    @When("bucket CORS is configured with origin {string} and methods {string}")
    public void putBucketCors(String origin, String methods) {
        var body = "<CORSConfiguration><CORSRule>" +
            "<AllowedOrigin>" + origin + "</AllowedOrigin>" +
            "<AllowedMethod>GET</AllowedMethod>" +
            "</CORSRule></CORSConfiguration>";
        var status = webTestClient.put()
            .uri("/test-bucket?cors")
            .contentType(MediaType.APPLICATION_XML)
            .bodyValue(body)
            .exchange()
            .returnResult()
            .getStatus();
        commonSteps.setResponseStatus(status);
    }

    @When("bucket CORS configuration is requested")
    public void getBucketCors() {
        var result = webTestClient.get()
            .uri("/test-bucket?cors")
            .accept(MediaType.APPLICATION_XML)
            .exchange()
            .expectBody(String.class)
            .returnResult();
        commonSteps.setResponseBody(result.getResponseBody());
        commonSteps.setResponseStatus(result.getStatus());
    }

    @When("bucket CORS configuration is requested for {string}")
    public void getBucketCorsFor(String bucket) {
        var result = webTestClient.get()
            .uri("/{bucket}?cors", bucket)
            .accept(MediaType.APPLICATION_XML)
            .exchange()
            .expectBody(String.class)
            .returnResult();
        commonSteps.setResponseBody(result.getResponseBody());
        commonSteps.setResponseStatus(result.getStatus());
    }

    @When("bucket CORS configuration is deleted")
    public void deleteBucketCors() {
        var status = webTestClient.delete()
            .uri("/test-bucket?cors")
            .exchange()
            .returnResult()
            .getStatus();
        commonSteps.setResponseStatus(status);
    }

    @When("bucket CORS configuration is deleted for {string}")
    public void deleteBucketCorsFor(String bucket) {
        var status = webTestClient.delete()
            .uri("/{bucket}?cors", bucket)
            .exchange()
            .returnResult()
            .getStatus();
        commonSteps.setResponseStatus(status);
    }

    @When("bucket CORS is configured for {string} with origin {string} and methods {string}")
    public void putBucketCorsFor(String bucket, String origin, String methods) {
        var body = "<CORSConfiguration><CORSRule>" +
            "<AllowedOrigin>" + origin + "</AllowedOrigin>" +
            "<AllowedMethod>GET</AllowedMethod>" +
            "</CORSRule></CORSConfiguration>";
        var status = webTestClient.put()
            .uri("/{bucket}?cors", bucket)
            .contentType(MediaType.APPLICATION_XML)
            .bodyValue(body)
            .exchange()
            .returnResult()
            .getStatus();
        commonSteps.setResponseStatus(status);
    }

    // ── Then ── (uses BucketSteps.responseStatusIs and BucketSteps.metadataResponseContains)
}
