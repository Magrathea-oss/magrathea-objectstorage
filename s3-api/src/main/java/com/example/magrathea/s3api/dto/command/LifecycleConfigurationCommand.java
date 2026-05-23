package com.example.magrathea.s3api.dto.command;

import tools.jackson.dataformat.xml.annotation.JacksonXmlProperty;
import tools.jackson.dataformat.xml.annotation.JacksonXmlElementWrapper;
import tools.jackson.dataformat.xml.annotation.JacksonXmlProperty;
import tools.jackson.dataformat.xml.annotation.JacksonXmlRootElement;

import java.util.List;

/**
 * Command DTO for PutBucketLifecycleConfiguration XML body.
 */
@JacksonXmlRootElement(localName = "LifecycleConfiguration")
public record LifecycleConfigurationCommand(
    @JacksonXmlElementWrapper(localName = "Rule", useWrapping = false)
    @JacksonXmlProperty(localName = "Rule")
    List<Rule> rules
) {
    public record Rule(
        @JacksonXmlProperty(localName = "ID")
        String id,
        @JacksonXmlProperty(localName = "Status")
        String status,
        @JacksonXmlProperty(localName = "Prefix")
        String prefix,
        @JacksonXmlProperty(localName = "Expiration")
        Expiration expiration,
        @JacksonXmlProperty(localName = "NoncurrentVersionExpiration")
        NoncurrentVersionExpiration noncurrentVersionExpiration,
        @JacksonXmlProperty(localName = "AbortIncompleteMultipartUpload")
        AbortIncompleteMultipartUpload abortIncompleteMultipartUpload
    ) {}

    public record Expiration(
        String days,
        String date
    ) {}

    public record NoncurrentVersionExpiration(
        String noncurrentDays
    ) {}

    public record AbortIncompleteMultipartUpload(
        String daysAfterInitiation
    ) {}
}
