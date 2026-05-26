package com.example.magrathea.s3api.dto.query;

import tools.jackson.dataformat.xml.annotation.JacksonXmlElementWrapper;
import tools.jackson.dataformat.xml.annotation.JacksonXmlProperty;
import tools.jackson.dataformat.xml.annotation.JacksonXmlRootElement;

import java.util.List;

/**
 * Response for GET /{bucket}?metadata-table-config — GetBucketMetadataTableConfiguration.
 */
@JacksonXmlRootElement(localName = "MetadataTableConfiguration")
public record MetadataTableConfigurationQuery(
    @JacksonXmlElementWrapper(localName = "MetadataTableRule", useWrapping = false)
    @JacksonXmlProperty(localName = "MetadataTableRule")
    List<MetadataTableRuleEntry> rules
) {
    public record MetadataTableRuleEntry(
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
