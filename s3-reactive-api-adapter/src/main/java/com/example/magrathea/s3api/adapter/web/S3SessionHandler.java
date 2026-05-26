package com.example.magrathea.s3api.adapter.web;

import com.example.magrathea.s3api.dto.query.CreateSessionQuery;
import org.springframework.http.MediaType;
import org.springframework.web.reactive.function.server.ServerRequest;
import org.springframework.web.reactive.function.server.ServerResponse;
import reactor.core.publisher.Mono;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Session-context S3 batch operations.
 * Handles creation and management of batch operation sessions.
 * Uses an in-memory store for session tokens — no domain dependencies.
 */
public class S3SessionHandler {

    private final Map<String, SessionEntry> sessions = new ConcurrentHashMap<>();

    public S3SessionHandler() {
    }

    /** POST /{bucket}?session — CreateSession */
    public Mono<ServerResponse> createSession(ServerRequest request) {
        var bucketName = request.pathVariable("bucket");
        var token = UUID.randomUUID().toString();
        sessions.put(token, new SessionEntry(bucketName, System.currentTimeMillis()));
        return ServerResponse.ok()
            .contentType(MediaType.APPLICATION_XML)
            .bodyValue(CreateSessionQuery.from(token));
    }

    /**
     * In-memory session entry.
     */
    private record SessionEntry(String bucketName, long createdAt) {}
}
