package com.example.magrathea.s3api.adapter.web;

import com.example.magrathea.objectstorage.application.service.BucketService;
import com.example.magrathea.s3api.adapter.web.xml.S3XmlResponses;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.web.reactive.function.server.ServerRequest;
import org.springframework.web.reactive.function.server.ServerResponse;
import reactor.core.publisher.Mono;

import java.util.List;
import java.util.concurrent.ConcurrentHashMap;
import java.util.regex.Pattern;

/**
 * Bucket metadata-context S3 operations: ACL and tagging.
 */
public class S3BucketMetadataHandler {

    private static final Pattern TAG_PATTERN = Pattern.compile(
        "<Tag>\\s*<Key>([^<]+)</Key>\\s*<Value>([^<]*)</Value>\\s*</Tag>",
        Pattern.DOTALL
    );

    private final BucketService bucketService;
    private final ConcurrentHashMap<String, String> bucketAclStore = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<String, List<S3XmlResponses.Tag>> bucketTagStore = new ConcurrentHashMap<>();

    public S3BucketMetadataHandler(BucketService bucketService) {
        this.bucketService = bucketService;
    }

    /** GET /{bucket}?acl — GetBucketAcl */
    public Mono<ServerResponse> getBucketAcl(ServerRequest request) {
        var bucket = request.pathVariable("bucket");
        if (S3WebSupport.findBucket(bucketService, bucket).isEmpty()) {
            return S3WebSupport.xmlError(HttpStatus.NOT_FOUND, "NoSuchBucket", "Bucket not found");
        }
        return ServerResponse.ok()
            .contentType(MediaType.APPLICATION_XML)
            .bodyValue(S3XmlResponses.AccessControlPolicy.canned(bucketAclStore.getOrDefault(bucket, "private")));
    }

    /** PUT /{bucket}?acl — PutBucketAcl */
    public Mono<ServerResponse> putBucketAcl(ServerRequest request) {
        var bucket = request.pathVariable("bucket");
        if (S3WebSupport.findBucket(bucketService, bucket).isEmpty()) {
            return S3WebSupport.xmlError(HttpStatus.NOT_FOUND, "NoSuchBucket", "Bucket not found");
        }
        bucketAclStore.put(bucket, request.headers().firstHeader("x-amz-acl") != null
            ? request.headers().firstHeader("x-amz-acl")
            : "private");
        return ServerResponse.ok().build();
    }

    /** GET /{bucket}?tagging — GetBucketTagging */
    public Mono<ServerResponse> getBucketTagging(ServerRequest request) {
        var bucket = request.pathVariable("bucket");
        if (S3WebSupport.findBucket(bucketService, bucket).isEmpty()) {
            return S3WebSupport.xmlError(HttpStatus.NOT_FOUND, "NoSuchBucket", "Bucket not found");
        }
        return ServerResponse.ok()
            .contentType(MediaType.APPLICATION_XML)
            .bodyValue(new S3XmlResponses.Tagging(bucketTagStore.getOrDefault(bucket, List.of())));
    }

    /** PUT /{bucket}?tagging — PutBucketTagging */
    public Mono<ServerResponse> putBucketTagging(ServerRequest request) {
        var bucket = request.pathVariable("bucket");
        if (S3WebSupport.findBucket(bucketService, bucket).isEmpty()) {
            return S3WebSupport.xmlError(HttpStatus.NOT_FOUND, "NoSuchBucket", "Bucket not found");
        }
        return request.bodyToMono(String.class)
            .defaultIfEmpty("")
            .flatMap(body -> {
                bucketTagStore.put(bucket, parseTags(body));
                return ServerResponse.ok().build();
            });
    }

    /** DELETE /{bucket}?tagging — DeleteBucketTagging */
    public Mono<ServerResponse> deleteBucketTagging(ServerRequest request) {
        var bucket = request.pathVariable("bucket");
        if (S3WebSupport.findBucket(bucketService, bucket).isEmpty()) {
            return S3WebSupport.xmlError(HttpStatus.NOT_FOUND, "NoSuchBucket", "Bucket not found");
        }
        bucketTagStore.remove(bucket);
        return ServerResponse.noContent().build();
    }

    private static List<S3XmlResponses.Tag> parseTags(String body) {
        return TAG_PATTERN.matcher(body).results()
            .map(match -> new S3XmlResponses.Tag(match.group(1), match.group(2)))
            .toList();
    }
}
