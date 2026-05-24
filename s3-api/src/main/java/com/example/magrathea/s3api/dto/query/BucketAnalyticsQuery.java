package com.example.magrathea.s3api.dto.query;

import com.example.magrathea.objectstorage.domain.valueobject.BucketAnalyticsConfiguration;
import tools.jackson.dataformat.xml.annotation.JacksonXmlProperty;
import tools.jackson.dataformat.xml.annotation.JacksonXmlRootElement;

import java.util.Optional;

/**
 * Response for GET /{bucket}?analytics (GetBucketAnalyticsConfiguration).
 */
@JacksonXmlRootElement(localName = "AnalyticsConfiguration")
public record BucketAnalyticsQuery(
    @JacksonXmlProperty(localName = "Id")
    String id,
    @JacksonXmlProperty(localName = "Filter")
    AnalyticsFilter filter
) {
    public record AnalyticsFilter(
        @JacksonXmlProperty(localName = "Prefix")
        String prefix
    ) {}

    public static BucketAnalyticsQuery from(Optional<BucketAnalyticsConfiguration> config) {
        var c = config.orElseThrow(() -> new IllegalArgumentException("No analytics configuration"));
        var filter = c.filterRule() != null
            ? new AnalyticsFilter(c.filterRule())
            : null;
        return new BucketAnalyticsQuery(c.analyticsId(), filter);
    }
}
