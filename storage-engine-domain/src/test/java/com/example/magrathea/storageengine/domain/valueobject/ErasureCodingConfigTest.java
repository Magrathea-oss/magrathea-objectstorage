package com.example.magrathea.storageengine.domain.valueobject;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

/** Narrow value-object checks complementing the fixed-geometry Cucumber requirement. */
class ErasureCodingConfigTest {

    @Test
    void fixedEc42IsAccepted() {
        ErasureCodingConfig ec = ErasureCodingConfig.of(4, 2);
        assertEquals(ErasureCodingConfig.FIXED_DATA_BLOCKS, ec.dataBlocks());
        assertEquals(ErasureCodingConfig.FIXED_PARITY_BLOCKS, ec.parityBlocks());
        assertEquals(ErasureCodingConfig.FIXED_TOTAL_BLOCKS,
                ec.dataBlocks() + ec.parityBlocks());
    }

    @Test
    void everyOtherGeometryIsRejectedUntilParameterizedValidationExists() {
        assertThrows(IllegalArgumentException.class, () -> ErasureCodingConfig.of(2, 1));
        assertThrows(IllegalArgumentException.class, () -> ErasureCodingConfig.of(6, 3));
        assertThrows(IllegalArgumentException.class, () -> ErasureCodingConfig.of(20, 12));
        assertThrows(IllegalArgumentException.class, () -> ErasureCodingConfig.of(4, 1));
        assertThrows(IllegalArgumentException.class, () -> ErasureCodingConfig.of(3, 2));
    }
}
