package com.example.magrathea.objectstore.domain;

import com.example.magrathea.objectstore.domain.valueobject.BucketLoggingConfiguration;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Pure JUnit test for BucketLoggingConfiguration value object.
 */
class BucketLoggingConfigurationTest {

    @Test
    void createValid() {
        var config = new BucketLoggingConfiguration("test-bucket", "log-bucket", "prefix/");
        assertEquals("test-bucket", config.bucketName());
        assertEquals("log-bucket", config.targetBucket());
        assertEquals("prefix/", config.targetPrefix());
        assertTrue(config.hasLogging());
    }

    @Test
    void nullBucketNameThrows() {
        assertThrows(NullPointerException.class, () -> new BucketLoggingConfiguration(null, null, null));
    }

    @Test
    void noLoggingWhenEmpty() {
        var config = new BucketLoggingConfiguration("bucket", null, null);
        assertFalse(config.hasLogging());
    }
}
