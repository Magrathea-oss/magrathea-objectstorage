package com.example.magrathea.s3api.dto.command;

import tools.jackson.dataformat.xml.annotation.JacksonXmlProperty;
import tools.jackson.dataformat.xml.annotation.JacksonXmlRootElement;

/**
 * Command DTO for PUT /{bucket}?journal-table-config — JournalTableConfiguration XML body.
 */
@JacksonXmlRootElement(localName = "JournalTableConfiguration")
public record JournalTableConfigurationCommand(
    @JacksonXmlProperty(localName = "Id")
    String id,
    @JacksonXmlProperty(localName = "DestinationFormat")
    String destinationFormat,
    @JacksonXmlProperty(localName = "ScheduleFrequency")
    String scheduleFrequency,
    @JacksonXmlProperty(localName = "Enabled")
    String enabled
) {}
