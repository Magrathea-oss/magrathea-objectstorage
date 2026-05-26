package com.example.magrathea.objectstore.domain.valueobject;

import java.util.Objects;
import java.util.UUID;

/**
 * AWS S3 UploadId — value object.
 * Identifies a multipart upload session.
 * Immutable, zero framework dependencies.
 */
public record UploadId(String value) {

    public UploadId {
        Objects.requireNonNull(value);
        if (value.isBlank()) throw new IllegalArgumentException("UploadId must not be blank");
        if (!value.matches("^[a-zA-Z0-9]+$"))
            throw new IllegalArgumentException("UploadId must be alphanumeric");
    }

    public static UploadId generate() {
        return new UploadId(UUID.randomUUID().toString().replace("-", ""));
    }

    public static UploadId of(String value) {
        return new UploadId(value);
    }
}
