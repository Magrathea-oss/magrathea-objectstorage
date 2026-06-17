package com.example.magrathea.bootstrap;

import com.example.magrathea.storageengine.application.pipeline.StorageEventType;
import com.example.magrathea.storageengine.infrastructure.observability.MicrometerStorageEventListener;
import io.micrometer.core.instrument.MeterRegistry;
import io.opentelemetry.sdk.testing.exporter.InMemorySpanExporter;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.system.OutputCaptureExtension;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.reactive.server.WebTestClient;

import java.nio.file.Path;
import java.util.List;

import static com.example.magrathea.bootstrap.Phase4ObservabilityTestSupport.cleanStorageRoot;
import static com.example.magrathea.bootstrap.Phase4ObservabilityTestSupport.finishedSpans;
import static com.example.magrathea.bootstrap.Phase4ObservabilityTestSupport.path;
import static com.example.magrathea.bootstrap.Phase4ObservabilityTestSupport.registerStorageEngineProperties;
import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest(
        webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
        properties = {
                "admin.server.port=0",
                "storage.engine.filesystem.node-count=1",
                "storage.engine.filesystem.fault-injection.interrupt-after-chunk-temp-write=true",
                "storage.engine.filesystem.fault-injection.leave-partial-temporary-artifacts=true"
        })
@ActiveProfiles("storage-engine")
@Import(Phase4ObservabilityTestSupport.ObservabilityProbeConfig.class)
@ExtendWith(OutputCaptureExtension.class)
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_EACH_TEST_METHOD)
class Phase4ObservabilityFaultInjectionIntegrationTest {

    private static final Path STORAGE_ROOT = Phase4ObservabilityTestSupport
            .createStorageRoot("magrathea-phase4-fault-");

    @DynamicPropertySource
    static void storageEngineProperties(DynamicPropertyRegistry registry) {
        registerStorageEngineProperties(registry, STORAGE_ROOT, "magrathea-phase4-fault-catalog-");
    }

    @LocalServerPort
    private int port;

    @Autowired
    private Phase4ObservabilityTestSupport.RecordingStorageEventListener events;

    @Autowired
    private MeterRegistry meterRegistry;

    @Autowired
    private InMemorySpanExporter spanExporter;

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
    void REQ_OBS_002_webClientFaultInjectionPublishesClassifiedFailureAndCleanup(CapturedOutput output) {
        String bucket = "obs-pipeline-failure-bucket";
        String key = "observability/2026/events/failed-object.bin";
        String body = "fault-injected-body-must-not-leak";

        client.put()
                .uri(path(bucket))
                .exchange()
                .expectStatus().isOk();

        client.put()
                .uri(path(bucket, key))
                .contentType(MediaType.APPLICATION_OCTET_STREAM)
                .header("x-amz-storage-class", "STANDARD")
                .header("x-amz-meta-secret", "top-secret")
                .header("Authorization", "AWS4-HMAC-SHA256 Credential=AKIA-MUST-NOT-LEAK")
                .bodyValue(body)
                .exchange()
                .expectStatus().is5xxServerError();

        assertThat(events.events()).anySatisfy(event -> {
            assertThat(event.type()).isEqualTo(StorageEventType.STAGE_FAILED);
            assertThat(event.stageName()).isEqualTo("chunk-persistence");
        });
        assertThat(events.events()).anySatisfy(event -> assertThat(event.type())
                .isEqualTo(StorageEventType.CLEANUP_COMPLETED));
        String failedCorrelationId = events.events().stream()
                .filter(candidate -> candidate.type() == StorageEventType.STAGE_FAILED)
                .findFirst()
                .orElseThrow()
                .correlationId();
        assertThat(events.events()).noneMatch(event -> failedCorrelationId.equals(event.correlationId())
                && event.type() == StorageEventType.STAGE_SUCCEEDED
                && ("manifest-persistence".equals(event.stageName())
                || "object-index-persistence".equals(event.stageName())));
        assertCounterAtLeast(MicrometerStorageEventListener.FAILURES, 1,
                "operation", "put-object", "stage", "chunk-persistence",
                "failure.classification", "storage-io-failure");
        assertThat(finishedSpans(spanExporter)).anySatisfy(span -> assertThat(span.getAttributes().asMap().toString())
                .contains("failure.classification", "storage-io-failure"));
        assertThat(output).contains("failureClassification=storage-io-failure");

        String allSignals = events.events() + meterRegistry.getMeters().toString()
                + finishedSpans(spanExporter) + output;
        assertThat(allSignals).doesNotContain(body, "top-secret", "AKIA-MUST-NOT-LEAK");
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
}
