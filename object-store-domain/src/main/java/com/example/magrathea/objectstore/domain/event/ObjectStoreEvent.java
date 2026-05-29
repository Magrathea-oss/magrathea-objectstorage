package com.example.magrathea.objectstore.domain.event;

import com.example.magrathea.objectstore.domain.aggregate.Bucket;
import com.example.magrathea.objectstore.domain.aggregate.MultipartUpload;
import com.example.magrathea.objectstore.domain.aggregate.S3Object;
import com.example.magrathea.objectstore.domain.valueobject.AbacConfiguration;
import com.example.magrathea.objectstore.domain.valueobject.BucketAccelerateConfiguration;
import com.example.magrathea.objectstore.domain.valueobject.BucketAnalyticsConfiguration;
import com.example.magrathea.objectstore.domain.valueobject.BucketConfig;
import com.example.magrathea.objectstore.domain.valueobject.BucketEncryptionConfiguration;
import com.example.magrathea.objectstore.domain.valueobject.BucketIntelligentTieringConfiguration;
import com.example.magrathea.objectstore.domain.valueobject.BucketInventoryConfiguration;
import com.example.magrathea.objectstore.domain.valueobject.BucketLifecycleConfiguration;
import com.example.magrathea.objectstore.domain.valueobject.BucketLoggingConfiguration;
import com.example.magrathea.objectstore.domain.valueobject.BucketMetadataConfiguration;
import com.example.magrathea.objectstore.domain.valueobject.BucketMetadataTableConfiguration;
import com.example.magrathea.objectstore.domain.valueobject.BucketMetricsConfiguration;
import com.example.magrathea.objectstore.domain.valueobject.BucketNotificationConfiguration;
import com.example.magrathea.objectstore.domain.valueobject.BucketOwnershipControls;
import com.example.magrathea.objectstore.domain.valueobject.BucketReplicationConfiguration;
import com.example.magrathea.objectstore.domain.valueobject.BucketRequestPaymentConfiguration;
import com.example.magrathea.objectstore.domain.valueobject.BucketWebsiteConfiguration;
import com.example.magrathea.objectstore.domain.valueobject.ContentDescriptor;
import com.example.magrathea.objectstore.domain.valueobject.CorsConfiguration;
import com.example.magrathea.objectstore.domain.valueobject.ObjectKey;
import com.example.magrathea.objectstore.domain.valueobject.ObjectLockConfiguration;
import com.example.magrathea.objectstore.domain.valueobject.PublicAccessBlockConfiguration;
import com.example.magrathea.objectstore.domain.valueobject.RestoreConfiguration;
import com.example.magrathea.objectstore.domain.valueobject.EncryptionConfiguration;
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

    // ── Object archive ──
    record ObjectArchived(S3Object.Id id, Bucket.Id bucketId, String storageClass, Instant occurredOn) implements ObjectStoreEvent {}

    // ── Object restore ──
    record ObjectRestored(S3Object.Id id, Bucket.Id bucketId, RestoreConfiguration.RestoreTier tier, Instant occurredOn) implements ObjectStoreEvent {}

    // ── Legal hold ──
    record LegalHoldApplied(S3Object.Id id, Instant occurredOn) implements ObjectStoreEvent {}
    record LegalHoldRemoved(S3Object.Id id, Instant occurredOn) implements ObjectStoreEvent {}

    // ── Object lock ──
    record ObjectLockConfigured(S3Object.Id id, ObjectLockConfiguration.ObjectLockMode mode, Duration retentionDuration, Instant occurredOn) implements ObjectStoreEvent {}

    // ── Retention ──
    record ObjectRetentionSet(S3Object.Id id, Instant expirationAt, Instant occurredOn) implements ObjectStoreEvent {}

    // ── Encryption update ──
    record ObjectEncryptionUpdated(S3Object.Id id, EncryptionConfiguration encryption, Instant occurredOn) implements ObjectStoreEvent {}

    // ── Phase F: Rename ──
    record ObjectRenamed(S3Object.Id id, Bucket.Id bucketId, ObjectKey oldKey, ObjectKey newKey, Instant occurredOn) implements ObjectStoreEvent {}

    // ── Phase F: Session ──
    record SessionCreated(String sessionToken, Instant occurredOn) implements ObjectStoreEvent {}

    // ── Phase F: Bucket directory ──
    record DirectoryBucketConfigured(Bucket.Id id, Instant occurredOn) implements ObjectStoreEvent {}

    // ── Phase F: Bucket ABAC config ──
    record AbacConfigChanged(Bucket.Id id, AbacConfiguration config, Instant occurredOn) implements ObjectStoreEvent {}

    // ── Phase F: Bucket metadata config ──
    record MetadataConfigChanged(Bucket.Id id, BucketMetadataConfiguration config, Instant occurredOn) implements ObjectStoreEvent {}

    // ── Phase F: Bucket metadata table config ──
    record MetadataTableConfigChanged(Bucket.Id id, BucketMetadataTableConfiguration config, Instant occurredOn) implements ObjectStoreEvent {}

    // ── Bucket configuration events per config type ──
    record BucketCorsConfigured(Bucket.Id id, CorsConfiguration config, Instant occurredOn) implements ObjectStoreEvent {}
    record BucketCorsDeleted(Bucket.Id id, Instant occurredOn) implements ObjectStoreEvent {}

    record BucketLifecycleConfigured(Bucket.Id id, BucketLifecycleConfiguration config, Instant occurredOn) implements ObjectStoreEvent {}
    record BucketLifecycleDeleted(Bucket.Id id, Instant occurredOn) implements ObjectStoreEvent {}

    record BucketLoggingConfigured(Bucket.Id id, BucketLoggingConfiguration config, Instant occurredOn) implements ObjectStoreEvent {}
    record BucketLoggingDeleted(Bucket.Id id, Instant occurredOn) implements ObjectStoreEvent {}

    record BucketNotificationConfigured(Bucket.Id id, BucketNotificationConfiguration config, Instant occurredOn) implements ObjectStoreEvent {}
    record BucketNotificationDeleted(Bucket.Id id, Instant occurredOn) implements ObjectStoreEvent {}

    record BucketWebsiteConfigured(Bucket.Id id, BucketWebsiteConfiguration config, Instant occurredOn) implements ObjectStoreEvent {}
    record BucketWebsiteDeleted(Bucket.Id id, Instant occurredOn) implements ObjectStoreEvent {}

    record BucketPolicyConfigured(Bucket.Id id, String policy, Instant occurredOn) implements ObjectStoreEvent {}
    record BucketPolicyDeleted(Bucket.Id id, Instant occurredOn) implements ObjectStoreEvent {}

    record BucketEncryptionConfigured(Bucket.Id id, BucketEncryptionConfiguration config, Instant occurredOn) implements ObjectStoreEvent {}
    record BucketEncryptionDeleted(Bucket.Id id, Instant occurredOn) implements ObjectStoreEvent {}

    record BucketReplicationConfigured(Bucket.Id id, BucketReplicationConfiguration config, Instant occurredOn) implements ObjectStoreEvent {}
    record BucketReplicationDeleted(Bucket.Id id, Instant occurredOn) implements ObjectStoreEvent {}

    record BucketRequestPaymentConfigured(Bucket.Id id, BucketRequestPaymentConfiguration config, Instant occurredOn) implements ObjectStoreEvent {}
    record BucketRequestPaymentDeleted(Bucket.Id id, Instant occurredOn) implements ObjectStoreEvent {}

    record BucketOwnershipControlsConfigured(Bucket.Id id, BucketOwnershipControls config, Instant occurredOn) implements ObjectStoreEvent {}
    record BucketOwnershipControlsDeleted(Bucket.Id id, Instant occurredOn) implements ObjectStoreEvent {}

    record BucketPublicAccessBlockConfigured(Bucket.Id id, PublicAccessBlockConfiguration config, Instant occurredOn) implements ObjectStoreEvent {}
    record BucketPublicAccessBlockDeleted(Bucket.Id id, Instant occurredOn) implements ObjectStoreEvent {}

    record BucketAccelerateConfigured(Bucket.Id id, BucketAccelerateConfiguration config, Instant occurredOn) implements ObjectStoreEvent {}
    record BucketAccelerateDeleted(Bucket.Id id, Instant occurredOn) implements ObjectStoreEvent {}

    record BucketAnalyticsConfigured(Bucket.Id id, BucketAnalyticsConfiguration config, Instant occurredOn) implements ObjectStoreEvent {}
    record BucketAnalyticsDeleted(Bucket.Id id, Instant occurredOn) implements ObjectStoreEvent {}

    record BucketInventoryConfigured(Bucket.Id id, BucketInventoryConfiguration config, Instant occurredOn) implements ObjectStoreEvent {}
    record BucketInventoryDeleted(Bucket.Id id, Instant occurredOn) implements ObjectStoreEvent {}

    record BucketMetricsConfigured(Bucket.Id id, BucketMetricsConfiguration config, Instant occurredOn) implements ObjectStoreEvent {}
    record BucketMetricsDeleted(Bucket.Id id, Instant occurredOn) implements ObjectStoreEvent {}

    record BucketIntelligentTieringConfigured(Bucket.Id id, BucketIntelligentTieringConfiguration config, Instant occurredOn) implements ObjectStoreEvent {}
    record BucketIntelligentTieringDeleted(Bucket.Id id, Instant occurredOn) implements ObjectStoreEvent {}
}
