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

    @Given("bucket lifecycle is preset with rule {string} and status {string}")
    public void bucketLifecyclePreset(String ruleId, String status) {
        var body = "<LifecycleConfiguration><Rule>" +
            "<ID>" + ruleId + "</ID>" +
            "<Status>" + status + "</Status>" +
            "<Prefix>/</Prefix>" +
            "</Rule></LifecycleConfiguration>";
        webTestClient.put()
            .uri("/test-bucket?lifecycle")
            .contentType(MediaType.APPLICATION_XML)
            .bodyValue(body)
            .exchange()
            .expectStatus().isOk();
    }

    @Given("bucket policy is preset with {string}")
    public void bucketPolicyPreset(String policyJson) {
        webTestClient.put()
            .uri("/test-bucket?policy")
            .contentType(MediaType.APPLICATION_JSON)
            .bodyValue(policyJson)
            .exchange()
            .expectStatus().isOk();
    }

    @Given("bucket encryption is preset with algorithm {string}")
    public void bucketEncryptionPreset(String algorithm) {
        var body = "<EncryptionConfiguration>" +
            "<Algorithm>" + algorithm + "</Algorithm>" +
            "</EncryptionConfiguration>";
        webTestClient.put()
            .uri("/test-bucket?encryption")
            .contentType(MediaType.APPLICATION_XML)
            .bodyValue(body)
            .exchange()
            .expectStatus().isOk();
    }

    @Given("bucket logging is preset with target bucket {string} and prefix {string}")
    public void bucketLoggingPreset(String targetBucket, String prefix) {
        var body = "<LoggingConfiguration>" +
            "<TargetBucket>" + targetBucket + "</TargetBucket>" +
            "<TargetPrefix>" + prefix + "</TargetPrefix>" +
            "</LoggingConfiguration>";
        webTestClient.put()
            .uri("/test-bucket?logging")
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

    // ── Lifecycle ──

    @When("bucket lifecycle is configured with rule {string} and status {string}")
    public void putBucketLifecycle(String ruleId, String status) {
        var body = "<LifecycleConfiguration><Rule>" +
            "<ID>" + ruleId + "</ID>" +
            "<Status>" + status + "</Status>" +
            "<Prefix>/</Prefix>" +
            "</Rule></LifecycleConfiguration>";
        var result = webTestClient.put()
            .uri("/test-bucket?lifecycle")
            .contentType(MediaType.APPLICATION_XML)
            .bodyValue(body)
            .exchange()
            .returnResult();
        commonSteps.setResponseStatus(result.getStatus());
    }

    @When("bucket lifecycle configuration is requested")
    public void getBucketLifecycle() {
        var result = webTestClient.get()
            .uri("/test-bucket?lifecycle")
            .accept(MediaType.APPLICATION_XML)
            .exchange()
            .expectBody(String.class)
            .returnResult();
        commonSteps.setResponseBody(result.getResponseBody());
        commonSteps.setResponseStatus(result.getStatus());
    }

    @When("bucket lifecycle configuration is requested for {string}")
    public void getBucketLifecycleFor(String bucket) {
        var result = webTestClient.get()
            .uri("/{bucket}?lifecycle", bucket)
            .accept(MediaType.APPLICATION_XML)
            .exchange()
            .expectBody(String.class)
            .returnResult();
        commonSteps.setResponseBody(result.getResponseBody());
        commonSteps.setResponseStatus(result.getStatus());
    }

    @When("bucket lifecycle configuration is deleted")
    public void deleteBucketLifecycle() {
        var status = webTestClient.delete()
            .uri("/test-bucket?lifecycle")
            .exchange()
            .returnResult()
            .getStatus();
        commonSteps.setResponseStatus(status);
    }

    @When("bucket lifecycle configuration is deleted for {string}")
    public void deleteBucketLifecycleFor(String bucket) {
        var status = webTestClient.delete()
            .uri("/{bucket}?lifecycle", bucket)
            .exchange()
            .returnResult()
            .getStatus();
        commonSteps.setResponseStatus(status);
    }

    @When("bucket lifecycle is configured for {string} with rule {string} and status {string}")
    public void putBucketLifecycleFor(String bucket, String ruleId, String status) {
        var body = "<LifecycleConfiguration><Rule>" +
            "<ID>" + ruleId + "</ID>" +
            "<Status>" + status + "</Status>" +
            "<Prefix>/</Prefix>" +
            "</Rule></LifecycleConfiguration>";
        var result = webTestClient.put()
            .uri("/{bucket}?lifecycle", bucket)
            .contentType(MediaType.APPLICATION_XML)
            .bodyValue(body)
            .exchange()
            .returnResult();
        commonSteps.setResponseStatus(result.getStatus());
    }

    // ── Policy ──

    @When("bucket policy is set to {string}")
    public void putBucketPolicy(String policyJson) {
        var result = webTestClient.put()
            .uri("/test-bucket?policy")
            .contentType(MediaType.APPLICATION_JSON)
            .bodyValue(policyJson)
            .exchange()
            .returnResult();
        commonSteps.setResponseStatus(result.getStatus());
    }

    @When("bucket policy is requested")
    public void getBucketPolicy() {
        var result = webTestClient.get()
            .uri("/test-bucket?policy")
            .accept(MediaType.APPLICATION_JSON)
            .exchange()
            .expectBody(String.class)
            .returnResult();
        commonSteps.setResponseBody(result.getResponseBody());
        commonSteps.setResponseStatus(result.getStatus());
    }

    @When("bucket policy is requested for {string}")
    public void getBucketPolicyFor(String bucket) {
        var result = webTestClient.get()
            .uri("/{bucket}?policy", bucket)
            .accept(MediaType.APPLICATION_JSON)
            .exchange()
            .expectBody(String.class)
            .returnResult();
        commonSteps.setResponseBody(result.getResponseBody());
        commonSteps.setResponseStatus(result.getStatus());
    }

    @When("bucket policy is deleted")
    public void deleteBucketPolicy() {
        var status = webTestClient.delete()
            .uri("/test-bucket?policy")
            .exchange()
            .returnResult()
            .getStatus();
        commonSteps.setResponseStatus(status);
    }

    @When("bucket policy is deleted for {string}")
    public void deleteBucketPolicyFor(String bucket) {
        var status = webTestClient.delete()
            .uri("/{bucket}?policy", bucket)
            .exchange()
            .returnResult()
            .getStatus();
        commonSteps.setResponseStatus(status);
    }

    @When("bucket policy is set for {string} to {string}")
    public void putBucketPolicyFor(String bucket, String policyJson) {
        var result = webTestClient.put()
            .uri("/{bucket}?policy", bucket)
            .contentType(MediaType.APPLICATION_JSON)
            .bodyValue(policyJson)
            .exchange()
            .returnResult();
        commonSteps.setResponseStatus(result.getStatus());
    }

    // ── Encryption ──

    @When("bucket encryption is configured with algorithm {string}")
    public void putBucketEncryption(String algorithm) {
        var body = "<EncryptionConfiguration>" +
            "<Algorithm>" + algorithm + "</Algorithm>" +
            "</EncryptionConfiguration>";
        var result = webTestClient.put()
            .uri("/test-bucket?encryption")
            .contentType(MediaType.APPLICATION_XML)
            .bodyValue(body)
            .exchange()
            .returnResult();
        commonSteps.setResponseStatus(result.getStatus());
    }

    @When("bucket encryption configuration is requested")
    public void getBucketEncryption() {
        var result = webTestClient.get()
            .uri("/test-bucket?encryption")
            .accept(MediaType.APPLICATION_XML)
            .exchange()
            .expectBody(String.class)
            .returnResult();
        commonSteps.setResponseBody(result.getResponseBody());
        commonSteps.setResponseStatus(result.getStatus());
    }

    @When("bucket encryption configuration is requested for {string}")
    public void getBucketEncryptionFor(String bucket) {
        var result = webTestClient.get()
            .uri("/{bucket}?encryption", bucket)
            .accept(MediaType.APPLICATION_XML)
            .exchange()
            .expectBody(String.class)
            .returnResult();
        commonSteps.setResponseBody(result.getResponseBody());
        commonSteps.setResponseStatus(result.getStatus());
    }

    @When("bucket encryption configuration is deleted")
    public void deleteBucketEncryption() {
        var status = webTestClient.delete()
            .uri("/test-bucket?encryption")
            .exchange()
            .returnResult()
            .getStatus();
        commonSteps.setResponseStatus(status);
    }

    @When("bucket encryption configuration is deleted for {string}")
    public void deleteBucketEncryptionFor(String bucket) {
        var status = webTestClient.delete()
            .uri("/{bucket}?encryption", bucket)
            .exchange()
            .returnResult()
            .getStatus();
        commonSteps.setResponseStatus(status);
    }

    @When("bucket encryption is configured for {string} with algorithm {string}")
    public void putBucketEncryptionFor(String bucket, String algorithm) {
        var body = "<EncryptionConfiguration>" +
            "<Algorithm>" + algorithm + "</Algorithm>" +
            "</EncryptionConfiguration>";
        var result = webTestClient.put()
            .uri("/{bucket}?encryption", bucket)
            .contentType(MediaType.APPLICATION_XML)
            .bodyValue(body)
            .exchange()
            .returnResult();
        commonSteps.setResponseStatus(result.getStatus());
    }

    // ── Logging ──

    @When("bucket logging is configured with target bucket {string} and prefix {string}")
    public void putBucketLogging(String targetBucket, String prefix) {
        var body = "<LoggingConfiguration>" +
            "<TargetBucket>" + targetBucket + "</TargetBucket>" +
            "<TargetPrefix>" + prefix + "</TargetPrefix>" +
            "</LoggingConfiguration>";
        var result = webTestClient.put()
            .uri("/test-bucket?logging")
            .contentType(MediaType.APPLICATION_XML)
            .bodyValue(body)
            .exchange()
            .returnResult();
        commonSteps.setResponseStatus(result.getStatus());
    }

    @When("bucket logging configuration is requested")
    public void getBucketLogging() {
        var result = webTestClient.get()
            .uri("/test-bucket?logging")
            .accept(MediaType.APPLICATION_XML)
            .exchange()
            .expectBody(String.class)
            .returnResult();
        commonSteps.setResponseBody(result.getResponseBody());
        commonSteps.setResponseStatus(result.getStatus());
    }

    @When("bucket logging configuration is requested for {string}")
    public void getBucketLoggingFor(String bucket) {
        var result = webTestClient.get()
            .uri("/{bucket}?logging", bucket)
            .accept(MediaType.APPLICATION_XML)
            .exchange()
            .expectBody(String.class)
            .returnResult();
        commonSteps.setResponseBody(result.getResponseBody());
        commonSteps.setResponseStatus(result.getStatus());
    }

    @When("bucket logging configuration is deleted")
    public void deleteBucketLogging() {
        var status = webTestClient.delete()
            .uri("/test-bucket?logging")
            .exchange()
            .returnResult()
            .getStatus();
        commonSteps.setResponseStatus(status);
    }

    @When("bucket logging configuration is deleted for {string}")
    public void deleteBucketLoggingFor(String bucket) {
        var status = webTestClient.delete()
            .uri("/{bucket}?logging", bucket)
            .exchange()
            .returnResult()
            .getStatus();
        commonSteps.setResponseStatus(status);
    }

    @When("bucket logging is configured for {string} with target bucket {string} and prefix {string}")
    public void putBucketLoggingFor(String bucket, String targetBucket, String prefix) {
        var body = "<LoggingConfiguration>" +
            "<TargetBucket>" + targetBucket + "</TargetBucket>" +
            "<TargetPrefix>" + prefix + "</TargetPrefix>" +
            "</LoggingConfiguration>";
        var result = webTestClient.put()
            .uri("/{bucket}?logging", bucket)
            .contentType(MediaType.APPLICATION_XML)
            .bodyValue(body)
            .exchange()
            .returnResult();
        commonSteps.setResponseStatus(result.getStatus());
    }

    // ── Then ── (uses BucketSteps.responseStatusIs and BucketSteps.metadataResponseContains)
}
