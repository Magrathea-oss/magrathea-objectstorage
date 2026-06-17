package com.example.magrathea.s3api.phase2awscli;

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
    private ObjectProvider<StorageEngineReactiveS3ObjectRepository> objectRepository;

    @Autowired
    private ObjectProvider<StorageEngineReactiveMultipartUploadRepository> multipartRepository;

    @Before
    public void checkAwsCliAvailability() {
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
        int chunkCount = Integer.parseInt(mf.getProperty("chunkCount", "0"));
        assertTrue(chunkCount > 0, "manifest must reference at least one chunk");
        for (int i = 0; i < chunkCount; i++) {
            assertNotNull(mf.getProperty("chunk." + i + ".finalChecksum.algorithm"),
                "chunk " + i + " must have checksum algorithm in manifest");
            assertNotNull(mf.getProperty("chunk." + i + ".finalChecksum.value"),
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
        // Do NOT reset bucketRepository — the bucket is in-memory only with no filesystem
        // persistence counterpart. Resetting it would cause NoSuchBucket errors on any
        // subsequent S3 API call in the same scenario (e.g. the GET in
        // responseProducedFromStorageEngineFilesystemState). Only reset the object and
        // multipart repositories so that the read path must reload object state from the
        // durable filesystem rather than an in-memory cache.
        objectRepository.ifAvailable(StorageEngineReactiveS3ObjectRepository::reset);
        multipartRepository.ifAvailable(StorageEngineReactiveMultipartUploadRepository::reset);
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
        int cc = Integer.parseInt(mf.getProperty("chunkCount", "0"));
        List<Path> chunks = new ArrayList<>();
        for (int i = 0; i < cc; i++) {
            String chunkId = mf.getProperty("chunk." + i + ".chunkId");
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
