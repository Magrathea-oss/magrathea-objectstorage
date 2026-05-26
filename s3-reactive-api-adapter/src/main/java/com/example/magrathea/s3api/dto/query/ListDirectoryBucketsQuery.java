package com.example.magrathea.s3api.dto.query;

import tools.jackson.dataformat.xml.annotation.JacksonXmlElementWrapper;
import tools.jackson.dataformat.xml.annotation.JacksonXmlProperty;
import tools.jackson.dataformat.xml.annotation.JacksonXmlRootElement;

import java.util.List;

/**
 * Response for GET /?directory-buckets (ListDirectoryBuckets).
 *
 * <pre>{@code
 * <ListDirectoryBucketsResult>
 *   <Buckets>
 *     <Bucket>
 *       <BucketName>my-directory-bucket</BucketName>
 *       <BucketId>abc123</BucketId>
 *       <CreationDate>2025-01-01T00:00:00Z</CreationDate>
 *     </Bucket>
 *   </Buckets>
 * </ListDirectoryBucketsResult>
 * }</pre>
 */
@JacksonXmlRootElement(localName = "ListDirectoryBucketsResult")
public record ListDirectoryBucketsQuery(
    @JacksonXmlElementWrapper(localName = "Buckets")
    @JacksonXmlProperty(localName = "Bucket")
    List<BucketEntry> buckets
) {
    public record BucketEntry(
        @JacksonXmlProperty(localName = "BucketName")
        String bucketName,
        @JacksonXmlProperty(localName = "BucketId")
        String bucketId,
        @JacksonXmlProperty(localName = "CreationDate")
        String creationDate
    ) {}
}
