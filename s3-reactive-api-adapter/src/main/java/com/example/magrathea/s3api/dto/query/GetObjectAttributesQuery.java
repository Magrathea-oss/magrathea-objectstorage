package com.example.magrathea.s3api.dto.query;

import com.example.magrathea.objectstore.domain.aggregate.S3Object;
import tools.jackson.dataformat.xml.annotation.JacksonXmlProperty;
import tools.jackson.dataformat.xml.annotation.JacksonXmlRootElement;

/**
 * Response for GET /{bucket}/{key}?attributes.
 */
@JacksonXmlRootElement(localName = "GetObjectAttributesOutput")
public record GetObjectAttributesQuery(
    @JacksonXmlProperty(localName = "Key")
    String key,
    @JacksonXmlProperty(localName = "ContentType")
    String contentType,
    @JacksonXmlProperty(localName = "ObjectSize")
    long size,
    @JacksonXmlProperty(localName = "StorageClass")
    String storageClass,
    @JacksonXmlProperty(localName = "ETag")
    String etag
) {
    public static GetObjectAttributesQuery from(S3Object obj) {
        return new GetObjectAttributesQuery(
            obj.key().key(),
            null, // contentType not stored on sealed hierarchy
            obj.size(),
            obj.storageClass() != null ? obj.storageClass() : "STANDARD",
            "\"\""
        );
    }
}
