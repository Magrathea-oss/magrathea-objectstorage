package com.example.magrathea.objectstore.domain.aggregate;

import com.example.magrathea.objectstore.domain.IllegalStateTransitionException;
import com.example.magrathea.objectstore.domain.event.ObjectStoreEvent;
import com.example.magrathea.objectstore.domain.valueobject.EncryptionConfiguration;
import com.example.magrathea.objectstore.domain.valueobject.ObjectChecksum;
import com.example.magrathea.objectstore.domain.valueobject.ObjectKey;
import com.example.magrathea.objectstore.domain.valueobject.ObjectLockConfiguration;

import java.time.ZonedDateTime;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * AWS S3 Object — aggregate root as a sealed state machine hierarchy.
 * <p>
 * States:
 * <ul>
 *   <li>{@code ActiveS3Object} — has content, normal operations</li>
 *   <li>{@code ArchivedS3Object} — archived to Glacier/Deep Archive</li>
 *   <li>{@code LockedS3Object} — object lock applied</li>
 *   <li>{@code DeletedS3Object} — terminal state</li>
 * </ul>
 * </p>
 * In addition to the sealed state hierarchy, each object carries a
 * {@link WriteState} for the write lifecycle (CREATED → WRITING → WRITTEN → DELETED).
 * <p>
 * Transitions produce new state instances with accumulated domain events.
 * Call {@link #domainEvents()} to retrieve events after a transition.
 * Call {@link #clearEvents()} to obtain a clean instance for persistence.
 * <p>
 * Uses ONLY AWS S3 native terminology per domain constraint.
 * Pure domain — NO framework dependencies.
 */
public sealed abstract class S3Object
    permits ActiveS3Object, ArchivedS3Object, LockedS3Object, DeletedS3Object {

    // ── Common fields ──
    private final ObjectKey key;
    private final String storageClass;
    private final Map<String, String> userMetadata;
    private final EncryptionConfiguration encryption;
    private final ObjectChecksum checksum;
    private final long size;
    private final ZonedDateTime createdAt;
    private final WriteState writeState;
    private final List<ObjectStoreEvent> events;

    /**
     * Package-private constructor for subclasses.
     */
    S3Object(ObjectKey key, String storageClass,
             Map<String, String> userMetadata, EncryptionConfiguration encryption,
             ObjectChecksum checksum, long size, ZonedDateTime createdAt,
             WriteState writeState, List<ObjectStoreEvent> events) {
        this.key = Objects.requireNonNull(key, "key must not be null");
        this.storageClass = storageClass;
        this.userMetadata = Map.copyOf(Objects.requireNonNull(userMetadata, "userMetadata must not be null"));
        this.encryption = encryption;
        this.checksum = checksum;
        this.size = size;
        this.createdAt = Objects.requireNonNull(createdAt, "createdAt must not be null");
        this.writeState = Objects.requireNonNull(writeState, "writeState must not be null");
        this.events = Objects.requireNonNull(events, "events must not be null");
    }

    // ── Getters ──

    /** Returns the object key (bucket/key composite). */
    public ObjectKey key() { return key; }

    /** Returns the storage class, or {@code null} if not set. */
    public String storageClass() { return storageClass; }

    /** Returns the user metadata (x-amz-meta-* headers). */
    public Map<String, String> userMetadata() { return userMetadata; }

    /** Returns the encryption configuration, or {@code null} if not encrypted. */
    public EncryptionConfiguration encryption() { return encryption; }

    /** Returns the checksum, or {@code null} if not yet attached. */
    public ObjectChecksum checksum() { return checksum; }

    /** Returns the content size in bytes. */
    public long size() { return size; }

    /** Returns the creation timestamp. */
    public ZonedDateTime createdAt() { return createdAt; }

    /** Returns the write lifecycle state. */
    public WriteState writeState() { return writeState; }

    /** Returns the accumulated domain events since the last {@link #clearEvents()}. */
    public List<ObjectStoreEvent> domainEvents() { return events; }

    /** Returns {@code true} if encryption is configured. */
    public boolean hasEncryption() { return encryption != null; }

    /** Returns {@code true} if a checksum is attached. */
    public boolean hasChecksum() { return checksum != null; }

    /** Returns {@code true} if a storage class is set. */
    public boolean hasStorageClass() { return storageClass != null; }

    /**
     * Returns a new instance of the same state with all events cleared — suitable for persistence.
     */
    public abstract S3Object clearEvents();

    /**
     * Returns a new instance of the same sealed subclass with the given write state.
     */
    protected abstract S3Object withWriteState(WriteState newState);

    /**
     * Appends a domain event to the current events list.
     */
    protected final List<ObjectStoreEvent> appendEvent(List<ObjectStoreEvent> current, ObjectStoreEvent event) {
        var newEvents = new ArrayList<ObjectStoreEvent>(current.size() + 1);
        newEvents.addAll(current);
        newEvents.add(event);
        return Collections.unmodifiableList(newEvents);
    }

    // ── Factory methods ──

    /**
     * Factory method — create a new S3Object in {@code ActiveS3Object} state with content fully written.
     * The {@code createdAt} is set internally to the current time.
     * Write state is {@link WriteState#WRITTEN}.
     * Records an {@link ObjectStoreEvent.ObjectCreated} event.
     */
    public static ActiveS3Object create(
            ObjectKey key,
            String storageClass,
            Map<String, String> userMetadata,
            EncryptionConfiguration encryption,
            ObjectChecksum checksum,
            long size) {
        Objects.requireNonNull(key, "key must not be null");
        Objects.requireNonNull(checksum, "checksum must not be null");
        if (size < 0) throw new IllegalArgumentException("size must be non-negative");
        var meta = userMetadata != null
            ? Map.<String, String>copyOf(userMetadata)
            : Map.<String, String>of();
        var now = ZonedDateTime.now();
        var events = List.<ObjectStoreEvent>of(
            new ObjectStoreEvent.ObjectCreated(key, now)
        );
        return new ActiveS3Object(key, storageClass, meta, encryption,
            checksum, size, now, WriteState.WRITTEN, events);
    }

    /**
     * Factory method — create a pending S3Object in {@code ActiveS3Object} state with no content yet.
     * The {@code createdAt} is set internally to the current time.
     * Write state is {@link WriteState#CREATED}.
     * No domain event is emitted (content not yet created).
     *
     * @param key            the object key
     * @param storageClass   the storage class (may be null)
     * @param userMetadata   user metadata (may be null)
     * @param encryption     encryption configuration (may be null)
     * @return a new {@code ActiveS3Object} in CREATED write state
     */
    public static ActiveS3Object createPending(
            ObjectKey key,
            String storageClass,
            Map<String, String> userMetadata,
            EncryptionConfiguration encryption) {
        Objects.requireNonNull(key, "key must not be null");
        var meta = userMetadata != null
            ? Map.<String, String>copyOf(userMetadata)
            : Map.<String, String>of();
        var now = ZonedDateTime.now();
        return new ActiveS3Object(key, storageClass, meta, encryption,
            null, 0L, now, WriteState.CREATED, List.of());
    }

    /**
     * Factory method — restore an {@code ActiveS3Object} from persistence (event sourcing).
     */
    public static ActiveS3Object restoreActive(
            ObjectKey key, String storageClass,
            Map<String, String> userMetadata, EncryptionConfiguration encryption,
            ObjectChecksum checksum, long size, ZonedDateTime createdAt,
            List<ObjectStoreEvent> events) {
        Objects.requireNonNull(key, "key must not be null");
        Objects.requireNonNull(createdAt, "createdAt must not be null");
        var meta = userMetadata != null
            ? Map.<String, String>copyOf(userMetadata)
            : Map.<String, String>of();
        var evts = events != null ? List.<ObjectStoreEvent>copyOf(events) : List.<ObjectStoreEvent>of();
        var writeState = checksum != null ? WriteState.WRITTEN : WriteState.CREATED;
        return new ActiveS3Object(key, storageClass, meta, encryption,
            checksum, size, createdAt, writeState, evts);
    }

    /**
     * Factory method — restore a {@code LockedS3Object} from persistence (event sourcing).
     */
    public static LockedS3Object restoreLocked(
            ObjectKey key, String storageClass,
            Map<String, String> userMetadata, EncryptionConfiguration encryption,
            ObjectChecksum checksum, long size, ZonedDateTime createdAt,
            ObjectLockConfiguration lockConfiguration,
            List<ObjectStoreEvent> events) {
        Objects.requireNonNull(key, "key must not be null");
        Objects.requireNonNull(createdAt, "createdAt must not be null");
        Objects.requireNonNull(lockConfiguration, "lockConfiguration must not be null");
        var meta = userMetadata != null
            ? Map.<String, String>copyOf(userMetadata)
            : Map.<String, String>of();
        var evts = events != null ? List.<ObjectStoreEvent>copyOf(events) : List.<ObjectStoreEvent>of();
        var writeState = WriteState.WRITTEN;
        return new LockedS3Object(key, storageClass, meta, encryption,
            checksum, size, createdAt, lockConfiguration, writeState, evts);
    }

    /**
     * Factory method — restore an {@code ArchivedS3Object} from persistence (event sourcing).
     */
    public static ArchivedS3Object restoreArchived(
            ObjectKey key, String storageClass,
            Map<String, String> userMetadata, EncryptionConfiguration encryption,
            ObjectChecksum checksum, long size, ZonedDateTime createdAt,
            boolean restored, ZonedDateTime restoreExpiry,
            List<ObjectStoreEvent> events) {
        Objects.requireNonNull(key, "key must not be null");
        Objects.requireNonNull(createdAt, "createdAt must not be null");
        var meta = userMetadata != null
            ? Map.<String, String>copyOf(userMetadata)
            : Map.<String, String>of();
        var evts = events != null ? List.<ObjectStoreEvent>copyOf(events) : List.<ObjectStoreEvent>of();
        var writeState = WriteState.WRITTEN;
        return new ArchivedS3Object(key, storageClass, meta, encryption,
            checksum, size, createdAt, restored, restoreExpiry, writeState, evts);
    }

    /**
     * Factory method — restore a {@code DeletedS3Object} from persistence (event sourcing).
     */
    public static DeletedS3Object restoreDeleted(
            ObjectKey key, String storageClass,
            Map<String, String> userMetadata,
            ZonedDateTime createdAt,
            List<ObjectStoreEvent> events) {
        Objects.requireNonNull(key, "key must not be null");
        Objects.requireNonNull(createdAt, "createdAt must not be null");
        var meta = userMetadata != null
            ? Map.<String, String>copyOf(userMetadata)
            : Map.<String, String>of();
        var evts = events != null ? List.<ObjectStoreEvent>copyOf(events) : List.<ObjectStoreEvent>of();
        return new DeletedS3Object(key, storageClass, meta, createdAt, WriteState.DELETED, evts);
    }

    // ── Write state machine transitions ──

    /**
     * Initiate upload — transition from {@link WriteState#CREATED} to {@link WriteState#WRITING}.
     *
     * @return a new instance of the same sealed subclass with write state {@code WRITING}
     * @throws IllegalStateTransitionException if current write state is not {@code CREATED}
     */
    public S3Object initiateUpload() {
        if (writeState != WriteState.CREATED) {
            throw new IllegalStateTransitionException(
                "Cannot initiate upload: current write state is " + writeState +
                ", expected CREATED");
        }
        return withWriteState(WriteState.WRITING);
    }

    /**
     * Complete upload — transition from {@link WriteState#WRITING} to {@link WriteState#WRITTEN}.
     * Sets the checksum and size for the completed content.
     *
     * @param checksum the final checksum of the uploaded content
     * @param size     the final size of the uploaded content
     * @return a new instance of the same sealed subclass with write state {@code WRITTEN}
     * @throws IllegalStateTransitionException if current write state is not {@code WRITING}
     */
    public S3Object completeUpload(ObjectChecksum checksum, long size) {
        if (writeState != WriteState.WRITING) {
            throw new IllegalStateTransitionException(
                "Cannot complete upload: current write state is " + writeState +
                ", expected WRITING");
        }
        Objects.requireNonNull(checksum, "checksum must not be null");
        if (size < 0) throw new IllegalArgumentException("size must be non-negative: " + size);
        // Record ObjectCreated event when content is fully written
        var newEvents = appendEvent(domainEvents(),
            new ObjectStoreEvent.ObjectCreated(key(), ZonedDateTime.now()));
        // Hook for subclasses to update checksum, size, and events
        return withWriteStateAndContent(checksum, size, newEvents);
    }

    /**
     * Hook for subclasses to update checksum, size, and events during completeUpload.
     * ActiveS3Object and LockedS3Object override this to carry checksum/size forward.
     */
    protected S3Object withWriteStateAndContent(ObjectChecksum checksum, long size, List<ObjectStoreEvent> events) {
        throw new UnsupportedOperationException(
            "Subclass " + getClass().getSimpleName() + " must override withWriteStateAndContent");
    }

    /**
     * Validates that the current write state allows deletion.
     *
     * @throws IllegalStateTransitionException if current write state is not {@code WRITTEN} or {@code CREATED}
     */
    protected void validateWriteStateForDelete() {
        if (writeState != WriteState.WRITTEN && writeState != WriteState.CREATED) {
            throw new IllegalStateTransitionException(
                "Cannot delete: current write state is " + writeState +
                ", expected WRITTEN or CREATED");
        }
    }
}
