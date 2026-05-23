package com.example.magrathea.s3api.dto.query;

import tools.jackson.dataformat.xml.annotation.JacksonXmlElementWrapper;
import tools.jackson.dataformat.xml.annotation.JacksonXmlProperty;
import tools.jackson.dataformat.xml.annotation.JacksonXmlRootElement;

import java.util.List;

/**
 * Response for GET /{bucket}?tagging and GET /{bucket}/{key}?tagging.
 */
@JacksonXmlRootElement(localName = "Tagging")
public record TaggingQuery(
    @JacksonXmlElementWrapper(localName = "TagSet")
    @JacksonXmlProperty(localName = "TagSet")
    TagSet tagSet
) {
    public record TagSet(
        @JacksonXmlElementWrapper(localName = "Tag")
        @JacksonXmlProperty(localName = "Tag")
        List<TagEntry> tags
    ) {}

    public record TagEntry(
        @JacksonXmlProperty(localName = "Key")
        String key,
        @JacksonXmlProperty(localName = "Value")
        String value
    ) {}
}
