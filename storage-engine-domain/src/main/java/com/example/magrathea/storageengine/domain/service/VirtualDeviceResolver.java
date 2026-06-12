package com.example.magrathea.storageengine.domain.service;

import com.example.magrathea.storageengine.domain.valueobject.BucketRef;
import com.example.magrathea.storageengine.domain.valueobject.DedupConfig;
import com.example.magrathea.storageengine.domain.valueobject.DedupNamespace;
import com.example.magrathea.storageengine.domain.valueobject.DedupScope;
import com.example.magrathea.storageengine.domain.valueobject.DeviceHealth;
import com.example.magrathea.storageengine.domain.valueobject.EffectiveStoragePolicy;
import com.example.magrathea.storageengine.domain.valueobject.StorageDevice;
import com.example.magrathea.storageengine.domain.valueobject.StoragePolicy;
import com.example.magrathea.storageengine.domain.valueobject.VirtualDevice;
import com.example.magrathea.storageengine.domain.valueobject.WorkflowCompatibilityKey;

import java.util.Comparator;
import java.util.List;
import java.util.Objects;

/**
 * Pure domain service — resolves an EffectiveStoragePolicy into a VirtualDevice.
 * Returns BucketDevice if dedup is disabled/bypassed, DedupDevice otherwise.
 * No framework dependencies, no I/O, no reactive types.
 */
public class VirtualDeviceResolver {

    /**
     * Selects the best {@link StorageDevice} from the supplied list for the given
     * {@link StoragePolicy}.
     *
     * <p>Selection rules (applied in order):
     * <ol>
     *   <li>Only {@link DeviceHealth#HEALTHY} devices are eligible for new writes.</li>
     *   <li>Among healthy devices, prefer the one with the highest available capacity.</li>
     * </ol>
     *
     * @param policy    the storage policy that drives eligibility requirements
     * @param available the set of candidate storage devices
     * @return the selected {@link StorageDevice}
     * @throws IllegalArgumentException if no eligible device is found
     * @throws NullPointerException     if {@code policy} or {@code available} is null
     */
    public StorageDevice selectDevice(StoragePolicy policy, List<StorageDevice> available) {
        Objects.requireNonNull(policy, "policy must not be null");
        Objects.requireNonNull(available, "available must not be null");

        return available.stream()
                .filter(StorageDevice::isWriteEligible)
                .max(Comparator.comparingLong(StorageDevice::availableCapacityBytes))
                .orElseThrow(() -> new IllegalArgumentException(
                        "No healthy StorageDevice available for policy '" + policy.id().value() + "'. "
                        + "Checked " + available.size() + " device(s); none were write-eligible."));
    }

    /**
     * Resolves a VirtualDevice from an EffectiveStoragePolicy and BucketRef.
     *
     * @param effectivePolicy the resolved effective storage policy
     * @param bucketRef       the bucket reference
     * @return the appropriate VirtualDevice
     */
    public VirtualDevice resolve(EffectiveStoragePolicy effectivePolicy, BucketRef bucketRef) {
        Objects.requireNonNull(effectivePolicy, "effectivePolicy must not be null");
        Objects.requireNonNull(bucketRef, "bucketRef must not be null");

        // If dedup is disabled or bypassed, return a BucketDevice
        if (effectivePolicy.dedup().isEmpty()) {
            return new VirtualDevice.BucketDevice(bucketRef, effectivePolicy);
        }

        // Dedup is enabled — build a DedupDevice with proper namespace and workflow key
        DedupConfig dedupConfig = effectivePolicy.dedup().get();
        final BucketRef ref = bucketRef;
        DedupNamespace namespace = switch (dedupConfig.scope()) {
            case GLOBAL_LEVEL -> DedupNamespace.GlobalDedupNamespace.INSTANCE;
            case BUCKET_LEVEL -> new DedupNamespace.BucketDedupNamespace(ref);
        };

        WorkflowCompatibilityKey workflowKey = WorkflowCompatibilityKey.from(effectivePolicy);
        return new VirtualDevice.DedupDevice(namespace, workflowKey);
    }
}
