package com.example.magrathea.storageengine.domain;

import com.example.magrathea.storageengine.domain.valueobject.BucketId;
import com.example.magrathea.storageengine.domain.valueobject.BucketRef;
import com.example.magrathea.storageengine.domain.valueobject.ChunkAlignment;
import com.example.magrathea.storageengine.domain.valueobject.CompressionAlgorithm;
import com.example.magrathea.storageengine.domain.valueobject.CompressionConfig;
import com.example.magrathea.storageengine.domain.valueobject.DedupConfig;
import com.example.magrathea.storageengine.domain.valueobject.DedupScope;
import com.example.magrathea.storageengine.domain.valueobject.EffectiveStoragePolicy;
import com.example.magrathea.storageengine.domain.valueobject.EncryptionAlgorithm;
import com.example.magrathea.storageengine.domain.valueobject.EncryptionMode;
import com.example.magrathea.storageengine.domain.valueobject.EncryptionPolicy;
import com.example.magrathea.storageengine.domain.valueobject.EncryptionRequest;
import com.example.magrathea.storageengine.domain.valueobject.ErasureCodingConfig;
import com.example.magrathea.storageengine.domain.valueobject.FingerprintAlgorithm;
import com.example.magrathea.storageengine.domain.valueobject.ManifestId;
import com.example.magrathea.storageengine.domain.valueobject.ObjectContentDescriptor;
import com.example.magrathea.storageengine.domain.valueobject.ObjectId;
import com.example.magrathea.storageengine.domain.valueobject.ObjectKey;
import com.example.magrathea.storageengine.domain.valueobject.ObjectMetadataDescriptor;
import com.example.magrathea.storageengine.domain.valueobject.ReplicationConfig;
import com.example.magrathea.storageengine.domain.valueobject.StorageClassId;
import com.example.magrathea.storageengine.domain.valueobject.StoragePolicy;
import com.example.magrathea.storageengine.domain.valueobject.UploadRequestContext;
import com.example.magrathea.storageengine.domain.valueobject.VersionId;
import com.example.magrathea.storageengine.domain.valueobject.VirtualDevice;

import java.util.Optional;

/**
 * Static factory helpers shared across all domain test classes.
 * All methods return deterministic, well-known instances suitable for unit tests.
 */
public final class TestFixtures {

    private TestFixtures() {}

    // -------------------------------------------------------------------------
    // Identity value objects
    // -------------------------------------------------------------------------

    public static BucketId aBucketId() {
        return BucketId.of("bucket-test-id-1");
    }

    public static BucketRef aBucketRef() {
        return BucketRef.of(aBucketId(), "my-test-bucket");
    }

    public static BucketRef aBucketRefNamed(String name) {
        return BucketRef.of(BucketId.of("bucket-" + name), name);
    }

    public static ObjectId anObjectId() {
        return ObjectId.of("obj-00000001");
    }

    public static ObjectId anObjectId(String suffix) {
        return ObjectId.of("obj-" + suffix);
    }

    public static VersionId aVersionId() {
        return VersionId.of("v1");
    }

    public static VersionId aVersionId(String value) {
        return VersionId.of(value);
    }

    public static ManifestId aManifestId() {
        return ManifestId.generate();
    }

    public static StorageClassId aStorageClassId() {
        return StorageClassId.STANDARD;
    }

    // -------------------------------------------------------------------------
    // DedupConfig
    // -------------------------------------------------------------------------

    /** Bucket-level dedup with SHA256 and default chunk size (1 MB). */
    public static DedupConfig aBucketDedupConfig() {
        return DedupConfig.of(DedupScope.BUCKET_LEVEL, FingerprintAlgorithm.SHA256);
    }

    /** Global-level dedup with SHA256. */
    public static DedupConfig aGlobalDedupConfig() {
        return DedupConfig.of(DedupScope.GLOBAL_LEVEL, FingerprintAlgorithm.SHA256);
    }

    /** Dedup with custom chunk size and explicit alignment. */
    public static DedupConfig aDedupConfig(DedupScope scope, long chunkSize, ChunkAlignment alignment) {
        return DedupConfig.of(scope, FingerprintAlgorithm.SHA256, chunkSize, alignment);
    }

    // -------------------------------------------------------------------------
    // Other configs
    // -------------------------------------------------------------------------

    public static CompressionConfig aCompressionConfig() {
        return CompressionConfig.of(CompressionAlgorithm.ZSTD, 3);
    }

    public static EncryptionPolicy anEncryptionPolicy() {
        return EncryptionPolicy.of(EncryptionAlgorithm.SSE_S3, Optional.empty());
    }

    public static ErasureCodingConfig anErasureCodingConfig() {
        return ErasureCodingConfig.of(6, 3);
    }

    public static ReplicationConfig aReplicationConfig() {
        return ReplicationConfig.of(3);
    }

    // -------------------------------------------------------------------------
    // StoragePolicy
    // -------------------------------------------------------------------------

    /** Full policy with dedup, compression, encryption, EC and replication=3. */
    public static StoragePolicy aStoragePolicy() {
        return StoragePolicy.of(
                aStorageClassId(),
                Optional.of(aBucketDedupConfig()),
                Optional.of(aCompressionConfig()),
                Optional.of(anEncryptionPolicy()),
                Optional.of(anErasureCodingConfig()),
                aReplicationConfig());
    }

    /** Full policy with global-scope dedup, compression, encryption, EC and replication=3. */
    public static StoragePolicy aStoragePolicyWithGlobalDedup() {
        return StoragePolicy.of(
                aStorageClassId(),
                Optional.of(aGlobalDedupConfig()),
                Optional.of(aCompressionConfig()),
                Optional.of(anEncryptionPolicy()),
                Optional.of(anErasureCodingConfig()),
                aReplicationConfig());
    }

    /** Policy with dedup only, no compression/encryption/EC. */
    public static StoragePolicy aPolicyWithDedupOnly() {
        return StoragePolicy.of(
                aStorageClassId(),
                Optional.of(aBucketDedupConfig()),
                Optional.empty(),
                Optional.empty(),
                Optional.empty(),
                ReplicationConfig.of(1));
    }

    /** Policy with compression only (no dedup, encryption, EC). */
    public static StoragePolicy aPolicyWithCompressionOnly() {
        return StoragePolicy.of(
                aStorageClassId(),
                Optional.empty(),
                Optional.of(aCompressionConfig()),
                Optional.empty(),
                Optional.empty(),
                ReplicationConfig.of(1));
    }

    /** Policy with SSE-S3 encryption only (no dedup, compression, EC). */
    public static StoragePolicy aPolicyWithEncryptionOnly() {
        return StoragePolicy.of(
                aStorageClassId(),
                Optional.empty(),
                Optional.empty(),
                Optional.of(anEncryptionPolicy()),
                Optional.empty(),
                ReplicationConfig.of(1));
    }

    /** Policy with erasure coding only. */
    public static StoragePolicy aPolicyWithECOnly() {
        return StoragePolicy.of(
                aStorageClassId(),
                Optional.empty(),
                Optional.empty(),
                Optional.empty(),
                Optional.of(anErasureCodingConfig()),
                ReplicationConfig.of(1));
    }

    // -------------------------------------------------------------------------
    // EffectiveStoragePolicy
    // -------------------------------------------------------------------------

    /** Minimal effective policy — no dedup, compression, encryption, EC; replication=1. */
    public static EffectiveStoragePolicy aMinimalEffectivePolicy() {
        return aMinimalEffectivePolicy(aBucketRef());
    }

    public static EffectiveStoragePolicy aMinimalEffectivePolicy(BucketRef bucketRef) {
        return EffectiveStoragePolicy.of(
                aStorageClassId(),
                bucketRef,
                Optional.empty(),
                Optional.empty(),
                Optional.empty(),
                Optional.empty(),
                ReplicationConfig.of(1));
    }

    /** Full effective policy with all features enabled. */
    public static EffectiveStoragePolicy aFullEffectivePolicy(BucketRef bucketRef) {
        return EffectiveStoragePolicy.of(
                aStorageClassId(),
                bucketRef,
                Optional.of(aBucketDedupConfig()),
                Optional.of(aCompressionConfig()),
                Optional.of(anEncryptionPolicy()),
                Optional.of(anErasureCodingConfig()),
                aReplicationConfig());
    }

    /** Effective policy with global-scope dedup and no other features. */
    public static EffectiveStoragePolicy anEffectivePolicyWithGlobalDedup(BucketRef bucketRef) {
        return EffectiveStoragePolicy.of(
                aStorageClassId(),
                bucketRef,
                Optional.of(aGlobalDedupConfig()),
                Optional.empty(),
                Optional.empty(),
                Optional.empty(),
                ReplicationConfig.of(1));
    }

    /** Effective policy with bucket-scope dedup and no other features. */
    public static EffectiveStoragePolicy anEffectivePolicyWithBucketDedup(BucketRef bucketRef) {
        return EffectiveStoragePolicy.of(
                aStorageClassId(),
                bucketRef,
                Optional.of(aBucketDedupConfig()),
                Optional.empty(),
                Optional.empty(),
                Optional.empty(),
                ReplicationConfig.of(1));
    }

    /** Effective policy with EC only. */
    public static EffectiveStoragePolicy anEffectivePolicyWithEC(BucketRef bucketRef) {
        return EffectiveStoragePolicy.of(
                aStorageClassId(),
                bucketRef,
                Optional.empty(),
                Optional.empty(),
                Optional.empty(),
                Optional.of(anErasureCodingConfig()),
                ReplicationConfig.of(1));
    }

    // -------------------------------------------------------------------------
    // VirtualDevice
    // -------------------------------------------------------------------------

    /** Creates a BucketDevice backed by a minimal effective policy. */
    public static VirtualDevice aBucketDevice() {
        BucketRef bucketRef = aBucketRef();
        return new VirtualDevice.BucketDevice(bucketRef, aMinimalEffectivePolicy(bucketRef));
    }

    public static VirtualDevice aBucketDevice(BucketRef bucketRef) {
        return new VirtualDevice.BucketDevice(bucketRef, aMinimalEffectivePolicy(bucketRef));
    }

    // -------------------------------------------------------------------------
    // UploadRequestContext
    // -------------------------------------------------------------------------

    /**
     * Builds a minimal UploadRequestContext with the given parameters.
     *
     * @param bucketRef       the target bucket
     * @param objectSize      size of the object in bytes
     * @param mimeType        MIME type of the content
     * @param encryptionMode  requested encryption mode
     * @return fully constructed upload context
     */
    public static UploadRequestContext anUploadRequestContext(
            BucketRef bucketRef,
            long objectSize,
            String mimeType,
            EncryptionMode encryptionMode) {
        return UploadRequestContext.of(
                ObjectKey.of(bucketRef.bucketName(), "test-object/key"),
                bucketRef,
                StorageClassId.STANDARD,
                ObjectContentDescriptor.of(mimeType, objectSize),
                ObjectMetadataDescriptor.empty(),
                EncryptionRequest.of(encryptionMode, Optional.empty()),
                Optional.empty());
    }

    /** Upload context with application/octet-stream and NONE encryption. */
    public static UploadRequestContext aDefaultUploadRequestContext(BucketRef bucketRef, long objectSize) {
        return anUploadRequestContext(bucketRef, objectSize, "application/octet-stream", EncryptionMode.NONE);
    }
}
