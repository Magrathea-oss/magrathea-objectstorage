package com.example.magrathea.s3api.dto.command;

import tools.jackson.dataformat.xml.annotation.JacksonXmlProperty;
import tools.jackson.dataformat.xml.annotation.JacksonXmlRootElement;

/**
 * Command DTO for PUT /{bucket}?object-lock — PutObjectLockConfiguration XML body.
 */
@JacksonXmlRootElement(localName = "ObjectLockConfiguration")
public record ObjectLockConfigurationCommand(
    @JacksonXmlProperty(localName = "ObjectLockEnabled")
    String objectLockEnabled,
    @JacksonXmlProperty(localName = "Rule")
    Rule rule
) {
    public record Rule(
        @JacksonXmlProperty(localName = "DefaultRetention")
        DefaultRetention defaultRetention
    ) {}

    public record DefaultRetention(
        @JacksonXmlProperty(localName = "Mode")
        String mode,
        @JacksonXmlProperty(localName = "Days")
        Integer days,
        @JacksonXmlProperty(localName = "Years")
        Integer years
    ) {}

    public boolean isEnabled() {
        return "true".equals(objectLockEnabled);
    }
}
