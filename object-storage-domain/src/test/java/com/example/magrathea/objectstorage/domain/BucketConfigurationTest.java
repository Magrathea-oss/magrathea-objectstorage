package com.example.magrathea.objectstorage.domain;

import com.example.magrathea.objectstorage.domain.valueobject.BucketConfiguration;
import com.example.magrathea.objectstorage.domain.valueobject.BucketConfiguration.CorsRule;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Pure JUnit test for BucketConfiguration value object.
 */
class BucketConfigurationTest {

    @Test
    void createValid() {
        var config = new BucketConfiguration("test-bucket", null);
        assertEquals("test-bucket", config.bucketName());
    }

    @Test
    void nullBucketNameThrows() {
        assertThrows(NullPointerException.class, () -> new BucketConfiguration(null, null));
    }

    @Test
    void withCorsRules() {
        var rule = new CorsRule(List.of("*"), List.of("GET"), null, 0, null, null);
        var config = new BucketConfiguration("bucket", List.of(rule));
        assertTrue(config.hasCors());
        assertEquals(1, config.corsRules().size());
    }
}
