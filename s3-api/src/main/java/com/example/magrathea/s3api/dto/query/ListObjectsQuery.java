package com.example.magrathea.s3api.dto.query;

import com.example.magrathea.objectstorage.application.dto.ObjectResponse;
import tools.jackson.dataformat.xml.annotation.JacksonXmlElementWrapper;
import tools.jackson.dataformat.xml.annotation.JacksonXmlProperty;
import tools.jackson.dataformat.xml.annotation.JacksonXmlRootElement;

import java.util.List;

/**
 * Response for GET /{bucket} (ListObjects).
 */
@JacksonXmlRootElement(localName = "ListBucketResult")
public record ListObjectsQuery(
    String bucket,
    @JacksonXmlElementWrapper(localName = "Content")
    @JacksonXmlProperty(localName = "Content")
    List<ObjectEntry> contents
) {
    public static ListObjectsQuery from(String bucket, List<ObjectResponse> objects) {
        return new ListObjectsQuery(bucket,
            objects.stream()
                .map(o -> new ObjectEntry(o.key(), o.contentType(), o.size(), "\"\""))
                .toList());
    }

    public record ObjectEntry(
        @JacksonXmlProperty(localName = "Key")
        String key,
        String contentType,
        @JacksonXmlProperty(localName = "Size")
        long size,
        @JacksonXmlProperty(localName = "ETag")
        String etag
    ) {}
}
