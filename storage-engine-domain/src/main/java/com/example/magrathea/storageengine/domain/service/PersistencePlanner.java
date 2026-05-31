package com.example.magrathea.storageengine.domain.service;

import com.example.magrathea.storageengine.domain.valueobject.ChunkPersistenceTrace;
import com.example.magrathea.storageengine.domain.valueobject.ContentHash;
import com.example.magrathea.storageengine.domain.valueobject.EffectiveStoragePolicy;
import com.example.magrathea.storageengine.domain.valueobject.ObjectManifest;
import com.example.magrathea.storageengine.domain.valueobject.PersistencePlan;
import com.example.magrathea.storageengine.domain.valueobject.StepExecutionRecord;
import com.example.magrathea.storageengine.domain.valueobject.StepId;
import com.example.magrathea.storageengine.domain.valueobject.StepPlan;
import com.example.magrathea.storageengine.domain.valueobject.VirtualDevice;

import java.util.List;
import java.util.Objects;

/**
 * Pure domain service — creates persistence plans and validates execution traces.
 * No framework dependencies, no I/O, no reactive types.
 */
public class PersistencePlanner {

    public PersistencePlan createPlan(EffectiveStoragePolicy effectivePolicy, VirtualDevice targetDevice) {
        Objects.requireNonNull(effectivePolicy, "effectivePolicy must not be null");
        Objects.requireNonNull(targetDevice, "targetDevice must not be null");
        return PersistencePlan.create(effectivePolicy, targetDevice);
    }

    /**
     * Validates a ChunkPersistenceTrace against its PersistencePlan.
     * Checks: 6 steps, correct order, status matching plan, checksum chain integrity,
     * and sub-operation consistency.
     */
    public void validateTrace(ChunkPersistenceTrace trace, PersistencePlan plan) {
        Objects.requireNonNull(trace, "trace must not be null");
        Objects.requireNonNull(plan, "plan must not be null");

        List<StepExecutionRecord> steps = trace.steps();
        List<StepPlan> stepPlans = plan.steps();

        // Invariant: exactly 6 steps
        if (steps.size() != 6) {
            throw new IllegalArgumentException(
                    "Trace must have exactly 6 steps: " + steps.size());
        }

        // Check order and status matching
        StepId[] expectedOrder = {
                StepId.DEDUP, StepId.COMPRESS, StepId.CRYPT,
                StepId.ERASURE_CODING, StepId.REPLICATION, StepId.STORE
        };

        for (int i = 0; i < 6; i++) {
            StepExecutionRecord record = steps.get(i);
            StepPlan expected = stepPlans.get(i);

            // Validate step ID order
            if (record.stepId() != expectedOrder[i]) {
                throw new IllegalArgumentException(
                        "Step order violation at index " + i + ": expected "
                                + expectedOrder[i] + " but got " + record.stepId());
            }

            // Validate status matches plan
            if (record.status() != expected.expectedStatus()) {
                throw new IllegalArgumentException(
                        "Step " + record.stepId() + " status mismatch: expected "
                                + expected.expectedStatus() + " but got " + record.status());
            }
        }

        // Validate checksum chain: outputChecksum of step N must equal inputChecksum of step N+1
        for (int i = 0; i < 5; i++) {
            StepExecutionRecord current = steps.get(i);
            StepExecutionRecord next = steps.get(i + 1);
            ContentHash currentOutput = current.outputChecksum()
                    .orElseThrow(() -> new IllegalArgumentException(
                            "Step " + current.stepId() + " is missing outputChecksum"));
            ContentHash nextInput = next.inputChecksum()
                    .orElseThrow(() -> new IllegalArgumentException(
                            "Step " + next.stepId() + " is missing inputChecksum"));
            if (!currentOutput.equals(nextInput)) {
                throw new IllegalArgumentException(
                        "Checksum chain broken at " + current.stepId()
                                + " -> " + next.stepId());
            }
        }

        // Validate sub-operation consistency
        for (StepExecutionRecord record : steps) {
            switch (record.status()) {
                case EXECUTED -> {
                    if (record.operationOutcome().isEmpty()) {
                        throw new IllegalArgumentException(
                                "EXECUTED step " + record.stepId() + " missing operationOutcome");
                    }
                }
                case SKIPPED -> {
                    if (record.operationOutcome().isPresent()) {
                        throw new IllegalArgumentException(
                                "SKIPPED step " + record.stepId() + " has unexpected operationOutcome");
                    }
                }
                case BYPASSED -> {
                    if (record.bypassReason().isEmpty()) {
                        throw new IllegalArgumentException(
                                "BYPASSED step " + record.stepId() + " missing bypassReason");
                    }
                }
            }
        }
    }

    /**
     * Validates an ObjectManifest against its PersistencePlan.
     * Ensures the manifest's device hash matches, policy decisions are consistent,
     * and chunk references align with the plan.
     */
    public void validateManifest(ObjectManifest manifest, PersistencePlan plan) {
        Objects.requireNonNull(manifest, "manifest must not be null");
        Objects.requireNonNull(plan, "plan must not be null");

        // Validate device hash matches plan
        if (!manifest.deviceHash().equals(plan.deviceHash())) {
            throw new IllegalArgumentException(
                    "Manifest device hash mismatch: manifest="
                            + manifest.deviceHash().value() + ", plan=" + plan.deviceHash().value());
        }

        // Validate storage class matches
        if (!manifest.storageClassId().equals(plan.effectivePolicy().storageClassId())) {
            throw new IllegalArgumentException(
                    "Manifest storage class mismatch: manifest="
                            + manifest.storageClassId().value() + ", plan="
                            + plan.effectivePolicy().storageClassId().value());
        }

        // Validate policy decisions: should have exactly 6 decisions (one per step feature)
        List<com.example.magrathea.storageengine.domain.valueobject.PolicyDecision> decisions =
                manifest.policyDecisions();
        if (decisions.size() != 6) {
            throw new IllegalArgumentException(
                    "Expected 6 policy decisions but got " + decisions.size());
        }

        // Validate step plans match policy decisions
        for (int i = 0; i < 6; i++) {
            StepPlan stepPlan = plan.steps().get(i);
            com.example.magrathea.storageengine.domain.valueobject.PolicyDecision decision = decisions.get(i);
            if (stepPlan.stepId() != decision.feature()) {
                throw new IllegalArgumentException(
                        "Policy decision order mismatch at index " + i
                                + ": plan step " + stepPlan.stepId()
                                + " vs decision feature " + decision.feature());
            }
        }

        // Validate chunk count consistency
        if (manifest.chunkCount() != manifest.chunks().size()) {
            throw new IllegalArgumentException(
                    "chunkCount mismatch: declared " + manifest.chunkCount()
                            + " but chunks list has " + manifest.chunks().size());
        }

        // Validate total sizes
        if (manifest.totalOriginalSize() < 0) {
            throw new IllegalArgumentException(
                    "totalOriginalSize must be >= 0: " + manifest.totalOriginalSize());
        }
        if (manifest.totalStoredSize() < 0) {
            throw new IllegalArgumentException(
                    "totalStoredSize must be >= 0: " + manifest.totalStoredSize());
        }
    }
}
