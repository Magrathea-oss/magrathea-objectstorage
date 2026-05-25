package com.example.magrathea.s3api.dto.query;

import tools.jackson.dataformat.xml.annotation.JacksonXmlProperty;
import tools.jackson.dataformat.xml.annotation.JacksonXmlRootElement;

/**
 * Response for UploadPart and UploadPartCopy.
 */
@JacksonXmlRootElement(localName = "UploadPartResult")
public record UploadPartResultQuery(
    @JacksonXmlProperty(localName = "ETag")
    String etag
) {
    public static UploadPartResultQuery from(String etag) {
        return new UploadPartResultQuery(etag);
    }
}
