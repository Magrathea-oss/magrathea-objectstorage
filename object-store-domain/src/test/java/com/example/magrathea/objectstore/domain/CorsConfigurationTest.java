package com.example.magrathea.objectstore.domain;

import com.example.magrathea.objectstore.domain.valueobject.CorsConfiguration;
import com.example.magrathea.objectstore.domain.valueobject.CorsConfiguration.CorsRule;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Pure JUnit test for CorsConfiguration value object.
 */
class CorsConfigurationTest {

    @Test
    void createValidRule() {
        var rule = new CorsRule(
            List.of("*"),
            List.of("GET", "PUT"),
            List.of("*"),
            3600,
            null,
            "my-rule"
        );
        assertEquals(List.of("*"), rule.allowedOrigins());
        assertEquals(List.of("GET", "PUT"), rule.allowedMethods());
        assertEquals(3600, rule.maxAgeSeconds());
    }

    @Test
    void createValidConfiguration() {
        var rule = new CorsRule(
            List.of("http://example.com"),
            List.of("GET"),
            List.of("x-amz-*"),
            0,
            List.of("x-amz-request-id"),
            null
        );
        var config = new CorsConfiguration(List.of(rule));
        assertEquals(1, config.corsRules().size());
        assertEquals("http://example.com", config.corsRules().getFirst().allowedOrigins().getFirst());
    }

    @Test
    void nullOriginThrows() {
        assertThrows(NullPointerException.class, () -> new CorsRule(null, List.of("GET"), null, 0, null, null));
    }

    @Test
    void nullMethodThrows() {
        assertThrows(NullPointerException.class, () -> new CorsRule(List.of("*"), null, null, 0, null, null));
    }

    @Test
    void emptyOriginsAllowed() {
        var rule = new CorsRule(List.of(), List.of("GET"), null, 0, null, null);
        assertTrue(rule.allowedOrigins().isEmpty());
    }

    @Test
    void emptyMethodsAllowed() {
        var rule = new CorsRule(List.of("*"), List.of(), null, 0, null, null);
        assertTrue(rule.allowedMethods().isEmpty());
    }

    @Test
    void configurationImmutability() {
        var mutableOrigins = new java.util.ArrayList<>(List.of("*"));
        var rule = new CorsRule(mutableOrigins, List.of("GET"), null, 0, null, null);
        mutableOrigins.add("http://evil.com");
        assertEquals(1, rule.allowedOrigins().size());
    }
}
