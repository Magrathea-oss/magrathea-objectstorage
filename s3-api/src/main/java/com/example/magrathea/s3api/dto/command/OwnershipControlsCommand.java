package com.example.magrathea.s3api.dto.command;

import tools.jackson.dataformat.xml.annotation.JacksonXmlProperty;
import tools.jackson.dataformat.xml.annotation.JacksonXmlRootElement;

/**
 * Command DTO for PutBucketOwnershipControls XML body.
 */
@JacksonXmlRootElement(localName = "OwnershipControls")
public record OwnershipControlsCommand(
    @JacksonXmlProperty(localName = "ID")
    String id,
    @JacksonXmlProperty(localName = "Ownership")
    String ownership
) {}
