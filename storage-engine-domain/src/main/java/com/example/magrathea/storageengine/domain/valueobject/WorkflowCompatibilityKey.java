package com.example.magrathea.storageengine.domain.valueobject;

import java.util.Optional;

public record WorkflowCompatibilityKey(
        DedupNamespace namespace,
        long chunkSize,
        ChunkAlignment alignment,
        FingerprintAlgorithm fingerprintAlgorithm,
        Optional<CompressionConfig> compression,
        Optional<EncryptionConfig> encryption,
        Optional<ErasureCodingConfig> erasureCoding) {

    public WorkflowCompatibilityKey {
        java.util.Objects.requireNonNull(namespace, "namespace must not be null");
        java.util.Objects.requireNonNull(alignment, "alignment must not be null");
        java.util.Objects.requireNonNull(fingerprintAlgorithm, "fingerprintAlgorithm must not be null");
        java.util.Objects.requireNonNull(compression, "compression must not be null");
        java.util.Objects.requireNonNull(encryption, "encryption must not be null");
        java.util.Objects.requireNonNull(erasureCoding, "erasureCoding must not be null");
        if (chunkSize <= 0) throw new IllegalArgumentException("chunkSize must be positive: " + chunkSize);
    }

    public static WorkflowCompatibilityKey from(EffectiveStoragePolicy effectivePolicy) {
        DedupNamespace namespace;
        long chunkSize;
        ChunkAlignment alignment;
        if (effectivePolicy.dedup().isPresent()) {
            DedupConfig dedupConfig = effectivePolicy.dedup().get();
            if (dedupConfig.scope() == DedupScope.GLOBAL_LEVEL) {
                namespace = DedupNamespace.GlobalDedupNamespace.INSTANCE;
            } else {
                namespace = new DedupNamespace.BucketDedupNamespace(effectivePolicy.bucketRef());
            }
            chunkSize = dedupConfig.chunkSize();
            alignment = dedupConfig.alignment();
        } else {
            namespace = DedupNamespace.GlobalDedupNamespace.INSTANCE;
            chunkSize = 1048576L;
            alignment = ChunkAlignment.NONE;
        }

        FingerprintAlgorithm fingerprintAlgorithm = effectivePolicy.dedup()
                .map(DedupConfig::algorithm)
                .orElse(FingerprintAlgorithm.SHA256);

        Optional<CompressionConfig> compression = effectivePolicy.compression();
        Optional<EncryptionConfig> encryption = effectivePolicy.encryption().map(e ->
                EncryptionConfig.of(e.algorithm(), e.defaultKeyReference()));
        Optional<ErasureCodingConfig> erasureCoding = effectivePolicy.erasureCoding();

        return new WorkflowCompatibilityKey(
                namespace,
                chunkSize,
                alignment,
                fingerprintAlgorithm,
                compression,
                encryption,
                erasureCoding);
    }

    public DeviceConfigurationHash deriveDeviceHash() {
        String canonical = namespace.canonicalRepresentation()
                + ":" + chunkSize + ":" + alignment.name()
                + ":" + fingerprintAlgorithm.name()
                + ":" + compression.map(c -> c.algorithm().name() + ":" + c.level()).orElse("none")
                + ":" + encryption.map(e -> e.algorithm().name()).orElse("none")
                + ":" + erasureCoding.map(ec -> ec.dataBlocks() + ":" + ec.parityBlocks()).orElse("none");
        // Using hashCode as a simplified device configuration hash (real impl would use a cryptographic hash)
        int hash = canonical.hashCode();
        return DeviceConfigurationHash.of(String.format("%08x", hash));
    }
}
