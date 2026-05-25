package com.example.magrathea.s3api.dto.query;

import com.example.magrathea.objectstorage.domain.aggregate.Bucket;
import com.example.magrathea.objectstorage.domain.valueobject.PublicAccessBlockConfiguration;
import tools.jackson.dataformat.xml.annotation.JacksonXmlProperty;
import tools.jackson.dataformat.xml.annotation.JacksonXmlRootElement;

import java.util.Optional;

/**
 * Response for GET /{bucket}?publicAccessBlock (GetPublicAccessBlock).
 */
@JacksonXmlRootElement(localName = "PublicAccessBlockConfiguration")
public record PublicAccessBlockQuery(
    @JacksonXmlProperty(localName = "BlockPublicAcls")
    String blockPublicAcls,
    @JacksonXmlProperty(localName = "IgnorePublicAcls")
    String ignorePublicAcls,
    @JacksonXmlProperty(localName = "BlockPublicPolicy")
    String blockPublicPolicy,
    @JacksonXmlProperty(localName = "RestrictPublicBuckets")
    String restrictPublicBuckets
) {
    public static PublicAccessBlockQuery from(Optional<PublicAccessBlockConfiguration> config) {
        var c = config.orElseThrow(() -> new IllegalArgumentException("No public access block configuration"));
        return new PublicAccessBlockQuery(
            String.valueOf(c.blockPublicAcls()),
            String.valueOf(c.ignorePublicAcls()),
            String.valueOf(c.blockPublicPolicy()),
            String.valueOf(c.restrictPublicBuckets())
        );
    }

}
