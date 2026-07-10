package com.example.magrathea.s3api.security;

import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;

public final class InMemoryS3SecurityAuditSink implements S3SecurityAuditSink {

    private final CopyOnWriteArrayList<AuditEvent> events = new CopyOnWriteArrayList<>();

    @Override
    public void record(AuditEvent event) {
        events.add(event);
    }

    @Override
    public List<AuditEvent> events() {
        return List.copyOf(events);
    }

    @Override
    public void clear() {
        events.clear();
    }
}
