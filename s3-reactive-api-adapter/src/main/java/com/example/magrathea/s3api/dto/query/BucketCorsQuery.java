package com.example.magrathea.s3api.dto.query;

import com.example.magrathea.objectstore.domain.valueobject.CorsConfiguration;
import tools.jackson.dataformat.xml.annotation.JacksonXmlElementWrapper;
import tools.jackson.dataformat.xml.annotation.JacksonXmlProperty;
import tools.jackson.dataformat.xml.annotation.JacksonXmlRootElement;

import java.util.List;
import java.util.Optional;

/**
 * Response for GET /{bucket}?cors (GetBucketCors).
 */
@JacksonXmlRootElement(localName = "CORSConfiguration")
public record BucketCorsQuery(
    @JacksonXmlProperty(localName = "CORSRule")
    List<CorsRuleEntry> corsRules
) {
    public static BucketCorsQuery from(String bucket, Optional<CorsConfiguration> config) {
        if (config.isEmpty() || config.get().corsRules() == null || config.get().corsRules().isEmpty()) {
            throw new IllegalArgumentException("No CORS configuration");
        }
        var rules = config.get().corsRules().stream()
            .map(r -> new CorsRuleEntry(
                r.allowedOrigins(), r.allowedMethods(), r.allowedHeaders(),
                r.maxAgeSeconds(), r.exposeHeaders(), r.id()))
            .toList();
        return new BucketCorsQuery(rules);
    }


    public record CorsRuleEntry(
        @JacksonXmlElementWrapper(localName = "AllowedOrigin", useWrapping = false)
        @JacksonXmlProperty(localName = "AllowedOrigin")
        List<String> allowedOrigins,
        @JacksonXmlElementWrapper(localName = "AllowedMethod", useWrapping = false)
        @JacksonXmlProperty(localName = "AllowedMethod")
        List<String> allowedMethods,
        @JacksonXmlElementWrapper(localName = "AllowedHeader", useWrapping = false)
        @JacksonXmlProperty(localName = "AllowedHeader")
        List<String> allowedHeaders,
        @JacksonXmlProperty(localName = "MaxAgeSeconds")
        Integer maxAgeSeconds,
        @JacksonXmlElementWrapper(localName = "ExposeHeader", useWrapping = false)
        @JacksonXmlProperty(localName = "ExposeHeader")
        List<String> exposeHeaders,
        @JacksonXmlProperty(localName = "ID")
        String id
    ) {}
}
