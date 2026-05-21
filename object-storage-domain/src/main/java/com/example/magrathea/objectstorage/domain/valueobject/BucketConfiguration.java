package com.example.magrathea.objectstorage.domain.valueobject;

import java.util.List;
import java.util.Objects;

/**
 * AWS S3 bucket configuration — value object.
 * Holds CORS rules and other bucket-level configuration.
 *
 * Pure domain — NO framework dependencies.
 */
public record BucketConfiguration(
    String bucketName,
    List<CorsRule> corsRules
) {
    public BucketConfiguration {
        Objects.requireNonNull(bucketName);
        if (corsRules != null) {
            corsRules = List.copyOf(corsRules);
        }
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

    public boolean hasCors() {
        return corsRules != null && !corsRules.isEmpty();
    }
}
