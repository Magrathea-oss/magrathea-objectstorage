package com.example.magrathea.objectstorage.domain.event;

import com.example.magrathea.objectstorage.domain.aggregate.Bucket;
import com.example.magrathea.objectstorage.domain.aggregate.S3Object;
import com.example.magrathea.objectstorage.domain.valueobject.ObjectKey;

import java.time.Instant;

/**
 * Domain events for ObjectStorage bounded context.
 * Sealed interface — Java 17+ pattern: permits only known event types.
 */
public sealed interface ObjectStorageEvent {

    record BucketCreated(Bucket.Id id, String name, Instant occurredOn) implements ObjectStorageEvent {}
    record BucketDeleted(Bucket.Id id, Instant occurredOn) implements ObjectStorageEvent {}
    record ObjectCreated(S3Object.Id id, Bucket.Id bucketId, ObjectKey key, Instant occurredOn) implements ObjectStorageEvent {}
    record ObjectDeleted(S3Object.Id id, Bucket.Id bucketId, Instant occurredOn) implements ObjectStorageEvent {}
}
