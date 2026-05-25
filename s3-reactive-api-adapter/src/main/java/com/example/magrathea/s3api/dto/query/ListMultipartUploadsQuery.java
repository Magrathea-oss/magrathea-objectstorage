package com.example.magrathea.s3api.dto.query;

import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.time.Instant;

/**
 * Response for GET /{bucket}?uploads (ListMultipartUploads).
 * Builds XML reactively from Flux<UploadEntry> without holding the full list in memory.
 */
public record ListMultipartUploadsQuery(String xmlContent) {

    /**
     * Builds the ListMultipartUploadsResult XML reactively by streaming each UploadEntry
     * into XML fragments and accumulating them in a StringBuilder.
     */
    public static Mono<ListMultipartUploadsQuery> from(String bucket, Flux<UploadEntry> uploads) {
        String escapedBucket = xmlEscape(bucket);
        return uploads
            .map(u -> {
                String key = xmlEscape(u.key());
                String uploadId = xmlEscape(u.uploadId());
                String initiated = xmlEscape(u.initiated());
                return "<Upload><Key>" + key + "</Key><UploadId>" + uploadId + "</UploadId><Initiated>" + initiated + "</Initiated></Upload>";
            })
            .collect(StringBuilder::new, (sb, s) -> sb.append(s), StringBuilder::append)
            .map(sb -> {
                String uploadsXml = sb.toString();
                return "<ListMultipartUploadsResult><Bucket>" + escapedBucket + "</Bucket>" + uploadsXml + "</ListMultipartUploadsResult>";
            })
            .map(ListMultipartUploadsQuery::new);
    }

    /**
     * Returns the raw XML content for direct response body writing.
     */
    public String xmlContent() {
        return xmlContent;
    }

    /**
     * Helper record to hold upload entry data for conversion to XML fragments.
     */
    public record UploadEntry(
        String key,
        String uploadId,
        String initiated
    ) {
        public static UploadEntry from(String key, String uploadId, Instant initiated) {
            return new UploadEntry(key, uploadId, initiated.toString());
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
