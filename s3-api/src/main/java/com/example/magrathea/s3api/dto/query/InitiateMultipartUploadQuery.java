package com.example.magrathea.s3api.dto.query;

import tools.jackson.dataformat.xml.annotation.JacksonXmlProperty;
import tools.jackson.dataformat.xml.annotation.JacksonXmlRootElement;

/**
 * Response for POST /{bucket}/{key}?uploads (CreateMultipartUpload).
 */
@JacksonXmlRootElement(localName = "InitiateMultipartUploadResult")
public record InitiateMultipartUploadQuery(
    String bucket,
    String key,
    @JacksonXmlProperty(localName = "UploadId")
    String uploadId
) {
    public static InitiateMultipartUploadQuery from(String bucket, String key, String uploadId) {
        return new InitiateMultipartUploadQuery(bucket, key, uploadId);
    }
}
