package com.example.magrathea.s3api.adapter.web;

import com.example.magrathea.objectstore.domain.aggregate.Bucket;
import com.example.magrathea.objectstore.domain.aggregate.S3Object;
import com.example.magrathea.objectstore.domain.valueobject.BucketWebsiteConfiguration;
import com.example.magrathea.objectstore.domain.valueobject.CorsConfiguration;
import com.example.magrathea.objectstore.domain.valueobject.ObjectKey;
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
import java.util.Map;
import java.util.Optional;
import java.util.StringJoiner;

final class S3WebSupport {

    private S3WebSupport() {
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
    static Mono<S3Object> findObjectReactive(ReactiveObjectService objectService, Bucket.Id bucketId, String bucketName, String key) {
        return objectService.findByBucketAndKey(bucketId, ObjectKey.of(bucketName, key));
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
    //  Runtime bucket configuration effects
    // ─────────────────────────────────────────────────────

    static Mono<ServerResponse> validateRuntimeRequest(ServerRequest request, Bucket bucket) {
        return validateCorsOrigin(request, bucket)
            .switchIfEmpty(Mono.defer(() -> validateRequestPayment(request, bucket)))
            .switchIfEmpty(Mono.defer(() -> validatePublicAccessBlockRequest(request, bucket)));
    }

    static void applyRuntimeHeaders(ServerResponse.HeadersBuilder<?> builder,
                                    ServerRequest request,
                                    Bucket bucket) {
        applyCorsHeaders(builder, request, bucket);
        applyAccelerateHeaders(builder, bucket);
    }

    static Mono<ServerResponse> validatePublicAclMutation(Bucket bucket, String acl) {
        var publicAccessBlock = bucket.bucketConfig() != null
            ? bucket.bucketConfig().getPublicAccessBlockConfiguration().orElse(null) : null;
        if (publicAccessBlock != null && publicAccessBlock.blockPublicAcls() && isPublicAcl(acl)) {
            return xmlError(HttpStatus.FORBIDDEN, "AccessDenied",
                "Public ACLs are blocked by PublicAccessBlock");
        }
        return Mono.empty();
    }

    static String visibleAcl(Bucket bucket, String acl) {
        var publicAccessBlock = bucket.bucketConfig() != null
            ? bucket.bucketConfig().getPublicAccessBlockConfiguration().orElse(null) : null;
        if (publicAccessBlock != null && publicAccessBlock.ignorePublicAcls() && isPublicAcl(acl)) {
            return "private";
        }
        return acl;
    }

    static Map<String, String> objectOwnershipMetadata(Bucket bucket, ServerRequest request) {
        var configured = bucket.bucketConfig() != null
            ? bucket.bucketConfig().getOwnershipControls().orElse(null) : null;
        var ownership = configured != null && configured.ownership() != null && !configured.ownership().isBlank()
            ? configured.ownership() : "ObjectWriter";
        var owner = switch (ownership) {
            case "BucketOwnerEnforced", "BucketOwnerPreferred" -> bucket.name();
            default -> Optional.ofNullable(request.headers().firstHeader("x-amz-account-id"))
                .filter(value -> !value.isBlank())
                .orElse("object-writer");
        };
        return Map.of("x-amz-object-ownership", ownership, "x-amz-object-owner", owner);
    }

    private static Mono<ServerResponse> validateRequestPayment(ServerRequest request, Bucket bucket) {
        var requestPayment = bucket.bucketConfig() != null
            ? bucket.bucketConfig().getRequestPaymentConfiguration().orElse(null) : null;
        if (requestPayment == null || !"Requester".equals(requestPayment.payer())) {
            return Mono.empty();
        }
        var payer = request.headers().firstHeader("x-amz-request-payer");
        if (payer != null && "requester".equalsIgnoreCase(payer)) {
            return Mono.empty();
        }
        return xmlError(HttpStatus.FORBIDDEN, "AccessDenied",
            "Requester pays bucket requires x-amz-request-payer: requester");
    }

    private static Mono<ServerResponse> validatePublicAccessBlockRequest(ServerRequest request, Bucket bucket) {
        var publicAccessBlock = bucket.bucketConfig() != null
            ? bucket.bucketConfig().getPublicAccessBlockConfiguration().orElse(null) : null;
        var policy = bucket.bucketConfig() != null
            ? bucket.bucketConfig().getBucketPolicy().orElse(null) : null;
        if (publicAccessBlock != null
            && publicAccessBlock.restrictPublicBuckets()
            && isPublicPolicy(policy)
            && request.headers().firstHeader("Authorization") == null) {
            return xmlError(HttpStatus.FORBIDDEN, "AccessDenied",
                "Public bucket access is restricted by PublicAccessBlock");
        }
        return Mono.empty();
    }

    private static boolean isPublicAcl(String acl) {
        if (acl == null) {
            return false;
        }
        return switch (acl) {
            case "public-read", "public-read-write", "authenticated-read" -> true;
            default -> false;
        };
    }

    private static boolean isPublicPolicy(String policy) {
        if (policy == null || policy.isBlank()) {
            return false;
        }
        var compact = policy.replace(" ", "").replace("\n", "").replace("\r", "").replace("\t", "");
        return compact.contains("\"Effect\":\"Allow\"")
            && (compact.contains("\"Principal\":\"*\"")
                || compact.contains("\"Principal\":{\"AWS\":\"*\"}"));
    }

    private static void applyCorsHeaders(ServerResponse.HeadersBuilder<?> builder,
                                         ServerRequest request,
                                         Bucket bucket) {
        var origin = request.headers().firstHeader("Origin");
        if (origin == null || origin.isBlank()) {
            return;
        }
        var corsConfig = bucket.bucketConfig() != null
            ? bucket.bucketConfig().getCorsConfiguration().orElse(null) : null;
        var rule = matchingCorsRule(corsConfig, origin).orElse(null);
        if (rule == null) {
            return;
        }
        builder.header("Access-Control-Allow-Origin", origin);
        if (rule.allowedMethods() != null && !rule.allowedMethods().isEmpty()) {
            builder.header("Access-Control-Allow-Methods", commaSeparated(rule.allowedMethods()));
        }
        if (rule.allowedHeaders() != null && !rule.allowedHeaders().isEmpty()) {
            builder.header("Access-Control-Allow-Headers", commaSeparated(rule.allowedHeaders()));
        }
        if (rule.exposeHeaders() != null && !rule.exposeHeaders().isEmpty()) {
            builder.header("Access-Control-Expose-Headers", commaSeparated(rule.exposeHeaders()));
        }
        if (rule.maxAgeSeconds() > 0) {
            builder.header("Access-Control-Max-Age", String.valueOf(rule.maxAgeSeconds()));
        }
    }

    private static void applyAccelerateHeaders(ServerResponse.HeadersBuilder<?> builder, Bucket bucket) {
        var accelerate = bucket.bucketConfig() != null
            ? bucket.bucketConfig().getAccelerateConfiguration().orElse(null) : null;
        if (accelerate != null && "Enabled".equalsIgnoreCase(accelerate.status())) {
            builder.header("x-amz-transfer-acceleration", "Enabled");
        }
    }

    private static Optional<CorsConfiguration.CorsRule> matchingCorsRule(CorsConfiguration corsConfig, String origin) {
        if (corsConfig == null || corsConfig.corsRules() == null || corsConfig.corsRules().isEmpty()) {
            return Optional.empty();
        }
        return corsConfig.corsRules().stream()
            .filter(rule -> rule.allowedOrigins() != null
                && rule.allowedOrigins().stream().anyMatch(allowed -> originMatches(origin, allowed)))
            .findFirst();
    }

    private static String commaSeparated(Iterable<String> values) {
        var builder = new StringJoiner(", ");
        values.forEach(builder::add);
        return builder.toString();
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
        var corsConfig = bucket.bucketConfig() != null
            ? bucket.bucketConfig().getCorsConfiguration().orElse(null) : null;
        if (corsConfig == null || corsConfig.corsRules() == null || corsConfig.corsRules().isEmpty()) {
            return xmlError(HttpStatus.FORBIDDEN, "AccessForbidden", "CORS not configured");
        }
        for (var rule : corsConfig.corsRules()) {
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
        var corsConfig = bucket.bucketConfig() != null
            ? bucket.bucketConfig().getCorsConfiguration().orElse(null) : null;
        if (corsConfig == null || corsConfig.corsRules() == null || corsConfig.corsRules().isEmpty()) {
            return response;
        }
        // Find matching CORS rule and return response with CORS headers
        for (var rule : corsConfig.corsRules()) {
            for (var allowedOrigin : rule.allowedOrigins()) {
                if (originMatches(origin, allowedOrigin)) {
                    return response.flatMap(r -> {
                        var builder = ServerResponse.from(r);
                        builder.header("Access-Control-Allow-Origin", origin);
                        if (!rule.allowedMethods().isEmpty()) {
                            builder.header("Access-Control-Allow-Methods",
                                commaSeparated(rule.allowedMethods()));
                        }
                        if (rule.allowedHeaders() != null && !rule.allowedHeaders().isEmpty()) {
                            builder.header("Access-Control-Allow-Headers",
                                commaSeparated(rule.allowedHeaders()));
                        }
                        if (rule.exposeHeaders() != null && !rule.exposeHeaders().isEmpty()) {
                            builder.header("Access-Control-Expose-Headers",
                                commaSeparated(rule.exposeHeaders()));
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
     * Returns {@code Mono.empty()} when the bucket has no website runtime config,
     * letting the caller fall through to normal S3 handling.
     */
    static Mono<ServerResponse> handleWebsiteRequest(ServerRequest request, Bucket bucket) {
        var websiteConfig = bucket.bucketConfig() != null
            ? bucket.bucketConfig().getWebsiteConfiguration().orElse(null) : null;
        return handleWebsiteRequest(request, bucket, websiteConfig);
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
        if (!hasWebsiteRuntimeConfig(websiteConfig)) {
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
            var protocol = websiteConfig.routingRuleRedirectProtocol() != null
                && !websiteConfig.routingRuleRedirectProtocol().isBlank()
                    ? websiteConfig.routingRuleRedirectProtocol() : "http";
            var redirectUri = protocol + "://" + redirectAll + path;
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
            var protocol = websiteConfig.routingRuleRedirectProtocol() != null
                && !websiteConfig.routingRuleRedirectProtocol().isBlank()
                    ? websiteConfig.routingRuleRedirectProtocol() : "http";
            var redirectUri = protocol + "://" + redirectHost + path;
            return ServerResponse.status(HttpStatus.FOUND)
                .header("Location", redirectUri)
                .build();
        }

        return Mono.empty();
    }

    private static boolean hasWebsiteRuntimeConfig(BucketWebsiteConfiguration config) {
        return config != null
            && ((config.indexDocument() != null && !config.indexDocument().isBlank())
                || (config.errorDocument() != null && !config.errorDocument().isBlank())
                || (config.redirectAllRequestsTo() != null && !config.redirectAllRequestsTo().isBlank())
                || (config.routingRuleRedirectHost() != null && !config.routingRuleRedirectHost().isBlank()));
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
