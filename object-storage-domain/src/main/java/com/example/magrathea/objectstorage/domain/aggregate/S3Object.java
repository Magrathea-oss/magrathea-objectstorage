package com.example.magrathea.objectstorage.domain.aggregate;

import com.example.magrathea.objectstorage.domain.valueobject.ObjectKey;

import java.time.Instant;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;

/**
 * AWS S3 Object — aggregate root as a Java 17+ record.
 * Uses ONLY AWS S3 native terminology per domain constraint.
 *
 * An Amazon S3 object is the entity stored in a bucket,
 * consisting of: Key, ETag, Size, StorageClass, LastModified,
 * Content-Type, Content-Disposition, Content-Encoding, and Metadata.
 *
 * Pure domain — NO framework dependencies.
 */
public record S3Object(
    Id id,
    Bucket.Id bucketId,
    ObjectKey key,
    String etag,
    long size,
    String storageClass,
    Instant lastModified,
    String contentType,
    String contentDisposition,
    String contentEncoding,
    Map<String, String> metadata
) {

    public record Id(String value) {
        public Id { Objects.requireNonNull(value); }
        public static Id generate() { return new Id(UUID.randomUUID().toString()); }
        public static Id of(String value) { return new Id(value); }
    }

    public boolean hasEtag() { return etag != null; }
    public boolean hasStorageClass() { return storageClass != null; }
    public boolean hasContentType() { return contentType != null; }
    public boolean hasContentDisposition() { return contentDisposition != null; }
    public boolean hasContentEncoding() { return contentEncoding != null; }
    public boolean hasMetadata() { return !metadata.isEmpty(); }

    /**
     * Factory method — create a new S3Object from a PutObject request.
     */
    public static S3Object create(Id id, Bucket.Id bucketId, ObjectKey key,
                                  String contentType, String contentDisposition,
                                  String contentEncoding, long size,
                                  Map<String, String> metadata) {
        Objects.requireNonNull(id);
        Objects.requireNonNull(bucketId);
        Objects.requireNonNull(key);
        if (size < 0) throw new IllegalArgumentException("size must be non-negative");
        var meta = metadata != null
            ? Map.<String, String>copyOf(metadata)
            : Map.<String, String>of();
        return new S3Object(id, bucketId, key, null, size, null, Instant.now(),
            contentType, contentDisposition, contentEncoding, meta);
    }

    /**
     * Factory method for loading from persistence (already validated data).
     */
    public static S3Object restore(Id id, Bucket.Id bucketId, ObjectKey key,
                                   String etag, long size, String storageClass,
                                   Instant lastModified, String contentType,
                                   String contentDisposition, String contentEncoding,
                                   Map<String, String> metadata) {
        Objects.requireNonNull(id);
        Objects.requireNonNull(bucketId);
        Objects.requireNonNull(key);
        Objects.requireNonNull(lastModified);
        var meta = metadata != null
            ? Map.<String, String>copyOf(metadata)
            : Map.<String, String>of();
        return new S3Object(id, bucketId, key, etag, size, storageClass,
            lastModified, contentType, contentDisposition, contentEncoding, meta);
    }

    public S3Object withEtag(String etag) {
        return new S3Object(id, bucketId, key, etag, size, storageClass,
            lastModified, contentType, contentDisposition, contentEncoding, metadata);
    }

    public S3Object withStorageClass(String storageClass) {
        return new S3Object(id, bucketId, key, etag, size, storageClass,
            lastModified, contentType, contentDisposition, contentEncoding, metadata);
    }
}
