package com.example.magrathea.objectstorage.domain.aggregate;

import com.example.magrathea.objectstorage.domain.event.ObjectStorageEvent;
import com.example.magrathea.objectstorage.domain.valueobject.ObjectKey;
import com.example.magrathea.objectstorage.domain.valueobject.UploadId;
import com.example.magrathea.objectstorage.domain.valueobject.UploadPart;

import java.time.Instant;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Objects;
import java.util.UUID;

/**
 * AWS S3 MultipartUpload — aggregate root as a Java 17+ record.
 * Tracks an ongoing multipart upload session.
 * Immutable — transitions produce new records with accumulated domain events.
 *
 * Pure domain — NO framework dependencies.
 *
 * Transitions return NEW instances with accumulated domain events.
 * Call {@link #domainEvents()} to retrieve events after a transition.
 * Call {@link #clearEvents()} to obtain a clean instance for persistence.
 */
public record MultipartUpload(
    Id id,
    Bucket.Id bucketId,
    ObjectKey key,
    UploadId uploadId,
    Instant initiated,
    List<UploadPart> parts,
    boolean completed,
    boolean aborted,
    List<ObjectStorageEvent> events
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
        Objects.requireNonNull(events);
        if (completed && aborted)
            throw new IllegalStateException("MultipartUpload cannot be both completed and aborted");
    }

    /**
     * Factory method — create a new multipart upload session.
     * Records a {@link ObjectStorageEvent.MultipartUploadCreated} event.
     */
    public static MultipartUpload create(Id id, Bucket.Id bucketId, ObjectKey key, UploadId uploadId) {
        Objects.requireNonNull(id);
        Objects.requireNonNull(bucketId);
        Objects.requireNonNull(key);
        Objects.requireNonNull(uploadId);
        var now = Instant.now();
        var events = List.<ObjectStorageEvent>of(
            new ObjectStorageEvent.MultipartUploadCreated(id, uploadId, bucketId, key, now)
        );
        return new MultipartUpload(id, bucketId, key, uploadId, now, List.of(), false, false, events);
    }

    /**
     * Factory method — restore from persistence (no events).
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
            List.copyOf(parts), completed, aborted, List.of());
    }

    /**
     * Add a part to this upload.
     * Returns new MultipartUpload with the part appended and a {@link ObjectStorageEvent.PartUploaded} event.
     */
    public MultipartUpload withPart(UploadPart part) {
        Objects.requireNonNull(part);
        if (completed) throw new IllegalStateException("Cannot add part to completed upload");
        if (aborted) throw new IllegalStateException("Cannot add part to aborted upload");
        var newParts = new ArrayList<>(parts);
        newParts.add(part);
        var newEvents = appendEvent(
            new ObjectStorageEvent.PartUploaded(id, uploadId, part.partNumber().value(), part.etag(), Instant.now())
        );
        return new MultipartUpload(id, bucketId, key, uploadId, initiated,
            Collections.unmodifiableList(newParts), false, false, newEvents);
    }

    /**
     * Mark this upload as completed.
     * Returns new MultipartUpload with completed=true and a {@link ObjectStorageEvent.MultipartUploadCompleted} event.
     */
    public MultipartUpload withCompleted() {
        if (aborted) throw new IllegalStateException("Cannot complete an aborted upload");
        var newEvents = appendEvent(
            new ObjectStorageEvent.MultipartUploadCompleted(id, uploadId, bucketId, key, Instant.now())
        );
        return new MultipartUpload(id, bucketId, key, uploadId, initiated, parts, true, false, newEvents);
    }

    /**
     * Mark this upload as aborted.
     * Returns new MultipartUpload with aborted=true and a {@link ObjectStorageEvent.MultipartUploadAborted} event.
     */
    public MultipartUpload withAborted() {
        if (completed) throw new IllegalStateException("Cannot abort a completed upload");
        var newEvents = appendEvent(
            new ObjectStorageEvent.MultipartUploadAborted(id, uploadId, Instant.now())
        );
        return new MultipartUpload(id, bucketId, key, uploadId, initiated, parts, false, true, newEvents);
    }

    public boolean isActive() { return !completed && !aborted; }
    public boolean hasParts() { return !parts.isEmpty(); }
    public int partCount() { return parts.size(); }

    /**
     * Returns the accumulated domain events since the last {@link #clearEvents()}.
     */
    public List<ObjectStorageEvent> domainEvents() {
        return events;
    }

    /**
     * Returns a new MultipartUpload with all events cleared — suitable for persistence.
     */
    public MultipartUpload clearEvents() {
        return new MultipartUpload(id, bucketId, key, uploadId, initiated, parts, completed, aborted, List.of());
    }

    private List<ObjectStorageEvent> appendEvent(ObjectStorageEvent event) {
        var newEvents = new ArrayList<ObjectStorageEvent>(events.size() + 1);
        newEvents.addAll(events);
        newEvents.add(event);
        return Collections.unmodifiableList(newEvents);
    }
}
