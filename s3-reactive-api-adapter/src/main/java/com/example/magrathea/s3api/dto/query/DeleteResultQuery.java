package com.example.magrathea.s3api.dto.query;

import tools.jackson.dataformat.xml.annotation.JacksonXmlElementWrapper;
import tools.jackson.dataformat.xml.annotation.JacksonXmlProperty;
import tools.jackson.dataformat.xml.annotation.JacksonXmlRootElement;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.util.List;

/**
 * Response for POST /{bucket}?delete (DeleteObjects).
 * Uses Jackson XML annotations for serialization via Jackson XML codec.
 */
@JacksonXmlRootElement(localName = "DeleteResult")
public record DeleteResultQuery(
    @JacksonXmlElementWrapper(localName = "Deleted", useWrapping = false)
    @JacksonXmlProperty(localName = "Deleted")
    List<DeletedEntry> deleted
) {
    /**
     * Builds the DeleteResult reactively by collecting keys flux into a list.
     */
    public static Mono<DeleteResultQuery> from(Flux<String> keys) {
        return keys.map(DeletedEntry::new).collectList()
            .map(DeleteResultQuery::new);
    }

    public record DeletedEntry(
        @JacksonXmlProperty(localName = "Key")
        String key
    ) {}
}
