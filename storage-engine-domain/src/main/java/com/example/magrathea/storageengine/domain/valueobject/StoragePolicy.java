package com.example.magrathea.storageengine.domain.valueobject;

import java.util.Optional;

public record StoragePolicy(
        StorageClassId id,
        Optional<DedupConfig> dedup,
        Optional<CompressionConfig> compression,
        Optional<EncryptionPolicy> encryption,
        Optional<ErasureCodingConfig> erasureCoding,
        ReplicationConfig replication) {

    public StoragePolicy {
        java.util.Objects.requireNonNull(id, "id must not be null");
        java.util.Objects.requireNonNull(dedup, "dedup must not be null");
        java.util.Objects.requireNonNull(compression, "compression must not be null");
        java.util.Objects.requireNonNull(encryption, "encryption must not be null");
        java.util.Objects.requireNonNull(erasureCoding, "erasureCoding must not be null");
        java.util.Objects.requireNonNull(replication, "replication must not be null");
    }

    /**
     * Creates a minimal policy with no dedup, no compression, no encryption,
     * no erasure coding, and replication factor 1.
     * Useful for tests and simple single-node topologies.
     */
    public static StoragePolicy minimal(StorageClassId id) {
        return new StoragePolicy(
                id,
                Optional.empty(),
                Optional.empty(),
                Optional.empty(),
                Optional.empty(),
                ReplicationConfig.of(1));
    }

    public static StoragePolicy of(
            StorageClassId id,
            Optional<DedupConfig> dedup,
            Optional<CompressionConfig> compression,
            Optional<EncryptionPolicy> encryption,
            Optional<ErasureCodingConfig> erasureCoding,
            ReplicationConfig replication) {
        return new StoragePolicy(id, dedup, compression, encryption, erasureCoding, replication);
    }
}
