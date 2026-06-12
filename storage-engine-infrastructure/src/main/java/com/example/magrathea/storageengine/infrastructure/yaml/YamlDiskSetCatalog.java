package com.example.magrathea.storageengine.infrastructure.yaml;

import com.example.magrathea.storageengine.application.port.DiskSetCatalog;
import com.example.magrathea.storageengine.domain.valueobject.DiskSet;
import com.example.magrathea.storageengine.domain.valueobject.FailureDomain;
import com.example.magrathea.storageengine.domain.valueobject.StorageDeviceId;
import com.example.magrathea.storageengine.infrastructure.yaml.dto.DiskSetYaml;
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
 * YAML-backed implementation of {@link DiskSetCatalog}.
 *
 * <p>Disk sets are loaded at construction time from {@code *.yaml} files in a
 * configurable directory. If no external directory is configured, files are
 * loaded from the classpath under {@code disk-sets/}.
 *
 * <p>Duplicate disk-set IDs trigger an {@link IllegalStateException} at startup.
 * A disk set with zero {@code deviceIds} is invalid and will trigger an error.
 * Malformed YAML files are logged and skipped.
 */
public class YamlDiskSetCatalog implements DiskSetCatalog {

    private static final Logger log = LoggerFactory.getLogger(YamlDiskSetCatalog.class);

    /** Key: diskSetId (YAML field). Value: parsed domain disk set. */
    private final AtomicReference<Map<String, DiskSet>> diskSetById;

    private static final YAMLMapper YAML_MAPPER = YAMLMapper.builder().build();

    // -------------------------------------------------------------------------
    // Constructors / factories
    // -------------------------------------------------------------------------

    /**
     * Loads disk sets from the given filesystem directory.
     *
     * @param diskSetDirectory path to a directory containing {@code *.yaml} disk-set files
     * @throws IllegalStateException    if duplicate disk-set IDs are found or a set has no devices
     * @throws IllegalArgumentException if the directory does not exist or is not a directory
     */
    public YamlDiskSetCatalog(Path diskSetDirectory) {
        if (!Files.isDirectory(diskSetDirectory)) {
            throw new IllegalArgumentException(
                    "Disk-set directory does not exist or is not a directory: " + diskSetDirectory);
        }
        Map<String, DiskSet> map = loadFromDirectory(diskSetDirectory);
        this.diskSetById = new AtomicReference<>(map);
        log.info("YamlDiskSetCatalog: loaded {} disk sets from {}", map.size(), diskSetDirectory);
    }

    /**
     * Loads disk sets from the classpath under the given prefix directory.
     *
     * @param classpathPrefix classpath directory prefix, e.g. {@code "disk-sets"}
     * @return a fully initialised catalog
     */
    public static YamlDiskSetCatalog fromClasspath(String classpathPrefix) {
        return new YamlDiskSetCatalog(classpathPrefix, true);
    }

    /**
     * Creates a catalog from a filesystem directory path string, or falls back
     * to the classpath {@code "disk-sets/"} directory if the path is blank.
     *
     * @param dirPath external directory path (may be null or blank)
     * @return a fully initialised catalog
     */
    public static YamlDiskSetCatalog create(String dirPath) {
        if (dirPath != null && !dirPath.isBlank()) {
            return new YamlDiskSetCatalog(Path.of(dirPath));
        }
        return fromClasspath("disk-sets");
    }

    // Private classpath constructor
    private YamlDiskSetCatalog(String classpathPrefix, boolean ignored) {
        Map<String, DiskSet> map = loadFromClasspath(classpathPrefix);
        this.diskSetById = new AtomicReference<>(map);
        log.info("YamlDiskSetCatalog: loaded {} disk sets from classpath:{}/",
                map.size(), classpathPrefix);
    }

    // -------------------------------------------------------------------------
    // DiskSetCatalog implementation
    // -------------------------------------------------------------------------

    @Override
    public Mono<DiskSet> findById(String diskSetId) {
        DiskSet set = diskSetById.get().get(diskSetId);
        return set != null ? Mono.just(set) : Mono.empty();
    }

    @Override
    public Flux<DiskSet> findAll() {
        return Flux.fromIterable(diskSetById.get().values());
    }

    // -------------------------------------------------------------------------
    // Loading helpers
    // -------------------------------------------------------------------------

    private static Map<String, DiskSet> loadFromDirectory(Path dir) {
        Map<String, DiskSet> byId = new LinkedHashMap<>();

        List<Path> yamlFiles;
        try {
            yamlFiles = Files.list(dir)
                    .filter(p -> p.getFileName().toString().endsWith(".yaml")
                            || p.getFileName().toString().endsWith(".yml"))
                    .sorted()
                    .toList();
        } catch (IOException e) {
            throw new IllegalStateException("Cannot list disk-set directory: " + dir, e);
        }

        for (Path file : yamlFiles) {
            try (InputStream in = Files.newInputStream(file)) {
                parseAndRegister(in, file.toString(), byId);
            } catch (IOException e) {
                log.error("Failed to read disk-set YAML file {}: {} — skipping", file, e.getMessage(), e);
            }
        }
        return byId;
    }

    private static Map<String, DiskSet> loadFromClasspath(String prefix) {
        Map<String, DiskSet> byId = new LinkedHashMap<>();
        List<URL> resources = findClasspathResources(prefix);
        for (URL url : resources) {
            try (InputStream in = url.openStream()) {
                parseAndRegister(in, url.toString(), byId);
            } catch (IOException e) {
                log.error("Failed to read classpath disk-set resource {}: {} — skipping",
                        url, e.getMessage(), e);
            }
        }
        return byId;
    }

    private static List<URL> findClasspathResources(String prefix) {
        List<URL> found = new ArrayList<>();
        ClassLoader cl = Thread.currentThread().getContextClassLoader();
        if (cl == null) {
            cl = YamlDiskSetCatalog.class.getClassLoader();
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
            Map<String, DiskSet> byId) {

        DiskSetYaml dto;
        try {
            dto = YAML_MAPPER.readValue(in, DiskSetYaml.class);
        } catch (Exception e) {
            throw new IllegalStateException("Malformed disk-set YAML in " + source + ": " + e.getMessage(), e);
        }

        if (dto.diskSetId == null || dto.diskSetId.isBlank()) {
            throw new IllegalStateException("Disk-set YAML missing required 'diskSetId' in " + source);
        }
        if (byId.containsKey(dto.diskSetId)) {
            throw new IllegalStateException(
                    "Duplicate diskSetId '" + dto.diskSetId + "' found in " + source);
        }
        if (dto.deviceIds == null || dto.deviceIds.isEmpty()) {
            throw new IllegalStateException(
                    "Disk-set '" + dto.diskSetId + "' has no deviceIds in " + source);
        }

        DiskSet diskSet = toDomain(dto, source);
        byId.put(dto.diskSetId, diskSet);
    }

    // -------------------------------------------------------------------------
    // DTO → Domain mapping
    // -------------------------------------------------------------------------

    private static DiskSet toDomain(DiskSetYaml dto, String source) {
        FailureDomain failureDomain = switch (
                dto.failureDomain == null ? "DISK" : dto.failureDomain.toUpperCase()) {
            case "RACK" -> FailureDomain.RACK;
            case "HOST" -> FailureDomain.HOST;
            case "DISK" -> FailureDomain.DISK;
            default -> throw new IllegalStateException(
                    "Unknown failure domain '" + dto.failureDomain + "' in " + source);
        };

        List<StorageDeviceId> deviceIds = dto.deviceIds.stream()
                .map(id -> {
                    if (id == null || id.isBlank()) {
                        throw new IllegalStateException(
                                "Blank deviceId in disk-set '" + dto.diskSetId + "' in " + source);
                    }
                    return StorageDeviceId.of(id);
                })
                .toList();

        return DiskSet.of(dto.diskSetId, failureDomain, deviceIds);
    }
}
