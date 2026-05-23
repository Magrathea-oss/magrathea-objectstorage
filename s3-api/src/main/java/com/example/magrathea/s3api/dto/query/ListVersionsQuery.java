package com.example.magrathea.s3api.dto.query;

import com.example.magrathea.objectstorage.application.dto.ObjectResponse;
import tools.jackson.dataformat.xml.annotation.JacksonXmlElementWrapper;
import tools.jackson.dataformat.xml.annotation.JacksonXmlProperty;
import tools.jackson.dataformat.xml.annotation.JacksonXmlRootElement;

import java.util.List;

/**
 * Response for GET /{bucket}?versions (ListObjectVersions).
 */
@JacksonXmlRootElement(localName = "ListVersionsResult")
public record ListVersionsQuery(
    String bucket,
    @JacksonXmlElementWrapper(localName = "Version")
    @JacksonXmlProperty(localName = "Version")
    List<VersionEntry> versions
) {
    public static ListVersionsQuery from(String bucket, List<ObjectResponse> objects) {
        return new ListVersionsQuery(bucket,
            objects.stream()
                .map(o -> new VersionEntry(o.key(), "\"\""))
                .toList());
    }

    public record VersionEntry(
        String key,
        String etag
    ) {}
}
