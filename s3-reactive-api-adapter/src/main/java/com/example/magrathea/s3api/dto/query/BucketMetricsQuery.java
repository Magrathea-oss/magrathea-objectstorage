package com.example.magrathea.s3api.dto.query;

import com.example.magrathea.objectstorage.domain.model.Bucket;
import com.example.magrathea.objectstorage.domain.valueobject.BucketMetricsConfiguration;
import tools.jackson.dataformat.xml.annotation.JacksonXmlProperty;
import tools.jackson.dataformat.xml.annotation.JacksonXmlRootElement;

import java.util.Optional;

/**
 * Response for GET /{bucket}?metrics (GetBucketMetricsConfiguration).
 */
@JacksonXmlRootElement(localName = "MetricsConfiguration")
public record BucketMetricsQuery(
    @JacksonXmlProperty(localName = "Id")
    String id,
    @JacksonXmlProperty(localName = "Filter")
    MetricsFilter filter
) {
    public record MetricsFilter(
        @JacksonXmlProperty(localName = "Prefix")
        String prefix
    ) {}

    public static BucketMetricsQuery from(Optional<BucketMetricsConfiguration> config) {
        var c = config.orElseThrow(() -> new IllegalArgumentException("No metrics configuration"));
        var filter = c.filterRule() != null
            ? new MetricsFilter(c.filterRule())
            : null;
        return new BucketMetricsQuery(c.metricsId(), filter);
    }

    public static BucketMetricsQuery from(Bucket.BucketConfiguration config) {
        if (!config.hasMetrics()) {
            throw new IllegalArgumentException("No metrics configuration");
        }
        var filter = config.metricsFilterRule() != null
            ? new MetricsFilter(config.metricsFilterRule())
            : null;
        return new BucketMetricsQuery(config.metricsId(), filter);
    }
}
