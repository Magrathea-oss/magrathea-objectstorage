package com.example.magrathea.s3api.dto.query;

import com.example.magrathea.objectstorage.domain.valueobject.BucketLifecycleConfiguration;
import tools.jackson.dataformat.xml.annotation.JacksonXmlElementWrapper;
import tools.jackson.dataformat.xml.annotation.JacksonXmlProperty;
import tools.jackson.dataformat.xml.annotation.JacksonXmlRootElement;

import java.util.List;
import java.util.Optional;

/**
 * Response for GET /{bucket}?lifecycle (GetBucketLifecycleConfiguration).
 */
@JacksonXmlRootElement(localName = "LifecycleConfiguration")
public record BucketLifecycleQuery(
    @JacksonXmlElementWrapper(localName = "Rule")
    @JacksonXmlProperty(localName = "Rule")
    List<RuleEntry> rules
) {
    public static BucketLifecycleQuery from(Optional<BucketLifecycleConfiguration> config) {
        var rules = config.map(c -> c.rules().stream()
            .map(r -> new RuleEntry(
                r.id(), r.status(), r.prefix(),
                r.expiration() != null ? new ExpirationEntry(r.expiration().days(), r.expiration().date()) : null,
                r.noncurrentVersionExpiration() != null
                    ? new NoncurrentVersionExpirationEntry(r.noncurrentVersionExpiration().noncurrentDays()) : null,
                r.abortIncompleteMultipartUpload() != null
                    ? new AbortIncompleteMultipartUploadEntry(r.abortIncompleteMultipartUpload().daysAfterInitiation()) : null))
            .toList()).orElse(List.of());
        return new BucketLifecycleQuery(rules);
    }

    public record RuleEntry(
        @JacksonXmlProperty(localName = "ID")
        String id,
        @JacksonXmlProperty(localName = "Status")
        String status,
        @JacksonXmlProperty(localName = "Prefix")
        String prefix,
        @JacksonXmlProperty(localName = "Expiration")
        ExpirationEntry expiration,
        @JacksonXmlProperty(localName = "NoncurrentVersionExpiration")
        NoncurrentVersionExpirationEntry noncurrentVersionExpiration,
        @JacksonXmlProperty(localName = "AbortIncompleteMultipartUpload")
        AbortIncompleteMultipartUploadEntry abortIncompleteMultipartUpload
    ) {}

    public record ExpirationEntry(
        String days,
        String date
    ) {}

    public record NoncurrentVersionExpirationEntry(
        String noncurrentDays
    ) {}

    public record AbortIncompleteMultipartUploadEntry(
        String daysAfterInitiation
    ) {}
}
