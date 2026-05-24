package com.example.magrathea.s3api.dto.query;

import tools.jackson.dataformat.xml.annotation.JacksonXmlElementWrapper;
import tools.jackson.dataformat.xml.annotation.JacksonXmlProperty;
import tools.jackson.dataformat.xml.annotation.JacksonXmlRootElement;

import java.util.List;

/**
 * Response for GET /{bucket}?inventory&list-type (ListBucketInventoryConfigurations).
 */
@JacksonXmlRootElement(localName = "ListInventoryConfigurationsResult")
public record BucketInventoryListQuery(
    @JacksonXmlElementWrapper(useWrapping = false)
    @JacksonXmlProperty(localName = "InventoryConfiguration")
    List<InventoryIdEntry> inventoryConfigurations
) {
    public record InventoryIdEntry(
        @JacksonXmlProperty(localName = "Id")
        String id
    ) {}

    public static BucketInventoryListQuery fromIds(List<String> inventoryIds) {
        return new BucketInventoryListQuery(
            inventoryIds.stream().map(InventoryIdEntry::new).toList()
        );
    }
}
