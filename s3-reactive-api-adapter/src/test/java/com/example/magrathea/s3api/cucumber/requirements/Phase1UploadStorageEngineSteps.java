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

    private Response lastObservedRead;

    @io.cucumber.java.Before
    public void resetPhase1State() {
        lastObservedRead = null;
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
        int chunkCount = Integer.parseInt(manifest.getProperty("chunkCount", "0"));
        assertTrue(chunkCount > 0, "manifest should reference at least one chunk");
        for (int i = 0; i < chunkCount; i++) {
            assertNotNull(manifest.getProperty("chunk." + i + ".finalChecksum.algorithm"));
            assertNotNull(manifest.getProperty("chunk." + i + ".finalChecksum.value"));
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
        bucketRepository.ifAvailable(StorageEngineReactiveBucketRepository::reset);
        objectRepository.ifAvailable(StorageEngineReactiveS3ObjectRepository::reset);
        multipartRepository.ifAvailable(StorageEngineReactiveMultipartUploadRepository::reset);
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
