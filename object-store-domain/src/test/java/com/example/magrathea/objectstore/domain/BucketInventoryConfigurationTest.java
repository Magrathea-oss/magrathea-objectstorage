package com.example.magrathea.objectstore.domain;

import com.example.magrathea.objectstore.domain.valueobject.BucketInventoryConfiguration;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Pure JUnit test for BucketInventoryConfiguration value object.
 */
class BucketInventoryConfigurationTest {

    @Test
    void createValid() {
        var config = new BucketInventoryConfiguration(
            "test-bucket", "inv-1", "CSV", "Daily", true);
        assertEquals("test-bucket", config.bucketName());
        assertEquals("inv-1", config.inventoryId());
        assertEquals("CSV", config.format());
        assertEquals("Daily", config.frequency());
        assertTrue(config.enabled());
    }

    @Test
    void nullBucketNameThrows() {
        assertThrows(NullPointerException.class,
            () -> new BucketInventoryConfiguration(null, "inv-1", "CSV", "Daily", true));
    }

    @Test
    void disabledInventory() {
        var config = new BucketInventoryConfiguration(
            "bucket", "inv-1", "Parquet", "Weekly", false);
        assertEquals("bucket", config.bucketName());
        assertEquals("Parquet", config.format());
        assertEquals("Weekly", config.frequency());
        assertFalse(config.enabled());
    }

    @Test
    void nullOptionalFields() {
        var config = new BucketInventoryConfiguration(
            "bucket", null, null, null, true);
        assertEquals("bucket", config.bucketName());
        assertNull(config.inventoryId());
        assertNull(config.format());
        assertNull(config.frequency());
        assertTrue(config.enabled());
    }
}
