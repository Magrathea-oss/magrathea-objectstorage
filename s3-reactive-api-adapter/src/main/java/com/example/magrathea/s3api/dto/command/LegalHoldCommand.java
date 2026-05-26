package com.example.magrathea.s3api.dto.command;

import tools.jackson.dataformat.xml.annotation.JacksonXmlProperty;
import tools.jackson.dataformat.xml.annotation.JacksonXmlRootElement;

/**
 * Command DTO for PUT /{bucket}/{key}?legal-hold — PutObjectLegalHold XML body.
 */
@JacksonXmlRootElement(localName = "LegalHold")
public record LegalHoldCommand(
    @JacksonXmlProperty(localName = "Status")
    String status
) {
    public boolean isActive() {
        return "ON".equals(status);
    }
}
