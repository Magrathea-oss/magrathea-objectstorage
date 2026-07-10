package com.example.magrathea.s3api.dto.query;

import tools.jackson.dataformat.xml.annotation.JacksonXmlProperty;
import tools.jackson.dataformat.xml.annotation.JacksonXmlRootElement;

import java.time.Instant;

/**
 * S3-compatible response body for UploadPartCopy.
 */
@JacksonXmlRootElement(localName = "CopyPartResult")
public record UploadPartCopyResultQuery(
    @JacksonXmlProperty(localName = "LastModified")
    String lastModified,

    @JacksonXmlProperty(localName = "ETag")
    String etag
) {
    public static UploadPartCopyResultQuery from(String etag) {
        return new UploadPartCopyResultQuery(Instant.now().toString(), etag);
    }
}
