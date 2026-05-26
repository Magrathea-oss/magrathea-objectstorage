package com.example.magrathea.objectstore.domain;

import com.example.magrathea.objectstore.domain.valueobject.BucketMetricsConfiguration;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Pure JUnit test for BucketMetricsConfiguration value object.
 */
class BucketMetricsConfigurationTest {

    @Test
    void createValid() {
        var config = new BucketMetricsConfiguration("test-bucket", "metrics-1", "prefix");
        assertEquals("test-bucket", config.bucketName());
        assertEquals("metrics-1", config.metricsId());
        assertEquals("prefix", config.filterRule());
    }

    @Test
    void nullBucketNameThrows() {
        assertThrows(NullPointerException.class,
            () -> new BucketMetricsConfiguration(null, "metrics-1", "prefix"));
    }

    @Test
    void optionalFilterRule() {
        var config = new BucketMetricsConfiguration("bucket", "metrics-1", null);
        assertEquals("bucket", config.bucketName());
        assertEquals("metrics-1", config.metricsId());
        assertNull(config.filterRule());
    }

    @Test
    void optionalMetricsId() {
        var config = new BucketMetricsConfiguration("bucket", null, "prefix");
        assertEquals("bucket", config.bucketName());
        assertNull(config.metricsId());
        assertEquals("prefix", config.filterRule());
    }
}
