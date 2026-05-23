package com.example.magrathea.s3api.dto.query;

import com.example.magrathea.objectstorage.domain.valueobject.PartNumber;
import tools.jackson.dataformat.xml.annotation.JacksonXmlElementWrapper;
import tools.jackson.dataformat.xml.annotation.JacksonXmlProperty;
import tools.jackson.dataformat.xml.annotation.JacksonXmlRootElement;

import java.util.List;

/**
 * Response for GET /{bucket}/{key}?uploadId (ListParts).
 */
@JacksonXmlRootElement(localName = "ListPartsResult")
public record ListPartsQuery(
    String bucket,
    String key,
    String uploadId,
    @JacksonXmlElementWrapper(useWrapping = false)
    @JacksonXmlProperty(localName = "Part")
    List<PartEntry> parts
) {
    public static ListPartsQuery from(String bucket, String key, String uploadId, List<PartEntry> parts) {
        return new ListPartsQuery(bucket, key, uploadId, parts);
    }

    public record PartEntry(
        @JacksonXmlProperty(localName = "PartNumber")
        int partNumber,
        @JacksonXmlProperty(localName = "ETag")
        String etag
    ) {
        public static PartEntry from(PartNumber partNumber, String etag) {
            return new PartEntry(partNumber.value(), etag);
        }
    }
}
