package com.example.magrathea.storageengine.domain.service;

import com.example.magrathea.storageengine.domain.TestFixtures;
import com.example.magrathea.storageengine.domain.valueobject.BucketRef;
import com.example.magrathea.storageengine.domain.valueobject.EffectiveStoragePolicy;
import com.example.magrathea.storageengine.domain.valueobject.PersistencePlan;
import com.example.magrathea.storageengine.domain.valueobject.StepExecutionStatus;
import com.example.magrathea.storageengine.domain.valueobject.StepId;
import com.example.magrathea.storageengine.domain.valueobject.StepPlan;
import com.example.magrathea.storageengine.domain.valueobject.VirtualDevice;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Unit tests for {@link PersistencePlanner}.
 * Pure JUnit 5 — no Spring, no Mockito, no reactive imports.
 */
class PersistencePlannerTest {

    private PersistencePlanner planner;
    private BucketRef bucket;

    @BeforeEach
    void setUp() {
        planner = new PersistencePlanner();
        bucket = TestFixtures.aBucketRef();
    }

    // -------------------------------------------------------------------------
    // Plan structure invariants
    // -------------------------------------------------------------------------

    @Test
    void createPlan_returns6Steps() {
        EffectiveStoragePolicy policy = TestFixtures.aMinimalEffectivePolicy(bucket);
        VirtualDevice device = TestFixtures.aBucketDevice(bucket);

        PersistencePlan plan = planner.createPlan(policy, device);

        assertEquals(6, plan.steps().size(), "A persistence plan must always contain exactly 6 steps");
    }

    @Test
    void createPlan_stepsInOrder_DEDUP_COMPRESS_CRYPT_EC_REPLICATION_STORE() {
        EffectiveStoragePolicy policy = TestFixtures.aMinimalEffectivePolicy(bucket);
        VirtualDevice device = TestFixtures.aBucketDevice(bucket);

        PersistencePlan plan = planner.createPlan(policy, device);
        List<StepPlan> steps = plan.steps();

        assertEquals(StepId.DEDUP,          steps.get(0).stepId(), "Step 0 must be DEDUP");
        assertEquals(StepId.COMPRESS,       steps.get(1).stepId(), "Step 1 must be COMPRESS");
        assertEquals(StepId.CRYPT,          steps.get(2).stepId(), "Step 2 must be CRYPT");
        assertEquals(StepId.ERASURE_CODING, steps.get(3).stepId(), "Step 3 must be ERASURE_CODING");
        assertEquals(StepId.REPLICATION,    steps.get(4).stepId(), "Step 4 must be REPLICATION");
        assertEquals(StepId.STORE,          steps.get(5).stepId(), "Step 5 must be STORE");
    }

    // -------------------------------------------------------------------------
    // Dedup step status based on policy
    // -------------------------------------------------------------------------

    @Test
    void createPlan_dedupEnabled_dedupStepIsExecuted() {
        EffectiveStoragePolicy policy = TestFixtures.anEffectivePolicyWithBucketDedup(bucket);
        VirtualDevice device = new VirtualDevice.DedupDevice(
                new com.example.magrathea.storageengine.domain.valueobject.DedupNamespace.BucketDedupNamespace(bucket),
                com.example.magrathea.storageengine.domain.valueobject.WorkflowCompatibilityKey.from(policy));

        PersistencePlan plan = planner.createPlan(policy, device);

        StepPlan dedupStep = plan.steps().get(0);
        assertEquals(StepExecutionStatus.EXECUTED, dedupStep.expectedStatus(),
                "DEDUP step must be EXECUTED when dedup is enabled in effective policy");
    }

    @Test
    void createPlan_noDedupNoCompression_dedupAndCompressStepsAreSkipped() {
        EffectiveStoragePolicy policy = TestFixtures.aMinimalEffectivePolicy(bucket);
        VirtualDevice device = TestFixtures.aBucketDevice(bucket);

        PersistencePlan plan = planner.createPlan(policy, device);

        StepPlan dedupStep    = plan.steps().get(0);
        StepPlan compressStep = plan.steps().get(1);

        assertEquals(StepExecutionStatus.SKIPPED, dedupStep.expectedStatus(),
                "DEDUP step must be SKIPPED when dedup is absent from effective policy");
        assertEquals(StepExecutionStatus.SKIPPED, compressStep.expectedStatus(),
                "COMPRESS step must be SKIPPED when compression is absent from effective policy");
    }

    // -------------------------------------------------------------------------
    // Erasure coding step status based on policy
    // -------------------------------------------------------------------------

    @Test
    void createPlan_ecEnabled_ECStepIsExecuted() {
        EffectiveStoragePolicy policy = TestFixtures.anEffectivePolicyWithEC(bucket);
        VirtualDevice device = TestFixtures.aBucketDevice(bucket);

        PersistencePlan plan = planner.createPlan(policy, device);

        StepPlan ecStep = plan.steps().get(3);
        assertEquals(StepExecutionStatus.EXECUTED, ecStep.expectedStatus(),
                "ERASURE_CODING step must be EXECUTED when EC is enabled in effective policy");
    }

    @Test
    void createPlan_noEC_ECStepIsSkipped() {
        EffectiveStoragePolicy policy = TestFixtures.aMinimalEffectivePolicy(bucket);
        VirtualDevice device = TestFixtures.aBucketDevice(bucket);

        PersistencePlan plan = planner.createPlan(policy, device);

        StepPlan ecStep = plan.steps().get(3);
        assertEquals(StepExecutionStatus.SKIPPED, ecStep.expectedStatus(),
                "ERASURE_CODING step must be SKIPPED when EC is absent from effective policy");
    }

    // -------------------------------------------------------------------------
    // Always-executed steps
    // -------------------------------------------------------------------------

    @Test
    void createPlan_replicationStep_alwaysExecuted() {
        EffectiveStoragePolicy policy = TestFixtures.aMinimalEffectivePolicy(bucket);
        VirtualDevice device = TestFixtures.aBucketDevice(bucket);

        PersistencePlan plan = planner.createPlan(policy, device);

        StepPlan replicationStep = plan.steps().get(4);
        assertEquals(StepExecutionStatus.EXECUTED, replicationStep.expectedStatus(),
                "REPLICATION step must always be EXECUTED regardless of policy options");
    }

    @Test
    void createPlan_storeStep_alwaysExecuted() {
        EffectiveStoragePolicy policy = TestFixtures.aMinimalEffectivePolicy(bucket);
        VirtualDevice device = TestFixtures.aBucketDevice(bucket);

        PersistencePlan plan = planner.createPlan(policy, device);

        StepPlan storeStep = plan.steps().get(5);
        assertEquals(StepExecutionStatus.EXECUTED, storeStep.expectedStatus(),
                "STORE step must always be EXECUTED regardless of policy options");
    }
}
