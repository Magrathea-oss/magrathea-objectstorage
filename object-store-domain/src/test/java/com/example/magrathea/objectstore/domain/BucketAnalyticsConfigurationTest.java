package com.example.magrathea.objectstore.domain;

import com.example.magrathea.objectstore.domain.valueobject.BucketAnalyticsConfiguration;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Pure JUnit test for BucketAnalyticsConfiguration value object.
 */
class BucketAnalyticsConfigurationTest {

    @Test
    void createValid() {
        var config = new BucketAnalyticsConfiguration("test-bucket", "analytics-1", "prefix");
        assertEquals("test-bucket", config.bucketName());
        assertEquals("analytics-1", config.analyticsId());
        assertEquals("prefix", config.filterRule());
    }

    @Test
    void nullBucketNameThrows() {
        assertThrows(NullPointerException.class,
            () -> new BucketAnalyticsConfiguration(null, "analytics-1", "prefix"));
    }

    @Test
    void optionalFilterRule() {
        var config = new BucketAnalyticsConfiguration("bucket", "analytics-1", null);
        assertEquals("bucket", config.bucketName());
        assertEquals("analytics-1", config.analyticsId());
        assertNull(config.filterRule());
    }

    @Test
    void optionalAnalyticsId() {
        var config = new BucketAnalyticsConfiguration("bucket", null, "prefix");
        assertEquals("bucket", config.bucketName());
        assertNull(config.analyticsId());
        assertEquals("prefix", config.filterRule());
    }
}
