package com.example.magrathea.objectstore.domain.valueobject;

import java.util.Objects;

/**
 * ContentDescriptor — a value object that describes stored content without holding raw bytes.
 * <p>
 * Contains metadata needed for the infrastructure to retrieve actual content:
 * <ul>
 *   <li>{@code size} — content size in bytes</li>
 *   <li>{@code md5Hash} — MD5 hash (or etag) of the content</li>
 *   <li>{@code contentId} — a reference ID for infrastructure to retrieve the actual bytes</li>
 * </ul>
 * <p>
 * Pure domain — NO framework dependencies.
 */
public record ContentDescriptor(
    long size,
    String md5Hash,
    String contentId
) {

    public ContentDescriptor {
        Objects.requireNonNull(md5Hash);
        Objects.requireNonNull(contentId);
        if (size < 0) {
            throw new IllegalArgumentException("size must be non-negative");
        }
    }

    public static ContentDescriptor of(long size, String md5Hash, String contentId) {
        return new ContentDescriptor(size, md5Hash, contentId);
    }
}
