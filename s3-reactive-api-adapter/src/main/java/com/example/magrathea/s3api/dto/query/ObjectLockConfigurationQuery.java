package com.example.magrathea.s3api.dto.query;

import tools.jackson.dataformat.xml.annotation.JacksonXmlProperty;
import tools.jackson.dataformat.xml.annotation.JacksonXmlRootElement;

/**
 * Response DTO for GET /{bucket}?object-lock — GetObjectLockConfiguration.
 */
@JacksonXmlRootElement(localName = "ObjectLockConfiguration")
public record ObjectLockConfigurationQuery(
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

    public static ObjectLockConfigurationQuery fromEnabled(boolean enabled) {
        if (!enabled) {
            return new ObjectLockConfigurationQuery("false", null);
        }
        return new ObjectLockConfigurationQuery("true",
            new Rule(new DefaultRetention("GOVERNANCE", 5, null)));
    }

    public static ObjectLockConfigurationQuery from(String mode, int days) {
        return new ObjectLockConfigurationQuery("true",
            new Rule(new DefaultRetention(mode, days, null)));
    }

    public static ObjectLockConfigurationQuery from(String mode, int days, int years) {
        return new ObjectLockConfigurationQuery("true",
            new Rule(new DefaultRetention(mode, days, years)));
    }
}
