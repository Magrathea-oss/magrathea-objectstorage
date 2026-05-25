package com.example.magrathea.s3api.dto.query;

import tools.jackson.dataformat.xml.annotation.JacksonXmlElementWrapper;
import tools.jackson.dataformat.xml.annotation.JacksonXmlProperty;
import tools.jackson.dataformat.xml.annotation.JacksonXmlRootElement;

import java.util.List;

/**
 * Response for GET /{bucket}?inventory&list-type (ListBucketInventoryConfigurations).
 * Uses Jackson XML annotations for serialization via Jackson XML codec.
 */
@JacksonXmlRootElement(localName = "ListInventoryConfigurationsResult")
public record BucketInventoryListQuery(
    @JacksonXmlElementWrapper(localName = "InventoryConfiguration", useWrapping = false)
    @JacksonXmlProperty(localName = "InventoryConfiguration")
    List<InventoryConfigurationEntry> configurations
) {
    public record InventoryConfigurationEntry(
        @JacksonXmlProperty(localName = "Id")
        String id
    ) {}
}
