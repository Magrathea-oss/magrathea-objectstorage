package com.example.magrathea.s3api.dto.query;

import tools.jackson.dataformat.xml.annotation.JacksonXmlElementWrapper;
import tools.jackson.dataformat.xml.annotation.JacksonXmlProperty;
import tools.jackson.dataformat.xml.annotation.JacksonXmlRootElement;

import java.util.List;

/**
 * Response for GET /{bucket}?analytics&list-type (ListBucketAnalyticsConfigurations).
 */
@JacksonXmlRootElement(localName = "ListAnalyticsConfigurationsResult")
public record BucketAnalyticsListQuery(
    @JacksonXmlElementWrapper(useWrapping = false)
    @JacksonXmlProperty(localName = "AnalyticsConfiguration")
    List<AnalyticsIdEntry> analyticsConfigurations
) {
    public record AnalyticsIdEntry(
        @JacksonXmlProperty(localName = "Id")
        String id
    ) {}

    public static BucketAnalyticsListQuery fromIds(List<String> analyticsIds) {
        return new BucketAnalyticsListQuery(
            analyticsIds.stream().map(AnalyticsIdEntry::new).toList()
        );
    }
}
