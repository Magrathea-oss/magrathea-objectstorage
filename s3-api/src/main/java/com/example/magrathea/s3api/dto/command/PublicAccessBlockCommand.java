package com.example.magrathea.s3api.dto.command;

import tools.jackson.dataformat.xml.annotation.JacksonXmlProperty;
import tools.jackson.dataformat.xml.annotation.JacksonXmlRootElement;

/**
 * Command DTO for PutPublicAccessBlock XML body.
 */
@JacksonXmlRootElement(localName = "PublicAccessBlockConfiguration")
public record PublicAccessBlockCommand(
    @JacksonXmlProperty(localName = "BlockPublicAcls")
    String blockPublicAcls,
    @JacksonXmlProperty(localName = "IgnorePublicAcls")
    String ignorePublicAcls,
    @JacksonXmlProperty(localName = "BlockPublicPolicy")
    String blockPublicPolicy,
    @JacksonXmlProperty(localName = "RestrictPublicBuckets")
    String restrictPublicBuckets
) {}
