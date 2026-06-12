package com.example.magrathea.storageengine.domain.service;

import com.example.magrathea.storageengine.domain.TestFixtures;
import com.example.magrathea.storageengine.domain.valueobject.BucketRef;
import com.example.magrathea.storageengine.domain.valueobject.DedupNamespace;
import com.example.magrathea.storageengine.domain.valueobject.DeviceHealth;
import com.example.magrathea.storageengine.domain.valueobject.EffectiveStoragePolicy;
import com.example.magrathea.storageengine.domain.valueobject.StorageDevice;
import com.example.magrathea.storageengine.domain.valueobject.StorageDeviceId;
import com.example.magrathea.storageengine.domain.valueobject.VirtualDevice;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;

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

    // -------------------------------------------------------------------------
    // Phase 3: selectDevice() — physical StorageDevice selection
    // -------------------------------------------------------------------------

    @Test
    void selectDevice_singleHealthyDevice_returnsIt() {
        StorageDevice healthy = TestFixtures.aHealthyStorageDevice();
        StorageDevice selected = resolver.selectDevice(
                TestFixtures.aStoragePolicy(), List.of(healthy));
        assertEquals(healthy.id(), selected.id());
    }

    @Test
    void selectDevice_prefersHigherAvailableCapacity() {
        // Two healthy devices: one with more available capacity must be selected.
        StorageDevice small = StorageDevice.create(
                StorageDeviceId.of("disk-small"), "/data/small", 100_000L);
        StorageDevice large = StorageDevice.create(
                StorageDeviceId.of("disk-large"), "/data/large", 1_000_000L);

        StorageDevice selected = resolver.selectDevice(
                TestFixtures.aStoragePolicy(), List.of(small, large));

        assertEquals(large.id(), selected.id(),
                "selectDevice must prefer the device with more available capacity");
    }

    @Test
    void selectDevice_skipsDegradedDevices() {
        // A degraded device must not be selected for new writes.
        StorageDevice degraded = TestFixtures.aDegradedStorageDevice();
        StorageDevice healthy = TestFixtures.aHealthyStorageDevice();

        StorageDevice selected = resolver.selectDevice(
                TestFixtures.aStoragePolicy(), List.of(degraded, healthy));

        assertEquals(healthy.id(), selected.id(),
                "selectDevice must skip DEGRADED devices");
    }

    @Test
    void selectDevice_skipsUnavailableDevices() {
        // An unavailable device must not be selected.
        StorageDevice unavailable = TestFixtures.anUnavailableStorageDevice();
        StorageDevice healthy = TestFixtures.aHealthyStorageDevice();

        StorageDevice selected = resolver.selectDevice(
                TestFixtures.aStoragePolicy(), List.of(unavailable, healthy));

        assertEquals(healthy.id(), selected.id(),
                "selectDevice must skip UNAVAILABLE devices");
    }

    @Test
    void selectDevice_allDevicesUnavailable_throwsIllegalArgumentException() {
        // If no healthy device exists, the resolver must fail with a clear error.
        List<StorageDevice> noHealthy = List.of(
                TestFixtures.aDegradedStorageDevice(),
                TestFixtures.anUnavailableStorageDevice());

        assertThrows(IllegalArgumentException.class, () ->
                resolver.selectDevice(TestFixtures.aStoragePolicy(), noHealthy),
                "selectDevice must throw when no write-eligible device is available");
    }

    @Test
    void selectDevice_emptyList_throwsIllegalArgumentException() {
        assertThrows(IllegalArgumentException.class, () ->
                resolver.selectDevice(TestFixtures.aStoragePolicy(), List.of()));
    }

    @Test
    void selectDevice_nullPolicy_throwsNullPointerException() {
        assertThrows(NullPointerException.class, () ->
                resolver.selectDevice(null, List.of(TestFixtures.aHealthyStorageDevice())));
    }

    @Test
    void selectDevice_nullAvailableList_throwsNullPointerException() {
        assertThrows(NullPointerException.class, () ->
                resolver.selectDevice(TestFixtures.aStoragePolicy(), null));
    }
}
