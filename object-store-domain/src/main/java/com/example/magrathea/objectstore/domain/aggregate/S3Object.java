package com.example.magrathea.objectstore.domain.aggregate;

import com.example.magrathea.objectstore.domain.event.ObjectStoreEvent;
import com.example.magrathea.objectstore.domain.valueobject.ContentDescriptor;
import com.example.magrathea.objectstore.domain.valueobject.ObjectKey;

import java.time.Instant;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
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
 * Content bytes are NOT stored in the domain — use {@link ContentDescriptor}
 * for content metadata and a reference ID for infrastructure retrieval.
 *
 * Pure domain — NO framework dependencies.
 *
 * Transitions return NEW instances with accumulated domain events.
 * Call {@link #domainEvents()} to retrieve events after a transition.
 * Call {@link #clearEvents()} to obtain a clean instance for persistence.
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
    Map<String, String> metadata,
    ContentDescriptor contentDescriptor,
    List<ObjectStoreEvent> events
) {

    public record Id(String value) {
        public Id { Objects.requireNonNull(value); }
        public static Id generate() { return new Id(UUID.randomUUID().toString()); }
        public static Id of(String value) { return new Id(value); }
    }

    /**
     * Compact constructor with validation.
     */
    public S3Object {
        Objects.requireNonNull(id);
        Objects.requireNonNull(bucketId);
        Objects.requireNonNull(key);
        Objects.requireNonNull(lastModified);
        Objects.requireNonNull(metadata);
        Objects.requireNonNull(events);
        if (size < 0) throw new IllegalArgumentException("size must be non-negative");
    }

    public boolean hasContentDescriptor() { return contentDescriptor != null; }

    public boolean hasEtag() { return etag != null; }
    public boolean hasStorageClass() { return storageClass != null; }
    public boolean hasContentType() { return contentType != null; }
    public boolean hasContentDisposition() { return contentDisposition != null; }
    public boolean hasContentEncoding() { return contentEncoding != null; }
    public boolean hasMetadata() { return !metadata.isEmpty(); }

    /**
     * Factory method — create a new S3Object from a PutObject request.
     * Records an {@link ObjectStoreEvent.ObjectCreated} event.
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
        var now = Instant.now();
        var events = List.<ObjectStoreEvent>of(
            new ObjectStoreEvent.ObjectCreated(id, bucketId, key, now)
        );
        return new S3Object(id, bucketId, key, null, size, null, now,
            contentType, contentDisposition, contentEncoding, meta, null, events);
    }

    /**
     * Factory method for loading from persistence (already validated data, no events).
     */
    public static S3Object restore(Id id, Bucket.Id bucketId, ObjectKey key,
                                   String etag, long size, String storageClass,
                                   Instant lastModified, String contentType,
                                   String contentDisposition, String contentEncoding,
                                   Map<String, String> metadata,
                                   ContentDescriptor contentDescriptor) {
        Objects.requireNonNull(id);
        Objects.requireNonNull(bucketId);
        Objects.requireNonNull(key);
        Objects.requireNonNull(lastModified);
        var meta = metadata != null
            ? Map.<String, String>copyOf(metadata)
            : Map.<String, String>of();
        return new S3Object(id, bucketId, key, etag, size, storageClass,
            lastModified, contentType, contentDisposition, contentEncoding, meta,
            contentDescriptor, List.of());
    }

    /**
     * Update the ETag. Returns new instance with an {@link ObjectStoreEvent.ObjectEtagUpdated} event.
     */
    public S3Object withEtag(String etag) {
        var newEvents = appendEvent(
            new ObjectStoreEvent.ObjectEtagUpdated(id, etag, Instant.now())
        );
        return new S3Object(id, bucketId, key, etag, size, storageClass,
            lastModified, contentType, contentDisposition, contentEncoding, metadata,
            contentDescriptor, newEvents);
    }

    /**
     * Update the storage class. Returns new instance with an {@link ObjectStoreEvent.ObjectStoreStorageClassChanged} event.
     */
    public S3Object withStorageClass(String storageClass) {
        var newEvents = appendEvent(
            new ObjectStoreEvent.ObjectStoreStorageClassChanged(id, storageClass, Instant.now())
        );
        return new S3Object(id, bucketId, key, etag, size, storageClass,
            lastModified, contentType, contentDisposition, contentEncoding, metadata,
            contentDescriptor, newEvents);
    }

    /**
     * Assigns a {@link ContentDescriptor} to this object.
     * Returns a new instance with a {@link ObjectStoreEvent.ContentDescriptorCreated} event.
     *
     * @param descriptor the content descriptor (size, md5Hash, contentId)
     * @return new S3Object with the content descriptor attached
     */
    public S3Object withContent(ContentDescriptor descriptor) {
        Objects.requireNonNull(descriptor);
        var newEvents = appendEvent(
            new ObjectStoreEvent.ContentDescriptorCreated(id, descriptor, Instant.now())
        );
        return new S3Object(id, bucketId, key, etag, descriptor.size(), storageClass,
            lastModified, contentType, contentDisposition, contentEncoding, metadata,
            descriptor, newEvents);
    }

    /**
     * Returns the {@link ContentDescriptor} of this object, or {@code null} if not yet assigned.
     */
    public ContentDescriptor content() {
        return contentDescriptor;
    }

    /**
     * Returns the accumulated domain events since the last {@link #clearEvents()}.
     */
    public List<ObjectStoreEvent> domainEvents() {
        return events;
    }

    /**
     * Returns a new S3Object with all events cleared — suitable for persistence.
     */
    public S3Object clearEvents() {
        return new S3Object(id, bucketId, key, etag, size, storageClass,
            lastModified, contentType, contentDisposition, contentEncoding, metadata,
            contentDescriptor, List.of());
    }

    private List<ObjectStoreEvent> appendEvent(ObjectStoreEvent event) {
        var newEvents = new ArrayList<ObjectStoreEvent>(events.size() + 1);
        newEvents.addAll(events);
        newEvents.add(event);
        return Collections.unmodifiableList(newEvents);
    }
}
