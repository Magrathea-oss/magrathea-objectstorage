package com.example.magrathea.storageengine.domain.service;

import com.example.magrathea.storageengine.domain.TestFixtures;
import com.example.magrathea.storageengine.domain.valueobject.BucketRef;
import com.example.magrathea.storageengine.domain.valueobject.ChunkAlignment;
import com.example.magrathea.storageengine.domain.valueobject.DedupConfig;
import com.example.magrathea.storageengine.domain.valueobject.DedupScope;
import com.example.magrathea.storageengine.domain.valueobject.EffectiveStoragePolicy;
import com.example.magrathea.storageengine.domain.valueobject.EncryptionMode;
import com.example.magrathea.storageengine.domain.valueobject.FingerprintAlgorithm;
import com.example.magrathea.storageengine.domain.valueobject.ReplicationConfig;
import com.example.magrathea.storageengine.domain.valueobject.StorageClassId;
import com.example.magrathea.storageengine.domain.valueobject.StoragePolicy;
import com.example.magrathea.storageengine.domain.valueobject.UploadRequestContext;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Unit tests for {@link EffectivePolicyResolver}.
 * Pure JUnit 5 — no Spring, no Mockito, no reactive imports.
 */
class EffectivePolicyResolverTest {

    private EffectivePolicyResolver resolver;
    private BucketRef bucket;

    @BeforeEach
    void setUp() {
        resolver = new EffectivePolicyResolver();
        bucket = TestFixtures.aBucketRef();
    }

    // -------------------------------------------------------------------------
    // Dedup bypass: object smaller than DEDUP_CHUNK_SIZE (64 KB)
    // -------------------------------------------------------------------------

    @Test
    void resolve_objectSmallerThanChunkThreshold_bypassesDedup() {
        // Object size = 1024 bytes (< 64 KB) with a policy that has dedup enabled
        StoragePolicy policy = TestFixtures.aPolicyWithDedupOnly();
        UploadRequestContext context = TestFixtures.anUploadRequestContext(
                bucket, 1024L, "application/octet-stream", EncryptionMode.NONE);

        EffectiveStoragePolicy effective = resolver.resolve(policy, context);

        assertTrue(effective.dedup().isEmpty(),
                "Dedup must be bypassed for objects smaller than " + EffectivePolicyResolver.DEDUP_CHUNK_SIZE + " bytes");
    }

    @Test
    void resolve_objectAtChunkSize_keepsDedup() {
        // Object size = exactly the policy DedupConfig chunk size (1 MB by default).
        // The resolver uses dedupConfig.chunkSize() as the threshold, so an object of
        // exactly that size must NOT have dedup bypassed.
        long chunkSize = DedupConfig.DEFAULT_CHUNK_SIZE; // 1 MB = 1_048_576
        StoragePolicy policy = TestFixtures.aPolicyWithDedupOnly();
        UploadRequestContext context = TestFixtures.anUploadRequestContext(
                bucket, chunkSize, "application/octet-stream", EncryptionMode.NONE);

        EffectiveStoragePolicy effective = resolver.resolve(policy, context);

        assertTrue(effective.dedup().isPresent(),
                "Dedup must NOT be bypassed for objects at or above the policy chunk size (" + chunkSize + " bytes)");
    }

    @Test
    void resolve_usesChunkSizeFromPolicy_notHardCodedConstant() {
        // Use a small custom chunk size (32 KB) in the policy DedupConfig.
        // An object of 40 KB (> 32 KB chunk) must keep dedup.
        // An object of 16 KB (< 32 KB chunk) must bypass dedup.
        // This proves the resolver reads from the policy, not a hard-coded constant.
        long smallChunkSize = 32 * 1024L; // 32 KB
        DedupConfig smallChunkDedup = DedupConfig.of(
                DedupScope.BUCKET_LEVEL, FingerprintAlgorithm.SHA256, smallChunkSize, ChunkAlignment.NONE);
        StoragePolicy policyWithSmallChunk = StoragePolicy.of(
                StorageClassId.STANDARD,
                Optional.of(smallChunkDedup),
                Optional.empty(), Optional.empty(), Optional.empty(),
                ReplicationConfig.of(1));

        UploadRequestContext contextAbove = TestFixtures.anUploadRequestContext(
                bucket, 40 * 1024L, "application/octet-stream", EncryptionMode.NONE);
        UploadRequestContext contextBelow = TestFixtures.anUploadRequestContext(
                bucket, 16 * 1024L, "application/octet-stream", EncryptionMode.NONE);

        EffectiveStoragePolicy aboveThreshold = resolver.resolve(policyWithSmallChunk, contextAbove);
        EffectiveStoragePolicy belowThreshold = resolver.resolve(policyWithSmallChunk, contextBelow);

        assertTrue(aboveThreshold.dedup().isPresent(),
                "Object (40 KB) >= small chunk size (32 KB): dedup must be kept");
        assertTrue(belowThreshold.dedup().isEmpty(),
                "Object (16 KB) < small chunk size (32 KB): dedup must be bypassed");
    }

    // -------------------------------------------------------------------------
    // Compression bypass: already-compressed MIME types
    // -------------------------------------------------------------------------

    @Test
    void resolve_compressedMimeType_jpeg_bypassesCompression() {
        StoragePolicy policy = TestFixtures.aPolicyWithCompressionOnly();
        UploadRequestContext context = TestFixtures.anUploadRequestContext(
                bucket, 1_000_000L, "image/jpeg", EncryptionMode.NONE);

        EffectiveStoragePolicy effective = resolver.resolve(policy, context);

        assertTrue(effective.compression().isEmpty(),
                "Compression must be bypassed for 'image/jpeg' (already compressed format)");
    }

    @Test
    void resolve_compressedMimeType_gzip_bypassesCompression() {
        StoragePolicy policy = TestFixtures.aPolicyWithCompressionOnly();
        UploadRequestContext context = TestFixtures.anUploadRequestContext(
                bucket, 500_000L, "application/gzip", EncryptionMode.NONE);

        EffectiveStoragePolicy effective = resolver.resolve(policy, context);

        assertTrue(effective.compression().isEmpty(),
                "Compression must be bypassed for 'application/gzip' (already compressed format)");
    }

    @Test
    void resolve_normalMimeType_json_keepsCompression() {
        StoragePolicy policy = TestFixtures.aPolicyWithCompressionOnly();
        UploadRequestContext context = TestFixtures.anUploadRequestContext(
                bucket, 50_000L, "application/json", EncryptionMode.NONE);

        EffectiveStoragePolicy effective = resolver.resolve(policy, context);

        assertTrue(effective.compression().isPresent(),
                "Compression must be kept for 'application/json' (not a compressed format)");
    }

    // -------------------------------------------------------------------------
    // Encryption: mode-based override
    // -------------------------------------------------------------------------

    @Test
    void resolve_encryptionModeNone_removesEncryption() {
        StoragePolicy policy = TestFixtures.aPolicyWithEncryptionOnly();
        UploadRequestContext context = TestFixtures.anUploadRequestContext(
                bucket, 100_000L, "application/octet-stream", EncryptionMode.NONE);

        EffectiveStoragePolicy effective = resolver.resolve(policy, context);

        assertTrue(effective.encryption().isEmpty(),
                "Encryption must be removed when EncryptionMode.NONE is requested");
    }

    @Test
    void resolve_encryptionModeSseS3_setsEncryption() {
        // Start with a policy that has no encryption configured
        StoragePolicy policy = StoragePolicy.minimal(TestFixtures.aStorageClassId());
        UploadRequestContext context = TestFixtures.anUploadRequestContext(
                bucket, 100_000L, "application/octet-stream", EncryptionMode.SSE_S3);

        EffectiveStoragePolicy effective = resolver.resolve(policy, context);

        assertTrue(effective.encryption().isPresent(),
                "Encryption must be present when EncryptionMode.SSE_S3 is requested");
    }

    // -------------------------------------------------------------------------
    // No dedup / no compression in original policy
    // -------------------------------------------------------------------------

    @Test
    void resolve_noDedup_effectivePolicyHasNoDedup() {
        StoragePolicy policy = StoragePolicy.minimal(TestFixtures.aStorageClassId());
        UploadRequestContext context = TestFixtures.anUploadRequestContext(
                bucket, 1_000_000L, "application/octet-stream", EncryptionMode.NONE);

        EffectiveStoragePolicy effective = resolver.resolve(policy, context);

        assertTrue(effective.dedup().isEmpty(),
                "Effective policy must have no dedup when base policy has no dedup");
    }

    @Test
    void resolve_noCompression_effectivePolicyHasNoCompression() {
        StoragePolicy policy = StoragePolicy.minimal(TestFixtures.aStorageClassId());
        UploadRequestContext context = TestFixtures.anUploadRequestContext(
                bucket, 50_000L, "application/json", EncryptionMode.NONE);

        EffectiveStoragePolicy effective = resolver.resolve(policy, context);

        assertTrue(effective.compression().isEmpty(),
                "Effective policy must have no compression when base policy has no compression");
    }
}
