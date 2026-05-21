package com.example.magrathea.s3api.adapter.web.xml;

import com.example.magrathea.objectstorage.application.dto.BucketResponse;
import com.example.magrathea.objectstorage.application.dto.ObjectResponse;
import tools.jackson.dataformat.xml.annotation.JacksonXmlElementWrapper;
import tools.jackson.dataformat.xml.annotation.JacksonXmlProperty;
import tools.jackson.dataformat.xml.annotation.JacksonXmlRootElement;

import java.util.List;

/**
 * S3 XML response objects using Jackson 3 XML annotations.
 * Spring Boot 4 uses Jackson 3 natively; jackson-dataformat-xml 3.x provides
 * XmlMapper with @JacksonXmlRootElement etc. in tools.jackson.dataformat.xml.annotation.
 */
public final class S3XmlResponses {

    private S3XmlResponses() {}

    // ── ListAllMyBucketsResult ──

    @JacksonXmlRootElement(localName = "ListAllMyBucketsResult")
    public record ListAllMyBucketsResult(
        @JacksonXmlProperty(localName = "Owner")
        Owner owner,
        @JacksonXmlElementWrapper(localName = "Buckets", useWrapping = true)
        @JacksonXmlProperty(localName = "Bucket")
        List<BucketEntry> buckets
    ) {
        public static ListAllMyBucketsResult from(List<BucketResponse> buckets) {
            return new ListAllMyBucketsResult(
                new Owner("000000000000", "magrathea"),
                buckets.stream().map(b -> new BucketEntry(b.name(), "2026-01-01T00:00:00Z")).toList()
            );
        }
    }

    public record Owner(
        @JacksonXmlProperty(localName = "ID") String id,
        @JacksonXmlProperty(localName = "DisplayName") String displayName
    ) {}

    public record BucketEntry(
        @JacksonXmlProperty(localName = "Name") String name,
        @JacksonXmlProperty(localName = "CreationDate") String creationDate
    ) {}

    // ── ListBucketResult ──

    @JacksonXmlRootElement(localName = "ListBucketResult")
    public record ListBucketResult(
        @JacksonXmlProperty(localName = "Name") String name,
        @JacksonXmlProperty(localName = "Prefix") String prefix,
        @JacksonXmlProperty(localName = "Marker") String marker,
        @JacksonXmlProperty(localName = "MaxKeys") int maxKeys,
        @JacksonXmlProperty(localName = "IsTruncated") boolean isTruncated,
        @JacksonXmlElementWrapper(localName = "Contents", useWrapping = true)
        @JacksonXmlProperty(localName = "Contents")
        List<ContentsEntry> contents
    ) {
        public static ListBucketResult from(String bucketName, List<ObjectResponse> objects) {
            return new ListBucketResult(
                bucketName, "", "", 1000, false,
                objects.stream().map(o -> new ContentsEntry(
                    o.key(), o.size(), o.etag() != null ? o.etag() : "", "STANDARD"
                )).toList()
            );
        }
    }

    public record ContentsEntry(
        @JacksonXmlProperty(localName = "Key") String key,
        @JacksonXmlProperty(localName = "Size") long size,
        @JacksonXmlProperty(localName = "ETag") String etag,
        @JacksonXmlProperty(localName = "StorageClass") String storageClass
    ) {}

    // ── Error ──

    @JacksonXmlRootElement(localName = "Error")
    public record Error(
        @JacksonXmlProperty(localName = "Code") String code,
        @JacksonXmlProperty(localName = "Message") String message
    ) {
        public static Error from(String code, String message) {
            return new Error(code, message);
        }
    }
}
