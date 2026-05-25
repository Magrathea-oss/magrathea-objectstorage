package com.example.magrathea.s3api.dto.command;

import tools.jackson.dataformat.xml.annotation.JacksonXmlElementWrapper;
import tools.jackson.dataformat.xml.annotation.JacksonXmlProperty;
import tools.jackson.dataformat.xml.annotation.JacksonXmlRootElement;

import java.util.List;

/**
 * Command DTO for PutBucketCors XML body — Jackson XML annotated for codec deserialization.
 */
@JacksonXmlRootElement(localName = "CORSConfiguration")
public record CorsConfigurationCommand(
    @JacksonXmlElementWrapper(localName = "CORSRule", useWrapping = false)
    @JacksonXmlProperty(localName = "CORSRule")
    List<CorsRuleDto> corsRules
) {
    public record CorsRuleDto(
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
