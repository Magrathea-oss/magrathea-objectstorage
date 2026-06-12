package com.example.magrathea.admin.web;

import com.example.magrathea.storageengine.application.port.DiskSetCatalog;
import com.example.magrathea.storageengine.application.port.StorageDeviceCatalog;
import com.example.magrathea.storageengine.application.port.StoragePolicyCatalog;
import com.example.magrathea.storageengine.domain.valueobject.*;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.web.reactive.function.server.RouterFunction;
import org.springframework.web.reactive.function.server.ServerRequest;
import org.springframework.web.reactive.function.server.ServerResponse;
import reactor.core.publisher.Mono;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.springframework.web.reactive.function.server.RequestPredicates.DELETE;
import static org.springframework.web.reactive.function.server.RequestPredicates.GET;
import static org.springframework.web.reactive.function.server.RequestPredicates.POST;
import static org.springframework.web.reactive.function.server.RequestPredicates.PUT;
import static org.springframework.web.reactive.function.server.RouterFunctions.route;

@Configuration
public class AdminRouter {

    private static final String STORAGE_POLICY_CATALOG = "storage-policy-catalog";
    private static final String STORAGE_DEVICE_CATALOG = "storage-device-catalog";
    private static final String DISK_SET_CATALOG = "disk-set-catalog";

    private final ObjectProvider<StoragePolicyCatalog> storagePolicyCatalog;
    private final ObjectProvider<StorageDeviceCatalog> storageDeviceCatalog;
    private final ObjectProvider<DiskSetCatalog> diskSetCatalog;

    public AdminRouter(
            ObjectProvider<StoragePolicyCatalog> storagePolicyCatalog,
            ObjectProvider<StorageDeviceCatalog> storageDeviceCatalog,
            ObjectProvider<DiskSetCatalog> diskSetCatalog) {
        this.storagePolicyCatalog = storagePolicyCatalog;
        this.storageDeviceCatalog = storageDeviceCatalog;
        this.diskSetCatalog = diskSetCatalog;
    }

    @Bean
    public RouterFunction<ServerResponse> adminRoutes() {
        return route(GET("/admin/health"), this::health)
            .andRoute(GET("/admin/storage-policies"), this::listStoragePolicies)
            .andRoute(GET("/admin/storage-policies/{id}"), this::getStoragePolicy)
            .andRoute(POST("/admin/storage-policies/validate"), this::validateStoragePolicy)
            .andRoute(POST("/admin/storage-policies"), this::readOnlyMutation)
            .andRoute(PUT("/admin/storage-policies/{id}"), this::readOnlyMutation)
            .andRoute(DELETE("/admin/storage-policies/{id}"), this::readOnlyMutation)
            .andRoute(GET("/admin/storage-devices"), this::listStorageDevices)
            .andRoute(GET("/admin/storage-devices/{id}"), this::getStorageDevice)
            .andRoute(GET("/admin/disk-sets"), this::listDiskSets)
            .andRoute(GET("/admin/disk-sets/{id}"), this::getDiskSet);
    }

    private Mono<ServerResponse> health(ServerRequest request) {
        return ok(Map.of(
            "status", "ok",
            "message", "Admin API running",
            "mode", "configuration-as-code",
            "_links", adminLinks()));
    }

    private Mono<ServerResponse> listStoragePolicies(ServerRequest request) {
        return Mono.defer(() -> {
            StoragePolicyCatalog catalog = storagePolicyCatalog.getIfAvailable();
            if (catalog == null) {
                return notConfigured(STORAGE_POLICY_CATALOG, "/admin/storage-policies");
            }
            return catalog.findAll()
                .map(policy -> toPolicyMap(policy, null))
                .collectList()
                .flatMap(items -> ok(collectionResponse("storagePolicies", items,
                    Map.of(
                        "self", link("/admin/storage-policies"),
                        "validate", link("/admin/storage-policies/validate", "POST"),
                        "admin", link("/admin/health")))));
        }).onErrorResume(error -> serviceError(STORAGE_POLICY_CATALOG, error, "/admin/storage-policies"));
    }

    private Mono<ServerResponse> getStoragePolicy(ServerRequest request) {
        String id = request.pathVariable("id");
        return Mono.defer(() -> {
            StoragePolicyCatalog catalog = storagePolicyCatalog.getIfAvailable();
            if (catalog == null) {
                return notConfigured(STORAGE_POLICY_CATALOG, "/admin/storage-policies/" + id);
            }
            return catalog.findById(id)
                .map(policy -> toPolicyMap(policy, id))
                .flatMap(this::ok)
                .switchIfEmpty(notFound("storage-policy-not-found", "Storage policy not found: " + id,
                    "/admin/storage-policies/" + id,
                    Map.of("collection", link("/admin/storage-policies"))));
        }).onErrorResume(error -> serviceError(STORAGE_POLICY_CATALOG, error, "/admin/storage-policies/" + id));
    }

    private Mono<ServerResponse> validateStoragePolicy(ServerRequest request) {
        return request.bodyToMono(StoragePolicyCommand.class)
            .switchIfEmpty(Mono.error(new IllegalArgumentException("Request body is required")))
            .flatMap(command -> Mono.fromCallable(command::toDomain)
                .map(policy -> validationResponse(true, toPolicyMap(policy, null), List.of()))
                .onErrorResume(error -> Mono.just(validationResponse(false, null,
                    List.of(validationError("storagePolicy", error.getMessage()))))))
            .flatMap(this::ok)
            .onErrorResume(error -> badRequest("invalid-storage-policy-validation-request",
                error.getMessage(), "/admin/storage-policies/validate"));
    }

    private Mono<ServerResponse> readOnlyMutation(ServerRequest request) {
        return ServerResponse.status(HttpStatus.METHOD_NOT_ALLOWED)
            .header(HttpHeaders.ALLOW, "GET, HEAD")
            .contentType(MediaType.APPLICATION_JSON)
            .bodyValue(errorBody("admin-catalog-read-only",
                "Admin catalog endpoints are read-only. Change storage policies through configuration-as-code and redeploy/reload the catalog.",
                request.path(),
                Map.of(
                    "collection", link("/admin/storage-policies"),
                    "validate", link("/admin/storage-policies/validate", "POST"))));
    }

    private Mono<ServerResponse> listStorageDevices(ServerRequest request) {
        return Mono.defer(() -> {
            StorageDeviceCatalog catalog = storageDeviceCatalog.getIfAvailable();
            if (catalog == null) {
                return notConfigured(STORAGE_DEVICE_CATALOG, "/admin/storage-devices");
            }
            return catalog.findAll()
                .map(this::toDeviceMap)
                .collectList()
                .flatMap(items -> ok(collectionResponse("storageDevices", items,
                    Map.of(
                        "self", link("/admin/storage-devices"),
                        "admin", link("/admin/health")))));
        }).onErrorResume(error -> serviceError(STORAGE_DEVICE_CATALOG, error, "/admin/storage-devices"));
    }

    private Mono<ServerResponse> getStorageDevice(ServerRequest request) {
        String id = request.pathVariable("id");
        return Mono.defer(() -> {
            StorageDeviceCatalog catalog = storageDeviceCatalog.getIfAvailable();
            if (catalog == null) {
                return notConfigured(STORAGE_DEVICE_CATALOG, "/admin/storage-devices/" + id);
            }
            return catalog.findById(StorageDeviceId.of(id))
                .map(this::toDeviceMap)
                .flatMap(this::ok)
                .switchIfEmpty(notFound("storage-device-not-found", "Storage device not found: " + id,
                    "/admin/storage-devices/" + id,
                    Map.of("collection", link("/admin/storage-devices"))));
        }).onErrorResume(error -> serviceError(STORAGE_DEVICE_CATALOG, error, "/admin/storage-devices/" + id));
    }

    private Mono<ServerResponse> listDiskSets(ServerRequest request) {
        return Mono.defer(() -> {
            DiskSetCatalog catalog = diskSetCatalog.getIfAvailable();
            if (catalog == null) {
                return notConfigured(DISK_SET_CATALOG, "/admin/disk-sets");
            }
            return catalog.findAll()
                .map(this::toDiskSetMap)
                .collectList()
                .flatMap(items -> ok(collectionResponse("diskSets", items,
                    Map.of(
                        "self", link("/admin/disk-sets"),
                        "admin", link("/admin/health")))));
        }).onErrorResume(error -> serviceError(DISK_SET_CATALOG, error, "/admin/disk-sets"));
    }

    private Mono<ServerResponse> getDiskSet(ServerRequest request) {
        String id = request.pathVariable("id");
        return Mono.defer(() -> {
            DiskSetCatalog catalog = diskSetCatalog.getIfAvailable();
            if (catalog == null) {
                return notConfigured(DISK_SET_CATALOG, "/admin/disk-sets/" + id);
            }
            return catalog.findById(id)
                .map(this::toDiskSetMap)
                .flatMap(this::ok)
                .switchIfEmpty(notFound("disk-set-not-found", "Disk set not found: " + id,
                    "/admin/disk-sets/" + id,
                    Map.of("collection", link("/admin/disk-sets"))));
        }).onErrorResume(error -> serviceError(DISK_SET_CATALOG, error, "/admin/disk-sets/" + id));
    }

    private Mono<ServerResponse> ok(Object body) {
        return ServerResponse.ok()
            .contentType(MediaType.APPLICATION_JSON)
            .bodyValue(body);
    }

    private Mono<ServerResponse> badRequest(String code, String message, String path) {
        return ServerResponse.badRequest()
            .contentType(MediaType.APPLICATION_JSON)
            .bodyValue(errorBody(code, message, path, Map.of("admin", link("/admin/health"))));
    }

    private Mono<ServerResponse> notConfigured(String catalogName, String path) {
        return ServerResponse.status(HttpStatus.SERVICE_UNAVAILABLE)
            .contentType(MediaType.APPLICATION_JSON)
            .bodyValue(errorBody("catalog-not-configured",
                "Required catalog bean is not configured: " + catalogName,
                path,
                adminLinks()));
    }

    private Mono<ServerResponse> notFound(String code, String message, String path, Map<String, Object> links) {
        return ServerResponse.status(HttpStatus.NOT_FOUND)
            .contentType(MediaType.APPLICATION_JSON)
            .bodyValue(errorBody(code, message, path, links));
    }

    private Mono<ServerResponse> serviceError(String catalogName, Throwable error, String path) {
        return ServerResponse.status(HttpStatus.SERVICE_UNAVAILABLE)
            .contentType(MediaType.APPLICATION_JSON)
            .bodyValue(errorBody("catalog-unavailable",
                "Catalog is unavailable: " + catalogName,
                path,
                Map.of("message", safeMessage(error)),
                Map.of("admin", link("/admin/health"))));
    }

    private Map<String, Object> collectionResponse(
            String fieldName,
            List<Map<String, Object>> items,
            Map<String, Object> links) {
        Map<String, Object> response = new LinkedHashMap<>();
        response.put(fieldName, items);
        response.put("count", items.size());
        response.put("_links", links);
        return response;
    }

    private Map<String, Object> validationResponse(
            boolean valid,
            Map<String, Object> policy,
            List<Map<String, Object>> errors) {
        Map<String, Object> response = new LinkedHashMap<>();
        response.put("valid", valid);
        response.put("errors", errors);
        if (policy != null) {
            response.put("policy", policy);
        }
        response.put("_links", Map.of(
            "self", link("/admin/storage-policies/validate", "POST"),
            "collection", link("/admin/storage-policies"),
            "admin", link("/admin/health")));
        return response;
    }

    private Map<String, Object> errorBody(
            String code,
            String message,
            String path,
            Map<String, Object> links) {
        return errorBody(code, message, path, Map.of(), links);
    }

    private Map<String, Object> errorBody(
            String code,
            String message,
            String path,
            Map<String, Object> details,
            Map<String, Object> links) {
        Map<String, Object> error = new LinkedHashMap<>();
        error.put("code", code);
        error.put("message", message == null ? "" : message);
        error.put("path", path);
        if (!details.isEmpty()) {
            error.put("details", details);
        }
        return Map.of(
            "error", error,
            "_links", links);
    }

    private Map<String, Object> validationError(String field, String message) {
        return Map.of(
            "field", field,
            "message", message == null ? "Validation failed" : message);
    }

    private Map<String, Object> adminLinks() {
        return Map.of(
            "self", link("/admin/health"),
            "policies", link("/admin/storage-policies"),
            "devices", link("/admin/storage-devices"),
            "diskSets", link("/admin/disk-sets"),
            "validation", link("/admin/storage-policies/validate", "POST"));
    }

    private Map<String, Object> link(String href) {
        return Map.of("href", href);
    }

    private Map<String, Object> link(String href, String method) {
        return Map.of("href", href, "method", method);
    }

    private Map<String, Object> toPolicyMap(StoragePolicy policy, String catalogId) {
        Map<String, Object> map = new LinkedHashMap<>();
        map.put("storageClassId", policy.id().value());
        policy.dedup().ifPresent(dedup -> map.put("dedup", Map.of(
            "scope", dedup.scope().name(),
            "algorithm", dedup.algorithm().name(),
            "chunkSize", dedup.chunkSize(),
            "alignment", dedup.alignment().name())));
        policy.compression().ifPresent(compression -> map.put("compression", Map.of(
            "algorithm", compression.algorithm().name(),
            "level", compression.level())));
        policy.encryption().ifPresent(encryption -> {
            Map<String, Object> encryptionMap = new LinkedHashMap<>();
            encryptionMap.put("algorithm", encryption.algorithm().name());
            encryption.defaultKeyReference().ifPresent(key -> encryptionMap.put("defaultKeyReference", key.keyId()));
            map.put("encryption", encryptionMap);
        });
        policy.erasureCoding().ifPresent(erasureCoding -> map.put("erasureCoding", Map.of(
            "dataBlocks", erasureCoding.dataBlocks(),
            "parityBlocks", erasureCoding.parityBlocks())));
        map.put("replication", Map.of("factor", policy.replication().factor()));
        Map<String, Object> links = new LinkedHashMap<>();
        if (catalogId == null) {
            links.put("itemLookupTemplate", Map.of("href", "/admin/storage-policies/{id}", "templated", true));
        } else {
            links.put("self", link("/admin/storage-policies/" + catalogId));
        }
        links.put("collection", link("/admin/storage-policies"));
        links.put("validate", link("/admin/storage-policies/validate", "POST"));
        map.put("_links", links);
        return map;
    }

    private Map<String, Object> toDeviceMap(StorageDevice device) {
        return Map.of(
            "id", device.id().value(),
            "storagePath", device.storagePath(),
            "totalCapacityBytes", device.totalCapacityBytes(),
            "availableCapacityBytes", device.availableCapacityBytes(),
            "health", device.health().name(),
            "writeEligible", device.isWriteEligible(),
            "readEligible", device.isReadEligible(),
            "_links", Map.of(
                "self", link("/admin/storage-devices/" + device.id().value()),
                "collection", link("/admin/storage-devices")));
    }

    private Map<String, Object> toDiskSetMap(DiskSet diskSet) {
        return Map.of(
            "name", diskSet.name(),
            "failureDomain", diskSet.failureDomain().name(),
            "devices", diskSet.devices().stream().map(StorageDeviceId::value).toList(),
            "size", diskSet.size(),
            "_links", Map.of(
                "self", link("/admin/disk-sets/" + diskSet.name()),
                "collection", link("/admin/disk-sets")));
    }

    private static String safeMessage(Throwable error) {
        return error.getMessage() == null ? error.getClass().getSimpleName() : error.getMessage();
    }

    record StoragePolicyCommand(
            String storageClassId,
            Map<String, Object> dedup,
            Map<String, Object> compression,
            Map<String, Object> encryption,
            Map<String, Object> erasureCoding,
            Map<String, Object> replication,
            Object replicationFactor) {

        StoragePolicy toDomain() {
            return StoragePolicy.of(
                StorageClassId.of(requiredText(storageClassId, "storageClassId")),
                dedup == null ? Optional.empty() : Optional.of(toDedupConfig(dedup)),
                compression == null ? Optional.empty() : Optional.of(toCompressionConfig(compression)),
                encryption == null ? Optional.empty() : Optional.of(toEncryptionPolicy(encryption)),
                erasureCoding == null ? Optional.empty() : Optional.of(toErasureCodingConfig(erasureCoding)),
                ReplicationConfig.of(resolveReplicationFactor()));
        }

        private DedupConfig toDedupConfig(Map<String, Object> value) {
            return DedupConfig.of(
                enumValue(DedupScope.class, value.get("scope"), "dedup.scope"),
                enumValue(FingerprintAlgorithm.class, value.get("algorithm"), "dedup.algorithm"),
                asLong(value.get("chunkSize"), "dedup.chunkSize", DedupConfig.DEFAULT_CHUNK_SIZE),
                value.containsKey("alignment")
                    ? enumValue(ChunkAlignment.class, value.get("alignment"), "dedup.alignment")
                    : ChunkAlignment.NONE);
        }

        private CompressionConfig toCompressionConfig(Map<String, Object> value) {
            return CompressionConfig.of(
                enumValue(CompressionAlgorithm.class, value.get("algorithm"), "compression.algorithm"),
                asInt(value.get("level"), "compression.level", 6));
        }

        private EncryptionPolicy toEncryptionPolicy(Map<String, Object> value) {
            Optional<KeyReference> defaultKeyReference = Optional.ofNullable(value.get("defaultKeyReference"))
                .or(() -> Optional.ofNullable(value.get("keyId")))
                .map(key -> KeyReference.of(requiredText(key, "encryption.defaultKeyReference")));
            return EncryptionPolicy.of(
                enumValue(EncryptionAlgorithm.class, value.get("algorithm"), "encryption.algorithm"),
                defaultKeyReference);
        }

        private ErasureCodingConfig toErasureCodingConfig(Map<String, Object> value) {
            return ErasureCodingConfig.of(
                asInt(value.get("dataBlocks"), "erasureCoding.dataBlocks"),
                asInt(value.get("parityBlocks"), "erasureCoding.parityBlocks"));
        }

        private int resolveReplicationFactor() {
            if (replicationFactor != null) {
                return asInt(replicationFactor, "replicationFactor");
            }
            if (replication != null && replication.containsKey("factor")) {
                return asInt(replication.get("factor"), "replication.factor");
            }
            return 1;
        }

        private static String requiredText(Object value, String field) {
            if (value == null) {
                throw new IllegalArgumentException(field + " is required");
            }
            String text = value.toString();
            if (text.isBlank()) {
                throw new IllegalArgumentException(field + " must not be blank");
            }
            return text;
        }

        private static <E extends Enum<E>> E enumValue(Class<E> type, Object value, String field) {
            String text = requiredText(value, field);
            try {
                return Enum.valueOf(type, text);
            } catch (IllegalArgumentException ex) {
                throw new IllegalArgumentException(field + " has unsupported value: " + text);
            }
        }

        private static int asInt(Object value, String field) {
            return asInt(value, field, null);
        }

        private static int asInt(Object value, String field, Integer defaultValue) {
            if (value == null) {
                if (defaultValue != null) {
                    return defaultValue;
                }
                throw new IllegalArgumentException(field + " is required");
            }
            long number = asLong(value, field, null);
            if (number < Integer.MIN_VALUE || number > Integer.MAX_VALUE) {
                throw new IllegalArgumentException(field + " is outside int range: " + number);
            }
            return (int) number;
        }

        private static long asLong(Object value, String field, Long defaultValue) {
            if (value == null) {
                if (defaultValue != null) {
                    return defaultValue;
                }
                throw new IllegalArgumentException(field + " is required");
            }
            if (value instanceof Number number) {
                double asDouble = number.doubleValue();
                long asLong = number.longValue();
                if (asDouble != (double) asLong) {
                    throw new IllegalArgumentException(field + " must be an integer number: " + value);
                }
                return asLong;
            }
            if (value instanceof String text && !text.isBlank()) {
                try {
                    return Long.parseLong(text);
                } catch (NumberFormatException ex) {
                    throw new IllegalArgumentException(field + " must be an integer number: " + text);
                }
            }
            throw new IllegalArgumentException(field + " must be an integer number");
        }
    }
}
