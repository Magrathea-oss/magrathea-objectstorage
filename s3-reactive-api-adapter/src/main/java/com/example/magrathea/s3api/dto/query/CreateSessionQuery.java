package com.example.magrathea.s3api.dto.query;

import tools.jackson.dataformat.xml.annotation.JacksonXmlProperty;
import tools.jackson.dataformat.xml.annotation.JacksonXmlRootElement;

/**
 * Response for POST /{bucket}?session (CreateSession).
 *
 * <pre>{@code
 * <CreateSessionResult>
 *   <SessionToken>abc123...</SessionToken>
 * </CreateSessionResult>
 * }</pre>
 */
@JacksonXmlRootElement(localName = "CreateSessionResult")
public record CreateSessionQuery(
    @JacksonXmlProperty(localName = "SessionToken")
    String sessionToken
) {

    public static CreateSessionQuery from(String token) {
        return new CreateSessionQuery(token);
    }
}
