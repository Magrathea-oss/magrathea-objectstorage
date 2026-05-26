package com.example.magrathea.objectstore.domain;

import com.example.magrathea.objectstore.domain.valueobject.CorsConfiguration;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Pure JUnit test for CorsConfiguration value object.
 */
class BucketConfigurationTest {

    @Test
    void createWithEmptyCorsRules() {
        var config = new CorsConfiguration(List.of());
        assertTrue(config.corsRules().isEmpty());
    }

    @Test
    void withCorsRules() {
        var rule = new CorsConfiguration.CorsRule(List.of("*"), List.of("GET"), null, 0, null, null);
        var config = new CorsConfiguration(List.of(rule));
        assertFalse(config.corsRules().isEmpty());
        assertEquals(1, config.corsRules().size());
        assertEquals("*", config.corsRules().get(0).allowedOrigins().get(0));
        assertEquals("GET", config.corsRules().get(0).allowedMethods().get(0));
    }

    @Test
    void corsRule_nullAllowedOrigins_throws() {
        assertThrows(NullPointerException.class,
            () -> new CorsConfiguration.CorsRule(null, List.of("GET"), null, 0, null, null));
    }

    @Test
    void corsRule_nullAllowedMethods_throws() {
        assertThrows(NullPointerException.class,
            () -> new CorsConfiguration.CorsRule(List.of("*"), null, null, 0, null, null));
    }

    @Test
    void config_immutableCorsRules() {
        var mutableRules = new java.util.ArrayList<CorsConfiguration.CorsRule>();
        mutableRules.add(new CorsConfiguration.CorsRule(List.of("*"), List.of("GET"), null, 0, null, null));
        var config = new CorsConfiguration(mutableRules);
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
        var rule = new CorsConfiguration.CorsRule(mutableOrigins, mutableMethods, null, 0, null, null);
        // Modify the originals
        mutableOrigins.add("https://example.com");
        mutableMethods.add("POST");
        assertEquals(1, rule.allowedOrigins().size());
        assertEquals(1, rule.allowedMethods().size());
    }

    @Test
    void config_equality() {
        var rule1 = new CorsConfiguration.CorsRule(List.of("*"), List.of("GET"), null, 0, null, null);
        var config1 = new CorsConfiguration(List.of(rule1));
        var rule2 = new CorsConfiguration.CorsRule(List.of("*"), List.of("GET"), null, 0, null, null);
        var config2 = new CorsConfiguration(List.of(rule2));
        assertEquals(config1, config2);
        assertEquals(config1.hashCode(), config2.hashCode());
    }

    @Test
    void config_inequality() {
        var config1 = new CorsConfiguration(List.of(
            new CorsConfiguration.CorsRule(List.of("*"), List.of("GET"), null, 0, null, null)));
        var config2 = new CorsConfiguration(List.of());
        assertNotEquals(config1, config2);
        assertNotEquals(config1.hashCode(), config2.hashCode());
    }
}
