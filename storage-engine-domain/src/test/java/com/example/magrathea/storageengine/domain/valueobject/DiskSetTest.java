package com.example.magrathea.storageengine.domain.valueobject;

import com.example.magrathea.storageengine.domain.TestFixtures;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Unit tests for {@link DiskSet}.
 * Pure JUnit 5 — no Spring, no Mockito, no reactive imports.
 */
class DiskSetTest {

    // -------------------------------------------------------------------------
    // Construction invariants
    // -------------------------------------------------------------------------

    @Test
    void diskSet_valid_singleDevice_accepted() {
        DiskSet set = DiskSet.of(
                "rack1-set",
                FailureDomain.RACK,
                List.of(StorageDeviceId.of("disk-001")));
        assertNotNull(set);
        assertEquals("rack1-set", set.name());
        assertEquals(FailureDomain.RACK, set.failureDomain());
        assertEquals(1, set.size());
    }

    @Test
    void diskSet_emptyDeviceList_throwsIllegalArgumentException() {
        assertThrows(IllegalArgumentException.class, () ->
                DiskSet.of("empty-set", FailureDomain.HOST, List.of()),
                "DiskSet with 0 devices must throw IllegalArgumentException");
    }

    @Test
    void diskSet_nullName_throwsNullPointerException() {
        assertThrows(NullPointerException.class, () ->
                DiskSet.of(null, FailureDomain.HOST, List.of(StorageDeviceId.of("disk-001"))));
    }

    @Test
    void diskSet_blankName_throwsIllegalArgumentException() {
        assertThrows(IllegalArgumentException.class, () ->
                DiskSet.of("   ", FailureDomain.HOST, List.of(StorageDeviceId.of("disk-001"))));
    }

    @Test
    void diskSet_nullFailureDomain_throwsNullPointerException() {
        assertThrows(NullPointerException.class, () ->
                DiskSet.of("set1", null, List.of(StorageDeviceId.of("disk-001"))));
    }

    @Test
    void diskSet_nullDeviceList_throwsNullPointerException() {
        assertThrows(NullPointerException.class, () ->
                DiskSet.of("set1", FailureDomain.DISK, null));
    }

    // -------------------------------------------------------------------------
    // Size and membership
    // -------------------------------------------------------------------------

    @Test
    void diskSet_multipleDevices_sizeMatchesInput() {
        DiskSet set = TestFixtures.aDiskSet();
        assertEquals(2, set.size(), "aDiskSet() fixture must contain 2 devices");
    }

    @Test
    void diskSet_contains_existingDevice_returnsTrue() {
        StorageDeviceId id = StorageDeviceId.of("disk-sda");
        DiskSet set = DiskSet.of("host-set", FailureDomain.HOST, List.of(id));
        assertTrue(set.contains(id));
    }

    @Test
    void diskSet_contains_nonMemberDevice_returnsFalse() {
        DiskSet set = DiskSet.of(
                "host-set", FailureDomain.HOST,
                List.of(StorageDeviceId.of("disk-sda")));
        assertFalse(set.contains(StorageDeviceId.of("disk-sdb")));
    }

    // -------------------------------------------------------------------------
    // Collection immutability
    // -------------------------------------------------------------------------

    @Test
    void diskSet_devices_returnedListIsImmutable() {
        DiskSet set = TestFixtures.aDiskSet();
        assertThrows(UnsupportedOperationException.class, () ->
                set.devices().add(StorageDeviceId.of("disk-extra")),
                "devices() must return an immutable list");
    }

    @Test
    void diskSet_passedMutableList_changesDoNotAffectSet() {
        List<StorageDeviceId> mutable = new ArrayList<>();
        mutable.add(StorageDeviceId.of("disk-a"));
        DiskSet set = DiskSet.of("mutable-test-set", FailureDomain.DISK, mutable);

        // Mutate after construction — set must be unchanged.
        mutable.add(StorageDeviceId.of("disk-b"));

        assertEquals(1, set.size(),
                "DiskSet size must not change after the source list is mutated");
    }

    // -------------------------------------------------------------------------
    // Failure domain semantics
    // -------------------------------------------------------------------------

    @Test
    void diskSet_failureDomain_rack() {
        DiskSet set = DiskSet.of(
                "rack-1-set", FailureDomain.RACK,
                List.of(StorageDeviceId.of("disk-rack1-sda"),
                        StorageDeviceId.of("disk-rack1-sdb"),
                        StorageDeviceId.of("disk-rack1-sdc")));
        assertEquals(FailureDomain.RACK, set.failureDomain());
        assertEquals(3, set.size());
    }

    @Test
    void diskSet_failureDomain_disk_singleDevice() {
        DiskSet set = DiskSet.of(
                "disk-set", FailureDomain.DISK,
                List.of(StorageDeviceId.of("disk-nvme0")));
        assertEquals(FailureDomain.DISK, set.failureDomain());
    }
}
