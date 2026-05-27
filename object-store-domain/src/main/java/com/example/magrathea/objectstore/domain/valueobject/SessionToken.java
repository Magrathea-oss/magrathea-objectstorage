package com.example.magrathea.objectstore.domain.valueobject;

import java.util.Objects;

/**
 * SessionToken — a value object representing an S3 session token.
 * <p>
 * Encapsulates the token string returned by CreateSession for delegated
 * authorization in directory buckets.
 * </p>
 * Pure domain — NO framework dependencies.
 */
public record SessionToken(
    String value
) {

    public SessionToken {
        Objects.requireNonNull(value);
        if (value.isBlank()) throw new IllegalArgumentException("Session token must not be blank");
    }

    /**
     * Factory method.
     */
    public static SessionToken of(String value) {
        return new SessionToken(value);
    }
}
