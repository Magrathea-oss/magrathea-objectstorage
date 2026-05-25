package com.example.magrathea.s3api.adapter.web;

import com.example.magrathea.objectstorage.domain.aggregate.Bucket;
import com.example.magrathea.objectstorage.domain.aggregate.S3Object;
import com.example.magrathea.objectstorage.domain.valueobject.BucketWebsiteConfiguration;
import com.example.magrathea.objectstorage.domain.valueobject.ObjectKey;
import com.example.magrathea.reactive.application.service.ReactiveBucketService;
import com.example.magrathea.reactive.application.service.ReactiveObjectService;
import com.example.magrathea.s3api.dto.query.ErrorQuery;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.web.reactive.function.server.ServerRequest;
import org.springframework.web.reactive.function.server.ServerResponse;
import reactor.core.publisher.Mono;

import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.util.Optional;
import java.util.Set;

final class S3WebSupport {

    private S3WebSupport() {
    }

    static boolean acceptXml(ServerRequest request) {
        var accept = request.headers().accept();
        if (accept.isEmpty()) {
            return true;
        }
        return accept.stream()
            .anyMatch(mediaType -> mediaType.equals(MediaType.ALL)
                || mediaType.equals(MediaType.APPLICATION_XML)
                || mediaType.includes(MediaType.APPLICATION_XML));
    }

    static boolean acceptJson(ServerRequest request) {
        return request.headers().accept().stream()
            .anyMatch(mediaType -> mediaType.equals(MediaType.APPLICATION_JSON));
    }

    static boolean hasQuery(ServerRequest request, String key) {
        var rawQuery = request.uri().getRawQuery();
        if (rawQuery == null || rawQuery.isBlank()) {
            return false;
        }
        for (var part : rawQuery.split("&")) {
            var name = part.contains("=") ? part.substring(0, part.indexOf('=')) : part;
            if (URLDecoder.decode(name, StandardCharsets.UTF_8).equals(key)) {
                return true;
            }
        }
        return false;
    }

    /**
     * Reactive lookup — finds a bucket by name using ReactiveBucketService.
     */
    static Mono<Bucket> findBucketReactive(ReactiveBucketService bucketService, String bucketName) {
        return bucketService.findByName(bucketName);
    }

    /**
     * Reactive lookup — finds an object by bucket and key using ReactiveObjectService.
     */
    static Mono<S3Object> findObjectReactive(ReactiveObjectService objectService, Bucket.Id bucketId, String key) {
        return objectService.findByBucketAndKey(bucketId, ObjectKey.of(key));
    }

    /**
     * Checks if the request has no S3-specific query parameters.
     * Used to detect "plain" GETs that may be website hosting requests.
     */
    static boolean isPlainGet(ServerRequest request) {
        var rawQuery = request.uri().getRawQuery();
        if (rawQuery == null || rawQuery.isBlank()) {
            return true;
        }
        // S3 API query parameters that indicate S3 operations
        var s3Params = Set.of("acl", "tagging", "location", "versioning", "versions",
            "list-type", "cors", "lifecycle", "policy", "encryption", "logging",
            "website", "notification", "replication", "requestPayment",
            "ownershipControls", "publicAccessBlock", "accelerate", "analytics",
            "inventory", "metrics", "intelligent-tiering", "uploads", "uploadId",
            "delete", "attributes");
        for (var part : rawQuery.split("&")) {
            var name = part.contains("?") ? part.substring(part.indexOf('?') + 1) : part;
            name = name.contains("=") ? name.substring(0, name.indexOf('=')) : name;
            name = URLDecoder.decode(name, StandardCharsets.UTF_8);
            if (s3Params.contains(name)) {
                return false;
            }
        }
        return true;
    }

    static Optional<String[]> decodeCopySource(String header) {
        if (header == null || header.isBlank()) {
            return Optional.empty();
        }
        var withoutLeadingSlash = header.startsWith("/") ? header.substring(1) : header;
        var decoded = URLDecoder.decode(withoutLeadingSlash, StandardCharsets.UTF_8);
        var separator = decoded.indexOf('/');
        if (separator <= 0 || separator == decoded.length() - 1) {
            return Optional.empty();
        }
        return Optional.of(new String[] { decoded.substring(0, separator), decoded.substring(separator + 1) });
    }

    static Mono<ServerResponse> xmlError(HttpStatus status, String code, String message) {
        return ServerResponse.status(status)
            .contentType(MediaType.APPLICATION_XML)
            .bodyValue(ErrorQuery.from(code, message));
    }

    // ─────────────────────────────────────────────────────
    //  CORS Runtime Origin Check
    // ─────────────────────────────────────────────────────

    /**
     * Validates the Origin header against the bucket's CORS configuration.
     * If the request has no Origin header, returns Mono.empty() — no CORS check needed.
     * If the Origin header is present, checks against all CORS rules.
     * Returns Mono.empty() on match (caller should apply CORS headers via {@link #applyCorsHeaders}).
     * Returns a 403 Forbidden error on mismatch.
     */
    static Mono<ServerResponse> validateCorsOrigin(ServerRequest request, Bucket bucket) {
        var origin = request.headers().firstHeader("Origin");
        if (origin == null || origin.isBlank()) {
            return Mono.empty();
        }
        var config = bucket.configuration();
        if (config == null || !config.hasCors()) {
            return xmlError(HttpStatus.FORBIDDEN, "AccessForbidden", "CORS not configured");
        }
        for (var rule : config.corsRules()) {
            for (var allowedOrigin : rule.allowedOrigins()) {
                if (originMatches(origin, allowedOrigin)) {
                    return Mono.empty();
                }
            }
        }
        return xmlError(HttpStatus.FORBIDDEN, "AccessForbidden", "Origin not allowed");
    }

    /**
     * Matches an origin against an allowed origin pattern.
     * Supports wildcard '*' (matches any origin) and wildcard subdomains (e.g. http://*.example.com).
     */
    private static boolean originMatches(String origin, String allowedOrigin) {
        if ("*".equals(allowedOrigin)) {
            return true;
        }
        // Wildcard subdomain pattern: http://*.example.com
        if (allowedOrigin.contains("*")) {
            var parts = allowedOrigin.split("\\*", -1);
            if (parts.length == 0 || parts.length > 2) {
                return false;
            }
            if (parts.length == 1) {
                return origin.equals(parts[0]);
            }
            // prefix + suffix match
            return origin.startsWith(parts[0]) && origin.endsWith(parts[1]);
        }
        return origin.equals(allowedOrigin);
    }

    /**
     * Applies CORS headers to a ServerResponse.Builder for the matching origin.
     * Finds the first CORS rule that allows the given origin and adds:
     * - Access-Control-Allow-Origin
     * - Access-Control-Allow-Methods
     * - Access-Control-Allow-Headers
     * - Access-Control-Expose-Headers (if present)
     * - Access-Control-Max-Age (if > 0)
     */
    static Mono<ServerResponse> applyCorsHeaders(Mono<ServerResponse> response, Bucket bucket, String origin) {
        var config = bucket.configuration();
        if (config == null || !config.hasCors()) {
            return response;
        }
        // Find matching CORS rule and return response with CORS headers
        for (var rule : config.corsRules()) {
            for (var allowedOrigin : rule.allowedOrigins()) {
                if (originMatches(origin, allowedOrigin)) {
                    return response.flatMap(r -> {
                        var builder = ServerResponse.from(r);
                        builder.header("Access-Control-Allow-Origin", origin);
                        if (!rule.allowedMethods().isEmpty()) {
                            builder.header("Access-Control-Allow-Methods",
                                String.join(", ", rule.allowedMethods()));
                        }
                        if (rule.allowedHeaders() != null && !rule.allowedHeaders().isEmpty()) {
                            builder.header("Access-Control-Allow-Headers",
                                String.join(", ", rule.allowedHeaders()));
                        }
                        if (rule.exposeHeaders() != null && !rule.exposeHeaders().isEmpty()) {
                            builder.header("Access-Control-Expose-Headers",
                                String.join(", ", rule.exposeHeaders()));
                        }
                        if (rule.maxAgeSeconds() > 0) {
                            builder.header("Access-Control-Max-Age", String.valueOf(rule.maxAgeSeconds()));
                        }
                        return builder.build();
                    });
                }
            }
        }
        return response;
    }

    // ─────────────────────────────────────────────────────
    //  Website Request Routing
    // ─────────────────────────────────────────────────────

    /**
     * Handles website routing for a bucket with website configuration.
     * <p>
     * Checks if the bucket has website configuration and applies:
     * <ul>
     *   <li>IndexDocument redirect (for root or trailing-slash paths)</li>
     *   <li>ErrorDocument serving (for 404s)</li>
     *   <li>RedirectAllRequestsTo</li>
     *   <li>RoutingRules condition matching</li>
     * </ul>
     * <p>
     * Returns {@code Mono.empty()} if no website configuration is found — the caller
     * should fall through to normal S3 handling.
     * <p>
     * NOTE: The current domain model does not store website configuration on
     * {@link Bucket.Configuration}. Once ADR 0011 is resolved and website fields
     * are added to {@link Bucket.Configuration} or stored via
     * {@link BucketWebsiteConfiguration}, this method will activate the routing logic.
     *
     * @param request the incoming HTTP request
     * @param bucket  the bucket aggregate
     * @return a Mono resolving to a redirect or error response, or Mono.empty() if no website config
     */
    static Mono<ServerResponse> handleWebsiteRequest(ServerRequest request, Bucket bucket) {
        // Website config is not stored on Bucket.Configuration in the current domain model.
        // Always returns Mono.empty() until ADR 0011 adds website fields.
        return Mono.empty();
    }

    /**
     * Overload that accepts a {@link BucketWebsiteConfiguration} directly.
     * Used when website config is available from a separate store.
     *
     * @param request        the incoming HTTP request
     * @param bucket         the bucket aggregate
     * @param websiteConfig  the website configuration value object
     * @return a Mono resolving to a redirect or error response
     */
    static Mono<ServerResponse> handleWebsiteRequest(ServerRequest request, Bucket bucket,
                                                      BucketWebsiteConfiguration websiteConfig) {
        if (websiteConfig == null || !websiteConfig.hasWebsite()) {
            return Mono.empty();
        }
        return doWebsiteRouting(request, bucket, websiteConfig);
    }

    /**
     * Internal website routing logic using {@link BucketWebsiteConfiguration}.
     */
    private static Mono<ServerResponse> doWebsiteRouting(ServerRequest request, Bucket bucket,
                                                          BucketWebsiteConfiguration websiteConfig) {
        var uri = request.uri();
        var path = uri.getPath();
        var bucketName = bucket.name();

        // RedirectAllRequestsTo — redirect all requests to a specific hostname
        var redirectAll = websiteConfig.redirectAllRequestsTo();
        if (redirectAll != null && !redirectAll.isBlank()) {
            var redirectUri = "http://" + redirectAll + path;
            return ServerResponse.status(HttpStatus.FOUND)
                .header("Location", redirectUri)
                .build();
        }

        // IndexDocument — if path is root or ends with '/', redirect to index document
        var indexDocument = websiteConfig.indexDocument();
        if (indexDocument != null && !indexDocument.isBlank()) {
            // Root path or trailing slash: redirect to index document
            if (path == null || path.equals("/" + bucketName) || path.equals("/" + bucketName + "/")) {
                var redirectPath = "/" + bucketName + "/" + indexDocument;
                return ServerResponse.status(HttpStatus.FOUND)
                    .header("Location", redirectPath)
                    .build();
            }
        }

        // Routing rules redirect host/protocol
        var redirectHost = websiteConfig.routingRuleRedirectHost();
        if (redirectHost != null && !redirectHost.isBlank()) {
            var redirectUri = "http://" + redirectHost + path;
            return ServerResponse.status(HttpStatus.FOUND)
                .header("Location", redirectUri)
                .build();
        }

        return Mono.empty();
    }

    /**
     * Checks if the bucket configuration has website configuration.
     * <p>
     * Currently returns false because the domain model's {@link Bucket.Configuration}
     * does not yet have website fields. Once ADR 0011 adds website fields to
     * {@code Configuration}, this method will check for them.
     */
    private static boolean hasWebsiteConfig(Bucket.Configuration config) {
        // Future: config.websiteIndexDocument() != null or config.websiteRedirectAll() != null
        // ADR 0011 scope — domain model needs website fields on Configuration
        return false;
    }

    /**
     * Returns the error document for website hosting when a 404 occurs.
     * <p>
     * If ErrorDocument is configured in the website config, serves it as HTML response body.
     *
     * @param request        the incoming HTTP request
     * @param bucket         the bucket aggregate
     * @param websiteConfig  the website configuration (may be null)
     * @return a Mono resolving to the error document or standard XML error
     */
    static Mono<ServerResponse> serveWebsiteErrorDocument(ServerRequest request, Bucket bucket,
                                                           BucketWebsiteConfiguration websiteConfig) {
        if (websiteConfig == null || !websiteConfig.hasWebsite()) {
            return xmlError(HttpStatus.NOT_FOUND, "NoSuchKey", "Key not found");
        }
        var errorDocument = websiteConfig.errorDocument();
        if (errorDocument != null && !errorDocument.isBlank()) {
            var body = "<html><body><h1>404 - " + errorDocument + "</h1></body></html>";
            return ServerResponse.status(HttpStatus.NOT_FOUND)
                .contentType(MediaType.TEXT_HTML)
                .bodyValue(body);
        }
        return xmlError(HttpStatus.NOT_FOUND, "NoSuchKey", "Key not found");
    }
}
