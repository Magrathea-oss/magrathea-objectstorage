package com.example.magrathea.s3api.dto.command;

import tools.jackson.dataformat.xml.annotation.JacksonXmlProperty;
import tools.jackson.dataformat.xml.annotation.JacksonXmlRootElement;

/**
 * Command DTO for PutBucketAnalyticsConfiguration XML body.
 */
@JacksonXmlRootElement(localName = "AnalyticsConfiguration")
public record AnalyticsConfigurationCommand(
    @JacksonXmlProperty(localName = "Id")
    String id,
    @JacksonXmlProperty(localName = "Filter")
    AnalyticsFilter filter
) {
    public record AnalyticsFilter(
        @JacksonXmlProperty(localName = "Prefix")
        String prefix
    ) {}
}
