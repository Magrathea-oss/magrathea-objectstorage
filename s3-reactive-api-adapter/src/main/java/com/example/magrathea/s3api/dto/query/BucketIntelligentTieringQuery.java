package com.example.magrathea.s3api.dto.query;

import com.example.magrathea.objectstorage.domain.model.Bucket;
import com.example.magrathea.objectstorage.domain.valueobject.BucketIntelligentTieringConfiguration;
import tools.jackson.dataformat.xml.annotation.JacksonXmlProperty;
import tools.jackson.dataformat.xml.annotation.JacksonXmlRootElement;

import java.util.Optional;

/**
 * Response for GET /{bucket}?intelligent-tiering (GetBucketIntelligentTieringConfiguration).
 */
@JacksonXmlRootElement(localName = "IntelligentTieringConfiguration")
public record BucketIntelligentTieringQuery(
    @JacksonXmlProperty(localName = "Id")
    String id,
    @JacksonXmlProperty(localName = "TieringPolicy")
    IntelligentTieringPolicy tieringPolicy,
    @JacksonXmlProperty(localName = "AutoTieringStatus")
    String autoTieringStatus
) {
    public record IntelligentTieringPolicy(
        @JacksonXmlProperty(localName = "TieringRule")
        String tieringRule
    ) {}

    public static BucketIntelligentTieringQuery from(Optional<BucketIntelligentTieringConfiguration> config) {
        var c = config.orElseThrow(() -> new IllegalArgumentException("No intelligent-tiering configuration"));
        var policy = c.tieringPolicy() != null
            ? new IntelligentTieringPolicy(c.tieringPolicy())
            : null;
        return new BucketIntelligentTieringQuery(c.tieringId(), policy, c.autoTieringStatus());
    }

    public static BucketIntelligentTieringQuery from(Bucket.BucketConfiguration config) {
        if (!config.hasIntelligentTiering()) {
            throw new IllegalArgumentException("No intelligent-tiering configuration");
        }
        var policy = config.intelligentTieringTieringPolicy() != null
            ? new IntelligentTieringPolicy(config.intelligentTieringTieringPolicy())
            : null;
        return new BucketIntelligentTieringQuery(config.intelligentTieringId(), policy, config.intelligentTieringAutoTieringStatus());
    }
}
