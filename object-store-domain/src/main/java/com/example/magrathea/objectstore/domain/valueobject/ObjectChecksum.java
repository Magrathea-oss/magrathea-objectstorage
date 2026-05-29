package com.example.magrathea.objectstore.domain.valueobject;

import java.util.Objects;
import java.util.Set;

/**
 * ObjectChecksum — a value object that encapsulates checksum values associated
 * with an S3 object.
 * <p>
 * Holds a {@link Set} of {@link ChecksumValue} entries (one per algorithm) plus
 * an optional SDK-level checksum algorithm hint string ({@code x-amz-sdk-checksum-algorithm}).
 * </p>
 * <p>
 * Pure domain — NO framework dependencies.
 */
public record ObjectChecksum(
    Set<ChecksumValue> checksums,
    String sdkAlgorithm
) {

    public ObjectChecksum {
        Objects.requireNonNull(checksums, "checksums must not be null");
        checksums = Set.copyOf(checksums);
        // sdkAlgorithm may be null (not provided).
    }

    /**
     * Creates an {@code ObjectChecksum} with the given checksums and no SDK algorithm hint.
     *
     * @param checksums the set of checksum values
     * @return a new {@code ObjectChecksum}
     */
    public static ObjectChecksum of(Set<ChecksumValue> checksums) {
        return new ObjectChecksum(checksums, null);
    }

    /**
     * Creates an {@code ObjectChecksum} with the given checksums and an optional SDK algorithm hint.
     *
     * @param checksums     the set of checksum values
     * @param sdkAlgorithm  the SDK-level checksum algorithm hint (may be null)
     * @return a new {@code ObjectChecksum}
     */
    public static ObjectChecksum of(Set<ChecksumValue> checksums, String sdkAlgorithm) {
        return new ObjectChecksum(checksums, sdkAlgorithm);
    }

    /**
     * Returns {@code true} if this container holds no checksum entries.
     *
     * @return {@code true} if empty
     */
    public boolean isEmpty() {
        return checksums.isEmpty();
    }
}
