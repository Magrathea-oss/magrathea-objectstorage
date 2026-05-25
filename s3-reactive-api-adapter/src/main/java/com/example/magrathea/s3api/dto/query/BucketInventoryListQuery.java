package com.example.magrathea.s3api.dto.query;

import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

/**
 * Response for GET /{bucket}?inventory&list-type (ListBucketInventoryConfigurations).
 * Builds XML reactively from Flux<String> inventory IDs without holding the full list in memory.
 */
public record BucketInventoryListQuery(String xmlContent) {

    /**
     * Builds the ListInventoryConfigurationsResult XML reactively by streaming each inventory ID
     * into XML fragments and accumulating them in a StringBuilder.
     */
    public static Mono<BucketInventoryListQuery> fromIds(Flux<String> inventoryIds) {
        return inventoryIds
            .map(id -> {
                String escapedId = xmlEscape(id);
                return "<InventoryConfiguration><Id>" + escapedId + "</Id></InventoryConfiguration>";
            })
            .collect(StringBuilder::new, (sb, s) -> sb.append(s), StringBuilder::append)
            .map(sb -> {
                String configsXml = sb.toString();
                return "<ListInventoryConfigurationsResult>" + configsXml + "</ListInventoryConfigurationsResult>";
            })
            .map(BucketInventoryListQuery::new);
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
