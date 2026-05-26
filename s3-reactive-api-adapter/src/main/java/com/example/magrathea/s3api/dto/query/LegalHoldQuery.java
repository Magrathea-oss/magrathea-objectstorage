package com.example.magrathea.s3api.dto.query;

import tools.jackson.dataformat.xml.annotation.JacksonXmlProperty;
import tools.jackson.dataformat.xml.annotation.JacksonXmlRootElement;

/**
 * Response DTO for GET /{bucket}/{key}?legal-hold — GetObjectLegalHold.
 */
@JacksonXmlRootElement(localName = "LegalHold")
public record LegalHoldQuery(
    @JacksonXmlProperty(localName = "Status")
    String status
) {
    public static LegalHoldQuery from(boolean active) {
        return new LegalHoldQuery(active ? "ON" : "OFF");
    }
}
