package com.example.magrathea.objectstore.domain;

import com.example.magrathea.objectstore.domain.valueobject.PublicAccessBlockConfiguration;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Pure JUnit test for PublicAccessBlockConfiguration value object.
 */
class PublicAccessBlockConfigurationTest {

    @Test
    void createValid() {
        var config = new PublicAccessBlockConfiguration("test-bucket", true, false, true, false);
        assertEquals("test-bucket", config.bucketName());
        assertTrue(config.blockPublicAcls());
        assertFalse(config.ignorePublicAcls());
    }

    @Test
    void nullBucketNameThrows() {
        assertThrows(NullPointerException.class, () -> new PublicAccessBlockConfiguration(null, false, false, false, false));
    }

    @Test
    void allBlocked() {
        var config = new PublicAccessBlockConfiguration("bucket", true, true, true, true);
        assertTrue(config.restrictPublicBuckets());
    }
}
