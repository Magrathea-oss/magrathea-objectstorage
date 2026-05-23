package com.example.magrathea.s3api.dto.query;

import tools.jackson.dataformat.xml.annotation.JacksonXmlProperty;
import tools.jackson.dataformat.xml.annotation.JacksonXmlRootElement;

/**
 * Response for POST /{bucket}/{key}?uploadId (CompleteMultipartUpload).
 */
@JacksonXmlRootElement(localName = "CompleteMultipartUploadResult")
public record CompleteMultipartUploadQuery(
    @JacksonXmlProperty(localName = "Bucket")
    String bucket,
    @JacksonXmlProperty(localName = "Key")
    String key,
    @JacksonXmlProperty(localName = "ETag")
    String etag
) {
    public static CompleteMultipartUploadQuery from(String bucket, String key, String etag) {
        return new CompleteMultipartUploadQuery(bucket, key, etag);
    }
}
