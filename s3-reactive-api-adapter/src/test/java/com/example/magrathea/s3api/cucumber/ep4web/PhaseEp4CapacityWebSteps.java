package com.example.magrathea.s3api.cucumber.ep4web;

import com.example.magrathea.s3api.cucumber.ep4.Ep4CapacityTestSupport.CapacityEventRecorder;
import com.example.magrathea.s3api.cucumber.requirements.RequirementsTestApp;
import com.example.magrathea.storageengine.infrastructure.filesystem.FileSystemBucketCapacityStore;
import io.cucumber.java.After;
import io.cucumber.java.Before;
import io.cucumber.java.en.Given;
import io.cucumber.java.en.Then;
import io.cucumber.java.en.When;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.http.HttpHeaders;
import org.springframework.test.web.reactive.server.WebTestClient;
import org.springframework.web.reactive.function.server.HandlerStrategies;
import org.springframework.web.reactive.function.server.RouterFunction;
import org.springframework.web.reactive.function.server.ServerResponse;
import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;

import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.Arrays;
import java.util.HexFormat;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

import static org.junit.jupiter.api.Assertions.*;

public class PhaseEp4CapacityWebSteps {
    private static final String BUCKET = "capacity-protection-bucket";
    private static final String EXISTING_KEY = "capacity/2026/existing.bin";
    private static final int TWO_MIB = 2 * 1024 * 1024;

    @Autowired private WebTestClient webTestClient;
    @Autowired private RequirementsTestApp.MutableFileSystemWriteFaultInjector faultInjector;
    @Autowired private CapacityEventRecorder events;
    @Autowired @Qualifier("adminRoutes") private RouterFunction<ServerResponse> adminRoutes;

    private WebTestClient adminClient;
    private final Set<String> keys = ConcurrentHashMap.newKeySet();
    private byte[] existingBytes;
    private byte[] uploadBytes;
    private Response uploadResponse;
    private List<Integer> concurrentStatuses = List.of();
    private Capacity capacity;
    private Set<Path> filesBeforeFailure = Set.of();
    private String rejectedKey;

    @Before
    public void reset() {
        faultInjector.disable();
        events.reset();
        keys.clear();
        adminClient = WebTestClient.bindToRouterFunction(adminRoutes)
            .handlerStrategies(HandlerStrategies.withDefaults()).build();
    }

    @After
    public void cleanup() {
        faultInjector.disable();
        keys.forEach(this::deleteObjectIgnoringStatus);
    }

    @Given("the S3 API is configured with profile {string} and backend {string}")
    public void configured(String profile, String backend) {
        assertEquals("storage-engine-it", profile);
        assertEquals("storage-engine", backend);
    }

    @Given("bucket {string} exists")
    public void bucketExists(String bucket) {
        assertEquals(BUCKET, bucket);
        int status = webTestClient.put().uri("/" + bucket).exchange()
            .expectBody().returnResult().getStatus().value();
        assertTrue(status == 200 || status == 409, "bucket setup must create or reuse the test bucket");
    }

    @Given("object key {string} contains committed fixture {string}")
    public void existingObject(String key, String fixture) {
        existingBytes = readFixture(fixture);
        put(key, existingBytes).expectStatus().isOk();
        keys.add(key);
    }

    @Given("validation mode {string} is selected for requirement {string}")
    public void validationMode(String mode, String requirement) {
        assertEquals("webclient", mode);
        assertTrue(Set.of("REQ-QUOTA-001", "REQ-QUOTA-002", "REQ-CAPACITY-001").contains(requirement));
    }

    @Given("the administrator configures bucket {string} with byte quota {long}")
    public void configureQuota(String bucket, long quota) {
        capacity = putQuota(bucket, quota);
        assertEquals(quota, capacity.quotaBytes());
        assertEquals(existingBytes.length, capacity.usedBytes());
        assertEquals(0, capacity.reservedBytes());
    }

    @Given("fixture {string} is exactly {long} bytes")
    public void exactFixture(String path, long size) {
        uploadBytes = deterministicBytes(Math.toIntExact(size), (byte) 0x41);
        writeFixture(path, uploadBytes);
        assertEquals(size, fileSize(path));
    }

    @When("the S3 client uploads the fixture to key {string}")
    public void uploadFixture(String key) {
        rejectedKey = key;
        filesBeforeFailure = regularFiles();
        uploadResponse = response(put(key, uploadBytes));
    }

    @Then("PutObject returns the documented S3 quota-exceeded error")
    public void quotaError() {
        assertEquals(507, uploadResponse.status());
        assertTrue(uploadResponse.body().contains("<Code>QuotaExceeded</Code>"), uploadResponse.body());
    }

    @Then("no object reference, manifest, whole-object unit, multipart part, dedup chunk, or EC shard is published for the rejected key")
    public void noPublishedArtifact() {
        assertEquals(404, get(rejectedKey).status());
        assertEquals(filesBeforeFailure, regularFiles(), "quota rejection must publish no filesystem artifact");
        assertNoReference(rejectedKey);
    }

    @Then("the previously committed object remains readable with its original checksum")
    public void existingIntegrity() {
        Response response = get(EXISTING_KEY);
        assertEquals(200, response.status(), response.body());
        assertEquals(sha256(existingBytes), sha256(response.bytes()));
    }

    @Then("the Admin API reports used bytes, reserved bytes, quota bytes, and the rejected reservation")
    public void adminReportsRejection() {
        Capacity report = getCapacity(BUCKET);
        assertEquals(existingBytes.length, report.usedBytes());
        assertEquals(0, report.reservedBytes());
        assertEquals(capacity.quotaBytes(), report.quotaBytes());
        assertTrue(report.rejectedReservations() >= capacity.rejectedReservations() + 1);
        assertEquals(uploadBytes.length, report.lastRejectedBytes());
    }

    @Given("bucket {string} has quota for exactly one 2 MiB object beyond current usage")
    public void exactOneObjectQuota(String bucket) {
        capacity = putQuota(bucket, existingBytes.length + TWO_MIB);
    }

    @When("two clients concurrently upload distinct 2 MiB keys")
    public void concurrentUploads() {
        byte[] bytes = deterministicBytes(TWO_MIB, (byte) 0x5a);
        String a = "capacity/2026/concurrent-a.bin";
        String b = "capacity/2026/concurrent-b.bin";
        keys.add(a); keys.add(b);
        Mono<Integer> first = Mono.fromCallable(() -> response(put(a, bytes)).status())
            .subscribeOn(reactor.core.scheduler.Schedulers.boundedElastic());
        Mono<Integer> second = Mono.fromCallable(() -> response(put(b, bytes)).status())
            .subscribeOn(reactor.core.scheduler.Schedulers.boundedElastic());
        StepVerifier.create(Mono.zip(first, second))
            .assertNext(result -> concurrentStatuses = List.of(result.getT1(), result.getT2()))
            .verifyComplete();
    }

    @Then("exactly one upload commits and exactly one receives the documented quota-exceeded error")
    public void oneWinner() {
        assertEquals(1, concurrentStatuses.stream().filter(status -> status == 200).count());
        assertEquals(1, concurrentStatuses.stream().filter(status -> status == 507).count());
        long readable = List.of("capacity/2026/concurrent-a.bin", "capacity/2026/concurrent-b.bin")
            .stream().filter(key -> get(key).status() == 200).count();
        assertEquals(1, readable, "exactly one distinct key must be committed");
    }

    @Then("reported used plus reserved bytes never exceeds the configured quota")
    public void noOversubscription() {
        Capacity report = getCapacity(BUCKET);
        assertTrue(report.usedBytes() + report.reservedBytes() <= report.quotaBytes());
        assertEquals(existingBytes.length + TWO_MIB, report.usedBytes());
        assertEquals(0, report.reservedBytes());
    }

    @Then("restart preserves the committed usage and releases the failed reservation")
    public void restartCapacity() {
        Capacity report = getCapacity(BUCKET);
        FileSystemBucketCapacityStore restarted = new FileSystemBucketCapacityStore(PhaseEp4CapacityWebCucumberConfig.ROOT);
        StepVerifier.create(restarted.capacity(BUCKET)).assertNext(reloaded -> {
            assertEquals(report.usedBytes(), reloaded.usedBytes());
            assertEquals(0, reloaded.reservedBytes());
            assertEquals(report.quotaBytes(), reloaded.quotaBytes());
        }).verifyComplete();
    }

    @Given("deterministic fault injection reports ENOSPC after at least one temporary artifact is written")
    public void injectEnospc() {
        putQuota(BUCKET, existingBytes.length + 16L * 1024 * 1024);
        events.reset();
        faultInjector.enospcAfterNextChunkTempWrite();
    }

    @When("the S3 client uploads fixture {string} to key {string}")
    public void uploadEnospc(String path, String key) {
        uploadBytes = deterministicBytes(8 * 1024 * 1024, (byte) 0x33);
        writeFixture(path, uploadBytes);
        rejectedKey = key;
        filesBeforeFailure = regularFiles();
        uploadResponse = response(put(key, uploadBytes));
    }

    @Then("PutObject returns the documented storage-capacity S3 error rather than success")
    public void enospcError() {
        assertEquals(507, uploadResponse.status());
        assertTrue(uploadResponse.body().contains("<Code>InsufficientStorage</Code>"), uploadResponse.body());
    }

    @Then("every temporary file and unpublished storage artifact for the failed key is removed or quarantined")
    public void enospcCleanup() {
        assertEquals(filesBeforeFailure, regularFiles(), "ENOSPC must clean all newly created unpublished artifacts");
        assertTrue(regularFiles().stream().noneMatch(path -> path.getFileName().toString().contains(".tmp.")));
    }

    @Then("no manifest or object reference is committed for the failed key")
    public void noFailedReference() {
        assertEquals(404, get(rejectedKey).status());
        assertNoReference(rejectedKey);
    }

    @Then("a capacity failure event exposes backend, storage root, requested bytes, and available bytes without payload content")
    public void capacityEvent() {
        Map<String, String> fields = events.capacityFailureFields();
        assertEquals("storage-engine", fields.get("backend"));
        assertEquals(PhaseEp4CapacityWebCucumberConfig.ROOT.toString(), fields.get("storage.root"));
        assertNotNull(fields.get("requested.bytes"));
        assertEquals("0", fields.get("available.bytes"));
        assertFalse(fields.toString().contains(HexFormat.of().formatHex(Arrays.copyOf(uploadBytes, 32))));
        assertFalse(fields.containsKey("object.key"));
    }

    private WebTestClient.ResponseSpec put(String key, byte[] bytes) {
        return webTestClient.put().uri("/" + BUCKET + "/" + key)
            .header("x-amz-storage-class", "PLAIN")
            .header(HttpHeaders.CONTENT_LENGTH, Integer.toString(bytes.length))
            .bodyValue(bytes).exchange();
    }

    private Response get(String key) {
        var result = webTestClient.get().uri("/" + BUCKET + "/" + key).exchange()
            .expectBody(byte[].class).returnResult();
        return new Response(result.getStatus().value(), result.getResponseBody() == null ? new byte[0] : result.getResponseBody());
    }

    private Response response(WebTestClient.ResponseSpec spec) {
        var result = spec.expectBody(byte[].class).returnResult();
        byte[] body = result.getResponseBody() == null ? new byte[0] : result.getResponseBody();
        return new Response(result.getStatus().value(), body);
    }

    private Capacity putQuota(String bucket, long quota) {
        var result = adminClient.put().uri("/admin/buckets/" + bucket + "/quota")
            .bodyValue(Map.of("quotaBytes", quota)).exchange().expectStatus().isOk()
            .expectBody(String.class).returnResult();
        return parseCapacity(result.getResponseBody());
    }

    private Capacity getCapacity(String bucket) {
        var result = adminClient.get().uri("/admin/buckets/" + bucket + "/capacity")
            .exchange().expectStatus().isOk().expectBody(String.class).returnResult();
        return parseCapacity(result.getResponseBody());
    }

    private void deleteObjectIgnoringStatus(String key) {
        webTestClient.delete().uri("/" + BUCKET + "/" + key).exchange().expectBody().returnResult();
    }

    private void assertNoReference(String key) {
        Path root = PhaseEp4CapacityWebCucumberConfig.ROOT.resolve("metadata/s3-object-references");
        if (!Files.isDirectory(root)) return;
        try (var paths = Files.walk(root)) {
            assertTrue(paths.filter(Files::isRegularFile).noneMatch(path -> readString(path).contains(key)));
        } catch (IOException error) { throw new UncheckedIOException(error); }
    }

    private Set<Path> regularFiles() {
        try (var paths = Files.walk(PhaseEp4CapacityWebCucumberConfig.ROOT)) {
            return Set.copyOf(paths.filter(Files::isRegularFile).map(PhaseEp4CapacityWebCucumberConfig.ROOT::relativize).toList());
        } catch (IOException error) { throw new UncheckedIOException(error); }
    }

    private static Capacity parseCapacity(String json) {
        return new Capacity(number(json, "usedBytes"), number(json, "reservedBytes"), number(json, "quotaBytes"),
            number(json, "rejectedReservations"), number(json, "lastRejectedBytes"));
    }

    private static long number(String json, String name) {
        var matcher = java.util.regex.Pattern.compile("\\\"" + name + "\\\"\\s*:\\s*(-?\\d+)").matcher(json);
        if (!matcher.find()) throw new AssertionError("Missing " + name + " in " + json);
        return Long.parseLong(matcher.group(1));
    }

    private static byte[] deterministicBytes(int size, byte value) { byte[] bytes = new byte[size]; Arrays.fill(bytes, value); return bytes; }
    private static void writeFixture(String path, byte[] bytes) { try { Path file = Path.of(path); Files.createDirectories(file.getParent()); Files.write(file, bytes); } catch (IOException e) { throw new UncheckedIOException(e); } }
    private static long fileSize(String path) { try { return Files.size(Path.of(path)); } catch (IOException e) { throw new UncheckedIOException(e); } }
    private static String readString(Path path) { try { return Files.readString(path); } catch (IOException e) { throw new UncheckedIOException(e); } }
    private static byte[] readFixture(String fixture) { try (InputStream in = Thread.currentThread().getContextClassLoader().getResourceAsStream(fixture)) { if (in == null) throw new IOException("Missing fixture " + fixture); return in.readAllBytes(); } catch (IOException e) { throw new UncheckedIOException(e); } }
    private static String sha256(byte[] bytes) { try { return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(bytes)); } catch (NoSuchAlgorithmException e) { throw new IllegalStateException(e); } }

    private record Response(int status, byte[] bytes) { String body() { return new String(bytes, StandardCharsets.UTF_8); } }
    private record Capacity(long usedBytes, long reservedBytes, long quotaBytes, long rejectedReservations, long lastRejectedBytes) { }
}
