package com.example.magrathea.s3api.dto.command;

import tools.jackson.dataformat.xml.annotation.JacksonXmlProperty;
import tools.jackson.dataformat.xml.annotation.JacksonXmlRootElement;

/**
 * Command DTO for PutBucketInventoryConfiguration XML body.
 */
@JacksonXmlRootElement(localName = "InventoryConfiguration")
public record InventoryConfigurationCommand(
    @JacksonXmlProperty(localName = "Id")
    String id,
    @JacksonXmlProperty(localName = "Destination")
    InventoryDestination destination,
    @JacksonXmlProperty(localName = "Schedule")
    InventorySchedule schedule,
    @JacksonXmlProperty(localName = "Enabled")
    String enabled
) {
    public record InventoryDestination(
        @JacksonXmlProperty(localName = "Format")
        String format
    ) {}

    public record InventorySchedule(
        @JacksonXmlProperty(localName = "Frequency")
        String frequency
    ) {}
}
