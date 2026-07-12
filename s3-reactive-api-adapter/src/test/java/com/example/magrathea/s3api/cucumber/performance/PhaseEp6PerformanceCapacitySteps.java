package com.example.magrathea.s3api.cucumber.performance;

import com.example.magrathea.s3api.adapter.web.S3MultipartPartStore;
import com.example.magrathea.s3api.capacity.S3CapacityMetrics;
import com.example.magrathea.s3api.capacity.S3CapacityProperties;
import com.example.magrathea.s3api.capacity.S3CapacityWebFilter;
import com.example.magrathea.s3api.config.JacksonXmlCodecConfig;
import io.cucumber.java.After;
import io.cucumber.java.Before;
import io.cucumber.java.en.And;
import io.cucumber.java.en.Given;
import io.cucumber.java.en.Then;
import io.cucumber.java.en.When;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import io.netty.buffer.PooledByteBufAllocator;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.core.io.buffer.DataBuffer;
import org.springframework.core.io.buffer.DataBufferUtils;
import org.springframework.core.io.buffer.NettyDataBufferFactory;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.client.reactive.ReactorClientHttpConnector;
import org.springframework.http.server.reactive.ReactorHttpHandlerAdapter;
import org.springframework.test.web.reactive.server.EntityExchangeResult;
import org.springframework.test.web.reactive.server.WebTestClient;
import org.springframework.web.reactive.function.BodyInserters;
import org.springframework.web.reactive.function.client.WebClient;
import org.springframework.web.reactive.function.server.HandlerStrategies;
import org.springframework.web.reactive.function.server.RouterFunction;
import org.springframework.web.reactive.function.server.RouterFunctions;
import org.springframework.web.reactive.function.server.ServerResponse;
import org.springframework.web.server.adapter.WebHttpHandlerBuilder;
import reactor.core.Disposable;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import reactor.netty.DisposableServer;
import reactor.netty.http.client.HttpClient;
import reactor.netty.http.server.HttpServer;
import tools.jackson.dataformat.xml.XmlMapper;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.UncheckedIOException;
import java.net.Socket;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.HexFormat;
import java.util.List;
import java.util.Set;
import java.util.concurrent.atomic.AtomicLong;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.*;

/** Real-loopback HTTP semantic validation for REQ-PERF-001..007. */
public class PhaseEp6PerformanceCapacitySteps {
    private static final long MIB = 1024L * 1024;
    private static final int CHUNK = 64 * 1024;
    private static final Duration HTTP_TIMEOUT = Duration.ofMinutes(5);
    private static final NettyDataBufferFactory BUFFERS =
        new NettyDataBufferFactory(PooledByteBufAllocator.DEFAULT);

    @Autowired @Qualifier("s3Routes") private RouterFunction<ServerResponse> routes;
    @Autowired private S3MultipartPartStore partStore;

    private S3CapacityProperties properties;
    private TestClock clock;
    private SimpleMeterRegistry meters;
    private DisposableServer server;
    private WebTestClient client;
    private WebClient asyncClient;
    private String bucket;
    private String key;
    private String uploadId;
    private final List<String> partEtags = new ArrayList<>();
    private final List<Disposable> heldRequests = new ArrayList<>();
    private HttpResult last;
    private long expectedLength;
    private String expectedChecksum;
    private Set<Path> filesBefore;
    private long rejectionStartedNanos;
    private long rejectionElapsedMillis;

    @Before
    public void reset() {
        properties = new S3CapacityProperties();
        properties.setRateLimitBurst(1000);
        properties.setRateLimitPerSecond(1000);
        properties.setRequestTimeout(Duration.ofSeconds(30));
        clock = new TestClock();
        partEtags.clear();
        heldRequests.clear();
        bucket = null;
        key = null;
        uploadId = null;
        last = null;
    }

    @After
    public void stopServer() {
        heldRequests.forEach(Disposable::dispose);
        heldRequests.clear();
        if (server != null) server.disposeNow();
    }

    @Given("the 0.1.x single-node S3 process runs with maximum heap {string}")
    public void boundedHeap(String heap) {
        assertEquals("256m", heap);
        assertTrue(Runtime.getRuntime().maxMemory() <= 256L * MIB,
            () -> "EP-6 runner must be forked with -Xmx256m; maxMemory=" + Runtime.getRuntime().maxMemory());
    }

    @Given("the 0.1.x single-node S3 process limits a single object to {long} bytes")
    public void singleLimit(long bytes) { properties.setMaxSinglePutBytes(bytes); }

    @Given("the 0.1.x single-node S3 process limits each multipart part to {long} bytes")
    public void partLimit(long bytes) { properties.setMaxMultipartPartBytes(bytes); }

    @And("the assembled multipart object limit is {long} bytes")
    public void assembledLimit(long bytes) { properties.setMaxAssembledMultipartBytes(bytes); }

    @Given("the 0.1.x single-node multipart limits are {long} bytes per part and {long} bytes assembled")
    public void multipartLimits(long part, long assembled) {
        properties.setMaxMultipartPartBytes(part);
        properties.setMaxAssembledMultipartBytes(assembled);
    }

    @Given("the 0.1.x single-node S3 process has a finite request timeout")
    public void finiteTimeout() { properties.setRequestTimeout(Duration.ofMillis(250)); }

    @Given("the 0.1.x single-node S3 process has a configured active-request concurrency limit")
    public void concurrencyLimit() {
        properties.setMaxConcurrentRequests(2);
        bucket = "ep6-concurrency-control";
    }

    @Given("the 0.1.x single-node S3 process has a configured token-bucket refill rate and burst capacity")
    public void tokenBucket() {
        properties.setRateLimitBurst(2);
        properties.setRateLimitPerSecond(10);
        bucket = "ep6-token-control";
    }

    @And("bucket {string} exists on filesystem root {string}")
    public void bucketExists(String name, String declaredRoot) {
        assertTrue(declaredRoot.startsWith("target/ep6/"));
        bucket = name;
        startServer();
        int status = client.put().uri("/{bucket}", bucket).exchange()
            .expectBody().returnResult().getStatus().value();
        assertTrue(status == 200 || status == 409, "bucket setup status=" + status);
    }

    @And("fixture {string} contains {long} deterministic bytes")
    public void deterministicFixture(String fixture, long bytes) {
        assertEquals("fixtures/upload/large-object.bin", fixture);
        expectedLength = bytes;
        expectedChecksum = digest(bytes, 0x31);
    }

    @When("the client uploads the fixture as object {string}")
    public void uploadFixture(String objectKey) {
        key = objectKey;
        last = putStream(objectKey, expectedLength, 0x31);
    }

    @Then("PutObject succeeds without an out-of-memory error")
    public void putSucceeded() { assertEquals(200, last.status, last.body); }

    @And("GetObject returns {long} bytes with the fixture checksum")
    public void fixtureRead(long bytes) {
        DigestResult result = streamedGet(key);
        assertEquals(bytes, result.length);
        assertEquals(expectedChecksum, result.sha256);
    }

    @And("the committed object has no truncated or unpublished storage artifacts")
    public void noUnpublishedArtifacts() { assertNoTemporaryArtifacts(); }

    @When("the client attempts PutObject for {string} with content length {long}")
    public void oversizedPut(String objectKey, long bytes) {
        key = objectKey;
        startServer();
        filesBefore = regularFiles();
        last = webResponse(asyncClient.put().uri(objectPath(bucket, key))
            .contentType(MediaType.APPLICATION_OCTET_STREAM)
            .contentLength(bytes)
            .body(BodyInserters.fromDataBuffers(stream(bytes, 0x42))));
    }

    @When("the client uploads four deterministic {long}-byte parts for object {string}")
    public void uploadFourParts(long bytes, String objectKey) {
        key = objectKey;
        initiateMultipart();
        expectedLength = Math.multiplyExact(4, bytes);
        MessageDigest digest = sha256();
        for (int part = 1; part <= 4; part++) {
            int seed = 0x40 + part;
            updateDigest(digest, bytes, seed);
            uploadPart(part, bytes, seed, 200);
        }
        expectedChecksum = HexFormat.of().formatHex(digest.digest());
    }

    @And("the client completes the multipart upload in part-number order")
    public void completeInOrder() { last = completeMultipart(); }

    @Then("CompleteMultipartUpload succeeds without an out-of-memory error")
    public void completeSucceeded() { assertEquals(200, last.status, last.body); }

    @And("GetObject returns {long} bytes with the checksum of the ordered parts")
    public void multipartRead(long bytes) {
        DigestResult result = streamedGet(key);
        assertEquals(bytes, result.length);
        assertEquals(expectedChecksum, result.sha256);
        assertNoTemporaryArtifacts();
    }

    @When("the client attempts {word} for object {string} with {long} bytes")
    public void multipartRejected(String operation, String objectKey, long bytes) {
        key = objectKey;
        initiateMultipart();
        if (operation.equals("UploadPart")) {
            filesBefore = regularFiles();
            last = uploadPart(1, bytes, 0x55, 413);
        } else if (operation.equals("CompleteMultipartUpload")) {
            long remaining = bytes;
            int part = 1;
            while (remaining > 0) {
                long size = Math.min(properties.getMaxMultipartPartBytes(), remaining);
                uploadPart(part++, size, 0x50 + part, 200);
                remaining -= size;
            }
            last = completeMultipart();
        } else {
            fail("Unexpected operation " + operation);
        }
    }

    @Then("the S3 response reports {string}")
    public void s3Error(String code) {
        int expected = code.equals("RequestTimeout")
            ? HttpStatus.REQUEST_TIMEOUT.value() : HttpStatus.PAYLOAD_TOO_LARGE.value();
        assertEquals(expected, last.status, last.body);
        assertTrue(last.body.contains("<Code>" + code + "</Code>"), last.body);
    }

    @And("object {string} does not exist")
    public void objectAbsent(String objectKey) {
        HttpResult result = smallResponse(client.get().uri(objectPath(bucket, objectKey)));
        assertEquals(404, result.status, result.body);
    }

    @And("no temporary whole-object, deduplication, or erasure-coding artifact remains for the rejected request")
    public void noRejectedPutArtifact() {
        assertEquals(filesBefore, regularFiles());
        assertNoTemporaryArtifacts();
    }

    @And("no committed multipart part or assembled-object artifact remains from the rejected operation")
    public void noRejectedMultipartArtifact() {
        Path multipartRoot = PhaseEp6PerformanceCapacityCucumberConfig.ROOT
            .resolve("metadata/s3-multipart-parts");
        if (last.status == 413 && Files.exists(multipartRoot)) {
            try (var walk = Files.walk(multipartRoot)) {
                assertTrue(walk.noneMatch(Files::isRegularFile), "rejected multipart bytes remain under " + multipartRoot);
            } catch (IOException e) {
                throw new UncheckedIOException(e);
            }
        }
        assertNoTemporaryArtifacts();
    }

    @When("a client deliberately stalls PutObject for {string} beyond the configured timeout")
    public void stallPut(String objectKey) {
        key = objectKey;
        startServer();
        filesBefore = regularFiles();
        last = stalledSocketPut(objectPath(bucket, key));
    }

    @And("the timed-out request releases its admission permit")
    public void timeoutPermitReleased() {
        awaitActive(0);
        HttpResult result = smallResponse(client.get().uri("/{bucket}/after-timeout", bucket));
        assertNotEquals(503, result.status, result.body);
    }

    @And("object {string} and its temporary artifacts do not exist")
    public void timedOutObjectAbsent(String objectKey) {
        objectAbsent(objectKey);
        assertEquals(filesBefore, regularFiles(), "timed-out PutObject must leave no storage artifact");
        assertNoTemporaryArtifacts();
    }

    @And("every concurrency permit is held by an active streaming S3 request")
    public void holdPermits() {
        startServer();
        for (int i = 0; i < properties.getMaxConcurrentRequests(); i++) {
            String holdKey = "limits/held-" + i + ".bin";
            Flux<DataBuffer> body = Flux.concat(stream(1, 0x20 + i), Flux.never());
            Disposable disposable = asyncClient.put().uri(objectPath(bucket, holdKey) + "?x-id=WriteGetObjectResponse")
                .contentType(MediaType.APPLICATION_OCTET_STREAM).contentLength(2)
                .body(BodyInserters.fromDataBuffers(body)).retrieve().toBodilessEntity().subscribe();
            heldRequests.add(disposable);
        }
        awaitActive(properties.getMaxConcurrentRequests());
    }

    @When("one additional S3 request arrives")
    public void excessConcurrencyRequest() {
        rejectionStartedNanos = System.nanoTime();
        last = smallResponse(client.get().uri(objectPath(bucket, "limits/excess.bin")));
        rejectionElapsedMillis = Duration.ofNanos(System.nanoTime() - rejectionStartedNanos).toMillis();
    }

    @Then("the additional request receives S3 error {string}")
    public void slowdown(String code) {
        assertEquals(503, last.status, last.body);
        assertTrue(last.body.contains("<Code>" + code + "</Code>"), last.body);
    }

    @And("the rejection occurs without entering a pending request queue")
    public void noPendingQueue() {
        assertTrue(rejectionElapsedMillis < 1000,
            "fail-fast rejection took " + rejectionElapsedMillis + " ms");
        assertEquals(properties.getMaxConcurrentRequests(), activeRequests());
    }

    @And("releasing one active request allows a subsequent request to acquire the freed permit")
    public void releaseAndReusePermit() {
        heldRequests.remove(0).dispose();
        awaitActive(properties.getMaxConcurrentRequests() - 1);
        HttpResult result = smallResponse(client.get().uri(objectPath(bucket, "limits/recovered.bin")));
        assertNotEquals(503, result.status, result.body);
    }

    @When("one client sends a deterministic burst that consumes the available tokens and then sends one excess request")
    public void deterministicBurst() {
        startServer();
        for (int i = 0; i < properties.getRateLimitBurst(); i++) {
            HttpResult admitted = smallResponse(client.get().uri(objectPath(bucket, "limits/token-" + i)));
            assertNotEquals(503, admitted.status, admitted.body);
        }
        last = smallResponse(client.get().uri(objectPath(bucket, "limits/token-excess")));
    }

    @Then("requests covered by available tokens are admitted")
    public void burstAdmitted() { assertEquals(503, last.status); }

    @And("the excess request receives S3 error {string} with a retry hint")
    public void burstRejected(String code) {
        slowdown(code);
        assertEquals("1", last.retryAfter);
    }

    @And("a request is admitted again after the configured token refill interval")
    public void tokenRefill() {
        clock.advanceMillis(100);
        HttpResult admitted = smallResponse(client.get().uri(objectPath(bucket, "limits/token-refilled")));
        assertNotEquals(503, admitted.status, admitted.body);
    }

    private void startServer() {
        if (server != null) return;
        meters = new SimpleMeterRegistry();
        S3CapacityWebFilter filter = new S3CapacityWebFilter(properties, new S3CapacityMetrics(meters), clock);
        var xml = XmlMapper.builder();
        HandlerStrategies strategies = HandlerStrategies.builder().codecs(codecs -> {
            codecs.customCodecs().register(new JacksonXmlCodecConfig.JacksonXmlEncoder(xml));
            codecs.customCodecs().register(new JacksonXmlCodecConfig.JacksonXmlDecoder(xml));
        }).build();
        var webHandler = RouterFunctions.toWebHandler(routes, strategies);
        var httpHandler = WebHttpHandlerBuilder.webHandler(webHandler).filter(filter).build();
        server = HttpServer.create().host("127.0.0.1").port(0)
            .handle(new ReactorHttpHandlerAdapter(httpHandler)).bindNow();
        String baseUrl = "http://127.0.0.1:" + server.port();
        var connector = new ReactorClientHttpConnector(HttpClient.create());
        client = WebTestClient.bindToServer(connector).baseUrl(baseUrl)
            .responseTimeout(HTTP_TIMEOUT)
            .codecs(codecs -> codecs.defaultCodecs().maxInMemorySize(1024 * 1024))
            .build();
        asyncClient = WebClient.builder().baseUrl(baseUrl).clientConnector(connector).build();
    }

    private HttpResult stalledSocketPut(String path) {
        try (Socket socket = new Socket("127.0.0.1", server.port())) {
            socket.setSoTimeout(5000);
            var output = socket.getOutputStream();
            output.write(("PUT " + path + " HTTP/1.1\r\nHost: 127.0.0.1:" + server.port()
                + "\r\nContent-Type: application/octet-stream\r\nContent-Length: 2\r\nConnection: close\r\n\r\nX")
                .getBytes(StandardCharsets.US_ASCII));
            output.flush();
            var reader = new BufferedReader(new InputStreamReader(socket.getInputStream(), StandardCharsets.UTF_8));
            String statusLine = reader.readLine();
            assertNotNull(statusLine, "stalled HTTP request returned no status line");
            int status = Integer.parseInt(statusLine.split(" ")[1]);
            String retryAfter = null;
            String line;
            while ((line = reader.readLine()) != null && !line.isEmpty()) {
                if (line.regionMatches(true, 0, "Retry-After:", 0, 12)) retryAfter = line.substring(12).trim();
            }
            String body = reader.lines().collect(Collectors.joining("\n"));
            return new HttpResult(status, body, retryAfter, null);
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    private void initiateMultipart() {
        startServer();
        HttpResult initiated = smallResponse(client.post()
            .uri(objectPath(bucket, key) + "?uploads"));
        assertEquals(200, initiated.status, initiated.body);
        var matcher = Pattern.compile("<UploadId>([^<]+)</UploadId>").matcher(initiated.body);
        assertTrue(matcher.find(), initiated.body);
        uploadId = matcher.group(1);
    }

    private HttpResult uploadPart(int number, long bytes, int seed, int expectedStatus) {
        HttpResult result = webResponse(asyncClient.put()
            .uri(objectPath(bucket, key) + "?uploadId=" + uploadId + "&partNumber=" + number)
            .contentType(MediaType.APPLICATION_OCTET_STREAM).contentLength(bytes)
            .body(BodyInserters.fromDataBuffers(stream(bytes, seed))));
        assertEquals(expectedStatus, result.status, result.body);
        if (expectedStatus == 200) partEtags.add(result.etag);
        return result;
    }

    private HttpResult completeMultipart() {
        return smallResponse(client.post()
            .uri(objectPath(bucket, key) + "?uploadId=" + uploadId));
    }

    private HttpResult putStream(String objectKey, long bytes, int seed) {
        startServer();
        return webResponse(asyncClient.put().uri(objectPath(bucket, objectKey))
            .contentType(MediaType.APPLICATION_OCTET_STREAM).contentLength(bytes)
            .body(BodyInserters.fromDataBuffers(stream(bytes, seed))));
    }

    private DigestResult streamedGet(String objectKey) {
        MessageDigest digest = sha256();
        AtomicLong length = new AtomicLong();
        asyncClient.get().uri(objectPath(bucket, objectKey)).exchangeToFlux(response -> {
            assertEquals(200, response.statusCode().value());
            return response.bodyToFlux(DataBuffer.class);
        }).doOnNext(buffer -> {
            length.addAndGet(buffer.readableByteCount());
            try (DataBuffer.ByteBufferIterator buffers = buffer.readableByteBuffers()) {
                while (buffers.hasNext()) digest.update(buffers.next());
            } finally {
                DataBufferUtils.release(buffer);
            }
        }).then().block(HTTP_TIMEOUT);
        return new DigestResult(length.get(), HexFormat.of().formatHex(digest.digest()));
    }

    private Flux<DataBuffer> stream(long bytes, int seed) {
        return Flux.generate(() -> 0L, (offset, sink) -> {
            if (offset >= bytes) {
                sink.complete();
                return offset;
            }
            int count = (int) Math.min(CHUNK, bytes - offset);
            byte[] chunk = deterministicChunk(offset, count, seed);
            sink.next(BUFFERS.wrap(chunk));
            return offset + count;
        });
    }

    private static byte[] deterministicChunk(long offset, int count, int seed) {
        byte[] bytes = new byte[count];
        for (int i = 0; i < count; i++) bytes[i] = (byte) (seed + ((offset + i) * 31));
        return bytes;
    }

    private static String digest(long bytes, int seed) {
        MessageDigest digest = sha256();
        updateDigest(digest, bytes, seed);
        return HexFormat.of().formatHex(digest.digest());
    }

    private static void updateDigest(MessageDigest digest, long bytes, int seed) {
        for (long offset = 0; offset < bytes; offset += CHUNK) {
            digest.update(deterministicChunk(offset, (int) Math.min(CHUNK, bytes - offset), seed));
        }
    }

    private static MessageDigest sha256() {
        try { return MessageDigest.getInstance("SHA-256"); }
        catch (NoSuchAlgorithmException e) { throw new IllegalStateException(e); }
    }

    private HttpResult smallResponse(WebTestClient.RequestHeadersSpec<?> request) {
        return response(request);
    }

    private HttpResult webResponse(WebClient.RequestHeadersSpec<?> request) {
        return request.exchangeToMono(response -> response.bodyToMono(String.class).defaultIfEmpty("")
            .map(body -> new HttpResult(response.statusCode().value(), body,
                response.headers().header(HttpHeaders.RETRY_AFTER).stream().findFirst().orElse(null),
                response.headers().header(HttpHeaders.ETAG).stream().findFirst().orElse(null))))
            .block(HTTP_TIMEOUT);
    }

    private HttpResult response(WebTestClient.RequestHeadersSpec<?> request) {
        EntityExchangeResult<String> result = request.exchange().expectBody(String.class).returnResult();
        return new HttpResult(result.getStatus().value(), result.getResponseBody() == null ? "" : result.getResponseBody(),
            result.getResponseHeaders().getFirst(HttpHeaders.RETRY_AFTER),
            result.getResponseHeaders().getFirst(HttpHeaders.ETAG));
    }

    private int activeRequests() {
        Double value = meters.find("magrathea.s3.requests.active").gauge().value();
        return value.intValue();
    }

    private void awaitActive(int expected) {
        long deadline = System.nanoTime() + Duration.ofSeconds(5).toNanos();
        while (System.nanoTime() < deadline) {
            if (activeRequests() == expected) return;
            try { Thread.sleep(10); }
            catch (InterruptedException e) { Thread.currentThread().interrupt(); throw new AssertionError(e); }
        }
        assertEquals(expected, activeRequests());
    }

    private static String objectPath(String bucket, String key) {
        return "/" + bucket + "/" + key;
    }

    private Set<Path> regularFiles() {
        Path root = PhaseEp6PerformanceCapacityCucumberConfig.ROOT;
        if (!Files.exists(root)) return Set.of();
        try (var walk = Files.walk(root)) {
            return walk.filter(Files::isRegularFile).map(root::relativize).collect(Collectors.toSet());
        } catch (IOException e) { throw new UncheckedIOException(e); }
    }

    private void assertNoTemporaryArtifacts() {
        Path root = PhaseEp6PerformanceCapacityCucumberConfig.ROOT;
        if (!Files.exists(root)) return;
        try (var walk = Files.walk(root)) {
            List<Path> temporary = walk.filter(Files::isRegularFile)
                .filter(path -> path.getFileName().toString().contains(".tmp"))
                .toList();
            assertTrue(temporary.isEmpty(), "temporary artifacts remain: " + temporary);
        } catch (IOException e) { throw new UncheckedIOException(e); }
    }

    private static final class TestClock extends Clock {
        private Instant instant = Instant.EPOCH;
        @Override public ZoneId getZone() { return ZoneId.of("UTC"); }
        @Override public Clock withZone(ZoneId zone) { return this; }
        @Override public Instant instant() { return instant; }
        void advanceMillis(long millis) { instant = instant.plusMillis(millis); }
    }

    private record HttpResult(int status, String body, String retryAfter, String etag) {}
    private record DigestResult(long length, String sha256) {}
}
