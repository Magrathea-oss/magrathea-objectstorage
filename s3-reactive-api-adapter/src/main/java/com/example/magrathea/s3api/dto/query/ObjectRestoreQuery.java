package com.example.magrathea.s3api.dto.query;

import com.example.magrathea.objectstore.domain.valueobject.RestoreConfiguration;
import tools.jackson.dataformat.xml.annotation.JacksonXmlProperty;
import tools.jackson.dataformat.xml.annotation.JacksonXmlRootElement;

/**
 * Response DTO for GET /{bucket}/{key}?restore — observable object restore state.
 */
@JacksonXmlRootElement(localName = "RestoreState")
public record ObjectRestoreQuery(
    @JacksonXmlProperty(localName = "RestoreRequestedAt")
    String restoreRequestedAt,
    @JacksonXmlProperty(localName = "ExpirationAt")
    String expirationAt,
    @JacksonXmlProperty(localName = "Tier")
    String tier
) {
    public static ObjectRestoreQuery from(RestoreConfiguration restore) {
        return new ObjectRestoreQuery(
            restore.restoreRequestedAt().toString(),
            restore.expirationAt().toString(),
            restore.tier().name());
    }
}
