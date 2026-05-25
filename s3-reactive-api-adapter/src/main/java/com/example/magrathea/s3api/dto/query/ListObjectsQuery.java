package com.example.magrathea.s3api.dto.query;

import com.example.magrathea.objectstorage.domain.model.S3Object;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import tools.jackson.dataformat.xml.annotation.JacksonXmlElementWrapper;
import tools.jackson.dataformat.xml.annotation.JacksonXmlProperty;
import tools.jackson.dataformat.xml.annotation.JacksonXmlRootElement;

import java.util.List;

/**
 * Response for GET /{bucket} (ListObjects).
 * Uses Jackson XML annotations for serialization via JacksonXmlEncoder.
 */
@JacksonXmlRootElement(localName = "ListBucketResult")
public record ListObjectsQuery(
    @JacksonXmlProperty(localName = "Bucket")
    String bucket,
    @JacksonXmlElementWrapper(useWrapping = false)
    @JacksonXmlProperty(localName = "Contents")
    List<ContentEntry> contents
) {
    public record ContentEntry(
        @JacksonXmlProperty(localName = "Key")
        String key,
        @JacksonXmlProperty(localName = "ContentType")
        String contentType,
        @JacksonXmlProperty(localName = "Size")
        long size,
        @JacksonXmlProperty(localName = "ETag")
        String eTag
    ) {}

    /**
     * Builds ListBucketResult reactively from Flux<S3Object>.
     * Maps each S3Object to ContentEntry, collects list, then builds POJO.
     */
    public static Mono<ListObjectsQuery> from(String bucket, Flux<S3Object> objects) {
        return objects
            .map(o -> new ContentEntry(
                o.key().value(),
                o.contentType(),
                o.size(),
                "\"\""
            ))
            .collectList()
            .map(list -> new ListObjectsQuery(bucket, list));
    }

    /**
     * Convenience overload from a List<S3Object> (non-reactive).
     * Kept for backward compatibility until all handlers are updated.
     */
    public static ListObjectsQuery from(String bucket, List<S3Object> objects) {
        List<ContentEntry> entries = objects.stream()
            .map(o -> new ContentEntry(
                o.key().value(),
                o.contentType(),
                o.size(),
                "\"\""
            ))
            .toList();
        return new ListObjectsQuery(bucket, entries);
    }
}
