package com.example.magrathea.s3api.dto.query;

import com.example.magrathea.objectstorage.domain.aggregate.S3Object;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import tools.jackson.dataformat.xml.annotation.JacksonXmlElementWrapper;
import tools.jackson.dataformat.xml.annotation.JacksonXmlProperty;
import tools.jackson.dataformat.xml.annotation.JacksonXmlRootElement;

import java.util.List;

/**
 * Response for GET /{bucket}?list-type=2 (ListObjectsV2).
 * Uses Jackson XML annotations for serialization via JacksonXmlEncoder.
 */
@JacksonXmlRootElement(localName = "ListBucketV2Result")
public record ListObjectsV2Query(
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
     * Builds ListBucketV2Result reactively from Flux<S3Object>.
     * Maps each S3Object to ContentEntry, collects list, then builds POJO.
     */
    public static Mono<ListObjectsV2Query> from(String bucket, Flux<S3Object> objects) {
        return objects
            .map(o -> new ContentEntry(
                o.key().value(),
                o.contentType(),
                o.size(),
                "\"\""
            ))
            .collectList()
            .map(list -> new ListObjectsV2Query(bucket, list));
    }

    /**
     * Convenience overload from a List<S3Object> (non-reactive).
     * Kept for backward compatibility until all handlers are updated.
     */
    public static ListObjectsV2Query from(String bucket, List<S3Object> objects) {
        List<ContentEntry> entries = objects.stream()
            .map(o -> new ContentEntry(
                o.key().value(),
                o.contentType(),
                o.size(),
                "\"\""
            ))
            .toList();
        return new ListObjectsV2Query(bucket, entries);
    }
}
