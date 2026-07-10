package com.example.magrathea.s3api.security;

import java.time.Instant;
import java.util.List;

public interface S3SecurityAuditSink {

    void record(AuditEvent event);

    List<AuditEvent> events();

    void clear();

    record AuditEvent(
        Instant timestamp,
        String requestId,
        String principal,
        String action,
        String bucket,
        String key,
        String decision,
        String reason,
        int responseStatus,
        String encryptionMode
    ) {
        public AuditEvent(Instant timestamp, String principal, String action, String bucket, String key,
                          String decision, String reason, int responseStatus) {
            this(timestamp, java.util.UUID.randomUUID().toString(), principal, action, bucket, key,
                decision, reason, responseStatus, null);
        }
    }
}
