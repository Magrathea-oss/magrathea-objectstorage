package com.example.magrathea.objectstorage.domain.valueobject;

import java.util.List;
import java.util.Objects;

/**
 * AWS S3 CORS configuration — value object.
 * Represents a single CorsRule as defined in the AWS S3 API.
 *
 * Pure domain — NO framework dependencies.
 */
public record CorsConfiguration(
    List<CorsRule> corsRules
) {
    public CorsConfiguration {
        Objects.requireNonNull(corsRules);
        corsRules = List.copyOf(corsRules);
    }

    public record CorsRule(
        List<String> allowedOrigins,
        List<String> allowedMethods,
        List<String> allowedHeaders,
        int maxAgeSeconds,
        List<String> exposeHeaders,
        String id
    ) {
        public CorsRule {
            Objects.requireNonNull(allowedOrigins);
            Objects.requireNonNull(allowedMethods);
            allowedOrigins = List.copyOf(allowedOrigins);
            allowedMethods = List.copyOf(allowedMethods);
            if (allowedHeaders != null) {
                allowedHeaders = List.copyOf(allowedHeaders);
            }
            if (exposeHeaders != null) {
                exposeHeaders = List.copyOf(exposeHeaders);
            }
        }
    }
}
