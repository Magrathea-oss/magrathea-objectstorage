package com.example.magrathea.s3api.adapter.web;

import com.example.magrathea.reactive.application.service.ReactiveBucketService;
import com.example.magrathea.s3api.dto.query.CreateSessionQuery;
import org.springframework.http.MediaType;
import org.springframework.web.reactive.function.server.ServerRequest;
import org.springframework.web.reactive.function.server.ServerResponse;
import reactor.core.publisher.Mono;

/**
 * Session-context S3 batch operations.
 * Handles creation and management of batch operation sessions.
 * Delegates session creation to ReactiveBucketService.
 */
public class S3SessionHandler {

    private final ReactiveBucketService bucketService;

    public S3SessionHandler(ReactiveBucketService bucketService) {
        this.bucketService = bucketService;
    }

    /** POST /{bucket}?session — CreateSession */
    public Mono<ServerResponse> createSession(ServerRequest request) {
        return bucketService.createSession()
            .flatMap(token -> ServerResponse.ok()
                .contentType(MediaType.APPLICATION_XML)
                .bodyValue(CreateSessionQuery.from(token.value())));
    }
}
