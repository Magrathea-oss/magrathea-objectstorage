package com.example.magrathea.s3api.dto.query;

import com.example.magrathea.objectstorage.domain.valueobject.BucketWebsiteConfiguration;
import tools.jackson.dataformat.xml.annotation.JacksonXmlProperty;
import tools.jackson.dataformat.xml.annotation.JacksonXmlRootElement;

import java.util.Optional;

/**
 * Response for GET /{bucket}?website (GetBucketWebsite).
 */
@JacksonXmlRootElement(localName = "WebsiteConfiguration")
public record BucketWebsiteQuery(
    @JacksonXmlProperty(localName = "IndexDocument")
    String indexDocument,
    @JacksonXmlProperty(localName = "ErrorDocument")
    String errorDocument,
    @JacksonXmlProperty(localName = "RedirectAllRequestsTo")
    String redirectAllRequestsTo,
    @JacksonXmlProperty(localName = "RoutingRuleRedirectHost")
    String routingRuleRedirectHost,
    @JacksonXmlProperty(localName = "RoutingRuleRedirectProtocol")
    String routingRuleRedirectProtocol
) {
    public static BucketWebsiteQuery from(Optional<BucketWebsiteConfiguration> config) {
        var c = config.orElseThrow(() -> new IllegalArgumentException("No website configuration"));
        return new BucketWebsiteQuery(
            c.indexDocument(), c.errorDocument(), c.redirectAllRequestsTo(),
            c.routingRuleRedirectHost(), c.routingRuleRedirectProtocol()
        );
    }
}
