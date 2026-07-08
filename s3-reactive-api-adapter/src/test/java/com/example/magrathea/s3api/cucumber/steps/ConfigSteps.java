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

    // ── Website ──

    @Given("bucket website is preset with index {string}")
    public void bucketWebsitePreset(String indexDoc) {
        var body = "<WebsiteConfiguration><IndexDocument>" + indexDoc + "</IndexDocument></WebsiteConfiguration>";
        webTestClient.put()
            .uri("/test-bucket?website")
            .contentType(MediaType.APPLICATION_XML)
            .bodyValue(body)
            .exchange()
            .expectStatus().isOk();
    }

    @When("bucket website is configured with index {string}")
    public void putBucketWebsite(String indexDoc) {
        var body = "<WebsiteConfiguration><IndexDocument>" + indexDoc + "</IndexDocument></WebsiteConfiguration>";
        var result = webTestClient.put()
            .uri("/test-bucket?website")
            .contentType(MediaType.APPLICATION_XML)
            .bodyValue(body)
            .exchange()
            .returnResult();
        commonSteps.setResponseStatus(result.getStatus());
    }

    @When("bucket website configuration is requested")
    public void getBucketWebsite() {
        var result = webTestClient.get()
            .uri("/test-bucket?website")
            .accept(MediaType.APPLICATION_XML)
            .exchange()
            .expectBody(String.class)
            .returnResult();
        commonSteps.setResponseBody(result.getResponseBody());
        commonSteps.setResponseStatus(result.getStatus());
    }

    @When("bucket website configuration is requested for {string}")
    public void getBucketWebsiteFor(String bucket) {
        var result = webTestClient.get()
            .uri("/{bucket}?website", bucket)
            .accept(MediaType.APPLICATION_XML)
            .exchange()
            .expectBody(String.class)
            .returnResult();
        commonSteps.setResponseBody(result.getResponseBody());
        commonSteps.setResponseStatus(result.getStatus());
    }

    @When("bucket website configuration is deleted")
    public void deleteBucketWebsite() {
        var status = webTestClient.delete()
            .uri("/test-bucket?website")
            .exchange()
            .returnResult()
            .getStatus();
        commonSteps.setResponseStatus(status);
    }

    @When("bucket website configuration is deleted for {string}")
    public void deleteBucketWebsiteFor(String bucket) {
        var status = webTestClient.delete()
            .uri("/{bucket}?website", bucket)
            .exchange()
            .returnResult()
            .getStatus();
        commonSteps.setResponseStatus(status);
    }

    @When("bucket website is configured for {string} with index {string}")
    public void putBucketWebsiteFor(String bucket, String indexDoc) {
        var body = "<WebsiteConfiguration><IndexDocument>" + indexDoc + "</IndexDocument></WebsiteConfiguration>";
        var result = webTestClient.put()
            .uri("/{bucket}?website", bucket)
            .contentType(MediaType.APPLICATION_XML)
            .bodyValue(body)
            .exchange()
            .returnResult();
        commonSteps.setResponseStatus(result.getStatus());
    }

    // ── Notification ──

    @Given("bucket notification is preset with event {string}")
    public void bucketNotificationPreset(String event) {
        var body = "<NotificationConfiguration><EventConfiguration><Event>" + event +
            "</Event></EventConfiguration></NotificationConfiguration>";
        webTestClient.put()
            .uri("/test-bucket?notification")
            .contentType(MediaType.APPLICATION_XML)
            .bodyValue(body)
            .exchange()
            .expectStatus().isOk();
    }

    @When("bucket notification is configured with event {string}")
    public void putBucketNotification(String event) {
        var body = "<NotificationConfiguration><EventConfiguration><Event>" + event +
            "</Event></EventConfiguration></NotificationConfiguration>";
        var result = webTestClient.put()
            .uri("/test-bucket?notification")
            .contentType(MediaType.APPLICATION_XML)
            .bodyValue(body)
            .exchange()
            .returnResult();
        commonSteps.setResponseStatus(result.getStatus());
    }

    @When("bucket notification configuration is requested")
    public void getBucketNotification() {
        var result = webTestClient.get()
            .uri("/test-bucket?notification")
            .accept(MediaType.APPLICATION_XML)
            .exchange()
            .expectBody(String.class)
            .returnResult();
        commonSteps.setResponseBody(result.getResponseBody());
        commonSteps.setResponseStatus(result.getStatus());
    }

    @When("bucket notification configuration is requested for {string}")
    public void getBucketNotificationFor(String bucket) {
        var result = webTestClient.get()
            .uri("/{bucket}?notification", bucket)
            .accept(MediaType.APPLICATION_XML)
            .exchange()
            .expectBody(String.class)
            .returnResult();
        commonSteps.setResponseBody(result.getResponseBody());
        commonSteps.setResponseStatus(result.getStatus());
    }

    @When("bucket notification configuration is deleted")
    public void deleteBucketNotification() {
        var status = webTestClient.delete()
            .uri("/test-bucket?notification")
            .exchange()
            .returnResult()
            .getStatus();
        commonSteps.setResponseStatus(status);
    }

    @When("bucket notification configuration is deleted for {string}")
    public void deleteBucketNotificationFor(String bucket) {
        var status = webTestClient.delete()
            .uri("/{bucket}?notification", bucket)
            .exchange()
            .returnResult()
            .getStatus();
        commonSteps.setResponseStatus(status);
    }

    @When("bucket notification is configured for {string} with event {string}")
    public void putBucketNotificationFor(String bucket, String event) {
        var body = "<NotificationConfiguration><EventConfiguration><Event>" + event +
            "</Event></EventConfiguration></NotificationConfiguration>";
        var result = webTestClient.put()
            .uri("/{bucket}?notification", bucket)
            .contentType(MediaType.APPLICATION_XML)
            .bodyValue(body)
            .exchange()
            .returnResult();
        commonSteps.setResponseStatus(result.getStatus());
    }

    // ── Replication ──

    @Given("bucket replication is preset with role {string}")
    public void bucketReplicationPreset(String role) {
        var body = "<ReplicationConfiguration><Role>" + role +
            "</Role><Rule><Status>Enabled</Status></Rule></ReplicationConfiguration>";
        webTestClient.put()
            .uri("/test-bucket?replication")
            .contentType(MediaType.APPLICATION_XML)
            .bodyValue(body)
            .exchange()
            .expectStatus().isOk();
    }

    @When("bucket replication is configured with role {string}")
    public void putBucketReplication(String role) {
        var body = "<ReplicationConfiguration><Role>" + role +
            "</Role><Rule><Status>Enabled</Status></Rule></ReplicationConfiguration>";
        var result = webTestClient.put()
            .uri("/test-bucket?replication")
            .contentType(MediaType.APPLICATION_XML)
            .bodyValue(body)
            .exchange()
            .returnResult();
        commonSteps.setResponseStatus(result.getStatus());
    }

    @When("bucket replication configuration is requested")
    public void getBucketReplication() {
        var result = webTestClient.get()
            .uri("/test-bucket?replication")
            .accept(MediaType.APPLICATION_XML)
            .exchange()
            .expectBody(String.class)
            .returnResult();
        commonSteps.setResponseBody(result.getResponseBody());
        commonSteps.setResponseStatus(result.getStatus());
    }

    @When("bucket replication configuration is requested for {string}")
    public void getBucketReplicationFor(String bucket) {
        var result = webTestClient.get()
            .uri("/{bucket}?replication", bucket)
            .accept(MediaType.APPLICATION_XML)
            .exchange()
            .expectBody(String.class)
            .returnResult();
        commonSteps.setResponseBody(result.getResponseBody());
        commonSteps.setResponseStatus(result.getStatus());
    }

    @When("bucket replication configuration is deleted")
    public void deleteBucketReplication() {
        var status = webTestClient.delete()
            .uri("/test-bucket?replication")
            .exchange()
            .returnResult()
            .getStatus();
        commonSteps.setResponseStatus(status);
    }

    @When("bucket replication configuration is deleted for {string}")
    public void deleteBucketReplicationFor(String bucket) {
        var status = webTestClient.delete()
            .uri("/{bucket}?replication", bucket)
            .exchange()
            .returnResult()
            .getStatus();
        commonSteps.setResponseStatus(status);
    }

    @When("bucket replication is configured for {string} with role {string}")
    public void putBucketReplicationFor(String bucket, String role) {
        var body = "<ReplicationConfiguration><Role>" + role +
            "</Role><Rule><Status>Enabled</Status></Rule></ReplicationConfiguration>";
        var result = webTestClient.put()
            .uri("/{bucket}?replication", bucket)
            .contentType(MediaType.APPLICATION_XML)
            .bodyValue(body)
            .exchange()
            .returnResult();
        commonSteps.setResponseStatus(result.getStatus());
    }

    // ── Request Payment ──

    @Given("bucket request payment is preset with payer {string}")
    public void bucketRequestPaymentPreset(String payer) {
        var body = "<RequestPaymentConfiguration><Payer>" + payer + "</Payer></RequestPaymentConfiguration>";
        webTestClient.put()
            .uri("/test-bucket?requestPayment")
            .contentType(MediaType.APPLICATION_XML)
            .bodyValue(body)
            .exchange()
            .expectStatus().isOk();
    }

    @When("bucket request payment is configured with payer {string}")
    public void putBucketRequestPayment(String payer) {
        var body = "<RequestPaymentConfiguration><Payer>" + payer + "</Payer></RequestPaymentConfiguration>";
        var result = webTestClient.put()
            .uri("/test-bucket?requestPayment")
            .contentType(MediaType.APPLICATION_XML)
            .bodyValue(body)
            .exchange()
            .returnResult();
        commonSteps.setResponseStatus(result.getStatus());
    }

    @When("bucket request payment configuration is requested")
    public void getBucketRequestPayment() {
        var result = webTestClient.get()
            .uri("/test-bucket?requestPayment")
            .accept(MediaType.APPLICATION_XML)
            .exchange()
            .expectBody(String.class)
            .returnResult();
        commonSteps.setResponseBody(result.getResponseBody());
        commonSteps.setResponseStatus(result.getStatus());
    }

    @When("bucket request payment configuration is requested for {string}")
    public void getBucketRequestPaymentFor(String bucket) {
        var result = webTestClient.get()
            .uri("/{bucket}?requestPayment", bucket)
            .accept(MediaType.APPLICATION_XML)
            .exchange()
            .expectBody(String.class)
            .returnResult();
        commonSteps.setResponseBody(result.getResponseBody());
        commonSteps.setResponseStatus(result.getStatus());
    }

    @When("bucket request payment configuration is deleted")
    public void deleteBucketRequestPayment() {
        var status = webTestClient.delete()
            .uri("/test-bucket?requestPayment")
            .exchange()
            .returnResult()
            .getStatus();
        commonSteps.setResponseStatus(status);
    }

    @When("bucket request payment configuration is deleted for {string}")
    public void deleteBucketRequestPaymentFor(String bucket) {
        var status = webTestClient.delete()
            .uri("/{bucket}?requestPayment", bucket)
            .exchange()
            .returnResult()
            .getStatus();
        commonSteps.setResponseStatus(status);
    }

    @When("bucket request payment is configured for {string} with payer {string}")
    public void putBucketRequestPaymentFor(String bucket, String payer) {
        var body = "<RequestPaymentConfiguration><Payer>" + payer + "</Payer></RequestPaymentConfiguration>";
        var result = webTestClient.put()
            .uri("/{bucket}?requestPayment", bucket)
            .contentType(MediaType.APPLICATION_XML)
            .bodyValue(body)
            .exchange()
            .returnResult();
        commonSteps.setResponseStatus(result.getStatus());
    }

    // ── Ownership Controls ──

    @Given("bucket ownership controls are preset with ownership {string}")
    public void bucketOwnershipControlsPreset(String ownership) {
        var body = "<OwnershipControls><Ownership>" + ownership + "</Ownership></OwnershipControls>";
        webTestClient.put()
            .uri("/test-bucket?ownershipControls")
            .contentType(MediaType.APPLICATION_XML)
            .bodyValue(body)
            .exchange()
            .expectStatus().isOk();
    }

    @When("bucket ownership controls are configured with ownership {string}")
    public void putBucketOwnershipControls(String ownership) {
        var body = "<OwnershipControls><Ownership>" + ownership + "</Ownership></OwnershipControls>";
        var result = webTestClient.put()
            .uri("/test-bucket?ownershipControls")
            .contentType(MediaType.APPLICATION_XML)
            .bodyValue(body)
            .exchange()
            .returnResult();
        commonSteps.setResponseStatus(result.getStatus());
    }

    @When("bucket ownership controls are requested")
    public void getBucketOwnershipControls() {
        var result = webTestClient.get()
            .uri("/test-bucket?ownershipControls")
            .accept(MediaType.APPLICATION_XML)
            .exchange()
            .expectBody(String.class)
            .returnResult();
        commonSteps.setResponseBody(result.getResponseBody());
        commonSteps.setResponseStatus(result.getStatus());
    }

    @When("bucket ownership controls are requested for {string}")
    public void getBucketOwnershipControlsFor(String bucket) {
        var result = webTestClient.get()
            .uri("/{bucket}?ownershipControls", bucket)
            .accept(MediaType.APPLICATION_XML)
            .exchange()
            .expectBody(String.class)
            .returnResult();
        commonSteps.setResponseBody(result.getResponseBody());
        commonSteps.setResponseStatus(result.getStatus());
    }

    @When("bucket ownership controls are deleted")
    public void deleteBucketOwnershipControls() {
        var status = webTestClient.delete()
            .uri("/test-bucket?ownershipControls")
            .exchange()
            .returnResult()
            .getStatus();
        commonSteps.setResponseStatus(status);
    }

    @When("bucket ownership controls are deleted for {string}")
    public void deleteBucketOwnershipControlsFor(String bucket) {
        var status = webTestClient.delete()
            .uri("/{bucket}?ownershipControls", bucket)
            .exchange()
            .returnResult()
            .getStatus();
        commonSteps.setResponseStatus(status);
    }

    @When("bucket ownership controls are configured for {string} with ownership {string}")
    public void putBucketOwnershipControlsFor(String bucket, String ownership) {
        var body = "<OwnershipControls><Ownership>" + ownership + "</Ownership></OwnershipControls>";
        var result = webTestClient.put()
            .uri("/{bucket}?ownershipControls", bucket)
            .contentType(MediaType.APPLICATION_XML)
            .bodyValue(body)
            .exchange()
            .returnResult();
        commonSteps.setResponseStatus(result.getStatus());
    }

    // ── Public Access Block ──

    @Given("bucket public access block is preset with blockPublicAcls {string}")
    public void bucketPublicAccessBlockPreset(String blockAcls) {
        var body = "<PublicAccessBlockConfiguration><BlockPublicAcls>" + blockAcls +
            "</BlockPublicAcls><IgnorePublicAcls>false</IgnorePublicAcls>" +
            "<BlockPublicPolicy>false</BlockPublicPolicy><RestrictPublicBuckets>false</RestrictPublicBuckets>" +
            "</PublicAccessBlockConfiguration>";
        webTestClient.put()
            .uri("/test-bucket?publicAccessBlock")
            .contentType(MediaType.APPLICATION_XML)
            .bodyValue(body)
            .exchange()
            .expectStatus().isOk();
    }

    @When("bucket public access block is configured with blockPublicAcls {string}")
    public void putBucketPublicAccessBlock(String blockAcls) {
        var body = "<PublicAccessBlockConfiguration><BlockPublicAcls>" + blockAcls +
            "</BlockPublicAcls><IgnorePublicAcls>false</IgnorePublicAcls>" +
            "<BlockPublicPolicy>false</BlockPublicPolicy><RestrictPublicBuckets>false</RestrictPublicBuckets>" +
            "</PublicAccessBlockConfiguration>";
        var result = webTestClient.put()
            .uri("/test-bucket?publicAccessBlock")
            .contentType(MediaType.APPLICATION_XML)
            .bodyValue(body)
            .exchange()
            .returnResult();
        commonSteps.setResponseStatus(result.getStatus());
    }

    @When("bucket public access block configuration is requested")
    public void getBucketPublicAccessBlock() {
        var result = webTestClient.get()
            .uri("/test-bucket?publicAccessBlock")
            .accept(MediaType.APPLICATION_XML)
            .exchange()
            .expectBody(String.class)
            .returnResult();
        commonSteps.setResponseBody(result.getResponseBody());
        commonSteps.setResponseStatus(result.getStatus());
    }

    @When("bucket public access block configuration is requested for {string}")
    public void getBucketPublicAccessBlockFor(String bucket) {
        var result = webTestClient.get()
            .uri("/{bucket}?publicAccessBlock", bucket)
            .accept(MediaType.APPLICATION_XML)
            .exchange()
            .expectBody(String.class)
            .returnResult();
        commonSteps.setResponseBody(result.getResponseBody());
        commonSteps.setResponseStatus(result.getStatus());
    }

    @When("bucket public access block configuration is deleted")
    public void deleteBucketPublicAccessBlock() {
        var status = webTestClient.delete()
            .uri("/test-bucket?publicAccessBlock")
            .exchange()
            .returnResult()
            .getStatus();
        commonSteps.setResponseStatus(status);
    }

    @When("bucket public access block configuration is deleted for {string}")
    public void deleteBucketPublicAccessBlockFor(String bucket) {
        var status = webTestClient.delete()
            .uri("/{bucket}?publicAccessBlock", bucket)
            .exchange()
            .returnResult()
            .getStatus();
        commonSteps.setResponseStatus(status);
    }

    @When("bucket public access block is configured for {string} with blockPublicAcls {string}")
    public void putBucketPublicAccessBlockFor(String bucket, String blockAcls) {
        var body = "<PublicAccessBlockConfiguration><BlockPublicAcls>" + blockAcls +
            "</BlockPublicAcls><IgnorePublicAcls>false</IgnorePublicAcls>" +
            "<BlockPublicPolicy>false</BlockPublicPolicy><RestrictPublicBuckets>false</RestrictPublicBuckets>" +
            "</PublicAccessBlockConfiguration>";
        var result = webTestClient.put()
            .uri("/{bucket}?publicAccessBlock", bucket)
            .contentType(MediaType.APPLICATION_XML)
            .bodyValue(body)
            .exchange()
            .returnResult();
        commonSteps.setResponseStatus(result.getStatus());
    }

    // ── Accelerate ──

    @Given("bucket accelerate is preset with status {string}")
    public void bucketAcceleratePreset(String status) {
        var body = "<AccelerateConfiguration><Status>" + status + "</Status></AccelerateConfiguration>";
        webTestClient.put()
            .uri("/test-bucket?accelerate")
            .contentType(MediaType.APPLICATION_XML)
            .bodyValue(body)
            .exchange()
            .expectStatus().isOk();
    }

    @When("bucket accelerate is configured with status {string}")
    public void putBucketAccelerate(String status) {
        var body = "<AccelerateConfiguration><Status>" + status + "</Status></AccelerateConfiguration>";
        var result = webTestClient.put()
            .uri("/test-bucket?accelerate")
            .contentType(MediaType.APPLICATION_XML)
            .bodyValue(body)
            .exchange()
            .returnResult();
        commonSteps.setResponseStatus(result.getStatus());
    }

    @When("bucket accelerate configuration is requested")
    public void getBucketAccelerate() {
        var result = webTestClient.get()
            .uri("/test-bucket?accelerate")
            .accept(MediaType.APPLICATION_XML)
            .exchange()
            .expectBody(String.class)
            .returnResult();
        commonSteps.setResponseBody(result.getResponseBody());
        commonSteps.setResponseStatus(result.getStatus());
    }

    @When("bucket accelerate configuration is requested for {string}")
    public void getBucketAccelerateFor(String bucket) {
        var result = webTestClient.get()
            .uri("/{bucket}?accelerate", bucket)
            .accept(MediaType.APPLICATION_XML)
            .exchange()
            .expectBody(String.class)
            .returnResult();
        commonSteps.setResponseBody(result.getResponseBody());
        commonSteps.setResponseStatus(result.getStatus());
    }

    @When("bucket accelerate configuration is deleted")
    public void deleteBucketAccelerate() {
        var status = webTestClient.delete()
            .uri("/test-bucket?accelerate")
            .exchange()
            .returnResult()
            .getStatus();
        commonSteps.setResponseStatus(status);
    }

    @When("bucket accelerate configuration is deleted for {string}")
    public void deleteBucketAccelerateFor(String bucket) {
        var status = webTestClient.delete()
            .uri("/{bucket}?accelerate", bucket)
            .exchange()
            .returnResult()
            .getStatus();
        commonSteps.setResponseStatus(status);
    }

    @When("bucket accelerate is configured for {string} with status {string}")
    public void putBucketAccelerateFor(String bucket, String status) {
        var body = "<AccelerateConfiguration><Status>" + status + "</Status></AccelerateConfiguration>";
        var result = webTestClient.put()
            .uri("/{bucket}?accelerate", bucket)
            .contentType(MediaType.APPLICATION_XML)
            .bodyValue(body)
            .exchange()
            .returnResult();
        commonSteps.setResponseStatus(result.getStatus());
    }

    // ── Analytics ──

    @Given("bucket analytics is preset with id {string} and filter {string}")
    public void bucketAnalyticsPreset(String id, String filter) {
        var body = "<AnalyticsConfiguration><Id>" + id + "</Id><Filter><Prefix>" + filter + "</Prefix></Filter></AnalyticsConfiguration>";
        webTestClient.put()
            .uri("/test-bucket?analytics&analyticsId=" + id)
            .contentType(MediaType.APPLICATION_XML)
            .bodyValue(body)
            .exchange()
            .expectStatus().isOk();
    }

    @When("bucket analytics is configured with id {string} and filter {string}")
    public void putBucketAnalytics(String id, String filter) {
        var body = "<AnalyticsConfiguration><Id>" + id + "</Id><Filter><Prefix>" + filter + "</Prefix></Filter></AnalyticsConfiguration>";
        var result = webTestClient.put()
            .uri("/test-bucket?analytics&analyticsId=" + id)
            .contentType(MediaType.APPLICATION_XML)
            .bodyValue(body)
            .exchange()
            .returnResult();
        commonSteps.setResponseStatus(result.getStatus());
    }

    @When("bucket analytics configuration is requested for id {string}")
    public void getBucketAnalytics(String id) {
        var result = webTestClient.get()
            .uri("/test-bucket?analytics&analyticsId=" + id)
            .accept(MediaType.APPLICATION_XML)
            .exchange()
            .expectBody(String.class)
            .returnResult();
        commonSteps.setResponseBody(result.getResponseBody());
        commonSteps.setResponseStatus(result.getStatus());
    }

    @When("bucket analytics configuration is deleted for id {string}")
    public void deleteBucketAnalytics(String id) {
        var status = webTestClient.delete()
            .uri("/test-bucket?analytics&analyticsId=" + id)
            .exchange()
            .returnResult()
            .getStatus();
        commonSteps.setResponseStatus(status);
    }

    @When("bucket analytics configurations are listed")
    public void listBucketAnalytics() {
        var result = webTestClient.get()
            .uri("/test-bucket?analytics&list-type")
            .accept(MediaType.APPLICATION_XML)
            .exchange()
            .expectBody(String.class)
            .returnResult();
        commonSteps.setResponseBody(result.getResponseBody());
        commonSteps.setResponseStatus(result.getStatus());
    }

    @When("bucket analytics configuration is requested for {string} with id {string}")
    public void getBucketAnalyticsFor(String bucket, String id) {
        var result = webTestClient.get()
            .uri("/{bucket}?analytics&analyticsId={id}", bucket, id)
            .accept(MediaType.APPLICATION_XML)
            .exchange()
            .expectBody(String.class)
            .returnResult();
        commonSteps.setResponseBody(result.getResponseBody());
        commonSteps.setResponseStatus(result.getStatus());
    }

    @When("bucket analytics is configured for {string} with id {string} and filter {string}")
    public void putBucketAnalyticsFor(String bucket, String id, String filter) {
        var body = "<AnalyticsConfiguration><Id>" + id + "</Id><Filter><Prefix>" + filter + "</Prefix></Filter></AnalyticsConfiguration>";
        var result = webTestClient.put()
            .uri("/{bucket}?analytics&analyticsId={id}", bucket, id)
            .contentType(MediaType.APPLICATION_XML)
            .bodyValue(body)
            .exchange()
            .returnResult();
        commonSteps.setResponseStatus(result.getStatus());
    }

    @When("bucket analytics configuration is deleted for {string} with id {string}")
    public void deleteBucketAnalyticsFor(String bucket, String id) {
        var status = webTestClient.delete()
            .uri("/{bucket}?analytics&analyticsId={id}", bucket, id)
            .exchange()
            .returnResult()
            .getStatus();
        commonSteps.setResponseStatus(status);
    }

    // ── Inventory ──

    @Given("bucket inventory is preset with id {string} and format {string}")
    public void bucketInventoryPreset(String id, String format) {
        var body = "<InventoryConfiguration><Id>" + id + "</Id><Destination><Format>" + format + "</Format></Destination><Schedule><Frequency>Daily</Frequency></Schedule><Enabled>true</Enabled></InventoryConfiguration>";
        webTestClient.put()
            .uri("/test-bucket?inventory&inventoryId=" + id)
            .contentType(MediaType.APPLICATION_XML)
            .bodyValue(body)
            .exchange()
            .expectStatus().isOk();
    }

    @When("bucket inventory is configured with id {string} and format {string}")
    public void putBucketInventory(String id, String format) {
        var body = "<InventoryConfiguration><Id>" + id + "</Id><Destination><Format>" + format + "</Format></Destination><Schedule><Frequency>Daily</Frequency></Schedule><Enabled>true</Enabled></InventoryConfiguration>";
        var result = webTestClient.put()
            .uri("/test-bucket?inventory&inventoryId=" + id)
            .contentType(MediaType.APPLICATION_XML)
            .bodyValue(body)
            .exchange()
            .returnResult();
        commonSteps.setResponseStatus(result.getStatus());
    }

    @When("bucket inventory configuration is requested for id {string}")
    public void getBucketInventory(String id) {
        var result = webTestClient.get()
            .uri("/test-bucket?inventory&inventoryId=" + id)
            .accept(MediaType.APPLICATION_XML)
            .exchange()
            .expectBody(String.class)
            .returnResult();
        commonSteps.setResponseBody(result.getResponseBody());
        commonSteps.setResponseStatus(result.getStatus());
    }

    @When("bucket inventory configuration is deleted for id {string}")
    public void deleteBucketInventory(String id) {
        var status = webTestClient.delete()
            .uri("/test-bucket?inventory&inventoryId=" + id)
            .exchange()
            .returnResult()
            .getStatus();
        commonSteps.setResponseStatus(status);
    }

    @When("bucket inventory configurations are listed")
    public void listBucketInventory() {
        var result = webTestClient.get()
            .uri("/test-bucket?inventory&list-type")
            .accept(MediaType.APPLICATION_XML)
            .exchange()
            .expectBody(String.class)
            .returnResult();
        commonSteps.setResponseBody(result.getResponseBody());
        commonSteps.setResponseStatus(result.getStatus());
    }

    @When("bucket inventory configuration is requested for {string} with id {string}")
    public void getBucketInventoryFor(String bucket, String id) {
        var result = webTestClient.get()
            .uri("/{bucket}?inventory&inventoryId={id}", bucket, id)
            .accept(MediaType.APPLICATION_XML)
            .exchange()
            .expectBody(String.class)
            .returnResult();
        commonSteps.setResponseBody(result.getResponseBody());
        commonSteps.setResponseStatus(result.getStatus());
    }

    @When("bucket inventory is configured for {string} with id {string} and format {string}")
    public void putBucketInventoryFor(String bucket, String id, String format) {
        var body = "<InventoryConfiguration><Id>" + id + "</Id><Destination><Format>" + format + "</Format></Destination><Schedule><Frequency>Daily</Frequency></Schedule><Enabled>true</Enabled></InventoryConfiguration>";
        var result = webTestClient.put()
            .uri("/{bucket}?inventory&inventoryId={id}", bucket, id)
            .contentType(MediaType.APPLICATION_XML)
            .bodyValue(body)
            .exchange()
            .returnResult();
        commonSteps.setResponseStatus(result.getStatus());
    }

    @When("bucket inventory configuration is deleted for {string} with id {string}")
    public void deleteBucketInventoryFor(String bucket, String id) {
        var status = webTestClient.delete()
            .uri("/{bucket}?inventory&inventoryId={id}", bucket, id)
            .exchange()
            .returnResult()
            .getStatus();
        commonSteps.setResponseStatus(status);
    }

    // ── Metrics ──

    @Given("bucket metrics is preset with id {string}")
    public void bucketMetricsPreset(String id) {
        var body = "<MetricsConfiguration><Id>" + id + "</Id></MetricsConfiguration>";
        webTestClient.put()
            .uri("/test-bucket?metrics&id=" + id)
            .contentType(MediaType.APPLICATION_XML)
            .bodyValue(body)
            .exchange()
            .expectStatus().isOk();
    }

    @When("bucket metrics is configured with id {string}")
    public void putBucketMetrics(String id) {
        var body = "<MetricsConfiguration><Id>" + id + "</Id></MetricsConfiguration>";
        var result = webTestClient.put()
            .uri("/test-bucket?metrics&id=" + id)
            .contentType(MediaType.APPLICATION_XML)
            .bodyValue(body)
            .exchange()
            .returnResult();
        commonSteps.setResponseStatus(result.getStatus());
    }

    @When("bucket metrics configuration is requested")
    public void getBucketMetrics() {
        var result = webTestClient.get()
            .uri("/test-bucket?metrics")
            .accept(MediaType.APPLICATION_XML)
            .exchange()
            .expectBody(String.class)
            .returnResult();
        commonSteps.setResponseBody(result.getResponseBody());
        commonSteps.setResponseStatus(result.getStatus());
    }

    @When("bucket metrics configuration is deleted")
    public void deleteBucketMetrics() {
        var status = webTestClient.delete()
            .uri("/test-bucket?metrics")
            .exchange()
            .returnResult()
            .getStatus();
        commonSteps.setResponseStatus(status);
    }

    @When("bucket metrics configuration is requested for {string}")
    public void getBucketMetricsFor(String bucket) {
        var result = webTestClient.get()
            .uri("/{bucket}?metrics", bucket)
            .accept(MediaType.APPLICATION_XML)
            .exchange()
            .expectBody(String.class)
            .returnResult();
        commonSteps.setResponseBody(result.getResponseBody());
        commonSteps.setResponseStatus(result.getStatus());
    }

    @When("bucket metrics is configured for {string} with id {string}")
    public void putBucketMetricsFor(String bucket, String id) {
        var body = "<MetricsConfiguration><Id>" + id + "</Id></MetricsConfiguration>";
        var result = webTestClient.put()
            .uri("/{bucket}?metrics&id={id}", bucket, id)
            .contentType(MediaType.APPLICATION_XML)
            .bodyValue(body)
            .exchange()
            .returnResult();
        commonSteps.setResponseStatus(result.getStatus());
    }

    @When("bucket metrics configuration is deleted for {string}")
    public void deleteBucketMetricsFor(String bucket) {
        var status = webTestClient.delete()
            .uri("/{bucket}?metrics", bucket)
            .exchange()
            .returnResult()
            .getStatus();
        commonSteps.setResponseStatus(status);
    }

    // ── Intelligent-Tiering ──

    @Given("bucket intelligent-tiering is preset with id {string} and status {string}")
    public void bucketIntelligentTieringPreset(String id, String status) {
        var body = "<IntelligentTieringConfiguration><Id>" + id + "</Id><AutoTieringStatus>" + status + "</AutoTieringStatus></IntelligentTieringConfiguration>";
        webTestClient.put()
            .uri("/test-bucket?intelligent-tiering&id=" + id)
            .contentType(MediaType.APPLICATION_XML)
            .bodyValue(body)
            .exchange()
            .expectStatus().isOk();
    }

    @When("bucket intelligent-tiering is configured with id {string} and status {string}")
    public void putBucketIntelligentTiering(String id, String status) {
        var body = "<IntelligentTieringConfiguration><Id>" + id + "</Id><AutoTieringStatus>" + status + "</AutoTieringStatus></IntelligentTieringConfiguration>";
        var result = webTestClient.put()
            .uri("/test-bucket?intelligent-tiering&id=" + id)
            .contentType(MediaType.APPLICATION_XML)
            .bodyValue(body)
            .exchange()
            .returnResult();
        commonSteps.setResponseStatus(result.getStatus());
    }

    @When("bucket intelligent-tiering configuration is requested")
    public void getBucketIntelligentTiering() {
        var result = webTestClient.get()
            .uri("/test-bucket?intelligent-tiering")
            .accept(MediaType.APPLICATION_XML)
            .exchange()
            .expectBody(String.class)
            .returnResult();
        commonSteps.setResponseBody(result.getResponseBody());
        commonSteps.setResponseStatus(result.getStatus());
    }

    @When("bucket intelligent-tiering configuration is deleted")
    public void deleteBucketIntelligentTiering() {
        var status = webTestClient.delete()
            .uri("/test-bucket?intelligent-tiering")
            .exchange()
            .returnResult()
            .getStatus();
        commonSteps.setResponseStatus(status);
    }

    @When("bucket intelligent-tiering configuration is requested for {string}")
    public void getBucketIntelligentTieringFor(String bucket) {
        var result = webTestClient.get()
            .uri("/{bucket}?intelligent-tiering", bucket)
            .accept(MediaType.APPLICATION_XML)
            .exchange()
            .expectBody(String.class)
            .returnResult();
        commonSteps.setResponseBody(result.getResponseBody());
        commonSteps.setResponseStatus(result.getStatus());
    }

    @When("bucket intelligent-tiering is configured for {string} with id {string} and status {string}")
    public void putBucketIntelligentTieringFor(String bucket, String id, String status) {
        var body = "<IntelligentTieringConfiguration><Id>" + id + "</Id><AutoTieringStatus>" + status + "</AutoTieringStatus></IntelligentTieringConfiguration>";
        var result = webTestClient.put()
            .uri("/{bucket}?intelligent-tiering&id={id}", bucket, id)
            .contentType(MediaType.APPLICATION_XML)
            .bodyValue(body)
            .exchange()
            .returnResult();
        commonSteps.setResponseStatus(result.getStatus());
    }

    @When("bucket intelligent-tiering configuration is deleted for {string}")
    public void deleteBucketIntelligentTieringFor(String bucket) {
        var status = webTestClient.delete()
            .uri("/{bucket}?intelligent-tiering", bucket)
            .exchange()
            .returnResult()
            .getStatus();
        commonSteps.setResponseStatus(status);
    }

    // ── ABAC ──

    @Given("bucket ABAC is preset with rule id {string} and principal {string}")
    public void bucketAbacPreset(String ruleId, String principal) {
        webTestClient.put()
            .uri("/test-bucket?abac")
            .contentType(MediaType.APPLICATION_XML)
            .bodyValue(abacBody(ruleId, principal))
            .exchange()
            .expectStatus().isOk();
    }

    @When("bucket ABAC is configured with rule id {string} and principal {string}")
    public void putBucketAbac(String ruleId, String principal) {
        var status = webTestClient.put()
            .uri("/test-bucket?abac")
            .contentType(MediaType.APPLICATION_XML)
            .bodyValue(abacBody(ruleId, principal))
            .exchange()
            .returnResult()
            .getStatus();
        commonSteps.setResponseStatus(status);
    }

    @When("bucket ABAC is configured for {string} with rule id {string} and principal {string}")
    public void putBucketAbacFor(String bucket, String ruleId, String principal) {
        var status = webTestClient.put()
            .uri("/{bucket}?abac", bucket)
            .contentType(MediaType.APPLICATION_XML)
            .bodyValue(abacBody(ruleId, principal))
            .exchange()
            .returnResult()
            .getStatus();
        commonSteps.setResponseStatus(status);
    }

    @When("bucket ABAC configuration is requested")
    public void getBucketAbac() {
        var result = webTestClient.get()
            .uri("/test-bucket?abac")
            .accept(MediaType.APPLICATION_XML)
            .exchange()
            .expectBody(String.class)
            .returnResult();
        commonSteps.setResponseBody(result.getResponseBody());
        commonSteps.setResponseStatus(result.getStatus());
    }

    @When("bucket ABAC configuration is requested for {string}")
    public void getBucketAbacFor(String bucket) {
        var result = webTestClient.get()
            .uri("/{bucket}?abac", bucket)
            .accept(MediaType.APPLICATION_XML)
            .exchange()
            .expectBody(String.class)
            .returnResult();
        commonSteps.setResponseBody(result.getResponseBody());
        commonSteps.setResponseStatus(result.getStatus());
    }

    // ── Object Lock ──

    @Given("bucket object lock is preset with mode {string} and days {int}")
    public void bucketObjectLockPreset(String mode, int days) {
        webTestClient.put()
            .uri("/test-bucket?object-lock")
            .contentType(MediaType.APPLICATION_XML)
            .bodyValue(objectLockBody(mode, days))
            .exchange()
            .expectStatus().isOk();
    }

    @When("bucket object lock is configured with mode {string} and days {int}")
    public void putBucketObjectLock(String mode, int days) {
        var status = webTestClient.put()
            .uri("/test-bucket?object-lock")
            .contentType(MediaType.APPLICATION_XML)
            .bodyValue(objectLockBody(mode, days))
            .exchange()
            .returnResult()
            .getStatus();
        commonSteps.setResponseStatus(status);
    }

    @When("bucket object lock is configured for {string} with mode {string} and days {int}")
    public void putBucketObjectLockFor(String bucket, String mode, int days) {
        var status = webTestClient.put()
            .uri("/{bucket}?object-lock", bucket)
            .contentType(MediaType.APPLICATION_XML)
            .bodyValue(objectLockBody(mode, days))
            .exchange()
            .returnResult()
            .getStatus();
        commonSteps.setResponseStatus(status);
    }

    @When("bucket object lock configuration is requested")
    public void getBucketObjectLock() {
        var result = webTestClient.get()
            .uri("/test-bucket?object-lock")
            .accept(MediaType.APPLICATION_XML)
            .exchange()
            .expectBody(String.class)
            .returnResult();
        commonSteps.setResponseBody(result.getResponseBody());
        commonSteps.setResponseStatus(result.getStatus());
    }

    @When("bucket object lock configuration is requested for {string}")
    public void getBucketObjectLockFor(String bucket) {
        var result = webTestClient.get()
            .uri("/{bucket}?object-lock", bucket)
            .accept(MediaType.APPLICATION_XML)
            .exchange()
            .expectBody(String.class)
            .returnResult();
        commonSteps.setResponseBody(result.getResponseBody());
        commonSteps.setResponseStatus(result.getStatus());
    }

    // ── Metadata Configuration ──

    @Given("bucket metadata is preset with rule id {string} and status {string}")
    public void bucketMetadataPreset(String ruleId, String statusValue) {
        webTestClient.put()
            .uri("/test-bucket?metadata-config")
            .contentType(MediaType.APPLICATION_XML)
            .bodyValue(metadataBody(ruleId, statusValue))
            .exchange()
            .expectStatus().isOk();
    }

    @When("bucket metadata is configured with rule id {string} and status {string}")
    public void putBucketMetadata(String ruleId, String statusValue) {
        var status = webTestClient.put()
            .uri("/test-bucket?metadata-config")
            .contentType(MediaType.APPLICATION_XML)
            .bodyValue(metadataBody(ruleId, statusValue))
            .exchange()
            .returnResult()
            .getStatus();
        commonSteps.setResponseStatus(status);
    }

    @When("bucket metadata is configured for {string} with rule id {string} and status {string}")
    public void putBucketMetadataFor(String bucket, String ruleId, String statusValue) {
        var status = webTestClient.put()
            .uri("/{bucket}?metadata-config", bucket)
            .contentType(MediaType.APPLICATION_XML)
            .bodyValue(metadataBody(ruleId, statusValue))
            .exchange()
            .returnResult()
            .getStatus();
        commonSteps.setResponseStatus(status);
    }

    @When("bucket metadata configuration is requested")
    public void getBucketMetadata() {
        var result = webTestClient.get()
            .uri("/test-bucket?metadata-config")
            .accept(MediaType.APPLICATION_XML)
            .exchange()
            .expectBody(String.class)
            .returnResult();
        commonSteps.setResponseBody(result.getResponseBody());
        commonSteps.setResponseStatus(result.getStatus());
    }

    @When("bucket metadata configuration is requested for {string}")
    public void getBucketMetadataFor(String bucket) {
        var result = webTestClient.get()
            .uri("/{bucket}?metadata-config", bucket)
            .accept(MediaType.APPLICATION_XML)
            .exchange()
            .expectBody(String.class)
            .returnResult();
        commonSteps.setResponseBody(result.getResponseBody());
        commonSteps.setResponseStatus(result.getStatus());
    }

    @When("bucket metadata configuration is deleted")
    public void deleteBucketMetadata() {
        var status = webTestClient.delete()
            .uri("/test-bucket?metadata-config")
            .exchange()
            .returnResult()
            .getStatus();
        commonSteps.setResponseStatus(status);
    }

    @When("bucket metadata configuration is deleted for {string}")
    public void deleteBucketMetadataFor(String bucket) {
        var status = webTestClient.delete()
            .uri("/{bucket}?metadata-config", bucket)
            .exchange()
            .returnResult()
            .getStatus();
        commonSteps.setResponseStatus(status);
    }

    // ── Metadata Table Configuration ──

    @Given("bucket metadata table is preset with rule id {string} and table name {string}")
    public void bucketMetadataTablePreset(String ruleId, String tableName) {
        webTestClient.put()
            .uri("/test-bucket?metadata-table-config")
            .contentType(MediaType.APPLICATION_XML)
            .bodyValue(metadataTableBody(ruleId, tableName))
            .exchange()
            .expectStatus().isOk();
    }

    @When("bucket metadata table is configured with rule id {string} and table name {string}")
    public void putBucketMetadataTable(String ruleId, String tableName) {
        var status = webTestClient.put()
            .uri("/test-bucket?metadata-table-config")
            .contentType(MediaType.APPLICATION_XML)
            .bodyValue(metadataTableBody(ruleId, tableName))
            .exchange()
            .returnResult()
            .getStatus();
        commonSteps.setResponseStatus(status);
    }

    @When("bucket metadata table is configured for {string} with rule id {string} and table name {string}")
    public void putBucketMetadataTableFor(String bucket, String ruleId, String tableName) {
        var status = webTestClient.put()
            .uri("/{bucket}?metadata-table-config", bucket)
            .contentType(MediaType.APPLICATION_XML)
            .bodyValue(metadataTableBody(ruleId, tableName))
            .exchange()
            .returnResult()
            .getStatus();
        commonSteps.setResponseStatus(status);
    }

    @When("bucket metadata table configuration is requested")
    public void getBucketMetadataTable() {
        var result = webTestClient.get()
            .uri("/test-bucket?metadata-table-config")
            .accept(MediaType.APPLICATION_XML)
            .exchange()
            .expectBody(String.class)
            .returnResult();
        commonSteps.setResponseBody(result.getResponseBody());
        commonSteps.setResponseStatus(result.getStatus());
    }

    @When("bucket metadata table configuration is requested for {string}")
    public void getBucketMetadataTableFor(String bucket) {
        var result = webTestClient.get()
            .uri("/{bucket}?metadata-table-config", bucket)
            .accept(MediaType.APPLICATION_XML)
            .exchange()
            .expectBody(String.class)
            .returnResult();
        commonSteps.setResponseBody(result.getResponseBody());
        commonSteps.setResponseStatus(result.getStatus());
    }

    @When("bucket metadata table configuration is deleted")
    public void deleteBucketMetadataTable() {
        var status = webTestClient.delete()
            .uri("/test-bucket?metadata-table-config")
            .exchange()
            .returnResult()
            .getStatus();
        commonSteps.setResponseStatus(status);
    }

    @When("bucket metadata table configuration is deleted for {string}")
    public void deleteBucketMetadataTableFor(String bucket) {
        var status = webTestClient.delete()
            .uri("/{bucket}?metadata-table-config", bucket)
            .exchange()
            .returnResult()
            .getStatus();
        commonSteps.setResponseStatus(status);
    }

    // ── Inventory Table Configuration ──

    @Given("bucket inventory table is preset with id {string} and format {string}")
    public void bucketInventoryTablePreset(String id, String format) {
        webTestClient.put()
            .uri("/test-bucket?inventory-table-config")
            .contentType(MediaType.APPLICATION_XML)
            .bodyValue(tableBody("InventoryTableConfiguration", id, format))
            .exchange()
            .expectStatus().isOk();
    }

    @When("bucket inventory table is configured with id {string} and format {string}")
    public void putBucketInventoryTable(String id, String format) {
        var status = webTestClient.put()
            .uri("/test-bucket?inventory-table-config")
            .contentType(MediaType.APPLICATION_XML)
            .bodyValue(tableBody("InventoryTableConfiguration", id, format))
            .exchange()
            .returnResult()
            .getStatus();
        commonSteps.setResponseStatus(status);
    }

    @When("bucket inventory table is configured for {string} with id {string} and format {string}")
    public void putBucketInventoryTableFor(String bucket, String id, String format) {
        var status = webTestClient.put()
            .uri("/{bucket}?inventory-table-config", bucket)
            .contentType(MediaType.APPLICATION_XML)
            .bodyValue(tableBody("InventoryTableConfiguration", id, format))
            .exchange()
            .returnResult()
            .getStatus();
        commonSteps.setResponseStatus(status);
    }

    @When("bucket inventory table configuration is requested")
    public void getBucketInventoryTable() {
        var result = webTestClient.get()
            .uri("/test-bucket?inventory-table-config")
            .accept(MediaType.APPLICATION_XML)
            .exchange()
            .expectBody(String.class)
            .returnResult();
        commonSteps.setResponseBody(result.getResponseBody());
        commonSteps.setResponseStatus(result.getStatus());
    }

    @When("bucket inventory table configuration is requested for {string}")
    public void getBucketInventoryTableFor(String bucket) {
        var result = webTestClient.get()
            .uri("/{bucket}?inventory-table-config", bucket)
            .accept(MediaType.APPLICATION_XML)
            .exchange()
            .expectBody(String.class)
            .returnResult();
        commonSteps.setResponseBody(result.getResponseBody());
        commonSteps.setResponseStatus(result.getStatus());
    }

    // ── Journal Table Configuration ──

    @Given("bucket journal table is preset with id {string} and format {string}")
    public void bucketJournalTablePreset(String id, String format) {
        webTestClient.put()
            .uri("/test-bucket?journal-table-config")
            .contentType(MediaType.APPLICATION_XML)
            .bodyValue(tableBody("JournalTableConfiguration", id, format))
            .exchange()
            .expectStatus().isOk();
    }

    @When("bucket journal table is configured with id {string} and format {string}")
    public void putBucketJournalTable(String id, String format) {
        var status = webTestClient.put()
            .uri("/test-bucket?journal-table-config")
            .contentType(MediaType.APPLICATION_XML)
            .bodyValue(tableBody("JournalTableConfiguration", id, format))
            .exchange()
            .returnResult()
            .getStatus();
        commonSteps.setResponseStatus(status);
    }

    @When("bucket journal table is configured for {string} with id {string} and format {string}")
    public void putBucketJournalTableFor(String bucket, String id, String format) {
        var status = webTestClient.put()
            .uri("/{bucket}?journal-table-config", bucket)
            .contentType(MediaType.APPLICATION_XML)
            .bodyValue(tableBody("JournalTableConfiguration", id, format))
            .exchange()
            .returnResult()
            .getStatus();
        commonSteps.setResponseStatus(status);
    }

    @When("bucket journal table configuration is requested")
    public void getBucketJournalTable() {
        var result = webTestClient.get()
            .uri("/test-bucket?journal-table-config")
            .accept(MediaType.APPLICATION_XML)
            .exchange()
            .expectBody(String.class)
            .returnResult();
        commonSteps.setResponseBody(result.getResponseBody());
        commonSteps.setResponseStatus(result.getStatus());
    }

    @When("bucket journal table configuration is requested for {string}")
    public void getBucketJournalTableFor(String bucket) {
        var result = webTestClient.get()
            .uri("/{bucket}?journal-table-config", bucket)
            .accept(MediaType.APPLICATION_XML)
            .exchange()
            .expectBody(String.class)
            .returnResult();
        commonSteps.setResponseBody(result.getResponseBody());
        commonSteps.setResponseStatus(result.getStatus());
    }

    private String abacBody(String ruleId, String principal) {
        return "<AbacConfiguration><AbacRule>" +
            "<Id>" + ruleId + "</Id>" +
            "<Principal>" + principal + "</Principal>" +
            "<Resource>arn:aws:s3:::test-bucket/*</Resource>" +
            "<Action>s3:GetObject</Action>" +
            "<Condition><Tag>department</Tag><Value>engineering</Value></Condition>" +
            "</AbacRule></AbacConfiguration>";
    }

    private String objectLockBody(String mode, int days) {
        return "<ObjectLockConfiguration>" +
            "<ObjectLockEnabled>true</ObjectLockEnabled>" +
            "<Rule><DefaultRetention><Mode>" + mode + "</Mode><Days>" + days +
            "</Days></DefaultRetention></Rule>" +
            "</ObjectLockConfiguration>";
    }

    private String metadataBody(String ruleId, String statusValue) {
        return "<MetadataConfiguration><MetadataRule>" +
            "<Id>" + ruleId + "</Id>" +
            "<Status>" + statusValue + "</Status>" +
            "<MetadataResourceType>Object</MetadataResourceType>" +
            "<MetadataResourceSubtype>UserMetadata</MetadataResourceSubtype>" +
            "</MetadataRule></MetadataConfiguration>";
    }

    private String metadataTableBody(String ruleId, String tableName) {
        return "<MetadataTableConfiguration><MetadataTableRule>" +
            "<Id>" + ruleId + "</Id>" +
            "<Status>Enabled</Status>" +
            "<MetadataTableName>" + tableName + "</MetadataTableName>" +
            "<MetadataTableDatabase>default</MetadataTableDatabase>" +
            "</MetadataTableRule></MetadataTableConfiguration>";
    }

    private String tableBody(String root, String id, String format) {
        return "<" + root + ">" +
            "<Id>" + id + "</Id>" +
            "<DestinationFormat>" + format + "</DestinationFormat>" +
            "<ScheduleFrequency>Daily</ScheduleFrequency>" +
            "<Enabled>true</Enabled>" +
            "</" + root + ">";
    }

    // ── Then ── (uses BucketSteps.responseStatusIs and BucketSteps.metadataResponseContains)
}
