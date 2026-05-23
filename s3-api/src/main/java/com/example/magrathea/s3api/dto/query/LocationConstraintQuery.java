package com.example.magrathea.s3api.dto.query;

import tools.jackson.dataformat.xml.annotation.JacksonXmlRootElement;
import tools.jackson.dataformat.xml.annotation.JacksonXmlText;

/**
 * Response for GET /{bucket}?location.
 */
@JacksonXmlRootElement(localName = "LocationConstraint")
public record LocationConstraintQuery(
    @JacksonXmlText
    String value
) {
    public static LocationConstraintQuery from(String region) {
        return new LocationConstraintQuery(region);
    }
}
