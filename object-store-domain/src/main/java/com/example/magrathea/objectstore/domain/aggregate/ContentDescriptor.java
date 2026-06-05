package com.example.magrathea.objectstore.domain.aggregate;

import com.example.magrathea.objectstore.domain.valueobject.ObjectChecksum;

import java.util.Objects;
import java.util.Optional;

/**
 * ContentDescriptor — value record encapsulating content metadata for an S3 object.
 * <p>
 * Pure domain — NO framework dependencies.
 */
public record ContentDescriptor(
    long size,
    ObjectChecksum checksum,
    Optional<String> contentType,
    Optional<String> contentDisposition,
    Optional<String> contentEncoding,
    Optional<String> contentLanguage
) {
    public ContentDescriptor {
        Objects.requireNonNull(checksum, "checksum must not be null");
        if (size < 0) throw new IllegalArgumentException("size must be non-negative: " + size);
        contentType = Objects.requireNonNull(contentType, "contentType must not be null");
        contentDisposition = Objects.requireNonNull(contentDisposition, "contentDisposition must not be null");
        contentEncoding = Objects.requireNonNull(contentEncoding, "contentEncoding must not be null");
        contentLanguage = Objects.requireNonNull(contentLanguage, "contentLanguage must not be null");
    }

    /**
     * Factory method — create with required fields and optional content metadata.
     */
    public static ContentDescriptor of(
            long size,
            ObjectChecksum checksum,
            String contentType,
            String contentDisposition,
            String contentEncoding,
            String contentLanguage) {
        return new ContentDescriptor(
            size,
            checksum,
            Optional.ofNullable(contentType),
            Optional.ofNullable(contentDisposition),
            Optional.ofNullable(contentEncoding),
            Optional.ofNullable(contentLanguage)
        );
    }

    /**
     * Factory method — create with only size and checksum, all optional fields empty.
     */
    public static ContentDescriptor of(long size, ObjectChecksum checksum) {
        return new ContentDescriptor(size, checksum,
            Optional.empty(), Optional.empty(),
            Optional.empty(), Optional.empty());
    }
}
