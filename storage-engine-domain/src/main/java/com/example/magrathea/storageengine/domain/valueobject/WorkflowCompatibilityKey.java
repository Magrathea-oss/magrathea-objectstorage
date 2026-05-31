package com.example.magrathea.storageengine.domain.valueobject;

import java.util.Optional;

public record WorkflowCompatibilityKey(
        DedupNamespace namespace,
        ChunkingConfig chunking,
        FingerprintAlgorithm fingerprintAlgorithm,
        Optional<CompressionConfig> compression,
        Optional<EncryptionConfig> encryption,
        Optional<ErasureCodingConfig> erasureCoding) {

    public WorkflowCompatibilityKey {
        java.util.Objects.requireNonNull(namespace, "namespace must not be null");
        java.util.Objects.requireNonNull(chunking, "chunking must not be null");
        java.util.Objects.requireNonNull(fingerprintAlgorithm, "fingerprintAlgorithm must not be null");
        java.util.Objects.requireNonNull(compression, "compression must not be null");
        java.util.Objects.requireNonNull(encryption, "encryption must not be null");
        java.util.Objects.requireNonNull(erasureCoding, "erasureCoding must not be null");
    }

    public static WorkflowCompatibilityKey from(EffectiveStoragePolicy effectivePolicy) {
        DedupNamespace namespace;
        if (effectivePolicy.dedup().isPresent()) {
            DedupConfig dedupConfig = effectivePolicy.dedup().get();
            if (dedupConfig.scope() == DedupScope.GLOBAL_LEVEL) {
                namespace = DedupNamespace.GlobalDedupNamespace.INSTANCE;
            } else {
                namespace = new DedupNamespace.BucketDedupNamespace(effectivePolicy.bucketRef());
            }
        } else {
            namespace = DedupNamespace.GlobalDedupNamespace.INSTANCE;
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
                effectivePolicy.chunking(),
                fingerprintAlgorithm,
                compression,
                encryption,
                erasureCoding);
    }

    public DeviceConfigurationHash deriveDeviceHash() {
        String canonical = namespace.canonicalRepresentation()
                + ":" + chunking.chunkSize() + ":" + chunking.alignment().name()
                + ":" + fingerprintAlgorithm.name()
                + ":" + compression.map(c -> c.algorithm().name() + ":" + c.level()).orElse("none")
                + ":" + encryption.map(e -> e.algorithm().name()).orElse("none")
                + ":" + erasureCoding.map(ec -> ec.dataBlocks() + ":" + ec.parityBlocks()).orElse("none");
        // Using hashCode as a simplified device configuration hash (real impl would use a cryptographic hash)
        int hash = canonical.hashCode();
        return DeviceConfigurationHash.of(String.format("%08x", hash));
    }
}
