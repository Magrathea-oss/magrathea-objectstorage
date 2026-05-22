package com.example.magrathea.objectstorage.domain.aggregate;

import com.example.magrathea.objectstorage.domain.valueobject.ObjectKey;
import com.example.magrathea.objectstorage.domain.valueobject.UploadId;
import com.example.magrathea.objectstorage.domain.valueobject.UploadPart;

import java.time.Instant;
import java.util.Collections;
import java.util.List;
import java.util.Objects;
import java.util.UUID;

/**
 * AWS S3 MultipartUpload — aggregate root as a Java 17+ record.
 * Tracks an ongoing multipart upload session.
 * Immutable — transitions produce new records.
 *
 * Pure domain — NO framework dependencies.
 */
public record MultipartUpload(
    Id id,
    Bucket.Id bucketId,
    ObjectKey key,
    UploadId uploadId,
    Instant initiated,
    List<UploadPart> parts,
    boolean completed,
    boolean aborted
) {

    public record Id(String value) {
        public Id { Objects.requireNonNull(value); }
        public static Id generate() { return new Id(UUID.randomUUID().toString()); }
        public static Id of(String value) { return new Id(value); }
    }

    public MultipartUpload {
        Objects.requireNonNull(id);
        Objects.requireNonNull(bucketId);
        Objects.requireNonNull(key);
        Objects.requireNonNull(uploadId);
        Objects.requireNonNull(initiated);
        Objects.requireNonNull(parts);
        if (completed && aborted)
            throw new IllegalStateException("MultipartUpload cannot be both completed and aborted");
    }

    /**
     * Factory method — create a new multipart upload session.
     */
    public static MultipartUpload create(Id id, Bucket.Id bucketId, ObjectKey key, UploadId uploadId) {
        Objects.requireNonNull(id);
        Objects.requireNonNull(bucketId);
        Objects.requireNonNull(key);
        Objects.requireNonNull(uploadId);
        return new MultipartUpload(id, bucketId, key, uploadId, Instant.now(), List.of(), false, false);
    }

    /**
     * Factory method — restore from persistence.
     */
    public static MultipartUpload restore(Id id, Bucket.Id bucketId, ObjectKey key,
                                          UploadId uploadId, Instant initiated,
                                          List<UploadPart> parts, boolean completed,
                                          boolean aborted) {
        Objects.requireNonNull(id);
        Objects.requireNonNull(bucketId);
        Objects.requireNonNull(key);
        Objects.requireNonNull(uploadId);
        Objects.requireNonNull(initiated);
        Objects.requireNonNull(parts);
        return new MultipartUpload(id, bucketId, key, uploadId, initiated,
            List.copyOf(parts), completed, aborted);
    }

    /**
     * Add a part to this upload. Returns new MultipartUpload with the part appended.
     */
    public MultipartUpload withPart(UploadPart part) {
        Objects.requireNonNull(part);
        if (completed) throw new IllegalStateException("Cannot add part to completed upload");
        if (aborted) throw new IllegalStateException("Cannot add part to aborted upload");
        var newParts = new java.util.ArrayList<>(parts);
        newParts.add(part);
        return new MultipartUpload(id, bucketId, key, uploadId, initiated,
            Collections.unmodifiableList(newParts), false, false);
    }

    /**
     * Mark this upload as completed. Returns new MultipartUpload with completed=true.
     */
    public MultipartUpload withCompleted() {
        if (aborted) throw new IllegalStateException("Cannot complete an aborted upload");
        return new MultipartUpload(id, bucketId, key, uploadId, initiated, parts, true, false);
    }

    /**
     * Mark this upload as aborted. Returns new MultipartUpload with aborted=true.
     */
    public MultipartUpload withAborted() {
        if (completed) throw new IllegalStateException("Cannot abort a completed upload");
        return new MultipartUpload(id, bucketId, key, uploadId, initiated, parts, false, true);
    }

    public boolean isActive() { return !completed && !aborted; }
    public boolean hasParts() { return !parts.isEmpty(); }
    public int partCount() { return parts.size(); }
}
