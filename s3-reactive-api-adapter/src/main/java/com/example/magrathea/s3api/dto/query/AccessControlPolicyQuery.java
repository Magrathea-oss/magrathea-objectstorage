package com.example.magrathea.s3api.dto.query;

import tools.jackson.dataformat.xml.annotation.JacksonXmlElementWrapper;
import tools.jackson.dataformat.xml.annotation.JacksonXmlProperty;
import tools.jackson.dataformat.xml.annotation.JacksonXmlRootElement;

import java.util.List;

/**
 * Response for GET /{bucket}?acl and GET /{bucket}/{key}?acl.
 */
@JacksonXmlRootElement(localName = "AccessControlPolicy")
public record AccessControlPolicyQuery(
    AccessControlList acl
) {
    public static AccessControlPolicyQuery canned(String acl) {
        String permission = switch (acl) {
            case "public-read" -> "READ";
            case "public-write" -> "WRITE";
            case "public-read-write" -> "FULL_CONTROL";
            case "authenticated-read" -> "READ";
            default -> "READ";
        };
        Grant grant = new Grant(new Grantee(permission));
        return new AccessControlPolicyQuery(new AccessControlList(List.of(grant)));
    }

    public record AccessControlList(
        @JacksonXmlElementWrapper(localName = "Grant", useWrapping = false)
        @JacksonXmlProperty(localName = "Grant")
        List<Grant> grants
    ) {}

    public record Grant(
        Grantee grantee
    ) {}

    public record Grantee(
        @JacksonXmlProperty(localName = "Permission")
        String permission
    ) {}
}
