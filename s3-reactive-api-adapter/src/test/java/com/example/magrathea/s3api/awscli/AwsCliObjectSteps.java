package com.example.magrathea.s3api.awscli;

import io.cucumber.java.en.Given;
import io.cucumber.java.en.When;
import io.cucumber.java.en.Then;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.test.web.server.LocalServerPort;

import java.io.*;
import java.nio.file.*;
import java.util.*;

import static org.junit.jupiter.api.Assertions.*;

public class AwsCliObjectSteps {

    @LocalServerPort
    private int port;

    private String endpointUrl;
    private String currentBucket;
    private String currentKey;
    private String currentContent;
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

    // ── Given steps ──

    @Given("bucket {string} exists")
    public void bucketExists(String bucketName) throws Exception {
        currentBucket = bucketName;
        // Create bucket via AWS CLI
        runAws("create-bucket", "--bucket", bucketName);
        assertEquals(0, exitCode, "Bucket should be created via CLI");
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
        assertEquals(0, exitCode, "Object should exist after put-object");
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
            "AWS CLI should exit with code " + expectedCode);
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
        runAws("get-object", "--bucket", currentBucket, "--key", currentKey,
            "--output", outFile.toString());
        if (exitCode == 0) {
            stdout = Files.readString(outFile);
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
        runAws("head-object", "--bucket", currentBucket, "--key", key);
    }

    // ── Then steps ──

    @Then("the response status is {int}")
    public void responseStatusIs(int expectedStatus) {
        // AWS CLI returns 0 for success (200/204), non-zero for failure
        if (expectedStatus == 200 || expectedStatus == 204) {
            assertEquals(0, exitCode, "Expected AWS CLI success (exit 0)");
        } else {
            assertNotEquals(0, exitCode, "Expected AWS CLI failure (exit non-zero)");
        }
    }

    @Then("the content is {string}")
    public void contentIs(String expectedContent) {
        assertEquals(expectedContent, stdout.trim());
    }

    @Then("the object appears in the object list")
    public void objectAppearsInList() throws Exception {
        runAws("list-objects", "--bucket", currentBucket);
        assertEquals(0, exitCode);
        assertTrue(stdout.contains(currentKey), "Object key should appear in list-objects output");
    }

    @Then("the object no longer appears in the object list")
    public void objectNoLongerAppears() throws Exception {
        runAws("list-objects", "--bucket", currentBucket);
        assertFalse(stdout.contains(currentKey), "Object key should not appear in list-objects output");
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
