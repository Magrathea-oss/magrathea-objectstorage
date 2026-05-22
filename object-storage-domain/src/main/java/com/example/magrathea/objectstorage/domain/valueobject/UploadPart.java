package com.example.magrathea.objectstorage.domain.valueobject;

import java.time.Instant;
import java.util.Objects;

/**
 * AWS S3 Upload Part — value object.
 * Represents a single part uploaded during a multipart upload session.
 * Immutable, zero framework dependencies.
 */
public record UploadPart(
    PartNumber partNumber,
    String etag,
    long size,
    Instant lastModified
) {

    public UploadPart {
        Objects.requireNonNull(partNumber);
        Objects.requireNonNull(etag);
        Objects.requireNonNull(lastModified);
        if (size < 0) throw new IllegalArgumentException("size must be non-negative");
    }

    public static UploadPart create(PartNumber partNumber, String etag, long size) {
        Objects.requireNonNull(partNumber);
        Objects.requireNonNull(etag);
        if (size < 0) throw new IllegalArgumentException("size must be non-negative");
        return new UploadPart(partNumber, etag, size, Instant.now());
    }

    public static UploadPart of(PartNumber partNumber, String etag, long size, Instant lastModified) {
        Objects.requireNonNull(partNumber);
        Objects.requireNonNull(etag);
        Objects.requireNonNull(lastModified);
        if (size < 0) throw new IllegalArgumentException("size must be non-negative");
        return new UploadPart(partNumber, etag, size, lastModified);
    }
}
