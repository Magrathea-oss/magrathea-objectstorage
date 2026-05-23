package com.example.magrathea.s3api.adapter.web;

import com.example.magrathea.objectstorage.application.dto.BucketResponse;
import com.example.magrathea.objectstorage.application.dto.ObjectResponse;
import com.example.magrathea.objectstorage.application.service.BucketService;
import com.example.magrathea.objectstorage.application.service.ObjectService;
import com.example.magrathea.s3api.dto.query.ErrorQuery;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.web.reactive.function.server.ServerRequest;
import org.springframework.web.reactive.function.server.ServerResponse;
import reactor.core.publisher.Mono;
import reactor.core.scheduler.Schedulers;

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
     * Blocking lookup — used inside Mono.fromCallable / subscribeOn(Schedulers.boundedElastic).
     * Finds a bucket by name from the full bucket list.
     */
    static Optional<BucketResponse> findBucket(BucketService bucketService, String bucketName) {
        return bucketService.findAll().stream()
            .filter(bucket -> bucket.name().equals(bucketName))
            .findFirst();
    }

    /**
     * Blocking lookup — used inside Mono.fromCallable / subscribeOn(Schedulers.boundedElastic).
     * Finds an object by key within a bucket.
     */
    static Optional<ObjectResponse> findObject(ObjectService objectService, BucketResponse bucket, String key) {
        return objectService.findByBucket(bucket.id()).stream()
            .filter(object -> object.key().equals(key))
            .findFirst();
    }

    /**
     * Reactive lookup — returns Mono<Optional<BucketResponse>> without blocking.
     */
    static Mono<Optional<BucketResponse>> findBucketReactive(BucketService bucketService, String bucketName) {
        return Mono.fromCallable(() ->
            bucketService.findAll().stream()
                .filter(bucket -> bucket.name().equals(bucketName))
                .findFirst()
        ).subscribeOn(Schedulers.boundedElastic());
    }

    /**
     * Reactive lookup — returns Mono<Optional<ObjectResponse>> without blocking.
     */
    static Mono<Optional<ObjectResponse>> findObjectReactive(ObjectService objectService, BucketResponse bucket, String key) {
        return Mono.fromCallable(() ->
            objectService.findByBucket(bucket.id()).stream()
                .filter(object -> object.key().equals(key))
                .findFirst()
        ).subscribeOn(Schedulers.boundedElastic());
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
