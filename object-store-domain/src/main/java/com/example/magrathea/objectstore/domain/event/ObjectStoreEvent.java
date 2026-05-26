package com.example.magrathea.objectstore.domain.event;

import com.example.magrathea.objectstore.domain.aggregate.Bucket;
import com.example.magrathea.objectstore.domain.aggregate.MultipartUpload;
import com.example.magrathea.objectstore.domain.aggregate.S3Object;
import com.example.magrathea.objectstore.domain.valueobject.BucketConfig;
import com.example.magrathea.objectstore.domain.valueobject.ContentDescriptor;
import com.example.magrathea.objectstore.domain.valueobject.ObjectKey;
import com.example.magrathea.objectstore.domain.valueobject.UploadId;

import java.time.Duration;
import java.time.Instant;

/**
 * Domain events for ObjectStore bounded context.
 * Sealed interface — Java 17+ pattern: permits only known event types.
 */
public sealed interface ObjectStoreEvent {

    record BucketCreated(Bucket.Id id, String name, Instant occurredOn) implements ObjectStoreEvent {}
    record BucketDeleted(Bucket.Id id, Instant occurredOn) implements ObjectStoreEvent {}
    record ObjectCreated(S3Object.Id id, Bucket.Id bucketId, ObjectKey key, Instant occurredOn) implements ObjectStoreEvent {}
    record ObjectDeleted(S3Object.Id id, Bucket.Id bucketId, Instant occurredOn) implements ObjectStoreEvent {}

    record MultipartUploadCreated(MultipartUpload.Id id, UploadId uploadId, Bucket.Id bucketId, ObjectKey key, Instant occurredOn) implements ObjectStoreEvent {}

    // ── Bucket state transitions ──
    record BucketVersioningEnabled(Bucket.Id id, Instant occurredOn) implements ObjectStoreEvent {}
    record BucketVersioningSuspended(Bucket.Id id, Instant occurredOn) implements ObjectStoreEvent {}
    record BucketEncryptionEnabled(Bucket.Id id, Instant occurredOn) implements ObjectStoreEvent {}
    record BucketConfigChanged(Bucket.Id id, BucketConfig config, Instant occurredOn) implements ObjectStoreEvent {}

    // ── Object state transitions ──
    record ObjectEtagUpdated(S3Object.Id id, String etag, Instant occurredOn) implements ObjectStoreEvent {}
    record ObjectStoreStorageClassChanged(S3Object.Id id, String storageClass, Instant occurredOn) implements ObjectStoreEvent {}
    record ContentDescriptorCreated(S3Object.Id id, ContentDescriptor descriptor, Instant occurredOn) implements ObjectStoreEvent {}
    record PartUploaded(MultipartUpload.Id id, UploadId uploadId, int partNumber, String etag, Instant occurredOn) implements ObjectStoreEvent {}
    record MultipartUploadCompleted(MultipartUpload.Id id, UploadId uploadId, Bucket.Id bucketId, ObjectKey key, Instant occurredOn) implements ObjectStoreEvent {}
    record MultipartUploadAborted(MultipartUpload.Id id, UploadId uploadId, Instant occurredOn) implements ObjectStoreEvent {}

    // ── Object restore ──
    record ObjectRestored(S3Object.Id id, Bucket.Id bucketId, RestoreTier tier, Instant occurredOn) implements ObjectStoreEvent {}

    // ── Legal hold ──
    record LegalHoldApplied(S3Object.Id id, Instant occurredOn) implements ObjectStoreEvent {}
    record LegalHoldRemoved(S3Object.Id id, Instant occurredOn) implements ObjectStoreEvent {}

    // ── Object lock ──
    record ObjectLockConfigured(S3Object.Id id, ObjectLockMode mode, Duration retentionDuration, Instant occurredOn) implements ObjectStoreEvent {}

    // ── Retention ──
    record ObjectRetentionSet(S3Object.Id id, Instant expirationAt, Instant occurredOn) implements ObjectStoreEvent {}

    // ── Restore tier enum ──
    enum RestoreTier { STANDARD, BULK, EXPEDITED }

    // ── Lock mode enum ──
    enum ObjectLockMode { GOVERNANCE, COMPLIANCE }
}
