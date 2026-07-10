package com.example.magrathea.s3api.phaseep2;

import com.example.magrathea.objectstorage.repository.storageengine.adapter.StorageEngineReactiveBucketRepository;
import com.example.magrathea.objectstorage.repository.storageengine.adapter.StorageEngineReactiveMultipartUploadRepository;
import com.example.magrathea.objectstorage.repository.storageengine.adapter.StorageEngineReactiveS3ObjectRepository;
import io.cucumber.java.Before;
import io.cucumber.java.en.Given;
import io.cucumber.java.en.Then;
import io.cucumber.java.en.When;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.core.io.buffer.DataBuffer;
import org.springframework.core.io.buffer.DataBufferUtils;
import org.springframework.http.MediaType;
import org.springframework.test.web.reactive.server.WebTestClient;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Comparator;
import java.util.regex.Pattern;

import static org.assertj.core.api.Assertions.assertThat;

public class PhaseEp2MetadataDurabilitySteps {

    private static final Path CONFIGURED_STORAGE_ROOT = Path.of("target/storage-engine-it/current");
    private static final Pattern UPLOAD_ID_PATTERN = Pattern.compile("<UploadId>([^<]+)</UploadId>");

    @LocalServerPort
    int port;

    @Autowired
    ObjectProvider<StorageEngineReactiveBucketRepository> bucketRepository;

    @Autowired
    ObjectProvider<StorageEngineReactiveS3ObjectRepository> objectRepository;

    @Autowired
    ObjectProvider<StorageEngineReactiveMultipartUploadRepository> multipartRepository;

    private WebTestClient client;
    private String currentBucket;
    private String recordedUploadId;

    @Before
    public void setUpClient() {
        client = WebTestClient.bindToServer()
            .baseUrl("http://127.0.0.1:" + port)
            .responseTimeout(java.time.Duration.ofSeconds(15))
            .build();
    }

    @Given("the storage-engine profile is active with filesystem root {string}")
    public void storageEngineProfileActiveWithFilesystemRoot(String root) {
        Path scenarioRoot = Path.of(root);
        cleanScenarioRootAndPointConfiguredSymlink(scenarioRoot);
        resetRepositories();
        currentBucket = null;
        recordedUploadId = null;
    }

    @Given("an object exists at bucket {string} and key {string}")
    public void objectExistsAtBucketAndKey(String bucket, String key) {
        createBucketIfNeeded(bucket);
        putObject(bucket, key, "ep2 metadata durability fixture for " + key);
        currentBucket = bucket;
    }

    @Given("the object has tags {string} and {string}")
    public void objectHasTwoTags(String first, String second) {
        putObjectTags(currentBucket, "documents/tags-doc.pdf", first, second);
    }

    @Given("the object has an ACL grant {string} for user {string}")
    public void objectHasAclGrant(String permission, String user) {
        putObjectAcl(currentBucket, "documents/acl-file.txt", permission, user);
    }

    @Given("bucket {string} is created through the S3 CreateBucket API")
    public void bucketCreatedThroughS3CreateBucketApi(String bucket) {
        createBucketIfNeeded(bucket);
        currentBucket = bucket;
    }

    @Given("an object exists at bucket {string} and key {string} with tags {string} and an ACL grant {string} for user {string}")
    public void objectExistsWithTagsAndAcl(String bucket, String key, String tag, String permission, String user) {
        createBucketIfNeeded(bucket);
        putObject(bucket, key, "combined metadata fixture");
        putObjectTags(bucket, key, tag);
        putObjectAcl(bucket, key, permission, user);
        currentBucket = bucket;
    }

    @Given("a multipart upload is initiated for bucket {string} and key {string} with the upload ID recorded")
    public void multipartUploadInitiated(String bucket, String key) {
        String body = client.post()
            .uri("/" + bucket + "/" + key + "?uploads")
            .exchange()
            .expectStatus().isOk()
            .expectBody(String.class)
            .returnResult()
            .getResponseBody();
        var matcher = UPLOAD_ID_PATTERN.matcher(body == null ? "" : body);
        assertThat(matcher.find()).as("initiate multipart response must contain UploadId: %s", body).isTrue();
        recordedUploadId = matcher.group(1);
        currentBucket = bucket;
    }

    @When("the application process is stopped and started again with the same filesystem root")
    public void applicationProcessStoppedAndStartedAgainWithSameFilesystemRoot() {
        resetRepositories();
    }

    @Then("GetObjectTagging for key {string} returns tags {string} and {string}")
    public void getObjectTaggingReturnsTwoTags(String key, String first, String second) {
        String body = getString("/" + currentBucket + "/" + key + "?tagging");
        assertTagPresent(body, first);
        assertTagPresent(body, second);
    }

    @Then("GetObjectTagging for key {string} returns tags {string}")
    public void getObjectTaggingReturnsTags(String key, String tag) {
        String body = getString("/" + currentBucket + "/" + key + "?tagging");
        assertTagPresent(body, tag);
    }

    @Then("GetObjectAcl for key {string} returns grant {string} for user {string}")
    public void getObjectAclReturnsGrant(String key, String permission, String user) {
        String body = getString("/" + currentBucket + "/" + key + "?acl");
        assertThat(body).contains(permission).contains(user);
    }

    @Then("a HeadBucket request for bucket {string} returns HTTP {int}")
    public void headBucketReturnsHttp(String bucket, int status) {
        client.head()
            .uri("/" + bucket)
            .exchange()
            .expectStatus().isEqualTo(status);
        currentBucket = bucket;
    }

    @Then("listing multipart uploads for bucket {string} includes the recorded upload ID")
    public void listingMultipartUploadsIncludesRecordedUploadId(String bucket) {
        String body = getString("/" + bucket + "?uploads");
        assertThat(body).contains(recordedUploadId);
    }

    private void createBucketIfNeeded(String bucket) {
        client.put()
            .uri("/" + bucket)
            .exchange()
            .expectStatus().value(status -> assertThat(status).isIn(200, 409));
    }

    private void putObject(String bucket, String key, String body) {
        client.put()
            .uri("/" + bucket + "/" + key)
            .contentType(MediaType.TEXT_PLAIN)
            .header("x-amz-storage-class", "STANDARD")
            .bodyValue(body)
            .exchange()
            .expectStatus().isOk();
    }

    private void putObjectTags(String bucket, String key, String... tags) {
        StringBuilder xml = new StringBuilder("<Tagging><TagSet>");
        for (String tag : tags) {
            String[] parts = tag.split("=", 2);
            xml.append("<Tag><Key>").append(parts[0]).append("</Key><Value>")
                .append(parts.length > 1 ? parts[1] : "").append("</Value></Tag>");
        }
        xml.append("</TagSet></Tagging>");
        client.put()
            .uri("/" + bucket + "/" + key + "?tagging")
            .contentType(MediaType.APPLICATION_XML)
            .bodyValue(xml.toString())
            .exchange()
            .expectStatus().isOk();
    }

    private void putObjectAcl(String bucket, String key, String permission, String user) {
        client.put()
            .uri("/" + bucket + "/" + key + "?acl")
            .header("x-amz-grant-full-control", user)
            .exchange()
            .expectStatus().isOk();
    }

    private String getString(String uri) {
        return client.get()
            .uri(uri)
            .exchange()
            .expectStatus().isOk()
            .returnResult(DataBuffer.class)
            .getResponseBody()
            .map(buffer -> {
                byte[] bytes = new byte[buffer.readableByteCount()];
                buffer.read(bytes);
                DataBufferUtils.release(buffer);
                return new String(bytes, StandardCharsets.UTF_8);
            })
            .collectList()
            .map(parts -> String.join("", parts))
            .block();
    }

    private static void assertTagPresent(String body, String expectedTag) {
        String[] parts = expectedTag.split("=", 2);
        assertThat(body).contains(parts[0]);
        if (parts.length > 1) {
            assertThat(body).contains(parts[1]);
        }
    }

    private void resetRepositories() {
        bucketRepository.ifAvailable(StorageEngineReactiveBucketRepository::reloadFromDisk);
        objectRepository.ifAvailable(StorageEngineReactiveS3ObjectRepository::reloadFromDisk);
        multipartRepository.ifAvailable(StorageEngineReactiveMultipartUploadRepository::reloadFromDisk);
    }

    private void cleanScenarioRootAndPointConfiguredSymlink(Path scenarioRoot) {
        try {
            deleteRecursively(scenarioRoot);
            Files.createDirectories(scenarioRoot);
            deleteRecursively(CONFIGURED_STORAGE_ROOT);
            Files.createDirectories(CONFIGURED_STORAGE_ROOT.getParent());
            Files.createSymbolicLink(CONFIGURED_STORAGE_ROOT, scenarioRoot.toAbsolutePath());
            createClusterLayout(CONFIGURED_STORAGE_ROOT);
            createClusterLayout(scenarioRoot);
        } catch (IOException e) {
            throw new UncheckedIOException("Failed to prepare scenario storage root " + scenarioRoot, e);
        }
    }

    private static void createClusterLayout(Path root) throws IOException {
        Files.createDirectories(root.resolve("nodes/node-001/chunks"));
        Files.createDirectories(root.resolve("devices/bucket"));
        Files.createDirectories(root.resolve("devices/dedup"));
        Files.createDirectories(root.resolve("metadata/manifests"));
        Files.createDirectories(root.resolve("metadata/content-address-index"));
        Files.createDirectories(root.resolve("metadata/s3-object-references"));
        Files.createDirectories(root.resolve("metadata/s3-object-acls"));
    }

    private static void deleteRecursively(Path path) throws IOException {
        if (path == null || (!Files.exists(path) && !Files.isSymbolicLink(path))) {
            return;
        }
        if (Files.isSymbolicLink(path) || Files.isRegularFile(path)) {
            Files.deleteIfExists(path);
            return;
        }
        try (var stream = Files.walk(path)) {
            for (Path entry : stream.sorted(Comparator.reverseOrder()).toList()) {
                Files.deleteIfExists(entry);
            }
        }
    }
}
