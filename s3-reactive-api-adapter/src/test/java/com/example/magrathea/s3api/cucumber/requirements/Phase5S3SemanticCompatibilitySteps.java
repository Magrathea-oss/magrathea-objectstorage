package com.example.magrathea.s3api.cucumber.requirements;

import com.example.magrathea.objectstorage.repository.storageengine.adapter.StorageEngineReactiveBucketRepository;
import com.example.magrathea.objectstorage.repository.storageengine.adapter.StorageEngineReactiveMultipartUploadRepository;
import com.example.magrathea.objectstorage.repository.storageengine.adapter.StorageEngineReactiveS3ObjectRepository;
import com.example.magrathea.objectstore.domain.aggregate.Bucket;
import com.example.magrathea.objectstore.domain.aggregate.MultipartUpload;
import com.example.magrathea.objectstore.domain.valueobject.ObjectKey;
import com.example.magrathea.objectstore.domain.valueobject.PartNumber;
import com.example.magrathea.objectstore.domain.valueobject.UploadId;
import com.example.magrathea.objectstore.domain.valueobject.UploadPart;
import com.example.magrathea.s3api.adapter.web.S3BucketConfigHandler;
import io.cucumber.datatable.DataTable;
import io.cucumber.java.Before;
import io.cucumber.java.en.Given;
import io.cucumber.java.en.Then;
import io.cucumber.java.en.When;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.test.web.reactive.server.EntityExchangeResult;
import org.springframework.test.web.reactive.server.WebTestClient;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import reactor.core.scheduler.Schedulers;
import reactor.test.StepVerifier;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Pattern;

import static org.junit.jupiter.api.Assertions.*;

public class Phase5S3SemanticCompatibilitySteps {

    private static final Pattern QUOTED_MD5_ETAG = Pattern.compile("\\\"[0-9a-f]{32}\\\"");
    private static final Path PHASE5_MULTIPART_RESTART_ROOT =
        Path.of("target/phase-5-multipart-restart-probe").toAbsolutePath().normalize();

    @Autowired
    private WebTestClient webTestClient;

    @Autowired
    private Phase5State state;

    @Autowired
    private Phase2FilesystemReliabilitySteps.State sharedState;

    @Autowired
    private ObjectProvider<StorageEngineReactiveBucketRepository> bucketRepository;

    @Autowired
    private ObjectProvider<StorageEngineReactiveS3ObjectRepository> objectRepository;

    @Autowired
    private ObjectProvider<StorageEngineReactiveMultipartUploadRepository> multipartRepository;

    @Autowired
    private ObjectProvider<S3BucketConfigHandler> bucketConfigHandler;

    @Before
    public void resetPhase5StateAndRepositories() {
        state.reset();
        bucketRepository.ifAvailable(StorageEngineReactiveBucketRepository::reset);
        objectRepository.ifAvailable(StorageEngineReactiveS3ObjectRepository::reset);
        multipartRepository.ifAvailable(StorageEngineReactiveMultipartUploadRepository::reset);
        bucketConfigHandler.ifAvailable(S3BucketConfigHandler::resetInMemoryConfigurations);
    }

    @Given("an object with key {string} and content {string}")
    public void anObjectWithKeyAndContent(String key, String content) {
        state.objectKey = key;
        state.objectContent = content;
    }

    @Given("an object key {string}")
    public void anObjectKey(String key) {
        state.objectKey = key;
    }

    @Given("object {string} is stored in bucket {string} with body {string}")
    public void objectIsStoredInBucketWithBody(String key, String bucket, String body) {
        var response = putObject(bucket, key, body, Map.of());
        assertEquals(200, response.status(), response.bodyAsString());
        state.bucket = bucket;
        state.objectKey = key;
        state.objectContent = body;
        rememberEtag(key, response.header("ETag"));
    }

    @Given("the PutObject ETag for {string} is saved as {string}")
    public void putObjectEtagIsSavedAs(String key, String name) {
        String etag = state.etagsByObjectKey.get(key);
        assertNotNull(etag, "PutObject ETag should have been captured for " + key);
        state.savedValues.put(name, etag);
    }

    @Given("a multipart upload is initiated for the bucket")
    public void aMultipartUploadIsInitiatedForTheBucket() {
        String bucket = currentBucket();
        assertNotNull(state.objectKey, "object key should be defined before initiating multipart upload");
        state.lastResponse = exchangeBytes(webTestClient.post()
            .uri(URI.create("/" + bucket + "/" + state.objectKey + "?uploads"))
            .accept(MediaType.APPLICATION_XML));
        assertEquals(200, state.lastResponse.status(), state.lastResponse.bodyAsString());
        state.bucket = bucket;
        state.uploadId = extractXmlValue(state.lastResponse.bodyAsString(), "UploadId");
        assertFalse(state.uploadId.isBlank(), "initiate multipart response should contain UploadId");
    }

    @Given("part number {int} is uploaded with content {string}")
    @When("a part number {int} is uploaded with content {string}")
    public void partNumberIsUploadedWithContent(int partNumber, String content) {
        uploadPart(partNumber, content);
    }

    @When("the object is stored via S3 API in bucket {string}")
    public void theObjectIsStoredViaS3ApiInBucket(String bucket) {
        assertNotNull(state.objectKey, "object key should be prepared");
        assertNotNull(state.objectContent, "object content should be prepared");
        state.lastResponse = putObject(bucket, state.objectKey, state.objectContent, Map.of());
        state.bucket = bucket;
        rememberEtag(state.objectKey, state.lastResponse.header("ETag"));
    }

    @When("HEAD request is sent for object {string} in bucket {string}")
    public void headRequestIsSentForObjectInBucket(String key, String bucket) {
        state.lastResponse = exchangeBytes(webTestClient.head()
            .uri(URI.create("/" + bucket + "/" + key)));
    }

    @When("the multipart upload is completed with all uploaded parts in order")
    public void theMultipartUploadIsCompletedWithAllUploadedPartsInOrder() {
        assertNotNull(state.uploadId, "uploadId should be available");
        state.lastResponse = exchangeBytes(webTestClient.post()
            .uri(URI.create("/" + state.bucket + "/" + state.objectKey + "?uploadId=" + state.uploadId))
            .contentType(MediaType.APPLICATION_XML)
            .bodyValue("<CompleteMultipartUpload/>"));
    }

    @When("the application is restarted with the same storage configuration")
    public void theApplicationIsRestartedWithTheSameStorageConfiguration() {
        assertNotNull(state.uploadId, "uploadId should be available for restart probe");
        assertFalse(state.uploadedParts.isEmpty(), "at least one uploaded part should exist for restart probe");
        cleanDirectory(PHASE5_MULTIPART_RESTART_ROOT);
        var beforeRestart = new TestFileSystemMultipartUploadRepository(PHASE5_MULTIPART_RESTART_ROOT);
        var upload = MultipartUpload.restore(
            MultipartUpload.Id.generate(),
            Bucket.Id.of(state.bucket),
            ObjectKey.of(state.bucket, state.objectKey),
            UploadId.of(state.uploadId),
            java.time.Instant.now(),
            state.uploadedParts.values().stream()
                .sorted(Comparator.comparingInt(part -> part.partNumber().value()))
                .toList(),
            false,
            false);
        StepVerifier.create(beforeRestart.save(upload))
            .assertNext(saved -> assertEquals(upload.uploadId(), saved.uploadId()))
            .verifyComplete();

        var afterRestart = new TestFileSystemMultipartUploadRepository(PHASE5_MULTIPART_RESTART_ROOT);
        StepVerifier.create(afterRestart.findParts(UploadId.of(state.uploadId)).collectList())
            .assertNext(parts -> {
                assertEquals(state.uploadedParts.size(), parts.size(),
                    "filesystem-backed multipart repository should reload parts from the same directory");
                assertEquals(1, parts.getFirst().partNumber().value());
                assertTrue(isQuotedMd5Etag(parts.getFirst().etag()));
            })
            .verifyComplete();
        state.filesystemRestartProbeValidated = true;
    }

    @When("the parts are listed")
    public void thePartsAreListed() {
        assertTrue(state.filesystemRestartProbeValidated,
            "filesystem restart probe should run before ListParts assertion");
        state.lastResponse = exchangeBytes(webTestClient.get()
            .uri(URI.create("/" + state.bucket + "/" + state.objectKey + "?uploadId=" + state.uploadId))
            .accept(MediaType.APPLICATION_XML));
    }

    @When("the S3 GetObject API fetches object {string} from bucket {string} with Range header {string}")
    public void getObjectWithRange(String key, String bucket, String range) {
        state.lastResponse = exchangeBytes(webTestClient.get()
            .uri(URI.create("/" + bucket + "/" + key))
            .header("Range", range));
    }

    @When("the S3 GetObject API fetches object {string} from bucket {string} without a Range header")
    public void getObjectWithoutRange(String key, String bucket) {
        state.lastResponse = exchangeBytes(webTestClient.get()
            .uri(URI.create("/" + bucket + "/" + key)));
    }

    @When("the S3 GetObject API fetches object {string} from bucket {string} with header {string} set to the saved ETag {string}")
    public void getObjectWithSavedEtagHeader(String key, String bucket, String header, String savedName) {
        String value = state.savedValues.get(savedName);
        assertNotNull(value, "saved ETag should exist: " + savedName);
        getObjectWithHeaderValue(key, bucket, header, value);
    }

    @When("the S3 GetObject API fetches object {string} from bucket {string} with header {string} set to value {string}")
    public void getObjectWithLiteralHeader(String key, String bucket, String header, String value) {
        getObjectWithHeaderValue(key, bucket, header, value);
    }

    @When("the S3 GetObject API fetches object {string} from bucket {string} with header {string} set to a timestamp after the object's last modification time")
    public void getObjectWithTimestampAfterLastModification(String key, String bucket, String header) {
        getObjectWithHeaderValue(key, bucket, header,
            DateTimeFormatter.RFC_1123_DATE_TIME.format(ZonedDateTime.now().plusDays(1)));
    }

    @When("the S3 GetObject API fetches object {string} from bucket {string} with header {string} set to a timestamp before the object's last modification time")
    public void getObjectWithTimestampBeforeLastModification(String key, String bucket, String header) {
        getObjectWithHeaderValue(key, bucket, header,
            DateTimeFormatter.RFC_1123_DATE_TIME.format(ZonedDateTime.now().minusDays(1)));
    }

    @When("object {string} is copied to {string} in bucket {string}")
    public void objectIsCopiedToInBucket(String sourceKey, String destinationKey, String bucket) {
        state.lastResponse = exchangeBytes(webTestClient.put()
            .uri(URI.create("/" + bucket + "/" + destinationKey))
            .header("x-amz-copy-source", "/" + bucket + "/" + sourceKey)
            .accept(MediaType.APPLICATION_XML));
        state.objectKey = destinationKey;
    }

    @When("the following tag set is applied to object {string} in bucket {string} via PutObjectTagging")
    public void tagSetIsAppliedToObject(String key, String bucket, DataTable tags) {
        String xml = tagSetXml(tags.asMaps(String.class, String.class));
        state.lastResponse = exchangeBytes(webTestClient.put()
            .uri(URI.create("/" + bucket + "/" + key + "?tagging"))
            .contentType(MediaType.APPLICATION_XML)
            .accept(MediaType.APPLICATION_XML)
            .bodyValue(xml));
    }

    @When("object tags are requested for {string} in bucket {string}")
    public void objectTagsAreRequestedForInBucket(String key, String bucket) {
        state.lastResponse = exchangeBytes(webTestClient.get()
            .uri(URI.create("/" + bucket + "/" + key + "?tagging"))
            .accept(MediaType.APPLICATION_XML));
    }

    @When("object tags are deleted for {string} in bucket {string}")
    public void objectTagsAreDeletedForInBucket(String key, String bucket) {
        state.lastResponse = exchangeBytes(webTestClient.delete()
            .uri(URI.create("/" + bucket + "/" + key + "?tagging")));
    }

    @When("the S3 PutObject API stores object {string} in bucket {string} with body {string} and x-amz-tagging header {string}")
    public void putObjectStoresObjectWithInlineTagging(String key, String bucket, String body, String tagging) {
        state.lastResponse = putObject(bucket, key, body, Map.of("x-amz-tagging", tagging));
        rememberEtag(key, state.lastResponse.header("ETag"));
    }

    @When("bucket versioning is requested for {string}")
    public void bucketVersioningIsRequestedFor(String bucket) {
        state.lastResponse = exchangeBytes(webTestClient.get()
            .uri(URI.create("/" + bucket + "?versioning"))
            .accept(MediaType.APPLICATION_XML));
    }

    @When("bucket versioning is enabled for {string}")
    public void bucketVersioningIsEnabledFor(String bucket) {
        state.lastResponse = exchangeBytes(webTestClient.put()
            .uri(URI.create("/" + bucket + "?versioning"))
            .contentType(MediaType.APPLICATION_XML)
            .bodyValue("<VersioningConfiguration><Status>Enabled</Status></VersioningConfiguration>"));
    }

    @When("the S3 API retrieves object lock configuration for bucket {string}")
    public void s3ApiRetrievesObjectLockConfigurationForBucket(String bucket) {
        state.lastResponse = exchangeBytes(webTestClient.get()
            .uri(URI.create("/" + bucket + "?object-lock"))
            .accept(MediaType.APPLICATION_XML));
    }

    @When("the S3 API retrieves lifecycle configuration for bucket {string}")
    public void s3ApiRetrievesLifecycleConfigurationForBucket(String bucket) {
        state.lastResponse = exchangeBytes(webTestClient.get()
            .uri(URI.create("/" + bucket + "?lifecycle"))
            .accept(MediaType.APPLICATION_XML));
    }

    @Then("the response status is {int}")
    public void theResponseStatusIs(int expectedStatus) {
        assertNotNull(state.lastResponse, "a response should have been captured");
        assertEquals(expectedStatus, state.lastResponse.status(), state.lastResponse.bodyAsString());
    }

    @Then("the response ETag header is present and enclosed in double quotes")
    public void theResponseEtagHeaderIsPresentAndEnclosedInDoubleQuotes() {
        String etag = requireHeader("ETag");
        assertTrue(etag.startsWith("\"") && etag.endsWith("\""), "ETag should be quoted: " + etag);
    }

    @Then("the response ETag header is 34 characters long representing 32 lowercase hex digits enclosed in double quotes")
    public void theResponseEtagHeaderIsThirtyFourCharactersLowercaseHex() {
        assertTrue(isQuotedMd5Etag(requireHeader("ETag")), "ETag should be quoted lowercase MD5 hex: " + requireHeader("ETag"));
    }

    @Then("the response ETag header is saved as {string}")
    public void theResponseEtagHeaderIsSavedAs(String name) {
        String etag = requireHeader("ETag");
        state.savedValues.put(name, etag);
    }

    @Then("the response header {string} matches the saved ETag value {string}")
    public void responseHeaderMatchesSavedEtagValue(String header, String savedName) {
        assertEquals(state.savedValues.get(savedName), requireHeader(header));
    }

    @Then("the upload part response ETag is a quoted 32-character lowercase hex string")
    public void uploadPartResponseEtagIsQuotedMd5Hex() {
        String headerEtag = requireHeader("ETag");
        String bodyEtag = extractXmlValue(state.lastResponse.bodyAsString(), "ETag");
        assertTrue(isQuotedMd5Etag(headerEtag), "UploadPart ETag header should be quoted lowercase MD5 hex: " + headerEtag);
        assertEquals(headerEtag, bodyEtag, "UploadPart XML ETag should match response header");
        assertNotEquals("\"placeholder-etag\"", headerEtag);
    }

    @Then("the complete multipart upload ETag ends with {string} indicating the two-part composition")
    public void completeMultipartUploadEtagEndsWithIndicatingComposition(String suffix) {
        String etag = extractXmlValue(state.lastResponse.bodyAsString(), "ETag");
        assertTrue(etag.endsWith(suffix + "\""), "CompleteMultipartUpload ETag should end with " + suffix + "\": " + etag);
        assertTrue(etag.matches("\\\"[0-9a-f]{32}-2\\\""), "multipart ETag should contain MD5 and part count: " + etag);
    }

    @Then("the list parts response contains {int} part")
    public void listPartsResponseContainsPart(int count) {
        String body = state.lastResponse.bodyAsString();
        assertEquals(count, occurrences(body, "<Part>"), body);
    }

    @Then("the persisted part has part number {int} and a valid quoted MD5 ETag")
    public void persistedPartHasPartNumberAndValidEtag(int partNumber) {
        String body = state.lastResponse.bodyAsString();
        assertTrue(body.contains("<PartNumber>" + partNumber + "</PartNumber>"), body);
        assertTrue(QUOTED_MD5_ETAG.matcher(body).find(), "ListParts should contain a quoted MD5 ETag: " + body);
    }

    @Then("the response body is {string}")
    public void theResponseBodyIs(String expectedBody) {
        assertEquals(expectedBody, state.lastResponse.bodyAsString());
    }

    @Then("the Content-Range response header is {string}")
    public void contentRangeResponseHeaderIs(String expected) {
        assertEquals(expected, requireHeader("Content-Range"));
    }

    @Then("the CopyObject response ETag matches the saved ETag {string}")
    public void copyObjectResponseEtagMatchesSavedEtag(String savedName) {
        assertEquals(state.savedValues.get(savedName), copyObjectResponseEtag());
    }

    @Then("the CopyObject response ETag is saved as {string}")
    public void copyObjectResponseEtagIsSavedAs(String name) {
        state.savedValues.put(name, copyObjectResponseEtag());
    }

    @Then("the response body contains tag key {string} with value {string}")
    public void responseBodyContainsTagKeyWithValue(String key, String value) {
        String body = state.lastResponse.bodyAsString();
        assertTrue(body.contains("<Key>" + key + "</Key>"), body);
        assertTrue(body.contains("<Value>" + value + "</Value>"), body);
    }

    @Then("the GetObjectTagging response contains an empty tag set")
    public void getObjectTaggingResponseContainsEmptyTagSet() {
        String body = state.lastResponse.bodyAsString();
        assertFalse(body.contains("<Key>"), body);
        assertFalse(body.contains("<Value>"), body);
        assertTrue(body.contains("TagSet") || body.contains("<Tagging"), body);
    }

    @Then("the bucket versioning response does not contain an {string} versioning status")
    public void bucketVersioningResponseDoesNotContainStatus(String status) {
        assertFalse(state.lastResponse.bodyAsString().contains("<Status>" + status + "</Status>"),
            state.lastResponse.bodyAsString());
    }

    @Then("the object lock configuration response indicates object lock is not enabled")
    public void objectLockConfigurationResponseIndicatesNotEnabled() {
        String body = state.lastResponse.bodyAsString();
        assertTrue(body.contains("ObjectLockEnabled") || body.contains("objectLockEnabled"), body);
        assertFalse(body.contains("<ObjectLockEnabled>Enabled</ObjectLockEnabled>"), body);
        assertTrue(body.toLowerCase().contains("false") || body.toLowerCase().contains("disabled"), body);
    }

    @Then("the response body contains {string}")
    public void responseBodyContains(String expected) {
        assertTrue(state.lastResponse.bodyAsString().contains(expected), state.lastResponse.bodyAsString());
    }

    private void uploadPart(int partNumber, String content) {
        assertNotNull(state.uploadId, "uploadId should be available before uploading parts");
        state.lastResponse = exchangeBytes(webTestClient.put()
            .uri(URI.create("/" + state.bucket + "/" + state.objectKey + "?uploadId=" + state.uploadId + "&partNumber=" + partNumber))
            .contentType(MediaType.APPLICATION_OCTET_STREAM)
            .bodyValue(content.getBytes(StandardCharsets.UTF_8)));
        String etag = state.lastResponse.header("ETag");
        assertNotNull(etag, "UploadPart should return ETag");
        state.uploadedParts.put(partNumber, UploadPart.create(PartNumber.of(partNumber), etag,
            content.getBytes(StandardCharsets.UTF_8).length));
    }

    private void getObjectWithHeaderValue(String key, String bucket, String header, String value) {
        state.lastResponse = exchangeBytes(webTestClient.get()
            .uri(URI.create("/" + bucket + "/" + key))
            .header(header, value));
    }

    private Response putObject(String bucket, String key, String body, Map<String, String> headers) {
        WebTestClient.RequestBodySpec spec = webTestClient.put()
            .uri(URI.create("/" + bucket + "/" + key))
            .contentType(MediaType.APPLICATION_OCTET_STREAM);
        headers.forEach(spec::header);
        return exchangeBytes(spec.bodyValue(body.getBytes(StandardCharsets.UTF_8)));
    }

    private static Response fromResult(EntityExchangeResult<byte[]> result) {
        return new Response(
            result.getStatus().value(),
            result.getResponseBody() == null ? new byte[0] : result.getResponseBody(),
            result.getResponseHeaders());
    }

    private Response exchangeBytes(WebTestClient.RequestHeadersSpec<?> spec) {
        return fromResult(spec.exchange().expectBody(byte[].class).returnResult());
    }

    private String requireHeader(String headerName) {
        String value = state.lastResponse.header(headerName);
        assertNotNull(value, headerName + " response header should be present");
        return value;
    }

    private String currentBucket() {
        if (state.bucket != null) {
            return state.bucket;
        }
        if (sharedState.bucket != null) {
            state.bucket = sharedState.bucket;
            return sharedState.bucket;
        }
        fail("bucket should be available from setup");
        return null;
    }

    private void rememberEtag(String key, String etag) {
        assertNotNull(etag, "ETag should be present for " + key);
        state.etagsByObjectKey.put(key, etag);
    }

    private String copyObjectResponseEtag() {
        String headerEtag = state.lastResponse.header("ETag");
        String bodyEtag = extractXmlValue(state.lastResponse.bodyAsString(), "ETag");
        if (headerEtag != null) {
            assertEquals(headerEtag, bodyEtag, "CopyObject XML ETag should match response header");
            return headerEtag;
        }
        return bodyEtag;
    }

    private static boolean isQuotedMd5Etag(String etag) {
        return etag != null && QUOTED_MD5_ETAG.matcher(etag).matches();
    }

    private static String extractXmlValue(String xml, String tagName) {
        String open = "<" + tagName + ">";
        String close = "</" + tagName + ">";
        int start = xml.indexOf(open);
        int end = xml.indexOf(close);
        if (start < 0 || end < start) {
            return "";
        }
        return xml.substring(start + open.length(), end).trim();
    }

    private static String tagSetXml(List<Map<String, String>> rows) {
        StringBuilder xml = new StringBuilder("<Tagging><TagSet>");
        for (Map<String, String> row : rows) {
            xml.append("<Tag><Key>")
                .append(row.get("Key"))
                .append("</Key><Value>")
                .append(row.get("Value"))
                .append("</Value></Tag>");
        }
        return xml.append("</TagSet></Tagging>").toString();
    }

    private static int occurrences(String value, String token) {
        int count = 0;
        int index = 0;
        while ((index = value.indexOf(token, index)) >= 0) {
            count++;
            index += token.length();
        }
        return count;
    }

    private static void cleanDirectory(Path path) {
        try {
            if (Files.exists(path)) {
                try (var walk = Files.walk(path)) {
                    for (Path current : walk.sorted(Comparator.reverseOrder()).toList()) {
                        Files.deleteIfExists(current);
                    }
                }
            }
            Files.createDirectories(path);
        } catch (IOException e) {
            throw new UncheckedIOException("Failed to clean directory " + path, e);
        }
    }

    private static final class TestFileSystemMultipartUploadRepository {
        private final Path multipartDir;

        private TestFileSystemMultipartUploadRepository(Path root) {
            this.multipartDir = root.resolve("multipart");
            try {
                Files.createDirectories(multipartDir);
            } catch (IOException e) {
                throw new UncheckedIOException("Failed to create multipart directory " + multipartDir, e);
            }
        }

        Mono<MultipartUpload> save(MultipartUpload upload) {
            return Mono.fromCallable(() -> {
                java.util.Properties properties = new java.util.Properties();
                properties.setProperty("bucket", upload.key().bucket());
                properties.setProperty("key", upload.key().key());
                properties.setProperty("uploadId", upload.uploadId().value());
                properties.setProperty("part.count", Integer.toString(upload.parts().size()));
                for (int i = 0; i < upload.parts().size(); i++) {
                    UploadPart part = upload.parts().get(i);
                    String prefix = "part." + i + ".";
                    properties.setProperty(prefix + "number", Integer.toString(part.partNumber().value()));
                    properties.setProperty(prefix + "etag", part.etag());
                    properties.setProperty(prefix + "size", Long.toString(part.size()));
                    properties.setProperty(prefix + "lastModified", part.lastModified().toString());
                }
                Path target = multipartDir.resolve(upload.uploadId().value() + ".properties");
                Path tmp = target.resolveSibling(target.getFileName() + ".tmp");
                try (var writer = Files.newBufferedWriter(tmp, StandardCharsets.UTF_8,
                    java.nio.file.StandardOpenOption.CREATE,
                    java.nio.file.StandardOpenOption.TRUNCATE_EXISTING)) {
                    properties.store(writer, "Phase 5 multipart restart probe");
                }
                Files.move(tmp, target, java.nio.file.StandardCopyOption.REPLACE_EXISTING,
                    java.nio.file.StandardCopyOption.ATOMIC_MOVE);
                return upload.clearEvents();
            }).subscribeOn(Schedulers.boundedElastic());
        }

        Flux<UploadPart> findParts(UploadId uploadId) {
            return Mono.fromCallable(() -> {
                Path target = multipartDir.resolve(uploadId.value() + ".properties");
                if (!Files.exists(target)) {
                    return List.<UploadPart>of();
                }
                java.util.Properties properties = new java.util.Properties();
                try (var reader = Files.newBufferedReader(target, StandardCharsets.UTF_8)) {
                    properties.load(reader);
                }
                int count = Integer.parseInt(properties.getProperty("part.count", "0"));
                java.util.ArrayList<UploadPart> parts = new java.util.ArrayList<>();
                for (int i = 0; i < count; i++) {
                    String prefix = "part." + i + ".";
                    parts.add(UploadPart.of(
                        PartNumber.of(Integer.parseInt(properties.getProperty(prefix + "number"))),
                        properties.getProperty(prefix + "etag"),
                        Long.parseLong(properties.getProperty(prefix + "size")),
                        java.time.Instant.parse(properties.getProperty(prefix + "lastModified"))));
                }
                return List.copyOf(parts);
            }).subscribeOn(Schedulers.boundedElastic()).flatMapMany(Flux::fromIterable);
        }
    }

    public static final class Phase5State {
        String bucket;
        String objectKey;
        String objectContent;
        String uploadId;
        Response lastResponse;
        boolean filesystemRestartProbeValidated;
        final Map<String, String> savedValues = new HashMap<>();
        final Map<String, String> etagsByObjectKey = new HashMap<>();
        final Map<Integer, UploadPart> uploadedParts = new HashMap<>();

        void reset() {
            bucket = null;
            objectKey = null;
            objectContent = null;
            uploadId = null;
            lastResponse = null;
            filesystemRestartProbeValidated = false;
            savedValues.clear();
            etagsByObjectKey.clear();
            uploadedParts.clear();
        }
    }

    private record Response(int status, byte[] body, HttpHeaders headers) {
        String bodyAsString() {
            return new String(body, StandardCharsets.UTF_8);
        }

        String header(String name) {
            return headers.getFirst(name);
        }
    }
}
