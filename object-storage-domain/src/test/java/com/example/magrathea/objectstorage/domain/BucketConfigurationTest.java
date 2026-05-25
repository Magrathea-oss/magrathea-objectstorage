package com.example.magrathea.objectstorage.domain;

import com.example.magrathea.objectstorage.domain.aggregate.Bucket;
import com.example.magrathea.objectstorage.domain.aggregate.Bucket.Configuration.CorsRule;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Pure JUnit test for Bucket.Configuration value object.
 */
class BucketConfigurationTest {

    @Test
    void createEmptyConfig() {
        var config = new Bucket.Configuration(null);
        assertNull(config.corsRules());
    }

    @Test
    void createWithEmptyCorsRules() {
        var config = new Bucket.Configuration(List.of());
        assertFalse(config.hasCors());
    }

    @Test
    void withCorsRules() {
        var rule = new CorsRule(List.of("*"), List.of("GET"), null, 0, null, null);
        var config = new Bucket.Configuration(List.of(rule));
        assertTrue(config.hasCors());
        assertEquals(1, config.corsRules().size());
        assertEquals("*", config.corsRules().get(0).allowedOrigins().get(0));
        assertEquals("GET", config.corsRules().get(0).allowedMethods().get(0));
    }

    @Test
    void corsRule_nullAllowedOrigins_throws() {
        assertThrows(NullPointerException.class,
            () -> new CorsRule(null, List.of("GET"), null, 0, null, null));
    }

    @Test
    void corsRule_nullAllowedMethods_throws() {
        assertThrows(NullPointerException.class,
            () -> new CorsRule(List.of("*"), null, null, 0, null, null));
    }

    @Test
    void config_immutableCorsRules() {
        var mutableRules = new java.util.ArrayList<CorsRule>();
        mutableRules.add(new CorsRule(List.of("*"), List.of("GET"), null, 0, null, null));
        var config = new Bucket.Configuration(mutableRules);
        // Modify the original list — should not affect config
        mutableRules.clear();
        assertEquals(1, config.corsRules().size());
    }

    @Test
    void corsRule_immutableCollections() {
        var mutableOrigins = new java.util.ArrayList<String>();
        mutableOrigins.add("*");
        var mutableMethods = new java.util.ArrayList<String>();
        mutableMethods.add("GET");
        var rule = new CorsRule(mutableOrigins, mutableMethods, null, 0, null, null);
        // Modify the originals
        mutableOrigins.add("https://example.com");
        mutableMethods.add("POST");
        assertEquals(1, rule.allowedOrigins().size());
        assertEquals(1, rule.allowedMethods().size());
    }

    @Test
    void config_equality() {
        var rule1 = new CorsRule(List.of("*"), List.of("GET"), null, 0, null, null);
        var config1 = new Bucket.Configuration(List.of(rule1));
        var rule2 = new CorsRule(List.of("*"), List.of("GET"), null, 0, null, null);
        var config2 = new Bucket.Configuration(List.of(rule2));
        assertEquals(config1, config2);
        assertEquals(config1.hashCode(), config2.hashCode());
    }

    @Test
    void config_inequality() {
        var config1 = new Bucket.Configuration(List.of(
            new CorsRule(List.of("*"), List.of("GET"), null, 0, null, null)));
        var config2 = new Bucket.Configuration(null);
        assertNotEquals(config1, config2);
        assertNotEquals(config1.hashCode(), config2.hashCode());
    }
}
