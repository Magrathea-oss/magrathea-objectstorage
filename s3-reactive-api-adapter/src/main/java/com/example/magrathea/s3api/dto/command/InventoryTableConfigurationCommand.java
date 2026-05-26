package com.example.magrathea.s3api.dto.command;

import tools.jackson.dataformat.xml.annotation.JacksonXmlProperty;
import tools.jackson.dataformat.xml.annotation.JacksonXmlRootElement;

/**
 * Command DTO for PUT /{bucket}?inventory-table-config — InventoryTableConfiguration XML body.
 */
@JacksonXmlRootElement(localName = "InventoryTableConfiguration")
public record InventoryTableConfigurationCommand(
    @JacksonXmlProperty(localName = "Id")
    String id,
    @JacksonXmlProperty(localName = "DestinationFormat")
    String destinationFormat,
    @JacksonXmlProperty(localName = "ScheduleFrequency")
    String scheduleFrequency,
    @JacksonXmlProperty(localName = "Enabled")
    String enabled
) {}
