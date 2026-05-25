package com.example.magrathea.s3api.dto.query;

import com.example.magrathea.objectstorage.domain.aggregate.S3Object;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import tools.jackson.dataformat.xml.annotation.JacksonXmlElementWrapper;
import tools.jackson.dataformat.xml.annotation.JacksonXmlProperty;
import tools.jackson.dataformat.xml.annotation.JacksonXmlRootElement;

import java.util.List;

/**
 * Response for GET /{bucket}?versions (ListObjectVersions).
 * Uses Jackson XML annotations for serialization via JacksonXmlEncoder.
 */
@JacksonXmlRootElement(localName = "ListVersionsResult")
public record ListVersionsQuery(
    @JacksonXmlProperty(localName = "Bucket")
    String bucket,
    @JacksonXmlElementWrapper(useWrapping = false)
    @JacksonXmlProperty(localName = "Version")
    List<VersionEntry> versions
) {
    public record VersionEntry(
        @JacksonXmlProperty(localName = "Key")
        String key,
        @JacksonXmlProperty(localName = "ETag")
        String eTag
    ) {}

    /**
     * Builds ListVersionsResult reactively from Flux<S3Object>.
     * Maps each S3Object to VersionEntry, collects list, then builds POJO.
     */
    public static Mono<ListVersionsQuery> from(String bucket, Flux<S3Object> objects) {
        return objects
            .map(o -> new VersionEntry(
                o.key().value(),
                "\"\""
            ))
            .collectList()
            .map(list -> new ListVersionsQuery(bucket, list));
    }

    /**
     * Convenience overload from a List<S3Object> (non-reactive).
     * Kept for backward compatibility until all handlers are updated.
     */
    public static ListVersionsQuery from(String bucket, List<S3Object> objects) {
        List<VersionEntry> entries = objects.stream()
            .map(o -> new VersionEntry(
                o.key().value(),
                "\"\""
            ))
            .toList();
        return new ListVersionsQuery(bucket, entries);
    }
}
