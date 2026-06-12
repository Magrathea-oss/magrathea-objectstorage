package com.example.magrathea.storageengine.domain.service;

import com.example.magrathea.storageengine.domain.valueobject.CompressionConfig;
import com.example.magrathea.storageengine.domain.valueobject.DedupConfig;
import com.example.magrathea.storageengine.domain.valueobject.EffectiveStoragePolicy;
import com.example.magrathea.storageengine.domain.valueobject.EncryptionMode;
import com.example.magrathea.storageengine.domain.valueobject.EncryptionPolicy;
import com.example.magrathea.storageengine.domain.valueobject.ErasureCodingConfig;
import com.example.magrathea.storageengine.domain.valueobject.KeyReference;
import com.example.magrathea.storageengine.domain.valueobject.ReplicationConfig;
import com.example.magrathea.storageengine.domain.valueobject.StoragePolicy;
import com.example.magrathea.storageengine.domain.valueobject.UploadRequestContext;

import java.util.Optional;
import java.util.Set;

/**
 * Pure domain service — resolves a StoragePolicy into an EffectiveStoragePolicy
 * by applying upload request context overrides and bypass rules.
 * No framework dependencies, no I/O, no reactive types.
 */
public class EffectivePolicyResolver {

    /**
     * MIME types that are already compressed; compression is bypassed for these.
     */
    public static final Set<String> COMPRESSED_MIME_TYPES = Set.of(
            "application/gzip",
            "application/x-gzip",
            "application/x-compress",
            "application/x-bzip2",
            "application/xz",
            "application/zstd",
            "application/x-lz4",
            "application/x-lzma",
            "application/x-xz",
            "image/jpeg",
            "image/png",
            "image/webp",
            "image/gif",
            "video/mp4",
            "video/webm",
            "audio/mp3",
            "audio/aac",
            "audio/ogg",
            "audio/flac"
    );

    /**
     * Fallback minimum object size for dedup bypass when the policy's DedupConfig
     * does not provide an explicit chunk size. Prefer using {@code dedupConfig.chunkSize()}
     * when the DedupConfig is present.
     *
     * @deprecated Use the DedupConfig chunk size from the StoragePolicy instead.
     *             This constant is retained only for backward compatibility with tests
     *             that reference it directly.
     */
    @Deprecated
    public static final long DEDUP_CHUNK_SIZE = 64 * 1024;

    /**
     * Resolves a StoragePolicy into an EffectiveStoragePolicy, applying
     * request context overrides and bypass rules.
     *
     * @param policy  the base storage policy
     * @param context the upload request context
     * @return resolved effective storage policy
     */
    public EffectiveStoragePolicy resolve(StoragePolicy policy, UploadRequestContext context) {
        // 1. Dedup bypass: skip dedup for objects smaller than one chunk.
        // The threshold is driven by the policy's DedupConfig.chunkSize(), so the
        // bypass decision is policy-driven, not hard-coded.
        Optional<DedupConfig> dedup = policy.dedup();
        if (dedup.isPresent()) {
            long objectSize = context.contentDescriptor().objectSize();
            long chunkSizeThreshold = dedup.get().chunkSize();
            if (objectSize < chunkSizeThreshold) {
                dedup = Optional.empty(); // bypass dedup: object smaller than one chunk
            }
        }

        // 2. Compression bypass: skip compression for already-compressed MIME types
        Optional<CompressionConfig> compression = policy.compression();
        if (compression.isPresent()) {
            String mimeType = context.contentDescriptor().mimeType().toLowerCase();
            if (COMPRESSED_MIME_TYPES.contains(mimeType)) {
                compression = Optional.empty(); // bypass compression
            }
        }

        // 3. Encryption: resolve from request vs policy
        Optional<EncryptionPolicy> encryption = policy.encryption();
        EncryptionMode requestMode = context.encryptionRequest().mode();

        if (requestMode == EncryptionMode.NONE) {
            encryption = Optional.empty(); // no encryption requested
        } else if (requestMode == EncryptionMode.SSE_C) {
            // For SSE-C, derive encryption config from request
            KeyReference keyRef = context.encryptionRequest().keyReference()
                    .orElseThrow(() -> new IllegalArgumentException(
                            "SSE-C requires a key reference"));
            encryption = Optional.of(EncryptionPolicy.of(
                    com.example.magrathea.storageengine.domain.valueobject.EncryptionAlgorithm.SSE_S3,
                    Optional.of(keyRef)));
        } else if (requestMode == EncryptionMode.SSE_S3) {
            encryption = Optional.of(EncryptionPolicy.of(
                    com.example.magrathea.storageengine.domain.valueobject.EncryptionAlgorithm.SSE_S3,
                    Optional.empty()));
        } else if (requestMode == EncryptionMode.SSE_KMS) {
            KeyReference keyRef = context.encryptionRequest().keyReference()
                    .orElseThrow(() -> new IllegalArgumentException(
                            "SSE-KMS requires a key reference"));
            encryption = Optional.of(EncryptionPolicy.of(
                    com.example.magrathea.storageengine.domain.valueobject.EncryptionAlgorithm.SSE_KMS,
                    Optional.of(keyRef)));
        }

        // 4. Erasure coding: keep as-is from policy
        Optional<ErasureCodingConfig> erasureCoding = policy.erasureCoding();

        // 5. Replication: keep as-is from policy
        ReplicationConfig replication = policy.replication();

        return EffectiveStoragePolicy.of(
                policy.id(),
                context.bucket(),
                dedup,
                compression,
                encryption,
                erasureCoding,
                replication);
    }
}
