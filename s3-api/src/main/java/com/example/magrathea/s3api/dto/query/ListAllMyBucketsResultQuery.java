package com.example.magrathea.s3api.dto.query;

import com.example.magrathea.objectstorage.application.dto.BucketResponse;
import tools.jackson.dataformat.xml.annotation.JacksonXmlElementWrapper;
import tools.jackson.dataformat.xml.annotation.JacksonXmlProperty;
import tools.jackson.dataformat.xml.annotation.JacksonXmlRootElement;

import java.util.List;

/**
 * Response for GET / (ListBuckets).
 */
@JacksonXmlRootElement(localName = "ListAllMyBucketsResult")
public record ListAllMyBucketsResultQuery(
    @JacksonXmlElementWrapper(localName = "Bucket")
    @JacksonXmlProperty(localName = "Bucket")
    List<BucketEntry> buckets
) {
    public static ListAllMyBucketsResultQuery from(List<BucketResponse> buckets) {
        return new ListAllMyBucketsResultQuery(
            buckets.stream()
                .map(b -> new BucketEntry(b.name(), java.time.Instant.now().toString()))
                .toList()
        );
    }

    public record BucketEntry(
        @JacksonXmlProperty(localName = "Name")
        String name,
        @JacksonXmlProperty(localName = "CreationDate")
        String creationDate
    ) {}
}
