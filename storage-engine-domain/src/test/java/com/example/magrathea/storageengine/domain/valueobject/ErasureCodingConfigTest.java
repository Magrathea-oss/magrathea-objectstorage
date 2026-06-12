package com.example.magrathea.storageengine.domain.valueobject;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Unit tests for {@link ErasureCodingConfig}.
 * Pure JUnit 5 — no Spring, no Mockito, no reactive imports.
 */
class ErasureCodingConfigTest {

    @Test
    void of_valid_k2_m1_accepted() {
        ErasureCodingConfig ec = ErasureCodingConfig.of(2, 1);
        assertEquals(2, ec.dataBlocks());
        assertEquals(1, ec.parityBlocks());
    }

    @Test
    void of_valid_k6_m3_accepted() {
        ErasureCodingConfig ec = ErasureCodingConfig.of(6, 3);
        assertEquals(6, ec.dataBlocks());
        assertEquals(3, ec.parityBlocks());
    }

    @Test
    void dataBlocks_lessThan2_throwsIllegalArgumentException() {
        // k=1 is below the minimum of 2
        assertThrows(IllegalArgumentException.class, () -> ErasureCodingConfig.of(1, 1));
    }

    @Test
    void parityBlocks_lessThan1_throwsIllegalArgumentException() {
        // m=0 is below the minimum of 1
        assertThrows(IllegalArgumentException.class, () -> ErasureCodingConfig.of(2, 0));
    }

    @Test
    void kPlusM_over32_throwsIllegalArgumentException() {
        // k=20, m=13 → 33 > 32, must fail
        assertThrows(IllegalArgumentException.class, () -> ErasureCodingConfig.of(20, 13));
    }

    @Test
    void kPlusM_exactly32_accepted() {
        // k=20, m=12 → exactly 32, must succeed
        assertDoesNotThrow(() -> ErasureCodingConfig.of(20, 12));
        ErasureCodingConfig ec = ErasureCodingConfig.of(20, 12);
        assertEquals(32, ec.dataBlocks() + ec.parityBlocks());
    }
}
