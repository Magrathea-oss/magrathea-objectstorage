package com.example.magrathea.storageengine.domain.service;

import com.example.magrathea.storageengine.domain.valueobject.BucketRef;
import com.example.magrathea.storageengine.domain.valueobject.DedupConfig;
import com.example.magrathea.storageengine.domain.valueobject.DedupNamespace;
import com.example.magrathea.storageengine.domain.valueobject.EffectiveStoragePolicy;
import com.example.magrathea.storageengine.domain.valueobject.VirtualDevice;
import com.example.magrathea.storageengine.domain.valueobject.WorkflowCompatibilityKey;

import java.util.Objects;

/**
 * Pure domain service — resolves an EffectiveStoragePolicy into a VirtualDevice.
 * Returns BucketDevice if dedup is disabled/bypassed, DedupDevice otherwise.
 * No framework dependencies, no I/O, no reactive types.
 */
public class VirtualDeviceResolver {

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
        DedupNamespace namespace;
        switch (dedupConfig.scope()) {
            case GLOBAL_LEVEL -> namespace = DedupNamespace.GlobalDedupNamespace.INSTANCE;
            case BUCKET_LEVEL -> namespace = new DedupNamespace.BucketDedupNamespace(bucketRef);
            default -> throw new IllegalStateException("Unknown dedup scope: " + dedupConfig.scope());
        }

        WorkflowCompatibilityKey workflowKey = WorkflowCompatibilityKey.from(effectivePolicy);
        return new VirtualDevice.DedupDevice(namespace, workflowKey);
    }
}
