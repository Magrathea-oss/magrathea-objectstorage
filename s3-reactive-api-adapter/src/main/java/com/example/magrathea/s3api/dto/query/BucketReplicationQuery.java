package com.example.magrathea.s3api.dto.query;

import com.example.magrathea.objectstore.domain.aggregate.Bucket;
import com.example.magrathea.objectstore.domain.valueobject.BucketReplicationConfiguration;
import tools.jackson.dataformat.xml.annotation.JacksonXmlProperty;
import tools.jackson.dataformat.xml.annotation.JacksonXmlRootElement;

import java.util.List;
import java.util.Optional;

/**
 * Response for GET /{bucket}?replication (GetBucketReplication).
 */
@JacksonXmlRootElement(localName = "ReplicationConfiguration")
public record BucketReplicationQuery(
    @JacksonXmlProperty(localName = "Role")
    String role,
    @JacksonXmlProperty(localName = "Rule")
    List<RuleEntry> rules
) {
    public static BucketReplicationQuery from(Optional<BucketReplicationConfiguration> config) {
        var c = config.orElseThrow(() -> new IllegalArgumentException("No replication configuration"));
        return new BucketReplicationQuery(c.role(), c.rules().stream()
            .map(r -> new RuleEntry(r.id(), r.status(), r.prefix(), r.destinationBucket(), r.destinationStorageClass()))
            .toList());
    }


    public record RuleEntry(
        @JacksonXmlProperty(localName = "ID")
        String id,
        @JacksonXmlProperty(localName = "Status")
        String status,
        @JacksonXmlProperty(localName = "Prefix")
        String prefix,
        @JacksonXmlProperty(localName = "DestinationBucket")
        String destinationBucket,
        @JacksonXmlProperty(localName = "DestinationStorageClass")
        String destinationStorageClass
    ) {}
}
