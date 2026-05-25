package com.example.magrathea.s3api.dto.query;

import tools.jackson.dataformat.xml.annotation.JacksonXmlElementWrapper;
import tools.jackson.dataformat.xml.annotation.JacksonXmlProperty;
import tools.jackson.dataformat.xml.annotation.JacksonXmlRootElement;

import java.util.List;

/**
 * Response for GET /{bucket}?analytics&list-type (ListBucketAnalyticsConfigurations).
 * Uses Jackson XML annotations for serialization via Jackson XML codec.
 */
@JacksonXmlRootElement(localName = "ListAnalyticsConfigurationsResult")
public record BucketAnalyticsListQuery(
    @JacksonXmlElementWrapper(localName = "AnalyticsConfiguration", useWrapping = false)
    @JacksonXmlProperty(localName = "AnalyticsConfiguration")
    List<AnalyticsConfigurationEntry> configurations
) {
    public record AnalyticsConfigurationEntry(
        @JacksonXmlProperty(localName = "Id")
        String id
    ) {}
}
