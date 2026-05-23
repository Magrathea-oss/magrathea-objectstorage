package com.example.magrathea.s3api.dto.command;

import tools.jackson.dataformat.xml.annotation.JacksonXmlElementWrapper;
import tools.jackson.dataformat.xml.annotation.JacksonXmlProperty;
import tools.jackson.dataformat.xml.annotation.JacksonXmlRootElement;

import java.util.List;

/**
 * Command DTO for PutBucketTagging / PutObjectTagging XML body.
 */
@JacksonXmlRootElement(localName = "Tagging")
public record TaggingCommand(
    @JacksonXmlProperty(localName = "TagSet")
    TagSet tagSet
) {
    public record TagSet(
        @JacksonXmlElementWrapper(localName = "Tag", useWrapping = false)
        @JacksonXmlProperty(localName = "Tag")
        List<Tag> tags
    ) {}

    public record Tag(
        @JacksonXmlProperty(localName = "Key")
        String key,
        @JacksonXmlProperty(localName = "Value")
        String value
    ) {}
}
