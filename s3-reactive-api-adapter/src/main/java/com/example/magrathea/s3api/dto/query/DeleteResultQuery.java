package com.example.magrathea.s3api.dto.query;

import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

/**
 * Response for POST /{bucket}?delete (DeleteObjects).
 * Builds XML reactively from Flux<String> keys without holding the full list in memory.
 */
public record DeleteResultQuery(String xmlContent) {

    /**
     * Builds the DeleteResult XML reactively by streaming each key
     * into XML fragments and accumulating them in a StringBuilder.
     */
    public static Mono<DeleteResultQuery> from(Flux<String> keys) {
        return keys
            .map(key -> {
                String escapedKey = xmlEscape(key);
                return "<Deleted><Key>" + escapedKey + "</Key></Deleted>";
            })
            .collect(StringBuilder::new, (sb, s) -> sb.append(s), StringBuilder::append)
            .map(sb -> {
                String deletedXml = sb.toString();
                return "<DeleteResult>" + deletedXml + "</DeleteResult>";
            })
            .map(DeleteResultQuery::new);
    }

    /**
     * Returns the raw XML content for direct response body writing.
     */
    public String xmlContent() {
        return xmlContent;
    }

    private static String xmlEscape(String s) {
        if (s == null) return "";
        return s.replace("&", "&amp;")
                .replace("<", "&lt;")
                .replace(">", "&gt;")
                .replace("\"", "&quot;");
    }
}
