package com.example.magrathea.s3api.dto.command;

import tools.jackson.dataformat.xml.annotation.JacksonXmlProperty;
import tools.jackson.dataformat.xml.annotation.JacksonXmlRootElement;

/**
 * Command DTO for PutBucketMetricsConfiguration XML body.
 */
@JacksonXmlRootElement(localName = "MetricsConfiguration")
public record MetricsConfigurationCommand(
    @JacksonXmlProperty(localName = "Id")
    String id,
    @JacksonXmlProperty(localName = "Filter")
    MetricsFilter filter
) {
    public record MetricsFilter(
        @JacksonXmlProperty(localName = "Prefix")
        String prefix
    ) {}
}
