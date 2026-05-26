package com.example.magrathea.s3api.dto.query;

import tools.jackson.dataformat.xml.annotation.JacksonXmlProperty;
import tools.jackson.dataformat.xml.annotation.JacksonXmlRootElement;

/**
 * Response for GET /{bucket}?inventory-table-config — GetBucketInventoryTableConfiguration.
 */
@JacksonXmlRootElement(localName = "InventoryTableConfiguration")
public record InventoryTableConfigurationQuery(
    @JacksonXmlProperty(localName = "Id")
    String id,
    @JacksonXmlProperty(localName = "DestinationFormat")
    String destinationFormat,
    @JacksonXmlProperty(localName = "ScheduleFrequency")
    String scheduleFrequency,
    @JacksonXmlProperty(localName = "Enabled")
    String enabled
) {}
