package com.example.magrathea.s3api.dto.query;

import tools.jackson.dataformat.xml.annotation.JacksonXmlElementWrapper;
import tools.jackson.dataformat.xml.annotation.JacksonXmlProperty;
import tools.jackson.dataformat.xml.annotation.JacksonXmlRootElement;

import java.util.List;

/**
 * Response for POST /{bucket}?delete (DeleteObjects).
 */
@JacksonXmlRootElement(localName = "DeleteResult")
public record DeleteResultQuery(
    @JacksonXmlElementWrapper(localName = "Deleted")
    @JacksonXmlProperty(localName = "Deleted")
    List<DeletedEntry> deleted
) {
    public record DeletedEntry(
        @JacksonXmlProperty(localName = "Key")
        String key
    ) {}

    public static DeleteResultQuery from(List<String> keys) {
        return new DeleteResultQuery(
            keys.stream().map(DeletedEntry::new).toList()
        );
    }
}
