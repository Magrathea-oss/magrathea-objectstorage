package com.example.magrathea.s3api.dto.query;

import com.example.magrathea.objectstorage.domain.model.Bucket;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import tools.jackson.dataformat.xml.annotation.JacksonXmlElementWrapper;
import tools.jackson.dataformat.xml.annotation.JacksonXmlProperty;
import tools.jackson.dataformat.xml.annotation.JacksonXmlRootElement;

import java.time.Instant;
import java.util.List;

/**
 * Response for GET / (ListBuckets).
 * Uses Jackson XML annotations for serialization via JacksonXmlEncoder.
 */
@JacksonXmlRootElement(localName = "ListAllMyBucketsResult")
public record ListAllMyBucketsResultQuery(
    @JacksonXmlProperty(localName = "Owner")
    OwnerEntry owner,
    @JacksonXmlElementWrapper(localName = "Buckets")
    @JacksonXmlProperty(localName = "Bucket")
    List<BucketEntry> buckets
) {
    public record OwnerEntry(
        @JacksonXmlProperty(localName = "ID")
        String id
    ) {}

    public record BucketEntry(
        @JacksonXmlProperty(localName = "Name")
        String name,
        @JacksonXmlProperty(localName = "CreationDate")
        String creationDate
    ) {}

    /**
     * Builds ListAllMyBucketsResult reactively from Flux<Bucket>.
     * Maps each Bucket to BucketEntry, collects list, then builds POJO.
     */
    public static Mono<ListAllMyBucketsResultQuery> from(Flux<Bucket> buckets) {
        return buckets
            .map(b -> new BucketEntry(b.name(), Instant.now().toString()))
            .collectList()
            .map(list -> new ListAllMyBucketsResultQuery(
                new OwnerEntry("owner"),
                list
            ));
    }

    /**
     * Convenience overload from a List<Bucket> (non-reactive).
     * Kept for backward compatibility until all handlers are updated.
     */
    public static ListAllMyBucketsResultQuery from(List<Bucket> buckets) {
        List<BucketEntry> entries = buckets.stream()
            .map(b -> new BucketEntry(b.name(), Instant.now().toString()))
            .toList();
        return new ListAllMyBucketsResultQuery(
            new OwnerEntry("owner"),
            entries
        );
    }
}
