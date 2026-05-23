package com.example.magrathea.s3api.dto.command;

import tools.jackson.dataformat.xml.annotation.JacksonXmlProperty;
import tools.jackson.dataformat.xml.annotation.JacksonXmlRootElement;

/**
 * Command DTO for PutBucketWebsite XML body.
 */
@JacksonXmlRootElement(localName = "WebsiteConfiguration")
public record WebsiteConfigurationCommand(
    @JacksonXmlProperty(localName = "IndexDocument")
    String indexDocument,
    @JacksonXmlProperty(localName = "ErrorDocument")
    String errorDocument,
    @JacksonXmlProperty(localName = "RedirectAllRequestsTo")
    String redirectAllRequestsTo,
    @JacksonXmlProperty(localName = "HostName")
    String hostName,
    @JacksonXmlProperty(localName = "Protocol")
    String protocol
) {}
