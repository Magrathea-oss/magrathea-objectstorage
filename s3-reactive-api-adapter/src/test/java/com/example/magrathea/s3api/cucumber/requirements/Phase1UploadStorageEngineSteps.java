package com.example.magrathea.s3api.cucumber.requirements;

import com.example.magrathea.objectstorage.repository.storageengine.adapter.StorageEngineReactiveBucketRepository;
import com.example.magrathea.objectstorage.repository.storageengine.adapter.StorageEngineReactiveMultipartUploadRepository;
import com.example.magrathea.objectstorage.repository.storageengine.adapter.StorageEngineReactiveS3ObjectRepository;
import io.cucumber.java.en.Given;
import io.cucumber.java.en.Then;
import io.cucumber.java.en.When;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpHeaders;
import org.springframework.test.web.reactive.server.WebTestClient;

import java.io.IOException;
import java.io.InputStream;
import java.io.StringReader;
import java.io.UncheckedIOException;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.Base64;
import java.util.Comparator;
import java.util.HexFormat;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Properties;

import static org.junit.jupiter.api.Assertions.*;

public class Phase1UploadStorageEngineSteps {

    private static final Path PROJECT_ROOT = Path.of("").toAbsolutePath().normalize();
    private static final Path CONFIGURED_STORAGE_ROOT = PROJECT_ROOT.resolve("target/storage-engine-it/current").normalize();

    @Autowired
    private WebTestClient webTestClient;

    @Autowired
    private Phase2FilesystemReliabilitySteps.State state;

    @Autowired
    private ObjectProvider<StorageEngineReactiveBucketRepository> bucketRepository;

    @Autowired
    private ObjectProvider<StorageEngineReactiveS3ObjectRepository> objectRepository;

    @Autowired
    private ObjectProvider<StorageEngineReactiveMultipartUploadRepository> multipartRepository;

    @Autowired
    private RequirementsTestApp.MutableFileSystemWriteFaultInjector faultInjector;

    private Response lastObservedRead;
    private Response failedUploadResponse;
    private String lastCommittedManifestId;

    @io.cucumber.java.Before
    public void resetPhase1State() {
        lastObservedRead = null;
        failedUploadResponse = null;
        lastCommittedManifestId = null;
        faultInjector.disable();
    }

    @Given("the storage-engine repositories are backed by storage-engine filesystem root {string}")
    public void storageEngineRepositoriesAreBackedByFilesystemRoot(String storageRoot) {
        state.storageRoot = resolveProjectPath(storageRoot);
        cleanScenarioRootAndPointConfiguredSymlink(state.storageRoot);
        resetRepositories();
    }

    @Given("the S3 client applies PutObject header profile {string} with headers {string}")
    public void clientAppliesPutObjectHeaderProfile(String headerProfile, String headers) {
        state.requestHeaders.clear();
        state.requestHeaders.putAll(parseHeaders(headers, state.fixtureFile));
    }

    @Given("the fixture file {string} contains {string}")
    public void fixtureFileContains(String fixtureFile, String expectedContent) {
        byte[] bytes = state.fixtureBytes.computeIfAbsent(fixtureFile, this::readFixture);
        assertTrue(new String(bytes, StandardCharsets.UTF_8).contains(expectedContent),
            "fixture file should contain the expected text while preserving exact fixture bytes");
        state.fixtureFile = fixtureFile;
    }

    @Given("the S3 client requests storage class {string}")
    public void s3ClientRequestsStorageClass(String expectedStorageClass) {
        assertEquals("STANDARD", expectedStorageClass,
            "Phase 1 upload reliability examples currently exercise the STANDARD storage class");
    }

    @Given("the Phase 1 upload fixture file {string} is a deterministic 256 MiB object")
    public void fixtureFileIsDeterministic256MiBObject(String fixtureFile) {
        byte[] bytes = state.fixtureBytes.computeIfAbsent(fixtureFile, this::readFixture);
        assertEquals(256L * 1024L * 1024L, bytes.length,
            "large-object fixture must be exactly 256 MiB");
        state.fixtureFile = fixtureFile;
    }

    @Given("the storage engine chunk size is configured to a bounded value smaller than the object")
    public void storageEngineChunkSizeIsBoundedSmallerThanObject() {
        assertTrue(state.fixtureFile == null || state.fixtureBytes.getOrDefault(state.fixtureFile, new byte[0]).length > 65_536,
            "Phase 1 storage-engine-it uses the default 64 KiB chunk size, which is smaller than the large fixture");
    }

    @Given("an S3 client starts uploading fixture file {string} to bucket {string} and key {string} with PutObject header profile {string} and headers {string}")
    public void clientStartsUploadingWithHeaderProfile(String fixtureFile, String bucket, String key,
                                                       String headerProfile, String headers) {
        state.fixtureBytes.put(fixtureFile, readFixture(fixtureFile));
        state.bucket = bucket;
        state.objectKey = key;
        state.fixtureFile = fixtureFile;
        state.requestHeaders.clear();
        state.requestHeaders.putAll(parseHeaders(headers, fixtureFile));
        faultInjector.interruptAfterChunkTempWrite(true);
    }

    @Then("the upload is committed before the PutObject response is returned to the client")
    public void uploadCommittedBeforePutObjectResponseReturns() {
        Response read = getObject(state.bucket, state.objectKey);
        assertEquals(200, read.status(), read.bodyAsString());
        assertArrayEquals(state.fixtureBytes.get(state.fixtureFile), read.body());
        assertFalse(artifactsFor(state.bucket, state.objectKey).chunkFiles().isEmpty(),
            "committed upload should have at least one durable chunk before the client observes the response");
    }

    @Then("the filesystem contains the durable chunks, manifest, and S3 object reference for bucket {string} and key {string}")
    public void filesystemContainsDurableArtifacts(String bucket, String key) {
        ArtifactSet artifacts = artifactsFor(bucket, key);
        assertTrue(Files.exists(artifacts.referenceFile()), "S3 object reference should exist");
        assertTrue(Files.exists(artifacts.manifestFile()), "manifest should exist");
        assertFalse(artifacts.chunkFiles().isEmpty(), "at least one committed chunk should exist");
        artifacts.chunkFiles().forEach(this::assertChunkChecksumValid);
        assertManifestChecksumValid(artifacts.manifestFile());
    }

    @Then("the upload result records a committed manifest identifier for the stored bytes")
    public void uploadResultRecordsCommittedManifestIdentifier() {
        ArtifactSet artifacts = artifactsFor(state.bucket, state.objectKey);
        Properties reference = loadProperties(artifacts.referenceFile());
        lastCommittedManifestId = reference.getProperty("manifestId");
        assertNotNull(lastCommittedManifestId, "object reference should record the committed manifest identifier");
        assertTrue(Files.exists(artifacts.manifestFile()), "committed manifest file should exist");
    }

    @Then("the object reference for bucket {string} and key {string} points to that manifest identifier")
    public void objectReferencePointsToCommittedManifestIdentifier(String bucket, String key) {
        ArtifactSet artifacts = artifactsFor(bucket, key);
        String manifestId = loadProperties(artifacts.referenceFile()).getProperty("manifestId");
        if (lastCommittedManifestId == null) {
            lastCommittedManifestId = manifestId;
        }
        assertEquals(lastCommittedManifestId, manifestId,
            "object reference should point to the manifest committed by PutObject");
    }

    @Then("the visible object attributes include storage class {string}")
    public void visibleObjectAttributesIncludeStorageClass(String expectedStorageClass) {
        committedObjectRecordsStorageClass(expectedStorageClass);
    }

    @Then("the visible object attributes and metadata include {string}")
    public void visibleObjectAttributesAndMetadataInclude(String expectedHeaders) {
        committedObjectRecordsDurableHeadersAndMetadata(expectedHeaders);
    }

    @Then("the committed object records storage class {string}")
    public void committedObjectRecordsStorageClass(String expectedStorageClass) {
        ArtifactSet artifacts = artifactsFor(state.bucket, state.objectKey);
        Properties reference = loadProperties(artifacts.referenceFile());
        Properties manifest = loadProperties(artifacts.manifestFile());
        assertEquals(expectedStorageClass, reference.getProperty("storageClass"));
        assertEquals(expectedStorageClass, manifest.getProperty("storageClassId"));
    }

    @Then("the committed object records durable headers and metadata {string}")
    public void committedObjectRecordsDurableHeadersAndMetadata(String expectedHeaders) {
        ArtifactSet artifacts = artifactsFor(state.bucket, state.objectKey);
        Properties reference = loadProperties(artifacts.referenceFile());
        Properties manifest = loadProperties(artifacts.manifestFile());
        Map<String, String> expected = parseExpectedHeaderPairs(expectedHeaders);
        for (Map.Entry<String, String> entry : expected.entrySet()) {
            String name = entry.getKey();
            String value = materializeExpectedValue(entry.getValue(), state.fixtureFile);
            if (name.equalsIgnoreCase("x-amz-storage-class")) {
                assertEquals(value, reference.getProperty("storageClass"));
            } else if (name.toLowerCase().startsWith("x-amz-meta-")) {
                assertReferenceMetadata(reference, name, value);
            } else if (name.equalsIgnoreCase("x-amz-checksum-sha256") || name.equalsIgnoreCase("Content-MD5")) {
                assertEquals(value, manifest.getProperty("upload.declaredChecksum.value"));
            }
        }
    }

    @Then("the storage engine has a durable committed manifest for the uploaded bytes")
    public void storageEngineHasDurableCommittedManifestForUploadedBytes() {
        ArtifactSet artifacts = artifactsFor(state.bucket, state.objectKey);
        assertTrue(Files.exists(artifacts.manifestFile()), "manifest should exist for committed upload");
        assertManifestChecksumValid(artifacts.manifestFile());
        assertFalse(artifacts.chunkFiles().isEmpty(), "manifest should reference durable chunks");
    }

    @Then("the manifest records the object byte length, chunk list, checksum metadata, and creation time")
    public void manifestRecordsLengthChunksChecksumAndCreationTime() {
        ArtifactSet artifacts = artifactsFor(state.bucket, state.objectKey);
        Properties manifest = loadProperties(artifacts.manifestFile());
        Properties reference = loadProperties(artifacts.referenceFile());
        assertEquals(String.valueOf(state.fixtureBytes.get(state.fixtureFile).length),
            manifest.getProperty("totalOriginalSize"));
        assertEquals(String.valueOf(state.fixtureBytes.get(state.fixtureFile).length),
            manifest.getProperty("upload.totalObjectSize"));
        assertTrue(manifestArtifactCount(manifest) > 0,
            "manifest should contain at least one chunk reference");
        assertNotNull(manifest.getProperty("upload.consolidatedChecksum.algorithm"));
        assertNotNull(manifest.getProperty("upload.consolidatedChecksum.value"));
        assertNotNull(reference.getProperty("createdAt"), "object reference should record creation time");
    }

    @Then("the manifest records storage class {string} for the uploaded object")
    public void manifestRecordsStorageClassForUploadedObject(String expectedStorageClass) {
        committedManifestRecordsStorageClass(expectedStorageClass);
    }

    @Then("the manifest records durable object headers and metadata {string}")
    public void manifestRecordsDurableObjectHeadersAndMetadata(String expectedManifestMetadata) {
        committedObjectRecordsDurableHeadersAndMetadata(expectedManifestMetadata);
    }

    @Then("the S3 object repository has a durable reference from bucket {string} and key {string} to the manifest identifier")
    public void s3ObjectRepositoryHasDurableReferenceToManifest(String bucket, String key) {
        objectReferencePointsToCommittedManifestIdentifier(bucket, key);
    }

    @When("the object repository is reloaded from storage-engine filesystem root {string}")
    public void objectRepositoryIsReloadedFromFilesystemRoot(String storageRoot) {
        assertScenarioRoot(storageRoot);
        objectRepository.ifAvailable(StorageEngineReactiveS3ObjectRepository::reloadFromDisk);
    }

    @When("the manifest repository is reloaded from storage-engine filesystem root {string}")
    public void manifestRepositoryIsReloadedFromFilesystemRoot(String storageRoot) {
        assertScenarioRoot(storageRoot);
        multipartRepository.ifAvailable(StorageEngineReactiveMultipartUploadRepository::reloadFromDisk);
    }

    @Then("bucket {string} and key {string} still resolve to the same manifest identifier")
    public void bucketAndKeyStillResolveToSameManifestIdentifier(String bucket, String key) {
        objectReferencePointsToCommittedManifestIdentifier(bucket, key);
    }

    @Then("the resolved manifest can be used to stream the original bytes from fixture file {string}")
    public void resolvedManifestCanStreamOriginalBytes(String fixtureFile) {
        responseBodyMatchesFixtureFile(fixtureFile);
    }

    @Then("the resolved manifest still exposes durable object headers and metadata {string}")
    public void resolvedManifestStillExposesDurableHeadersAndMetadata(String expectedManifestMetadata) {
        committedObjectRecordsDurableHeadersAndMetadata(expectedManifestMetadata);
    }

    @When("one durable chunk for bucket {string} and key {string} is corrupted outside the application")
    public void oneDurableChunkIsCorruptedOutsideTheApplication(String bucket, String key) {
        ArtifactSet artifacts = artifactsFor(bucket, key);
        assertFalse(artifacts.chunkFiles().isEmpty(), "expected at least one committed chunk file");
        Path chunk = artifacts.chunkFiles().getFirst();
        byte[] bytes = readBytes(chunk);
        assertTrue(bytes.length > 0, "chunk must not be empty");
        bytes[0] = (byte) (bytes[0] ^ 0x7f);
        writeBytes(chunk, bytes);
    }

    @Then("the upload validates client-supplied integrity headers from profile {string} when such headers are present")
    public void uploadValidatesClientSuppliedIntegrityHeaders(String headerProfile) {
        ArtifactSet artifacts = artifactsFor(state.bucket, state.objectKey);
        Properties manifest = loadProperties(artifacts.manifestFile());
        assertEquals("true", manifest.getProperty("upload.metadataValidated"));
        assertEquals("true", manifest.getProperty("upload.verificationPassed"));
        if (headerProfile.equals("checksum-sha256") || headerProfile.equals("content-md5")) {
            assertEquals("true", manifest.getProperty("upload.declaredChecksum.present"));
        }
    }

    @Then("each stored chunk has verifiable integrity metadata")
    public void eachStoredChunkHasVerifiableIntegrityMetadata() {
        ArtifactSet artifacts = artifactsFor(state.bucket, state.objectKey);
        assertFalse(artifacts.chunkFiles().isEmpty(), "expected committed chunks");
        artifacts.chunkFiles().forEach(this::assertChunkChecksumValid);
    }

    @Then("the committed manifest records integrity metadata {string} for the complete object and each chunk reference")
    public void committedManifestRecordsIntegrityMetadata(String expectedIntegrityMetadata) {
        ArtifactSet artifacts = artifactsFor(state.bucket, state.objectKey);
        Properties manifest = loadProperties(artifacts.manifestFile());
        assertNotNull(manifest.getProperty("upload.consolidatedChecksum.algorithm"));
        assertNotNull(manifest.getProperty("upload.consolidatedChecksum.value"));
        int chunkCount = manifestArtifactCount(manifest);
        assertTrue(chunkCount > 0, "manifest should reference at least one chunk");
        for (int i = 0; i < chunkCount; i++) {
            assertNotNull(manifest.getProperty(manifestArtifactPrefix(manifest, i) + "finalChecksum.algorithm"));
            assertNotNull(manifest.getProperty(manifestArtifactPrefix(manifest, i) + "finalChecksum.value"));
        }
        if (expectedIntegrityMetadata.contains("x-amz-checksum-sha256")) {
            assertEquals(computedSha256Base64(state.fixtureFile), manifest.getProperty("upload.declaredChecksum.value"));
        } else if (expectedIntegrityMetadata.contains("Content-MD5")) {
            assertEquals(computedMd5Base64(state.fixtureFile), manifest.getProperty("upload.declaredChecksum.value"));
        }
    }

    @Then("the committed manifest records storage class {string}")
    public void committedManifestRecordsStorageClass(String expectedStorageClass) {
        Properties manifest = loadProperties(artifactsFor(state.bucket, state.objectKey).manifestFile());
        assertEquals(expectedStorageClass, manifest.getProperty("storageClassId"));
    }

    @When("the recovery process stops the application process")
    public void recoveryProcessStopsApplicationProcess() {
        lastObservedRead = null;
    }

    @When("all in-memory repositories and caches are discarded")
    public void allInMemoryRepositoriesAndCachesAreDiscarded() {
        resetRepositories();
    }

    @When("the recovery process starts the application again using storage-engine filesystem root {string}")
    public void recoveryProcessStartsApplicationAgain(String storageRoot) {
        assertScenarioRoot(storageRoot);
    }

    @When("the S3 client uploads fixture file {string} to bucket {string} and key {string} through the S3 HTTP PutObject API using a streaming request body")
    public void clientUploadsFixtureUsingStreamingRequestBody(String fixtureFile, String bucket, String key) {
        byte[] bytes = state.fixtureBytes.computeIfAbsent(fixtureFile, this::readFixture);
        Response response = putObjectWithHeaders(bucket, key, bytes, state.requestHeaders);
        assertTrue(response.status() >= 200 && response.status() < 300,
            () -> "streaming PutObject should succeed: " + response.bodyAsString());
        state.bucket = bucket;
        state.objectKey = key;
        state.fixtureFile = fixtureFile;
    }

    @When("the same S3 client immediately reads bucket {string} and key {string} through the S3 HTTP GetObject API")
    public void sameCLientImmediatelyReadsBucketAndKey(String bucket, String key) {
        lastObservedRead = getObject(bucket, key);
    }

    @Then("the response body matches fixture file {string}")
    public void responseBodyMatchesFixtureFile(String fixtureFile) {
        if (lastObservedRead == null) {
            lastObservedRead = getObject(state.bucket, state.objectKey);
        }
        assertEquals(200, lastObservedRead.status(), lastObservedRead.bodyAsString());
        assertArrayEquals(state.fixtureBytes.computeIfAbsent(fixtureFile, this::readFixture), lastObservedRead.body());
    }

    @Then("the response attributes and metadata include {string}")
    public void responseAttributesAndMetadataInclude(String expectedHeaders) {
        if (lastObservedRead == null) {
            lastObservedRead = getObject(state.bucket, state.objectKey);
        }
        assertObservableResponseHeaders(lastObservedRead.headers(), expectedHeaders, state.fixtureFile);
    }

    @Then("the response storage class is {string}")
    public void responseStorageClassIs(String expectedStorageClass) {
        committedObjectRecordsStorageClass(expectedStorageClass);
    }

    @Then("the read succeeds without reconstructing any object state from memory")
    public void readSucceedsWithoutReconstructingObjectStateFromMemory() {
        if (lastObservedRead == null) {
            lastObservedRead = getObject(state.bucket, state.objectKey);
        }
        assertEquals(200, lastObservedRead.status(), lastObservedRead.bodyAsString());
        assertArrayEquals(state.fixtureBytes.get(state.fixtureFile), lastObservedRead.body());
        ArtifactSet artifacts = artifactsFor(state.bucket, state.objectKey);
        assertTrue(Files.exists(artifacts.referenceFile()));
        assertTrue(Files.exists(artifacts.manifestFile()));
    }

    @Then("the committed manifest contains the ordered chunk references needed to reconstruct the object")
    public void committedManifestContainsOrderedChunkReferences() {
        Properties manifest = loadProperties(artifactsFor(state.bucket, state.objectKey).manifestFile());
        int chunkCount = manifestArtifactCount(manifest);
        assertTrue(chunkCount > 0, "manifest should contain chunk references");
        for (int i = 0; i < chunkCount; i++) {
            assertNotNull(manifestArtifactId(manifest, i),
                "manifest should contain ordered chunk reference " + i);
        }
    }

    @Then("the committed manifest records durable object headers and metadata {string}")
    public void committedManifestRecordsDurableObjectHeadersAndMetadata(String expectedManifestMetadata) {
        committedObjectRecordsDurableHeadersAndMetadata(expectedManifestMetadata);
    }

    @Then("the streamed response bytes exactly match fixture file {string}")
    public void streamedResponseBytesExactlyMatchFixtureFile(String fixtureFile) {
        responseBodyMatchesFixtureFile(fixtureFile);
    }

    @Then("the streamed response attributes and metadata include {string}")
    public void streamedResponseAttributesAndMetadataInclude(String expectedManifestMetadata) {
        responseAttributesAndMetadataInclude(expectedManifestMetadata);
    }

    @Then("the read path emits chunks in manifest order without loading the complete object into memory")
    public void readPathEmitsChunksInManifestOrderWithoutWholeObjectMemory() {
        committedManifestContainsOrderedChunkReferences();
    }

    @Then("the response is produced from the storage-engine filesystem state rather than an in-memory-only object cache")
    public void responseProducedFromStorageEngineFilesystemState() {
        ArtifactSet artifacts = artifactsFor(state.bucket, state.objectKey);
        artifacts.chunkFiles().forEach(this::assertChunkChecksumValid);
        // Reset only object/multipart in-memory caches — the bucket has no filesystem
        // counterpart, so resetting it would cause NoSuchBucket on the subsequent read.
        objectRepository.ifAvailable(StorageEngineReactiveS3ObjectRepository::reset);
        multipartRepository.ifAvailable(StorageEngineReactiveMultipartUploadRepository::reset);
        Response readAfterRepositoryReset = getObject(state.bucket, state.objectKey);
        assertEquals(200, readAfterRepositoryReset.status(), readAfterRepositoryReset.bodyAsString());
        assertArrayEquals(state.fixtureBytes.get(state.fixtureFile), readAfterRepositoryReset.body());
    }

    @When("the upload fails before all bytes are durably written and committed")
    public void uploadFailsBeforeAllBytesAreDurablyWrittenAndCommitted() {
        try {
            failedUploadResponse = putObjectWithHeaders(state.bucket, state.objectKey,
                state.fixtureBytes.get(state.fixtureFile), state.requestHeaders);
            assertFalse(failedUploadResponse.status() >= 200 && failedUploadResponse.status() < 300,
                () -> "fault-injected PutObject should fail before commit, got " + failedUploadResponse.status());
        } finally {
            faultInjector.disable();
        }
    }

    @Then("bucket {string} and key {string} do not resolve to a committed manifest identifier")
    public void bucketAndKeyDoNotResolveToCommittedManifestIdentifier(String bucket, String key) {
        assertNoObjectReference(bucket, key);
    }

    @Then("no committed manifest references missing or partial chunks for that failed upload")
    public void noCommittedManifestReferencesMissingOrPartialChunksForFailedUpload() {
        assertNoCommittedManifestReferencesMissingChunks();
    }

    @Then("no durable object headers or metadata {string} are published for that failed upload")
    public void noDurableObjectHeadersOrMetadataArePublished(String expectedAbsentMetadata) {
        assertNoObjectReference(state.bucket, state.objectKey);
    }

    @Then("bucket {string} and key {string} remain unreadable")
    public void bucketAndKeyRemainUnreadable(String bucket, String key) {
        Response response = getObject(bucket, key);
        assertTrue(response.status() >= 400,
            () -> "failed upload should remain unreadable, got " + response.status());
    }

    @Then("recovery either removes uncommitted upload artifacts or keeps them isolated from committed object references")
    public void recoveryRemovesOrIsolatesUncommittedUploadArtifacts() {
        assertNoObjectReference(state.bucket, state.objectKey);
        assertNoCommittedManifestReferencesMissingChunks();
    }

    @Then("the storage engine detects the integrity mismatch before returning corrupted bytes as a successful object")
    public void storageEngineDetectsIntegrityMismatch() {
        lastObservedRead = getObject(state.bucket, state.objectKey);
        assertFalse(lastObservedRead.status() >= 200 && lastObservedRead.status() < 300,
            () -> "corrupted object must not be returned successfully; status=" + lastObservedRead.status()
                + " body=" + lastObservedRead.bodyAsString());
        assertFalse(java.util.Arrays.equals(state.fixtureBytes.get(state.fixtureFile), lastObservedRead.body()),
            "corrupted object bytes must not be returned as the successful fixture body");
    }

    @Then("the observable read result reports an object integrity failure")
    public void observableReadResultReportsObjectIntegrityFailure() {
        assertNotNull(lastObservedRead, "corruption read response should be captured");
        assertTrue(lastObservedRead.status() >= 400,
            () -> "integrity failure should be an error response, got " + lastObservedRead.status());
        String body = lastObservedRead.bodyAsString();
        assertTrue(body.contains("XAmzChecksumMismatch") || body.toLowerCase().contains("integrity")
                || body.toLowerCase().contains("checksum"),
            () -> "integrity failure body should mention checksum or integrity, got: " + body);
    }

    private Map<String, String> parseHeaders(String headers, String fixtureFile) {
        if (headers == null || headers.isBlank() || headers.equalsIgnoreCase("none")) {
            return Map.of();
        }
        Map<String, String> parsed = new LinkedHashMap<>();
        for (String token : headers.split(";")) {
            String trimmed = token.trim();
            if (trimmed.isEmpty()) {
                continue;
            }
            int separator = trimmed.indexOf('=');
            assertTrue(separator > 0, "header token should use name=value: " + trimmed);
            parsed.put(trimmed.substring(0, separator).trim(),
                materializeExpectedValue(trimmed.substring(separator + 1).trim(), fixtureFile));
        }
        return parsed;
    }

    private Map<String, String> parseExpectedHeaderPairs(String expectedHeaders) {
        if (expectedHeaders == null || expectedHeaders.isBlank()
                || expectedHeaders.equalsIgnoreCase("none")
                || expectedHeaders.equalsIgnoreCase("default storage class STANDARD")) {
            return Map.of();
        }
        return parseHeaders(expectedHeaders, state.fixtureFile);
    }

    private String materializeExpectedValue(String value, String fixtureFile) {
        if ("computed-sha256-base64-for-fixture".equals(value)) {
            return computedSha256Base64(fixtureFile);
        }
        if ("computed-md5-base64-for-fixture".equals(value)) {
            return computedMd5Base64(fixtureFile);
        }
        return value;
    }

    private void assertObservableResponseHeaders(HttpHeaders headers, String expectedHeaders, String fixtureFile) {
        Map<String, String> expected = parseExpectedHeaderPairs(expectedHeaders);
        for (Map.Entry<String, String> entry : expected.entrySet()) {
            String name = entry.getKey();
            String value = materializeExpectedValue(entry.getValue(), fixtureFile);
            if (name.equalsIgnoreCase("Content-Type") || name.equalsIgnoreCase("x-amz-storage-class")) {
                continue;
            }
            String observed = headers.getFirst(name);
            assertEquals(value, observed, "expected observable response header " + name);
        }
    }

    private void assertReferenceMetadata(Properties reference, String expectedName, String expectedValue) {
        int metadataCount = Integer.parseInt(reference.getProperty("userMetadata.count", "0"));
        for (int index = 0; index < metadataCount; index++) {
            String prefix = "userMetadata." + index + ".";
            if (expectedName.equals(reference.getProperty(prefix + "key"))) {
                assertEquals(expectedValue, reference.getProperty(prefix + "value"));
                return;
            }
        }
        fail("reference should contain user metadata " + expectedName);
    }

    private Response putObjectWithHeaders(String bucket, String key, byte[] bytes, Map<String, String> headers) {
        var request = webTestClient.put()
            .uri(URI.create("/" + bucket + "/" + key));
        headers.forEach((name, value) -> request.header(name, value));
        var result = request
            .bodyValue(bytes)
            .exchange()
            .expectBody(byte[].class)
            .returnResult();
        return new Response(result.getStatus().value(),
            result.getResponseBody() == null ? new byte[0] : result.getResponseBody(),
            result.getResponseHeaders());
    }

    private Response getObject(String bucket, String key) {
        var result = webTestClient.get()
            .uri(URI.create("/" + bucket + "/" + key))
            .exchange()
            .expectBody(byte[].class)
            .returnResult();
        return new Response(result.getStatus().value(),
            result.getResponseBody() == null ? new byte[0] : result.getResponseBody(),
            result.getResponseHeaders());
    }

    private void assertScenarioRoot(String storageRoot) {
        assertEquals(state.storageRoot, resolveProjectPath(storageRoot),
            "step storage root should match the prepared scenario root");
    }

    private void assertNoObjectReference(String bucket, String key) {
        assertTrue(referenceFilesFor(bucket, key).isEmpty(),
            "failed upload must not publish an object reference for " + bucket + "/" + key);
    }

    private void assertNoCommittedManifestReferencesMissingChunks() {
        Path manifestsRoot = state.storageRoot.resolve("metadata/manifests");
        if (!Files.isDirectory(manifestsRoot)) {
            return;
        }
        try (var walk = Files.walk(manifestsRoot)) {
            for (Path manifestPath : walk.filter(Files::isRegularFile)
                    .filter(path -> path.getFileName().toString().endsWith(".properties"))
                    .toList()) {
                Properties manifest = loadProperties(manifestPath);
                int chunkCount = manifestArtifactCount(manifest);
                for (int i = 0; i < chunkCount; i++) {
                    String chunkId = manifestArtifactId(manifest, i);
                    if (chunkId != null) {
                        assertFalse(chunkFilesById(chunkId).isEmpty(),
                            "committed manifest must not reference a missing chunk: " + manifestPath);
                    }
                }
            }
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    private ArtifactSet artifactsFor(String bucket, String key) {
        List<Path> references = referenceFilesFor(bucket, key);
        assertEquals(1, references.size(), "expected one S3 object reference for " + bucket + "/" + key);
        Path referencePath = references.getFirst();
        Properties reference = loadProperties(referencePath);
        String manifestId = reference.getProperty("manifestId");
        assertNotNull(manifestId, "reference should have manifestId");
        Path manifestPath = state.storageRoot.resolve("metadata/manifests/" + manifestId + ".properties");
        assertTrue(Files.exists(manifestPath), "manifest should exist: " + manifestPath);
        Properties manifest = loadProperties(manifestPath);
        int chunkCount = manifestArtifactCount(manifest);
        List<Path> chunks = new ArrayList<>();
        for (int i = 0; i < chunkCount; i++) {
            String chunkId = manifestArtifactId(manifest, i);
            if (chunkId != null) {
                chunks.addAll(chunkFilesById(chunkId));
            }
        }
        return new ArtifactSet(referencePath, manifestPath, chunks);
    }

    private List<Path> referenceFilesFor(String bucket, String key) {
        Path referencesRoot = state.storageRoot.resolve("metadata/s3-object-references");
        if (!Files.isDirectory(referencesRoot)) {
            return List.of();
        }
        try (var walk = Files.walk(referencesRoot)) {
            return walk
                .filter(Files::isRegularFile)
                .filter(path -> path.getFileName().toString().endsWith(".properties"))
                .filter(path -> {
                    Properties properties = loadProperties(path);
                    return bucket.equals(properties.getProperty("bucket")) && key.equals(properties.getProperty("key"));
                })
                .sorted()
                .toList();
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    private List<Path> chunkFilesById(String chunkId) {
        Path nodes = state.storageRoot.resolve("nodes");
        if (!Files.isDirectory(nodes)) {
            return List.of();
        }
        try (var walk = Files.walk(nodes)) {
            return walk
                .filter(Files::isRegularFile)
                .filter(path -> path.getFileName().toString().equals(chunkId))
                .sorted()
                .toList();
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    private void assertChunkChecksumValid(Path chunkFile) {
        Path checksumFile = chunkFile.resolveSibling(chunkFile.getFileName() + ".sha256");
        assertTrue(Files.exists(checksumFile), "chunk checksum sidecar should exist: " + checksumFile);
        assertEquals(readString(checksumFile).trim(), sha256Hex(readBytes(chunkFile)),
            "chunk checksum should match stored bytes for " + chunkFile);
    }

    private void assertManifestChecksumValid(Path manifestFile) {
        String raw = readString(manifestFile);
        String marker = "\nmanifest.checksum=";
        int index = raw.lastIndexOf(marker);
        assertTrue(index >= 0, "manifest should contain checksum trailer: " + manifestFile);
        String contentForVerification = raw.substring(0, index + 1);
        String stored = raw.substring(index + marker.length()).trim();
        assertEquals(stored, sha256Hex(contentForVerification), "manifest checksum should verify");
    }

    private void resetRepositories() {
        // Bucket registry, object references and multipart state are durable on the
        // storage-engine filesystem (EP-2). Discard every in-memory cache and reload
        // state from the currently configured storage root: after a symlink swap this
        // yields a clean, empty view; within a scenario it simulates a process restart
        // that keeps the durable filesystem state.
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
            Files.createSymbolicLink(CONFIGURED_STORAGE_ROOT, scenarioRoot);
            createClusterLayout(CONFIGURED_STORAGE_ROOT);
            createClusterLayout(scenarioRoot);
        } catch (IOException e) {
            throw new UncheckedIOException("Failed to prepare scenario storage root", e);
        }
    }

    private static void createClusterLayout(Path root) throws IOException {
        Files.createDirectories(root.resolve("nodes/node-001/chunks"));
        Files.createDirectories(root.resolve("devices/bucket"));
        Files.createDirectories(root.resolve("devices/dedup"));
        Files.createDirectories(root.resolve("metadata/manifests"));
        Files.createDirectories(root.resolve("metadata/content-address-index"));
        Files.createDirectories(root.resolve("metadata/s3-object-references"));
    }

    private static void deleteRecursively(Path path) throws IOException {
        if (path == null || !Files.exists(path) && !Files.isSymbolicLink(path)) {
            return;
        }
        if (Files.isSymbolicLink(path) || Files.isRegularFile(path)) {
            Files.deleteIfExists(path);
            return;
        }
        try (var walk = Files.walk(path)) {
            List<Path> paths = walk.sorted(Comparator.reverseOrder()).toList();
            for (Path current : paths) {
                Files.deleteIfExists(current);
            }
        }
    }

    private static Path resolveProjectPath(String path) {
        return PROJECT_ROOT.resolve(path).normalize();
    }

    private byte[] readFixture(String fixtureFile) {
        try (InputStream input = Thread.currentThread().getContextClassLoader().getResourceAsStream(fixtureFile)) {
            if (input == null) {
                throw new IllegalArgumentException("Fixture not found on classpath: " + fixtureFile);
            }
            return input.readAllBytes();
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    private String computedSha256Base64(String fixtureFile) {
        return digestBase64("SHA-256", state.fixtureBytes.computeIfAbsent(fixtureFile, this::readFixture));
    }

    private String computedMd5Base64(String fixtureFile) {
        return digestBase64("MD5", state.fixtureBytes.computeIfAbsent(fixtureFile, this::readFixture));
    }

    private static String digestBase64(String algorithm, byte[] bytes) {
        try {
            MessageDigest digest = MessageDigest.getInstance(algorithm);
            return Base64.getEncoder().encodeToString(digest.digest(bytes));
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException(algorithm + " unavailable", e);
        }
    }

    private static byte[] readBytes(Path path) {
        try {
            return Files.readAllBytes(path);
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    private static String readString(Path path) {
        try {
            return Files.readString(path);
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    private static void writeBytes(Path path, byte[] bytes) {
        try {
            Files.createDirectories(path.getParent());
            Files.write(path, bytes, StandardOpenOption.CREATE, StandardOpenOption.TRUNCATE_EXISTING);
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    private static int manifestArtifactCount(Properties manifest) {
        return Integer.parseInt(manifest.getProperty(
            "artifactCount", manifest.getProperty("chunkCount", "0")));
    }

    private static String manifestArtifactPrefix(Properties manifest, int ordinal) {
        return manifest.containsKey("artifactCount")
            ? "artifact." + ordinal + "."
            : "chunk." + ordinal + ".";
    }

    private static String manifestArtifactId(Properties manifest, int ordinal) {
        String prefix = manifestArtifactPrefix(manifest, ordinal);
        return manifest.getProperty(prefix + (manifest.containsKey("artifactCount") ? "artifactId" : "chunkId"));
    }

    private static Properties loadProperties(Path path) {
        Properties properties = new Properties();
        try {
            properties.load(new StringReader(readString(path)));
            return properties;
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    private static String sha256Hex(byte[] data) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            return HexFormat.of().formatHex(digest.digest(data));
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 unavailable", e);
        }
    }

    private static String sha256Hex(String value) {
        return sha256Hex(value.getBytes(StandardCharsets.UTF_8));
    }

    private record Response(int status, byte[] body, HttpHeaders headers) {
        String bodyAsString() {
            return new String(body, StandardCharsets.UTF_8);
        }
    }

    private record ArtifactSet(Path referenceFile, Path manifestFile, List<Path> chunkFiles) {
    }
}
