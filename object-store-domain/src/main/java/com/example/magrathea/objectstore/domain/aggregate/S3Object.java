package com.example.magrathea.objectstore.domain.aggregate;

import com.example.magrathea.objectstore.domain.event.ObjectStoreEvent;
import com.example.magrathea.objectstore.domain.valueobject.ContentDescriptor;
import com.example.magrathea.objectstore.domain.valueobject.EncryptionConfiguration;
import com.example.magrathea.objectstore.domain.valueobject.ObjectKey;
import com.example.magrathea.objectstore.domain.valueobject.ObjectLockConfiguration;
import com.example.magrathea.objectstore.domain.valueobject.RestoreConfiguration;

import java.time.Instant;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;

/**
 * AWS S3 Object — aggregate root as a sealed state machine hierarchy.
 * <p>
 * States:
 * <ul>
 *   <li>{@code CreatingS3Object} — initial state, no content yet</li>
 *   <li>{@code ActiveS3Object} — has content, normal operations</li>
 *   <li>{@code LockedS3Object} — object lock applied</li>
 *   <li>{@code ArchivedS3Object} — archived to Glacier/Deep Archive</li>
 *   <li>{@code RestoredS3Object} — restored from archive</li>
 *   <li>{@code DeletedS3Object} — terminal state</li>
 * </ul>
 * </p>
 * Transitions produce new state instances with accumulated domain events.
 * Call {@link #domainEvents()} to retrieve events after a transition.
 * Call {@link #clearEvents()} to obtain a clean instance for persistence.
 * <p>
 * Uses ONLY AWS S3 native terminology per domain constraint.
 * Pure domain — NO framework dependencies.
 */
public sealed abstract class S3Object
    permits CreatingS3Object, ActiveS3Object, LockedS3Object,
            ArchivedS3Object, RestoredS3Object, DeletedS3Object {

    // ── Common fields ──
    private final Id id;
    private final Bucket.Id bucketId;
    private final ObjectKey key;
    private final String storageClass;
    private final Map<String, String> userMetadata;
    private final ContentDescriptor contentDescriptor;
    private final EncryptionConfiguration encryption;
    private final String etag;
    private final String versionId;
    private final List<ObjectStoreEvent> events;

    /**
     * Package-private constructor for subclasses.
     */
    S3Object(Id id, Bucket.Id bucketId, ObjectKey key, String storageClass,
             Map<String, String> userMetadata, ContentDescriptor contentDescriptor,
             EncryptionConfiguration encryption, String etag, String versionId,
             List<ObjectStoreEvent> events) {
        this.id = Objects.requireNonNull(id, "id must not be null");
        this.bucketId = Objects.requireNonNull(bucketId, "bucketId must not be null");
        this.key = Objects.requireNonNull(key, "key must not be null");
        this.storageClass = storageClass;
        this.userMetadata = Map.copyOf(Objects.requireNonNull(userMetadata, "userMetadata must not be null"));
        this.contentDescriptor = contentDescriptor;
        this.encryption = encryption;
        this.etag = etag;
        this.versionId = versionId;
        this.events = Objects.requireNonNull(events, "events must not be null");
    }

    // ── Getters ──

    /** Returns the object identifier. */
    public Id id() { return id; }

    /** Returns the bucket identifier. */
    public Bucket.Id bucketId() { return bucketId; }

    /** Returns the object key. */
    public ObjectKey key() { return key; }

    /** Returns the storage class, or {@code null} if not set. */
    public String storageClass() { return storageClass; }

    /** Returns the user metadata (x-amz-meta-* headers). */
    public Map<String, String> userMetadata() { return userMetadata; }

    /** Returns the content descriptor, or {@code null} if not yet attached. */
    public ContentDescriptor contentDescriptor() { return contentDescriptor; }

    /** Returns the encryption configuration, or {@code null} if not encrypted. */
    public EncryptionConfiguration encryption() { return encryption; }

    /** Returns the ETag, or {@code null} if not set. */
    public String etag() { return etag; }

    /** Returns the version ID, or {@code null} if versioning is not enabled. */
    public String versionId() { return versionId; }

    /** Returns {@code true} if a content descriptor is attached. */
    public boolean hasContentDescriptor() { return contentDescriptor != null; }

    /** Returns {@code true} if an ETag is set. */
    public boolean hasEtag() { return etag != null; }

    /** Returns {@code true} if a storage class is set. */
    public boolean hasStorageClass() { return storageClass != null; }

    /** Returns {@code true} if encryption is configured. */
    public boolean hasEncryption() { return encryption != null; }

    /** Returns the accumulated domain events since the last {@link #clearEvents()}. */
    public List<ObjectStoreEvent> domainEvents() { return events; }

    /**
     * Returns a new instance of the same state with all events cleared — suitable for persistence.
     */
    public abstract S3Object clearEvents();

    /**
     * Appends a domain event to the current events list.
     */
    protected final List<ObjectStoreEvent> appendEvent(List<ObjectStoreEvent> current, ObjectStoreEvent event) {
        var newEvents = new ArrayList<ObjectStoreEvent>(current.size() + 1);
        newEvents.addAll(current);
        newEvents.add(event);
        return Collections.unmodifiableList(newEvents);
    }

    // ── Nested Id record ──

    /**
     * Value object for the S3Object identifier.
     */
    public static final class Id {
        private final String value;

        public Id(String value) {
            this.value = Objects.requireNonNull(value, "value must not be null");
        }

        public String value() { return value; }

        public static Id generate() { return new Id(UUID.randomUUID().toString()); }

        public static Id of(String value) { return new Id(value); }

        @Override
        public boolean equals(Object o) {
            if (this == o) return true;
            if (!(o instanceof Id other)) return false;
            return value.equals(other.value);
        }

        @Override
        public int hashCode() { return value.hashCode(); }

        @Override
        public String toString() { return "Id[" + value + "]"; }
    }

    // ── Factory methods ──

    /**
     * Factory method — create a new S3Object in {@code CreatingS3Object} state.
     * Records an {@link ObjectStoreEvent.ObjectCreated} event.
     */
    public static CreatingS3Object create(Id id, Bucket.Id bucketId, ObjectKey key,
                                          String contentType, String contentDisposition,
                                          String contentEncoding, long size,
                                          Map<String, String> userMetadata,
                                          EncryptionConfiguration encryption) {
        Objects.requireNonNull(id, "id must not be null");
        Objects.requireNonNull(bucketId, "bucketId must not be null");
        Objects.requireNonNull(key, "key must not be null");
        if (size < 0) throw new IllegalArgumentException("size must be non-negative");
        var meta = userMetadata != null
            ? Map.<String, String>copyOf(userMetadata)
            : Map.<String, String>of();
        var now = Instant.now();
        var events = List.<ObjectStoreEvent>of(
            new ObjectStoreEvent.ObjectCreated(id, bucketId, key, now)
        );
        return new CreatingS3Object(id, bucketId, key, null, meta, null,
            encryption, null, null, events);
    }

    /**
     * Factory method — restore an {@code ActiveS3Object} from persistence.
     */
    public static ActiveS3Object restoreActive(Id id, Bucket.Id bucketId, ObjectKey key,
                                                String etag, String storageClass,
                                                Instant lastModified, String contentType,
                                                String contentDisposition, String contentEncoding,
                                                Map<String, String> userMetadata,
                                                ContentDescriptor contentDescriptor,
                                                EncryptionConfiguration encryption) {
        Objects.requireNonNull(id, "id must not be null");
        Objects.requireNonNull(bucketId, "bucketId must not be null");
        Objects.requireNonNull(key, "key must not be null");
        Objects.requireNonNull(lastModified, "lastModified must not be null");
        var meta = userMetadata != null
            ? Map.<String, String>copyOf(userMetadata)
            : Map.<String, String>of();
        return new ActiveS3Object(id, bucketId, key, storageClass, meta,
            contentDescriptor, encryption, etag, null, List.of());
    }

    /**
     * Factory method — restore a {@code LockedS3Object} from persistence.
     */
    public static LockedS3Object restoreLocked(Id id, Bucket.Id bucketId, ObjectKey key,
                                                String etag, String storageClass,
                                                Instant lastModified, String contentType,
                                                String contentDisposition, String contentEncoding,
                                                Map<String, String> userMetadata,
                                                ContentDescriptor contentDescriptor,
                                                EncryptionConfiguration encryption,
                                                ObjectLockConfiguration lockConfiguration) {
        Objects.requireNonNull(id, "id must not be null");
        Objects.requireNonNull(bucketId, "bucketId must not be null");
        Objects.requireNonNull(key, "key must not be null");
        Objects.requireNonNull(lastModified, "lastModified must not be null");
        var meta = userMetadata != null
            ? Map.<String, String>copyOf(userMetadata)
            : Map.<String, String>of();
        return new LockedS3Object(id, bucketId, key, storageClass, meta,
            contentDescriptor, encryption, etag, null, lockConfiguration, List.of());
    }

    /**
     * Factory method — restore an {@code ArchivedS3Object} from persistence.
     */
    public static ArchivedS3Object restoreArchived(Id id, Bucket.Id bucketId, ObjectKey key,
                                                    String etag, String storageClass,
                                                    Instant lastModified, String contentType,
                                                    String contentDisposition, String contentEncoding,
                                                    Map<String, String> userMetadata,
                                                    ContentDescriptor contentDescriptor,
                                                    EncryptionConfiguration encryption,
                                                    RestoreConfiguration restoreConfiguration) {
        Objects.requireNonNull(id, "id must not be null");
        Objects.requireNonNull(bucketId, "bucketId must not be null");
        Objects.requireNonNull(key, "key must not be null");
        Objects.requireNonNull(lastModified, "lastModified must not be null");
        var meta = userMetadata != null
            ? Map.<String, String>copyOf(userMetadata)
            : Map.<String, String>of();
        return new ArchivedS3Object(id, bucketId, key, storageClass, meta,
            contentDescriptor, encryption, etag, null, restoreConfiguration, List.of());
    }

    /**
     * Factory method — restore a {@code RestoredS3Object} from persistence.
     */
    public static RestoredS3Object restoreRestored(Id id, Bucket.Id bucketId, ObjectKey key,
                                                    String etag, String storageClass,
                                                    Instant lastModified, String contentType,
                                                    String contentDisposition, String contentEncoding,
                                                    Map<String, String> userMetadata,
                                                    ContentDescriptor contentDescriptor,
                                                    EncryptionConfiguration encryption,
                                                    RestoreConfiguration restoreConfiguration) {
        Objects.requireNonNull(id, "id must not be null");
        Objects.requireNonNull(bucketId, "bucketId must not be null");
        Objects.requireNonNull(key, "key must not be null");
        Objects.requireNonNull(lastModified, "lastModified must not be null");
        var meta = userMetadata != null
            ? Map.<String, String>copyOf(userMetadata)
            : Map.<String, String>of();
        return new RestoredS3Object(id, bucketId, key, storageClass, meta,
            contentDescriptor, encryption, etag, null, restoreConfiguration, List.of());
    }

    /**
     * Factory method — restore a {@code DeletedS3Object} from persistence.
     */
    public static DeletedS3Object restoreDeleted(Id id, Bucket.Id bucketId, ObjectKey key,
                                                  String storageClass,
                                                  Map<String, String> userMetadata) {
        Objects.requireNonNull(id, "id must not be null");
        Objects.requireNonNull(bucketId, "bucketId must not be null");
        Objects.requireNonNull(key, "key must not be null");
        var meta = userMetadata != null
            ? Map.<String, String>copyOf(userMetadata)
            : Map.<String, String>of();
        return new DeletedS3Object(id, bucketId, key, storageClass, meta, List.of());
    }

    // ── Convenience restore (detects state from data) ──

    /**
     * Convenience factory — detect state from provided fields and restore the appropriate subclass.
     * Used by legacy callers that do not know the exact state.
     */
    public static S3Object restore(Id id, Bucket.Id bucketId, ObjectKey key,
                                    String etag, long size, String storageClass,
                                    Instant lastModified, String contentType,
                                    String contentDisposition, String contentEncoding,
                                    Map<String, String> userMetadata,
                                    ContentDescriptor contentDescriptor) {
        // When contentDescriptor is present, return ActiveS3Object
        if (contentDescriptor != null) {
            return restoreActive(id, bucketId, key, etag, storageClass, lastModified,
                contentType, contentDisposition, contentEncoding, userMetadata,
                contentDescriptor, null);
        }
        // No content — return CreatingS3Object
        var meta = userMetadata != null
            ? Map.<String, String>copyOf(userMetadata)
            : Map.<String, String>of();
        return new CreatingS3Object(id, bucketId, key, storageClass, meta,
            null, null, etag, null, List.of());
    }

    /**
     * Convenience factory — restore with optional lock, restore, encryption.
     * Dispatches to the appropriate subclass based on provided fields.
     */
    public static S3Object restore(Id id, Bucket.Id bucketId, ObjectKey key,
                                    String etag, long size, String storageClass,
                                    Instant lastModified, String contentType,
                                    String contentDisposition, String contentEncoding,
                                    Map<String, String> userMetadata,
                                    ContentDescriptor contentDescriptor,
                                    ObjectLockConfiguration objectLockConfiguration,
                                    RestoreConfiguration restoreConfiguration,
                                    EncryptionConfiguration encryption) {
        Objects.requireNonNull(contentDescriptor, "contentDescriptor must not be null");
        var meta = userMetadata != null
            ? Map.<String, String>copyOf(userMetadata)
            : Map.<String, String>of();

        if (objectLockConfiguration != null) {
            return new LockedS3Object(id, bucketId, key, storageClass, meta,
                contentDescriptor, encryption, etag, null,
                objectLockConfiguration, List.of());
        }
        if (restoreConfiguration != null) {
            // If already restored, return RestoredS3Object
            return new RestoredS3Object(id, bucketId, key, storageClass, meta,
                contentDescriptor, encryption, etag, null,
                restoreConfiguration, List.of());
        }
        return new ActiveS3Object(id, bucketId, key, storageClass, meta,
            contentDescriptor, encryption, etag, null, List.of());
    }
}
