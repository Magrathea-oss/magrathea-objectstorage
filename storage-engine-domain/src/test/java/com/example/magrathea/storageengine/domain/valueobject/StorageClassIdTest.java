package com.example.magrathea.storageengine.domain.valueobject;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Unit tests for {@link StorageClassId}.
 * Pure JUnit 5 — no Spring, no Mockito, no reactive imports.
 */
class StorageClassIdTest {

    @Test
    void of_validValue_returnsInstance() {
        StorageClassId id = StorageClassId.of("GLACIER");
        assertNotNull(id);
        assertEquals("GLACIER", id.value());
    }

    @Test
    void of_null_throwsNullPointerException() {
        assertThrows(NullPointerException.class, () -> StorageClassId.of(null));
    }

    @Test
    void of_blank_throwsIllegalArgumentException() {
        assertThrows(IllegalArgumentException.class, () -> StorageClassId.of("   "));
    }

    @Test
    void of_emptyString_throwsIllegalArgumentException() {
        assertThrows(IllegalArgumentException.class, () -> StorageClassId.of(""));
    }

    @Test
    void STANDARD_constant_hasExpectedValue() {
        assertEquals("STANDARD", StorageClassId.STANDARD.value());
    }

    @Test
    void MINIO_STANDARD_constant_hasExpectedValue() {
        assertEquals("MINIO_STANDARD", StorageClassId.MINIO_STANDARD.value());
    }

    @Test
    void equals_sameValue_isEqual() {
        StorageClassId a = StorageClassId.of("REDUCED_REDUNDANCY");
        StorageClassId b = StorageClassId.of("REDUCED_REDUNDANCY");
        assertEquals(a, b);
        assertEquals(a.hashCode(), b.hashCode());
    }

    @Test
    void equals_differentValue_isNotEqual() {
        StorageClassId a = StorageClassId.of("STANDARD");
        StorageClassId b = StorageClassId.of("GLACIER");
        assertNotEquals(a, b);
    }
}
