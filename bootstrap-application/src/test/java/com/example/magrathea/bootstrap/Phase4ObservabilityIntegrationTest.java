package com.example.magrathea.bootstrap;

import com.example.magrathea.storageengine.application.pipeline.StorageEvent;
import com.example.magrathea.storageengine.application.pipeline.StorageEventType;
import com.example.magrathea.storageengine.infrastructure.filesystem.FileSystemRecoveryScanner;
import com.example.magrathea.storageengine.infrastructure.observability.MicrometerStorageEventListener;
import io.micrometer.core.instrument.MeterRegistry;
import io.opentelemetry.sdk.testing.exporter.InMemorySpanExporter;
import io.opentelemetry.sdk.trace.data.SpanData;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.system.OutputCaptureExtension;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.context.annotation.Import;
import org.springframework.core.io.buffer.DataBuffer;
import org.springframework.http.MediaType;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.reactive.server.WebTestClient;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

import static com.example.magrathea.bootstrap.Phase4ObservabilityTestSupport.cleanStorageRoot;
import static com.example.magrathea.bootstrap.Phase4ObservabilityTestSupport.drain;
import static com.example.magrathea.bootstrap.Phase4ObservabilityTestSupport.finishedSpans;
import static com.example.magrathea.bootstrap.Phase4ObservabilityTestSupport.committedStorageArtifacts;
import static com.example.magrathea.bootstrap.Phase4ObservabilityTestSupport.path;
import static com.example.magrathea.bootstrap.Phase4ObservabilityTestSupport.registerStorageEngineProperties;
import static com.example.magrathea.bootstrap.Phase4ObservabilityTestSupport.sha256Hex;
import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest(
        webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
        properties = {
                "admin.server.port=0",
                "storage.engine.filesystem.node-count=1"
        })
@ActiveProfiles("storage-engine")
@Import(Phase4ObservabilityTestSupport.ObservabilityProbeConfig.class)
@ExtendWith(OutputCaptureExtension.class)
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_EACH_TEST_METHOD)
class Phase4ObservabilityIntegrationTest {

    private static final Path STORAGE_ROOT = Phase4ObservabilityTestSupport
            .createStorageRoot("magrathea-phase4-observability-");

    @DynamicPropertySource
    static void storageEngineProperties(DynamicPropertyRegistry registry) {
        registerStorageEngineProperties(registry, STORAGE_ROOT, "magrathea-phase4-catalog-");
    }

    @LocalServerPort
    private int port;

    @Autowired
    private Phase4ObservabilityTestSupport.RecordingStorageEventListener events;

    @Autowired
    private MeterRegistry meterRegistry;

    @Autowired
    private InMemorySpanExporter spanExporter;

    @Autowired
    private FileSystemRecoveryScanner recoveryScanner;

    private WebTestClient client;

    @BeforeEach
    void setUp() {
        cleanStorageRoot(STORAGE_ROOT);
        events.clear();
        spanExporter.reset();
        meterRegistry.clear();
        client = WebTestClient.bindToServer()
                .baseUrl("http://127.0.0.1:" + port)
                .build();
    }

    @Test
    void REQ_OBS_001_003_004_successfulS3PutAndGetEmitEventsMetricsAndOpenTelemetrySpans(CapturedOutput output) {
        String bucket = "obs-pipeline-success-bucket";
        String key = "observability/2026/events/success-object.txt";
        String body = "Hello durable Magrathea!";

        createBucket(bucket);
        putObject(bucket, key, body, "x-amz-meta-project", "magrathea");
        String downloaded = getObject(bucket, key);

        assertThat(downloaded).isEqualTo(body);
        assertStages("put-object", List.of("validation", "policy-resolution", "chunking", "dedup-lookup",
                "chunk-persistence", "manifest-persistence", "object-index-persistence"));
        assertStages("get-object", List.of("validation", "policy-resolution", "read-planning",
                "chunk-reading", "response-streaming"));
        assertThat(events.events()).filteredOn(event -> event.duration().isPresent())
                .allSatisfy(event -> assertThat(event.duration().orElseThrow().isNegative()).isFalse());
        assertThat(events.events()).anySatisfy(event -> assertThat(event.manifestId()).isPresent());

        assertCounterAtLeast(MicrometerStorageEventListener.BYTES, body.length(),
                "operation", "put-object", "stage", "chunk-persistence", "direction", "write");
        assertCounterAtLeast(MicrometerStorageEventListener.BYTES, body.length(),
                "operation", "get-object", "stage", "response-streaming", "direction", "read");
        assertCounterAtLeast(MicrometerStorageEventListener.CHUNKS, 1,
                "operation", "put-object", "stage", "chunk-persistence");
        assertThat(meterRegistry.find(MicrometerStorageEventListener.STAGE_DURATION)
                .tag("operation", "put-object")
                .tag("stage", "chunk-persistence")
                .timer()).isNotNull();

        List<SpanData> spans = finishedSpans(spanExporter);
        assertThat(spans).extracting(SpanData::getName)
                .contains("PutObject", "GetObject",
                        "magrathea.storage.put-object.chunk-persistence",
                        "magrathea.storage.get-object.response-streaming");
        assertThat(spans).anySatisfy(span -> assertThat(span.getParentSpanContext().isValid()).isTrue());
        assertThat(spans).anySatisfy(span -> assertThat(span.getAttributes().asMap().toString())
                .contains("correlation.id", "request.id", "backend", "stage.duration.ms"));

        assertNoSensitiveLeak(output, body, "top-secret", "AKIA-MUST-NOT-LEAK");
    }

    @Test
    void REQ_OBS_002_singlePassCorruptedArtifactReadEmitsSuccessSignalsWithoutSensitiveLeak(CapturedOutput output) throws Exception {
        String bucket = "obs-pipeline-failure-bucket";
        String key = "observability/2026/events/failed-object.bin";
        String body = "CORRUPTIBLE-CONTENT-WITHOUT-SENSITIVE-PAYLOAD";

        createBucket(bucket);
        putObject(bucket, key, body, "x-amz-meta-secret", "top-secret");
        for (Path artifact : committedStorageArtifacts(STORAGE_ROOT)) {
            byte[] corrupted = Files.readAllBytes(artifact);
            corrupted[0] ^= 0x7f;
            Files.write(artifact, corrupted);
        }
        events.clear();
        spanExporter.reset();
        meterRegistry.clear();

        var result = client.get()
                .uri(path(bucket, key))
                .header("Authorization", "AWS4-HMAC-SHA256 Credential=AKIA-MUST-NOT-LEAK")
                .exchange()
                .expectStatus().isOk()
                .expectBody(byte[].class)
                .returnResult();

        assertThat(result.getResponseHeaders().getETag()).isNotBlank();
        assertThat(new String(result.getResponseBody(), StandardCharsets.UTF_8)).isNotEqualTo(body);
        assertThat(events.events()).anySatisfy(event -> {
            assertThat(event.type()).isEqualTo(StorageEventType.STAGE_SUCCEEDED);
            assertThat(event.stageName()).isEqualTo("response-streaming");
        });
        assertCounterAtLeast(MicrometerStorageEventListener.BYTES, result.getResponseBody().length,
                "operation", "get-object", "stage", "response-streaming", "direction", "read");
        assertThat(finishedSpans(spanExporter)).extracting(SpanData::getName)
                .contains("GetObject", "magrathea.storage.get-object.response-streaming");
        assertThat(output).doesNotContain("failureClassification=integrity-failure");
        assertNoSensitiveLeak(output, body, "top-secret", "AKIA-MUST-NOT-LEAK");
    }

    @Test
    void REQ_OBS_005_recoveryScannerEmitsSignalsAndKeepsValidObjectReadable(CapturedOutput output) throws Exception {
        String bucket = "obs-recovery-bucket";
        String key = "observability/2026/recovery/healthy-object.txt";
        String body = "Hello durable Magrathea!";

        createBucket(bucket);
        putObject(bucket, key, body);
        createRecoveryFindings();
        events.clear();
        spanExporter.reset();
        meterRegistry.clear();

        FileSystemRecoveryScanner.ScanReport report = recoveryScanner.scan(STORAGE_ROOT);
        recoveryScanner.quarantine(STORAGE_ROOT, report);

        assertThat(report.findings()).extracting(FileSystemRecoveryScanner.Finding::artifactType)
                .contains("orphaned-chunk", "incomplete-manifest", "broken-reference", "checksum-mismatch");
        assertThat(getObject(bucket, key)).isEqualTo(body);
        assertThat(events.events()).filteredOn(event -> event.type() == StorageEventType.RECOVERY_ARTIFACT_QUARANTINED)
                .extracting(event -> ((StorageEvent.RecoveryArtifactQuarantined) event).artifactType())
                .contains("orphaned-chunk", "incomplete-manifest", "broken-reference", "checksum-mismatch");
        assertCounterAtLeast(MicrometerStorageEventListener.RECOVERY_FINDINGS, 4,
                "operation", "recovery", "stage", "recovery-scan");
        assertCounterAtLeast(MicrometerStorageEventListener.RECOVERY_QUARANTINES, 4,
                "operation", "recovery", "stage", "recovery-quarantine");
        assertThat(output).contains("Storage recovery", "artifactType=orphaned-chunk", "artifactType=checksum-mismatch");
        assertNoSensitiveLeak(output, "SECRET-PAYLOAD-MUST-NOT-LEAK", "top-secret", "AKIA-MUST-NOT-LEAK");
    }

    @Test
    void REQ_OBS_006_redactionPreventsPayloadMetadataAndAuthorizationLeakAcrossSignals(CapturedOutput output) {
        String bucket = "obs-redaction-bucket";
        String key = "observability/2026/redaction/secret-object.txt";
        String body = "SECRET-PAYLOAD-MUST-NOT-LEAK";

        createBucket(bucket);
        client.put()
                .uri(path(bucket, key))
                .contentType(MediaType.TEXT_PLAIN)
                .header("x-amz-storage-class", "STANDARD")
                .header("x-amz-meta-secret", "top-secret")
                .header("x-amz-meta-token", "token-123")
                .header("x-amz-meta-customer-email", "customer@example.test")
                .header("Authorization", "AWS4-HMAC-SHA256 Credential=AKIA-MUST-NOT-LEAK")
                .bodyValue(body)
                .exchange()
                .expectStatus().isOk();
        String downloaded = getObject(bucket, key);

        assertThat(downloaded).isEqualTo(body);
        assertThat(events.events()).isNotEmpty();
        assertThat(events.events()).allSatisfy(event -> assertThat(event.correlationId()).isNotBlank());
        assertCounterAtLeast(MicrometerStorageEventListener.BYTES, body.length(),
                "operation", "put-object", "stage", "chunk-persistence", "direction", "write");
        assertThat(finishedSpans(spanExporter)).isNotEmpty();
        assertNoSensitiveLeak(output, body, "top-secret", "token-123", "customer@example.test", "AKIA-MUST-NOT-LEAK");
    }

    private void createBucket(String bucket) {
        client.put()
                .uri(path(bucket))
                .exchange()
                .expectStatus().isOk();
    }

    private void putObject(String bucket, String key, String body, String... extraHeaderPairs) {
        WebTestClient.RequestBodySpec spec = client.put()
                .uri(path(bucket, key))
                .contentType(MediaType.TEXT_PLAIN)
                .header("x-amz-storage-class", "STANDARD");
        for (int index = 0; index < extraHeaderPairs.length; index += 2) {
            spec.header(extraHeaderPairs[index], extraHeaderPairs[index + 1]);
        }
        spec.bodyValue(body)
                .exchange()
                .expectStatus().isOk();
    }

    private void putObject(String bucket, String key, String body) {
        putObject(bucket, key, body, new String[0]);
    }

    private String getObject(String bucket, String key) {
        return drain(client.get()
                .uri(path(bucket, key))
                .exchange()
                .expectStatus().isOk()
                .returnResult(DataBuffer.class)
                .getResponseBody());
    }

    private void assertStages(String operation, List<String> expectedStages) {
        Set<String> stages = events.events().stream()
                .filter(event -> event.outcome().orElse("success").equals("success"))
                .filter(event -> switch (operation) {
                    case "put-object" -> event.operation().name().equals("WRITE");
                    case "get-object" -> event.operation().name().equals("READ");
                    default -> false;
                })
                .map(StorageEvent::stageName)
                .collect(Collectors.toSet());
        assertThat(stages).containsAll(expectedStages);
    }

    private void assertCounterAtLeast(String name, double expected, String... tags) {
        var search = meterRegistry.find(name).tag("backend", "filesystem");
        for (int index = 0; index < tags.length; index += 2) {
            search.tag(tags[index], tags[index + 1]);
        }
        var counter = search.counter();
        assertThat(counter).as(name + " " + List.of(tags)).isNotNull();
        assertThat(counter.count()).isGreaterThanOrEqualTo(expected);
    }

    private void assertNoSensitiveLeak(CapturedOutput output, String... forbidden) {
        String eventPayload = events.events().toString();
        String meterPayload = meterRegistry.getMeters().stream()
                .map(meter -> meter.getId().toString())
                .toList()
                .toString();
        String spanPayload = finishedSpans(spanExporter).stream()
                .map(span -> span.getName() + span.getAttributes().asMap())
                .toList()
                .toString();
        String logPayload = output.toString();
        for (String value : forbidden) {
            assertThat(eventPayload).doesNotContain(value);
            assertThat(meterPayload).doesNotContain(value);
            assertThat(spanPayload).doesNotContain(value);
            assertThat(logPayload).doesNotContain(value);
        }
    }

    private void createRecoveryFindings() {
        try {
            Path chunksDir = STORAGE_ROOT.resolve("nodes/node-001/chunks");
            Files.createDirectories(chunksDir);
            Files.writeString(chunksDir.resolve("orphan.tmp.abandoned"), "partial", StandardCharsets.UTF_8);
            Files.writeString(chunksDir.resolve("standalone-corrupt-chunk"), "corrupt", StandardCharsets.UTF_8);
            Files.writeString(chunksDir.resolve("standalone-corrupt-chunk.sha256"), sha256Hex("expected"), StandardCharsets.UTF_8);

            Path manifestsDir = STORAGE_ROOT.resolve("metadata/manifests");
            Files.createDirectories(manifestsDir);
            Files.writeString(manifestsDir.resolve("incomplete.properties.tmp.left"), "manifestId=incomplete", StandardCharsets.UTF_8);

            Path referencesDir = STORAGE_ROOT.resolve("metadata/s3-object-references/broken-bucket");
            Files.createDirectories(referencesDir);
            Files.writeString(referencesDir.resolve("broken-key.properties"), "manifestId=absent-manifest", StandardCharsets.UTF_8);
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }
}
