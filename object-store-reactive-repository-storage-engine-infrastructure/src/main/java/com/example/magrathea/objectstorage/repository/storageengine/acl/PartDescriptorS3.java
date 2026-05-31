package com.example.magrathea.objectstorage.repository.storageengine.acl;

import java.util.Optional;

/**
 * ACL helper — describes a single part in an S3 multipart upload for translation to
 * Storage Engine's {@code PartDescriptor}.
 * <p>
 * This is the Object Store side part descriptor, carrying S3-specific fields
 * (part number, ETag, size, checksum) before translation to the Storage Engine
 * {@code com.example.magrathea.storageengine.domain.valueobject.PartDescriptor}.
 * </p>
 */
public record PartDescriptorS3(
    int partNumber,
    long partSize,
    String etag,
    Optional<ChecksumDescriptor> partChecksum
) {
    public static PartDescriptorS3 of(
            int partNumber,
            long partSize,
            String etag,
            Optional<ChecksumDescriptor> partChecksum) {
        return new PartDescriptorS3(partNumber, partSize, etag, partChecksum);
    }
}
