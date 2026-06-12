package com.example.magrathea.storageengine.domain.service;

import com.example.magrathea.storageengine.domain.TestFixtures;
import com.example.magrathea.storageengine.domain.valueobject.BucketRef;
import com.example.magrathea.storageengine.domain.valueobject.EffectiveStoragePolicy;
import com.example.magrathea.storageengine.domain.valueobject.EncryptionMode;
import com.example.magrathea.storageengine.domain.valueobject.ErasureCodingConfig;
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
import static org.junit.jupiter.api.Assertions.assertFalse;
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
 * <p>The MINIO_STANDARD-equivalent policy is:
 * <ul>
 *   <li>Storage class: {@code STANDARD}</li>
 *   <li>Dedup disabled</li>
 *   <li>Erasure coding enabled — 4 data blocks, 2 parity blocks</li>
 *   <li>Replication factor 1 (single logical replica; EC provides redundancy)</li>
 *   <li>Compression disabled</li>
 *   <li>Encryption disabled by default</li>
 * </ul>
 *
 * <p>Pure JUnit 5 — no Spring, no Mockito, no reactive imports.
 */
class PersistencePlannerMinioStandardTest {

    private static final int MINIO_STANDARD_EC_DATA_BLOCKS = 4;
    private static final int MINIO_STANDARD_EC_PARITY_BLOCKS = 2;
    private static final long OBJECT_SIZE = 1_048_576L;

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

    private StoragePolicy minioStandardPolicy() {
        return StoragePolicy.of(
                StorageClassId.STANDARD,
                Optional.empty(),          // dedup disabled for MINIO_STANDARD
                Optional.empty(),          // compression disabled
                Optional.empty(),          // encryption disabled by default
                Optional.of(ErasureCodingConfig.of(
                        MINIO_STANDARD_EC_DATA_BLOCKS,
                        MINIO_STANDARD_EC_PARITY_BLOCKS)),
                ReplicationConfig.of(1));  // no multi-replica behavior
    }

    private EffectiveStoragePolicy resolveMinioStandardPolicy() {
        UploadRequestContext context = TestFixtures.anUploadRequestContext(
                bucket, OBJECT_SIZE, "application/octet-stream", EncryptionMode.NONE);
        return resolver.resolve(minioStandardPolicy(), context);
    }

    private PersistencePlan createMinioStandardPlan() {
        EffectiveStoragePolicy effective = resolveMinioStandardPolicy();
        VirtualDevice device = deviceResolver.resolve(effective, bucket);
        return planner.createPlan(effective, device);
    }

    @Test
    void resolve_minioStandard_effectivePolicyHasStandardClassAndNoDedup() {
        EffectiveStoragePolicy effective = resolveMinioStandardPolicy();

        assertEquals(StorageClassId.STANDARD, effective.storageClassId(),
                "MINIO_STANDARD-equivalent policy must resolve to STANDARD storage class");
        assertTrue(effective.dedup().isEmpty(),
                "MINIO_STANDARD must not enable dedup as its primary behavior");
    }

    @Test
    void resolve_minioStandard_effectivePolicyHasEC4Data2ParityAndReplicationFactor1() {
        EffectiveStoragePolicy effective = resolveMinioStandardPolicy();

        assertTrue(effective.erasureCoding().isPresent(),
                "MINIO_STANDARD must enable erasure coding");
        ErasureCodingConfig ec = effective.erasureCoding().get();
        assertEquals(MINIO_STANDARD_EC_DATA_BLOCKS, ec.dataBlocks(),
                "MINIO_STANDARD EC config must use 4 data blocks");
        assertEquals(MINIO_STANDARD_EC_PARITY_BLOCKS, ec.parityBlocks(),
                "MINIO_STANDARD EC config must use 2 parity blocks");
        assertEquals(1, effective.replication().factor(),
                "MINIO_STANDARD must keep replication factor 1; EC provides redundancy");
    }

    @Test
    void resolve_minioStandard_effectivePolicyHasNoCompressionOrEncryptionByDefault() {
        EffectiveStoragePolicy effective = resolveMinioStandardPolicy();

        assertTrue(effective.compression().isEmpty(),
                "MINIO_STANDARD compression must be disabled");
        assertTrue(effective.encryption().isEmpty(),
                "MINIO_STANDARD encryption must be disabled by default");
    }

    @Test
    void resolve_minioStandard_virtualDeviceIsBucketDeviceBecauseDedupIsDisabled() {
        EffectiveStoragePolicy effective = resolveMinioStandardPolicy();

        VirtualDevice device = deviceResolver.resolve(effective, bucket);

        assertInstanceOf(VirtualDevice.BucketDevice.class, device,
                "MINIO_STANDARD must not resolve to a DedupDevice when dedup is disabled");
    }

    @Test
    void plan_minioStandard_dedupStepIsSkippedAndHasNoConfig() {
        PersistencePlan plan = createMinioStandardPlan();

        StepPlan dedupStep = plan.steps().get(0);
        assertEquals(StepId.DEDUP, dedupStep.stepId(),
                "Step 0 must be DEDUP");
        assertEquals(StepExecutionStatus.SKIPPED, dedupStep.expectedStatus(),
                "MINIO_STANDARD DEDUP step must be SKIPPED because dedup is disabled");
        assertTrue(dedupStep.config().isEmpty(),
                "MINIO_STANDARD DEDUP step must not carry DedupStepConfig");
        assertFalse(plan.steps().stream()
                        .flatMap(step -> step.config().stream())
                        .anyMatch(StepConfig.DedupStepConfig.class::isInstance),
                "MINIO_STANDARD plan must not include any DedupStepConfig");
    }

    @Test
    void plan_minioStandard_ecStepIsExecutedWith4Data2ParityConfig() {
        PersistencePlan plan = createMinioStandardPlan();

        StepPlan ecStep = plan.steps().get(3);
        assertEquals(StepId.ERASURE_CODING, ecStep.stepId(),
                "Step 3 must be ERASURE_CODING");
        assertEquals(StepExecutionStatus.EXECUTED, ecStep.expectedStatus(),
                "MINIO_STANDARD ERASURE_CODING step must be EXECUTED");
        assertTrue(ecStep.config().isPresent(),
                "MINIO_STANDARD ERASURE_CODING step must carry an ECStepConfig");
        assertInstanceOf(StepConfig.ECStepConfig.class, ecStep.config().get(),
                "MINIO_STANDARD ERASURE_CODING step config must be ECStepConfig");

        StepConfig.ECStepConfig ecConfig = (StepConfig.ECStepConfig) ecStep.config().get();
        assertEquals(MINIO_STANDARD_EC_DATA_BLOCKS, ecConfig.config().dataBlocks(),
                "MINIO_STANDARD EC step must use 4 data blocks");
        assertEquals(MINIO_STANDARD_EC_PARITY_BLOCKS, ecConfig.config().parityBlocks(),
                "MINIO_STANDARD EC step must use 2 parity blocks");
    }

    @Test
    void plan_minioStandard_compressionAndCryptStepsAreSkipped() {
        PersistencePlan plan = createMinioStandardPlan();

        StepPlan compressStep = plan.steps().get(1);
        StepPlan cryptStep = plan.steps().get(2);

        assertEquals(StepId.COMPRESS, compressStep.stepId(),
                "Step 1 must be COMPRESS");
        assertEquals(StepExecutionStatus.SKIPPED, compressStep.expectedStatus(),
                "MINIO_STANDARD COMPRESS step must be SKIPPED");
        assertTrue(compressStep.config().isEmpty(),
                "MINIO_STANDARD COMPRESS step must have no config");

        assertEquals(StepId.CRYPT, cryptStep.stepId(),
                "Step 2 must be CRYPT");
        assertEquals(StepExecutionStatus.SKIPPED, cryptStep.expectedStatus(),
                "MINIO_STANDARD CRYPT step must be SKIPPED");
        assertTrue(cryptStep.config().isEmpty(),
                "MINIO_STANDARD CRYPT step must have no config");
    }

    @Test
    void plan_minioStandard_replicationFactorIs1WithNoMultiReplicaBehavior() {
        PersistencePlan plan = createMinioStandardPlan();

        StepPlan replStep = plan.steps().get(4);
        assertEquals(StepId.REPLICATION, replStep.stepId(),
                "Step 4 must be REPLICATION");
        assertEquals(StepExecutionStatus.EXECUTED, replStep.expectedStatus(),
                "REPLICATION step remains present but configured with factor 1");
        assertTrue(replStep.config().isPresent(),
                "REPLICATION step must carry ReplicationStepConfig");
        assertInstanceOf(StepConfig.ReplicationStepConfig.class, replStep.config().get());

        StepConfig.ReplicationStepConfig replConfig =
                (StepConfig.ReplicationStepConfig) replStep.config().get();
        assertEquals(1, replConfig.config().factor(),
                "MINIO_STANDARD replication config must be factor 1, not additional replicas");
    }

    @Test
    void plan_minioStandard_storageClassIsSTANDARD() {
        PersistencePlan plan = createMinioStandardPlan();

        assertEquals(StorageClassId.STANDARD, plan.effectivePolicy().storageClassId(),
                "MINIO_STANDARD-equivalent plan must retain STANDARD storage class");
    }

    @Test
    void plan_minioStandard_planHasExactly6Steps() {
        PersistencePlan plan = createMinioStandardPlan();

        assertEquals(6, plan.steps().size(),
                "MINIO_STANDARD plan must always have exactly 6 steps");
    }

    @Test
    void plan_minioStandard_storeStepIsAlwaysExecuted() {
        PersistencePlan plan = createMinioStandardPlan();

        StepPlan storeStep = plan.steps().get(5);
        assertEquals(StepId.STORE, storeStep.stepId(),
                "Step 5 must be STORE");
        assertEquals(StepExecutionStatus.EXECUTED, storeStep.expectedStatus(),
                "MINIO_STANDARD STORE step must always be EXECUTED");
    }
}
