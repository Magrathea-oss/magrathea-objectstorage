package com.example.magrathea.s3api.dto.query;

import tools.jackson.dataformat.xml.annotation.JacksonXmlProperty;
import tools.jackson.dataformat.xml.annotation.JacksonXmlRootElement;

/**
 * Response for CopyObject (PUT /{bucket}/{key} with x-amz-copy-source).
 */
@JacksonXmlRootElement(localName = "CopyObjectResult")
public record CopyObjectResultQuery(
    @JacksonXmlProperty(localName = "LastModified")
    String lastModified,
    @JacksonXmlProperty(localName = "ETag")
    String etag
) {
    public static CopyObjectResultQuery from(String lastModified, String etag) {
        return new CopyObjectResultQuery(lastModified, etag);
    }
}
