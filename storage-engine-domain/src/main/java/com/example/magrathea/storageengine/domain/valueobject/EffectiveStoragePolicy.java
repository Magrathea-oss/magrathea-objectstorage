package com.example.magrathea.storageengine.domain.valueobject;

import java.util.Optional;

/**
 * Resolved storage policy after applying upload request context overrides.
 * This is a pure domain value object — no framework dependencies.
 */
public record EffectiveStoragePolicy(
        StorageClassId storageClassId,
        BucketRef bucketRef,
        Optional<DedupConfig> dedup,
        Optional<CompressionConfig> compression,
        Optional<EncryptionPolicy> encryption,
        Optional<ErasureCodingConfig> erasureCoding,
        ReplicationConfig replication) {

    public EffectiveStoragePolicy {
        java.util.Objects.requireNonNull(storageClassId, "storageClassId must not be null");
        java.util.Objects.requireNonNull(bucketRef, "bucketRef must not be null");
        java.util.Objects.requireNonNull(dedup, "dedup must not be null");
        java.util.Objects.requireNonNull(compression, "compression must not be null");
        java.util.Objects.requireNonNull(encryption, "encryption must not be null");
        java.util.Objects.requireNonNull(erasureCoding, "erasureCoding must not be null");
        java.util.Objects.requireNonNull(replication, "replication must not be null");
    }

    public static EffectiveStoragePolicy of(
            StorageClassId storageClassId,
            BucketRef bucketRef,
            Optional<DedupConfig> dedup,
            Optional<CompressionConfig> compression,
            Optional<EncryptionPolicy> encryption,
            Optional<ErasureCodingConfig> erasureCoding,
            ReplicationConfig replication) {
        return new EffectiveStoragePolicy(
                storageClassId, bucketRef, dedup, compression, encryption, erasureCoding, replication);
    }
}
