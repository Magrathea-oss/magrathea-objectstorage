package com.example.magrathea.s3api.dto.query;

import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

/**
 * Response for GET /{bucket}?analytics&list-type (ListBucketAnalyticsConfigurations).
 * Builds XML reactively from Flux<String> analytics IDs without holding the full list in memory.
 */
public record BucketAnalyticsListQuery(String xmlContent) {

    /**
     * Builds the ListAnalyticsConfigurationsResult XML reactively by streaming each analytics ID
     * into XML fragments and accumulating them in a StringBuilder.
     */
    public static Mono<BucketAnalyticsListQuery> fromIds(Flux<String> analyticsIds) {
        return analyticsIds
            .map(id -> {
                String escapedId = xmlEscape(id);
                return "<AnalyticsConfiguration><Id>" + escapedId + "</Id></AnalyticsConfiguration>";
            })
            .collect(StringBuilder::new, (sb, s) -> sb.append(s), StringBuilder::append)
            .map(sb -> {
                String configsXml = sb.toString();
                return "<ListAnalyticsConfigurationsResult>" + configsXml + "</ListAnalyticsConfigurationsResult>";
            })
            .map(BucketAnalyticsListQuery::new);
    }

    /**
     * Returns the raw XML content for direct response body writing.
     */
    public String xmlContent() {
        return xmlContent;
    }

    private static String xmlEscape(String s) {
        if (s == null) return "";
        return s.replace("&", "&amp;")
                .replace("<", "&lt;")
                .replace(">", "&gt;")
                .replace("\"", "&quot;");
    }
}
