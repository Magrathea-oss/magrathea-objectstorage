package com.example.magrathea.objectstorage.domain;

import com.example.magrathea.objectstorage.domain.valueobject.BucketEncryptionConfiguration;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Pure JUnit test for BucketEncryptionConfiguration value object.
 */
class BucketEncryptionConfigurationTest {

    @Test
    void createValidEncryption() {
        var config = new BucketEncryptionConfiguration("test-bucket", "rule1", "AES256", null);
        assertEquals("test-bucket", config.bucketName());
        assertEquals("AES256", config.algorithm());
        assertTrue(config.hasEncryption());
    }

    @Test
    void nullBucketNameThrows() {
        assertThrows(NullPointerException.class, () -> new BucketEncryptionConfiguration(null, null, null, null));
    }

    @Test
    void nullAlgorithmIsNotHasEncryption() {
        var config = new BucketEncryptionConfiguration("bucket", null, null, null);
        assertFalse(config.hasEncryption());
    }

    @Test
    void kmsKeyId() {
        var config = new BucketEncryptionConfiguration("bucket", null, "KMS", "arn:aws:kms:key");
        assertEquals("KMS", config.algorithm());
        assertEquals("arn:aws:kms:key", config.kmsKeyId());
    }
}
