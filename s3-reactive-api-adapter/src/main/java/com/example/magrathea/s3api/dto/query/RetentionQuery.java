package com.example.magrathea.s3api.dto.query;

import tools.jackson.dataformat.xml.annotation.JacksonXmlProperty;
import tools.jackson.dataformat.xml.annotation.JacksonXmlRootElement;

/**
 * Response DTO for GET /{bucket}/{key}?retention — GetObjectRetention.
 */
@JacksonXmlRootElement(localName = "Retention")
public record RetentionQuery(
    @JacksonXmlProperty(localName = "Mode")
    String mode,
    @JacksonXmlProperty(localName = "RetainUntilDate")
    String retainUntilDate
) {
    public static RetentionQuery from(String mode, String retainUntilDate) {
        return new RetentionQuery(mode, retainUntilDate);
    }
}
