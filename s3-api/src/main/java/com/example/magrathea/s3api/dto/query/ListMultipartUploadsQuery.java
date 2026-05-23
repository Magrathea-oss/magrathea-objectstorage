package com.example.magrathea.s3api.dto.query;

import tools.jackson.dataformat.xml.annotation.JacksonXmlElementWrapper;
import tools.jackson.dataformat.xml.annotation.JacksonXmlProperty;
import tools.jackson.dataformat.xml.annotation.JacksonXmlRootElement;

import java.time.Instant;
import java.util.List;

/**
 * Response for GET /{bucket}?uploads (ListMultipartUploads).
 */
@JacksonXmlRootElement(localName = "ListMultipartUploadsResult")
public record ListMultipartUploadsQuery(
    String bucket,
    @JacksonXmlElementWrapper(useWrapping = false)
    @JacksonXmlProperty(localName = "Upload")
    List<UploadEntry> uploads
) {
    public static ListMultipartUploadsQuery from(String bucket, List<UploadEntry> uploads) {
        return new ListMultipartUploadsQuery(bucket, uploads);
    }

    public record UploadEntry(
        @JacksonXmlProperty(localName = "Key")
        String key,
        @JacksonXmlProperty(localName = "UploadId")
        String uploadId,
        @JacksonXmlProperty(localName = "Initiated")
        String initiated
    ) {
        public static UploadEntry from(String key, String uploadId, Instant initiated) {
            return new UploadEntry(key, uploadId, initiated.toString());
        }
    }
}
