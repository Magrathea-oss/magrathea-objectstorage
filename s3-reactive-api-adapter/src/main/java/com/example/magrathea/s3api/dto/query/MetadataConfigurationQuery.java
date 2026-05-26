package com.example.magrathea.s3api.dto.query;

import tools.jackson.dataformat.xml.annotation.JacksonXmlElementWrapper;
import tools.jackson.dataformat.xml.annotation.JacksonXmlProperty;
import tools.jackson.dataformat.xml.annotation.JacksonXmlRootElement;

import java.util.List;

/**
 * Response for GET /{bucket}?metadata-config — GetBucketMetadataConfiguration.
 */
@JacksonXmlRootElement(localName = "MetadataConfiguration")
public record MetadataConfigurationQuery(
    @JacksonXmlElementWrapper(localName = "MetadataRule", useWrapping = false)
    @JacksonXmlProperty(localName = "MetadataRule")
    List<MetadataRuleEntry> rules
) {
    public record MetadataRuleEntry(
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
