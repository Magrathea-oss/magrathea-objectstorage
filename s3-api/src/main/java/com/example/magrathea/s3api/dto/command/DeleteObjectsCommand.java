package com.example.magrathea.s3api.dto.command;

import tools.jackson.dataformat.xml.annotation.JacksonXmlElementWrapper;
import tools.jackson.dataformat.xml.annotation.JacksonXmlProperty;
import tools.jackson.dataformat.xml.annotation.JacksonXmlRootElement;

import java.util.List;

/**
 * Command DTO for DeleteObjects XML body (POST /{bucket}?delete).
 */
@JacksonXmlRootElement(localName = "Delete")
public record DeleteObjectsCommand(
    @JacksonXmlElementWrapper(localName = "Object", useWrapping = false)
    @JacksonXmlProperty(localName = "Object")
    List<ObjectEntry> objects
) {
    public record ObjectEntry(
        @JacksonXmlProperty(localName = "Key")
        String key
    ) {}
}
