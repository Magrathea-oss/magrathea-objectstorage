package com.example.magrathea.admin.web;

import com.example.magrathea.storageengine.domain.valueobject.*;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.MediaType;
import org.springframework.web.reactive.function.server.RouterFunction;
import org.springframework.web.reactive.function.server.ServerResponse;
import reactor.core.publisher.Mono;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

import static org.springframework.web.reactive.function.server.RequestPredicates.*;
import static org.springframework.web.reactive.function.server.RouterFunctions.route;

@Configuration
public class AdminRouter {

    // In-memory catalog of storage policies
    private final Map<String, StoragePolicy> policies = new ConcurrentHashMap<>();

    public AdminRouter() {
        // Default policies seeded from StorageClass definitions (anti-corruption layer)
        policies.put("STANDARD", StoragePolicy.of(
            StorageClassId.of("STANDARD"),
            ChunkingConfig.of((int) (8L * 1024 * 1024), ChunkAlignment.NONE),
            Optional.empty(),
            Optional.empty(),
            Optional.empty(),
            Optional.empty(),
            ReplicationConfig.of(1)));
        policies.put("STANDARD_IA", StoragePolicy.of(
            StorageClassId.of("STANDARD_IA"),
            ChunkingConfig.of((int) (8L * 1024 * 1024), ChunkAlignment.NONE),
            Optional.empty(),
            Optional.of(CompressionConfig.of(CompressionAlgorithm.GZIP, 6)),
            Optional.empty(),
            Optional.empty(),
            ReplicationConfig.of(1)));
        policies.put("GLACIER", StoragePolicy.of(
            StorageClassId.of("GLACIER"),
            ChunkingConfig.of((int) (64L * 1024 * 1024), ChunkAlignment.NONE),
            Optional.of(DedupConfig.of(DedupScope.BUCKET_LEVEL, FingerprintAlgorithm.SHA256)),
            Optional.of(CompressionConfig.of(CompressionAlgorithm.GZIP, 6)),
            Optional.of(EncryptionPolicy.of(EncryptionAlgorithm.SSE_S3)),
            Optional.empty(),
            ReplicationConfig.of(1)));
    }

    @Bean
    public RouterFunction<ServerResponse> adminRoutes() {
        return route(GET("/admin/health"), req ->
            ServerResponse.ok()
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue(new HealthResponse("ok", "Admin API running", List.of(
                    Map.of("self", Map.of("href", "/admin/health"))))))
            .andRoute(GET("/admin/storage-policies"), req ->
                ServerResponse.ok()
                    .contentType(MediaType.APPLICATION_JSON)
                    .bodyValue(toCollectionResponse()))
            .andRoute(GET("/admin/storage-policies/{id}"), req ->
                ServerResponse.ok()
                    .contentType(MediaType.APPLICATION_JSON)
                    .bodyValue(toItemResponse(req.pathVariable("id"))))
            .andRoute(POST("/admin/storage-policies"), req ->
                req.bodyToMono(StoragePolicyRequest.class)
                    .flatMap(body -> {
                        StoragePolicy policy = body.toDomain();
                        policies.put(policy.id().value(), policy);
                        return ServerResponse.ok()
                            .contentType(MediaType.APPLICATION_JSON)
                            .bodyValue(toItemResponse(policy.id().value()));
                    }))
            .andRoute(PUT("/admin/storage-policies/{id}"), req ->
                req.bodyToMono(StoragePolicyRequest.class)
                    .flatMap(body -> {
                        StoragePolicy policy = body.toDomain();
                        policies.put(req.pathVariable("id"), policy);
                        return ServerResponse.ok()
                            .contentType(MediaType.APPLICATION_JSON)
                            .bodyValue(toItemResponse(policy.id().value()));
                    }))
            .andRoute(DELETE("/admin/storage-policies/{id}"), req -> {
                policies.remove(req.pathVariable("id"));
                return ServerResponse.ok()
                    .contentType(MediaType.APPLICATION_JSON)
                    .bodyValue(Map.of("status", "deleted", "id", req.pathVariable("id"),
                        "_links", Map.of("collection", Map.of("href", "/admin/storage-policies"))));
            });
    }

    private Map<String, Object> toCollectionResponse() {
        List<Map<String, Object>> items = new ArrayList<>();
        for (StoragePolicy p : policies.values()) {
            items.add(toItemMap(p));
        }
        return Map.of(
            "storagePolicies", items,
            "_links", Map.of(
                "self", Map.of("href", "/admin/storage-policies"),
                "create", Map.of("href", "/admin/storage-policies", "method", "POST")));
    }

    private Map<String, Object> toItemResponse(String id) {
        StoragePolicy p = policies.get(id);
        if (p == null) throw new IllegalArgumentException("Storage policy not found: " + id);
        return toItemMap(p);
    }

    private Map<String, Object> toItemMap(StoragePolicy p) {
        Map<String, Object> map = new HashMap<>();
        map.put("storageClassId", p.id().value());
        map.put("chunking", Map.of("chunkSize", p.chunking().chunkSize(), "alignment", p.chunking().alignment().name()));
        p.dedup().ifPresent(d -> map.put("dedup", Map.of("algorithm", d.algorithm().name(), "scope", d.scope().name())));
        p.compression().ifPresent(c -> map.put("compression", Map.of("algorithm", c.algorithm().name(), "level", c.level())));
        p.encryption().ifPresent(e -> map.put("encryption", Map.of("algorithm", e.algorithm().name())));
        p.erasureCoding().ifPresent(e -> map.put("erasureCoding", Map.of("dataBlocks", e.dataBlocks(), "parityBlocks", e.parityBlocks())));
        map.put("replication", Map.of("factor", p.replication().factor()));
        map.put("_links", Map.of(
            "self", Map.of("href", "/admin/storage-policies/" + p.id().value()),
            "update", Map.of("href", "/admin/storage-policies/" + p.id().value(), "method", "PUT"),
            "delete", Map.of("href", "/admin/storage-policies/" + p.id().value(), "method", "DELETE"),
            "collection", Map.of("href", "/admin/storage-policies")));
        return map;
    }

    record HealthResponse(String status, String message, List<Map<String, Object>> _links) {}
    record StoragePolicyRequest(
            String storageClassId,
            int chunkSize,
            String chunkAlignment,
            Map<String, Object> dedup,
            Map<String, Object> compression,
            Map<String, Object> encryption,
            Map<String, Object> erasureCoding,
            int replicationFactor) {
        StoragePolicy toDomain() {
            ChunkAlignment alignment = chunkAlignment == null
                ? ChunkAlignment.NONE
                : ChunkAlignment.valueOf(chunkAlignment);
            return StoragePolicy.of(
                StorageClassId.of(storageClassId),
                ChunkingConfig.of(chunkSize, alignment),
                dedup == null ? Optional.empty() : Optional.of(DedupConfig.of(
                    DedupScope.valueOf((String) dedup.get("scope")),
                    FingerprintAlgorithm.valueOf((String) dedup.get("algorithm")))),
                compression == null ? Optional.empty() : Optional.of(CompressionConfig.of(
                    CompressionAlgorithm.valueOf((String) compression.get("algorithm")),
                    compression.containsKey("level") ? (int) compression.get("level") : 6)),
                encryption == null ? Optional.empty() : Optional.of(EncryptionPolicy.of(
                    EncryptionAlgorithm.valueOf((String) encryption.get("algorithm")))),
                erasureCoding == null ? Optional.empty() : Optional.of(ErasureCodingConfig.of(
                    (int) erasureCoding.get("dataBlocks"), (int) erasureCoding.get("parityBlocks"))),
                ReplicationConfig.of(replicationFactor));
        }
    }
}
