package com.example.magrathea.storageengine.infrastructure.observability;

import com.example.magrathea.storageengine.application.observability.StorageObservabilityFields;
import com.example.magrathea.storageengine.application.pipeline.StorageEvent;
import com.example.magrathea.storageengine.application.pipeline.StorageEventListener;
import com.example.magrathea.storageengine.application.pipeline.StorageEventType;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import reactor.core.publisher.Mono;

import java.util.Map;

/**
 * Structured operational log adapter for storage observability events.
 * Only safe fields produced by {@link StorageObservabilityFields} are logged.
 */
public final class StorageOperationalLoggingEventListener implements StorageEventListener {

    private static final Logger log = LoggerFactory.getLogger(StorageOperationalLoggingEventListener.class);

    @Override
    public Mono<Void> onEvent(StorageEvent event) {
        return Mono.fromRunnable(() -> log(event));
    }

    private void log(StorageEvent event) {
        if (!shouldLog(event)) {
            return;
        }
        Map<String, String> fields = StorageObservabilityFields.safeFields(event);
        if (event.type() == StorageEventType.STAGE_FAILED) {
            log.warn("Storage pipeline event correlationId={} requestId={} operation={} stage={} outcome={} failureClassification={} backend={} storageRoot={} requestedBytes={} availableBytes={} manifestId={} durationMs={}",
                    fields.get("correlation.id"),
                    fields.get("request.id"),
                    fields.get("operation"),
                    fields.get("stage"),
                    fields.get("outcome"),
                    fields.get("failure.classification"),
                    fields.get("backend"),
                    fields.getOrDefault("storage.root", "none"),
                    fields.getOrDefault("requested.bytes", "none"),
                    fields.getOrDefault("available.bytes", "none"),
                    fields.getOrDefault("manifest.id", "none"),
                    fields.getOrDefault("duration.ms", "0"));
            return;
        }
        if (event.type() == StorageEventType.RECOVERY_ARTIFACT_QUARANTINED) {
            log.warn("Storage recovery event correlationId={} requestId={} operation={} stage={} outcome={} backend={} artifactType={} artifactHash={} quarantineCount={}",
                    fields.get("correlation.id"),
                    fields.get("request.id"),
                    fields.get("operation"),
                    fields.get("stage"),
                    fields.get("outcome"),
                    fields.get("backend"),
                    fields.getOrDefault("artifact.type", "unknown"),
                    fields.getOrDefault("artifact.hash", "none"),
                    fields.getOrDefault("recovery.quarantines", "0"));
            return;
        }
        if (event.type() == StorageEventType.RECOVERY_SCAN_COMPLETED) {
            log.info("Storage recovery event correlationId={} requestId={} operation={} stage={} outcome={} backend={} findingCount={} durationMs={}",
                    fields.get("correlation.id"),
                    fields.get("request.id"),
                    fields.get("operation"),
                    fields.get("stage"),
                    fields.get("outcome"),
                    fields.get("backend"),
                    fields.getOrDefault("recovery.findings", "0"),
                    fields.getOrDefault("duration.ms", "0"));
            return;
        }
        log.info("Storage pipeline event correlationId={} requestId={} operation={} stage={} outcome={} backend={} manifestId={} durationMs={}",
                fields.get("correlation.id"),
                fields.get("request.id"),
                fields.get("operation"),
                fields.get("stage"),
                fields.get("outcome"),
                fields.get("backend"),
                fields.getOrDefault("manifest.id", "none"),
                fields.getOrDefault("duration.ms", "0"));
    }

    private static boolean shouldLog(StorageEvent event) {
        return event.type() == StorageEventType.STAGE_FAILED
                || event.type() == StorageEventType.CLEANUP_COMPLETED
                || event.type() == StorageEventType.RECOVERY_SCAN_COMPLETED
                || event.type() == StorageEventType.RECOVERY_ARTIFACT_QUARANTINED;
    }
}
