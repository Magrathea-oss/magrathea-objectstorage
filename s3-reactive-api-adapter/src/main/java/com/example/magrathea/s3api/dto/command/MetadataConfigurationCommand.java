package com.example.magrathea.s3api.dto.command;

import tools.jackson.dataformat.xml.annotation.JacksonXmlElementWrapper;
import tools.jackson.dataformat.xml.annotation.JacksonXmlProperty;
import tools.jackson.dataformat.xml.annotation.JacksonXmlRootElement;

import java.util.List;

/**
 * Command DTO for PUT/POST /{bucket}?metadata-config — MetadataConfiguration XML body.
 */
@JacksonXmlRootElement(localName = "MetadataConfiguration")
public record MetadataConfigurationCommand(
    @JacksonXmlElementWrapper(localName = "MetadataRule", useWrapping = false)
    @JacksonXmlProperty(localName = "MetadataRule")
    List<MetadataRuleDto> rules
) {
    public record MetadataRuleDto(
        @JacksonXmlProperty(localName = "Id")
        String id,
        @JacksonXmlProperty(localName = "Status")
        String status,
        @JacksonXmlProperty(localName = "MetadataResourceType")
        String metadataResourceType,
        @JacksonXmlProperty(localName = "MetadataResourceSubtype")
        String metadataResourceSubtype
    ) {}
}
