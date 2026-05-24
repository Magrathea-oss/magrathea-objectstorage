package com.example.magrathea.objectstorage.domain;

import com.example.magrathea.objectstorage.domain.valueobject.BucketIntelligentTieringConfiguration;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Pure JUnit test for BucketIntelligentTieringConfiguration value object.
 */
class BucketIntelligentTieringConfigurationTest {

    @Test
    void createValid() {
        var config = new BucketIntelligentTieringConfiguration(
            "test-bucket", "tiering-1", "MONITOR", "ACTIVE");
        assertEquals("test-bucket", config.bucketName());
        assertEquals("tiering-1", config.tieringId());
        assertEquals("MONITOR", config.tieringPolicy());
        assertEquals("ACTIVE", config.autoTieringStatus());
    }

    @Test
    void nullBucketNameThrows() {
        assertThrows(NullPointerException.class,
            () -> new BucketIntelligentTieringConfiguration(null, "tiering-1", "MONITOR", "ACTIVE"));
    }

    @Test
    void differentTieringPolicy() {
        var config = new BucketIntelligentTieringConfiguration(
            "bucket", "tiering-2", "ARCHIVE", "DISABLED");
        assertEquals("ARCHIVE", config.tieringPolicy());
        assertEquals("DISABLED", config.autoTieringStatus());
    }

    @Test
    void nullOptionalFields() {
        var config = new BucketIntelligentTieringConfiguration("bucket", null, null, null);
        assertEquals("bucket", config.bucketName());
        assertNull(config.tieringId());
        assertNull(config.tieringPolicy());
        assertNull(config.autoTieringStatus());
    }
}
