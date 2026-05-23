package com.example.magrathea.s3api.dto.command;

import tools.jackson.dataformat.xml.annotation.JacksonXmlProperty;
import tools.jackson.dataformat.xml.annotation.JacksonXmlRootElement;

/**
 * Command DTO for PutBucketLogging XML body.
 */
@JacksonXmlRootElement(localName = "BucketLoggingStatus")
public record LoggingConfigurationCommand(
    @JacksonXmlProperty(localName = "TargetBucket")
    String targetBucket,
    @JacksonXmlProperty(localName = "TargetPrefix")
    String targetPrefix
) {}
