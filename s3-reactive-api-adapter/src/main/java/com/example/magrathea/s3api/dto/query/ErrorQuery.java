package com.example.magrathea.s3api.dto.query;

import tools.jackson.dataformat.xml.annotation.JacksonXmlProperty;
import tools.jackson.dataformat.xml.annotation.JacksonXmlRootElement;

/**
 * Standard S3 XML error response.
 */
@JacksonXmlRootElement(localName = "Error")
public record ErrorQuery(
    @JacksonXmlProperty(localName = "Code")
    String code,

    @JacksonXmlProperty(localName = "Message")
    String message
) {
    public static ErrorQuery from(String code, String message) {
        return new ErrorQuery(code, message);
    }
}
