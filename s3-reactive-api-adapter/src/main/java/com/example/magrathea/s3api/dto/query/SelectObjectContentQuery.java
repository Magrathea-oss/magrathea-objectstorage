package com.example.magrathea.s3api.dto.query;

import tools.jackson.dataformat.xml.annotation.JacksonXmlProperty;
import tools.jackson.dataformat.xml.annotation.JacksonXmlRootElement;

/**
 * Query DTO for the SelectObjectContent response.
 * S3 returns a payload with Events, Progress, and Stats messages.
 * This is a simplified placeholder response acknowledging the select request.
 */
@JacksonXmlRootElement(localName = "SelectObjectContentResult")
public record SelectObjectContentQuery(
    @JacksonXmlProperty(localName = "Payload")
    Payload payload
) {
    public record Payload(
        @JacksonXmlProperty(localName = "Records")
        Records records,
        @JacksonXmlProperty(localName = "Stats")
        Stats stats,
        @JacksonXmlProperty(localName = "Progress")
        Progress progress
    ) {}

    public record Records(
        @JacksonXmlProperty(localName = "Content")
        String content
    ) {}

    public record Stats(
        @JacksonXmlProperty(localName = "BytesScanned")
        long bytesScanned,
        @JacksonXmlProperty(localName = "BytesReturned")
        long bytesReturned
    ) {}

    public record Progress(
        @JacksonXmlProperty(localName = "BytesScanned")
        long bytesScanned,
        @JacksonXmlProperty(localName = "BytesReturned")
        long bytesReturned
    ) {}

    /**
     * Factory method for a placeholder select result.
     *
     * @param expression the SQL expression that was received
     * @return a placeholder SelectObjectContentQuery acknowledging the request
     */
    public static SelectObjectContentQuery placeholder(String expression) {
        return new SelectObjectContentQuery(
            new Payload(
                new Records("Placeholder: select request acknowledged"),
                new Stats(0, 0),
                new Progress(0, 0)
            )
        );
    }
}
