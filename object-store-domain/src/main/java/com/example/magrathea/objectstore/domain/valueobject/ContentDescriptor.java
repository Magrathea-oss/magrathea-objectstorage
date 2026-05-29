package com.example.magrathea.objectstore.domain.valueobject;

import java.util.Objects;
import java.util.Set;

/**
 * ContentDescriptor — a value object that describes stored content without holding raw bytes.
 * <p>
 * Contains metadata needed for the infrastructure to retrieve actual content:
 * <ul>
 *   <li>{@code size} — content size in bytes</li>
 *   <li>{@code md5Hash} — MD5 hash (or etag) of the content, when provided</li>
 *   <li>{@code contentId} — a reference ID for infrastructure to retrieve the actual bytes</li>
 *   <li>{@code checksums} — typed AWS S3 checksum values (required, may be empty)</li>
 *   <li>{@code sdkChecksumAlgorithm} — SDK checksum algorithm name used by the client, when provided</li>
 * </ul>
 * <p>
 * Pure domain — NO framework dependencies.
 */
public record ContentDescriptor(
    long size,
    String md5Hash,
    String contentId,
    Set<ChecksumValue> checksums,
    String sdkChecksumAlgorithm
) {

    public ContentDescriptor {
        Objects.requireNonNull(contentId, "contentId must not be null");
        Objects.requireNonNull(checksums, "checksums must not be null");
        if (size < 0) {
            throw new IllegalArgumentException("size must be non-negative");
        }
        // md5Hash may be null (not provided).
        // sdkChecksumAlgorithm may be null (not provided).
        checksums = Set.copyOf(checksums);
    }

    public ContentDescriptor(long size, String md5Hash, String contentId) {
        this(size, md5Hash, contentId, Set.of(), null);
    }

    public static ContentDescriptor of(long size, String md5Hash, String contentId) {
        return new ContentDescriptor(size, md5Hash, contentId, Set.of(), null);
    }

    public static ContentDescriptor of(long size, String md5Hash, String contentId,
                                       Set<ChecksumValue> checksums) {
        return new ContentDescriptor(size, md5Hash, contentId, checksums, null);
    }

    public static ContentDescriptor of(long size, String md5Hash, String contentId,
                                       Set<ChecksumValue> checksums,
                                       String sdkChecksumAlgorithm) {
        return new ContentDescriptor(size, md5Hash, contentId, checksums, sdkChecksumAlgorithm);
    }
}
