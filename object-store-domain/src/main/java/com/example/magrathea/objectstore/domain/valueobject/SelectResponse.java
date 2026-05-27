package com.example.magrathea.objectstore.domain.valueobject;

import java.util.Objects;

/**
 * SelectResponse — a value object representing the result metadata of a SELECT query.
 * <p>
 * Domain-level representation of a {@code SelectObjectContent} response.
 * Actual result records are streamed by infrastructure — domain only holds
 * result metadata (payload length, status).
 * </p>
 * Pure domain — NO framework dependencies.
 */
public record SelectResponse(
    long payloadLength,
    String status,
    String requestId
) {

    public SelectResponse {
        if (payloadLength < 0) throw new IllegalArgumentException("payloadLength must be non-negative");
        Objects.requireNonNull(status);
        Objects.requireNonNull(requestId);
    }

    /**
     * Factory method.
     */
    public static SelectResponse of(long payloadLength, String status, String requestId) {
        return new SelectResponse(payloadLength, status, requestId);
    }
}
