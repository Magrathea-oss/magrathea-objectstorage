package com.example.magrathea.s3api.dto.command;

import tools.jackson.dataformat.xml.annotation.JacksonXmlProperty;
import tools.jackson.dataformat.xml.annotation.JacksonXmlRootElement;

/**
 * Command DTO for PutBucketAccelerateConfiguration XML body.
 */
@JacksonXmlRootElement(localName = "AccelerateConfiguration")
public record AccelerateConfigurationCommand(
    @JacksonXmlProperty(localName = "Status")
    String status
) {}
