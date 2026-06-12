package com.example.magrathea.storageengine.domain.valueobject;

import com.example.magrathea.storageengine.domain.TestFixtures;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Unit tests for {@link StorageDevice}, {@link StorageDeviceId}, and
 * {@link DeviceHealth} transition rules.
 * Pure JUnit 5 — no Spring, no Mockito, no reactive imports.
 */
class StorageDeviceTest {

    // -------------------------------------------------------------------------
    // StorageDeviceId invariants
    // -------------------------------------------------------------------------

    @Test
    void storageDeviceId_null_throwsNullPointerException() {
        assertThrows(NullPointerException.class, () -> StorageDeviceId.of(null));
    }

    @Test
    void storageDeviceId_blank_throwsIllegalArgumentException() {
        assertThrows(IllegalArgumentException.class, () -> StorageDeviceId.of("   "));
    }

    @Test
    void storageDeviceId_empty_throwsIllegalArgumentException() {
        assertThrows(IllegalArgumentException.class, () -> StorageDeviceId.of(""));
    }

    @Test
    void storageDeviceId_valid_returnsInstance() {
        StorageDeviceId id = StorageDeviceId.of("disk-rack1-host2-sda");
        assertNotNull(id);
        assertEquals("disk-rack1-host2-sda", id.value());
    }

    @Test
    void storageDeviceId_equals_sameValue() {
        StorageDeviceId a = StorageDeviceId.of("disk-001");
        StorageDeviceId b = StorageDeviceId.of("disk-001");
        assertEquals(a, b);
        assertEquals(a.hashCode(), b.hashCode());
    }

    @Test
    void storageDeviceId_equals_differentValue() {
        assertNotEquals(StorageDeviceId.of("disk-001"), StorageDeviceId.of("disk-002"));
    }

    // -------------------------------------------------------------------------
    // StorageDevice construction invariants
    // -------------------------------------------------------------------------

    @Test
    void storageDevice_create_withValidArguments_isHealthy() {
        StorageDevice device = StorageDevice.create(
                StorageDeviceId.of("disk-sda"), "/data/disk0", 1_000_000L);
        assertNotNull(device);
        assertEquals(DeviceHealth.HEALTHY, device.health());
        assertEquals(1_000_000L, device.totalCapacityBytes());
        assertEquals(1_000_000L, device.availableCapacityBytes(),
                "create() must set availableCapacityBytes == totalCapacityBytes");
    }

    @Test
    void storageDevice_nullId_throwsNullPointerException() {
        assertThrows(NullPointerException.class, () ->
                StorageDevice.create(null, "/data/disk0", 1_000_000L));
    }

    @Test
    void storageDevice_nullPath_throwsNullPointerException() {
        assertThrows(NullPointerException.class, () ->
                StorageDevice.create(StorageDeviceId.of("disk-x"), null, 1_000_000L));
    }

    @Test
    void storageDevice_blankPath_throwsIllegalArgumentException() {
        assertThrows(IllegalArgumentException.class, () ->
                StorageDevice.create(StorageDeviceId.of("disk-x"), "   ", 1_000_000L));
    }

    @Test
    void storageDevice_negativeCapacity_throwsIllegalArgumentException() {
        assertThrows(IllegalArgumentException.class, () ->
                StorageDevice.create(StorageDeviceId.of("disk-x"), "/data/x", -1L));
    }

    @Test
    void storageDevice_availableGreaterThanTotal_throwsIllegalArgumentException() {
        assertThrows(IllegalArgumentException.class, () ->
                StorageDevice.restore(
                        StorageDeviceId.of("disk-x"), "/data/x",
                        500L, 1000L, DeviceHealth.HEALTHY));
    }

    @Test
    void storageDevice_zeroCapacity_isAccepted() {
        // Zero capacity is technically valid (device exists but is full or empty).
        assertDoesNotThrow(() ->
                StorageDevice.create(StorageDeviceId.of("disk-zero"), "/data/zero", 0L));
    }

    @Test
    void storageDevice_storagePath_isString_notJavaNioPath() {
        // Confirm the domain uses a String, not java.nio.file.Path, for the path.
        StorageDevice device = TestFixtures.aHealthyStorageDevice();
        assertInstanceOf(String.class, device.storagePath(),
                "storagePath must be a domain String, not java.nio.file.Path");
    }

    // -------------------------------------------------------------------------
    // Health eligibility
    // -------------------------------------------------------------------------

    @Test
    void healthyDevice_isWriteEligible() {
        StorageDevice device = TestFixtures.aHealthyStorageDevice();
        assertTrue(device.isWriteEligible());
    }

    @Test
    void healthyDevice_isReadEligible() {
        StorageDevice device = TestFixtures.aHealthyStorageDevice();
        assertTrue(device.isReadEligible());
    }

    @Test
    void degradedDevice_isNotWriteEligible() {
        StorageDevice device = TestFixtures.aDegradedStorageDevice();
        assertFalse(device.isWriteEligible(),
                "A DEGRADED device must not accept new writes");
    }

    @Test
    void degradedDevice_isReadEligible() {
        StorageDevice device = TestFixtures.aDegradedStorageDevice();
        assertTrue(device.isReadEligible(),
                "A DEGRADED device must still serve reads");
    }

    @Test
    void unavailableDevice_isNotWriteEligible() {
        StorageDevice device = TestFixtures.anUnavailableStorageDevice();
        assertFalse(device.isWriteEligible());
    }

    @Test
    void unavailableDevice_isNotReadEligible() {
        StorageDevice device = TestFixtures.anUnavailableStorageDevice();
        assertFalse(device.isReadEligible());
    }

    // -------------------------------------------------------------------------
    // Health transitions via withHealth()
    // -------------------------------------------------------------------------

    @Test
    void withHealth_healthy_to_degraded_isPermitted() {
        StorageDevice device = TestFixtures.aHealthyStorageDevice();
        StorageDevice degraded = device.withHealth(DeviceHealth.DEGRADED);
        assertEquals(DeviceHealth.DEGRADED, degraded.health());
        // Original device must be unchanged (immutability).
        assertEquals(DeviceHealth.HEALTHY, device.health());
    }

    @Test
    void withHealth_healthy_to_unavailable_isPermitted() {
        StorageDevice device = TestFixtures.aHealthyStorageDevice();
        StorageDevice unavailable = device.withHealth(DeviceHealth.UNAVAILABLE);
        assertEquals(DeviceHealth.UNAVAILABLE, unavailable.health());
    }

    @Test
    void withHealth_degraded_to_unavailable_isPermitted() {
        StorageDevice degraded = TestFixtures.aDegradedStorageDevice();
        StorageDevice unavailable = degraded.withHealth(DeviceHealth.UNAVAILABLE);
        assertEquals(DeviceHealth.UNAVAILABLE, unavailable.health());
    }

    @Test
    void withHealth_degraded_to_healthy_isPermitted() {
        StorageDevice degraded = TestFixtures.aDegradedStorageDevice();
        StorageDevice recovered = degraded.withHealth(DeviceHealth.HEALTHY);
        assertEquals(DeviceHealth.HEALTHY, recovered.health());
    }

    @Test
    void withHealth_unavailable_to_healthy_isPermitted() {
        StorageDevice unavailable = TestFixtures.anUnavailableStorageDevice();
        StorageDevice recovered = unavailable.withHealth(DeviceHealth.HEALTHY);
        assertEquals(DeviceHealth.HEALTHY, recovered.health());
    }

    @Test
    void withHealth_unavailable_to_degraded_isForbidden() {
        StorageDevice unavailable = TestFixtures.anUnavailableStorageDevice();
        assertThrows(IllegalArgumentException.class, () ->
                unavailable.withHealth(DeviceHealth.DEGRADED),
                "UNAVAILABLE → DEGRADED must be rejected; recovery goes through HEALTHY");
    }

    @Test
    void withHealth_healthy_to_healthy_isForbidden() {
        StorageDevice device = TestFixtures.aHealthyStorageDevice();
        assertThrows(IllegalArgumentException.class, () ->
                device.withHealth(DeviceHealth.HEALTHY),
                "Self-transition HEALTHY → HEALTHY must be rejected");
    }

    @Test
    void withHealth_null_throwsNullPointerException() {
        StorageDevice device = TestFixtures.aHealthyStorageDevice();
        assertThrows(NullPointerException.class, () -> device.withHealth(null));
    }

    @Test
    void withHealth_returnsNewInstance_originalIsUnchanged() {
        StorageDevice original = TestFixtures.aHealthyStorageDevice();
        StorageDevice updated = original.withHealth(DeviceHealth.DEGRADED);

        assertNotSame(original, updated, "withHealth must return a new instance");
        assertEquals(DeviceHealth.HEALTHY, original.health(), "Original must remain HEALTHY");
        assertEquals(DeviceHealth.DEGRADED, updated.health());
    }
}
