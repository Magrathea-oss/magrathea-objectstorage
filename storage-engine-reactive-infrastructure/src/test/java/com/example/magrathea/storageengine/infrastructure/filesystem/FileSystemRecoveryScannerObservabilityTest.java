package com.example.magrathea.storageengine.infrastructure.filesystem;

import com.example.magrathea.storageengine.application.pipeline.CompositeStorageEventPublisher;
import com.example.magrathea.storageengine.application.pipeline.StorageEventPublisher;
import com.example.magrathea.storageengine.infrastructure.observability.MicrometerStorageEventListener;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class FileSystemRecoveryScannerObservabilityTest {

    @TempDir
    Path tempDir;

    @Test
    void recoveryScannerPublishesFindingAndQuarantineMetrics() throws Exception {
        SimpleMeterRegistry meterRegistry = new SimpleMeterRegistry();
        StorageEventPublisher publisher = new CompositeStorageEventPublisher(List.of(
                new MicrometerStorageEventListener(meterRegistry)));
        FileSystemRecoveryScanner scanner = new FileSystemRecoveryScanner(publisher);
        Path chunksDir = tempDir.resolve("nodes/node-1/chunks");
        Files.createDirectories(chunksDir);
        Files.writeString(chunksDir.resolve("chunk-1.tmp.abandoned"), "partial-data", StandardCharsets.UTF_8);

        FileSystemRecoveryScanner.ScanReport report = scanner.scan(tempDir);
        scanner.quarantine(tempDir, report);

        assertThat(report.findings()).hasSize(1);
        assertThat(meterRegistry.counter(MicrometerStorageEventListener.RECOVERY_FINDINGS,
                "operation", "recovery", "stage", "recovery-scan", "backend", "filesystem",
                "outcome", "findings").count())
                .isEqualTo(1.0);
        assertThat(meterRegistry.counter(MicrometerStorageEventListener.RECOVERY_QUARANTINES,
                "operation", "recovery", "stage", "recovery-quarantine", "backend", "filesystem",
                "outcome", "quarantined").count())
                .isEqualTo(1.0);
        assertThat(Files.exists(chunksDir.resolve("chunk-1.tmp.abandoned"))).isFalse();
        assertThat(Files.exists(tempDir.resolve("quarantine/nodes/node-1/chunks/chunk-1.tmp.abandoned"))).isTrue();
    }
}
