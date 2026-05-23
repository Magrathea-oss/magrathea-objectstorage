package com.example.magrathea.s3api.dto.query;

import tools.jackson.dataformat.xml.annotation.JacksonXmlProperty;
import tools.jackson.dataformat.xml.annotation.JacksonXmlRootElement;

/**
 * Response for GET /{bucket}?versioning.
 */
@JacksonXmlRootElement(localName = "VersioningConfiguration")
public record VersioningConfigurationQuery(
    @JacksonXmlProperty(localName = "Status")
    String status
) {
    public static VersioningConfigurationQuery from(boolean enabled) {
        return new VersioningConfigurationQuery(enabled ? "Enabled" : "Suspended");
    }
}
