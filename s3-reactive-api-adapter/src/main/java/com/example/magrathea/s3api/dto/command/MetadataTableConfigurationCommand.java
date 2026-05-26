package com.example.magrathea.s3api.dto.command;

import tools.jackson.dataformat.xml.annotation.JacksonXmlElementWrapper;
import tools.jackson.dataformat.xml.annotation.JacksonXmlProperty;
import tools.jackson.dataformat.xml.annotation.JacksonXmlRootElement;

import java.util.List;

/**
 * Command DTO for PUT/POST /{bucket}?metadata-table-config — MetadataTableConfiguration XML body.
 */
@JacksonXmlRootElement(localName = "MetadataTableConfiguration")
public record MetadataTableConfigurationCommand(
    @JacksonXmlElementWrapper(localName = "MetadataTableRule", useWrapping = false)
    @JacksonXmlProperty(localName = "MetadataTableRule")
    List<MetadataTableRuleDto> rules
) {
    public record MetadataTableRuleDto(
        @JacksonXmlProperty(localName = "Id")
        String id,
        @JacksonXmlProperty(localName = "Status")
        String status,
        @JacksonXmlProperty(localName = "MetadataTableName")
        String metadataTableName,
        @JacksonXmlProperty(localName = "MetadataTableDatabase")
        String metadataTableDatabase
    ) {}
}
