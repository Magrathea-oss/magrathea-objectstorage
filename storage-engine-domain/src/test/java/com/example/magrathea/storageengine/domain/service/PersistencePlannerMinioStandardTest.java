package com.example.magrathea.storageengine.domain.service;

import com.example.magrathea.storageengine.domain.TestFixtures;
import com.example.magrathea.storageengine.domain.valueobject.BucketRef;
import com.example.magrathea.storageengine.domain.valueobject.ChunkAlignment;
import com.example.magrathea.storageengine.domain.valueobject.DedupConfig;
import com.example.magrathea.storageengine.domain.valueobject.DedupScope;
import com.example.magrathea.storageengine.domain.valueobject.EffectiveStoragePolicy;
import com.example.magrathea.storageengine.domain.valueobject.EncryptionMode;
import com.example.magrathea.storageengine.domain.valueobject.FingerprintAlgorithm;
import com.example.magrathea.storageengine.domain.valueobject.PersistencePlan;
import com.example.magrathea.storageengine.domain.valueobject.ReplicationConfig;
import com.example.magrathea.storageengine.domain.valueobject.StorageClassId;
import com.example.magrathea.storageengine.domain.valueobject.StoragePolicy;
import com.example.magrathea.storageengine.domain.valueobject.StepConfig;
import com.example.magrathea.storageengine.domain.valueobject.StepExecutionStatus;
import com.example.magrathea.storageengine.domain.valueobject.StepId;
import com.example.magrathea.storageengine.domain.valueobject.StepPlan;
import com.example.magrathea.storageengine.domain.valueobject.UploadRequestContext;
import com.example.magrathea.storageengine.domain.valueobject.VirtualDevice;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Domain-level verification of MINIO_STANDARD policy planning behaviour.
 *
 * <p>These tests exercise the pure domain pipeline:
 * <pre>
 *   StoragePolicy (MINIO_STANDARD params)
 *     → EffectivePolicyResolver.resolve()
 *     → EffectiveStoragePolicy
 *     → VirtualDeviceResolver.resolve()
 *     → VirtualDevice
 *     → PersistencePlanner.createPlan()
 *     → PersistencePlan
 * </pre>
 *
 * <p>The MINIO_STANDARD policy is:
 * <ul>
 *   <li>Dedup enabled — BUCKET scope, SHA-256 fingerprint, 1 MiB chunk size, NONE alignment</li>
 *   <li>Compression disabled</li>
 *   <li>Encryption disabled</li>
 *   <li>Erasure coding disabled</li>
 *   <li>Replication factor 1 (single-node, single replica)</li>
 *   <li>Storage class: {@code STANDARD}</li>
 * </ul>
 *
 * <p>These domain parameters define the domain MINIO_STANDARD-equivalent plan shape.
 * Pure JUnit 5 — no Spring, no Mockito, no reactive imports.
 */
class PersistencePlannerMinioStandardTest {

    /** 1 MiB — the policy-driven chunk size for the MINIO_STANDARD-equivalent plan. */
    private static final long MINIO_STANDARD_CHUNK_SIZE = 1_048_576L;

    private EffectivePolicyResolver resolver;
    private PersistencePlanner planner;
    private VirtualDeviceResolver deviceResolver;
    private BucketRef bucket;

    @BeforeEach
    void setUp() {
        resolver = new EffectivePolicyResolver();
        planner = new PersistencePlanner();
        deviceResolver = new VirtualDeviceResolver();
        bucket = TestFixtures.aBucketRef();
    }

    // -------------------------------------------------------------------------
    // Helper: constructs a StoragePolicy with MINIO_STANDARD parameters.
    // Expected MINIO_STANDARD-equivalent domain parameters:
    //   - storageClassId: STANDARD
    //   - dedup.enabled: true, scope: BUCKET, algorithm: SHA256, chunkSizeBytes: 1048576
    //   - compression.enabled: false
    //   - encryption.enabled: false
    //   - erasureCoding.enabled: false
    //   - replication.factor: 1
    // -------------------------------------------------------------------------

    private StoragePolicy minioStandardPolicy() {
        return StoragePolicy.of(
                StorageClassId.STANDARD,
                Optional.of(DedupConfig.of(
                        DedupScope.BUCKET_LEVEL,
                        FingerprintAlgorithm.SHA256,
                        MINIO_STANDARD_CHUNK_SIZE,
                        ChunkAlignment.NONE)),
                Optional.empty(),          // no compression
                Optional.empty(),          // no encryption
                Optional.empty(),          // no EC
                ReplicationConfig.of(1));  // single replica
    }

    // -------------------------------------------------------------------------
    // 5a — Verify EffectivePolicyResolver uses policy-driven chunk size
    // -------------------------------------------------------------------------

    @Test
    void resolve_minioStandard_effectivePolicyHasDedupWith1MiBChunkSize() {
        // Object size = exactly 1 MiB: dedup must NOT be bypassed (object >= chunk size).
        // This verifies the resolver reads chunkSize from the policy, not a hard-coded constant.
        StoragePolicy policy = minioStandardPolicy();
        UploadRequestContext context = TestFixtures.anUploadRequestContext(
                bucket, MINIO_STANDARD_CHUNK_SIZE, "application/octet-stream", EncryptionMode.NONE);

        EffectiveStoragePolicy effective = resolver.resolve(policy, context);

        assertTrue(effective.dedup().isPresent(),
                "MINIO_STANDARD: dedup must be present for object >= 1 MiB");
        assertEquals(MINIO_STANDARD_CHUNK_SIZE, effective.dedup().get().chunkSize(),
                "MINIO_STANDARD: effective chunk size must be exactly 1 MiB (1048576 bytes)");
        assertEquals(FingerprintAlgorithm.SHA256, effective.dedup().get().algorithm(),
                "MINIO_STANDARD: fingerprint algorithm must be SHA-256");
        assertEquals(DedupScope.BUCKET_LEVEL, effective.dedup().get().scope(),
                "MINIO_STANDARD: dedup scope must be BUCKET_LEVEL");
    }

    @Test
    void resolve_minioStandard_effectivePolicyHasNoCompressionEncryptionOrEC() {
        StoragePolicy policy = minioStandardPolicy();
        UploadRequestContext context = TestFixtures.aDefaultUploadRequestContext(
                bucket, MINIO_STANDARD_CHUNK_SIZE);

        EffectiveStoragePolicy effective = resolver.resolve(policy, context);

        assertTrue(effective.compression().isEmpty(),
                "MINIO_STANDARD: compression must be disabled");
        assertTrue(effective.encryption().isEmpty(),
                "MINIO_STANDARD: encryption must be disabled");
        assertTrue(effective.erasureCoding().isEmpty(),
                "MINIO_STANDARD: erasure coding must be disabled");
        assertEquals(1, effective.replication().factor(),
                "MINIO_STANDARD: replication factor must be 1");
        assertEquals(StorageClassId.STANDARD, effective.storageClassId(),
                "MINIO_STANDARD-equivalent plan: storage class must be STANDARD");
    }

    @Test
    void resolve_minioStandard_chunkSizeIsFromPolicy_notHardCodedConstant() {
        // Proof that the resolver uses the policy's DedupConfig.chunkSize() as the bypass threshold,
        // not the deprecated EffectivePolicyResolver.DEDUP_CHUNK_SIZE constant (64 KB).
        // An object of 65 KB is > 64 KB (old constant) but < 1 MiB (policy chunk size).
        // With the policy-driven threshold it must be bypassed; with the old constant it would not.
        StoragePolicy policy = minioStandardPolicy();
        long objectSizeBetweenOldAndNew = 65 * 1024L; // 65 KiB: > 64 KB old constant, < 1 MiB policy chunk

        UploadRequestContext context = TestFixtures.anUploadRequestContext(
                bucket, objectSizeBetweenOldAndNew, "application/octet-stream", EncryptionMode.NONE);

        EffectiveStoragePolicy effective = resolver.resolve(policy, context);

        assertTrue(effective.dedup().isEmpty(),
                "MINIO_STANDARD: dedup must be bypassed for 65 KiB object "
                + "(policy chunk = 1 MiB; resolver must use policy-driven threshold, not 64 KB constant)");
    }

    // -------------------------------------------------------------------------
    // 5a — Verify dedup bypass for small objects
    // -------------------------------------------------------------------------

    @Test
    void resolve_minioStandard_smallObject_bypassesDedup() {
        StoragePolicy policy = minioStandardPolicy();
        long smallObjectSize = 512 * 1024L; // 512 KiB — less than 1 MiB chunk

        UploadRequestContext context = TestFixtures.anUploadRequestContext(
                bucket, smallObjectSize, "application/octet-stream", EncryptionMode.NONE);

        EffectiveStoragePolicy effective = resolver.resolve(policy, context);

        assertTrue(effective.dedup().isEmpty(),
                "MINIO_STANDARD: dedup must be bypassed for objects smaller than 1 MiB chunk size");
    }

    // -------------------------------------------------------------------------
    // 5a — Verify VirtualDevice selection
    // -------------------------------------------------------------------------

    @Test
    void resolve_minioStandard_largeObject_virtualDeviceIsDedupDevice() {
        StoragePolicy policy = minioStandardPolicy();
        UploadRequestContext context = TestFixtures.aDefaultUploadRequestContext(
                bucket, MINIO_STANDARD_CHUNK_SIZE);
        EffectiveStoragePolicy effective = resolver.resolve(policy, context);

        VirtualDevice device = deviceResolver.resolve(effective, bucket);

        assertInstanceOf(VirtualDevice.DedupDevice.class, device,
                "MINIO_STANDARD: VirtualDevice must be a DedupDevice when dedup is enabled");
    }

    @Test
    void resolve_minioStandard_smallObject_virtualDeviceIsBucketDevice() {
        // Small objects bypass dedup → resolver must return BucketDevice
        StoragePolicy policy = minioStandardPolicy();
        long smallSize = 512 * 1024L;
        UploadRequestContext context = TestFixtures.aDefaultUploadRequestContext(bucket, smallSize);
        EffectiveStoragePolicy effective = resolver.resolve(policy, context);

        VirtualDevice device = deviceResolver.resolve(effective, bucket);

        assertInstanceOf(VirtualDevice.BucketDevice.class, device,
                "MINIO_STANDARD: VirtualDevice must be a BucketDevice when dedup is bypassed");
    }

    // -------------------------------------------------------------------------
    // 5c — Verify PersistencePlanner produces correct plan for MINIO_STANDARD
    // -------------------------------------------------------------------------

    @Test
    void plan_minioStandard_dedupStepIsExecutedWithCorrectConfig() {
        StoragePolicy policy = minioStandardPolicy();
        UploadRequestContext context = TestFixtures.aDefaultUploadRequestContext(
                bucket, MINIO_STANDARD_CHUNK_SIZE);
        EffectiveStoragePolicy effective = resolver.resolve(policy, context);
        VirtualDevice device = deviceResolver.resolve(effective, bucket);

        PersistencePlan plan = planner.createPlan(effective, device);

        StepPlan dedupStep = plan.steps().get(0);
        assertEquals(StepId.DEDUP, dedupStep.stepId(),
                "Step 0 must be DEDUP");
        assertEquals(StepExecutionStatus.EXECUTED, dedupStep.expectedStatus(),
                "MINIO_STANDARD: DEDUP step must be EXECUTED when dedup is enabled");
        assertTrue(dedupStep.config().isPresent(),
                "MINIO_STANDARD: DEDUP step must carry a DedupStepConfig");
        assertInstanceOf(StepConfig.DedupStepConfig.class, dedupStep.config().get(),
                "MINIO_STANDARD: DEDUP step config must be a DedupStepConfig");

        StepConfig.DedupStepConfig dedupConfig = (StepConfig.DedupStepConfig) dedupStep.config().get();
        assertEquals(MINIO_STANDARD_CHUNK_SIZE, dedupConfig.config().chunkSize(),
                "MINIO_STANDARD: DEDUP step chunk size must be 1 MiB (1048576 bytes)");
        assertEquals(FingerprintAlgorithm.SHA256, dedupConfig.config().algorithm(),
                "MINIO_STANDARD: DEDUP step algorithm must be SHA-256");
    }

    @Test
    void plan_minioStandard_ecStepIsSkipped() {
        StoragePolicy policy = minioStandardPolicy();
        UploadRequestContext context = TestFixtures.aDefaultUploadRequestContext(
                bucket, MINIO_STANDARD_CHUNK_SIZE);
        EffectiveStoragePolicy effective = resolver.resolve(policy, context);
        VirtualDevice device = deviceResolver.resolve(effective, bucket);

        PersistencePlan plan = planner.createPlan(effective, device);

        StepPlan ecStep = plan.steps().get(3);
        assertEquals(StepId.ERASURE_CODING, ecStep.stepId(),
                "Step 3 must be ERASURE_CODING");
        assertEquals(StepExecutionStatus.SKIPPED, ecStep.expectedStatus(),
                "MINIO_STANDARD: ERASURE_CODING step must be SKIPPED (EC is disabled)");
        assertTrue(ecStep.config().isEmpty(),
                "MINIO_STANDARD: ERASURE_CODING step must have no config when EC is disabled");
    }

    @Test
    void plan_minioStandard_compressionStepIsSkipped() {
        StoragePolicy policy = minioStandardPolicy();
        UploadRequestContext context = TestFixtures.aDefaultUploadRequestContext(
                bucket, MINIO_STANDARD_CHUNK_SIZE);
        EffectiveStoragePolicy effective = resolver.resolve(policy, context);
        VirtualDevice device = deviceResolver.resolve(effective, bucket);

        PersistencePlan plan = planner.createPlan(effective, device);

        StepPlan compressStep = plan.steps().get(1);
        assertEquals(StepId.COMPRESS, compressStep.stepId(),
                "Step 1 must be COMPRESS");
        assertEquals(StepExecutionStatus.SKIPPED, compressStep.expectedStatus(),
                "MINIO_STANDARD: COMPRESS step must be SKIPPED (compression is disabled)");
    }

    @Test
    void plan_minioStandard_cryptStepIsSkipped() {
        StoragePolicy policy = minioStandardPolicy();
        UploadRequestContext context = TestFixtures.aDefaultUploadRequestContext(
                bucket, MINIO_STANDARD_CHUNK_SIZE);
        EffectiveStoragePolicy effective = resolver.resolve(policy, context);
        VirtualDevice device = deviceResolver.resolve(effective, bucket);

        PersistencePlan plan = planner.createPlan(effective, device);

        StepPlan cryptStep = plan.steps().get(2);
        assertEquals(StepId.CRYPT, cryptStep.stepId(),
                "Step 2 must be CRYPT");
        assertEquals(StepExecutionStatus.SKIPPED, cryptStep.expectedStatus(),
                "MINIO_STANDARD: CRYPT step must be SKIPPED (encryption is disabled)");
    }

    @Test
    void plan_minioStandard_replicationFactorIs1() {
        StoragePolicy policy = minioStandardPolicy();
        UploadRequestContext context = TestFixtures.aDefaultUploadRequestContext(
                bucket, MINIO_STANDARD_CHUNK_SIZE);
        EffectiveStoragePolicy effective = resolver.resolve(policy, context);
        VirtualDevice device = deviceResolver.resolve(effective, bucket);

        PersistencePlan plan = planner.createPlan(effective, device);

        StepPlan replStep = plan.steps().get(4);
        assertEquals(StepId.REPLICATION, replStep.stepId(),
                "Step 4 must be REPLICATION");
        assertEquals(StepExecutionStatus.EXECUTED, replStep.expectedStatus(),
                "MINIO_STANDARD: REPLICATION step must be EXECUTED (always active)");
        assertTrue(replStep.config().isPresent(),
                "MINIO_STANDARD: REPLICATION step must carry a ReplicationStepConfig");
        assertInstanceOf(StepConfig.ReplicationStepConfig.class, replStep.config().get());

        StepConfig.ReplicationStepConfig replConfig =
                (StepConfig.ReplicationStepConfig) replStep.config().get();
        assertEquals(1, replConfig.config().factor(),
                "MINIO_STANDARD: replication factor must be 1 (single-node, no redundant copies)");
    }

    @Test
    void plan_minioStandard_storageClassIsSTANDARD() {
        StoragePolicy policy = minioStandardPolicy();
        UploadRequestContext context = TestFixtures.aDefaultUploadRequestContext(
                bucket, MINIO_STANDARD_CHUNK_SIZE);
        EffectiveStoragePolicy effective = resolver.resolve(policy, context);
        VirtualDevice device = deviceResolver.resolve(effective, bucket);

        PersistencePlan plan = planner.createPlan(effective, device);

        assertEquals(StorageClassId.STANDARD, plan.effectivePolicy().storageClassId(),
                "MINIO_STANDARD-equivalent plan: storage class must be STANDARD");
    }

    @Test
    void plan_minioStandard_planHasExactly6Steps() {
        StoragePolicy policy = minioStandardPolicy();
        UploadRequestContext context = TestFixtures.aDefaultUploadRequestContext(
                bucket, MINIO_STANDARD_CHUNK_SIZE);
        EffectiveStoragePolicy effective = resolver.resolve(policy, context);
        VirtualDevice device = deviceResolver.resolve(effective, bucket);

        PersistencePlan plan = planner.createPlan(effective, device);

        assertEquals(6, plan.steps().size(),
                "MINIO_STANDARD: plan must always have exactly 6 steps");
    }

    @Test
    void plan_minioStandard_storeStepIsAlwaysExecuted() {
        StoragePolicy policy = minioStandardPolicy();
        UploadRequestContext context = TestFixtures.aDefaultUploadRequestContext(
                bucket, MINIO_STANDARD_CHUNK_SIZE);
        EffectiveStoragePolicy effective = resolver.resolve(policy, context);
        VirtualDevice device = deviceResolver.resolve(effective, bucket);

        PersistencePlan plan = planner.createPlan(effective, device);

        StepPlan storeStep = plan.steps().get(5);
        assertEquals(StepId.STORE, storeStep.stepId(),
                "Step 5 must be STORE");
        assertEquals(StepExecutionStatus.EXECUTED, storeStep.expectedStatus(),
                "MINIO_STANDARD: STORE step must always be EXECUTED");
    }
}
