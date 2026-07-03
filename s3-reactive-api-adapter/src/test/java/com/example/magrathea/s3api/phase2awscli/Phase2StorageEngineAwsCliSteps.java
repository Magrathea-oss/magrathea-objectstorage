package com.example.magrathea.s3api.phase2awscli;

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
import java.io.StringReader;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Base64;
import java.util.Comparator;
import java.util.HexFormat;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Properties;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.*;

public class Phase2StorageEngineAwsCliSteps {

    private static final Path PROJECT_ROOT = Path.of("").toAbsolutePath().normalize();
    private static final Path CONFIGURED_STORAGE_ROOT = PROJECT_ROOT.resolve("target/storage-engine-it/current").normalize();
    private static final Duration AWS_CLI_TIMEOUT = Duration.ofSeconds(30);
    private static final AwsCliPreflight AWS_CLI_PREFLIGHT = checkAwsCliPreflight();

    @LocalServerPort
    private int port;

    @Autowired
    private AwsCliSharedContext sharedCtx;

    @Autowired
    private ObjectProvider<StorageEngineReactiveBucketRepository> bucketRepository;

    @Autowired
    private ObjectProvider<StorageEngineReactiveS3ObjectRepository> objectRepository;

    @Autowired
    private ObjectProvider<StorageEngineReactiveMultipartUploadRepository> multipartRepository;

    @Autowired
    private ObjectProvider<S3BucketConfigHandler> bucketConfigHandler;

    private final State state = new State();

    @Before
    public void resetStateAndCheckAwsCli() {
        state.reset();
        sharedCtx.reset();
        Assumptions.assumeTrue(AWS_CLI_PREFLIGHT.available(), AWS_CLI_PREFLIGHT.reason());
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
        assertEquals("awscli", validationMode, "this runner executes only AWS CLI examples");
        state.requirementId = requirementId;
    }

    @Given("the storage engine operator uses filesystem root {string}")
    public void operatorUsesFilesystemRoot(String storageRoot) {
        state.storageRoot = resolveProjectPath(storageRoot);
        sharedCtx.storageRoot = state.storageRoot;
        cleanScenarioRootAndPointConfiguredSymlink(state.storageRoot);
        resetRepositories();
    }

    @Given("bucket {string} exists")
    public void bucketExists(String bucket) throws Exception {
        state.bucket = bucket;
        sharedCtx.bucket = bucket;
        AwsResult result = runAws("create-bucket", "--bucket", bucket);
        assertEquals(0, result.exitCode(), () -> "create-bucket should succeed: " + result.combinedOutput());
    }

    @Given("an S3 client has object content from fixture file {string} for bucket {string} and key {string}")
    public void clientHasObjectContent(String fixtureFile, String bucket, String key) {
        state.fixtureBytes.put(fixtureFile, readFixture(fixtureFile));
        state.bucket = bucket;
        state.objectKey = key;
        state.fixtureFile = fixtureFile;
        sharedCtx.bucket = bucket;
        sharedCtx.objectKey = key;
        sharedCtx.fixtureFile = fixtureFile;
        sharedCtx.fixtureBytes = state.fixtureBytes.get(fixtureFile);
    }

    @Given("no objects exist for key {string} and key {string} in bucket {string}")
    public void noObjectsExistForKeys(String keyA, String keyB, String bucket) throws Exception {
        assertGetObjectMissing(bucket, keyA);
        assertGetObjectMissing(bucket, keyB);
    }

    @Given("no object exists in bucket {string} for key {string}")
    public void noObjectExists(String bucket, String key) throws Exception {
        assertGetObjectMissing(bucket, key);
    }

    @Given("an S3 client prepares a PutObject for bucket {string} key {string} with content from fixture file {string}")
    public void clientPreparesPutObject(String bucket, String key, String fixtureFile) {
        byte[] bytes = readFixture(fixtureFile);
        state.fixtureBytes.put(fixtureFile, bytes);
        state.preparedPuts.add(new PreparedPut(bucket, key, fixtureFile, bytes));
    }

    @Given("two S3 clients each prepare a concurrent PutObject for bucket {string} key {string} with different fixture files {string} and {string}")
    public void twoClientsPrepareSameKeyPutObject(String bucket, String key, String fixtureFileA, String fixtureFileB) {
        byte[] bytesA = readFixture(fixtureFileA);
        byte[] bytesB = readFixture(fixtureFileB);
        state.fixtureBytes.put(fixtureFileA, bytesA);
        state.fixtureBytes.put(fixtureFileB, bytesB);
        state.preparedPuts.add(new PreparedPut(bucket, key, fixtureFileA, bytesA));
        state.preparedPuts.add(new PreparedPut(bucket, key, fixtureFileB, bytesB));
    }

    @When("the S3 client uploads fixture file {string} to bucket {string} and key {string} through the S3 HTTP PutObject API")
    public void clientUploadsFixture(String fixtureFile, String bucket, String key) throws Exception {
        byte[] bytes = state.fixtureBytes.computeIfAbsent(fixtureFile, this::readFixture);
        state.lastResult = putObjectWithHeaders(bucket, key, bytes, sharedCtx.requestHeaders);
        sharedCtx.lastGetExitCode = -1;
        state.bucket = bucket;
        state.objectKey = key;
        state.fixtureFile = fixtureFile;
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
        Path manifest = artifactsFor(bucket, key).manifestFile();
        String content = readString(manifest);
        int index = firstMutableManifestCharacter(content);
        assertTrue(index >= 0, "manifest should contain mutable content before checksum trailer");
        char replacement = content.charAt(index) == 'X' ? 'Y' : 'X';
        writeString(manifest, content.substring(0, index) + replacement + content.substring(index + 1));
        state.corruptedRuntimeArtifact = manifest;
    }

    @When("the S3 client reads bucket {string} and key {string} through the S3 HTTP GetObject API")
    public void clientReadsThroughGetObject(String bucket, String key) throws Exception {
        state.lastDownloadedFile = Files.createTempFile("magrathea-phase2-get-", ".bin");
        state.lastResult = runAws("get-object", "--bucket", bucket, "--key", key, state.lastDownloadedFile.toString());
        state.lastDownloadedBytes = Files.exists(state.lastDownloadedFile)
            ? Files.readAllBytes(state.lastDownloadedFile)
            : new byte[0];
        sharedCtx.lastGetExitCode = state.lastResult.exitCode();
        sharedCtx.lastGetBytes = state.lastDownloadedBytes;
        sharedCtx.lastGetStdout = state.lastResult.stdout();
        sharedCtx.lastGetStderr = state.lastResult.stderr();
    }

    @When("both PutObject requests are issued concurrently to the S3 HTTP API")
    public void bothPutObjectRequestsIssuedConcurrently() {
        assertEquals(2, state.preparedPuts.size(), "scenario should prepare exactly two PUT requests");
        List<CompletableFuture<PutResult>> futures = state.preparedPuts.stream()
            .map(prepared -> CompletableFuture.supplyAsync(() -> {
                try {
                    AwsResult result = putObject(prepared.bucket(), prepared.key(), prepared.bytes());
                    return new PutResult(prepared, result.exitCode(), result.combinedOutput());
                } catch (Exception e) {
                    throw new RuntimeException(e);
                }
            }))
            .toList();
        state.concurrentPutResults = futures.stream().map(CompletableFuture::join).toList();
    }

    @Then("the upload is committed and every chunk file in filesystem root {string} for bucket {string} and key {string} carries a verifiable checksum")
    public void uploadCommittedAndEveryChunkCarriesChecksum(String storageRoot, String bucket, String key) {
        assertScenarioRoot(storageRoot);
        assertEquals(0, state.lastResult.exitCode(), () -> "put-object should succeed: " + state.lastResult.combinedOutput());
        ArtifactSet artifacts = artifactsFor(bucket, key);
        assertFalse(artifacts.chunkFiles().isEmpty(), "expected committed chunk files");
        artifacts.chunkFiles().forEach(this::assertChunkChecksumValid);
    }

    @Then("the upload is committed and the manifest file in filesystem root {string} for bucket {string} and key {string} carries a verifiable checksum or digest")
    public void uploadCommittedAndManifestCarriesChecksum(String storageRoot, String bucket, String key) {
        assertScenarioRoot(storageRoot);
        assertEquals(0, state.lastResult.exitCode(), () -> "put-object should succeed: " + state.lastResult.combinedOutput());
        assertManifestChecksumValid(artifactsFor(bucket, key).manifestFile());
    }

    @Then("the S3 client can immediately read bucket {string} and key {string} and receive the exact original bytes from fixture file {string}")
    public void clientCanImmediatelyReadExactBytes(String bucket, String key, String fixtureFile) throws Exception {
        byte[] downloaded = getObjectBytes(bucket, key);
        assertArrayEquals(state.fixtureBytes.get(fixtureFile), downloaded);
    }

    @Then("the storage engine detects the checksum mismatch for the corrupted chunk before returning any response bytes")
    public void storageEngineDetectsCorruptedChunk() {
        assertNotNull(state.corruptedRuntimeArtifact, "corruption step should record the corrupted chunk artifact");
        assertNotEquals(readString(state.corruptedRuntimeArtifact.resolveSibling(state.corruptedRuntimeArtifact.getFileName() + ".sha256")).trim(),
            sha256Hex(readBytes(state.corruptedRuntimeArtifact)), "test fixture should have a real chunk checksum mismatch");
        assertGetObjectFailedWithoutOriginalBytes();
    }

    @Then("the S3 HTTP GetObject response signals an object integrity failure rather than returning corrupted bytes as a successful object body")
    public void getObjectSignalsObjectIntegrityFailure() {
        assertIntegrityErrorResult();
    }

    @Then("the storage engine detects the manifest checksum mismatch before loading any chunk or returning any response bytes")
    public void storageEngineDetectsManifestChecksumMismatch() {
        assertNotNull(state.corruptedRuntimeArtifact, "corruption step should record the corrupted manifest artifact");
        assertManifestChecksumInvalid(state.corruptedRuntimeArtifact);
        assertGetObjectFailedWithoutOriginalBytes();
    }

    @Then("the S3 HTTP GetObject response signals a manifest integrity failure rather than returning bytes guided by a corrupted manifest")
    public void getObjectSignalsManifestIntegrityFailure() {
        assertIntegrityErrorResult();
    }

    @Then("both PutObject requests complete with HTTP 200")
    public void bothPutObjectRequestsCompleteWithHttp200() {
        assertEquals(2, state.concurrentPutResults.size());
        state.concurrentPutResults.forEach(result -> assertEquals(0, result.exitCode(),
            () -> "put-object should succeed for " + result.put().key() + ": " + result.output()));
    }

    @Then("the S3 HTTP GetObject for bucket {string} key {string} returns the exact bytes from fixture file {string}")
    public void getObjectReturnsExactBytesFromFixture(String bucket, String key, String fixtureFile) throws Exception {
        assertArrayEquals(state.fixtureBytes.get(fixtureFile), getObjectBytes(bucket, key));
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
        assertTrue(state.concurrentPutResults.stream().anyMatch(result -> result.exitCode() == 0),
            () -> "expected at least one successful PUT but got " + state.concurrentPutResults);
    }

    @Then("after all concurrent requests complete, the S3 HTTP GetObject for bucket {string} key {string} returns a complete non-corrupted object body")
    public void afterConcurrentRequestsGetObjectReturnsCompleteBody(String bucket, String key) throws Exception {
        state.lastDownloadedBytes = getObjectBytes(bucket, key);
        assertTrue(state.lastDownloadedBytes.length > 0, "winner body should be complete and non-empty");
    }

    @Then("the returned object body exactly matches either fixture file {string} or fixture file {string} — not a mix of bytes from both")
    public void returnedBodyMatchesOneFixture(String fixtureFileA, String fixtureFileB) {
        byte[] body = state.lastDownloadedBytes;
        boolean matchesA = Arrays.equals(body, state.fixtureBytes.get(fixtureFileA));
        boolean matchesB = Arrays.equals(body, state.fixtureBytes.get(fixtureFileB));
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

    private void assertGetObjectMissing(String bucket, String key) throws Exception {
        Path out = Files.createTempFile("magrathea-phase2-missing-", ".bin");
        AwsResult result = runAws("get-object", "--bucket", bucket, "--key", key, out.toString());
        assertNotEquals(0, result.exitCode(), "object should not exist before scenario action");
        Files.deleteIfExists(out);
    }

    private byte[] getObjectBytes(String bucket, String key) throws Exception {
        Path out = Files.createTempFile("magrathea-phase2-download-", ".bin");
        AwsResult result = runAws("get-object", "--bucket", bucket, "--key", key, out.toString());
        assertEquals(0, result.exitCode(), () -> "get-object should succeed: " + result.combinedOutput());
        byte[] bytes = Files.readAllBytes(out);
        Files.deleteIfExists(out);
        return bytes;
    }

    private AwsResult putObject(String bucket, String key, byte[] bytes) throws Exception {
        return putObjectWithHeaders(bucket, key, bytes, Map.of());
    }

    private AwsResult putObjectWithHeaders(String bucket, String key, byte[] bytes,
                                            Map<String, String> extraHeaders) throws Exception {
        Path body = Files.createTempFile("magrathea-phase2-put-", ".bin");
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
                        case "content-type"                 -> { args.add("--content-type");          args.add(value); }
                        case "x-amz-storage-class"          -> { args.add("--storage-class");         args.add(value); }
                        case "x-amz-server-side-encryption" -> { args.add("--server-side-encryption"); args.add(value); }
                        case "x-amz-checksum-sha256"        -> { args.add("--checksum-sha256");        args.add(value); }
                        case "x-amz-sdk-checksum-algorithm" -> { args.add("--checksum-algorithm");     args.add(value); }
                        case "content-md5"                  -> { args.add("--content-md5");            args.add(value); }
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
        ProcessBuilder processBuilder = new ProcessBuilder(command);
        Map<String, String> env = processBuilder.environment();
        env.putIfAbsent("AWS_ACCESS_KEY_ID", "test");
        env.putIfAbsent("AWS_SECRET_ACCESS_KEY", "test");
        env.putIfAbsent("AWS_DEFAULT_REGION", "us-east-1");
        env.putIfAbsent("AWS_EC2_METADATA_DISABLED", "true");
        Process process = processBuilder.start();
        CompletableFuture<String> stdout = CompletableFuture.supplyAsync(() -> readProcessStream(process.getInputStream()));
        CompletableFuture<String> stderr = CompletableFuture.supplyAsync(() -> readProcessStream(process.getErrorStream()));
        boolean finished = process.waitFor(AWS_CLI_TIMEOUT.toSeconds(), TimeUnit.SECONDS);
        if (!finished) {
            process.destroyForcibly();
            fail("AWS CLI command timed out after " + AWS_CLI_TIMEOUT.toSeconds() + " seconds: " + String.join(" ", command));
        }
        return new AwsResult(process.exitValue(), stdout.join(), stderr.join());
    }

    private static String readProcessStream(InputStream stream) {
        try (stream) {
            return new String(stream.readAllBytes(), StandardCharsets.UTF_8);
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    private void resetRepositories() {
        // Bucket registry, object references and multipart state are durable on the
        // storage-engine filesystem (EP-2). After the symlink swap to a fresh scenario
        // root, reloading from disk yields a clean, empty repository view without
        // destroying durable state from the scenario itself.
        bucketRepository.ifAvailable(StorageEngineReactiveBucketRepository::reloadFromDisk);
        objectRepository.ifAvailable(StorageEngineReactiveS3ObjectRepository::reloadFromDisk);
        multipartRepository.ifAvailable(StorageEngineReactiveMultipartUploadRepository::reloadFromDisk);
        bucketConfigHandler.ifAvailable(S3BucketConfigHandler::resetInMemoryConfigurations);
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
        int chunkCount = Integer.parseInt(manifest.getProperty("chunkCount", "0"));
        List<Path> chunks = new ArrayList<>();
        for (int i = 0; i < chunkCount; i++) {
            String chunkId = manifest.getProperty("chunk." + i + ".chunkId");
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

    private void assertManifestChecksumInvalid(Path manifestFile) {
        String raw = readString(manifestFile);
        String marker = "\nmanifest.checksum=";
        int index = raw.lastIndexOf(marker);
        assertTrue(index >= 0, "manifest should contain checksum trailer: " + manifestFile);
        String contentForVerification = raw.substring(0, index + 1);
        String stored = raw.substring(index + marker.length()).trim();
        assertNotEquals(stored, sha256Hex(contentForVerification), "test fixture should have a real manifest checksum mismatch");
    }

    private void assertGetObjectFailedWithoutOriginalBytes() {
        assertNotNull(state.lastResult, "GetObject should have been invoked");
        assertNotEquals(0, state.lastResult.exitCode(),
            () -> "corrupted object GetObject should fail, output: " + state.lastResult.combinedOutput());
        byte[] original = state.fixtureBytes.get(state.fixtureFile);
        assertFalse(Arrays.equals(original, state.lastDownloadedBytes),
            "corrupted object bytes must not be returned as a successful body");
    }

    private void assertIntegrityErrorResult() {
        assertGetObjectFailedWithoutOriginalBytes();
        String output = state.lastResult.combinedOutput();
        assertTrue(output.contains("XAmzChecksumMismatch")
                || output.toLowerCase().contains("checksum")
                || output.toLowerCase().contains("integrity")
                || output.toLowerCase().contains("unknown"),
            () -> "expected integrity error or non-zero S3 error output from AWS CLI but got: " + output);
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

    private static AwsCliPreflight checkAwsCliPreflight() {
        try {
            Process process = new ProcessBuilder("aws", "--version").start();
            boolean finished = process.waitFor(5, TimeUnit.SECONDS);
            if (!finished) {
                process.destroyForcibly();
                return new AwsCliPreflight(false, "AWS CLI preflight failed: `aws --version` timed out");
            }
            String stdout = new String(process.getInputStream().readAllBytes(), StandardCharsets.UTF_8);
            String stderr = new String(process.getErrorStream().readAllBytes(), StandardCharsets.UTF_8);
            if (process.exitValue() != 0) {
                return new AwsCliPreflight(false,
                    "AWS CLI preflight failed: `aws --version` exited " + process.exitValue() + " " + stdout + stderr);
            }
            return new AwsCliPreflight(true, (stdout + stderr).trim());
        } catch (IOException e) {
            return new AwsCliPreflight(false,
                "AWS CLI binary not available on PATH: " + e.getMessage());
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            return new AwsCliPreflight(false,
                "AWS CLI preflight interrupted while running `aws --version`");
        }
    }

    private static final class State {
        String requirementId;
        Path storageRoot;
        String bucket;
        String objectKey;
        String fixtureFile;
        AwsResult lastResult;
        byte[] lastDownloadedBytes = new byte[0];
        Path lastDownloadedFile;
        final Map<String, byte[]> fixtureBytes = new java.util.HashMap<>();
        final List<PreparedPut> preparedPuts = new ArrayList<>();
        List<PutResult> concurrentPutResults = List.of();
        Path corruptedRuntimeArtifact;

        void reset() {
            requirementId = null;
            storageRoot = null;
            bucket = null;
            objectKey = null;
            fixtureFile = null;
            lastResult = null;
            lastDownloadedBytes = new byte[0];
            if (lastDownloadedFile != null) {
                try {
                    Files.deleteIfExists(lastDownloadedFile);
                } catch (IOException e) {
                    throw new UncheckedIOException(e);
                }
            }
            lastDownloadedFile = null;
            fixtureBytes.clear();
            preparedPuts.clear();
            concurrentPutResults = List.of();
            corruptedRuntimeArtifact = null;
        }
    }

    private record AwsCliPreflight(boolean available, String reason) {
    }

    private record AwsResult(int exitCode, String stdout, String stderr) {
        String combinedOutput() {
            return StreamSafe.join(stdout, stderr);
        }
    }

    private record PreparedPut(String bucket, String key, String fixtureFile, byte[] bytes) {
    }

    private record PutResult(PreparedPut put, int exitCode, String output) {
    }

    private record ArtifactSet(Path referenceFile, Path manifestFile, List<Path> chunkFiles) {
    }

    private static final class StreamSafe {
        static String join(String stdout, String stderr) {
            return (Objects.toString(stdout, "") + Objects.toString(stderr, "")).trim();
        }
    }
}
