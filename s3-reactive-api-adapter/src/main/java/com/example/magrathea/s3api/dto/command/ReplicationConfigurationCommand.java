package com.example.magrathea.s3api.dto.command;

import tools.jackson.dataformat.xml.annotation.JacksonXmlProperty;
import tools.jackson.dataformat.xml.annotation.JacksonXmlElementWrapper;
import tools.jackson.dataformat.xml.annotation.JacksonXmlProperty;
import tools.jackson.dataformat.xml.annotation.JacksonXmlRootElement;

import java.util.List;

/**
 * Command DTO for PutBucketReplication XML body.
 */
@JacksonXmlRootElement(localName = "ReplicationConfiguration")
public record ReplicationConfigurationCommand(
    @JacksonXmlProperty(localName = "Role")
    String role,
    @JacksonXmlElementWrapper(localName = "Rule", useWrapping = false)
    @JacksonXmlProperty(localName = "Rule")
    List<Rule> rules
) {
    public record Rule(
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
