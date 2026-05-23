package com.example.magrathea.objectstorage.application.dto;

import java.util.List;

/**
 * Input command for setting a bucket's CORS configuration.
 */
public record CorsConfigurationCommand(
    String bucketName,
    List<CorsRuleDto> corsRules
) {
    public record CorsRuleDto(
        List<String> allowedOrigins,
        List<String> allowedMethods,
        List<String> allowedHeaders,
        Integer maxAgeSeconds,
        List<String> exposeHeaders,
        String id
    ) {}
}
