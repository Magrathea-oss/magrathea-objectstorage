package com.example.magrathea.s3api.dto.query;

import tools.jackson.dataformat.xml.annotation.JacksonXmlElementWrapper;
import tools.jackson.dataformat.xml.annotation.JacksonXmlProperty;
import tools.jackson.dataformat.xml.annotation.JacksonXmlRootElement;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.util.List;

/**
 * Response for GET /{bucket}/{key}?uploadId (ListParts).
 * Uses Jackson XML annotations for serialization via Jackson XML codec.
 */
@JacksonXmlRootElement(localName = "ListPartsResult")
public record ListPartsQuery(
    @JacksonXmlProperty(localName = "Bucket")
    String bucket,
    @JacksonXmlProperty(localName = "Key")
    String key,
    @JacksonXmlProperty(localName = "UploadId")
    String uploadId,
    @JacksonXmlElementWrapper(localName = "Part", useWrapping = false)
    @JacksonXmlProperty(localName = "Part")
    List<PartEntry> parts
) {
    /**
     * Builds the ListPartsResult reactively by collecting PartEntry flux into a list.
     */
    public static Mono<ListPartsQuery> from(String bucket, String key, String uploadId, Flux<PartEntry> parts) {
        return parts.collectList()
            .map(list -> new ListPartsQuery(bucket, key, uploadId, list));
    }

    /**
     * Inner record for each part entry in the result.
     */
    public record PartEntry(
        @JacksonXmlProperty(localName = "PartNumber")
        int partNumber,
        @JacksonXmlProperty(localName = "ETag")
        String etag
    ) {
        public static PartEntry from(int partNumber, String etag) {
            return new PartEntry(partNumber, etag);
        }
    }
}
