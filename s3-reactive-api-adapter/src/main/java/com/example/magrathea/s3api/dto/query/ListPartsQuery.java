package com.example.magrathea.s3api.dto.query;

import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

/**
 * Response for GET /{bucket}/{key}?uploadId (ListParts).
 * Builds XML reactively from Flux<PartEntry> without holding the full list in memory.
 */
public record ListPartsQuery(String xmlContent) {

    /**
     * Builds the ListPartsResult XML reactively by streaming each PartEntry
     * into XML fragments and accumulating them in a StringBuilder.
     */
    public static Mono<ListPartsQuery> from(String bucket, String key, String uploadId, Flux<PartEntry> parts) {
        String escapedBucket = xmlEscape(bucket);
        String escapedKey = xmlEscape(key);
        String escapedUploadId = xmlEscape(uploadId);
        return parts
            .map(p -> {
                int partNumber = p.partNumber();
                String etag = xmlEscape(p.etag());
                return "<Part><PartNumber>" + partNumber + "</PartNumber><ETag>" + etag + "</ETag></Part>";
            })
            .collect(StringBuilder::new, (sb, s) -> sb.append(s), StringBuilder::append)
            .map(sb -> {
                String partsXml = sb.toString();
                return "<ListPartsResult><Bucket>" + escapedBucket + "</Bucket><Key>" + escapedKey + "</Key><UploadId>" + escapedUploadId + "</UploadId>" + partsXml + "</ListPartsResult>";
            })
            .map(ListPartsQuery::new);
    }

    /**
     * Returns the raw XML content for direct response body writing.
     */
    public String xmlContent() {
        return xmlContent;
    }

    /**
     * Helper record to hold part entry data for conversion to XML fragments.
     */
    public record PartEntry(
        int partNumber,
        String etag
    ) {
        public static PartEntry from(int partNumber, String etag) {
            return new PartEntry(partNumber, etag);
        }
    }

    private static String xmlEscape(String s) {
        if (s == null) return "";
        return s.replace("&", "&amp;")
                .replace("<", "&lt;")
                .replace(">", "&gt;")
                .replace("\"", "&quot;");
    }
}
