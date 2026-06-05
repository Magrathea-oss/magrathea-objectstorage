package com.example.magrathea.objectstore.domain;

import com.example.magrathea.objectstore.domain.aggregate.EncryptionConfiguration;
import com.example.magrathea.objectstore.domain.aggregate.EncryptionType;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Pure JUnit test for EncryptionConfiguration value record (aggregate package) — NO Spring context.
 */
class EncryptionConfigurationTest {

    @Test
    void none_createsWithNoEncryption() {
        var config = EncryptionConfiguration.none();
        assertEquals(EncryptionType.NONE, config.type());
        assertTrue(config.kmsKeyId().isEmpty());
        assertTrue(config.kmsEncryptionContext().isEmpty());
        assertTrue(config.algorithm().isEmpty());
    }

    @Test
    void sseS3_createsWithSseS3() {
        var config = EncryptionConfiguration.sseS3();
        assertEquals(EncryptionType.SSE_S3, config.type());
        assertTrue(config.kmsKeyId().isEmpty());
        assertTrue(config.kmsEncryptionContext().isEmpty());
        assertTrue(config.algorithm().isEmpty());
    }

    @Test
    void sseKms_createsWithKmsKey() {
        var config = EncryptionConfiguration.sseKms("kms-key-1", null);
        assertEquals(EncryptionType.SSE_KMS, config.type());
        assertTrue(config.kmsKeyId().isPresent());
        assertEquals("kms-key-1", config.kmsKeyId().get());
        assertTrue(config.kmsEncryptionContext().isEmpty());
    }

    @Test
    void sseKms_withEncryptionContext() {
        var config = EncryptionConfiguration.sseKms("kms-key-1", "context-123");
        assertEquals(EncryptionType.SSE_KMS, config.type());
        assertTrue(config.kmsKeyId().isPresent());
        assertEquals("kms-key-1", config.kmsKeyId().get());
        assertTrue(config.kmsEncryptionContext().isPresent());
        assertEquals("context-123", config.kmsEncryptionContext().get());
    }

    @Test
    void sseKms_nullKeyId_throws() {
        assertThrows(NullPointerException.class,
            () -> EncryptionConfiguration.sseKms(null, null));
    }

    @Test
    void sseC_createsWithAlgorithm() {
        var config = EncryptionConfiguration.sseC("AES256");
        assertEquals(EncryptionType.SSE_C, config.type());
        assertTrue(config.algorithm().isPresent());
        assertEquals("AES256", config.algorithm().get());
        assertTrue(config.kmsKeyId().isEmpty());
        assertTrue(config.kmsEncryptionContext().isEmpty());
    }

    @Test
    void sseC_nullAlgorithm_throws() {
        assertThrows(NullPointerException.class,
            () -> EncryptionConfiguration.sseC(null));
    }

    @Test
    void recordsEquals() {
        var c1 = EncryptionConfiguration.sseS3();
        var c2 = EncryptionConfiguration.sseS3();
        assertEquals(c1, c2);
        assertEquals(c1.hashCode(), c2.hashCode());
    }

    @Test
    void none_and_sseS3_areDifferent() {
        var none = EncryptionConfiguration.none();
        var sseS3 = EncryptionConfiguration.sseS3();
        assertNotEquals(none, sseS3);
    }

    @Test
    void recordsToString() {
        var config = EncryptionConfiguration.none();
        assertTrue(config.toString().contains("NONE"));
    }
}
