package com.example.magrathea.s3api.cucumber.ep4awscli;

import com.example.magrathea.s3api.cucumber.ep4.Ep4CapacityTestSupport.CapacityEventRecorder;
import com.example.magrathea.s3api.cucumber.requirements.RequirementsTestApp;
import com.example.magrathea.storageengine.infrastructure.filesystem.FileSystemBucketCapacityStore;
import io.cucumber.java.After;
import io.cucumber.java.Before;
import io.cucumber.java.en.Given;
import io.cucumber.java.en.Then;
import io.cucumber.java.en.When;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.web.server.LocalServerPort;
import reactor.test.StepVerifier;

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
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Duration;
import java.util.Arrays;
import java.util.HexFormat;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.*;

public class PhaseEp4CapacityAwsCliSteps {
    private static final String BUCKET = "capacity-protection-bucket";
    private static final String EXISTING_KEY = "capacity/2026/existing.bin";
    private static final int TWO_MIB = 2 * 1024 * 1024;
    private static final Duration TIMEOUT = Duration.ofSeconds(60);

    @LocalServerPort private int port;
    @Autowired private RequirementsTestApp.MutableFileSystemWriteFaultInjector faultInjector;
    @Autowired private CapacityEventRecorder events;

    private final HttpClient http = HttpClient.newHttpClient();
    private final Set<String> keys = ConcurrentHashMap.newKeySet();
    private byte[] existingBytes;
    private byte[] uploadBytes;
    private AwsResult uploadResult;
    private List<AwsResult> concurrentResults = List.of();
    private Capacity capacity;
    private Set<Path> filesBeforeFailure = Set.of();
    private String rejectedKey;

    @Before
    public void reset() {
        AwsResult version = run(List.of("aws", "--version"));
        assertEquals(0, version.exitCode(), "AWS CLI is mandatory for the EP-4 AWS CLI validation: " + version.output());
        faultInjector.disable();
        events.reset();
        keys.clear();
    }

    @After
    public void cleanup() {
        faultInjector.disable();
        keys.forEach(key -> runAws("delete-object", "--bucket", BUCKET, "--key", key));
    }

    @Given("the S3 API is configured with profile {string} and backend {string}")
    public void configured(String profile, String backend) { assertEquals("storage-engine-it", profile); assertEquals("storage-engine", backend); }

    @Given("bucket {string} exists")
    public void bucketExists(String bucket) {
        AwsResult result = runAws("create-bucket", "--bucket", bucket);
        assertTrue(result.exitCode() == 0 || result.output().contains("BucketAlreadyExists"), result.output());
    }

    @Given("object key {string} contains committed fixture {string}")
    public void existingObject(String key, String fixture) {
        existingBytes = readFixture(fixture);
        Path file = writeTemp("existing", existingBytes);
        AwsResult result = runAws("put-object", "--bucket", BUCKET, "--key", key, "--body", file.toString(), "--storage-class", "PLAIN");
        assertEquals(0, result.exitCode(), result.output());
        keys.add(key);
    }

    @Given("validation mode {string} is selected for requirement {string}")
    public void validationMode(String mode, String requirement) {
        assertEquals("awscli", mode);
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
        writeFile(Path.of(path), uploadBytes);
        assertEquals(size, fileSize(Path.of(path)));
    }

    @When("the S3 client uploads the fixture to key {string}")
    public void uploadFixture(String key) {
        rejectedKey = key;
        filesBeforeFailure = regularFiles();
        uploadResult = runAws("put-object", "--bucket", BUCKET, "--key", key,
            "--body", "target/test-fixtures/capacity/quota-overflow-2m.bin", "--storage-class", "PLAIN");
    }

    @Then("PutObject returns the documented S3 quota-exceeded error")
    public void quotaError() { assertNotEquals(0, uploadResult.exitCode()); assertTrue(uploadResult.output().contains("QuotaExceeded"), uploadResult.output()); }

    @Then("no object reference, manifest, whole-object unit, multipart part, dedup chunk, or EC shard is published for the rejected key")
    public void noPublishedArtifact() {
        assertNotEquals(0, head(rejectedKey).exitCode());
        assertEquals(filesBeforeFailure, regularFiles());
        assertNoReference(rejectedKey);
    }

    @Then("the previously committed object remains readable with its original checksum")
    public void existingIntegrity() {
        Path output = outputPath("existing-read");
        AwsResult result = runAws("get-object", "--bucket", BUCKET, "--key", EXISTING_KEY, output.toString());
        assertEquals(0, result.exitCode(), result.output());
        assertEquals(sha256(existingBytes), sha256(readBytes(output)));
    }

    @Then("the Admin API reports used bytes, reserved bytes, quota bytes, and the rejected reservation")
    public void adminReportsRejection() {
        Capacity report = getCapacity(BUCKET);
        assertEquals(existingBytes.length, report.usedBytes()); assertEquals(0, report.reservedBytes());
        assertEquals(capacity.quotaBytes(), report.quotaBytes());
        assertTrue(report.rejectedReservations() >= capacity.rejectedReservations() + 1);
        assertEquals(uploadBytes.length, report.lastRejectedBytes());
    }

    @Given("bucket {string} has quota for exactly one 2 MiB object beyond current usage")
    public void exactOneObjectQuota(String bucket) { capacity = putQuota(bucket, existingBytes.length + TWO_MIB); }

    @When("two clients concurrently upload distinct 2 MiB keys")
    public void concurrentUploads() {
        Path body = writeTemp("concurrent", deterministicBytes(TWO_MIB, (byte) 0x5a));
        String a = "capacity/2026/concurrent-a.bin", b = "capacity/2026/concurrent-b.bin";
        keys.add(a); keys.add(b);
        CompletableFuture<AwsResult> first = CompletableFuture.supplyAsync(() -> runAws("put-object", "--bucket", BUCKET, "--key", a, "--body", body.toString(), "--storage-class", "PLAIN"));
        CompletableFuture<AwsResult> second = CompletableFuture.supplyAsync(() -> runAws("put-object", "--bucket", BUCKET, "--key", b, "--body", body.toString(), "--storage-class", "PLAIN"));
        concurrentResults = CompletableFuture.allOf(first, second).thenApply(ignored -> List.of(first.join(), second.join())).join();
    }

    @Then("exactly one upload commits and exactly one receives the documented quota-exceeded error")
    public void oneWinner() {
        assertEquals(1, concurrentResults.stream().filter(result -> result.exitCode() == 0).count(), concurrentResults.toString());
        assertEquals(1, concurrentResults.stream().filter(result -> result.exitCode() != 0 && result.output().contains("QuotaExceeded")).count(), concurrentResults.toString());
        assertEquals(1, List.of("capacity/2026/concurrent-a.bin", "capacity/2026/concurrent-b.bin").stream().filter(key -> head(key).exitCode() == 0).count());
    }

    @Then("reported used plus reserved bytes never exceeds the configured quota")
    public void noOversubscription() {
        Capacity report = getCapacity(BUCKET);
        assertTrue(report.usedBytes() + report.reservedBytes() <= report.quotaBytes());
        assertEquals(existingBytes.length + TWO_MIB, report.usedBytes()); assertEquals(0, report.reservedBytes());
    }

    @Then("restart preserves the committed usage and releases the failed reservation")
    public void restartCapacity() {
        Capacity report = getCapacity(BUCKET);
        FileSystemBucketCapacityStore restarted = new FileSystemBucketCapacityStore(PhaseEp4CapacityAwsCliCucumberConfig.ROOT);
        StepVerifier.create(restarted.capacity(BUCKET)).assertNext(reloaded -> {
            assertEquals(report.usedBytes(), reloaded.usedBytes()); assertEquals(0, reloaded.reservedBytes()); assertEquals(report.quotaBytes(), reloaded.quotaBytes());
        }).verifyComplete();
    }

    @Given("deterministic fault injection reports ENOSPC after at least one temporary artifact is written")
    public void injectEnospc() { putQuota(BUCKET, existingBytes.length + 16L * 1024 * 1024); events.reset(); faultInjector.enospcAfterNextChunkTempWrite(); }

    @When("the S3 client uploads fixture {string} to key {string}")
    public void uploadEnospc(String path, String key) {
        uploadBytes = deterministicBytes(8 * 1024 * 1024, (byte) 0x33); writeFile(Path.of(path), uploadBytes);
        rejectedKey = key; filesBeforeFailure = regularFiles();
        uploadResult = runAws("put-object", "--bucket", BUCKET, "--key", key, "--body", path, "--storage-class", "PLAIN");
    }

    @Then("PutObject returns the documented storage-capacity S3 error rather than success")
    public void enospcError() { assertNotEquals(0, uploadResult.exitCode()); assertTrue(uploadResult.output().contains("InsufficientStorage"), uploadResult.output()); }

    @Then("every temporary file and unpublished storage artifact for the failed key is removed or quarantined")
    public void enospcCleanup() { assertEquals(filesBeforeFailure, regularFiles()); assertTrue(regularFiles().stream().noneMatch(path -> path.getFileName().toString().contains(".tmp."))); }

    @Then("no manifest or object reference is committed for the failed key")
    public void noFailedReference() { assertNotEquals(0, head(rejectedKey).exitCode()); assertNoReference(rejectedKey); }

    @Then("a capacity failure event exposes backend, storage root, requested bytes, and available bytes without payload content")
    public void capacityEvent() {
        Map<String, String> fields = events.capacityFailureFields();
        assertEquals("storage-engine", fields.get("backend")); assertEquals(PhaseEp4CapacityAwsCliCucumberConfig.ROOT.toString(), fields.get("storage.root"));
        assertNotNull(fields.get("requested.bytes")); assertEquals("0", fields.get("available.bytes"));
        assertFalse(fields.toString().contains(HexFormat.of().formatHex(Arrays.copyOf(uploadBytes, 32)))); assertFalse(fields.containsKey("object.key"));
    }

    private AwsResult head(String key) { return runAws("head-object", "--bucket", BUCKET, "--key", key); }

    private AwsResult runAws(String... operationArgs) {
        java.util.ArrayList<String> command = new java.util.ArrayList<>(List.of("aws", "s3api"));
        command.addAll(List.of(operationArgs));
        command.addAll(List.of("--endpoint-url", endpoint(), "--region", "us-east-1", "--no-sign-request", "--no-cli-pager"));
        return run(command);
    }

    private AwsResult run(List<String> command) {
        try {
            Process process = new ProcessBuilder(command).redirectErrorStream(true).start();
            boolean completed = process.waitFor(TIMEOUT.toSeconds(), TimeUnit.SECONDS);
            if (!completed) { process.destroyForcibly(); fail("AWS CLI timed out: " + command); }
            return new AwsResult(process.exitValue(), new String(process.getInputStream().readAllBytes(), StandardCharsets.UTF_8));
        } catch (IOException error) { throw new UncheckedIOException(error); }
        catch (InterruptedException error) { Thread.currentThread().interrupt(); throw new AssertionError(error); }
    }

    private Capacity putQuota(String bucket, long quota) { return parseCapacity(http("PUT", "/admin/buckets/" + bucket + "/quota", "{\"quotaBytes\":" + quota + "}")); }
    private Capacity getCapacity(String bucket) { return parseCapacity(http("GET", "/admin/buckets/" + bucket + "/capacity", null)); }

    private String http(String method, String path, String body) {
        try {
            HttpRequest.Builder request = HttpRequest.newBuilder(URI.create(endpoint() + path)).timeout(TIMEOUT);
            if (body == null) request.GET(); else request.header("Content-Type", "application/json").method(method, HttpRequest.BodyPublishers.ofString(body));
            HttpResponse<String> response = http.send(request.build(), HttpResponse.BodyHandlers.ofString());
            assertEquals(200, response.statusCode(), response.body()); return response.body();
        } catch (IOException error) { throw new UncheckedIOException(error); }
        catch (InterruptedException error) { Thread.currentThread().interrupt(); throw new AssertionError(error); }
    }

    private String endpoint() { return "http://127.0.0.1:" + port; }
    private void assertNoReference(String key) { Path root = PhaseEp4CapacityAwsCliCucumberConfig.ROOT.resolve("metadata/s3-object-references"); if (!Files.isDirectory(root)) return; try (var paths = Files.walk(root)) { assertTrue(paths.filter(Files::isRegularFile).noneMatch(path -> readString(path).contains(key))); } catch (IOException e) { throw new UncheckedIOException(e); } }
    private Set<Path> regularFiles() { try (var paths = Files.walk(PhaseEp4CapacityAwsCliCucumberConfig.ROOT)) { return Set.copyOf(paths.filter(Files::isRegularFile).map(PhaseEp4CapacityAwsCliCucumberConfig.ROOT::relativize).toList()); } catch (IOException e) { throw new UncheckedIOException(e); } }
    private static Capacity parseCapacity(String json) { return new Capacity(number(json,"usedBytes"),number(json,"reservedBytes"),number(json,"quotaBytes"),number(json,"rejectedReservations"),number(json,"lastRejectedBytes")); }
    private static long number(String json,String name) { var matcher=java.util.regex.Pattern.compile("\\\""+name+"\\\"\\s*:\\s*(-?\\d+)").matcher(json); if(!matcher.find()) throw new AssertionError("Missing "+name+" in "+json); return Long.parseLong(matcher.group(1)); }
    private static byte[] deterministicBytes(int size,byte value){byte[] bytes=new byte[size];Arrays.fill(bytes,value);return bytes;}
    private static Path writeTemp(String name,byte[] bytes){try{Path path=Files.createTempFile("magrathea-ep4-"+name+"-",".bin");Files.write(path,bytes);return path;}catch(IOException e){throw new UncheckedIOException(e);}}
    private static Path outputPath(String name){try{Path path=Files.createTempFile("magrathea-ep4-"+name+"-",".bin");Files.delete(path);return path;}catch(IOException e){throw new UncheckedIOException(e);}}
    private static void writeFile(Path path,byte[] bytes){try{Files.createDirectories(path.getParent());Files.write(path,bytes);}catch(IOException e){throw new UncheckedIOException(e);}}
    private static long fileSize(Path path){try{return Files.size(path);}catch(IOException e){throw new UncheckedIOException(e);}}
    private static byte[] readBytes(Path path){try{return Files.readAllBytes(path);}catch(IOException e){throw new UncheckedIOException(e);}}
    private static String readString(Path path){try{return Files.readString(path);}catch(IOException e){throw new UncheckedIOException(e);}}
    private static byte[] readFixture(String fixture){try(InputStream in=Thread.currentThread().getContextClassLoader().getResourceAsStream(fixture)){if(in==null)throw new IOException("Missing fixture "+fixture);return in.readAllBytes();}catch(IOException e){throw new UncheckedIOException(e);}}
    private static String sha256(byte[] bytes){try{return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(bytes));}catch(NoSuchAlgorithmException e){throw new IllegalStateException(e);}}

    private record AwsResult(int exitCode,String output) { }
    private record Capacity(long usedBytes,long reservedBytes,long quotaBytes,long rejectedReservations,long lastRejectedBytes) { }
}
