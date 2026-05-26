package com.example.magrathea.s3api.dto.command;

import tools.jackson.dataformat.xml.annotation.JacksonXmlProperty;
import tools.jackson.dataformat.xml.annotation.JacksonXmlRootElement;

/**
 * Command DTO for PUT /{bucket}/{key}?retention — PutObjectRetention XML body.
 */
@JacksonXmlRootElement(localName = "Retention")
public record RetentionCommand(
    @JacksonXmlProperty(localName = "Mode")
    String mode,
    @JacksonXmlProperty(localName = "RetainUntilDate")
    String retainUntilDate
) {
    public boolean isCompliance() {
        return "COMPLIANCE".equals(mode);
    }

    public boolean isGovernance() {
        return "GOVERNANCE".equals(mode);
    }
}
