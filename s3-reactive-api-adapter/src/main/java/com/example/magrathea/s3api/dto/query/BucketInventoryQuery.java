package com.example.magrathea.s3api.dto.query;

import com.example.magrathea.objectstorage.domain.model.Bucket;
import com.example.magrathea.objectstorage.domain.valueobject.BucketInventoryConfiguration;
import tools.jackson.dataformat.xml.annotation.JacksonXmlProperty;
import tools.jackson.dataformat.xml.annotation.JacksonXmlRootElement;

import java.util.Optional;

/**
 * Response for GET /{bucket}?inventory (GetBucketInventoryConfiguration).
 */
@JacksonXmlRootElement(localName = "InventoryConfiguration")
public record BucketInventoryQuery(
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

    public static BucketInventoryQuery from(Optional<BucketInventoryConfiguration> config) {
        var c = config.orElseThrow(() -> new IllegalArgumentException("No inventory configuration"));
        var dest = c.format() != null
            ? new InventoryDestination(c.format())
            : null;
        var schedule = c.frequency() != null
            ? new InventorySchedule(c.frequency())
            : null;
        return new BucketInventoryQuery(
            c.inventoryId(), dest, schedule, String.valueOf(c.enabled())
        );
    }

    public static BucketInventoryQuery from(Bucket.BucketConfiguration config) {
        if (!config.hasInventory()) {
            throw new IllegalArgumentException("No inventory configuration");
        }
        var dest = config.inventoryFormat() != null
            ? new InventoryDestination(config.inventoryFormat())
            : null;
        var schedule = config.inventoryFrequency() != null
            ? new InventorySchedule(config.inventoryFrequency())
            : null;
        return new BucketInventoryQuery(
            config.inventoryId(), dest, schedule, String.valueOf(config.inventoryEnabled())
        );
    }
}
