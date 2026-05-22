package com.example.magrathea.objectstorage.domain;

import com.example.magrathea.objectstorage.domain.valueobject.BucketAccelerateConfiguration;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Pure JUnit test for BucketAccelerateConfiguration value object.
 */
class BucketAccelerateConfigurationTest {

    @Test
    void createValid() {
        var config = new BucketAccelerateConfiguration("test-bucket", "Enabled");
        assertEquals("test-bucket", config.bucketName());
        assertEquals("Enabled", config.status());
    }

    @Test
    void nullBucketNameThrows() {
        assertThrows(NullPointerException.class, () -> new BucketAccelerateConfiguration(null, "Enabled"));
    }

    @Test
    void suspendedStatus() {
        var config = new BucketAccelerateConfiguration("bucket", "Suspended");
        assertEquals("Suspended", config.status());
    }
}
