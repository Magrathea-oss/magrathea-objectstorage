package com.example.magrathea.s3api.adapter.web;

import com.example.magrathea.objectstorage.application.dto.CorsConfigurationCommand;
import com.example.magrathea.objectstorage.application.service.BucketService;
import com.example.magrathea.s3api.adapter.web.xml.S3XmlResponses;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.web.reactive.function.server.ServerRequest;
import org.springframework.web.reactive.function.server.ServerResponse;
import reactor.core.publisher.Mono;
import reactor.core.scheduler.Schedulers;

import java.util.List;
import java.util.ArrayList;

/**
 * Bucket configuration operations: CORS, policy, encryption, logging, website, notification.
 */
public class S3BucketConfigHandler {

    private final BucketService bucketService;

    public S3BucketConfigHandler(BucketService bucketService) {
        this.bucketService = bucketService;
    }

    /** GET /{bucket}?cors — GetBucketCors */
    public Mono<ServerResponse> getBucketCors(ServerRequest request) {
        var bucket = request.pathVariable("bucket");
        return Mono.fromCallable(() -> {
            if (S3WebSupport.findBucket(bucketService, bucket).isEmpty()) {
                return S3WebSupport.xmlError(HttpStatus.NOT_FOUND, "NoSuchBucket", "Bucket not found");
            }
            var config = bucketService.getCorsConfiguration(bucket);
            if (config.isEmpty() || !config.get().hasCors()) {
                return S3WebSupport.xmlError(HttpStatus.NOT_FOUND, "NoSuchCorsConfiguration",
                    "The CORS configuration is not found");
            }
            return ServerResponse.ok()
                .contentType(MediaType.APPLICATION_XML)
                .bodyValue(S3XmlResponses.CORSConfiguration.from(bucket, config));
        }).subscribeOn(Schedulers.boundedElastic())
        .flatMap(Mono::from);
    }

    /** PUT /{bucket}?cors — PutBucketCors */
    public Mono<ServerResponse> putBucketCors(ServerRequest request) {
        var bucket = request.pathVariable("bucket");
        return Mono.fromCallable(() -> {
            if (S3WebSupport.findBucket(bucketService, bucket).isEmpty()) {
                return S3WebSupport.xmlError(HttpStatus.NOT_FOUND, "NoSuchBucket", "Bucket not found");
            }
            return request.bodyToMono(String.class)
                .defaultIfEmpty("")
                .flatMap(body -> {
                    var rules = parseCorsXml(body);
                    var cmd = new CorsConfigurationCommand(bucket, rules);
                    bucketService.putCorsConfiguration(cmd);
                    return ServerResponse.ok().build();
                });
        }).subscribeOn(Schedulers.boundedElastic())
        .flatMap(Mono::from);
    }

    /** DELETE /{bucket}?cors — DeleteBucketCors */
    public Mono<ServerResponse> deleteBucketCors(ServerRequest request) {
        var bucket = request.pathVariable("bucket");
        return Mono.fromCallable(() -> {
            if (S3WebSupport.findBucket(bucketService, bucket).isEmpty()) {
                return S3WebSupport.xmlError(HttpStatus.NOT_FOUND, "NoSuchBucket", "Bucket not found");
            }
            bucketService.deleteCorsConfiguration(bucket);
            return ServerResponse.noContent().build();
        }).subscribeOn(Schedulers.boundedElastic())
        .flatMap(Mono::from);
    }

    private static List<CorsConfigurationCommand.CorsRuleDto> parseCorsXml(String body) {
        // Minimal CORS XML parser — extracts CORSRule elements
        var rules = new ArrayList<CorsConfigurationCommand.CorsRuleDto>();
        var ruleMatcher = java.util.regex.Pattern.compile(
            "<CORSRule>(.*?)</CORSRule>", java.util.regex.Pattern.DOTALL
        ).matcher(body);
        while (ruleMatcher.find()) {
            var ruleBody = ruleMatcher.group(1);
            var origins = extractXmlList(ruleBody, "AllowedOrigin");
            var methods = extractXmlList(ruleBody, "AllowedMethod");
            var headers = extractXmlList(ruleBody, "AllowedHeader");
            var exposeHeaders = extractXmlList(ruleBody, "ExposeHeader");
            var maxAge = extractXmlValue(ruleBody, "MaxAgeSeconds");
            var id = extractXmlValue(ruleBody, "ID");
            rules.add(new CorsConfigurationCommand.CorsRuleDto(
                origins, methods, headers,
                maxAge.isEmpty() ? 0 : Integer.parseInt(maxAge),
                exposeHeaders, id.isEmpty() ? null : id
            ));
        }
        return List.copyOf(rules);
    }

    private static List<String> extractXmlList(String body, String tag) {
        var matcher = java.util.regex.Pattern.compile(
            "<" + tag + ">([^<]+)</" + tag + ">", java.util.regex.Pattern.DOTALL
        ).matcher(body);
        var result = new ArrayList<String>();
        while (matcher.find()) {
            result.add(matcher.group(1));
        }
        return result;
    }

    private static String extractXmlValue(String body, String tag) {
        var matcher = java.util.regex.Pattern.compile(
            "<" + tag + ">([^<]+)</" + tag + ">", java.util.regex.Pattern.DOTALL
        ).matcher(body);
        return matcher.find() ? matcher.group(1) : "";
    }
}
