package com.example.magrathea.s3api.adapter.web.xml;

import com.example.magrathea.objectstorage.application.dto.BucketResponse;
import com.example.magrathea.objectstorage.application.dto.ObjectResponse;
import tools.jackson.dataformat.xml.annotation.JacksonXmlElementWrapper;
import tools.jackson.dataformat.xml.annotation.JacksonXmlProperty;
import tools.jackson.dataformat.xml.annotation.JacksonXmlRootElement;
import tools.jackson.dataformat.xml.annotation.JacksonXmlText;

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
        @JacksonXmlElementWrapper(useWrapping = false)
        @JacksonXmlProperty(localName = "Contents")
        List<ContentsEntry> contents
    ) {
        public static ListBucketResult from(String bucketName, List<ObjectResponse> objects) {
            return new ListBucketResult(
                bucketName, "", "", 1000, false,
                objects.stream().map(ContentsEntry::from).toList()
            );
        }
    }

    public record ContentsEntry(
        @JacksonXmlProperty(localName = "Key") String key,
        @JacksonXmlProperty(localName = "Size") long size,
        @JacksonXmlProperty(localName = "ETag") String etag,
        @JacksonXmlProperty(localName = "StorageClass") String storageClass
    ) {
        public static ContentsEntry from(ObjectResponse object) {
            return new ContentsEntry(
                object.key(),
                object.size(),
                object.etag() != null ? object.etag() : "",
                object.storageClass() != null ? object.storageClass() : "STANDARD"
            );
        }
    }

    // ── ListBucketResult V2 ──

    @JacksonXmlRootElement(localName = "ListBucketResult")
    public record ListBucketV2Result(
        @JacksonXmlProperty(localName = "Name") String name,
        @JacksonXmlProperty(localName = "Prefix") String prefix,
        @JacksonXmlProperty(localName = "KeyCount") int keyCount,
        @JacksonXmlProperty(localName = "MaxKeys") int maxKeys,
        @JacksonXmlProperty(localName = "IsTruncated") boolean isTruncated,
        @JacksonXmlElementWrapper(useWrapping = false)
        @JacksonXmlProperty(localName = "Contents")
        List<ContentsEntry> contents
    ) {
        public static ListBucketV2Result from(String bucketName, List<ObjectResponse> objects) {
            return new ListBucketV2Result(
                bucketName, "", objects.size(), 1000, false,
                objects.stream().map(ContentsEntry::from).toList()
            );
        }
    }

    // ── GetBucketLocation ──

    @JacksonXmlRootElement(localName = "LocationConstraint")
    public record LocationConstraint(
        @JacksonXmlText String location
    ) {}

    // ── Bucket Versioning ──

    @JacksonXmlRootElement(localName = "VersioningConfiguration")
    public record VersioningConfiguration(
        @JacksonXmlProperty(localName = "Status") String status
    ) {
        public static VersioningConfiguration from(boolean enabled) {
            return new VersioningConfiguration(enabled ? "Enabled" : "Suspended");
        }
    }

    // ── CopyObject ──

    @JacksonXmlRootElement(localName = "CopyObjectResult")
    public record CopyObjectResult(
        @JacksonXmlProperty(localName = "LastModified") String lastModified,
        @JacksonXmlProperty(localName = "ETag") String etag
    ) {}

    // ── DeleteObjects ──

    @JacksonXmlRootElement(localName = "DeleteResult")
    public record DeleteResult(
        @JacksonXmlElementWrapper(useWrapping = false)
        @JacksonXmlProperty(localName = "Deleted")
        List<DeletedEntry> deleted
    ) {}

    public record DeletedEntry(
        @JacksonXmlProperty(localName = "Key") String key
    ) {}

    // ── ListObjectVersions ──

    @JacksonXmlRootElement(localName = "ListVersionsResult")
    public record ListVersionsResult(
        @JacksonXmlProperty(localName = "Name") String name,
        @JacksonXmlProperty(localName = "Prefix") String prefix,
        @JacksonXmlProperty(localName = "KeyMarker") String keyMarker,
        @JacksonXmlProperty(localName = "VersionIdMarker") String versionIdMarker,
        @JacksonXmlProperty(localName = "MaxKeys") int maxKeys,
        @JacksonXmlProperty(localName = "IsTruncated") boolean isTruncated,
        @JacksonXmlElementWrapper(useWrapping = false)
        @JacksonXmlProperty(localName = "Version")
        List<VersionEntry> versions
    ) {
        public static ListVersionsResult from(String bucketName, List<ObjectResponse> objects) {
            return new ListVersionsResult(
                bucketName, "", "", "", 1000, false,
                objects.stream().map(VersionEntry::from).toList()
            );
        }
    }

    public record VersionEntry(
        @JacksonXmlProperty(localName = "Key") String key,
        @JacksonXmlProperty(localName = "VersionId") String versionId,
        @JacksonXmlProperty(localName = "IsLatest") boolean isLatest,
        @JacksonXmlProperty(localName = "ETag") String etag,
        @JacksonXmlProperty(localName = "Size") long size,
        @JacksonXmlProperty(localName = "StorageClass") String storageClass
    ) {
        public static VersionEntry from(ObjectResponse object) {
            return new VersionEntry(
                object.key(), "null", true,
                object.etag() != null ? object.etag() : "",
                object.size(),
                object.storageClass() != null ? object.storageClass() : "STANDARD"
            );
        }
    }

    // ── ACL ──

    @JacksonXmlRootElement(localName = "AccessControlPolicy")
    public record AccessControlPolicy(
        @JacksonXmlProperty(localName = "Owner") Owner owner,
        @JacksonXmlElementWrapper(localName = "AccessControlList", useWrapping = true)
        @JacksonXmlProperty(localName = "Grant")
        List<Grant> grants
    ) {
        public static AccessControlPolicy canned(String acl) {
            var grants = new java.util.ArrayList<Grant>();
            grants.add(new Grant(new Grantee("CanonicalUser", "000000000000", "magrathea", null), "FULL_CONTROL"));
            if ("public-read".equals(acl)) {
                grants.add(new Grant(new Grantee("Group", null, null, "http://acs.amazonaws.com/groups/global/AllUsers"), "READ"));
            }
            return new AccessControlPolicy(new Owner("000000000000", "magrathea"), List.copyOf(grants));
        }
    }

    public record Grant(
        @JacksonXmlProperty(localName = "Grantee") Grantee grantee,
        @JacksonXmlProperty(localName = "Permission") String permission
    ) {}

    public record Grantee(
        @JacksonXmlProperty(isAttribute = true, localName = "type", namespace = "http://www.w3.org/2001/XMLSchema-instance") String type,
        @JacksonXmlProperty(localName = "ID") String id,
        @JacksonXmlProperty(localName = "DisplayName") String displayName,
        @JacksonXmlProperty(localName = "URI") String uri
    ) {}

    // ── Tagging ──

    @JacksonXmlRootElement(localName = "Tagging")
    public record Tagging(
        @JacksonXmlElementWrapper(localName = "TagSet", useWrapping = true)
        @JacksonXmlProperty(localName = "Tag")
        List<Tag> tagSet
    ) {}

    public record Tag(
        @JacksonXmlProperty(localName = "Key") String key,
        @JacksonXmlProperty(localName = "Value") String value
    ) {}

    // ── GetObjectAttributes ──

    @JacksonXmlRootElement(localName = "GetObjectAttributesOutput")
    public record GetObjectAttributesOutput(
        @JacksonXmlProperty(localName = "ETag") String etag,
        @JacksonXmlProperty(localName = "ObjectSize") long objectSize,
        @JacksonXmlProperty(localName = "StorageClass") String storageClass
    ) {
        public static GetObjectAttributesOutput from(ObjectResponse object) {
            return new GetObjectAttributesOutput(
                object.etag() != null ? object.etag() : "",
                object.size(),
                object.storageClass() != null ? object.storageClass() : "STANDARD"
            );
        }
    }

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
