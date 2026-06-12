package com.example.magrathea.storageengine.domain.service;

import com.example.magrathea.storageengine.domain.TestFixtures;
import com.example.magrathea.storageengine.domain.valueobject.BucketRef;
import com.example.magrathea.storageengine.domain.valueobject.EffectiveStoragePolicy;
import com.example.magrathea.storageengine.domain.valueobject.EncryptionMode;
import com.example.magrathea.storageengine.domain.valueobject.StoragePolicy;
import com.example.magrathea.storageengine.domain.valueobject.UploadRequestContext;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

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
    void resolve_objectAtChunkThreshold_keepsDedup() {
        // Object size = exactly 64 KB = the dedup threshold: dedup must be kept
        long threshold = EffectivePolicyResolver.DEDUP_CHUNK_SIZE; // 64 * 1024 = 65536
        StoragePolicy policy = TestFixtures.aPolicyWithDedupOnly();
        UploadRequestContext context = TestFixtures.anUploadRequestContext(
                bucket, threshold, "application/octet-stream", EncryptionMode.NONE);

        EffectiveStoragePolicy effective = resolver.resolve(policy, context);

        assertTrue(effective.dedup().isPresent(),
                "Dedup must NOT be bypassed for objects at or above the " + threshold + "-byte threshold");
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
