package com.example.magrathea.storageengine.domain.valueobject;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Unit tests for {@link ReplicationConfig}.
 * Pure JUnit 5 — no Spring, no Mockito, no reactive imports.
 */
class ReplicationConfigTest {

    @Test
    void of_factor1_accepted() {
        ReplicationConfig config = ReplicationConfig.of(1);
        assertEquals(1, config.factor());
    }

    @Test
    void of_factor3_accepted() {
        ReplicationConfig config = ReplicationConfig.of(3);
        assertEquals(3, config.factor());
    }

    @Test
    void factor_zero_throwsIllegalArgumentException() {
        assertThrows(IllegalArgumentException.class, () -> ReplicationConfig.of(0));
    }

    @Test
    void factor_negative_throwsIllegalArgumentException() {
        assertThrows(IllegalArgumentException.class, () -> ReplicationConfig.of(-1));
    }
}
