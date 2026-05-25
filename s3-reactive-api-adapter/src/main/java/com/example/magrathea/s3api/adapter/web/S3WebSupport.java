package com.example.magrathea.s3api.adapter.web;

import com.example.magrathea.objectstorage.domain.model.Bucket;
import com.example.magrathea.objectstorage.domain.model.S3Object;
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
    static Mono<S3Object> findObjectReactive(ReactiveObjectService objectService, Bucket.BucketId bucketId, String key) {
        var bucketKey = S3Object.ObjectId.BucketKey.of(bucketId, ObjectKey.of(key));
        return objectService.findByBucketAndKey(bucketKey);
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
}
