package com.example.magrathea.storageengine.infrastructure.yaml;

import com.example.magrathea.storageengine.application.port.StorageDeviceCatalog;
import com.example.magrathea.storageengine.domain.valueobject.DeviceHealth;
import com.example.magrathea.storageengine.domain.valueobject.StorageDevice;
import com.example.magrathea.storageengine.domain.valueobject.StorageDeviceId;
import com.example.magrathea.storageengine.infrastructure.yaml.dto.StorageDeviceYaml;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import tools.jackson.dataformat.yaml.YAMLMapper;

import java.io.IOException;
import java.io.InputStream;
import java.net.URL;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicReference;

/**
 * YAML-backed implementation of {@link StorageDeviceCatalog}.
 *
 * <p>Devices are loaded at construction time from {@code *.yaml} files in a
 * configurable directory. If no external directory is configured, files are
 * loaded from the classpath under {@code storage-devices/}.
 *
 * <p>Duplicate device IDs trigger an {@link IllegalStateException} at startup.
 * Malformed YAML files are logged and skipped.
 *
 * <p>{@link #findEligibleForWrite()} filters to only {@link DeviceHealth#HEALTHY} devices.
 */
public class YamlStorageDeviceCatalog implements StorageDeviceCatalog {

    private static final Logger log = LoggerFactory.getLogger(YamlStorageDeviceCatalog.class);

    /** Key: StorageDeviceId.value(). Value: parsed domain device. */
    private final AtomicReference<Map<String, StorageDevice>> deviceById;

    private static final YAMLMapper YAML_MAPPER = YAMLMapper.builder().build();

    // -------------------------------------------------------------------------
    // Constructors / factories
    // -------------------------------------------------------------------------

    /**
     * Loads devices from the given filesystem directory.
     *
     * @param deviceDirectory path to a directory containing {@code *.yaml} device files
     * @throws IllegalStateException    if duplicate device IDs are found
     * @throws IllegalArgumentException if the directory does not exist or is not a directory
     */
    public YamlStorageDeviceCatalog(Path deviceDirectory) {
        if (!Files.isDirectory(deviceDirectory)) {
            throw new IllegalArgumentException(
                    "Device directory does not exist or is not a directory: " + deviceDirectory);
        }
        Map<String, StorageDevice> map = loadFromDirectory(deviceDirectory);
        this.deviceById = new AtomicReference<>(map);
        log.info("YamlStorageDeviceCatalog: loaded {} devices from {}", map.size(), deviceDirectory);
    }

    /**
     * Loads devices from the classpath under the given prefix directory.
     *
     * @param classpathPrefix classpath directory prefix, e.g. {@code "storage-devices"}
     * @return a fully initialised catalog
     */
    public static YamlStorageDeviceCatalog fromClasspath(String classpathPrefix) {
        return new YamlStorageDeviceCatalog(classpathPrefix, true);
    }

    /**
     * Creates a catalog from a filesystem directory path string, or falls back
     * to the classpath {@code "storage-devices/"} directory if the path is blank.
     *
     * @param dirPath external directory path (may be null or blank)
     * @return a fully initialised catalog
     */
    public static YamlStorageDeviceCatalog create(String dirPath) {
        if (dirPath != null && !dirPath.isBlank()) {
            return new YamlStorageDeviceCatalog(Path.of(dirPath));
        }
        return fromClasspath("storage-devices");
    }

    // Private classpath constructor
    private YamlStorageDeviceCatalog(String classpathPrefix, boolean ignored) {
        Map<String, StorageDevice> map = loadFromClasspath(classpathPrefix);
        this.deviceById = new AtomicReference<>(map);
        log.info("YamlStorageDeviceCatalog: loaded {} devices from classpath:{}/",
                map.size(), classpathPrefix);
    }

    // -------------------------------------------------------------------------
    // StorageDeviceCatalog implementation
    // -------------------------------------------------------------------------

    @Override
    public Mono<StorageDevice> findById(StorageDeviceId deviceId) {
        StorageDevice device = deviceById.get().get(deviceId.value());
        return device != null ? Mono.just(device) : Mono.empty();
    }

    @Override
    public Flux<StorageDevice> findAll() {
        return Flux.fromIterable(deviceById.get().values());
    }

    @Override
    public Flux<StorageDevice> findEligibleForWrite() {
        return Flux.fromIterable(deviceById.get().values())
                .filter(StorageDevice::isWriteEligible);
    }

    // -------------------------------------------------------------------------
    // Loading helpers
    // -------------------------------------------------------------------------

    private static Map<String, StorageDevice> loadFromDirectory(Path dir) {
        Map<String, StorageDevice> byId = new LinkedHashMap<>();

        List<Path> yamlFiles;
        try {
            yamlFiles = Files.list(dir)
                    .filter(p -> p.getFileName().toString().endsWith(".yaml")
                            || p.getFileName().toString().endsWith(".yml"))
                    .sorted()
                    .toList();
        } catch (IOException e) {
            throw new IllegalStateException("Cannot list device directory: " + dir, e);
        }

        for (Path file : yamlFiles) {
            try (InputStream in = Files.newInputStream(file)) {
                parseAndRegister(in, file.toString(), byId);
            } catch (IOException e) {
                log.error("Failed to read device YAML file {}: {} — skipping", file, e.getMessage(), e);
            }
        }
        return byId;
    }

    private static Map<String, StorageDevice> loadFromClasspath(String prefix) {
        Map<String, StorageDevice> byId = new LinkedHashMap<>();
        List<URL> resources = findClasspathResources(prefix);
        for (URL url : resources) {
            try (InputStream in = url.openStream()) {
                parseAndRegister(in, url.toString(), byId);
            } catch (IOException e) {
                log.error("Failed to read classpath device resource {}: {} — skipping",
                        url, e.getMessage(), e);
            }
        }
        return byId;
    }

    private static List<URL> findClasspathResources(String prefix) {
        List<URL> found = new ArrayList<>();
        ClassLoader cl = Thread.currentThread().getContextClassLoader();
        if (cl == null) {
            cl = YamlStorageDeviceCatalog.class.getClassLoader();
        }
        try {
            var en = cl.getResources(prefix);
            while (en.hasMoreElements()) {
                URL dirUrl = en.nextElement();
                if ("file".equals(dirUrl.getProtocol())) {
                    Path dirPath = Path.of(dirUrl.toURI());
                    if (Files.isDirectory(dirPath)) {
                        Files.list(dirPath)
                                .filter(p -> p.getFileName().toString().endsWith(".yaml")
                                        || p.getFileName().toString().endsWith(".yml"))
                                .sorted()
                                .forEach(p -> {
                                    try {
                                        found.add(p.toUri().toURL());
                                    } catch (Exception ex) {
                                        log.warn("Cannot convert path to URL: {}", p, ex);
                                    }
                                });
                    }
                }
            }
        } catch (Exception e) {
            log.warn("Cannot enumerate classpath resources under '{}': {}", prefix, e.getMessage(), e);
        }
        return found;
    }

    private static void parseAndRegister(
            InputStream in,
            String source,
            Map<String, StorageDevice> byId) {

        StorageDeviceYaml dto;
        try {
            dto = YAML_MAPPER.readValue(in, StorageDeviceYaml.class);
        } catch (Exception e) {
            throw new IllegalStateException("Malformed device YAML in " + source + ": " + e.getMessage(), e);
        }

        if (dto.deviceId == null || dto.deviceId.isBlank()) {
            throw new IllegalStateException("Device YAML missing required 'deviceId' in " + source);
        }
        if (byId.containsKey(dto.deviceId)) {
            throw new IllegalStateException(
                    "Duplicate deviceId '" + dto.deviceId + "' found in " + source);
        }

        StorageDevice device = toDomain(dto, source);
        byId.put(dto.deviceId, device);
    }

    // -------------------------------------------------------------------------
    // DTO → Domain mapping
    // -------------------------------------------------------------------------

    private static StorageDevice toDomain(StorageDeviceYaml dto, String source) {
        StorageDeviceId id = StorageDeviceId.of(dto.deviceId);

        DeviceHealth health = switch (dto.health == null ? "HEALTHY" : dto.health.toUpperCase()) {
            case "HEALTHY"     -> DeviceHealth.HEALTHY;
            case "DEGRADED"    -> DeviceHealth.DEGRADED;
            case "UNAVAILABLE" -> DeviceHealth.UNAVAILABLE;
            default -> throw new IllegalStateException(
                    "Unknown device health '" + dto.health + "' in " + source);
        };

        long available = dto.availableCapacityBytes < 0
                ? dto.totalCapacityBytes
                : dto.availableCapacityBytes;

        return StorageDevice.restore(id, dto.storagePath, dto.totalCapacityBytes, available, health);
    }
}
