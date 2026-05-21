package com.example.magrathea.objectstorage.domain;

import com.example.magrathea.objectstorage.domain.valueobject.StorageClass;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Pure JUnit test for StorageClass value object.
 */
class StorageClassTest {

    @Test
    void predefinedClasses() {
        assertEquals("STANDARD", StorageClass.STANDARD.name());
        assertEquals("STANDARD_IA", StorageClass.STANDARD_IA.name());
        assertEquals("GLACIER", StorageClass.GLACIER.name());
        assertEquals("INTELLIGENT_TIERING", StorageClass.INTELLIGENT_TIERING.name());
    }

    @Test
    void standardHasNoMinimumDuration() {
        assertEquals(0, StorageClass.STANDARD.minStorageDurationDays());
    }

    @Test
    void glacierHas90DayMinimum() {
        assertEquals(90, StorageClass.GLACIER.minStorageDurationDays());
    }

    @Test
    void intelligentTieringHasZeroDuration() {
        assertEquals(0, StorageClass.INTELLIGENT_TIERING.minStorageDurationDays());
    }

    @Test
    void customStorageClass() {
        var sc = new StorageClass("CUSTOM", "Custom Class", 0, 0.05);
        assertEquals("CUSTOM", sc.name());
        assertEquals(0.05, sc.pricePerGBMonth());
    }

    @Test
    void equality() {
        assertEquals(StorageClass.STANDARD, StorageClass.STANDARD);
        assertNotEquals(StorageClass.STANDARD, StorageClass.GLACIER);
    }
}
