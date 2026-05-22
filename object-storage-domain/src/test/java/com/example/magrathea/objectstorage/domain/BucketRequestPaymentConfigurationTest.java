package com.example.magrathea.objectstorage.domain;

import com.example.magrathea.objectstorage.domain.valueobject.BucketRequestPaymentConfiguration;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Pure JUnit test for BucketRequestPaymentConfiguration value object.
 */
class BucketRequestPaymentConfigurationTest {

    @Test
    void createValid() {
        var config = new BucketRequestPaymentConfiguration("test-bucket", "Requester");
        assertEquals("test-bucket", config.bucketName());
        assertEquals("Requester", config.payer());
    }

    @Test
    void nullBucketNameThrows() {
        assertThrows(NullPointerException.class, () -> new BucketRequestPaymentConfiguration(null, null));
    }

    @Test
    void bucketOwnerPayer() {
        var config = new BucketRequestPaymentConfiguration("bucket", "BucketOwner");
        assertEquals("BucketOwner", config.payer());
    }
}
