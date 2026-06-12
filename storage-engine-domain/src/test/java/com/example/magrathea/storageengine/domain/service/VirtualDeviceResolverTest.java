package com.example.magrathea.storageengine.domain.service;

import com.example.magrathea.storageengine.domain.TestFixtures;
import com.example.magrathea.storageengine.domain.valueobject.BucketRef;
import com.example.magrathea.storageengine.domain.valueobject.DedupNamespace;
import com.example.magrathea.storageengine.domain.valueobject.EffectiveStoragePolicy;
import com.example.magrathea.storageengine.domain.valueobject.VirtualDevice;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Unit tests for {@link VirtualDeviceResolver}.
 * Pure JUnit 5 — no Spring, no Mockito, no reactive imports.
 */
class VirtualDeviceResolverTest {

    private VirtualDeviceResolver resolver;
    private BucketRef bucket;

    @BeforeEach
    void setUp() {
        resolver = new VirtualDeviceResolver();
        bucket = TestFixtures.aBucketRef();
    }

    @Test
    void resolve_noDedupEffectivePolicy_returnsBucketDevice() {
        EffectiveStoragePolicy policy = TestFixtures.aMinimalEffectivePolicy(bucket);

        VirtualDevice device = resolver.resolve(policy, bucket);

        assertInstanceOf(VirtualDevice.BucketDevice.class, device,
                "No dedup → must return a BucketDevice");
    }

    @Test
    void resolve_dedupEnabled_bucketScope_returnsBucketDedupDevice() {
        EffectiveStoragePolicy policy = TestFixtures.anEffectivePolicyWithBucketDedup(bucket);

        VirtualDevice device = resolver.resolve(policy, bucket);

        assertInstanceOf(VirtualDevice.DedupDevice.class, device,
                "Bucket-scoped dedup → must return a DedupDevice");
        VirtualDevice.DedupDevice dedupDevice = (VirtualDevice.DedupDevice) device;
        assertInstanceOf(DedupNamespace.BucketDedupNamespace.class, dedupDevice.namespace(),
                "Bucket-scope dedup must use a BucketDedupNamespace");
    }

    @Test
    void resolve_dedupEnabled_globalScope_returnsDedupDeviceWithGlobalNamespace() {
        EffectiveStoragePolicy policy = TestFixtures.anEffectivePolicyWithGlobalDedup(bucket);

        VirtualDevice device = resolver.resolve(policy, bucket);

        assertInstanceOf(VirtualDevice.DedupDevice.class, device,
                "Global dedup → must return a DedupDevice");
        VirtualDevice.DedupDevice dedupDevice = (VirtualDevice.DedupDevice) device;
        assertInstanceOf(DedupNamespace.GlobalDedupNamespace.class, dedupDevice.namespace(),
                "Global-scope dedup must use a GlobalDedupNamespace");
    }

    @Test
    void resolve_nullPolicy_throwsNullPointerException() {
        assertThrows(NullPointerException.class, () -> resolver.resolve(null, bucket));
    }

    @Test
    void resolve_nullBucketRef_throwsNullPointerException() {
        EffectiveStoragePolicy policy = TestFixtures.aMinimalEffectivePolicy(bucket);
        assertThrows(NullPointerException.class, () -> resolver.resolve(policy, null));
    }
}
