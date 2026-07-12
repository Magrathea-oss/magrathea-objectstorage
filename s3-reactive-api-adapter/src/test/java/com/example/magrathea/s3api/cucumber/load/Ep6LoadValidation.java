package com.example.magrathea.s3api.cucumber.load;

import com.example.magrathea.s3api.cucumber.ep4.Ep4CapacityTestSupport;
import com.example.magrathea.s3api.cucumber.requirements.RequirementsTestApp;
import com.example.magrathea.s3api.cucumber.support.ChildProcessSupport;

import java.io.IOException;
import java.net.ServerSocket;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HexFormat;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Real-HTTP, storage-engine EP-6 load/soak validator. This is deliberately a validation harness, not a benchmark. */
final class Ep6LoadValidation {
    static final String DISCLAIMER = "Capacity validation only; not a production sizing, comparative, or competitive benchmark";
    private static final int WORKERS = 8;
    private static final long MAX_HEAP = 256L * 1024 * 1024;
    private static final long WORKER_PERIOD_NANOS = TimeUnit.SECONDS.toNanos(2);
    private static final byte[] ONE_MIB = deterministicBytes(1024 * 1024, 0x31);
    private static final byte[] TWO_MIB = deterministicBytes(2 * 1024 * 1024, 0x52);
    private static final byte[] FIVE_MIB_A = deterministicBytes(5 * 1024 * 1024, 0x73);
    private static final byte[] FIVE_MIB_B = deterministicBytes(5 * 1024 * 1024, 0x14);
    private static final Pattern XML_UPLOAD_ID = Pattern.compile("<UploadId>([^<]+)</UploadId>");
    private static final Pattern USED_HEAP = Pattern.compile("used (\\d+)([KMG])", Pattern.CASE_INSENSITIVE);
    private static final Pattern METRIC_VALUE = Pattern.compile(
        "(?:\\\"value\\\"\\s*:\\s*([0-9.Ee+-]+)|<value>([0-9.Ee+-]+)</value>)");
    private static final Pattern METRIC_TAG = Pattern.compile(
        "(?:\\\"tag\\\"\\s*:\\s*\\\"([^\\\"]+)\\\"|<tag>([^<]+)</tag>)");
    private static final Map<String, Result> CACHE = new ConcurrentHashMap<>();
    private static final Map<String, Exception> FAILURES = new ConcurrentHashMap<>();

    static Result resultForConfiguredMode() throws Exception {
        String mode = System.getProperty("ep6.mode", "ci");
        if (FAILURES.containsKey(mode)) throw FAILURES.get(mode);
        try {
            return CACHE.computeIfAbsent(mode, ignored -> {
                try {
                    return run(mode, Integer.getInteger("ep6.duration.seconds", mode.equals("soak") ? 900 : 45));
                } catch (Exception e) {
                    FAILURES.put(mode, e);
                    throw new HarnessFailure(e);
                }
            });
        } catch (HarnessFailure failure) {
            throw (Exception) failure.getCause();
        }
    }

    private static Result run(String mode, int durationSeconds) throws Exception {
        assertTrue(mode.equals("ci") || mode.equals("soak"));
        assertEquals(mode.equals("soak") ? 900 : 45, durationSeconds,
            "EP-6 standard profiles must retain their declared duration");
        System.setProperty("jdk.httpclient.keepalive.timeout", "1");

        Path resultDir = Path.of("target/ep6/results", mode).toAbsolutePath().normalize();
        Path root = Path.of("target/ep6", mode.equals("soak") ? "soak" : "ci-load").toAbsolutePath().normalize();
        recreate(resultDir);
        recreate(root);
        Path log = resultDir.resolve("child.log");
        int port = availablePort();
        Path policies = Ep4CapacityTestSupport.extractCatalog("ep6-load-" + mode, "storage-policies", "minio-standard.yaml");
        Path devices = Ep4CapacityTestSupport.extractCatalog("ep6-load-" + mode, "storage-devices", "local-disk-0.yaml");
        Path disksets = Ep4CapacityTestSupport.extractCatalog("ep6-load-" + mode, "disk-sets", "default-diskset.yaml");
        String java = Path.of(System.getProperty("java.home"), "bin", "java").toString();
        String classpath = System.getProperty("surefire.test.class.path", System.getProperty("java.class.path"));
        List<String> command = List.of(java, "-Xmx256m", "-XX:+ExitOnOutOfMemoryError", "-cp", classpath,
            RequirementsTestApp.class.getName(),
            "--spring.profiles.active=storage-engine", "--magrathea.object-store.backend=storage-engine",
            "--storage.engine.filesystem.root=" + root, "--storage.engine.policies.dir=" + policies,
            "--storage.engine.devices.dir=" + devices, "--storage.engine.disksets.dir=" + disksets,
            "--server.port=" + port, "--spring.main.banner-mode=off",
            "--test.ep6.metrics-probe.enabled=true", "--s3.security.enabled=false",
            "--s3.capacity.enabled=true", "--s3.capacity.max-concurrent-requests=16",
            "--s3.capacity.request-timeout=10s", "--s3.capacity.rate-limit-per-second=100",
            "--s3.capacity.rate-limit-burst=200", "--s3.capacity.max-tcp-connections=64");

        Instant started = Instant.now();
        Stats stats = new Stats();
        Map<String, WriteRecord> acknowledged = new ConcurrentHashMap<>();
        List<Long> heapSamples = new ArrayList<>();
        List<Long> retainedHeapSamples = new ArrayList<>();
        try (ChildProcessSupport child = ChildProcessSupport.start(command, log)) {
            child.awaitLog("Netty started on port " + port, Duration.ofSeconds(45));
            HttpClient setupClient = client();
            expect(setupClient, port, "PUT", "/ep6-load", null, Map.of(), 200, stats, false);
            expect(setupClient, port, "PUT", "/ep6-load/read-fixture.bin", TWO_MIB, Map.of(), 200, stats, false);
            expect(setupClient, port, "PUT", "/ep6-load/head-fixture.bin", ONE_MIB, Map.of(), 200, stats, false);
            heapSamples.add(heapUsed(child.pid(), false));
            retainedHeapSamples.add(heapUsed(child.pid(), true));

            long loadStarted = System.nanoTime();
            long deadline = loadStarted + TimeUnit.SECONDS.toNanos(durationSeconds);
            var pool = Executors.newFixedThreadPool(WORKERS);
            List<Future<?>> workers = new ArrayList<>();
            for (int worker = 1; worker <= WORKERS; worker++) {
                int assigned = worker;
                workers.add(pool.submit(() -> workerLoop(assigned, deadline, port, mode, stats, acknowledged)));
            }
            long nextHeapSample = System.nanoTime() + TimeUnit.SECONDS.toNanos(mode.equals("soak") ? 60 : 5);
            long nextRetainedSample = loadStarted + TimeUnit.MINUTES.toNanos(5);
            while (System.nanoTime() < deadline) {
                assertTrue(child.process().isAlive(), "S3 child exited during load:\n" + child.readLog());
                if (System.nanoTime() >= nextHeapSample) {
                    heapSamples.add(heapUsed(child.pid(), false));
                    nextHeapSample += TimeUnit.SECONDS.toNanos(mode.equals("soak") ? 60 : 5);
                }
                if (mode.equals("soak") && System.nanoTime() >= nextRetainedSample) {
                    retainedHeapSamples.add(heapUsed(child.pid(), true));
                    nextRetainedSample += TimeUnit.MINUTES.toNanos(5);
                }
                Thread.sleep(200);
            }
            for (Future<?> worker : workers) worker.get(30, TimeUnit.SECONDS);
            pool.shutdown();
            assertTrue(pool.awaitTermination(10, TimeUnit.SECONDS));

            int checked = verifyAcknowledged(port, mode, acknowledged, stats);
            long retainedHeap = heapUsed(child.pid(), true);
            Thread.sleep(1000);
            long postGcHeap = heapUsed(child.pid(), true);
            heapSamples.add(retainedHeap);
            heapSamples.add(postGcHeap);
            retainedHeapSamples.add(retainedHeap);
            retainedHeapSamples.add(postGcHeap);
            Thread.sleep(3000);
            int idleConnections = establishedConnections(port);
            long temporaryArtifacts = temporaryArtifacts(root);
            ApplicationMetrics applicationMetrics = captureApplicationMetrics(port);
            long peakHeap = heapSamples.stream().max(Comparator.naturalOrder()).orElse(0L);
            long p99Millis = percentile99(stats.latencies);
            long completed = stats.completed.get();

            assertTrue(child.process().isAlive(), "child was forced to restart or exit");
            assertTrue(completed >= (mode.equals("soak") ? 1800 : 90), "completed operations: " + completed);
            assertEquals(0, stats.corruptions.get(), "checksum corruption");
            assertEquals(0, stats.unexpected.get(), "unexpected responses");
            assertTrue(p99Millis < 10_000, "p99 latency was " + p99Millis + " ms");
            assertTrue(peakHeap <= MAX_HEAP, "peak heap exceeded Xmx: " + peakHeap);
            assertTrue(postGcHeap <= 192L * 1024 * 1024, "post-GC heap exceeded idle bound: " + postGcHeap);
            if (mode.equals("soak") && retainedHeapSamples.size() >= 4) {
                long firstRetained = retainedHeapSamples.getFirst();
                assertTrue(postGcHeap <= Math.max(firstRetained + 32L * 1024 * 1024, firstRetained * 5 / 4),
                    "sustained retained-heap growth: first=" + firstRetained + " final=" + postGcHeap);
            }
            assertEquals(0, stats.active.get(), "load-harness active requests at idle");
            assertEquals(0, applicationMetrics.activeRequests(), "application active requests at idle");
            assertTrue(applicationMetrics.acceptedRequests() >= stats.accepted.get(),
                "application admission counter must cover every client-observed accepted request");
            assertEquals(Set.of("operation", "outcome"), applicationMetrics.tagKeys(),
                "production capacity metrics must expose only bounded tag keys");
            assertEquals(0, idleConnections, "open load-client TCP connections at idle");
            assertEquals(0, temporaryArtifacts, "temporary storage artifacts at idle");
            String childLog = child.readLog();
            assertFalse(childLog.contains("OutOfMemoryError"));
            assertFalse(childLog.contains("Authorization:"), "authorization material leaked to logs");

            Result result = new Result(mode, durationSeconds, completed, applicationMetrics.acceptedRequests(),
                stats.rejected.get(), applicationMetrics.concurrencyRejections(), applicationMetrics.rateLimitRejections(),
                applicationMetrics.requestTimeouts(), stats.unexpected.get(), stats.corruptions.get(), p99Millis, peakHeap,
                postGcHeap, stats.peakActive.get(), applicationMetrics.activeRequests(), idleConnections,
                temporaryArtifacts, checked, applicationMetrics.tagKeys(), resultDir, root,
                Instant.now().toEpochMilli() - started.toEpochMilli());
            writeEvidence(result, stats, heapSamples, retainedHeapSamples, log);
            return result;
        }
    }

    private static void workerLoop(int worker, long deadline, int port, String mode, Stats stats,
                                   Map<String, WriteRecord> acknowledged) {
        HttpClient http = client();
        int iteration = 0;
        while (System.nanoTime() < deadline) {
            long cycleStart = System.nanoTime();
            try {
                switch (worker) {
                    case 1, 2 -> put(worker, iteration, port, http, stats, acknowledged, mode);
                    case 3, 4 -> getFixture(port, http, stats);
                    case 5 -> headFixture(port, http, stats);
                    case 6 -> rangedGet(port, http, stats);
                    case 7 -> multipart(iteration, port, http, stats, acknowledged, mode);
                    case 8 -> delete(iteration, port, http, stats);
                    default -> throw new IllegalStateException("unknown worker");
                }
            } catch (Exception e) {
                stats.unexpected.incrementAndGet();
                stats.failures.add("worker=" + worker + " iteration=" + iteration + " " + e);
            }
            iteration++;
            long sleep = WORKER_PERIOD_NANOS - (System.nanoTime() - cycleStart);
            if (sleep > 0) {
                try {
                    TimeUnit.NANOSECONDS.sleep(sleep);
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    return;
                }
            }
        }
    }

    private static void put(int worker, int iteration, int port, HttpClient http, Stats stats,
                            Map<String, WriteRecord> acknowledged, String mode) throws Exception {
        byte[] body = ONE_MIB.clone();
        body[0] = (byte) worker;
        body[1] = (byte) iteration;
        body[2] = (byte) (iteration >>> 8);
        String key = "put/w" + worker + "/object-" + iteration + ".bin";
        expect(http, port, "PUT", "/ep6-load/" + key, body, Map.of(), 200, stats, true);
        acknowledged.put(key, new WriteRecord(sha256(body), mode.equals("ci") || iteration % 25 == 0));
    }

    private static void getFixture(int port, HttpClient http, Stats stats) throws Exception {
        HttpResponse<byte[]> response = expect(http, port, "GET", "/ep6-load/read-fixture.bin", null,
            Map.of(), 200, stats, true);
        if (!MessageDigest.isEqual(sha256(TWO_MIB), sha256(response.body()))) stats.corruptions.incrementAndGet();
    }

    private static void headFixture(int port, HttpClient http, Stats stats) throws Exception {
        expect(http, port, "HEAD", "/ep6-load/head-fixture.bin", null, Map.of(), 200, stats, true);
    }

    private static void rangedGet(int port, HttpClient http, Stats stats) throws Exception {
        HttpResponse<byte[]> response = expect(http, port, "GET", "/ep6-load/read-fixture.bin", null,
            Map.of("Range", "bytes=262144-786431"), 206, stats, true);
        byte[] expected = java.util.Arrays.copyOfRange(TWO_MIB, 262144, 786432);
        if (!MessageDigest.isEqual(sha256(expected), sha256(response.body()))) stats.corruptions.incrementAndGet();
    }

    private static void multipart(int iteration, int port, HttpClient http, Stats stats,
                                  Map<String, WriteRecord> acknowledged, String mode) throws Exception {
        String key = "multipart/object-" + iteration + ".bin";
        HttpResponse<byte[]> initiated = expect(http, port, "POST", "/ep6-load/" + key + "?uploads", null,
            Map.of("Accept", "application/xml"), 200, stats, false);
        var match = XML_UPLOAD_ID.matcher(new String(initiated.body(), StandardCharsets.UTF_8));
        if (!match.find()) throw new IllegalStateException("missing upload ID");
        String upload = java.net.URLEncoder.encode(match.group(1), StandardCharsets.UTF_8);
        expect(http, port, "PUT", "/ep6-load/" + key + "?uploadId=" + upload + "&partNumber=1",
            FIVE_MIB_A, Map.of(), 200, stats, false);
        expect(http, port, "PUT", "/ep6-load/" + key + "?uploadId=" + upload + "&partNumber=2",
            FIVE_MIB_B, Map.of(), 200, stats, false);
        expect(http, port, "POST", "/ep6-load/" + key + "?uploadId=" + upload,
            "<CompleteMultipartUpload/>".getBytes(StandardCharsets.UTF_8), Map.of("Content-Type", "application/xml"),
            200, stats, true);
        byte[] combined = new byte[FIVE_MIB_A.length + FIVE_MIB_B.length];
        System.arraycopy(FIVE_MIB_A, 0, combined, 0, FIVE_MIB_A.length);
        System.arraycopy(FIVE_MIB_B, 0, combined, FIVE_MIB_A.length, FIVE_MIB_B.length);
        acknowledged.put(key, new WriteRecord(sha256(combined), mode.equals("ci") || iteration % 25 == 0));
    }

    private static void delete(int iteration, int port, HttpClient http, Stats stats) throws Exception {
        String key = "/ep6-load/delete/owned-" + iteration + ".bin";
        expect(http, port, "PUT", key, new byte[]{(byte) iteration}, Map.of(), 200, stats, false);
        expect(http, port, "DELETE", key, null, Map.of(), 204, stats, true);
    }

    private static HttpResponse<byte[]> expect(HttpClient http, int port, String method, String path, byte[] body,
                                               Map<String, String> headers, int expected, Stats stats,
                                               boolean countOperation) throws Exception {
        HttpRequest.Builder request = HttpRequest.newBuilder(URI.create("http://127.0.0.1:" + port + path))
            .timeout(Duration.ofSeconds(10));
        headers.forEach(request::header);
        request.method(method, body == null ? HttpRequest.BodyPublishers.noBody() : HttpRequest.BodyPublishers.ofByteArray(body));
        long before = System.nanoTime();
        stats.active.incrementAndGet();
        stats.peakActive.accumulateAndGet(stats.active.get(), Math::max);
        try {
            HttpResponse<byte[]> response = http.send(request.build(), HttpResponse.BodyHandlers.ofByteArray());
            stats.accepted.incrementAndGet();
            if (response.statusCode() == 429 || response.statusCode() == 503) stats.rejected.incrementAndGet();
            if (response.statusCode() == 408) stats.timedOut.incrementAndGet();
            if (response.statusCode() != expected) {
                stats.unexpected.incrementAndGet();
                throw new AssertionError(method + " " + path + " returned " + response.statusCode() + ": "
                    + new String(response.body(), StandardCharsets.UTF_8));
            }
            if (countOperation) {
                stats.completed.incrementAndGet();
                stats.latencies.add(TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - before));
            }
            return response;
        } catch (java.net.http.HttpTimeoutException e) {
            stats.timedOut.incrementAndGet();
            throw e;
        } finally {
            stats.active.decrementAndGet();
        }
    }

    private static int verifyAcknowledged(int port, String mode, Map<String, WriteRecord> acknowledged, Stats stats)
            throws Exception {
        HttpClient http = client();
        int checked = 0;
        for (var entry : acknowledged.entrySet().stream().sorted(Map.Entry.comparingByKey()).toList()) {
            if (!entry.getValue().sampled()) continue;
            HttpResponse<byte[]> response = readForChecksumWithRetry(http, port, entry.getKey(), stats);
            if (!MessageDigest.isEqual(entry.getValue().checksum(), sha256(response.body()))) {
                stats.corruptions.incrementAndGet();
            }
            checked++;
        }
        assertTrue(checked > 0, mode + " must checksum at least one acknowledged write");
        return checked;
    }

    private static HttpResponse<byte[]> readForChecksumWithRetry(HttpClient http, int port, String key, Stats stats)
            throws Exception {
        for (int attempt = 0; attempt < 100; attempt++) {
            HttpRequest request = HttpRequest.newBuilder(URI.create("http://127.0.0.1:" + port + "/ep6-load/" + key))
                .timeout(Duration.ofSeconds(30)).GET().build();
            HttpResponse<byte[]> response = http.send(request, HttpResponse.BodyHandlers.ofByteArray());
            if (response.statusCode() == 200) {
                stats.accepted.incrementAndGet();
                return response;
            }
            if (response.statusCode() == 503) {
                stats.rejected.incrementAndGet();
                stats.rateLimitRejections.incrementAndGet();
                Thread.sleep(20);
                continue;
            }
            stats.unexpected.incrementAndGet();
            throw new AssertionError("checksum GET " + key + " returned " + response.statusCode());
        }
        stats.unexpected.incrementAndGet();
        throw new AssertionError("checksum GET remained rate-limited after retries: " + key);
    }

    private static void writeEvidence(Result result, Stats stats, List<Long> heapSamples,
                                      List<Long> retainedHeapSamples, Path log) throws Exception {
        Path manifest = result.resultDir().resolve("result.json");
        Path summary = result.resultDir().resolve("summary.md");
        String revision = command("git", "rev-parse", "HEAD").trim();
        boolean dirty = !command("git", "status", "--porcelain").isBlank();
        Map<String, String> checksums = new LinkedHashMap<>();
        checksums.put("child.log", hexSha256(Files.readAllBytes(log)));
        String json = """
            {
              "schemaVersion": 1,
              "revision": %s,
              "releaseLine": "0.1.x",
              "dirtyTree": %s,
              "validationMode": %s,
              "seed": "ep6-ci-0.1.x",
              "workerCount": 8,
              "durationSeconds": %d,
              "elapsedMillis": %d,
              "runtime": {"jvmVendor": %s, "jvmVersion": %s, "maximumHeap": "256m", "operatingSystem": %s, "processorCount": %d},
              "configuration": {"backend": "storage-engine", "filesystemRoot": %s, "singlePutLimitBytes": 268435456, "multipartPartLimitBytes": 67108864, "assembledMultipartLimitBytes": 268435456, "requestTimeoutSeconds": 10, "concurrencyLimit": 16, "rateLimitPerSecond": 100, "rateLimitBurst": 200, "tcpConnectionCap": 64},
              "counts": {"completedOperations": %d, "acceptedRequests": %d, "rejectedRequests": %d, "concurrencyRejections": %d, "rateLimitRejections": %d, "requestTimeouts": %d, "unexpectedResponses": %d, "corruptions": %d, "checksummedWrites": %d},
              "latency": {"p99Millis": %d},
              "heap": {"peakBytes": %d, "postGcBytes": %d, "samplesBytes": %s, "retainedSamplesBytes": %s},
              "resources": {"peakActiveRequests": %d, "idleActiveRequests": %d, "idleOpenTcpConnections": %d, "idleTemporaryArtifacts": %d},
              "metricLabels": {"observed": %s, "allowed": ["operation", "outcome"], "forbidden": ["accessKey", "authorization", "signature", "body", "metadata", "bucket", "objectKey"]},
              "artifactChecksumsSha256": {"child.log": %s},
              "disclaimer": %s
            }
            """.formatted(q(revision), dirty, q(result.mode()), result.durationSeconds(), result.elapsedMillis(),
                q(System.getProperty("java.vendor")), q(System.getProperty("java.version")),
                q(System.getProperty("os.name") + " " + System.getProperty("os.arch")), Runtime.getRuntime().availableProcessors(),
                q(result.root().toString()), result.completed(), result.accepted(), result.rejected(),
                result.concurrencyRejections(), result.rateLimitRejections(), result.timedOut(), result.unexpected(),
                result.corruptions(), result.checksummedWrites(), result.p99Millis(), result.peakHeap(),
                result.postGcHeap(), heapSamples, retainedHeapSamples, result.peakActive(), result.idleActiveRequests(),
                result.idleConnections(), result.temporaryArtifacts(), result.metricTagKeys().stream().sorted()
                    .map(Ep6LoadValidation::q).collect(Collectors.joining(",", "[", "]")),
                q(checksums.get("child.log")), q(DISCLAIMER));
        Files.writeString(manifest, json, StandardCharsets.UTF_8);
        String manifestChecksum = hexSha256(Files.readAllBytes(manifest));
        String markdown = """
            # EP-6 %s validation summary

            - Manifest: [`result.json`](result.json) — SHA-256 `%s`
            - Child log: [`child.log`](child.log) — SHA-256 `%s`
            - Tested envelope: Magrathea 0.1.x single-node, %s, %s %s, %d processors, storage-engine filesystem backend, `-Xmx256m`
            - Observed: %,d completed operations; p99 %,d ms; peak heap %,d bytes; post-GC heap %,d bytes; %,d checksummed writes
            - Configured limits: 8 workers; %,d seconds; 16 requests; 64 TCP connections; 100 requests/s with burst 200; 10 second timeout
            - Pass criteria: at least %,d operations; zero corruption and unexpected responses; p99 below 10,000 ms; heap at or below 268,435,456 bytes; idle resources zero

            %s.

            This result is not production sizing guidance and is not a comparison with another object store.
            """.formatted(result.mode().toUpperCase(Locale.ROOT), manifestChecksum, checksums.get("child.log"),
                System.getProperty("java.vendor"), System.getProperty("os.name"), System.getProperty("os.arch"),
                Runtime.getRuntime().availableProcessors(), result.completed(), result.p99Millis(), result.peakHeap(),
                result.postGcHeap(), result.checksummedWrites(), result.durationSeconds(),
                result.mode().equals("soak") ? 1800 : 90, DISCLAIMER);
        Files.writeString(summary, markdown, StandardCharsets.UTF_8);
    }

    private static ApplicationMetrics captureApplicationMetrics(int port) throws Exception {
        String requestMetrics = metricDocument(port, "magrathea.s3.requests", null, true);
        String rejectionMetrics = metricDocument(port, "magrathea.s3.rejections", null, false);
        var tagKeys = new LinkedHashSet<String>();
        tagKeys.addAll(metricTags(requestMetrics));
        tagKeys.addAll(metricTags(rejectionMetrics));
        return new ApplicationMetrics(
            metricValue(port, "magrathea.s3.requests", "outcome:accepted", true),
            metricValue(port, "magrathea.s3.rejections", "outcome:concurrency", false),
            metricValue(port, "magrathea.s3.rejections", "outcome:rate_limit", false),
            metricValue(port, "magrathea.s3.requests", "outcome:timeout", false),
            Math.toIntExact(metricValue(port, "magrathea.s3.requests.active", null, true)),
            Set.copyOf(tagKeys));
    }

    private static long metricValue(int port, String meter, String tag, boolean required) throws Exception {
        String document = metricDocument(port, meter, tag, required);
        if (document.isBlank()) return 0;
        var matcher = METRIC_VALUE.matcher(document);
        if (!matcher.find()) throw new IllegalStateException("Metric has no measurement: " + meter + " document=" + document);
        return Math.round(Double.parseDouble(matcher.group(1) == null ? matcher.group(2) : matcher.group(1)));
    }

    private static Set<String> metricTags(String document) {
        var tags = new LinkedHashSet<String>();
        var matcher = METRIC_TAG.matcher(document);
        while (matcher.find()) tags.add(matcher.group(1) == null ? matcher.group(2) : matcher.group(1));
        return tags;
    }

    private static String metricDocument(int port, String meter, String tag, boolean required) throws Exception {
        String query = tag == null ? "" : "?tag=" + tag;
        var request = HttpRequest.newBuilder(URI.create("http://127.0.0.1:" + port + "/admin/_test/metrics/" + meter + query))
            .header("Accept", "application/json").timeout(Duration.ofSeconds(10)).GET().build();
        var response = client().send(request, HttpResponse.BodyHandlers.ofString());
        if (!required && response.statusCode() == 404) return "";
        assertEquals(200, response.statusCode(), "metric endpoint " + meter + " " + response.body());
        return response.body();
    }

    private static long heapUsed(long pid, boolean gcFirst) throws Exception {
        if (gcFirst) command("jcmd", Long.toString(pid), "GC.run");
        String info = command("jcmd", Long.toString(pid), "GC.heap_info");
        var matcher = USED_HEAP.matcher(info);
        if (!matcher.find()) throw new IllegalStateException("Cannot parse child heap from: " + info);
        long amount = Long.parseLong(matcher.group(1));
        return switch (matcher.group(2).toUpperCase(Locale.ROOT)) {
            case "K" -> amount * 1024;
            case "M" -> amount * 1024 * 1024;
            case "G" -> amount * 1024 * 1024 * 1024;
            default -> throw new IllegalStateException("heap unit");
        };
    }

    private static String command(String... command) throws Exception {
        Process process = new ProcessBuilder(command).redirectErrorStream(true).start();
        String output = new String(process.getInputStream().readAllBytes(), StandardCharsets.UTF_8);
        assertTrue(process.waitFor(30, TimeUnit.SECONDS), "command timed out: " + List.of(command));
        assertEquals(0, process.exitValue(), output);
        return output;
    }

    private static long percentile99(ConcurrentLinkedQueue<Long> values) {
        List<Long> sorted = values.stream().sorted().toList();
        if (sorted.isEmpty()) return Long.MAX_VALUE;
        return sorted.get(Math.min(sorted.size() - 1, (int) Math.ceil(sorted.size() * 0.99) - 1));
    }

    private static int establishedConnections(int port) throws IOException {
        int count = 0;
        String portHex = String.format("%04X", port);
        for (String file : List.of("/proc/net/tcp", "/proc/net/tcp6")) {
            Path path = Path.of(file);
            if (!Files.isReadable(path)) continue;
            for (String line : Files.readAllLines(path)) {
                String[] fields = line.trim().split("\\s+");
                if (fields.length > 3 && fields[1].endsWith(":" + portHex) && fields[3].equals("01")) count++;
            }
        }
        return count;
    }

    private static long temporaryArtifacts(Path root) throws IOException {
        try (var files = Files.walk(root)) {
            return files.filter(Files::isRegularFile)
                .filter(path -> path.getFileName().toString().contains(".tmp.")).count();
        }
    }

    private static HttpClient client() {
        return HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(5)).version(HttpClient.Version.HTTP_1_1).build();
    }

    private static byte[] deterministicBytes(int size, int salt) {
        byte[] bytes = new byte[size];
        for (int i = 0; i < size; i++) bytes[i] = (byte) ((i * 31 + salt) & 0xff);
        return bytes;
    }

    private static byte[] sha256(byte[] bytes) {
        try {
            return MessageDigest.getInstance("SHA-256").digest(bytes);
        } catch (Exception e) {
            throw new IllegalStateException(e);
        }
    }

    private static String hexSha256(byte[] bytes) {
        return HexFormat.of().formatHex(sha256(bytes));
    }

    private static String q(String value) {
        return "\"" + value.replace("\\", "\\\\").replace("\"", "\\\"").replace("\n", "\\n") + "\"";
    }

    private static int availablePort() throws IOException {
        try (ServerSocket socket = new ServerSocket(0)) {
            return socket.getLocalPort();
        }
    }

    private static void recreate(Path root) throws IOException {
        if (Files.exists(root)) {
            try (var paths = Files.walk(root)) {
                for (Path path : paths.sorted(Comparator.reverseOrder()).toList()) Files.delete(path);
            }
        }
        Files.createDirectories(root);
    }

    record Result(String mode, int durationSeconds, long completed, long accepted, long rejected,
                  long concurrencyRejections, long rateLimitRejections, long timedOut,
                  long unexpected, long corruptions, long p99Millis, long peakHeap, long postGcHeap, int peakActive,
                  int idleActiveRequests, int idleConnections, long temporaryArtifacts, int checksummedWrites,
                  Set<String> metricTagKeys, Path resultDir, Path root, long elapsedMillis) {}
    private record WriteRecord(byte[] checksum, boolean sampled) {}
    private record ApplicationMetrics(long acceptedRequests, long concurrencyRejections, long rateLimitRejections,
                                      long requestTimeouts, int activeRequests, Set<String> tagKeys) {}

    private static final class Stats {
        final AtomicLong completed = new AtomicLong();
        final AtomicLong accepted = new AtomicLong();
        final AtomicLong rejected = new AtomicLong();
        final AtomicLong concurrencyRejections = new AtomicLong();
        final AtomicLong rateLimitRejections = new AtomicLong();
        final AtomicLong timedOut = new AtomicLong();
        final AtomicLong unexpected = new AtomicLong();
        final AtomicLong corruptions = new AtomicLong();
        final AtomicInteger active = new AtomicInteger();
        final AtomicInteger peakActive = new AtomicInteger();
        final ConcurrentLinkedQueue<Long> latencies = new ConcurrentLinkedQueue<>();
        final ConcurrentLinkedQueue<String> failures = new ConcurrentLinkedQueue<>();
    }

    private static final class HarnessFailure extends RuntimeException {
        HarnessFailure(Exception cause) { super(cause); }
    }
}
