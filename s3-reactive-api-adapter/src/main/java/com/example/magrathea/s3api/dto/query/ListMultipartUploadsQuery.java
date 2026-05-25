package com.example.magrathea.s3api.dto.query;

import tools.jackson.dataformat.xml.annotation.JacksonXmlElementWrapper;
import tools.jackson.dataformat.xml.annotation.JacksonXmlProperty;
import tools.jackson.dataformat.xml.annotation.JacksonXmlRootElement;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.time.Instant;
import java.util.List;

/**
 * Response for GET /{bucket}?uploads (ListMultipartUploads).
 * Uses Jackson XML annotations for serialization via Jackson XML codec.
 */
@JacksonXmlRootElement(localName = "ListMultipartUploadsResult")
public record ListMultipartUploadsQuery(
    @JacksonXmlProperty(localName = "Bucket")
    String bucket,
    @JacksonXmlElementWrapper(localName = "Upload", useWrapping = false)
    @JacksonXmlProperty(localName = "Upload")
    List<UploadEntry> uploads
) {
    /**
     * Builds the ListMultipartUploadsResult reactively by collecting UploadEntry flux into a list.
     */
    public static Mono<ListMultipartUploadsQuery> from(String bucket, Flux<UploadEntry> uploads) {
        return uploads.collectList()
            .map(list -> new ListMultipartUploadsQuery(bucket, list));
    }

    /**
     * Inner record for each upload entry in the result.
     */
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
