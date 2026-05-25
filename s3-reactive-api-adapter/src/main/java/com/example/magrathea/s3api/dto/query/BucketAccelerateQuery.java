package com.example.magrathea.s3api.dto.query;

import com.example.magrathea.objectstorage.domain.aggregate.Bucket;
import com.example.magrathea.objectstorage.domain.valueobject.BucketAccelerateConfiguration;
import tools.jackson.dataformat.xml.annotation.JacksonXmlProperty;
import tools.jackson.dataformat.xml.annotation.JacksonXmlRootElement;

import java.util.Optional;

/**
 * Response for GET /{bucket}?accelerate (GetBucketAccelerateConfiguration).
 */
@JacksonXmlRootElement(localName = "AccelerateConfiguration")
public record BucketAccelerateQuery(
    @JacksonXmlProperty(localName = "Status")
    String status
) {
    public static BucketAccelerateQuery from(Optional<BucketAccelerateConfiguration> config) {
        var c = config.orElseThrow(() -> new IllegalArgumentException("No accelerate configuration"));
        return new BucketAccelerateQuery(c.status());
    }

}
