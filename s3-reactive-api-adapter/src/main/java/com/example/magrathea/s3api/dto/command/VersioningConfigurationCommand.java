package com.example.magrathea.s3api.dto.command;

import tools.jackson.dataformat.xml.annotation.JacksonXmlProperty;
import tools.jackson.dataformat.xml.annotation.JacksonXmlRootElement;

/**
 * Command DTO for PutBucketVersioning XML body.
 */
@JacksonXmlRootElement(localName = "VersioningConfiguration")
public record VersioningConfigurationCommand(
    @JacksonXmlProperty(localName = "Status")
    String status
) {}
