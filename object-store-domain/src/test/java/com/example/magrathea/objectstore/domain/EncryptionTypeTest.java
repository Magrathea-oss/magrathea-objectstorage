package com.example.magrathea.objectstore.domain;

import com.example.magrathea.objectstore.domain.aggregate.EncryptionType;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Pure JUnit test for EncryptionType enum — NO Spring context.
 */
class EncryptionTypeTest {

    @Test
    void enum_hasExpectedValues() {
        assertEquals(4, EncryptionType.values().length);
        assertEquals(EncryptionType.NONE, EncryptionType.valueOf("NONE"));
        assertEquals(EncryptionType.SSE_S3, EncryptionType.valueOf("SSE_S3"));
        assertEquals(EncryptionType.SSE_KMS, EncryptionType.valueOf("SSE_KMS"));
        assertEquals(EncryptionType.SSE_C, EncryptionType.valueOf("SSE_C"));
    }

    @Test
    void none_isFirst() {
        assertEquals(EncryptionType.NONE, EncryptionType.values()[0]);
    }
}
