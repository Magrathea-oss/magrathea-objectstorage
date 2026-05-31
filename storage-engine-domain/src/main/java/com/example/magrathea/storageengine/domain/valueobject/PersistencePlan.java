package com.example.magrathea.storageengine.domain.valueobject;

import java.util.List;
import java.util.Optional;
import java.util.Collections;

public record PersistencePlan(
        EffectiveStoragePolicy effectivePolicy,
        VirtualDevice targetDevice,
        Optional<WorkflowCompatibilityKey> workflowKey,
        DeviceConfigurationHash deviceHash,
        List<StepPlan> steps) {

    public PersistencePlan {
        java.util.Objects.requireNonNull(effectivePolicy, "effectivePolicy must not be null");
        java.util.Objects.requireNonNull(targetDevice, "targetDevice must not be null");
        java.util.Objects.requireNonNull(workflowKey, "workflowKey must not be null");
        java.util.Objects.requireNonNull(deviceHash, "deviceHash must not be null");
        java.util.Objects.requireNonNull(steps, "steps must not be null");
        if (steps.size() != 6) {
            throw new IllegalArgumentException("steps must have exactly 6 entries: " + steps.size());
        }
        // Validate order: DEDUP, COMPRESS, CRYPT, ERASURE_CODING, REPLICATION, STORE
        StepId[] expectedOrder = {
                StepId.DEDUP, StepId.COMPRESS, StepId.CRYPT,
                StepId.ERASURE_CODING, StepId.REPLICATION, StepId.STORE
        };
        for (int i = 0; i < 6; i++) {
            StepId actual = steps.get(i).stepId();
            if (actual != expectedOrder[i]) {
                throw new IllegalArgumentException(
                        "Step order violation at index " + i + ": expected " + expectedOrder[i] + " but got " + actual);
            }
        }
    }

    public static PersistencePlan create(EffectiveStoragePolicy effectivePolicy, VirtualDevice targetDevice) {
        WorkflowCompatibilityKey workflowKey;
        if (targetDevice instanceof VirtualDevice.DedupDevice dd) {
            workflowKey = dd.workflowKey();
        } else {
            workflowKey = WorkflowCompatibilityKey.from(effectivePolicy);
        }

        DeviceConfigurationHash deviceHash = targetDevice.configurationHash();
        Optional<WorkflowCompatibilityKey> wk = Optional.of(workflowKey);

        // Build steps based on effective policy
        boolean dedupEnabled = effectivePolicy.dedup().isPresent();
        boolean compressEnabled = effectivePolicy.compression().isPresent();
        boolean cryptEnabled = effectivePolicy.encryption().isPresent();
        boolean ecEnabled = effectivePolicy.erasureCoding().isPresent();

        List<StepPlan> steps = List.of(
                // Step 1: DEDUP
                StepPlan.of(
                        StepId.DEDUP,
                        dedupEnabled ? StepExecutionStatus.EXECUTED : StepExecutionStatus.SKIPPED,
                        dedupEnabled
                                ? Optional.of(new StepConfig.DedupStepConfig(effectivePolicy.dedup().get()))
                                : Optional.empty(),
                        dedupEnabled,
                        false,
                        false,
                        false),

                // Step 2: COMPRESS
                StepPlan.of(
                        StepId.COMPRESS,
                        compressEnabled ? StepExecutionStatus.EXECUTED : StepExecutionStatus.SKIPPED,
                        compressEnabled
                                ? Optional.of(new StepConfig.CompressStepConfig(effectivePolicy.compression().get()))
                                : Optional.empty(),
                        compressEnabled,
                        true,
                        true,
                        true),

                // Step 3: CRYPT
                StepPlan.of(
                        StepId.CRYPT,
                        cryptEnabled ? StepExecutionStatus.EXECUTED : StepExecutionStatus.SKIPPED,
                        cryptEnabled
                                ? Optional.of(new StepConfig.CryptStepConfig(
                                        EncryptionConfig.of(
                                                effectivePolicy.encryption().get().algorithm(),
                                                effectivePolicy.encryption().get().defaultKeyReference())))
                                : Optional.empty(),
                        cryptEnabled,
                        true,
                        true,
                        true),

                // Step 4: ERASURE_CODING
                StepPlan.of(
                        StepId.ERASURE_CODING,
                        ecEnabled ? StepExecutionStatus.EXECUTED : StepExecutionStatus.SKIPPED,
                        ecEnabled
                                ? Optional.of(new StepConfig.ECStepConfig(effectivePolicy.erasureCoding().get()))
                                : Optional.empty(),
                        ecEnabled,
                        true,
                        true,
                        true),

                // Step 5: REPLICATION
                StepPlan.of(
                        StepId.REPLICATION,
                        StepExecutionStatus.EXECUTED,
                        Optional.of(new StepConfig.ReplicationStepConfig(effectivePolicy.replication())),
                        true,
                        false,
                        false,
                        true),

                // Step 6: STORE
                StepPlan.of(
                        StepId.STORE,
                        StepExecutionStatus.EXECUTED,
                        Optional.of(new StepConfig.StoreStepConfig(targetDevice)),
                        true,
                        false,
                        false,
                        false)
        );

        return new PersistencePlan(effectivePolicy, targetDevice, wk, deviceHash, Collections.unmodifiableList(steps));
    }
}
