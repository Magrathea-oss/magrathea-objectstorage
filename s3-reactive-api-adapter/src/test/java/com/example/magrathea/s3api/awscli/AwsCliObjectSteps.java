package com.example.magrathea.s3api.awscli;

import com.example.magrathea.reactive.infrastructure.adapter.persistence.InMemoryReactiveBucketRepository;
import com.example.magrathea.reactive.infrastructure.adapter.persistence.InMemoryReactiveMultipartUploadRepository;
import com.example.magrathea.reactive.infrastructure.adapter.persistence.InMemoryReactiveS3ObjectRepository;
import com.example.magrathea.s3api.adapter.web.S3BucketConfigHandler;
import io.cucumber.java.Before;
import io.cucumber.java.en.Given;
import io.cucumber.java.en.When;
import io.cucumber.java.en.Then;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.web.server.LocalServerPort;

import java.nio.file.*;
import java.util.*;

import static org.junit.jupiter.api.Assertions.*;

public class AwsCliObjectSteps {

    @LocalServerPort
    private int port;

    @Autowired
    private InMemoryReactiveBucketRepository bucketRepository;

    @Autowired
    private InMemoryReactiveS3ObjectRepository objectRepository;

    @Autowired
    private InMemoryReactiveMultipartUploadRepository multipartUploadRepository;

    @Autowired
    private S3BucketConfigHandler bucketConfigHandler;

    private String currentBucket;
    private String currentKey;
    private String currentContent;
    private String retrievedContent;
    private int exitCode;
    private String stdout;
    private String stderr;

    private String getEndpoint() {
        return "http://localhost:" + port;
    }

    private String[] awsCommand(String... args) {
        var cmd = new ArrayList<String>();
        cmd.add("aws");
        cmd.add("--endpoint-url");
        cmd.add(getEndpoint());
        cmd.add("s3api");
        cmd.addAll(Arrays.asList(args));
        return cmd.toArray(new String[0]);
    }

    private int runAws(String... args) throws Exception {
        var pb = new ProcessBuilder(awsCommand(args));
        pb.redirectErrorStream(true);
        var env = pb.environment();
        env.putIfAbsent("AWS_ACCESS_KEY_ID", "test");
        env.putIfAbsent("AWS_SECRET_ACCESS_KEY", "test");
        env.putIfAbsent("AWS_DEFAULT_REGION", "us-east-1");
        env.putIfAbsent("AWS_EC2_METADATA_DISABLED", "true");
        var proc = pb.start();
        stdout = new String(proc.getInputStream().readAllBytes());
        stderr = "";
        exitCode = proc.waitFor();
        return exitCode;
    }

    private Path writeTempFile(String content) throws Exception {
        var path = Files.createTempFile("magrathea-", ".txt");
        Files.writeString(path, content);
        return path;
    }

    @Before
    public void resetRepositories() {
        bucketRepository.reset();
        objectRepository.reset();
        multipartUploadRepository.reset();
        bucketConfigHandler.resetInMemoryConfigurations();
        currentBucket = null;
        currentKey = null;
        currentContent = null;
        retrievedContent = null;
        exitCode = 0;
        stdout = "";
        stderr = "";
    }

    // ── Given steps ──

    @Given("a bucket name {string}")
    public void aBucketName(String bucketName) {
        currentBucket = bucketName;
    }

    @Given("bucket {string} exists")
    public void bucketExists(String bucketName) throws Exception {
        currentBucket = bucketName;
        // Create bucket via AWS CLI after the scenario hook clears in-memory state.
        runAws("create-bucket", "--bucket", bucketName);
        assertEquals(0, exitCode, "Bucket should be created via CLI. Output: " + stdout);
    }

    @Given("bucket {string} does not exist")
    public void bucketDoesNotExist(String bucketName) throws Exception {
        currentBucket = bucketName;
        runAws("head-bucket", "--bucket", bucketName);
        assertNotEquals(0, exitCode, "Bucket should not exist: " + stdout);
    }

    @Given("an object with key {string} and content {string}")
    public void anObjectWithKeyAndContent(String key, String content) throws Exception {
        currentKey = key;
        currentContent = content;
    }

    @Given("object {string} exists with content {string}")
    public void objectExistsWithContent(String key, String content) throws Exception {
        currentKey = key;
        currentContent = content;
        var body = writeTempFile(content);
        runAws("put-object", "--bucket", currentBucket, "--key", key, "--body", body.toString());
        assertEquals(0, exitCode, "Object should exist after put-object. Output: " + stdout);
        Files.deleteIfExists(body);
    }

    @Given("object {string} exists")
    public void objectExists(String key) throws Exception {
        objectExistsWithContent(key, "dummy content");
    }

    @Given("object {string} does not exist")
    public void objectDoesNotExist(String key) throws Exception {
        // Verify it doesn't exist via head-object
        runAws("head-object", "--bucket", currentBucket, "--key", key);
        assertNotEquals(0, exitCode, "Object should not exist");
    }

    // ── When steps ──

    @When("the object is stored via AWS CLI with default headers")
    public void objectStoredViaAwsCliWithDefaultHeaders() throws Exception {
        var body = writeTempFile(currentContent);
        // Run aws s3api put-object WITHOUT specifying extra headers
        // AWS CLI will send its default headers (x-amz-checksum-crc64nvme, X-Amz-Date, etc.)
        runAws("put-object", "--bucket", currentBucket, "--key", currentKey,
            "--body", body.toString());
        Files.deleteIfExists(body);
    }

    @When("the object is stored via AWS CLI in bucket {string}")
    public void objectStoredViaAwsCliInBucket(String bucket) throws Exception {
        var body = writeTempFile(currentContent);
        runAws("put-object", "--bucket", bucket, "--key", currentKey,
            "--body", body.toString());
        Files.deleteIfExists(body);
    }

    @Then("the AWS CLI exit code is {int}")
    public void awsCliExitCodeIs(int expectedCode) {
        assertEquals(expectedCode, exitCode,
            "AWS CLI should exit with code " + expectedCode + ". Output: " + stdout);
    }

    @Then("the AWS CLI exit code is non-zero")
    public void awsCliExitCodeIsNonZero() {
        assertNotEquals(0, exitCode,
            "AWS CLI should exit with non-zero code (failure)");
    }

    @When("the object is stored via S3 API")
    public void objectStored() throws Exception {
        var body = writeTempFile(currentContent);
        runAws("put-object", "--bucket", currentBucket, "--key", currentKey, "--body", body.toString());
        Files.deleteIfExists(body);
    }

    @When("the object is stored via S3 API with storage class {string}")
    public void objectStoredWithStorageClass(String storageClass) throws Exception {
        var body = writeTempFile(currentContent);
        runAws("put-object", "--bucket", currentBucket, "--key", currentKey,
            "--body", body.toString(), "--storage-class", storageClass);
        Files.deleteIfExists(body);
    }

    @When("the object is stored via S3 API in bucket {string}")
    public void objectStoredInBucket(String bucket) throws Exception {
        currentBucket = bucket;
        var body = writeTempFile(currentContent);
        runAws("put-object", "--bucket", bucket, "--key", currentKey, "--body", body.toString());
        Files.deleteIfExists(body);
    }

    @When("the object is retrieved via S3 API")
    public void objectRetrieved() throws Exception {
        var outFile = Files.createTempFile("magrathea-out-", ".txt");
        runAws("get-object", "--bucket", currentBucket, "--key", currentKey, outFile.toString());
        if (exitCode == 0) {
            retrievedContent = Files.readString(outFile);
        }
        Files.deleteIfExists(outFile);
    }

    @When("the object with key {string} is retrieved via S3 API")
    public void objectWithKeyRetrieved(String key) throws Exception {
        currentKey = key;
        objectRetrieved();
    }

    @When("HEAD request is sent for object {string}")
    public void headObject(String key) throws Exception {
        currentKey = key;
        runAws("head-object", "--bucket", currentBucket, "--key", key);
    }

    @When("the bucket is created via AWS CLI")
    public void bucketCreatedViaAwsCli() throws Exception {
        runAws("create-bucket", "--bucket", currentBucket);
    }

    @When("the buckets are listed via AWS CLI")
    public void bucketsListedViaAwsCli() throws Exception {
        runAws("list-buckets");
    }

    @When("HEAD request is sent for bucket {string}")
    public void headBucket(String bucket) throws Exception {
        currentBucket = bucket;
        runAws("head-bucket", "--bucket", bucket);
    }

    @When("bucket location is requested via AWS CLI for {string}")
    public void bucketLocationRequestedViaAwsCli(String bucket) throws Exception {
        currentBucket = bucket;
        runAws("get-bucket-location", "--bucket", bucket);
    }

    @When("bucket versioning is requested via AWS CLI for {string}")
    public void bucketVersioningRequestedViaAwsCli(String bucket) throws Exception {
        currentBucket = bucket;
        runAws("get-bucket-versioning", "--bucket", bucket);
    }

    @When("bucket versioning is set via AWS CLI for {string} to {string}")
    public void bucketVersioningSetViaAwsCli(String bucket, String status) throws Exception {
        currentBucket = bucket;
        runAws("put-bucket-versioning", "--bucket", bucket,
            "--versioning-configuration", "Status=" + status);
    }

    @When("the bucket {string} is deleted via AWS CLI")
    public void bucketDeletedViaAwsCli(String bucket) throws Exception {
        currentBucket = bucket;
        runAws("delete-bucket", "--bucket", bucket);
    }

    @When("the objects are listed via AWS CLI")
    public void objectsListedViaAwsCli() throws Exception {
        runAws("list-objects", "--bucket", currentBucket);
    }

    @When("the objects are listed via AWS CLI V2")
    public void objectsListedViaAwsCliV2() throws Exception {
        runAws("list-objects-v2", "--bucket", currentBucket);
    }

    @When("the object is deleted via AWS CLI")
    public void objectDeletedViaAwsCli() throws Exception {
        runAws("delete-object", "--bucket", currentBucket, "--key", currentKey);
    }

    @When("the object with key {string} is deleted via AWS CLI")
    public void objectWithKeyDeletedViaAwsCli(String key) throws Exception {
        currentKey = key;
        objectDeletedViaAwsCli();
    }

    @When("object attributes are requested via AWS CLI for {string}")
    public void objectAttributesRequestedViaAwsCli(String key) throws Exception {
        currentKey = key;
        runAws("get-object-attributes", "--bucket", currentBucket, "--key", key,
            "--object-attributes", "StorageClass");
    }

    // ── Then steps ──

    @Then("the response status is {int}")
    public void responseStatusIs(int expectedStatus) {
        // AWS CLI returns 0 for success (200/204), non-zero for failure
        if (expectedStatus == 200 || expectedStatus == 204) {
            assertEquals(0, exitCode, "Expected AWS CLI success (exit 0). Output: " + stdout);
        } else {
            assertNotEquals(0, exitCode, "Expected AWS CLI failure (exit non-zero)");
        }
    }

    @Then("the content is {string}")
    public void contentIs(String expectedContent) {
        assertEquals(expectedContent, retrievedContent);
    }

    @Then("the object appears in the object list")
    public void objectAppearsInList() throws Exception {
        runAws("list-objects", "--bucket", currentBucket);
        assertEquals(0, exitCode, "list-objects should succeed. Output: " + stdout);
        assertTrue(stdout.contains(currentKey), "Object key should appear in list-objects output: " + stdout);
    }

    @Then("the object no longer appears in the object list")
    public void objectNoLongerAppears() throws Exception {
        runAws("list-objects", "--bucket", currentBucket);
        assertEquals(0, exitCode, "list-objects should succeed. Output: " + stdout);
        assertFalse(stdout.contains(currentKey), "Object key should not appear in list-objects output: " + stdout);
    }

    @Then("the AWS CLI output contains object {string}")
    public void awsCliOutputContainsObject(String key) {
        assertTrue(stdout.contains(key), "AWS CLI output should contain object key '" + key + "': " + stdout);
    }

    @Then("the AWS CLI output contains bucket {string}")
    public void awsCliOutputContainsBucket(String bucket) {
        assertTrue(stdout.contains(bucket), "AWS CLI output should contain bucket '" + bucket + "': " + stdout);
    }

    @Then("the AWS CLI output does not contain bucket {string}")
    public void awsCliOutputDoesNotContainBucket(String bucket) {
        assertFalse(stdout.contains(bucket), "AWS CLI output should not contain bucket '" + bucket + "': " + stdout);
    }

    @Then("the AWS CLI output contains {string}")
    public void awsCliOutputContains(String expected) {
        assertTrue(stdout.contains(expected), "AWS CLI output should contain '" + expected + "': " + stdout);
    }

    @Then("the AWS CLI bucket location contains {string}")
    public void awsCliBucketLocationContains(String expected) {
        assertEquals(0, exitCode, "get-bucket-location should succeed. Output: " + stdout);
        assertTrue(stdout.contains(expected), "Bucket location should contain '" + expected + "': " + stdout);
    }

    @Then("the AWS CLI bucket versioning contains status {string}")
    public void awsCliBucketVersioningContainsStatus(String status) {
        assertEquals(0, exitCode, "get-bucket-versioning should succeed. Output: " + stdout);
        assertTrue(stdout.contains("\"Status\": \"" + status + "\"")
                || (stdout.contains("Status") && stdout.contains(status)),
            "Bucket versioning should contain status '" + status + "': " + stdout);
    }

    @Then("bucket {string} does not appear in the bucket list")
    public void bucketDoesNotAppearInBucketList(String bucket) throws Exception {
        runAws("list-buckets");
        assertEquals(0, exitCode, "list-buckets should succeed. Output: " + stdout);
        assertFalse(stdout.contains(bucket), "Bucket should not appear in list-buckets output: " + stdout);
    }

    @Then("the AWS CLI output does not contain object {string}")
    public void awsCliOutputDoesNotContainObject(String key) {
        assertFalse(stdout.contains(key), "AWS CLI output should not contain object key '" + key + "': " + stdout);
    }

    @Then("object {string} does not appear in the object list")
    public void objectDoesNotAppearInObjectList(String key) throws Exception {
        runAws("list-objects", "--bucket", currentBucket);
        assertEquals(0, exitCode, "list-objects should succeed. Output: " + stdout);
        assertFalse(stdout.contains(key), "Object key should not appear in list-objects output: " + stdout);
    }

    @Then("the AWS CLI object attributes include storage class {string}")
    public void awsCliObjectAttributesIncludeStorageClass(String storageClass) {
        assertEquals(0, exitCode, "get-object-attributes should succeed. Output: " + stdout);
        assertTrue(stdout.contains("\"StorageClass\": \"" + storageClass + "\"")
                || (stdout.contains("StorageClass") && stdout.contains(storageClass)),
            "Object attributes should include storage class '" + storageClass + "': " + stdout);
    }

    // ── Checksum header step definitions ──

    @When("the object is stored via AWS CLI with checksum algorithm {string}")
    public void objectStoredViaAwsCliWithChecksumAlgorithm(String algorithm) throws Exception {
        var body = writeTempFile(currentContent);
        runAws("put-object", "--bucket", currentBucket, "--key", currentKey,
            "--body", body.toString(), "--checksum-algorithm", algorithm);
        Files.deleteIfExists(body);
    }

    @When("the object is stored via AWS CLI with content-md5 {string}")
    public void objectStoredViaAwsCliWithContentMd5(String md5Value) throws Exception {
        var body = writeTempFile(currentContent);
        runAws("put-object", "--bucket", currentBucket, "--key", currentKey,
            "--body", body.toString(), "--content-md5", md5Value);
        Files.deleteIfExists(body);
    }

    @Then("HEAD response contains header {string}")
    public void headResponseContainsHeader(String headerName) throws Exception {
        runAws("head-object", "--bucket", currentBucket, "--key", currentKey);
        assertEquals(0, exitCode, "HEAD should succeed");
        assertTrue(stdout.contains(headerName), "HEAD response should contain header '" + headerName + "'");
    }

    @Then("HEAD response contains header {string} with value {string}")
    public void headResponseContainsHeaderWithValue(String headerName, String expectedValue) throws Exception {
        runAws("head-object", "--bucket", currentBucket, "--key", currentKey);
        assertEquals(0, exitCode, "HEAD should succeed");
        assertTrue(stdout.contains(headerName + " = " + expectedValue) || stdout.contains(headerName + ": " + expectedValue),
            "HEAD response should contain header '" + headerName + "' with value '" + expectedValue + "'");
    }
}
