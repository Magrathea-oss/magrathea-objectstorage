package com.example.magrathea.s3api.phase5awscli;

import com.example.magrathea.objectstorage.repository.storageengine.adapter.StorageEngineReactiveBucketRepository;
import com.example.magrathea.objectstorage.repository.storageengine.adapter.StorageEngineReactiveMultipartUploadRepository;
import com.example.magrathea.objectstorage.repository.storageengine.adapter.StorageEngineReactiveS3ObjectRepository;
import com.example.magrathea.s3api.adapter.web.S3BucketConfigHandler;
import io.cucumber.java.Before;
import io.cucumber.java.en.Given;
import io.cucumber.java.en.Then;
import io.cucumber.java.en.When;
import org.junit.jupiter.api.Assumptions;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.web.server.LocalServerPort;

import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;
import java.util.regex.Pattern;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Step definitions for Phase 5 S3 semantic compatibility AWS CLI runner.
 *
 * <p>Covers the {@code @awscli-required} scenarios from
 * {@code phase-5-s3-semantic-compatibility.feature}:
 * <ul>
 *   <li>REQ-S3-002-A/B: multipart upload ETag validation via {@code aws s3api}</li>
 *   <li>REQ-S3-003-A through D: byte-range retrieval via Java {@link HttpClient}</li>
 *   <li>REQ-S3-004-A through F: conditional requests via Java {@link HttpClient}</li>
 * </ul>
 *
 * <p>AWS CLI availability is checked at class-load time. All scenarios are skipped
 * (via JUnit {@link Assumptions}) if the {@code aws} binary is not on {@code PATH}.
 *
 * <p>Bucket creation and object uploads use {@code aws s3api}. Range requests and
 * conditional requests use Java's built-in {@link HttpClient} so that precise HTTP
 * status codes (206, 304, 412, 416) and response headers (Content-Range, ETag) are
 * inspected without relying on AWS CLI error-message parsing.
 */
public class Phase5S3SemanticCompatibilityAwsCliSteps {

    private static final Pattern QUOTED_MD5_ETAG = Pattern.compile("\\\"[0-9a-f]{32}\\\"");
    private static final long AWS_CLI_TIMEOUT_SECONDS = 30;
    private static final AwsCliPreflight AWS_CLI_PREFLIGHT = checkAwsCliPreflight();

    // ── Spring-injected fields ─────────────────────────────────────────────────

    @LocalServerPort
    private int port;

    @Autowired
    private ObjectProvider<StorageEngineReactiveBucketRepository> bucketRepository;

    @Autowired
    private ObjectProvider<StorageEngineReactiveS3ObjectRepository> objectRepository;

    @Autowired
    private ObjectProvider<StorageEngineReactiveMultipartUploadRepository> multipartRepository;

    @Autowired
    private ObjectProvider<S3BucketConfigHandler> bucketConfigHandler;

    // ── Per-scenario state ─────────────────────────────────────────────────────

    /** Current bucket name set by {@code bucket "X" exists}. */
    private String bucket;

    /** Current object key set by {@code an object key "X"} or setup steps. */
    private String objectKey;

    /** Multipart upload ID parsed from {@code create-multipart-upload} response. */
    private String uploadId;

    /** HTTP status code of the last significant operation. */
    private int lastStatusCode = -1;

    /** Response body bytes of the last significant operation. */
    private byte[] lastBody = new byte[0];

    /** Content-Range header value from the last range GET, or {@code null}. */
    private String lastContentRange;

    /** ETag from the last {@code upload-part} response (quoted MD5 hex string). */
    private String lastPartEtag;

    /** ETag from the last {@code complete-multipart-upload} response. */
    private String lastCompleteEtag;

    /** Part ETags keyed by part number, accumulated during multipart scenario. */
    private final Map<Integer, String> partEtags = new LinkedHashMap<>();

    /** Named ETag values saved via {@code the PutObject ETag for "X" is saved as "Y"}. */
    private final Map<String, String> savedEtags = new HashMap<>();

    /** ETag per object key, populated during object upload setup steps. */
    private final Map<String, String> etagsByObjectKey = new HashMap<>();

    /** Reusable Java HTTP client — follows no redirects so 304/412/416 are returned as-is. */
    private final HttpClient httpClient = HttpClient.newBuilder()
        .followRedirects(HttpClient.Redirect.NEVER)
        .build();

    // ── Lifecycle ──────────────────────────────────────────────────────────────

    @Before
    public void resetStateAndCheckAwsCli() {
        bucket = null;
        objectKey = null;
        uploadId = null;
        lastStatusCode = -1;
        lastBody = new byte[0];
        lastContentRange = null;
        lastPartEtag = null;
        lastCompleteEtag = null;
        partEtags.clear();
        savedEtags.clear();
        etagsByObjectKey.clear();
        bucketRepository.ifAvailable(StorageEngineReactiveBucketRepository::reset);
        objectRepository.ifAvailable(StorageEngineReactiveS3ObjectRepository::reset);
        multipartRepository.ifAvailable(StorageEngineReactiveMultipartUploadRepository::reset);
        bucketConfigHandler.ifAvailable(S3BucketConfigHandler::resetInMemoryConfigurations);
        Assumptions.assumeTrue(AWS_CLI_PREFLIGHT.available(), AWS_CLI_PREFLIGHT.reason());
    }

    // ── Setup steps ───────────────────────────────────────────────────────────

    /**
     * Creates the named S3 bucket via {@code aws s3api create-bucket}.
     * Stores the bucket name in scenario state for subsequent steps.
     */
    @Given("bucket {string} exists")
    public void bucketExists(String bucketName) throws Exception {
        this.bucket = bucketName;
        AwsResult result = runAws("create-bucket", "--bucket", bucketName);
        assertEquals(0, result.exitCode(),
            () -> "create-bucket should succeed: " + result.combined());
    }

    /**
     * Sets the current object key in scenario state for multipart upload steps.
     */
    @Given("an object key {string}")
    public void anObjectKey(String key) {
        this.objectKey = key;
    }

    /**
     * Uploads an object with a literal body string via {@code aws s3api put-object}.
     * Parses and stores the ETag from the response for later use by
     * {@code the PutObject ETag for "X" is saved as "Y"}.
     */
    @Given("object {string} is stored in bucket {string} with body {string}")
    public void objectIsStoredInBucketWithBody(String key, String bucketName, String body) throws Exception {
        this.bucket = bucketName;
        this.objectKey = key;
        Path tempFile = Files.createTempFile("phase5awscli-put-", ".bin");
        try {
            Files.writeString(tempFile, body, StandardCharsets.UTF_8);
            AwsResult result = runAws("put-object",
                "--bucket", bucketName,
                "--key", key,
                "--body", tempFile.toString());
            assertEquals(0, result.exitCode(),
                () -> "put-object should succeed: " + result.combined());
            lastStatusCode = 200;
            String etag = extractJsonStringValue(result.stdout(), "ETag");
            etagsByObjectKey.put(key, etag);
        } finally {
            Files.deleteIfExists(tempFile);
        }
    }

    /**
     * Saves the ETag captured during the preceding {@code put-object} step under
     * the given alias name for use in conditional request headers.
     */
    @Given("the PutObject ETag for {string} is saved as {string}")
    public void putObjectEtagIsSavedAs(String key, String name) {
        String etag = etagsByObjectKey.get(key);
        assertNotNull(etag,
            "PutObject ETag should have been captured for key: " + key);
        savedEtags.put(name, etag);
    }

    // ── Multipart upload steps ────────────────────────────────────────────────

    /**
     * Initiates a multipart upload for the current bucket and object key via
     * {@code aws s3api create-multipart-upload}. Parses the {@code UploadId}.
     */
    @Given("a multipart upload is initiated for the bucket")
    public void aMultipartUploadIsInitiatedForTheBucket() throws Exception {
        assertNotNull(bucket, "bucket should be set before initiating multipart upload");
        assertNotNull(objectKey, "objectKey should be set before initiating multipart upload");
        AwsResult result = runAws("create-multipart-upload",
            "--bucket", bucket,
            "--key", objectKey);
        assertEquals(0, result.exitCode(),
            () -> "create-multipart-upload should succeed: " + result.combined());
        lastStatusCode = 200;
        uploadId = extractJsonStringValue(result.stdout(), "UploadId");
        assertFalse(uploadId.isBlank(),
            "create-multipart-upload response should contain UploadId");
    }

    /**
     * Uploads a numbered part with literal content via {@code aws s3api upload-part}.
     * Captures the part ETag for use in {@code complete-multipart-upload}.
     * Accepts both the {@code When} form (with {@code a}) and the {@code Given} form.
     */
    @Given("part number {int} is uploaded with content {string}")
    @When("a part number {int} is uploaded with content {string}")
    public void partNumberIsUploadedWithContent(int partNumber, String content) throws Exception {
        assertNotNull(uploadId, "uploadId should be available before uploading parts");
        Path tempFile = Files.createTempFile("phase5awscli-part-", ".bin");
        try {
            Files.writeString(tempFile, content, StandardCharsets.UTF_8);
            AwsResult result = runAws("upload-part",
                "--bucket", bucket,
                "--key", objectKey,
                "--upload-id", uploadId,
                "--part-number", String.valueOf(partNumber),
                "--body", tempFile.toString());
            assertEquals(0, result.exitCode(),
                () -> "upload-part " + partNumber + " should succeed: " + result.combined());
            lastStatusCode = 200;
            String etag = extractJsonStringValue(result.stdout(), "ETag");
            lastPartEtag = etag;
            partEtags.put(partNumber, etag);
        } finally {
            Files.deleteIfExists(tempFile);
        }
    }

    /**
     * Completes the multipart upload in part-number order via
     * {@code aws s3api complete-multipart-upload}. Captures the multipart ETag.
     */
    @When("the multipart upload is completed with all uploaded parts in order")
    public void theMultipartUploadIsCompletedWithAllUploadedPartsInOrder() throws Exception {
        assertNotNull(uploadId, "uploadId should be available before completing");
        assertFalse(partEtags.isEmpty(), "at least one uploaded part should exist");

        // Build the parts JSON required by complete-multipart-upload
        StringBuilder partsJson = new StringBuilder("{\"Parts\":[");
        boolean first = true;
        List<Integer> sortedPartNumbers = new ArrayList<>(partEtags.keySet());
        sortedPartNumbers.sort(Integer::compareTo);
        for (int partNum : sortedPartNumbers) {
            if (!first) partsJson.append(",");
            first = false;
            // ETag stored as "abc..." (with actual quote chars); escape for JSON embedding
            String etag = partEtags.get(partNum);
            String escapedEtag = etag.replace("\"", "\\\"");
            partsJson.append("{\"PartNumber\":").append(partNum)
                .append(",\"ETag\":\"").append(escapedEtag).append("\"}");
        }
        partsJson.append("]}");

        Path jsonFile = Files.createTempFile("phase5awscli-complete-", ".json");
        try {
            Files.writeString(jsonFile, partsJson.toString(), StandardCharsets.UTF_8);
            AwsResult result = runAws("complete-multipart-upload",
                "--bucket", bucket,
                "--key", objectKey,
                "--upload-id", uploadId,
                "--multipart-upload", "file://" + jsonFile.toAbsolutePath());
            assertEquals(0, result.exitCode(),
                () -> "complete-multipart-upload should succeed: " + result.combined());
            lastStatusCode = 200;
            lastCompleteEtag = extractJsonStringValue(result.stdout(), "ETag");
        } finally {
            Files.deleteIfExists(jsonFile);
        }
    }

    // ── Range GET steps ───────────────────────────────────────────────────────

    /**
     * Issues a GET request with a {@code Range} header using Java's
     * {@link HttpClient} to capture the precise HTTP status code (206 or 416),
     * response body bytes, and {@code Content-Range} response header.
     */
    @When("the S3 GetObject API fetches object {string} from bucket {string} with Range header {string}")
    public void getObjectWithRangeHeader(String key, String bucketName, String range) {
        doHttpGet(bucketName, key, Map.of("Range", range));
    }

    /**
     * Issues a plain GET request (no Range header) using Java's {@link HttpClient}.
     */
    @When("the S3 GetObject API fetches object {string} from bucket {string} without a Range header")
    public void getObjectWithoutRangeHeader(String key, String bucketName) {
        doHttpGet(bucketName, key, Map.of());
    }

    // ── Conditional GET steps ─────────────────────────────────────────────────

    /**
     * Issues a GET with a conditional header whose value is the named saved ETag
     * (e.g., {@code If-Match}, {@code If-None-Match}) using Java's {@link HttpClient}.
     */
    @When("the S3 GetObject API fetches object {string} from bucket {string} with header {string} set to the saved ETag {string}")
    public void getObjectWithSavedEtagHeader(String key, String bucketName, String header, String savedName) {
        String value = savedEtags.get(savedName);
        assertNotNull(value, "saved ETag should exist for name: " + savedName);
        doHttpGet(bucketName, key, Map.of(header, value));
    }

    /**
     * Issues a GET with a conditional header set to a literal value using Java's
     * {@link HttpClient}.
     */
    @When("the S3 GetObject API fetches object {string} from bucket {string} with header {string} set to value {string}")
    public void getObjectWithLiteralHeader(String key, String bucketName, String header, String value) {
        doHttpGet(bucketName, key, Map.of(header, value));
    }

    /**
     * Issues a GET with {@code If-Modified-Since} or {@code If-Unmodified-Since} set to
     * a timestamp one day in the future (i.e., after the object's last modification time).
     */
    @When("the S3 GetObject API fetches object {string} from bucket {string} with header {string} set to a timestamp after the object's last modification time")
    public void getObjectWithTimestampAfterLastModification(String key, String bucketName, String header) {
        String futureDate = DateTimeFormatter.RFC_1123_DATE_TIME.format(ZonedDateTime.now().plusDays(1));
        doHttpGet(bucketName, key, Map.of(header, futureDate));
    }

    /**
     * Issues a GET with {@code If-Modified-Since} or {@code If-Unmodified-Since} set to
     * a timestamp one day in the past (i.e., before the object's last modification time).
     */
    @When("the S3 GetObject API fetches object {string} from bucket {string} with header {string} set to a timestamp before the object's last modification time")
    public void getObjectWithTimestampBeforeLastModification(String key, String bucketName, String header) {
        String pastDate = DateTimeFormatter.RFC_1123_DATE_TIME.format(ZonedDateTime.now().minusDays(1));
        doHttpGet(bucketName, key, Map.of(header, pastDate));
    }

    // ── Assertion steps ───────────────────────────────────────────────────────

    @Then("the response status is {int}")
    public void theResponseStatusIs(int expectedStatus) {
        assertEquals(expectedStatus, lastStatusCode,
            "HTTP response status mismatch; last body: " + new String(lastBody, StandardCharsets.UTF_8));
    }

    @Then("the response body is {string}")
    public void theResponseBodyIs(String expected) {
        assertEquals(expected, new String(lastBody, StandardCharsets.UTF_8),
            "response body mismatch");
    }

    @Then("the Content-Range response header is {string}")
    public void contentRangeResponseHeaderIs(String expected) {
        assertEquals(expected, lastContentRange,
            "Content-Range response header mismatch");
    }

    @Then("the upload part response ETag is a quoted 32-character lowercase hex string")
    public void uploadPartResponseEtagIsQuotedMd5Hex() {
        assertNotNull(lastPartEtag, "upload-part ETag should have been captured");
        assertTrue(QUOTED_MD5_ETAG.matcher(lastPartEtag).matches(),
            "upload-part ETag should be quoted lowercase MD5 hex but was: " + lastPartEtag);
        assertNotEquals("\"placeholder-etag\"", lastPartEtag,
            "upload-part ETag must not be a placeholder");
    }

    @Then("the complete multipart upload ETag ends with {string} indicating the two-part composition")
    public void completeMultipartUploadEtagEndsWith(String suffix) {
        assertNotNull(lastCompleteEtag,
            "complete-multipart-upload ETag should have been captured");
        assertTrue(lastCompleteEtag.endsWith(suffix + "\""),
            "CompleteMultipartUpload ETag should end with " + suffix + "\": " + lastCompleteEtag);
        assertTrue(lastCompleteEtag.matches("\\\"[0-9a-f]{32}-2\\\""),
            "multipart ETag should match pattern \"<md5>-2\" but was: " + lastCompleteEtag);
    }

    // ── Private helpers ───────────────────────────────────────────────────────

    /**
     * Executes an HTTP GET request and captures the response status, body, and
     * {@code Content-Range} header. Uses Java's built-in {@link HttpClient} so
     * that precise status codes (206, 304, 412, 416) are available without
     * parsing AWS CLI error messages.
     */
    private void doHttpGet(String bucketName, String key, Map<String, String> headers) {
        try {
            HttpRequest.Builder builder = HttpRequest.newBuilder()
                .uri(URI.create("http://localhost:" + port + "/" + bucketName + "/" + key))
                .GET();
            headers.forEach(builder::header);
            HttpResponse<byte[]> response = httpClient.send(
                builder.build(),
                HttpResponse.BodyHandlers.ofByteArray());
            lastStatusCode = response.statusCode();
            lastBody = response.body() != null ? response.body() : new byte[0];
            lastContentRange = response.headers().firstValue("Content-Range").orElse(null);
        } catch (IOException e) {
            throw new UncheckedIOException("HTTP GET failed", e);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new RuntimeException("HTTP GET was interrupted", e);
        }
    }

    /**
     * Runs an {@code aws s3api} command against the test server and returns the
     * exit code, stdout, and stderr as an {@link AwsResult}.
     */
    private AwsResult runAws(String... args) throws Exception {
        List<String> command = new ArrayList<>();
        command.add("aws");
        command.add("--endpoint-url");
        command.add("http://localhost:" + port);
        command.add("s3api");
        command.addAll(Arrays.asList(args));
        ProcessBuilder pb = new ProcessBuilder(command);
        Map<String, String> env = pb.environment();
        env.putIfAbsent("AWS_ACCESS_KEY_ID", "test");
        env.putIfAbsent("AWS_SECRET_ACCESS_KEY", "test");
        env.putIfAbsent("AWS_DEFAULT_REGION", "us-east-1");
        env.putIfAbsent("AWS_EC2_METADATA_DISABLED", "true");
        Process process = pb.start();
        CompletableFuture<String> stdoutFuture =
            CompletableFuture.supplyAsync(() -> readStream(process.getInputStream()));
        CompletableFuture<String> stderrFuture =
            CompletableFuture.supplyAsync(() -> readStream(process.getErrorStream()));
        boolean finished = process.waitFor(AWS_CLI_TIMEOUT_SECONDS, TimeUnit.SECONDS);
        if (!finished) {
            process.destroyForcibly();
            fail("AWS CLI timed out after " + AWS_CLI_TIMEOUT_SECONDS + "s: " + String.join(" ", command));
        }
        return new AwsResult(process.exitValue(), stdoutFuture.join(), stderrFuture.join());
    }

    /**
     * Reads all bytes from the process stream and returns them as a UTF-8 string.
     */
    private static String readStream(InputStream stream) {
        try (stream) {
            return new String(stream.readAllBytes(), StandardCharsets.UTF_8);
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    /**
     * Extracts a JSON string value by key from a simple JSON object.
     * Handles JSON-escaped characters inside the value (e.g., {@code \"} in ETags).
     *
     * <p>Example: {@code {"ETag": "\"abc123\""}} → {@code extractJsonStringValue(json, "ETag")} → {@code "abc123"}
     * (with actual double-quote characters).
     */
    private static String extractJsonStringValue(String json, String key) {
        // Try both "key": "value" and "key":"value" formats
        for (String marker : List.of("\"" + key + "\": \"", "\"" + key + "\":\"")) {
            int start = json.indexOf(marker);
            if (start >= 0) {
                start += marker.length();
                StringBuilder sb = new StringBuilder();
                int i = start;
                while (i < json.length()) {
                    char c = json.charAt(i);
                    if (c == '\\' && i + 1 < json.length()) {
                        sb.append(json.charAt(i + 1));
                        i += 2;
                    } else if (c == '"') {
                        break;
                    } else {
                        sb.append(c);
                        i++;
                    }
                }
                return sb.toString();
            }
        }
        return "";
    }

    /**
     * Checks whether the {@code aws} CLI binary is available on {@code PATH} and caches
     * the result as a static field so the check runs exactly once per JVM.
     */
    private static AwsCliPreflight checkAwsCliPreflight() {
        try {
            Process process = new ProcessBuilder("aws", "--version").start();
            boolean finished = process.waitFor(10, TimeUnit.SECONDS);
            if (!finished) {
                process.destroyForcibly();
                return new AwsCliPreflight(false, "AWS CLI preflight failed: `aws --version` timed out");
            }
            String out = new String(process.getInputStream().readAllBytes(), StandardCharsets.UTF_8);
            String err = new String(process.getErrorStream().readAllBytes(), StandardCharsets.UTF_8);
            if (process.exitValue() != 0) {
                return new AwsCliPreflight(false,
                    "AWS CLI preflight failed: exit " + process.exitValue() + " " + out + err);
            }
            return new AwsCliPreflight(true, (out + err).trim());
        } catch (IOException e) {
            return new AwsCliPreflight(false,
                "AWS CLI binary not on PATH: " + e.getMessage());
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            return new AwsCliPreflight(false,
                "AWS CLI preflight interrupted");
        }
    }

    // ── Value types ───────────────────────────────────────────────────────────

    private record AwsCliPreflight(boolean available, String reason) {}

    private record AwsResult(int exitCode, String stdout, String stderr) {
        String combined() {
            return (Objects.toString(stdout, "") + Objects.toString(stderr, "")).trim();
        }
    }
}
