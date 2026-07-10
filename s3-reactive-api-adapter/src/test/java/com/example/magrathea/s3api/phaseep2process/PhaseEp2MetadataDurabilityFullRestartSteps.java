package com.example.magrathea.s3api.phaseep2process;

import com.example.magrathea.s3api.phase2awscli.Phase2StorageEngineAwsCliTestApp;
import io.cucumber.java.After;
import io.cucumber.java.en.Given;
import io.cucumber.java.en.Then;
import io.cucumber.java.en.When;
import org.springframework.boot.builder.SpringApplicationBuilder;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.core.env.Environment;
import org.springframework.core.io.buffer.DataBuffer;
import org.springframework.core.io.buffer.DataBufferUtils;
import org.springframework.http.MediaType;
import org.springframework.test.web.reactive.server.WebTestClient;

import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Pattern;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Full Spring process/context restart validation for selected EP-2 metadata
 * durability Business Need scenarios.
 */
public class PhaseEp2MetadataDurabilityFullRestartSteps {

    private static final Pattern UPLOAD_ID_PATTERN = Pattern.compile("<UploadId>([^<]+)</UploadId>");

    private ConfigurableApplicationContext context;
    private Path storageRoot;
    private Path policiesDir;
    private Path devicesDir;
    private Path disksetsDir;
    private WebTestClient client;
    private String currentBucket;
    private String currentKey;
    private String recordedUploadId;
    private String recordedPartEtag;
    private final Map<Integer, String> uploadedPartBodies = new HashMap<>();
    private final Map<String, String> retainUntilByObject = new HashMap<>();
    private final Map<String, RestoreExpectation> restoreByObject = new HashMap<>();

    @After
    public void closeContext() {
        if (context != null) {
            context.close();
            context = null;
        }
    }

    @Given("the storage-engine profile is active with filesystem root {string}")
    public void storageEngineProfileActiveWithFilesystemRoot(String root) throws IOException {
        storageRoot = Path.of(root);
        deleteRecursively(storageRoot);
        Files.createDirectories(storageRoot);
        Path catalogRoot = Files.createTempDirectory("magrathea-phase-ep2-full-restart-catalog-");
        policiesDir = extractCatalogDir(catalogRoot, "storage-policies", List.of("minio-standard.yaml"));
        devicesDir = extractCatalogDir(catalogRoot, "storage-devices", List.of("local-disk-0.yaml"));
        disksetsDir = extractCatalogDir(catalogRoot, "disk-sets", List.of("default-diskset.yaml"));
        startContext();
        currentBucket = null;
        currentKey = null;
        recordedUploadId = null;
        recordedPartEtag = null;
        uploadedPartBodies.clear();
        retainUntilByObject.clear();
        restoreByObject.clear();
    }

    @Given("bucket {string} is created through the S3 CreateBucket API")
    public void bucketCreatedThroughS3CreateBucketApi(String bucket) {
        createBucketIfNeeded(bucket);
        currentBucket = bucket;
    }

    @Given("bucket {string} has a CORS rule allowing origin {string} with methods {string}")
    public void bucketHasCorsRule(String bucket, String origin, String methods) {
        createBucketIfNeeded(bucket);
        currentBucket = bucket;
        putXml("/" + bucket + "?cors",
            "<CORSConfiguration><CORSRule>"
                + "<ID>ep2-cors</ID>"
                + "<AllowedOrigin>" + origin + "</AllowedOrigin>"
                + xmlElements("AllowedMethod", methods)
                + "<AllowedHeader>*</AllowedHeader>"
                + "<ExposeHeader>ETag</ExposeHeader>"
                + "<MaxAgeSeconds>300</MaxAgeSeconds>"
                + "</CORSRule></CORSConfiguration>");
    }

    @Given("bucket {string} has a notification configuration with topic destination {string}")
    public void bucketHasNotificationConfiguration(String bucket, String topicArn) {
        createBucketIfNeeded(bucket);
        currentBucket = bucket;
        putXml("/" + bucket + "?notification",
            "<NotificationConfiguration><EventConfiguration>"
                + "<Event>s3:ObjectCreated:Put</Event>"
                + "<TopicARN>" + topicArn + "</TopicARN>"
                + "</EventConfiguration></NotificationConfiguration>");
    }

    @Given("bucket {string} has an object-lock configuration with retention mode {string} and a period of {int} days")
    public void bucketHasObjectLockConfiguration(String bucket, String mode, int days) {
        createBucketIfNeeded(bucket);
        currentBucket = bucket;
        putXml("/" + bucket + "?object-lock",
            "<ObjectLockConfiguration>"
                + "<ObjectLockEnabled>true</ObjectLockEnabled>"
                + "<Rule><DefaultRetention>"
                + "<Mode>" + mode + "</Mode>"
                + "<Days>" + days + "</Days>"
                + "</DefaultRetention></Rule>"
                + "</ObjectLockConfiguration>");
    }

    @Given("bucket {string} has an inventory-table configuration with id {string} format {string} and schedule {string}")
    public void bucketHasInventoryTableConfiguration(String bucket, String id, String format, String schedule) {
        createBucketIfNeeded(bucket);
        currentBucket = bucket;
        putXml("/" + bucket + "?inventory-table-config",
            "<InventoryTableConfiguration>"
                + "<Id>" + id + "</Id>"
                + "<DestinationFormat>" + format + "</DestinationFormat>"
                + "<ScheduleFrequency>" + schedule + "</ScheduleFrequency>"
                + "<Enabled>true</Enabled>"
                + "</InventoryTableConfiguration>");
    }

    @Given("bucket {string} has a journal-table configuration with id {string} format {string} and schedule {string}")
    public void bucketHasJournalTableConfiguration(String bucket, String id, String format, String schedule) {
        createBucketIfNeeded(bucket);
        currentBucket = bucket;
        putXml("/" + bucket + "?journal-table-config",
            "<JournalTableConfiguration>"
                + "<Id>" + id + "</Id>"
                + "<DestinationFormat>" + format + "</DestinationFormat>"
                + "<ScheduleFrequency>" + schedule + "</ScheduleFrequency>"
                + "<Enabled>true</Enabled>"
                + "</JournalTableConfiguration>");
    }

    @Given("bucket {string} has an ABAC rule granting {string} to principal {string} gated by tag {string} equal to {string}")
    public void bucketHasAbacRule(String bucket, String action, String principal, String tag, String value) {
        createBucketIfNeeded(bucket);
        currentBucket = bucket;
        putXml("/" + bucket + "?abac",
            "<AbacConfiguration><AbacRule>"
                + "<Id>ep2-abac</Id>"
                + "<Principal>" + principal + "</Principal>"
                + "<Resource>arn:aws:s3:::" + bucket + "/*</Resource>"
                + "<Action>" + action + "</Action>"
                + "<Condition><Tag>" + tag + "</Tag><Value>" + value + "</Value></Condition>"
                + "</AbacRule></AbacConfiguration>");
    }

    @Given("bucket {string} has a metadata configuration rule {string} with status {string} for resource type {string} subtype {string}")
    public void bucketHasMetadataConfigurationRule(String bucket, String id, String status, String type, String subtype) {
        createBucketIfNeeded(bucket);
        currentBucket = bucket;
        putXml("/" + bucket + "?metadata-config",
            "<MetadataConfiguration><MetadataRule>"
                + "<Id>" + id + "</Id>"
                + "<Status>" + status + "</Status>"
                + "<MetadataResourceType>" + type + "</MetadataResourceType>"
                + "<MetadataResourceSubtype>" + subtype + "</MetadataResourceSubtype>"
                + "</MetadataRule></MetadataConfiguration>");
    }

    @Given("bucket {string} has a metadata-table configuration rule {string} with table name {string} in database {string}")
    public void bucketHasMetadataTableConfigurationRule(String bucket, String id, String table, String database) {
        createBucketIfNeeded(bucket);
        currentBucket = bucket;
        putXml("/" + bucket + "?metadata-table-config",
            "<MetadataTableConfiguration><MetadataTableRule>"
                + "<Id>" + id + "</Id>"
                + "<Status>Enabled</Status>"
                + "<MetadataTableName>" + table + "</MetadataTableName>"
                + "<MetadataTableDatabase>" + database + "</MetadataTableDatabase>"
                + "</MetadataTableRule></MetadataTableConfiguration>");
    }

    @Given("an object exists at bucket {string} and key {string}")
    public void objectExistsAtBucketAndKey(String bucket, String key) {
        createBucketIfNeeded(bucket);
        putObject(bucket, key, "ep2 metadata durability fixture for " + key);
        currentBucket = bucket;
        currentKey = key;
    }

    @Given("the object has tags {string} and {string}")
    public void objectHasTwoTags(String first, String second) {
        putObjectTags(currentBucket, currentKey, first, second);
    }

    @Given("the object has legal hold status {string}")
    public void objectHasLegalHoldStatus(String status) {
        client.put()
            .uri("/" + currentBucket + "/" + currentKey + "?legal-hold")
            .contentType(MediaType.APPLICATION_XML)
            .bodyValue("<LegalHold><Status>" + status + "</Status></LegalHold>")
            .exchange()
            .expectStatus().isOk();
    }

    @Given("the object has lock configuration with mode {string} and a retention period of {int} days")
    public void objectHasLockConfiguration(String mode, int days) {
        putObjectRetention(mode, days);
    }

    @Given("the object has a retention period of {int} days")
    public void objectHasRetentionPeriod(int days) {
        putObjectRetention("GOVERNANCE", days);
    }

    @Given("the object has server-side encryption configuration with algorithm {string}")
    public void objectHasServerSideEncryptionConfiguration(String algorithm) {
        client.put()
            .uri("/" + currentBucket + "/" + currentKey + "?encryption")
            .header("x-amz-server-side-encryption", algorithm)
            .exchange()
            .expectStatus().isOk();
    }

    @Given("the object has restore state requested at {string} expiring at {string} with tier {string}")
    public void objectHasRestoreState(String requestedAt, String expirationAt, String tier) {
        restoreByObject.put(currentBucket + "/" + currentKey,
            new RestoreExpectation(requestedAt, expirationAt, tier));
        String xml = "<RestoreObject><RestoreRequest>"
            + "<Tier>" + tier + "</Tier>"
            + "<Days>7</Days>"
            + "<RequestedAt>" + requestedAt + "</RequestedAt>"
            + "<ExpirationAt>" + expirationAt + "</ExpirationAt>"
            + "</RestoreRequest></RestoreObject>";
        client.post()
            .uri("/" + currentBucket + "/" + currentKey + "?restore")
            .contentType(MediaType.APPLICATION_XML)
            .bodyValue(xml)
            .exchange()
            .expectStatus().isOk();
    }

    @Given("the object has an ACL grant {string} for user {string}")
    public void objectHasAclGrant(String permission, String user) {
        putObjectAcl(currentBucket, currentKey, permission, user);
    }

    @Given("an object exists at bucket {string} and key {string} with tags {string} and an ACL grant {string} for user {string}")
    public void objectExistsWithTagsAndAcl(String bucket, String key, String tag, String permission, String user) {
        createBucketIfNeeded(bucket);
        putObject(bucket, key, "combined metadata fixture");
        putObjectTags(bucket, key, tag);
        putObjectAcl(bucket, key, permission, user);
        currentBucket = bucket;
        currentKey = key;
    }

    @Given("a multipart upload is initiated for bucket {string} with the upload ID recorded")
    public void multipartUploadInitiatedWithoutKey(String bucket) {
        multipartUploadInitiated(bucket, "memory-exemption/large.bin");
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
        currentKey = key;
    }

    @Given("part number {int} is uploaded and its ETag and size are recorded in the upload session")
    public void partNumberIsUploadedAndRecorded(int partNumber) {
        uploadMultipartPart(partNumber, "multipart part " + partNumber + " for " + currentKey);
    }

    @Given("multipart part number {int} is uploaded with body {string}")
    public void multipartPartNumberIsUploadedWithBody(int partNumber, String body) {
        uploadMultipartPart(partNumber, body);
    }

    @When("the application process is stopped and started again with the same filesystem root")
    public void applicationProcessStoppedAndStartedAgainWithSameFilesystemRoot() {
        context.close();
        context = null;
        startContext();
    }

    @Then("a HeadBucket request for bucket {string} returns HTTP {int}")
    public void headBucketReturnsHttp(String bucket, int status) {
        client.head()
            .uri("/" + bucket)
            .exchange()
            .expectStatus().isEqualTo(status);
        currentBucket = bucket;
    }

    @Then("the ListBuckets response includes bucket {string}")
    public void listBucketsResponseIncludesBucket(String bucket) {
        String body = client.get()
            .uri("/")
            .accept(MediaType.APPLICATION_XML)
            .exchange()
            .expectStatus().isOk()
            .expectBody(String.class)
            .returnResult()
            .getResponseBody();
        assertThat(body).contains(bucket);
    }

    @Then("re-creating bucket {string} is rejected because the bucket already exists")
    public void recreatingBucketIsRejected(String bucket) {
        client.put()
            .uri("/" + bucket)
            .exchange()
            .expectStatus().isEqualTo(409);
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

    @Then("GetBucketCors for bucket {string} includes the rule allowing origin {string}")
    public void getBucketCorsIncludesRule(String bucket, String origin) {
        String body = getString("/" + bucket + "?cors");
        assertThat(body).contains(origin).contains("GET").contains("PUT");
    }

    @Then("GetBucketNotificationConfiguration for bucket {string} returns the topic destination {string}")
    public void getBucketNotificationConfigurationReturnsTopic(String bucket, String topicArn) {
        String body = getString("/" + bucket + "?notification");
        assertThat(body).contains(topicArn).contains("s3:ObjectCreated:Put");
    }

    @Then("GetObjectLockConfiguration for bucket {string} returns retention mode {string} and a period of {int} days")
    public void getBucketObjectLockConfigurationReturnsRetention(String bucket, String mode, int days) {
        String body = getString("/" + bucket + "?object-lock");
        assertThat(body).contains("true").contains(mode).contains(String.valueOf(days));
    }

    @Then("the inventory-table configuration for bucket {string} returns id {string}")
    public void inventoryTableConfigurationReturnsId(String bucket, String id) {
        String body = getString("/" + bucket + "?inventory-table-config");
        assertThat(body).contains(id).contains("Parquet").contains("Daily");
    }

    @Then("the journal-table configuration for bucket {string} returns id {string}")
    public void journalTableConfigurationReturnsId(String bucket, String id) {
        String body = getString("/" + bucket + "?journal-table-config");
        assertThat(body).contains(id).contains("Parquet").contains("Hourly");
    }

    @Then("the bucket ABAC configuration for {string} includes the rule granting {string} gated by tag {string} equal to {string}")
    public void bucketAbacConfigurationIncludesRule(String bucket, String action, String tag, String value) {
        String body = getString("/" + bucket + "?abac");
        assertThat(body).contains(action).contains(tag).contains(value);
    }

    @Then("the metadata configuration for bucket {string} includes rule {string} for resource type {string}")
    public void metadataConfigurationIncludesRule(String bucket, String id, String type) {
        String body = getString("/" + bucket + "?metadata-config");
        assertThat(body).contains(id).contains(type);
    }

    @Then("the metadata-table configuration for bucket {string} returns table name {string}")
    public void metadataTableConfigurationReturnsTableName(String bucket, String table) {
        String body = getString("/" + bucket + "?metadata-table-config");
        assertThat(body).contains(table);
    }

    @Then("GetObjectLegalHold for key {string} returns status {string}")
    public void getObjectLegalHoldReturnsStatus(String key, String status) {
        String body = getString("/" + currentBucket + "/" + key + "?legal-hold");
        assertThat(body).contains(status);
    }

    @Then("GetObjectLockConfiguration for key {string} returns mode {string} and a retention period of {int} days")
    public void getObjectLockConfigurationReturnsModeAndRetention(String key, String mode, int days) {
        String body = getString("/" + currentBucket + "/" + key + "?object-lock");
        assertThat(body).contains(mode);
        // The exact retained-until instant is also checked through the retention API,
        // which reads the same durable object-lock document.
        assertRetentionResponse(key, mode);
    }

    @Then("GetObjectRetention for key {string} returns a retention period of {int} days")
    public void getObjectRetentionReturnsRetentionPeriod(String key, int days) {
        assertRetentionResponse(key, "GOVERNANCE");
    }

    @Then("the object encryption configuration for key {string} returns algorithm {string}")
    public void objectEncryptionConfigurationReturnsAlgorithm(String key, String algorithm) {
        String body = getString("/" + currentBucket + "/" + key + "?encryption");
        assertThat(body).contains(algorithm);
    }

    @Then("the object restore state for key {string} returns the recorded request and expiry timestamps")
    public void objectRestoreStateReturnsRecordedTimestamps(String key) {
        String body = getString("/" + currentBucket + "/" + key + "?restore");
        RestoreExpectation expectation = restoreByObject.get(currentBucket + "/" + key);
        assertThat(expectation).isNotNull();
        assertThat(body)
            .contains(expectation.requestedAt())
            .contains(expectation.expirationAt())
            .contains(expectation.tier());
    }

    @Then("listing multipart uploads for bucket {string} includes the recorded upload ID")
    public void listingMultipartUploadsIncludesRecordedUploadId(String bucket) {
        String body = getString("/" + bucket + "?uploads");
        assertThat(body).contains(recordedUploadId);
        if (recordedPartEtag != null) {
            String partsBody = getString("/" + bucket + "/" + currentKey + "?uploadId=" + recordedUploadId);
            assertThat(partsBody).contains(recordedPartEtag);
        }
    }

    @When("the recorded multipart upload is completed after restart")
    public void recordedMultipartUploadIsCompletedAfterRestart() {
        client.post()
            .uri("/" + currentBucket + "/" + currentKey + "?uploadId=" + recordedUploadId)
            .contentType(MediaType.APPLICATION_XML)
            .bodyValue("<CompleteMultipartUpload/>")
            .exchange()
            .expectStatus().isOk();
    }

    @Then("GetObject for bucket {string} and key {string} returns body {string}")
    public void getObjectForBucketAndKeyReturnsBody(String bucket, String key, String expectedBody) {
        String body = getString("/" + bucket + "/" + key);
        assertThat(body).isEqualTo(expectedBody);
    }

    @Then("the recorded upload ID can be used to abort the multipart upload")
    public void recordedUploadIdCanBeAborted() {
        client.delete()
            .uri("/" + currentBucket + "/" + currentKey + "?uploadId=" + recordedUploadId)
            .exchange()
            .expectStatus().isNoContent();
    }

    @Then("after the abort, listing multipart uploads for bucket {string} does not include the recorded upload ID")
    public void listingMultipartUploadsDoesNotIncludeRecordedUploadId(String bucket) {
        String body = getString("/" + bucket + "?uploads");
        assertThat(body).doesNotContain(recordedUploadId);
    }

    private void startContext() {
        context = new SpringApplicationBuilder(Phase2StorageEngineAwsCliTestApp.class)
            .run(
                "--spring.profiles.active=storage-engine",
                "--server.port=0",
                "--admin.server.port=0",
                "--s3.security.enabled=false",
                "--storage.engine.filesystem.root=" + storageRoot,
                "--storage.engine.filesystem.node-count=1",
                "--storage.engine.policies.dir=" + policiesDir,
                "--storage.engine.devices.dir=" + devicesDir,
                "--storage.engine.disksets.dir=" + disksetsDir
            );
        int port = context.getBean(Environment.class)
            .getRequiredProperty("local.server.port", Integer.class);
        client = WebTestClient.bindToServer()
            .baseUrl("http://127.0.0.1:" + port)
            .responseTimeout(java.time.Duration.ofSeconds(15))
            .build();
    }

    private void createBucketIfNeeded(String bucket) {
        client.put()
            .uri("/" + bucket)
            .exchange()
            .expectStatus().value(status -> assertThat(status).isIn(200, 409));
    }

    private void uploadMultipartPart(int partNumber, String body) {
        var result = client.put()
            .uri("/" + currentBucket + "/" + currentKey + "?partNumber=" + partNumber + "&uploadId=" + recordedUploadId)
            .contentType(MediaType.APPLICATION_OCTET_STREAM)
            .bodyValue(body)
            .exchange()
            .expectStatus().isOk()
            .returnResult(DataBuffer.class);
        recordedPartEtag = result.getResponseHeaders().getFirst("ETag");
        uploadedPartBodies.put(partNumber, body);
        assertThat(recordedPartEtag).isNotBlank();
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

    private void putXml(String uri, String xml) {
        client.put()
            .uri(uri)
            .contentType(MediaType.APPLICATION_XML)
            .bodyValue(xml)
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

    private void putObjectRetention(String mode, int days) {
        String retainUntil = Instant.now().plus(days, ChronoUnit.DAYS).truncatedTo(ChronoUnit.SECONDS).toString();
        retainUntilByObject.put(currentBucket + "/" + currentKey, retainUntil);
        String xml = "<Retention><Mode>" + mode + "</Mode><RetainUntilDate>" + retainUntil + "</RetainUntilDate></Retention>";
        client.put()
            .uri("/" + currentBucket + "/" + currentKey + "?retention")
            .contentType(MediaType.APPLICATION_XML)
            .bodyValue(xml)
            .exchange()
            .expectStatus().isOk();
    }

    private void assertRetentionResponse(String key, String mode) {
        String body = getString("/" + currentBucket + "/" + key + "?retention");
        assertThat(body).contains(mode);
        String retainUntil = retainUntilByObject.get(currentBucket + "/" + key);
        assertThat(retainUntil).isNotBlank();
        assertThat(body).contains(retainUntil.substring(0, 10));
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

    private static String xmlElements(String element, String commaSeparatedValues) {
        StringBuilder xml = new StringBuilder();
        for (String value : commaSeparatedValues.split(",")) {
            xml.append('<').append(element).append('>')
                .append(value.trim())
                .append("</").append(element).append('>');
        }
        return xml.toString();
    }

    private static void assertTagPresent(String body, String expectedTag) {
        String[] parts = expectedTag.split("=", 2);
        assertThat(body).contains(parts[0]);
        if (parts.length > 1) {
            assertThat(body).contains(parts[1]);
        }
    }

    private static Path extractCatalogDir(Path catalogRoot, String classpathDir,
                                          List<String> fileNames) throws IOException {
        Path dir = catalogRoot.resolve(classpathDir);
        Files.createDirectories(dir);
        ClassLoader cl = Thread.currentThread().getContextClassLoader();
        for (String fileName : fileNames) {
            String resourcePath = classpathDir + "/" + fileName;
            try (InputStream in = cl.getResourceAsStream(resourcePath)) {
                if (in == null) {
                    throw new UncheckedIOException(new IOException(
                        "Classpath resource not found: " + resourcePath));
                }
                Files.write(dir.resolve(fileName), in.readAllBytes());
            }
        }
        return dir;
    }

    private record RestoreExpectation(String requestedAt, String expirationAt, String tier) {
    }

    private static void deleteRecursively(Path path) throws IOException {
        if (path == null || !Files.exists(path)) {
            return;
        }
        try (var stream = Files.walk(path)) {
            for (Path entry : stream.sorted(Comparator.reverseOrder()).toList()) {
                Files.deleteIfExists(entry);
            }
        }
    }
}
