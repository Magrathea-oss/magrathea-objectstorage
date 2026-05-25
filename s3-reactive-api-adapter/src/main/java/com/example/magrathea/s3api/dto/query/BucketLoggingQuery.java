package com.example.magrathea.s3api.dto.query;

import com.example.magrathea.objectstorage.domain.model.Bucket;
import com.example.magrathea.objectstorage.domain.valueobject.BucketLoggingConfiguration;
import tools.jackson.dataformat.xml.annotation.JacksonXmlProperty;
import tools.jackson.dataformat.xml.annotation.JacksonXmlRootElement;

import java.util.Optional;

/**
 * Response for GET /{bucket}?logging (GetBucketLogging).
 */
@JacksonXmlRootElement(localName = "BucketLoggingStatus")
public record BucketLoggingQuery(
    @JacksonXmlProperty(localName = "TargetBucket")
    String targetBucket,
    @JacksonXmlProperty(localName = "TargetPrefix")
    String targetPrefix
) {
    public static BucketLoggingQuery from(Optional<BucketLoggingConfiguration> config) {
        var c = config.orElseThrow(() -> new IllegalArgumentException("No logging configuration"));
        return new BucketLoggingQuery(c.targetBucket(), c.targetPrefix());
    }

    public static BucketLoggingQuery from(Bucket.BucketConfiguration config) {
        if (!config.hasLogging()) {
            throw new IllegalArgumentException("No logging configuration");
        }
        return new BucketLoggingQuery(config.loggingTargetBucket(), config.loggingTargetPrefix());
    }
}
