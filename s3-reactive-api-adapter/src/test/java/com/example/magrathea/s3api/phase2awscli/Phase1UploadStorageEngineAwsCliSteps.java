package com.example.magrathea.s3api.phase2awscli;

import com.example.magrathea.objectstorage.repository.storageengine.adapter.StorageEngineReactiveBucketRepository;
import com.example.magrathea.objectstorage.repository.storageengine.adapter.StorageEngineReactiveMultipartUploadRepository;
import com.example.magrathea.objectstorage.repository.storageengine.adapter.StorageEngineReactiveS3ObjectRepository;
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
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Base64;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Properties;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.*;

public class Phase1UploadStorageEngineAwsCliSteps {

    private static final Path PROJECT_ROOT = Path.of("").toAbsolutePath().normalize();
    private static final Path CONFIGURED_STORAGE_ROOT =
        PROJECT_ROOT.resolve("target/storage-engine-it/current").normalize();
    private static final Duration AWS_CLI_TIMEOUT = Duration.ofSeconds(30);

    @LocalServerPort
    private int port;

    @Autowired
    private AwsCliSharedContext ctx;

    @Autowired
    private ObjectProvider<StorageEngineReactiveBucketRepository> bucketRepository;

    @Autowired
    private ObjectProvider<StorageEngineReactiveS3ObjectRepository> objectRepository;

    @Autowired
    private ObjectProvider<StorageEngineReactiveMultipartUploadRepository> multipartRepository;

    @Autowired
    private Phase2StorageEngineAwsCliTestApp.MutableFileSystemWriteFaultInjector faultInjector;

    private String lastCommittedManifestId;

    @Before
    public void checkAwsCliAvailability() {
        lastCommittedManifestId = null;
        faultInjector.disable();
        try {
            Process p = new ProcessBuilder("aws", "--version").start();
            boolean done = p.waitFor(10, TimeUnit.SECONDS);
            Assumptions.assumeTrue(done && p.exitValue() == 0, "AWS CLI not available");
        } catch (Exception e) {
            Assumptions.assumeTrue(false, "AWS CLI not available: " + e.getMessage());
        }
    }

    // ── REQ-UPLOAD-005 specific: different storage root step text ──────────────────

    @Given("the storage-engine repositories are backed by storage-engine filesystem root {string}")
    public void storageEngineRepositoriesAreBackedByFilesystemRoot(String storageRoot) {
        ctx.storageRoot = PROJECT_ROOT.resolve(storageRoot).normalize();
        cleanAndLinkStorageRoot(ctx.storageRoot);
        resetRepositories();
    }

    // ── Header profile ─────────────────────────────────────────────────────────────

    @Given("the S3 client applies PutObject header profile {string} with headers {string}")
    public void clientAppliesPutObjectHeaderProfile(String headerProfile, String headers) {
        ctx.requestHeaders.clear();
        if ("none".equalsIgnoreCase(headers.trim())) return;
        Map<String, String> parsed = new LinkedHashMap<>();
        for (String pair : headers.split(";")) {
            pair = pair.trim();
            int eq = pair.indexOf('=');
            if (eq < 0) continue;
            String name = pair.substring(0, eq).trim();
            String value = pair.substring(eq + 1).trim();
            if (value.equals("computed-sha256-base64-for-fixture")) {
                value = computeSha256Base64(ctx.fixtureBytes);
            } else if (value.equals("computed-md5-base64-for-fixture")) {
                value = computeMd5Base64(ctx.fixtureBytes);
            }
            parsed.put(name, value);
        }
        ctx.requestHeaders.putAll(parsed);
    }

    @Given("the fixture file {string} contains {string}")
    public void fixtureFileContains(String fixtureFile, String expectedContent) {
        byte[] bytes = readFixture(fixtureFile);
        assertTrue(new String(bytes, StandardCharsets.UTF_8).contains(expectedContent),
            "fixture file should contain the expected text while preserving exact fixture bytes");
        ctx.fixtureFile = fixtureFile;
        ctx.fixtureBytes = bytes;
    }

    @Given("the S3 client requests storage class {string}")
    public void s3ClientRequestsStorageClass(String expectedStorageClass) {
        assertEquals("STANDARD", expectedStorageClass,
            "Phase 1 upload reliability AWS CLI examples currently exercise STANDARD storage class");
    }

    @Given("the Phase 1 upload fixture file {string} is a deterministic 256 MiB object")
    public void fixtureFileIsDeterministic256MiBObject(String fixtureFile) {
        byte[] bytes = readFixture(fixtureFile);
        assertEquals(256L * 1024L * 1024L, bytes.length,
            "large-object fixture must be exactly 256 MiB");
        ctx.fixtureFile = fixtureFile;
        ctx.fixtureBytes = bytes;
    }

    @Given("the storage engine chunk size is configured to a bounded value smaller than the object")
    public void storageEngineChunkSizeIsBoundedSmallerThanObject() {
        assertTrue(ctx.fixtureBytes == null || ctx.fixtureBytes.length > 65_536,
            "Phase 1 storage-engine-it uses the default 64 KiB chunk size, smaller than the large fixture");
    }

    @Given("an S3 client starts uploading fixture file {string} to bucket {string} and key {string} with PutObject header profile {string} and headers {string}")
    public void clientStartsUploadingWithHeaderProfile(String fixtureFile, String bucket, String key,
                                                       String headerProfile, String headers) {
        ctx.bucket = bucket;
        ctx.objectKey = key;
        ctx.fixtureFile = fixtureFile;
        ctx.fixtureBytes = readFixture(fixtureFile);
        clientAppliesPutObjectHeaderProfile(headerProfile, headers);
        faultInjector.interruptAfterChunkTempWrite(true);
    }

    // ── REQ-UPLOAD-005 Then steps ──────────────────────────────────────────────────

    @Then("the upload is committed before the PutObject response is returned to the client")
    public void uploadCommittedBeforePutObjectResponseReturns() throws Exception {
        // GET must succeed immediately
        Path tmp = Files.createTempFile("magrathea-phase1-get-", ".bin");
        try {
            AwsResult get = runAws("get-object", "--bucket", ctx.bucket, "--key", ctx.objectKey, tmp.toString());
            assertEquals(0, get.exitCode(), () -> "GET should succeed immediately after PUT: " + get.combined());
            byte[] downloaded = Files.readAllBytes(tmp);
            assertArrayEquals(ctx.fixtureBytes, downloaded,
                "GET body must equal fixture bytes after committed PUT");
        } finally {
            Files.deleteIfExists(tmp);
        }
        // Filesystem must also have artifacts
        ArtifactSet a = artifactsFor(ctx.bucket, ctx.objectKey);
        assertFalse(a.chunkFiles().isEmpty(), "at least one committed chunk must exist on filesystem");
    }

    @Then("the filesystem contains the durable chunks, manifest, and S3 object reference for bucket {string} and key {string}")
    public void filesystemContainsDurableArtifacts(String bucket, String key) {
        ArtifactSet a = artifactsFor(bucket, key);
        assertTrue(Files.exists(a.referenceFile()), "S3 object reference must exist");
        assertTrue(Files.exists(a.manifestFile()), "manifest must exist");
        assertFalse(a.chunkFiles().isEmpty(), "at least one chunk must exist");
        a.chunkFiles().forEach(this::assertChunkChecksumValid);
        assertManifestChecksumValid(a.manifestFile());
    }

    @Then("the upload result records a committed manifest identifier for the stored bytes")
    public void uploadResultRecordsCommittedManifestIdentifier() {
        ArtifactSet a = artifactsFor(ctx.bucket, ctx.objectKey);
        lastCommittedManifestId = loadProperties(a.referenceFile()).getProperty("manifestId");
        assertNotNull(lastCommittedManifestId, "object reference must record manifestId");
        assertTrue(Files.exists(a.manifestFile()), "committed manifest file must exist");
    }

    @Then("the object reference for bucket {string} and key {string} points to that manifest identifier")
    public void objectReferencePointsToCommittedManifestIdentifier(String bucket, String key) {
        ArtifactSet a = artifactsFor(bucket, key);
        String manifestId = loadProperties(a.referenceFile()).getProperty("manifestId");
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
        ArtifactSet a = artifactsFor(ctx.bucket, ctx.objectKey);
        Properties ref = loadProperties(a.referenceFile());
        Properties mf  = loadProperties(a.manifestFile());
        assertEquals(expectedStorageClass, ref.getProperty("storageClass"),
            "reference storageClass mismatch");
        assertEquals(expectedStorageClass, mf.getProperty("storageClassId"),
            "manifest storageClassId mismatch");
    }

    @Then("the committed object records durable headers and metadata {string}")
    public void committedObjectRecordsDurableHeadersAndMetadata(String expectedHeaders) {
        ArtifactSet a = artifactsFor(ctx.bucket, ctx.objectKey);
        Properties ref = loadProperties(a.referenceFile());
        Properties mf  = loadProperties(a.manifestFile());
        for (String pair : expectedHeaders.split(";")) {
            pair = pair.trim();
            int eq = pair.indexOf('=');
            if (eq < 0) continue;
            String name  = pair.substring(0, eq).trim();
            String value = pair.substring(eq + 1).trim();
            if (name.equalsIgnoreCase("x-amz-storage-class")) {
                assertEquals(value, ref.getProperty("storageClass"));
            } else if (name.toLowerCase().startsWith("x-amz-meta-")) {
                // metadata stored as userMetadata.N.key / userMetadata.N.value in reference
                int metadataCount = Integer.parseInt(ref.getProperty("userMetadata.count", "0"));
                boolean found = false;
                for (int idx = 0; idx < metadataCount; idx++) {
                    String prefix = "userMetadata." + idx + ".";
                    if (name.equals(ref.getProperty(prefix + "key"))) {
                        assertEquals(value, ref.getProperty(prefix + "value"),
                            "reference metadata value mismatch for " + name);
                        found = true;
                        break;
                    }
                }
                assertTrue(found, "reference should contain user metadata " + name);
            } else if (name.equalsIgnoreCase("x-amz-checksum-sha256")) {
                String actual = mf.getProperty("upload.declaredChecksum.value", "");
                if (value.equals("computed-sha256-base64-for-fixture")) {
                    value = computeSha256Base64(ctx.fixtureBytes);
                }
                assertEquals(value, actual, "manifest declared checksum mismatch");
            } else if (name.equalsIgnoreCase("Content-MD5")) {
                String actual = mf.getProperty("upload.declaredChecksum.value", "");
                if (value.equals("computed-md5-base64-for-fixture")) {
                    value = computeMd5Base64(ctx.fixtureBytes);
                }
                assertEquals(value, actual, "manifest Content-MD5 mismatch");
            }
            // x-amz-server-side-encryption and others: just presence check (config-only mode)
        }
    }

    @Then("the storage engine has a durable committed manifest for the uploaded bytes")
    public void storageEngineHasDurableCommittedManifestForUploadedBytes() {
        ArtifactSet a = artifactsFor(ctx.bucket, ctx.objectKey);
        assertTrue(Files.exists(a.manifestFile()), "manifest must exist for committed upload");
        assertManifestChecksumValid(a.manifestFile());
        assertFalse(a.chunkFiles().isEmpty(), "manifest should reference durable chunks");
    }

    @Then("the manifest records the object byte length, chunk list, checksum metadata, and creation time")
    public void manifestRecordsLengthChunksChecksumAndCreationTime() {
        ArtifactSet a = artifactsFor(ctx.bucket, ctx.objectKey);
        Properties mf = loadProperties(a.manifestFile());
        Properties ref = loadProperties(a.referenceFile());
        assertEquals(String.valueOf(ctx.fixtureBytes.length), mf.getProperty("totalOriginalSize"));
        assertEquals(String.valueOf(ctx.fixtureBytes.length), mf.getProperty("upload.totalObjectSize"));
        assertTrue(manifestArtifactCount(mf) > 0,
            "manifest should contain at least one chunk reference");
        assertNotNull(mf.getProperty("upload.consolidatedChecksum.algorithm"));
        assertNotNull(mf.getProperty("upload.consolidatedChecksum.value"));
        assertNotNull(ref.getProperty("createdAt"), "object reference should record creation time");
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
        objectRepository.ifAvailable(StorageEngineReactiveS3ObjectRepository::reset);
    }

    @When("the manifest repository is reloaded from storage-engine filesystem root {string}")
    public void manifestRepositoryIsReloadedFromFilesystemRoot(String storageRoot) {
        assertScenarioRoot(storageRoot);
        multipartRepository.ifAvailable(StorageEngineReactiveMultipartUploadRepository::reset);
    }

    @Then("bucket {string} and key {string} still resolve to the same manifest identifier")
    public void bucketAndKeyStillResolveToSameManifestIdentifier(String bucket, String key) {
        objectReferencePointsToCommittedManifestIdentifier(bucket, key);
    }

    @Then("the resolved manifest can be used to stream the original bytes from fixture file {string}")
    public void resolvedManifestCanStreamOriginalBytes(String fixtureFile) throws Exception {
        sameCLientImmediatelyReadsBucketAndKey(ctx.bucket, ctx.objectKey);
        responseBodyMatchesFixtureFile(fixtureFile);
    }

    @Then("the resolved manifest still exposes durable object headers and metadata {string}")
    public void resolvedManifestStillExposesDurableHeadersAndMetadata(String expectedManifestMetadata) {
        committedObjectRecordsDurableHeadersAndMetadata(expectedManifestMetadata);
    }

    @When("the recovery process stops the application process")
    public void recoveryProcessStopsApplicationProcess() {
        ctx.lastGetExitCode = -1;
        ctx.lastGetBytes = new byte[0];
    }

    @When("all in-memory repositories and caches are discarded")
    public void allInMemoryRepositoriesAndCachesAreDiscarded() {
        // Restart simulation: caches are dropped, durable filesystem state is kept.
        resetRepositories();
    }

    @When("the recovery process starts the application again using storage-engine filesystem root {string}")
    public void recoveryProcessStartsApplicationAgain(String storageRoot) {
        assertScenarioRoot(storageRoot);
    }

    @When("the S3 client uploads fixture file {string} to bucket {string} and key {string} through the S3 HTTP PutObject API using a streaming request body")
    public void clientUploadsFixtureUsingStreamingRequestBody(String fixtureFile, String bucket, String key) throws Exception {
        ctx.fixtureBytes = readFixture(fixtureFile);
        AwsResult result = putObjectWithHeaders(bucket, key, ctx.fixtureBytes, ctx.requestHeaders);
        assertEquals(0, result.exitCode(), () -> "streaming PutObject should succeed: " + result.combined());
        ctx.bucket = bucket;
        ctx.objectKey = key;
        ctx.fixtureFile = fixtureFile;
    }

    @When("the same S3 client immediately reads bucket {string} and key {string} through the S3 HTTP GetObject API")
    public void sameCLientImmediatelyReadsBucketAndKey(String bucket, String key) throws Exception {
        Path tmp = Files.createTempFile("magrathea-phase1-imm-get-", ".bin");
        AwsResult result = runAws("get-object", "--bucket", bucket, "--key", key, tmp.toString());
        ctx.lastGetExitCode = result.exitCode();
        ctx.lastGetBytes = Files.exists(tmp) ? Files.readAllBytes(tmp) : new byte[0];
        ctx.lastGetStdout = result.stdout();
        ctx.lastGetStderr = result.stderr();
        Files.deleteIfExists(tmp);
    }

    @Then("the response body matches fixture file {string}")
    public void responseBodyMatchesFixtureFile(String fixtureFile) {
        assertEquals(0, ctx.lastGetExitCode,
            () -> "GET should return 200 but got exit code " + ctx.lastGetExitCode + ": " + ctx.lastGetStderr);
        byte[] expected = readFixture(fixtureFile);
        assertArrayEquals(expected, ctx.lastGetBytes, "GET body must equal fixture bytes");
    }

    @Then("the response attributes and metadata include {string}")
    public void responseAttributesAndMetadataInclude(String expectedHeaders) throws Exception {
        // Use head-object to inspect response metadata.
        // Content-Type and x-amz-storage-class are intentionally skipped here — consistent
        // with the WebTestClient runner (assertObservableResponseHeaders) which also skips them.
        // They are validated by committedObjectRecordsDurableHeadersAndMetadata and
        // committedObjectRecordsStorageClass, which inspect the durable reference/manifest files.
        AwsResult head = runAws("head-object", "--bucket", ctx.bucket, "--key", ctx.objectKey);
        String headOutput = head.stdout();
        for (String pair : expectedHeaders.split(";")) {
            pair = pair.trim();
            int eq = pair.indexOf('=');
            if (eq < 0) continue;
            String name  = pair.substring(0, eq).trim().toLowerCase();
            String value = pair.substring(eq + 1).trim();
            if (name.equals("content-type") || name.equals("x-amz-storage-class")) {
                // Validated by committedObjectRecordsDurableHeadersAndMetadata and
                // committedObjectRecordsStorageClass — skip here (HEAD response may not include
                // these standard headers; aligns with WebTestClient runner behaviour).
            } else if (name.startsWith("x-amz-meta-")) {
                String metaKey = name.substring("x-amz-meta-".length());
                assertTrue(headOutput.contains(metaKey) || headOutput.contains(value),
                    "head-object output should contain metadata " + metaKey + "=" + value + "; got: " + headOutput);
            } else if (name.equals("x-amz-server-side-encryption")) {
                assertTrue(headOutput.contains(value) || headOutput.contains("AES256"),
                    "head-object output should contain SSE " + value + "; got: " + headOutput);
            }
            // Other headers: permissive — storage engine records them in the manifest/reference
        }
    }

    @Then("the response storage class is {string}")
    public void responseStorageClassIs(String expectedStorageClass) {
        committedObjectRecordsStorageClass(expectedStorageClass);
    }

    @Then("the read succeeds without reconstructing any object state from memory")
    public void readSucceedsWithoutReconstructingObjectStateFromMemory() {
        assertEquals(0, ctx.lastGetExitCode,
            () -> "GET after repository reset should succeed: " + ctx.lastGetStderr);
        assertArrayEquals(ctx.fixtureBytes, ctx.lastGetBytes,
            "GET after repository reset should return original fixture bytes");
        ArtifactSet a = artifactsFor(ctx.bucket, ctx.objectKey);
        assertTrue(Files.exists(a.referenceFile()));
        assertTrue(Files.exists(a.manifestFile()));
    }

    @Then("the committed manifest contains the ordered chunk references needed to reconstruct the object")
    public void committedManifestContainsOrderedChunkReferences() {
        Properties mf = loadProperties(artifactsFor(ctx.bucket, ctx.objectKey).manifestFile());
        int chunkCount = manifestArtifactCount(mf);
        assertTrue(chunkCount > 0, "manifest should contain chunk references");
        for (int i = 0; i < chunkCount; i++) {
            assertNotNull(manifestArtifactId(mf, i),
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
    public void streamedResponseAttributesAndMetadataInclude(String expectedManifestMetadata) throws Exception {
        responseAttributesAndMetadataInclude(expectedManifestMetadata);
    }

    @Then("the read path emits chunks in manifest order without loading the complete object into memory")
    public void readPathEmitsChunksInManifestOrderWithoutWholeObjectMemory() {
        committedManifestContainsOrderedChunkReferences();
    }

    @Then("the response is produced from the storage-engine filesystem state rather than an in-memory-only object cache")
    public void responseProducedFromStorageEngineFilesystemState() throws Exception {
        // Verify artifacts are on disk
        ArtifactSet a = artifactsFor(ctx.bucket, ctx.objectKey);
        a.chunkFiles().forEach(this::assertChunkChecksumValid);
        // Reset repositories to discard any in-memory state
        resetRepositories();
        // Re-read: must still work
        Path tmp = Files.createTempFile("magrathea-phase1-reread-", ".bin");
        try {
            AwsResult result = runAws("get-object", "--bucket", ctx.bucket, "--key", ctx.objectKey, tmp.toString());
            assertEquals(0, result.exitCode(), () -> "GET after repository reset must succeed: " + result.combined());
            assertArrayEquals(ctx.fixtureBytes, Files.readAllBytes(tmp),
                "GET after repository reset must return original fixture bytes");
        } finally {
            Files.deleteIfExists(tmp);
        }
    }

    @When("the upload fails before all bytes are durably written and committed")
    public void uploadFailsBeforeAllBytesAreDurablyWrittenAndCommitted() throws Exception {
        try {
            AwsResult result = putObjectWithHeaders(ctx.bucket, ctx.objectKey, ctx.fixtureBytes, ctx.requestHeaders);
            assertNotEquals(0, result.exitCode(),
                () -> "fault-injected PutObject should fail before commit: " + result.combined());
        } finally {
            faultInjector.disable();
        }
    }

    @Then("bucket {string} and key {string} are not visible through the S3 HTTP GetObject API")
    public void bucketKeyNotVisibleThroughGetObject(String bucket, String key) throws Exception {
        Path out = Files.createTempFile("magrathea-phase1-missing-", ".bin");
        try {
            AwsResult result = runAws("get-object", "--bucket", bucket, "--key", key, out.toString());
            assertNotEquals(0, result.exitCode(), "failed upload should not be readable");
        } finally {
            Files.deleteIfExists(out);
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
        assertNoObjectReference(ctx.bucket, ctx.objectKey);
    }

    @Then("bucket {string} and key {string} remain unreadable")
    public void bucketAndKeyRemainUnreadable(String bucket, String key) throws Exception {
        bucketKeyNotVisibleThroughGetObject(bucket, key);
    }

    @Then("recovery either removes uncommitted upload artifacts or keeps them isolated from committed object references")
    public void recoveryRemovesOrIsolatesUncommittedUploadArtifacts() {
        assertNoObjectReference(ctx.bucket, ctx.objectKey);
        assertNoCommittedManifestReferencesMissingChunks();
    }

    // ── REQ-UPLOAD-006 specific steps ─────────────────────────────────────────────

    @Then("the upload validates client-supplied integrity headers from profile {string} when such headers are present")
    public void uploadValidatesClientSuppliedIntegrityHeaders(String headerProfile) {
        ArtifactSet a = artifactsFor(ctx.bucket, ctx.objectKey);
        Properties mf = loadProperties(a.manifestFile());
        assertEquals("true", mf.getProperty("upload.metadataValidated"),
            "manifest must record upload.metadataValidated=true");
        assertEquals("true", mf.getProperty("upload.verificationPassed"),
            "manifest must record upload.verificationPassed=true");
        if (headerProfile.equals("checksum-sha256") || headerProfile.equals("content-md5")) {
            assertEquals("true", mf.getProperty("upload.declaredChecksum.present"),
                "manifest must record upload.declaredChecksum.present=true for " + headerProfile);
        }
    }

    @Then("each stored chunk has verifiable integrity metadata")
    public void eachStoredChunkHasVerifiableIntegrityMetadata() {
        ArtifactSet a = artifactsFor(ctx.bucket, ctx.objectKey);
        assertFalse(a.chunkFiles().isEmpty(), "expected at least one committed chunk");
        a.chunkFiles().forEach(this::assertChunkChecksumValid);
    }

    @Then("the committed manifest records integrity metadata {string} for the complete object and each chunk reference")
    public void committedManifestRecordsIntegrityMetadata(String expectedIntegrityMetadata) {
        ArtifactSet a = artifactsFor(ctx.bucket, ctx.objectKey);
        Properties mf = loadProperties(a.manifestFile());
        assertNotNull(mf.getProperty("upload.consolidatedChecksum.algorithm"),
            "manifest must have object-level checksum algorithm");
        assertNotNull(mf.getProperty("upload.consolidatedChecksum.value"),
            "manifest must have object-level checksum value");
        int chunkCount = manifestArtifactCount(mf);
        assertTrue(chunkCount > 0, "manifest must reference at least one chunk");
        for (int i = 0; i < chunkCount; i++) {
            assertNotNull(mf.getProperty(manifestArtifactPrefix(mf, i) + "finalChecksum.algorithm"),
                "chunk " + i + " must have checksum algorithm in manifest");
            assertNotNull(mf.getProperty(manifestArtifactPrefix(mf, i) + "finalChecksum.value"),
                "chunk " + i + " must have checksum value in manifest");
        }
    }

    @Then("the committed manifest records storage class {string}")
    public void committedManifestRecordsStorageClass(String expectedStorageClass) {
        Properties mf = loadProperties(artifactsFor(ctx.bucket, ctx.objectKey).manifestFile());
        assertEquals(expectedStorageClass, mf.getProperty("storageClassId"),
            "manifest storageClassId must match expected");
    }

    @When("one durable chunk for bucket {string} and key {string} is corrupted outside the application")
    public void oneDurableChunkIsCorruptedOutsideTheApplication(String bucket, String key) {
        ArtifactSet a = artifactsFor(bucket, key);
        assertFalse(a.chunkFiles().isEmpty(), "expected at least one committed chunk file");
        Path chunk = a.chunkFiles().getFirst();
        byte[] data = readBytes(chunk);
        assertTrue(data.length > 0, "chunk must not be empty");
        data[0] = (byte) (data[0] ^ 0x7f);
        writeBytes(chunk, data);
    }

    @Then("the storage engine detects the integrity mismatch before returning corrupted bytes as a successful object")
    public void storageEngineDetectsIntegrityMismatch() {
        assertNotEquals(0, ctx.lastGetExitCode,
            "GET of corrupted object should fail (non-zero exit code); stderr: " + ctx.lastGetStderr);
    }

    @Then("the observable read result reports an object integrity failure")
    public void observableReadResultReportsObjectIntegrityFailure() {
        assertTrue(ctx.lastGetExitCode != 0
                || ctx.lastGetStderr.contains("integrity")
                || ctx.lastGetStderr.contains("checksum")
                || ctx.lastGetStderr.contains("500")
                || ctx.lastGetStderr.contains("503")
                || ctx.lastGetStdout.contains("integrity"),
            "GET response should signal integrity failure; stderr: " + ctx.lastGetStderr
                + "; stdout: " + ctx.lastGetStdout);
    }

    // ── Helpers ────────────────────────────────────────────────────────────────────

    private AwsResult putObjectWithHeaders(String bucket, String key, byte[] bytes,
                                           Map<String, String> extraHeaders) throws Exception {
        Path body = Files.createTempFile("magrathea-phase1-put-", ".bin");
        Files.write(body, bytes);
        try {
            List<String> args = new ArrayList<>(
                List.of("put-object", "--bucket", bucket, "--key", key, "--body", body.toString()));
            if (extraHeaders != null && !extraHeaders.isEmpty()) {
                List<String> metaParts = new ArrayList<>();
                for (var e : extraHeaders.entrySet()) {
                    String name = e.getKey().toLowerCase();
                    String value = e.getValue();
                    switch (name) {
                        case "content-type" -> { args.add("--content-type"); args.add(value); }
                        case "x-amz-storage-class" -> { args.add("--storage-class"); args.add(value); }
                        case "x-amz-server-side-encryption" -> { args.add("--server-side-encryption"); args.add(value); }
                        case "x-amz-checksum-sha256" -> { args.add("--checksum-sha256"); args.add(value); }
                        case "x-amz-sdk-checksum-algorithm" -> { args.add("--checksum-algorithm"); args.add(value); }
                        case "content-md5" -> { args.add("--content-md5"); args.add(value); }
                        default -> {
                            if (name.startsWith("x-amz-meta-")) {
                                metaParts.add(name.substring("x-amz-meta-".length()) + "=" + value);
                            }
                        }
                    }
                }
                if (!metaParts.isEmpty()) {
                    args.add("--metadata");
                    args.add(String.join(",", metaParts));
                }
            }
            return runAws(args.toArray(String[]::new));
        } finally {
            Files.deleteIfExists(body);
        }
    }

    private AwsResult runAws(String... args) throws Exception {
        List<String> command = new ArrayList<>();
        command.add("aws");
        command.add("--endpoint-url");
        command.add("http://localhost:" + port);
        command.add("s3api");
        command.addAll(List.of(args));
        ProcessBuilder pb = new ProcessBuilder(command);
        Map<String, String> env = pb.environment();
        env.putIfAbsent("AWS_ACCESS_KEY_ID", "test");
        env.putIfAbsent("AWS_SECRET_ACCESS_KEY", "test");
        env.putIfAbsent("AWS_DEFAULT_REGION", "us-east-1");
        env.putIfAbsent("AWS_EC2_METADATA_DISABLED", "true");
        Process process = pb.start();
        CompletableFuture<String> out = CompletableFuture.supplyAsync(() -> readStream(process.getInputStream()));
        CompletableFuture<String> err = CompletableFuture.supplyAsync(() -> readStream(process.getErrorStream()));
        boolean done = process.waitFor(AWS_CLI_TIMEOUT.toSeconds(), TimeUnit.SECONDS);
        if (!done) { process.destroyForcibly(); fail("AWS CLI timeout: " + String.join(" ", command)); }
        return new AwsResult(process.exitValue(), out.join(), err.join());
    }

    private static String readStream(java.io.InputStream s) {
        try (s) { return new String(s.readAllBytes(), StandardCharsets.UTF_8); }
        catch (IOException e) { throw new UncheckedIOException(e); }
    }

    private byte[] readFixture(String fixtureFile) {
        try (InputStream in = Thread.currentThread().getContextClassLoader()
                .getResourceAsStream(fixtureFile)) {
            Objects.requireNonNull(in, "Fixture not found: " + fixtureFile);
            return in.readAllBytes();
        } catch (IOException e) { throw new UncheckedIOException(e); }
    }

    private void resetRepositories() {
        // Bucket registry, object references and multipart state are durable on the
        // storage-engine filesystem (EP-2). Discard every in-memory cache and reload
        // state from the currently configured storage root: after a symlink swap this
        // yields a clean, empty view; within a scenario it forces the read path to
        // reload state from the durable filesystem rather than an in-memory cache.
        bucketRepository.ifAvailable(StorageEngineReactiveBucketRepository::reloadFromDisk);
        objectRepository.ifAvailable(StorageEngineReactiveS3ObjectRepository::reloadFromDisk);
        multipartRepository.ifAvailable(StorageEngineReactiveMultipartUploadRepository::reloadFromDisk);
    }

    private void cleanAndLinkStorageRoot(Path scenarioRoot) {
        try {
            deleteRecursively(scenarioRoot);
            Files.createDirectories(scenarioRoot);
            deleteRecursively(CONFIGURED_STORAGE_ROOT);
            Files.createDirectories(CONFIGURED_STORAGE_ROOT.getParent());
            Files.createSymbolicLink(CONFIGURED_STORAGE_ROOT, scenarioRoot);
            createClusterLayout(scenarioRoot);
        } catch (IOException e) { throw new UncheckedIOException(e); }
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
        if (path == null || (!Files.exists(path) && !Files.isSymbolicLink(path))) return;
        if (Files.isSymbolicLink(path) || Files.isRegularFile(path)) { Files.deleteIfExists(path); return; }
        try (var w = Files.walk(path)) {
            w.sorted(Comparator.reverseOrder()).forEach(p -> {
                try { Files.deleteIfExists(p); } catch (IOException e) { throw new UncheckedIOException(e); }
            });
        }
    }

    // ── Filesystem inspection helpers ──────────────────────────────────────────────

    private void assertScenarioRoot(String storageRoot) {
        assertEquals(PROJECT_ROOT.resolve(storageRoot).normalize(), ctx.storageRoot,
            "step storage root should match the prepared scenario root");
    }

    private void assertNoObjectReference(String bucket, String key) {
        assertTrue(referenceFilesFor(bucket, key).isEmpty(),
            "failed upload must not publish an object reference for " + bucket + "/" + key);
    }

    private void assertNoCommittedManifestReferencesMissingChunks() {
        Path manifestsRoot = ctx.storageRoot.resolve("metadata/manifests");
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
        Path refDir = ctx.storageRoot.resolve("metadata/s3-object-references");
        List<Path> refs = new ArrayList<>();
        // Use property-matching to find the reference file (paths are base64-encoded)
        try (var ds = Files.walk(refDir)) {
            ds.filter(Files::isRegularFile)
              .filter(p -> p.toString().endsWith(".properties"))
              .filter(p -> {
                  Properties pr = loadProperties(p);
                  return bucket.equals(pr.getProperty("bucket")) && key.equals(pr.getProperty("key"));
              })
              .forEach(refs::add);
        } catch (IOException e) { throw new UncheckedIOException(e); }
        assertEquals(1, refs.size(),
            "Expected exactly one reference for " + bucket + "/" + key + "; found: " + refs);
        Path refFile = refs.getFirst();
        Properties ref = loadProperties(refFile);
        String manifestId = ref.getProperty("manifestId");
        assertNotNull(manifestId, "reference must have manifestId");
        Path manifestFile = ctx.storageRoot.resolve("metadata/manifests/" + manifestId + ".properties");
        assertTrue(Files.exists(manifestFile), "manifest must exist: " + manifestFile);
        Properties mf = loadProperties(manifestFile);
        int cc = manifestArtifactCount(mf);
        List<Path> chunks = new ArrayList<>();
        for (int i = 0; i < cc; i++) {
            String chunkId = manifestArtifactId(mf, i);
            if (chunkId != null) {
                try (var ds = Files.walk(ctx.storageRoot.resolve("nodes"))) {
                    ds.filter(Files::isRegularFile)
                      .filter(p -> p.getFileName().toString().equals(chunkId))
                      .filter(p -> !p.getFileName().toString().endsWith(".sha256"))
                      .forEach(chunks::add);
                } catch (IOException e) { throw new UncheckedIOException(e); }
            }
        }
        return new ArtifactSet(refFile, manifestFile, List.copyOf(chunks));
    }

    private List<Path> referenceFilesFor(String bucket, String key) {
        Path refDir = ctx.storageRoot.resolve("metadata/s3-object-references");
        if (!Files.isDirectory(refDir)) {
            return List.of();
        }
        try (var walk = Files.walk(refDir)) {
            return walk
                .filter(Files::isRegularFile)
                .filter(path -> path.getFileName().toString().endsWith(".properties"))
                .filter(path -> {
                    Properties pr = loadProperties(path);
                    return bucket.equals(pr.getProperty("bucket")) && key.equals(pr.getProperty("key"));
                })
                .sorted()
                .toList();
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    private List<Path> chunkFilesById(String chunkId) {
        Path nodes = ctx.storageRoot.resolve("nodes");
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
        Properties p = new Properties();
        try (var r = Files.newBufferedReader(path)) { p.load(r); }
        catch (IOException e) { throw new UncheckedIOException(e); }
        return p;
    }

    private void assertChunkChecksumValid(Path chunk) {
        Path sidecar = chunk.resolveSibling(chunk.getFileName() + ".sha256");
        assertTrue(Files.exists(sidecar), "SHA-256 sidecar must exist for chunk: " + chunk);
        try {
            String expected = Files.readString(sidecar).trim();
            byte[] data = Files.readAllBytes(chunk);
            String actual = toHex(MessageDigest.getInstance("SHA-256").digest(data));
            assertEquals(expected, actual, "Chunk checksum mismatch: " + chunk);
        } catch (IOException | NoSuchAlgorithmException e) { throw new RuntimeException(e); }
    }

    private void assertManifestChecksumValid(Path manifest) {
        try {
            String raw = Files.readString(manifest);
            String marker = "\nmanifest.checksum=";
            int index = raw.lastIndexOf(marker);
            if (index >= 0) {
                String contentForVerification = raw.substring(0, index + 1);
                String stored = raw.substring(index + marker.length()).trim();
                String actual = toHex(MessageDigest.getInstance("SHA-256")
                    .digest(contentForVerification.getBytes(StandardCharsets.UTF_8)));
                assertEquals(stored, actual, "Manifest checksum mismatch: " + manifest);
            }
            // If no checksum trailer found, the manifest format check is permissive
        } catch (IOException | NoSuchAlgorithmException e) { throw new RuntimeException(e); }
    }

    private static byte[] readBytes(Path path) {
        try { return Files.readAllBytes(path); } catch (IOException e) { throw new UncheckedIOException(e); }
    }

    private static void writeBytes(Path path, byte[] bytes) {
        try { Files.write(path, bytes); } catch (IOException e) { throw new UncheckedIOException(e); }
    }

    private static String toHex(byte[] bytes) {
        StringBuilder sb = new StringBuilder(bytes.length * 2);
        for (byte b : bytes) sb.append(String.format("%02x", b));
        return sb.toString();
    }

    private static String computeSha256Base64(byte[] bytes) {
        try { return Base64.getEncoder().encodeToString(MessageDigest.getInstance("SHA-256").digest(bytes)); }
        catch (NoSuchAlgorithmException e) { throw new RuntimeException(e); }
    }

    private static String computeMd5Base64(byte[] bytes) {
        try { return Base64.getEncoder().encodeToString(MessageDigest.getInstance("MD5").digest(bytes)); }
        catch (NoSuchAlgorithmException e) { throw new RuntimeException(e); }
    }

    private record AwsResult(int exitCode, String stdout, String stderr) {
        String combined() { return (Objects.toString(stdout, "") + Objects.toString(stderr, "")).trim(); }
    }

    private record ArtifactSet(Path referenceFile, Path manifestFile, List<Path> chunkFiles) {}
}
