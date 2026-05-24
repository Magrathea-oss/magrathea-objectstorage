package com.example.magrathea.s3api.dto.command;

import tools.jackson.dataformat.xml.annotation.JacksonXmlProperty;
import tools.jackson.dataformat.xml.annotation.JacksonXmlRootElement;

/**
 * Command DTO for PutBucketIntelligentTieringConfiguration XML body.
 */
@JacksonXmlRootElement(localName = "IntelligentTieringConfiguration")
public record IntelligentTieringConfigurationCommand(
    @JacksonXmlProperty(localName = "Id")
    String id,
    @JacksonXmlProperty(localName = "TieringPolicy")
    IntelligentTieringPolicy tieringPolicy,
    @JacksonXmlProperty(localName = "AutoTieringStatus")
    String autoTieringStatus
) {
    public record IntelligentTieringPolicy(
        @JacksonXmlProperty(localName = "TieringRule")
        String tieringRule
    ) {}
}
