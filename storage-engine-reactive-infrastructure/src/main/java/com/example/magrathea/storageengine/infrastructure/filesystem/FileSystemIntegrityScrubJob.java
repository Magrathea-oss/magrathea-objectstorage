package com.example.magrathea.storageengine.infrastructure.filesystem;

import com.example.magrathea.storageengine.infrastructure.filesystem.config.StorageEngineIntegrityScrubEnabledCondition;
import org.springframework.context.annotation.Conditional;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.util.concurrent.atomic.AtomicReference;

/** Periodic, disabled-by-default filesystem integrity scrub job. */
@Component
@Conditional(StorageEngineIntegrityScrubEnabledCondition.class)
public class FileSystemIntegrityScrubJob {

    private final FileSystemStorageCluster storageCluster;
    private final FileSystemIntegrityScrubber scrubber;
    private final FileSystemIntegrityScrubber.RepairPolicy repairPolicy;
    private final AtomicReference<FileSystemIntegrityScrubber.ScrubReport> lastReport = new AtomicReference<>();

    public FileSystemIntegrityScrubJob(
            FileSystemStorageCluster storageCluster,
            FileSystemIntegrityScrubber scrubber,
            @Value("${storage.engine.integrity.scrub.repair-policy:REPORT_ONLY}") String repairPolicy) {
        this.storageCluster = java.util.Objects.requireNonNull(storageCluster, "storageCluster must not be null");
        this.scrubber = java.util.Objects.requireNonNull(scrubber, "scrubber must not be null");
        this.repairPolicy = FileSystemIntegrityScrubber.RepairPolicy.valueOf(repairPolicy.trim().toUpperCase());
    }

    @Scheduled(
            fixedDelayString = "${storage.engine.integrity.scrub.interval-ms:86400000}",
            initialDelayString = "${storage.engine.integrity.scrub.initial-delay-ms:60000}")
    public void runPeriodicScrub() {
        lastReport.set(scrubber.scrub(storageCluster.clusterRoot(), repairPolicy));
    }

    public FileSystemIntegrityScrubber.ScrubReport runOnce() {
        FileSystemIntegrityScrubber.ScrubReport report =
                scrubber.scrub(storageCluster.clusterRoot(), repairPolicy);
        lastReport.set(report);
        return report;
    }

    public java.util.Optional<FileSystemIntegrityScrubber.ScrubReport> lastReport() {
        return java.util.Optional.ofNullable(lastReport.get());
    }
}
