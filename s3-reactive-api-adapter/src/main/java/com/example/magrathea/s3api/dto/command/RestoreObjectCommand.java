package com.example.magrathea.s3api.dto.command;

import tools.jackson.dataformat.xml.annotation.JacksonXmlProperty;
import tools.jackson.dataformat.xml.annotation.JacksonXmlRootElement;

/**
 * Command DTO for POST /{bucket}/{key}?restore (RestoreObject).
 * S3 RestoreObject XML body for restoring an object from Glacier/Deep Archive.
 */
@JacksonXmlRootElement(localName = "RestoreObject")
public record RestoreObjectCommand(
    @JacksonXmlProperty(localName = "RestoreRequest")
    RestoreRequest restoreRequest
) {
    public record RestoreRequest(
        @JacksonXmlProperty(localName = "GlacierJobParameters")
        GlacierJobParameters glacierJobParameters,
        @JacksonXmlProperty(localName = "Tier")
        String tier,
        @JacksonXmlProperty(localName = "Days")
        int days
    ) {
        public record GlacierJobParameters(
            @JacksonXmlProperty(localName = "Tier")
            String tier
        ) {}
    }
}
