package com.example.magrathea.s3api.cucumber.requirements;

import com.example.magrathea.objectstorage.repository.storageengine.adapter.StorageEngineReactiveBucketRepository;
import com.example.magrathea.objectstorage.repository.storageengine.adapter.StorageEngineReactiveMultipartUploadRepository;
import com.example.magrathea.objectstorage.repository.storageengine.adapter.StorageEngineReactiveS3ObjectRepository;
import com.example.magrathea.storageengine.infrastructure.filesystem.FileSystemRecoveryScanner;
import io.cucumber.java.Before;
import io.cucumber.java.en.Given;
import io.cucumber.java.en.Then;
import io.cucumber.java.en.When;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
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
import java.time.ZonedDateTime;
import java.util.ArrayList;
import java.util.Base64;
import java.util.Comparator;
import java.util.HexFormat;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Properties;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;

import static org.junit.jupiter.api.Assertions.*;

public class Phase2FilesystemReliabilitySteps {

    private static final Path PROJECT_ROOT = Path.of("").toAbsolutePath().normalize();
    private static final Path CONFIGURED_STORAGE_ROOT = PROJECT_ROOT.resolve("target/storage-engine-it/current").normalize();

    @Autowired
    private WebTestClient webTestClient;

    @Autowired
    private FileSystemRecoveryScanner recoveryScanner;

    @Autowired
    private State state;

    @Autowired
    private RequirementsTestApp.MutableFileSystemWriteFaultInjector faultInjector;

    @Autowired
    private ObjectProvider<StorageEngineReactiveBucketRepository> bucketRepository;

    @Autowired
    private ObjectProvider<StorageEngineReactiveS3ObjectRepository> objectRepository;

    @Autowired
    private ObjectProvider<StorageEngineReactiveMultipartUploadRepository> multipartRepository;

    @Before
    public void resetState() {
        state.resetVolatileState();
        faultInjector.disable();
    }

    @Given("the S3 API is configured with profile {string} and backend {string}")
    public void s3ApiIsConfiguredWithProfileAndBackend(String profile, String backend) {
        assertEquals("storage-engine-it", profile);
        assertEquals("storage-engine", backend);
    }

    @Given("the storage engine stores bytes, manifests, and object references on a real filesystem")
    public void storageEngineStoresOnRealFilesystem() {
        assertTrue(Files.exists(CONFIGURED_STORAGE_ROOT) || Files.exists(CONFIGURED_STORAGE_ROOT.getParent()),
            "storage-engine filesystem root parent should exist or be creatable");
    }

    @Given("each scenario uses a clean storage-engine filesystem root {string}")
    public void eachScenarioUsesCleanStorageEngineFilesystemRoot(String storageRootPattern) {
        assertTrue(storageRootPattern.contains("<scenario-id>"),
            "background documents the per-scenario root pattern");
    }

    @Given("validation mode {string} is selected for requirement {string}")
    public void validationModeIsSelectedForRequirement(String validationMode, String requirementId) {
        assertEquals("webclient", validationMode, "this runner executes only WebTestClient examples");
        state.requirementId = requirementId;
    }

    @Given("the storage engine operator uses filesystem root {string}")
    public void operatorUsesFilesystemRoot(String storageRoot) {
        state.storageRoot = resolveProjectPath(storageRoot);
        cleanScenarioRootAndPointConfiguredSymlink(state.storageRoot);
        resetRepositories();
    }

    @Given("bucket {string} exists")
    public void bucketExists(String bucket) {
        putBucket(bucket);
        state.bucket = bucket;
    }

    @Given("no object exists in bucket {string} for key {string}")
    public void noObjectExists(String bucket, String key) {
        var result = getObject(bucket, key);
        assertEquals(404, result.status(), "object should not exist before scenario action");
    }

    @Given("no objects exist for key {string} and key {string} in bucket {string}")
    public void noObjectsExistForKeys(String keyA, String keyB, String bucket) {
        assertEquals(404, getObject(bucket, keyA).status(), "first object should not exist before scenario action");
        assertEquals(404, getObject(bucket, keyB).status(), "second object should not exist before scenario action");
    }

    @Given("an S3 client has object content from fixture file {string} for bucket {string} and key {string}")
    public void clientHasObjectContent(String fixtureFile, String bucket, String key) {
        state.fixtureBytes.put(fixtureFile, readFixture(fixtureFile));
        state.bucket = bucket;
        state.objectKey = key;
        state.fixtureFile = fixtureFile;
    }

    @Given("the storage engine is configured to write chunks via temp-file-then-rename in filesystem root {string}")
    public void storageEngineWritesChunksViaTempFileThenRename(String storageRoot) {
        assertScenarioRoot(storageRoot);
        faultInjector.interruptAfterChunkTempWrite(true);
        state.expectedFault = ExpectedFault.CHUNK_TEMP_WRITE;
    }

    @Given("the storage engine is configured to write manifests via temp-file-then-rename in filesystem root {string}")
    public void storageEngineWritesManifestsViaTempFileThenRename(String storageRoot) {
        assertScenarioRoot(storageRoot);
        faultInjector.interruptAfterManifestTempWrite(true);
        state.expectedFault = ExpectedFault.MANIFEST_TEMP_WRITE;
    }

    @Given("a valid committed object exists in bucket {string} at key {string} uploaded from fixture file {string}")
    public void validCommittedObjectExists(String bucket, String key, String fixtureFile) {
        state.fixtureBytes.put(fixtureFile, readFixture(fixtureFile));
        state.bucket = bucket;
        state.objectKey = key;
        state.fixtureFile = fixtureFile;
        var put = putObject(bucket, key, state.fixtureBytes.get(fixtureFile));
        assertEquals(200, put.status(), put.bodyAsString());
        state.validObjectArtifacts = artifactsFor(bucket, key);
        state.validObjectHashes = hashArtifacts(state.validObjectArtifacts.allExistingPaths());
    }

    @Given("the committed object is readable through the S3 HTTP GetObject API and returns the exact bytes from fixture file {string} before the scanner runs")
    public void committedObjectReadableBeforeScanner(String fixtureFile) {
        Response response = getObject(state.bucket, state.objectKey);
        assertEquals(200, response.status(), response.bodyAsString());
        assertArrayEquals(state.fixtureBytes.get(fixtureFile), response.body());
    }

    @Given("orphaned chunk files from a failed upload exist in the chunk directory of filesystem root {string} with no committed manifest referencing them")
    public void orphanedChunkFilesExist(String storageRoot) {
        assertScenarioRoot(storageRoot);
        Path orphan = state.storageRoot.resolve("nodes/node-001/chunks/orphaned-" + UUID.randomUUID() + ".tmp." + UUID.randomUUID());
        writeBytes(orphan, "orphaned partial bytes".getBytes(StandardCharsets.UTF_8));
        state.orphanedChunkPath = orphan;
    }

    @Given("a final checksum sidecar without its corresponding committed chunk exists in filesystem root {string}")
    public void checksumSidecarWithoutCommittedChunkExists(String storageRoot) {
        assertScenarioRoot(storageRoot);
        String chunkId = UUID.randomUUID().toString();
        Path sidecar = state.storageRoot.resolve("nodes/node-001/chunks/" + chunkId + ".sha256");
        writeString(sidecar, "a".repeat(64));
        state.orphanedChecksumSidecarPath = sidecar;
    }

    @Given("an incomplete manifest with at least one missing required field exists in the manifest directory of filesystem root {string}")
    public void incompleteManifestExists(String storageRoot) {
        assertScenarioRoot(storageRoot);
        Path manifest = state.storageRoot.resolve("metadata/manifests/incomplete-" + UUID.randomUUID() + ".properties");
        writeString(manifest, "objectId=incomplete-object\nchunkCount=1\n");
        state.incompleteManifestPath = manifest;
    }

    @Given("a broken object reference pointing to an absent manifest exists in the object reference directory of filesystem root {string}")
    public void brokenReferenceExists(String storageRoot) {
        assertScenarioRoot(storageRoot);
        state.brokenReferenceKey = "recovery/2026/scan/broken-reference.bin";
        Path reference = referencePath(state.bucket, state.brokenReferenceKey);
        Properties properties = new Properties();
        properties.setProperty("bucket", state.bucket);
        properties.setProperty("key", state.brokenReferenceKey);
        properties.setProperty("storageClass", "STANDARD");
        properties.setProperty("size", "1");
        properties.setProperty("manifestId", UUID.randomUUID().toString());
        properties.setProperty("versionId", UUID.randomUUID().toString());
        properties.setProperty("latest", "true");
        properties.setProperty("createdAt", ZonedDateTime.now().toString());
        properties.setProperty("userMetadata.count", "0");
        writeProperties(reference, properties, "broken reference fixture");
        state.brokenReferencePath = reference;
    }

    @Given("a committed chunk file with a corrupted checksum mismatch exists in filesystem root {string}")
    public void corruptedChecksumChunkExists(String storageRoot) {
        assertScenarioRoot(storageRoot);
        String chunkId = UUID.randomUUID().toString();
        Path chunk = state.storageRoot.resolve("nodes/node-001/chunks/" + chunkId);
        writeBytes(chunk, "synthetic committed chunk".getBytes(StandardCharsets.UTF_8));
        writeString(chunk.resolveSibling(chunk.getFileName() + ".sha256"), "0000");
        state.corruptedScannerChunkPath = chunk;
    }

    @Given("an S3 client prepares a PutObject for bucket {string} key {string} with content from fixture file {string}")
    public void clientPreparesPutObject(String bucket, String key, String fixtureFile) {
        state.fixtureBytes.put(fixtureFile, readFixture(fixtureFile));
        state.preparedPuts.add(new PreparedPut(bucket, key, fixtureFile, state.fixtureBytes.get(fixtureFile)));
    }

    @Given("two S3 clients each prepare a concurrent PutObject for bucket {string} key {string} with different fixture files {string} and {string}")
    public void twoClientsPrepareSameKeyPutObject(String bucket, String key, String fixtureFileA, String fixtureFileB) {
        state.fixtureBytes.put(fixtureFileA, readFixture(fixtureFileA));
        state.fixtureBytes.put(fixtureFileB, readFixture(fixtureFileB));
        state.preparedPuts.add(new PreparedPut(bucket, key, fixtureFileA, state.fixtureBytes.get(fixtureFileA)));
        state.preparedPuts.add(new PreparedPut(bucket, key, fixtureFileB, state.fixtureBytes.get(fixtureFileB)));
    }

    @When("the S3 client uploads fixture file {string} to bucket {string} and key {string} through the S3 HTTP PutObject API")
    public void clientUploadsFixture(String fixtureFile, String bucket, String key) {
        byte[] bytes = state.fixtureBytes.computeIfAbsent(fixtureFile, this::readFixture);
        state.lastResponse = putObject(bucket, key, bytes);
        state.bucket = bucket;
        state.objectKey = key;
        state.fixtureFile = fixtureFile;
    }

    @When("the S3 client starts uploading fixture file {string} to bucket {string} and key {string} through the S3 HTTP PutObject API")
    public void clientStartsUploadingFixture(String fixtureFile, String bucket, String key) {
        clientUploadsFixture(fixtureFile, bucket, key);
    }

    @When("the upload process is interrupted after some chunk bytes are written but before the chunk rename is complete")
    public void uploadInterruptedAfterChunkBytesBeforeRename() {
        assertEquals(ExpectedFault.CHUNK_TEMP_WRITE, state.expectedFault);
        assertUploadInterrupted("chunk write");
        state.interruptedTemporaryChunks = temporaryChunkFiles();
        assertFalse(state.interruptedTemporaryChunks.isEmpty(),
            "fault injection should preserve at least one temporary chunk artifact");
    }

    @When("all chunk files are successfully written and renamed to their committed paths in filesystem root {string}")
    public void allChunkFilesWrittenAndRenamed(String storageRoot) {
        assertScenarioRoot(storageRoot);
        state.committedChunksAfterManifestInterruption =
            faultInjector.committedChunksAtManifestInterruption();
        assertFalse(state.committedChunksAfterManifestInterruption.isEmpty(),
            "manifest interruption should occur after at least one committed chunk is published");
        assertTrue(faultInjector.committedChunkChecksumsValidAtManifestInterruption(),
            "every chunk committed before manifest interruption should have a valid checksum sidecar");
    }

    @When("the manifest write is interrupted after manifest bytes are partially written but before the manifest rename is complete")
    public void manifestWriteInterruptedBeforeRename() {
        assertEquals(ExpectedFault.MANIFEST_TEMP_WRITE, state.expectedFault);
        assertUploadInterrupted("manifest write");
        state.interruptedTemporaryManifests = temporaryManifestFiles();
        assertFalse(state.interruptedTemporaryManifests.isEmpty(),
            "fault injection should preserve at least one temporary manifest artifact");
    }

    @When("the application is restarted using storage-engine filesystem root {string}")
    public void applicationRestartedUsingStorageEngineRoot(String storageRoot) {
        assertScenarioRoot(storageRoot);
        faultInjector.disable();
        resetRepositories();
    }

    @When("the corruption injector overwrites bytes inside a committed chunk file in filesystem root {string} for bucket {string} and key {string} outside the running application")
    public void corruptionInjectorOverwritesChunk(String storageRoot, String bucket, String key) {
        assertScenarioRoot(storageRoot);
        ArtifactSet artifacts = artifactsFor(bucket, key);
        assertFalse(artifacts.chunkFiles().isEmpty(), "expected at least one committed chunk file");
        Path chunk = artifacts.chunkFiles().getFirst();
        byte[] data = readBytes(chunk);
        assertTrue(data.length > 0, "chunk must not be empty");
        data[0] = (byte) (data[0] ^ 0x7f);
        writeBytes(chunk, data);
        state.corruptedRuntimeArtifact = chunk;
    }

    @When("the corruption injector overwrites bytes inside the committed manifest file in filesystem root {string} for bucket {string} and key {string} outside the running application")
    public void corruptionInjectorOverwritesManifest(String storageRoot, String bucket, String key) {
        assertScenarioRoot(storageRoot);
        ArtifactSet artifacts = artifactsFor(bucket, key);
        Path manifest = artifacts.manifestFile();
        String content = readString(manifest);
        int index = firstMutableManifestCharacter(content);
        assertTrue(index >= 0, "manifest should contain mutable content before checksum trailer");
        char replacement = content.charAt(index) == 'X' ? 'Y' : 'X';
        String corrupted = content.substring(0, index) + replacement + content.substring(index + 1);
        writeString(manifest, corrupted);
        state.corruptedRuntimeArtifact = manifest;
    }

    @When("the S3 client reads bucket {string} and key {string} through the S3 HTTP GetObject API")
    public void clientReadsThroughGetObject(String bucket, String key) {
        state.lastResponse = getObject(bucket, key);
    }

    @When("the recovery scanner is triggered for filesystem root {string}")
    public void recoveryScannerTriggered(String storageRoot) {
        assertScenarioRoot(storageRoot);
        state.lastScanReport = recoveryScanner.scan(state.storageRoot);
    }

    @When("the recovery scanner is triggered again for filesystem root {string} with no new artifacts introduced")
    public void recoveryScannerTriggeredAgain(String storageRoot) {
        assertScenarioRoot(storageRoot);
        state.lastScanReport = recoveryScanner.scan(state.storageRoot);
    }

    @When("both PutObject requests are issued concurrently to the S3 HTTP API")
    public void bothPutObjectRequestsIssuedConcurrently() {
        assertEquals(2, state.preparedPuts.size(), "scenario should prepare exactly two PUT requests");
        List<CompletableFuture<PutResult>> futures = state.preparedPuts.stream()
            .map(prepared -> CompletableFuture.supplyAsync(() -> {
                Response response = putObject(prepared.bucket(), prepared.key(), prepared.bytes());
                return new PutResult(prepared, response.status(), response.bodyAsString());
            }))
            .toList();
        state.concurrentPutResults = futures.stream().map(CompletableFuture::join).toList();
    }

    @Then("the final committed chunk path in filesystem root {string} for bucket {string} and key {string} does not contain a partial chunk file")
    public void finalCommittedChunkPathDoesNotContainPartialChunk(String storageRoot, String bucket, String key) {
        assertScenarioRoot(storageRoot);
        List<Path> temporaryChunks = temporaryChunkFiles();
        assertFalse(temporaryChunks.isEmpty(), "expected preserved temporary chunk artifact");
        assertTemporaryArtifactsDoNotHaveCommittedCounterparts(temporaryChunks, "chunk");
        assertNoObjectReference(bucket, key);
    }

    @Then("if a temporary chunk file remains in filesystem root {string}, it is isolated from committed chunk paths and not served as a committed chunk")
    public void temporaryChunkIsIsolatedAndNotServed(String storageRoot) {
        assertScenarioRoot(storageRoot);
        List<Path> temporaryChunks = temporaryChunkFiles();
        assertFalse(temporaryChunks.isEmpty(), "expected temporary chunk artifact to remain");
        assertTemporaryArtifactsDoNotHaveCommittedCounterparts(temporaryChunks, "chunk");
        assertBucketKeyUnreadable(state.bucket, state.objectKey);
    }

    @Then("bucket {string} and key {string} are not visible through the S3 HTTP GetObject API")
    public void bucketKeyNotVisibleThroughGetObject(String bucket, String key) {
        assertBucketKeyUnreadable(bucket, key);
    }

    @Then("bucket {string} and key {string} remain unreadable after restart")
    public void bucketKeyRemainUnreadableAfterRestart(String bucket, String key) {
        assertBucketKeyUnreadable(bucket, key);
    }

    @Then("the final committed chunk path in filesystem root {string} still does not contain a partial or incomplete chunk file")
    public void finalCommittedChunkPathStillDoesNotContainPartialChunk(String storageRoot) {
        assertScenarioRoot(storageRoot);
        List<Path> temporaryChunks = temporaryChunkFiles();
        assertFalse(temporaryChunks.isEmpty(), "expected temporary chunk artifact to remain after restart-equivalent reset");
        assertTemporaryArtifactsDoNotHaveCommittedCounterparts(temporaryChunks, "chunk");
    }

    @Then("any remaining temporary chunk files are not referenced by any committed manifest or object reference")
    public void temporaryChunkFilesAreNotReferenced() {
        assertTemporaryChunksNotReferencedByCommittedManifests();
        assertNoObjectReference(state.bucket, state.objectKey);
    }

    @Then("the committed manifest path in filesystem root {string} for bucket {string} and key {string} does not contain a partial or unparseable manifest file")
    public void committedManifestPathDoesNotContainPartialManifest(String storageRoot, String bucket, String key) {
        assertScenarioRoot(storageRoot);
        List<Path> temporaryManifests = temporaryManifestFiles();
        assertFalse(temporaryManifests.isEmpty(), "expected preserved temporary manifest artifact");
        assertTemporaryArtifactsDoNotHaveCommittedCounterparts(temporaryManifests, "manifest");
        assertNoObjectReference(bucket, key);
    }

    @Then("if a temporary manifest file remains in filesystem root {string}, it is not used to drive reads or referenced by any object reference")
    public void temporaryManifestIsNotUsedForReadsOrReferences(String storageRoot) {
        assertScenarioRoot(storageRoot);
        List<Path> temporaryManifests = temporaryManifestFiles();
        assertFalse(temporaryManifests.isEmpty(), "expected temporary manifest artifact to remain");
        assertTemporaryManifestsNotReferencedByObjectReferences();
        assertBucketKeyUnreadable(state.bucket, state.objectKey);
    }

    @Then("the committed manifest path in filesystem root {string} still does not contain a partial or unparseable manifest file")
    public void committedManifestPathStillDoesNotContainPartialManifest(String storageRoot) {
        assertScenarioRoot(storageRoot);
        List<Path> temporaryManifests = temporaryManifestFiles();
        assertFalse(temporaryManifests.isEmpty(), "expected temporary manifest artifact to remain after restart-equivalent reset");
        assertTemporaryArtifactsDoNotHaveCommittedCounterparts(temporaryManifests, "manifest");
    }

    @Then("any remaining temporary manifest file is not referenced by any committed object reference")
    public void temporaryManifestFileIsNotReferencedByObjectReference() {
        assertTemporaryManifestsNotReferencedByObjectReferences();
        assertNoObjectReference(state.bucket, state.objectKey);
    }

    @Then("the upload is committed and every chunk file in filesystem root {string} for bucket {string} and key {string} carries a verifiable checksum")
    public void uploadCommittedAndEveryChunkCarriesChecksum(String storageRoot, String bucket, String key) {
        assertScenarioRoot(storageRoot);
        assertEquals(200, state.lastResponse.status(), state.lastResponse.bodyAsString());
        ArtifactSet artifacts = artifactsFor(bucket, key);
        assertFalse(artifacts.chunkFiles().isEmpty(), "expected committed chunk files");
        artifacts.chunkFiles().forEach(this::assertChunkChecksumValid);
    }

    @Then("the upload is committed and the manifest file in filesystem root {string} for bucket {string} and key {string} carries a verifiable checksum or digest")
    public void uploadCommittedAndManifestCarriesChecksum(String storageRoot, String bucket, String key) {
        assertScenarioRoot(storageRoot);
        assertEquals(200, state.lastResponse.status(), state.lastResponse.bodyAsString());
        assertManifestChecksumValid(artifactsFor(bucket, key).manifestFile());
    }

    @Then("the S3 client can immediately read bucket {string} and key {string} and receive the exact original bytes from fixture file {string}")
    public void clientCanImmediatelyReadExactBytes(String bucket, String key, String fixtureFile) {
        Response response = getObject(bucket, key);
        assertEquals(200, response.status(), response.bodyAsString());
        assertArrayEquals(state.fixtureBytes.get(fixtureFile), response.body());
    }

    @Then("the storage engine detects the checksum mismatch for the corrupted chunk before returning any response bytes")
    public void storageEngineDetectsCorruptedChunk() {
        assertEquals(500, state.lastResponse.status(), state.lastResponse.bodyAsString());
        assertFalse(Objects.deepEquals(state.fixtureBytes.get(state.fixtureFile), state.lastResponse.body()),
            "corrupted object bytes must not be returned as a successful body");
    }

    @Then("the S3 HTTP GetObject response signals an object integrity failure rather than returning corrupted bytes as a successful object body")
    public void getObjectSignalsObjectIntegrityFailure() {
        assertIntegrityErrorResponse();
    }

    @Then("the storage engine detects the manifest checksum mismatch before loading any chunk or returning any response bytes")
    public void storageEngineDetectsManifestChecksumMismatch() {
        assertEquals(500, state.lastResponse.status(), state.lastResponse.bodyAsString());
    }

    @Then("the S3 HTTP GetObject response signals a manifest integrity failure rather than returning bytes guided by a corrupted manifest")
    public void getObjectSignalsManifestIntegrityFailure() {
        assertIntegrityErrorResponse();
    }

    @Then("the scanner report lists at least {string} findings")
    public void scannerReportListsAtLeastFindings(String minimumFindingCount) {
        assertNotNull(state.lastScanReport, "scanner report should exist");
        assertTrue(state.lastScanReport.size() >= Integer.parseInt(minimumFindingCount),
            () -> "expected at least " + minimumFindingCount + " findings but got " + state.lastScanReport.findings());
    }

    @Then("the scanner report includes the orphaned chunk artifact paths with artifact type {string} and a descriptive failure reason")
    public void reportIncludesOrphanedChunk(String artifactType) {
        assertFinding(state.orphanedChunkPath, artifactType);
    }

    @Then("the scanner report includes the checksum sidecar without committed chunk with artifact type {string} and a descriptive failure reason")
    public void reportIncludesOrphanedChecksumSidecar(String artifactType) {
        assertFinding(state.orphanedChecksumSidecarPath, artifactType);
    }

    @Then("the scanner report includes the incomplete manifest artifact path with artifact type {string} and a descriptive failure reason")
    public void reportIncludesIncompleteManifest(String artifactType) {
        assertFinding(state.incompleteManifestPath, artifactType);
    }

    @Then("the scanner report includes the broken object reference artifact path with artifact type {string} and a descriptive failure reason")
    public void reportIncludesBrokenReference(String artifactType) {
        assertFinding(state.brokenReferencePath, artifactType);
    }

    @Then("the scanner report includes the corrupted chunk artifact path with artifact type {string} and a descriptive failure reason")
    public void reportIncludesCorruptedChunk(String artifactType) {
        assertFinding(state.corruptedScannerChunkPath, artifactType);
    }

    @Then("the scanner removes or quarantines all reported incomplete and corrupt artifacts")
    public void scannerQuarantinesReportedArtifacts() {
        recoveryScanner.quarantine(state.storageRoot, state.lastScanReport);
        for (var finding : state.lastScanReport.findings()) {
            Path original = Path.of(finding.artifactPath());
            assertFalse(Files.exists(original), "reported artifact should be removed from original path: " + original);
            Path quarantined = state.storageRoot.resolve("quarantine").resolve(state.storageRoot.relativize(original));
            assertTrue(Files.exists(quarantined), "reported artifact should be quarantined: " + quarantined);
        }
    }

    @Then("the scanner does not remove or modify the valid committed object's chunks, manifest, or object reference in filesystem root {string}")
    public void scannerDoesNotModifyValidObject(String storageRoot) {
        assertScenarioRoot(storageRoot);
        Map<Path, String> currentHashes = hashArtifacts(state.validObjectArtifacts.allExistingPaths());
        assertEquals(state.validObjectHashes, currentHashes, "valid committed object artifacts should be unchanged");
    }

    @Then("after the scan, the S3 client reads bucket {string} and key {string} and receives the exact bytes from fixture file {string}")
    public void afterScanClientReadsExactBytes(String bucket, String key, String fixtureFile) {
        Response response = getObject(bucket, key);
        assertEquals(200, response.status(), response.bodyAsString());
        assertArrayEquals(state.fixtureBytes.get(fixtureFile), response.body());
    }

    @Then("after the scan, the orphaned, incomplete, and broken artifacts are no longer served through the S3 HTTP GetObject API")
    public void artifactsNoLongerServedThroughS3() {
        assertFalse(Files.exists(state.orphanedChunkPath), "orphaned chunk should no longer remain at original path");
        assertFalse(Files.exists(state.orphanedChecksumSidecarPath),
            "orphaned checksum sidecar should no longer remain at original path");
        assertFalse(Files.exists(state.incompleteManifestPath), "incomplete manifest should no longer remain at original path");
        assertFalse(Files.exists(state.brokenReferencePath), "broken reference should no longer remain at original path");
        Response brokenReferenceRead = getObject(state.bucket, state.brokenReferenceKey);
        assertEquals(404, brokenReferenceRead.status(), brokenReferenceRead.bodyAsString());
    }

    @Then("the scanner report lists zero findings, confirming deterministic idempotent reporting")
    public void scannerReportListsZeroFindings() {
        assertNotNull(state.lastScanReport, "scanner report should exist");
        assertEquals(0, state.lastScanReport.size(), () -> "expected no findings but got " + state.lastScanReport.findings());
    }

    @Then("both PutObject requests complete with HTTP 200")
    public void bothPutObjectRequestsCompleteWithHttp200() {
        assertEquals(2, state.concurrentPutResults.size());
        state.concurrentPutResults.forEach(result -> assertEquals(200, result.status(), result.body()));
    }

    @Then("the S3 HTTP GetObject for bucket {string} key {string} returns the exact bytes from fixture file {string}")
    public void getObjectReturnsExactBytesFromFixture(String bucket, String key, String fixtureFile) {
        Response response = getObject(bucket, key);
        assertEquals(200, response.status(), response.bodyAsString());
        assertArrayEquals(state.fixtureBytes.get(fixtureFile), response.body());
    }

    @Then("the chunk files for key {string} and key {string} in filesystem root {string} are not mixed, overwritten, or corrupted")
    public void chunkFilesForKeysAreNotMixed(String keyA, String keyB, String storageRoot) {
        assertScenarioRoot(storageRoot);
        ArtifactSet artifactsA = artifactsFor(state.preparedPuts.get(0).bucket(), keyA);
        ArtifactSet artifactsB = artifactsFor(state.preparedPuts.get(1).bucket(), keyB);
        assertFalse(artifactsA.chunkFiles().isEmpty(), "key A should have committed chunks");
        assertFalse(artifactsB.chunkFiles().isEmpty(), "key B should have committed chunks");
        artifactsA.chunkFiles().forEach(this::assertChunkChecksumValid);
        artifactsB.chunkFiles().forEach(this::assertChunkChecksumValid);
        assertNotEquals(artifactsA.manifestFile(), artifactsB.manifestFile(), "different keys should have separate committed manifests");
    }

    @Then("the committed manifest for key {string} in filesystem root {string} is parseable and has a valid checksum")
    public void committedManifestForKeyIsParseableAndValid(String key, String storageRoot) {
        assertScenarioRoot(storageRoot);
        String bucket = state.preparedPuts.stream()
            .filter(put -> put.key().equals(key))
            .findFirst()
            .orElseThrow()
            .bucket();
        assertManifestChecksumValid(artifactsFor(bucket, key).manifestFile());
    }

    @Then("at least one PutObject request completes with HTTP 200")
    public void atLeastOnePutObjectCompletesWithHttp200() {
        assertEquals(2, state.concurrentPutResults.size());
        assertTrue(state.concurrentPutResults.stream().anyMatch(result -> result.status() == 200),
            () -> "expected at least one successful PUT but got " + state.concurrentPutResults);
    }

    @Then("after all concurrent requests complete, the S3 HTTP GetObject for bucket {string} key {string} returns a complete non-corrupted object body")
    public void afterConcurrentRequestsGetObjectReturnsCompleteBody(String bucket, String key) {
        state.lastResponse = getObject(bucket, key);
        assertEquals(200, state.lastResponse.status(), state.lastResponse.bodyAsString());
        assertTrue(state.lastResponse.body().length > 0, "winner body should be complete and non-empty");
    }

    @Then("the returned object body exactly matches either fixture file {string} or fixture file {string} — not a mix of bytes from both")
    public void returnedBodyMatchesOneFixture(String fixtureFileA, String fixtureFileB) {
        byte[] body = state.lastResponse.body();
        boolean matchesA = java.util.Arrays.equals(body, state.fixtureBytes.get(fixtureFileA));
        boolean matchesB = java.util.Arrays.equals(body, state.fixtureBytes.get(fixtureFileB));
        assertTrue(matchesA || matchesB, "returned body should exactly match one submitted fixture");
    }

    @Then("the committed manifest for bucket {string} key {string} in filesystem root {string} is parseable and has a valid checksum")
    public void committedManifestForBucketKeyIsParseableAndValid(String bucket, String key, String storageRoot) {
        assertScenarioRoot(storageRoot);
        assertManifestChecksumValid(artifactsFor(bucket, key).manifestFile());
    }

    @Then("the object reference for bucket {string} key {string} in filesystem root {string} points to exactly one committed manifest identifier")
    public void objectReferencePointsToExactlyOneCommittedManifest(String bucket, String key, String storageRoot) {
        assertScenarioRoot(storageRoot);
        List<Path> references = referenceFilesFor(bucket, key);
        assertEquals(1, references.size(), "expected one S3 object reference for the key");
        Properties reference = loadProperties(references.getFirst());
        String manifestId = reference.getProperty("manifestId");
        assertNotNull(manifestId, "object reference should contain manifestId");
        assertTrue(Files.exists(state.storageRoot.resolve("metadata/manifests/" + manifestId + ".properties")),
            "referenced manifest should exist");
    }

    private void assertUploadInterrupted(String expectedMessagePart) {
        assertNotNull(state.lastResponse, "upload should have produced a response");
        assertTrue(state.lastResponse.status() >= 500,
            () -> "fault-injected upload should fail before publication, status="
                + state.lastResponse.status() + " body=" + state.lastResponse.bodyAsString());
        assertTrue(state.lastResponse.bodyAsString().toLowerCase().contains(expectedMessagePart),
            () -> "upload failure body should mention " + expectedMessagePart + " but was: "
                + state.lastResponse.bodyAsString());
    }

    private void assertBucketKeyUnreadable(String bucket, String key) {
        Response response = getObject(bucket, key);
        assertEquals(404, response.status(), response.bodyAsString());
    }

    private void assertNoObjectReference(String bucket, String key) {
        assertTrue(referenceFilesFor(bucket, key).isEmpty(),
            "failed upload must not publish an object reference for " + bucket + "/" + key);
    }

    private void assertTemporaryArtifactsDoNotHaveCommittedCounterparts(List<Path> temporaryArtifacts, String artifactType) {
        for (Path temporaryArtifact : temporaryArtifacts) {
            Path committedCounterpart = committedCounterpartForTemporaryArtifact(temporaryArtifact);
            assertFalse(Files.exists(committedCounterpart),
                () -> "temporary " + artifactType + " artifact must not be published at committed path "
                    + committedCounterpart + " from " + temporaryArtifact);
        }
    }

    private void assertTemporaryChunksNotReferencedByCommittedManifests() {
        List<String> interruptedChunkIds = temporaryChunkFiles().stream()
            .map(this::committedCounterpartForTemporaryArtifact)
            .map(path -> path.getFileName().toString())
            .toList();
        if (interruptedChunkIds.isEmpty()) {
            return;
        }
        for (Path manifestFile : committedManifestFiles()) {
            Properties manifest = loadProperties(manifestFile);
            int chunkCount = manifestArtifactCount(manifest);
            for (int i = 0; i < chunkCount; i++) {
                String chunkId = manifestArtifactId(manifest, i);
                assertFalse(interruptedChunkIds.contains(chunkId),
                    () -> "temporary chunk must not be referenced by committed manifest " + manifestFile);
            }
        }
    }

    private void assertTemporaryManifestsNotReferencedByObjectReferences() {
        List<String> interruptedManifestIds = temporaryManifestFiles().stream()
            .map(this::committedCounterpartForTemporaryArtifact)
            .map(path -> path.getFileName().toString())
            .map(fileName -> fileName.endsWith(".properties")
                ? fileName.substring(0, fileName.length() - ".properties".length())
                : fileName)
            .toList();
        if (interruptedManifestIds.isEmpty()) {
            return;
        }
        for (Path referenceFile : allReferenceFiles()) {
            Properties reference = loadProperties(referenceFile);
            assertFalse(interruptedManifestIds.contains(reference.getProperty("manifestId")),
                () -> "temporary manifest must not be referenced by object reference " + referenceFile);
        }
    }

    private List<Path> temporaryChunkFiles() {
        Path nodes = state.storageRoot.resolve("nodes");
        if (!Files.isDirectory(nodes)) {
            return List.of();
        }
        try (var walk = Files.walk(nodes)) {
            return walk
                .filter(Files::isRegularFile)
                .filter(path -> path.getFileName().toString().contains(".tmp."))
                .filter(path -> !path.getFileName().toString().contains(".sha256.tmp."))
                .sorted()
                .toList();
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    private List<Path> temporaryManifestFiles() {
        Path manifests = state.storageRoot.resolve("metadata/manifests");
        if (!Files.isDirectory(manifests)) {
            return List.of();
        }
        try (var walk = Files.walk(manifests)) {
            return walk
                .filter(Files::isRegularFile)
                .filter(path -> path.getFileName().toString().contains(".tmp."))
                .sorted()
                .toList();
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    private List<Path> committedChunkFiles() {
        Path nodes = state.storageRoot.resolve("nodes");
        if (!Files.isDirectory(nodes)) {
            return List.of();
        }
        try (var walk = Files.walk(nodes)) {
            return walk
                .filter(Files::isRegularFile)
                .filter(path -> !path.getFileName().toString().contains(".tmp."))
                .filter(path -> !path.getFileName().toString().endsWith(".sha256"))
                .sorted()
                .toList();
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    private List<Path> committedManifestFiles() {
        Path manifests = state.storageRoot.resolve("metadata/manifests");
        if (!Files.isDirectory(manifests)) {
            return List.of();
        }
        try (var walk = Files.walk(manifests)) {
            return walk
                .filter(Files::isRegularFile)
                .filter(path -> path.getFileName().toString().endsWith(".properties"))
                .filter(path -> !path.getFileName().toString().contains(".tmp."))
                .sorted()
                .toList();
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    private List<Path> allReferenceFiles() {
        Path referencesRoot = state.storageRoot.resolve("metadata/s3-object-references");
        if (!Files.isDirectory(referencesRoot)) {
            return List.of();
        }
        try (var walk = Files.walk(referencesRoot)) {
            return walk
                .filter(Files::isRegularFile)
                .filter(path -> path.getFileName().toString().endsWith(".properties"))
                .sorted()
                .toList();
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    private Path committedCounterpartForTemporaryArtifact(Path temporaryArtifact) {
        String fileName = temporaryArtifact.getFileName().toString();
        int tempMarker = fileName.indexOf(".tmp.");
        assertTrue(tempMarker > 0, "temporary artifact should include .tmp. marker: " + temporaryArtifact);
        return temporaryArtifact.resolveSibling(fileName.substring(0, tempMarker));
    }

    private void putBucket(String bucket) {
        var status = webTestClient.put()
            .uri("/{bucket}", bucket)
            .exchange()
            .returnResult(byte[].class)
            .getStatus()
            .value();
        assertEquals(200, status, "bucket create should succeed");
    }

    private Response putObject(String bucket, String key, byte[] bytes) {
        var request = webTestClient.put()
            .uri(URI.create("/" + bucket + "/" + key));
        state.requestHeaders.forEach(request::header);
        if (state.requestHeaders.keySet().stream().noneMatch(HttpHeaders.CONTENT_TYPE::equalsIgnoreCase)) {
            request.contentType(MediaType.APPLICATION_OCTET_STREAM);
        }
        var result = request
            .bodyValue(bytes)
            .exchange()
            .expectBody(byte[].class)
            .returnResult();
        return new Response(result.getStatus().value(), result.getResponseBody() == null ? new byte[0] : result.getResponseBody());
    }

    private Response getObject(String bucket, String key) {
        var result = webTestClient.get()
            .uri(URI.create("/" + bucket + "/" + key))
            .exchange()
            .expectBody(byte[].class)
            .returnResult();
        return new Response(result.getStatus().value(), result.getResponseBody() == null ? new byte[0] : result.getResponseBody());
    }

    private void resetRepositories() {
        // Bucket registry, object references and multipart state are durable on the
        // storage-engine filesystem (EP-2). Discarding caches and reloading from the
        // configured storage root is both the clean-setup behavior (after a symlink
        // swap to a fresh root) and the restart-simulation behavior (durable files
        // are kept and must be re-read from disk).
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
        Properties properties = loadProperties(manifestFile);
        assertNotNull(properties.getProperty("manifestId"), "manifest should be parseable and contain manifestId");
    }

    private void assertIntegrityErrorResponse() {
        assertEquals(500, state.lastResponse.status(), state.lastResponse.bodyAsString());
        assertTrue(state.lastResponse.bodyAsString().contains("XAmzChecksumMismatch"),
            () -> "expected XAmzChecksumMismatch body but got " + state.lastResponse.bodyAsString());
    }

    private void assertFinding(Path path, String artifactType) {
        assertNotNull(state.lastScanReport, "scanner report should exist");
        assertTrue(state.lastScanReport.findings().stream().anyMatch(finding ->
                Path.of(finding.artifactPath()).equals(path)
                    && finding.artifactType().equals(artifactType)
                    && finding.reason() != null
                    && !finding.reason().isBlank()),
            () -> "expected finding for " + path + " of type " + artifactType + " but got " + state.lastScanReport.findings());
    }

    private Map<Path, String> hashArtifacts(List<Path> paths) {
        return paths.stream()
            .filter(Files::exists)
            .collect(java.util.stream.Collectors.toMap(path -> path, path -> sha256Hex(readBytes(path)), (a, b) -> a, java.util.TreeMap::new));
    }

    private Path referencePath(String bucket, String key) {
        return state.storageRoot.resolve("metadata/s3-object-references")
            .resolve(encode(bucket))
            .resolve(encode(key) + ".properties");
    }

    private static String encode(String value) {
        return Base64.getUrlEncoder().withoutPadding().encodeToString(value.getBytes(StandardCharsets.UTF_8));
    }

    private static int firstMutableManifestCharacter(String content) {
        int checksumIndex = content.lastIndexOf("\nmanifest.checksum=");
        int limit = checksumIndex > 0 ? checksumIndex : content.length();
        for (int i = 0; i < limit; i++) {
            char c = content.charAt(i);
            if (Character.isLetterOrDigit(c)) {
                return i;
            }
        }
        return -1;
    }

    private void assertScenarioRoot(String storageRoot) {
        assertEquals(resolveProjectPath(storageRoot), state.storageRoot);
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

    private static void writeString(Path path, String content) {
        try {
            Files.createDirectories(path.getParent());
            Files.writeString(path, content, StandardOpenOption.CREATE, StandardOpenOption.TRUNCATE_EXISTING);
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    private static void writeProperties(Path path, Properties properties, String comments) {
        try {
            Files.createDirectories(path.getParent());
            try (var writer = Files.newBufferedWriter(path, StandardCharsets.UTF_8,
                StandardOpenOption.CREATE, StandardOpenOption.TRUNCATE_EXISTING)) {
                properties.store(writer, comments);
            }
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

    public static final class State {
        String requirementId;
        Path storageRoot;
        String bucket;
        String objectKey;
        String fixtureFile;
        Response lastResponse;
        ExpectedFault expectedFault = ExpectedFault.NONE;
        FileSystemRecoveryScanner.ScanReport lastScanReport;
        final Map<String, byte[]> fixtureBytes = new java.util.HashMap<>();
        final Map<String, String> requestHeaders = new java.util.LinkedHashMap<>();
        final List<PreparedPut> preparedPuts = new ArrayList<>();
        List<PutResult> concurrentPutResults = List.of();
        ArtifactSet validObjectArtifacts;
        Map<Path, String> validObjectHashes = Map.of();
        List<Path> interruptedTemporaryChunks = List.of();
        List<Path> interruptedTemporaryManifests = List.of();
        List<Path> committedChunksAfterManifestInterruption = List.of();
        Path orphanedChunkPath;
        Path orphanedChecksumSidecarPath;
        Path incompleteManifestPath;
        Path brokenReferencePath;
        String brokenReferenceKey;
        Path corruptedScannerChunkPath;
        Path corruptedRuntimeArtifact;

        void resetVolatileState() {
            requirementId = null;
            storageRoot = null;
            bucket = null;
            objectKey = null;
            fixtureFile = null;
            lastResponse = null;
            expectedFault = ExpectedFault.NONE;
            lastScanReport = null;
            fixtureBytes.clear();
            requestHeaders.clear();
            preparedPuts.clear();
            concurrentPutResults = List.of();
            validObjectArtifacts = null;
            validObjectHashes = Map.of();
            interruptedTemporaryChunks = List.of();
            interruptedTemporaryManifests = List.of();
            committedChunksAfterManifestInterruption = List.of();
            orphanedChunkPath = null;
            orphanedChecksumSidecarPath = null;
            incompleteManifestPath = null;
            brokenReferencePath = null;
            brokenReferenceKey = null;
            corruptedScannerChunkPath = null;
            corruptedRuntimeArtifact = null;
        }
    }

    private enum ExpectedFault {
        NONE,
        CHUNK_TEMP_WRITE,
        MANIFEST_TEMP_WRITE
    }

    private record Response(int status, byte[] body) {
        String bodyAsString() {
            return new String(body, StandardCharsets.UTF_8);
        }
    }

    private record PreparedPut(String bucket, String key, String fixtureFile, byte[] bytes) {
    }

    private record PutResult(PreparedPut put, int status, String body) {
    }

    private record ArtifactSet(Path referenceFile, Path manifestFile, List<Path> chunkFiles) {
        List<Path> allExistingPaths() {
            List<Path> paths = new ArrayList<>();
            paths.add(referenceFile);
            paths.add(manifestFile);
            paths.addAll(chunkFiles);
            chunkFiles.forEach(chunk -> paths.add(chunk.resolveSibling(chunk.getFileName() + ".sha256")));
            return paths.stream().filter(Files::exists).sorted().toList();
        }
    }
}
