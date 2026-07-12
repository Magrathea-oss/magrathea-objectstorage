package com.example.magrathea.admin.infrastructure;

import com.example.magrathea.admin.application.port.AdminBackendStatusProvider;
import com.example.magrathea.storageengine.application.port.DiskSetCatalog;
import com.example.magrathea.storageengine.application.port.StorageDeviceCatalog;
import com.example.magrathea.storageengine.application.port.StoragePolicyCatalog;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.core.env.Environment;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Mono;
import reactor.core.scheduler.Schedulers;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.Map;

/** Reads backend status from the active Spring configuration and real catalog providers. */
@Component
public final class ConfiguredAdminBackendStatusProvider implements AdminBackendStatusProvider {

    public static final String BACKEND_PROPERTY = "magrathea.object-store.backend";

    private final Environment environment;
    private final ObjectProvider<StoragePolicyCatalog> policies;
    private final ObjectProvider<StorageDeviceCatalog> devices;
    private final ObjectProvider<DiskSetCatalog> diskSets;

    public ConfiguredAdminBackendStatusProvider(
            Environment environment,
            ObjectProvider<StoragePolicyCatalog> policies,
            ObjectProvider<StorageDeviceCatalog> devices,
            ObjectProvider<DiskSetCatalog> diskSets) {
        this.environment = environment;
        this.policies = policies;
        this.devices = devices;
        this.diskSets = diskSets;
    }

    @Override
    public Mono<Map<String, Object>> backendStatus() {
        String profile = Arrays.stream(environment.getActiveProfiles())
            .filter("storage-engine"::equals)
            .findFirst()
            .orElseGet(() -> environment.getActiveProfiles().length == 0
                ? "not-configured" : environment.getActiveProfiles()[0]);
        String configuredBackend = environment.getProperty(BACKEND_PROPERTY);
        String selectedBackend = configuredBackend == null || configuredBackend.isBlank()
            ? ("storage-engine".equals(profile) ? "storage-engine" : "in-memory")
            : configuredBackend;

        return Mono.zip(
                catalogStatus(policies.getIfAvailable(), "storage.engine.policies.dir"),
                catalogStatus(devices.getIfAvailable(), "storage.engine.devices.dir"),
                catalogStatus(diskSets.getIfAvailable(), "storage.engine.disksets.dir"),
                storageRootStatus())
            .map(status -> {
                Map<String, Object> selection = new LinkedHashMap<>();
                selection.put("profile", profile);
                selection.put("property", Map.of(
                    "name", BACKEND_PROPERTY,
                    "value", configuredBackend == null || configuredBackend.isBlank()
                        ? "not-configured" : configuredBackend));

                Map<String, Object> response = new LinkedHashMap<>();
                response.put("selectedBackend", selectedBackend);
                response.put("selection", selection);
                response.put("catalogs", Map.of(
                    "policies", status.getT1(),
                    "devices", status.getT2(),
                    "diskSets", status.getT3()));
                response.put("storageRoots", status.getT4());
                response.put("recoverySummary", Map.of("availability", "not-configured"));
                return response;
            });
    }

    private Mono<Map<String, Object>> catalogStatus(Object catalog, String sourceProperty) {
        Map<String, Object> base = new LinkedHashMap<>();
        String source = environment.getProperty(sourceProperty);
        if (source != null && !source.isBlank()) {
            base.put("sourceDirectory", source);
        } else {
            base.put("sourceDirectory", "not-configured");
        }
        if (catalog == null) {
            base.put("availability", "not-configured");
            return Mono.just(base);
        }
        Mono<Long> count = switch (catalog) {
            case StoragePolicyCatalog value -> value.findAll().count();
            case StorageDeviceCatalog value -> value.findAll().count();
            case DiskSetCatalog value -> value.findAll().count();
            default -> Mono.error(new IllegalArgumentException("Unsupported Admin catalog provider"));
        };
        return count.map(itemCount -> {
            base.put("availability", "available");
            base.put("itemCount", itemCount);
            return base;
        }).onErrorResume(error -> {
            base.put("availability", "unavailable");
            base.put("error", "Catalog provider is unavailable");
            return Mono.just(base);
        });
    }

    private Mono<Map<String, Object>> storageRootStatus() {
        String configuredRoot = environment.getProperty("storage.engine.filesystem.root");
        if (configuredRoot == null || configuredRoot.isBlank()) {
            return Mono.just(Map.of());
        }
        return Mono.fromCallable(() -> Files.isDirectory(Path.of(configuredRoot)))
            .subscribeOn(Schedulers.boundedElastic())
            .map(available -> Map.of(configuredRoot, Map.of(
                "path", configuredRoot,
                "availability", available ? "available" : "unavailable")));
    }
}
