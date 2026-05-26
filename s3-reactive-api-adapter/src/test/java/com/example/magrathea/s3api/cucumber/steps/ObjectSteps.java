package com.example.magrathea.s3api.cucumber.steps;

import io.cucumber.datatable.DataTable;
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
    private String responseBody;

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

    @When("the object is stored via S3 API with storage class {string}")
    public void objectStoredWithStorageClass(String storageClass) {
        var status = webTestClient.put()
            .uri("/test-bucket/{key}", objectKey)
            .header("x-amz-storage-class", storageClass)
            .bodyValue(objectContent)
            .exchange()
            .returnResult()
            .getStatus();
        commonSteps.setResponseStatus(status);
    }

    @When("the object is stored via S3 API in bucket {string}")
    public void objectStoredInBucket(String bucket) {
        var status = webTestClient.put()
            .uri("/{bucket}/{key}", bucket, objectKey)
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

    @When("the object with key {string} is retrieved via S3 API")
    public void objectWithKeyRetrieved(String key) {
        var result = webTestClient.get()
            .uri("/test-bucket/{key}", key)
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

    @When("the objects are listed via S3 API V2")
    public void objectsListedV2() {
        var result = webTestClient.get()
            .uri("/test-bucket?list-type=2")
            .accept(MediaType.APPLICATION_XML)
            .exchange()
            .expectBody(String.class)
            .returnResult();
        responseBody = result.getResponseBody();
        commonSteps.setResponseStatus(result.getStatus());
    }

    @When("object {string} is copied to {string}")
    public void objectCopied(String sourceKey, String targetKey) {
        var result = webTestClient.put()
            .uri("/test-bucket/{key}", targetKey)
            .header("x-amz-copy-source", "/test-bucket/" + sourceKey)
            .exchange()
            .expectBody(String.class)
            .returnResult();
        responseBody = result.getResponseBody();
        commonSteps.setResponseStatus(result.getStatus());
    }

    @When("object {string} is copied to {string} in bucket {string}")
    public void objectCopiedToBucket(String sourceKey, String targetKey, String targetBucket) {
        var result = webTestClient.put()
            .uri("/{bucket}/{key}", targetBucket, targetKey)
            .header("x-amz-copy-source", "/test-bucket/" + sourceKey)
            .exchange()
            .expectBody(String.class)
            .returnResult();
        responseBody = result.getResponseBody();
        commonSteps.setResponseStatus(result.getStatus());
    }

    @When("object versions are listed via S3 API")
    public void objectVersionsListed() {
        var result = webTestClient.get()
            .uri("/test-bucket?versions")
            .accept(MediaType.APPLICATION_XML)
            .exchange()
            .expectBody(String.class)
            .returnResult();
        responseBody = result.getResponseBody();
        commonSteps.setResponseStatus(result.getStatus());
    }

    @When("objects are deleted via S3 API multi-delete")
    public void objectsDeletedViaMultiDelete(DataTable table) {
        var keys = table.asList();
        var body = new StringBuilder("<Delete>");
        keys.forEach(key -> body.append("<Object><Key>").append(key).append("</Key></Object>"));
        body.append("</Delete>");
        var result = webTestClient.post()
            .uri("/test-bucket?delete")
            .contentType(MediaType.APPLICATION_XML)
            .accept(MediaType.APPLICATION_XML)
            .bodyValue(body.toString())
            .exchange()
            .expectBody(String.class)
            .returnResult();
        responseBody = result.getResponseBody();
        commonSteps.setResponseStatus(result.getStatus());
    }

    @When("object ACL {string} is applied to {string}")
    public void objectAclApplied(String acl, String key) {
        var status = webTestClient.put()
            .uri("/test-bucket/{key}?acl", key)
            .header("x-amz-acl", acl)
            .exchange()
            .returnResult()
            .getStatus();
        commonSteps.setResponseStatus(status);
    }

    @When("object ACL {string} is applied to {string} in bucket {string}")
    public void objectAclAppliedInBucket(String acl, String key, String bucket) {
        var status = webTestClient.put()
            .uri("/{bucket}/{key}?acl", bucket, key)
            .header("x-amz-acl", acl)
            .exchange()
            .returnResult()
            .getStatus();
        commonSteps.setResponseStatus(status);
    }

    @When("object ACL is requested for {string}")
    public void objectAclRequested(String key) {
        var result = webTestClient.get()
            .uri("/test-bucket/{key}?acl", key)
            .accept(MediaType.APPLICATION_XML)
            .exchange()
            .expectBody(String.class)
            .returnResult();
        responseBody = result.getResponseBody();
        commonSteps.setResponseStatus(result.getStatus());
    }

    @When("object ACL is requested for {string} in bucket {string}")
    public void objectAclRequestedInBucket(String key, String bucket) {
        var result = webTestClient.get()
            .uri("/{bucket}/{key}?acl", bucket, key)
            .accept(MediaType.APPLICATION_XML)
            .exchange()
            .expectBody(String.class)
            .returnResult();
        responseBody = result.getResponseBody();
        commonSteps.setResponseStatus(result.getStatus());
    }

    @When("object tag {string} = {string} is applied to {string}")
    public void objectTagApplied(String tagKey, String tagValue, String objectKey) {
        var body = "<Tagging><TagSet><Tag><Key>" + tagKey + "</Key><Value>" + tagValue + "</Value></Tag></TagSet></Tagging>";
        var status = webTestClient.put()
            .uri("/test-bucket/{key}?tagging", objectKey)
            .contentType(MediaType.APPLICATION_XML)
            .bodyValue(body)
            .exchange()
            .returnResult()
            .getStatus();
        commonSteps.setResponseStatus(status);
    }

    @When("object tag {string} = {string} is applied to {string} in bucket {string}")
    public void objectTagAppliedInBucket(String tagKey, String tagValue, String objectKey, String bucket) {
        var body = "<Tagging><TagSet><Tag><Key>" + tagKey + "</Key><Value>" + tagValue + "</Value></Tag></TagSet></Tagging>";
        var status = webTestClient.put()
            .uri("/{bucket}/{key}?tagging", bucket, objectKey)
            .contentType(MediaType.APPLICATION_XML)
            .bodyValue(body)
            .exchange()
            .returnResult()
            .getStatus();
        commonSteps.setResponseStatus(status);
    }

    @When("object tags are requested for {string}")
    public void objectTagsRequested(String key) {
        var result = webTestClient.get()
            .uri("/test-bucket/{key}?tagging", key)
            .accept(MediaType.APPLICATION_XML)
            .exchange()
            .expectBody(String.class)
            .returnResult();
        responseBody = result.getResponseBody();
        commonSteps.setResponseStatus(result.getStatus());
    }

    @When("object tags are requested for {string} in bucket {string}")
    public void objectTagsRequestedInBucket(String key, String bucket) {
        var result = webTestClient.get()
            .uri("/{bucket}/{key}?tagging", bucket, key)
            .accept(MediaType.APPLICATION_XML)
            .exchange()
            .expectBody(String.class)
            .returnResult();
        responseBody = result.getResponseBody();
        commonSteps.setResponseStatus(result.getStatus());
    }

    @When("object tags are deleted for {string}")
    public void objectTagsDeleted(String key) {
        var status = webTestClient.delete()
            .uri("/test-bucket/{key}?tagging", key)
            .exchange()
            .returnResult()
            .getStatus();
        commonSteps.setResponseStatus(status);
    }

    @When("object tags are deleted for {string} in bucket {string}")
    public void objectTagsDeletedInBucket(String key, String bucket) {
        var status = webTestClient.delete()
            .uri("/{bucket}/{key}?tagging", bucket, key)
            .exchange()
            .returnResult()
            .getStatus();
        commonSteps.setResponseStatus(status);
    }

    @When("object attributes are requested for {string}")
    public void objectAttributesRequested(String key) {
        var result = webTestClient.get()
            .uri("/test-bucket/{key}?attributes", key)
            .header("x-amz-object-attributes", "ETag,ObjectSize,StorageClass")
            .accept(MediaType.APPLICATION_XML)
            .exchange()
            .expectBody(String.class)
            .returnResult();
        responseBody = result.getResponseBody();
        commonSteps.setResponseStatus(result.getStatus());
    }

    @When("object attributes are requested for {string} in bucket {string}")
    public void objectAttributesRequestedInBucket(String key, String bucket) {
        var result = webTestClient.get()
            .uri("/{bucket}/{key}?attributes", bucket, key)
            .header("x-amz-object-attributes", "ETag,ObjectSize,StorageClass")
            .accept(MediaType.APPLICATION_XML)
            .exchange()
            .expectBody(String.class)
            .returnResult();
        responseBody = result.getResponseBody();
        commonSteps.setResponseStatus(result.getStatus());
    }

    @When("the object is deleted via S3 API")
    public void objectDeleted() {
        var status = webTestClient.delete()
            .uri("/test-bucket/{key}", objectKey)
            .exchange()
            .returnResult()
            .getStatus();
        commonSteps.setResponseStatus(status);
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

    @Then("the object metadata response contains {string}")
    public void objectMetadataResponseContains(String expected) {
        assertNotNull(responseBody);
        assertTrue(responseBody.contains(expected));
    }

    @Then("the object appears in the object list V2")
    public void objectAppearsInListV2() {
        assertNotNull(responseBody);
        assertTrue(responseBody.contains(objectKey));
    }

    @Then("object {string} content is {string}")
    public void objectContentIs(String key, String expectedContent) {
        var body = webTestClient.get()
            .uri("/test-bucket/{key}", key)
            .exchange()
            .expectBody(String.class)
            .returnResult()
            .getResponseBody();
        assertEquals(expectedContent, body);
    }

    @Then("the versions response contains object {string}")
    public void versionsResponseContainsObject(String key) {
        assertNotNull(responseBody);
        assertTrue(responseBody.contains(key));
    }

    @Then("object {string} does not appear in the object list")
    public void objectDoesNotAppearInObjectList(String key) {
        var body = webTestClient.get()
            .uri("/test-bucket")
            .accept(MediaType.APPLICATION_XML)
            .exchange()
            .expectBody(String.class)
            .returnResult()
            .getResponseBody();
        assertNotNull(body);
        assertFalse(body.contains(key));
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

    // ── Batch 1 Phase F: RenameObject ──

    @When("object {string} is renamed to {string}")
    public void objectRenamed(String sourceKey, String destinationKey) {
        var status = webTestClient.put()
            .uri("/test-bucket/{key}?rename", sourceKey)
            .header("x-amz-rename-destination", destinationKey)
            .exchange()
            .returnResult()
            .getStatus();
        commonSteps.setResponseStatus(status);
    }

    // ── Batch 1 Phase F: UpdateObjectEncryption ──

    @When("encryption is updated for object {string} with SSE algorithm {string}")
    public void updateObjectEncryption(String key, String sseAlgorithm) {
        var status = webTestClient.put()
            .uri("/test-bucket/{key}?encryption", key)
            .header("x-amz-server-side-encryption", sseAlgorithm)
            .exchange()
            .returnResult()
            .getStatus();
        commonSteps.setResponseStatus(status);
    }

    @When("encryption is updated for object {string} with SSE algorithm {string} and KMS key {string}")
    public void updateObjectEncryptionWithKms(String key, String sseAlgorithm, String kmsKeyId) {
        var status = webTestClient.put()
            .uri("/test-bucket/{key}?encryption", key)
            .header("x-amz-server-side-encryption", sseAlgorithm)
            .header("x-amz-server-side-encryption-aws-kms-key-id", kmsKeyId)
            .exchange()
            .returnResult()
            .getStatus();
        commonSteps.setResponseStatus(status);
    }

    // ── Batch 1 Phase F: GetObjectTorrent ──

    @When("the torrent is retrieved for object {string}")
    public void getObjectTorrent(String key) {
        var result = webTestClient.get()
            .uri("/test-bucket/{key}?torrent", key)
            .exchange()
            .expectBody(String.class)
            .returnResult();
        retrievedContent = result.getResponseBody();
        commonSteps.setResponseStatus(result.getStatus());
    }

    // ── Batch 1 Phase F: RestoreObject ──

    @When("the object is restored via S3 API with tier {string} and days {int}")
    public void restoreObjectWithTierAndDays(String tier, int days) {
        var body = "<RestoreObject><RestoreRequest><Tier>" + tier +
            "</Tier><Days>" + days + "</Days></RestoreRequest></RestoreObject>";
        var status = webTestClient.post()
            .uri("/test-bucket/{key}?restore", objectKey)
            .contentType(MediaType.APPLICATION_XML)
            .bodyValue(body)
            .exchange()
            .returnResult()
            .getStatus();
        commonSteps.setResponseStatus(status);
    }

    @When("the object is restored via S3 API with Glacier tier {string} and days {int}")
    public void restoreObjectWithGlacierTier(String glacierTier, int days) {
        var body = "<RestoreObject><RestoreRequest><GlacierJobParameters><Tier>" +
            glacierTier + "</Tier></GlacierJobParameters><Days>" + days +
            "</Days></RestoreRequest></RestoreObject>";
        var status = webTestClient.post()
            .uri("/test-bucket/{key}?restore", objectKey)
            .contentType(MediaType.APPLICATION_XML)
            .bodyValue(body)
            .exchange()
            .returnResult()
            .getStatus();
        commonSteps.setResponseStatus(status);
    }

    @When("the object with key {string} is restored via S3 API")
    public void objectWithKeyRestored(String key) {
        var body = "<RestoreObject><RestoreRequest><Tier>Standard</Tier><Days>30</Days></RestoreRequest></RestoreObject>";
        var status = webTestClient.post()
            .uri("/test-bucket/{key}?restore", key)
            .contentType(MediaType.APPLICATION_XML)
            .bodyValue(body)
            .exchange()
            .returnResult()
            .getStatus();
        commonSteps.setResponseStatus(status);
    }
}
