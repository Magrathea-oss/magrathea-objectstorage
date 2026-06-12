package com.example.magrathea.storageengine.infrastructure.yaml;

import com.example.magrathea.storageengine.application.port.StoragePolicyCatalog;
import com.example.magrathea.storageengine.domain.valueobject.ChunkAlignment;
import com.example.magrathea.storageengine.domain.valueobject.CompressionAlgorithm;
import com.example.magrathea.storageengine.domain.valueobject.CompressionConfig;
import com.example.magrathea.storageengine.domain.valueobject.DedupConfig;
import com.example.magrathea.storageengine.domain.valueobject.DedupScope;
import com.example.magrathea.storageengine.domain.valueobject.EncryptionAlgorithm;
import com.example.magrathea.storageengine.domain.valueobject.EncryptionPolicy;
import com.example.magrathea.storageengine.domain.valueobject.ErasureCodingConfig;
import com.example.magrathea.storageengine.domain.valueobject.FingerprintAlgorithm;
import com.example.magrathea.storageengine.domain.valueobject.ReplicationConfig;
import com.example.magrathea.storageengine.domain.valueobject.StorageClassId;
import com.example.magrathea.storageengine.domain.valueobject.StoragePolicy;
import com.example.magrathea.storageengine.infrastructure.yaml.dto.StoragePolicyYaml;
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
import java.util.Optional;
import java.util.concurrent.atomic.AtomicReference;

/**
 * YAML-backed implementation of {@link StoragePolicyCatalog}.
 *
 * <p>Policies are loaded at construction time from {@code *.yaml} files in a
 * configurable directory. If no external directory is configured, files are
 * loaded from the classpath under {@code storage-policies/}.
 *
 * <p>Duplicate policy IDs trigger an {@link IllegalStateException} at startup.
 * Malformed YAML files are logged and skipped unless they corrupt the ID space.
 *
 * <p>The loaded map is cached in an {@link AtomicReference} and treated as an
 * immutable startup snapshot. Manual reload (if needed in the future) should
 * replace the reference atomically.
 */
public class YamlStoragePolicyCatalog implements StoragePolicyCatalog {

    private static final Logger log = LoggerFactory.getLogger(YamlStoragePolicyCatalog.class);

    /** Key: policyId (YAML field). Value: parsed domain policy. */
    private final AtomicReference<Map<String, StoragePolicy>> policyById;

    /** Key: StorageClassId value. Value: parsed domain policy. */
    private final AtomicReference<Map<String, StoragePolicy>> policyByStorageClassId;

    private static final YAMLMapper YAML_MAPPER = YAMLMapper.builder().build();

    // -------------------------------------------------------------------------
    // Constructors / factories
    // -------------------------------------------------------------------------

    /**
     * Loads policies from the given filesystem directory.
     *
     * @param policyDirectory path to a directory containing {@code *.yaml} policy files
     * @throws IllegalStateException    if duplicate policy IDs are found
     * @throws IllegalArgumentException if the directory does not exist or is not a directory
     */
    public YamlStoragePolicyCatalog(Path policyDirectory) {
        if (!Files.isDirectory(policyDirectory)) {
            throw new IllegalArgumentException(
                    "Policy directory does not exist or is not a directory: " + policyDirectory);
        }
        Map<String, StoragePolicy>[] maps = loadFromDirectory(policyDirectory);
        this.policyById = new AtomicReference<>(maps[0]);
        this.policyByStorageClassId = new AtomicReference<>(maps[1]);
        log.info("YamlStoragePolicyCatalog: loaded {} policies from {}", maps[0].size(), policyDirectory);
    }

    /**
     * Loads policies from the classpath under the given prefix directory.
     *
     * @param classpathPrefix classpath directory prefix, e.g. {@code "storage-policies"}
     * @return a fully initialised catalog
     * @throws IllegalStateException if duplicate policy IDs are found
     */
    public static YamlStoragePolicyCatalog fromClasspath(String classpathPrefix) {
        return new YamlStoragePolicyCatalog(classpathPrefix, true);
    }

    /**
     * Creates a catalog from a filesystem directory path string, or falls back
     * to the classpath {@code "storage-policies/"} directory if the path is blank.
     *
     * @param dirPath external directory path (may be null or blank)
     * @return a fully initialised catalog
     */
    public static YamlStoragePolicyCatalog create(String dirPath) {
        if (dirPath != null && !dirPath.isBlank()) {
            return new YamlStoragePolicyCatalog(Path.of(dirPath));
        }
        return fromClasspath("storage-policies");
    }

    // Private classpath constructor
    private YamlStoragePolicyCatalog(String classpathPrefix, boolean ignored) {
        Map<String, StoragePolicy>[] maps = loadFromClasspath(classpathPrefix);
        this.policyById = new AtomicReference<>(maps[0]);
        this.policyByStorageClassId = new AtomicReference<>(maps[1]);
        log.info("YamlStoragePolicyCatalog: loaded {} policies from classpath:{}/",
                maps[0].size(), classpathPrefix);
    }

    // -------------------------------------------------------------------------
    // StoragePolicyCatalog implementation
    // -------------------------------------------------------------------------

    @Override
    public Mono<StoragePolicy> findById(String policyId) {
        StoragePolicy policy = policyById.get().get(policyId);
        return policy != null ? Mono.just(policy) : Mono.empty();
    }

    @Override
    public Mono<StoragePolicy> findBy(StorageClassId id) {
        StoragePolicy policy = policyByStorageClassId.get().get(id.value());
        return policy != null ? Mono.just(policy) : Mono.empty();
    }

    @Override
    public Flux<StoragePolicy> findAll() {
        return Flux.fromIterable(policyById.get().values());
    }

    // -------------------------------------------------------------------------
    // Loading helpers
    // -------------------------------------------------------------------------

    @SuppressWarnings("unchecked")
    private static Map<String, StoragePolicy>[] loadFromDirectory(Path dir) {
        Map<String, StoragePolicy> byId = new LinkedHashMap<>();
        Map<String, StoragePolicy> byClassId = new LinkedHashMap<>();

        List<Path> yamlFiles;
        try {
            yamlFiles = Files.list(dir)
                    .filter(p -> p.getFileName().toString().endsWith(".yaml")
                            || p.getFileName().toString().endsWith(".yml"))
                    .sorted()
                    .toList();
        } catch (IOException e) {
            throw new IllegalStateException("Cannot list policy directory: " + dir, e);
        }

        for (Path file : yamlFiles) {
            try (InputStream in = Files.newInputStream(file)) {
                parseAndRegister(in, file.toString(), byId, byClassId);
            } catch (IOException e) {
                log.error("Failed to read policy YAML file {}: {} — skipping", file, e.getMessage(), e);
            }
        }
        return new Map[]{byId, byClassId};
    }

    @SuppressWarnings("unchecked")
    private static Map<String, StoragePolicy>[] loadFromClasspath(String prefix) {
        Map<String, StoragePolicy> byId = new LinkedHashMap<>();
        Map<String, StoragePolicy> byClassId = new LinkedHashMap<>();

        // Collect all YAML resources under the classpath prefix
        List<URL> resources = findClasspathResources(prefix);
        for (URL url : resources) {
            try (InputStream in = url.openStream()) {
                parseAndRegister(in, url.toString(), byId, byClassId);
            } catch (IOException e) {
                log.error("Failed to read classpath policy resource {}: {} — skipping",
                        url, e.getMessage(), e);
            }
        }
        return new Map[]{byId, byClassId};
    }

    private static List<URL> findClasspathResources(String prefix) {
        List<URL> found = new ArrayList<>();
        // Use the context class loader to find the prefix directory
        ClassLoader cl = Thread.currentThread().getContextClassLoader();
        if (cl == null) {
            cl = YamlStoragePolicyCatalog.class.getClassLoader();
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
            Map<String, StoragePolicy> byId,
            Map<String, StoragePolicy> byClassId) {

        StoragePolicyYaml dto;
        try {
            dto = YAML_MAPPER.readValue(in, StoragePolicyYaml.class);
        } catch (Exception e) {
            throw new IllegalStateException("Malformed policy YAML in " + source + ": " + e.getMessage(), e);
        }

        if (dto.policyId == null || dto.policyId.isBlank()) {
            throw new IllegalStateException("Policy YAML missing required 'policyId' in " + source);
        }
        if (dto.storageClassId == null || dto.storageClassId.isBlank()) {
            throw new IllegalStateException("Policy YAML missing required 'storageClassId' in " + source);
        }

        if (byId.containsKey(dto.policyId)) {
            throw new IllegalStateException(
                    "Duplicate policyId '" + dto.policyId + "' found in " + source);
        }

        StoragePolicy policy = toDomain(dto, source);
        byId.put(dto.policyId, policy);
        byClassId.put(dto.storageClassId, policy);
    }

    // -------------------------------------------------------------------------
    // DTO → Domain mapping
    // -------------------------------------------------------------------------

    private static StoragePolicy toDomain(StoragePolicyYaml dto, String source) {
        StorageClassId classId = StorageClassId.of(dto.storageClassId);

        Optional<DedupConfig> dedup = Optional.empty();
        if (dto.dedup != null && dto.dedup.enabled) {
            dedup = Optional.of(mapDedup(dto.dedup, source));
        }

        Optional<CompressionConfig> compression = Optional.empty();
        if (dto.compression != null && dto.compression.enabled) {
            compression = Optional.of(mapCompression(dto.compression, source));
        }

        Optional<EncryptionPolicy> encryption = Optional.empty();
        if (dto.encryption != null && dto.encryption.enabled
                && dto.encryption.mode != null && !"NONE".equalsIgnoreCase(dto.encryption.mode)) {
            encryption = Optional.of(mapEncryption(dto.encryption, source));
        }

        Optional<ErasureCodingConfig> erasureCoding = Optional.empty();
        if (dto.erasureCoding != null && dto.erasureCoding.enabled) {
            erasureCoding = Optional.of(
                    ErasureCodingConfig.of(dto.erasureCoding.dataBlocks, dto.erasureCoding.parityBlocks));
        }

        ReplicationConfig replication = (dto.replication != null)
                ? ReplicationConfig.of(dto.replication.factor)
                : ReplicationConfig.of(1);

        return StoragePolicy.of(classId, dedup, compression, encryption, erasureCoding, replication);
    }

    private static DedupConfig mapDedup(StoragePolicyYaml.DedupYaml yaml, String source) {
        DedupScope scope = switch (yaml.scope == null ? "BUCKET" : yaml.scope.toUpperCase()) {
            case "BUCKET", "BUCKET_LEVEL" -> DedupScope.BUCKET_LEVEL;
            case "GLOBAL", "GLOBAL_LEVEL" -> DedupScope.GLOBAL_LEVEL;
            default -> throw new IllegalStateException(
                    "Unknown dedup scope '" + yaml.scope + "' in " + source);
        };

        FingerprintAlgorithm algorithm = parseFingerprintAlgorithm(yaml.algorithm, source);

        long chunkSize = yaml.chunkSizeBytes > 0 ? yaml.chunkSizeBytes : DedupConfig.DEFAULT_CHUNK_SIZE;

        ChunkAlignment alignment = switch (yaml.alignment == null ? "NONE" : yaml.alignment.toUpperCase()) {
            case "NONE" -> ChunkAlignment.NONE;
            case "BLOCK_BOUNDARY" -> ChunkAlignment.BLOCK_BOUNDARY;
            default -> throw new IllegalStateException(
                    "Unknown chunk alignment '" + yaml.alignment + "' in " + source);
        };

        return DedupConfig.of(scope, algorithm, chunkSize, alignment);
    }

    private static FingerprintAlgorithm parseFingerprintAlgorithm(String value, String source) {
        if (value == null) return FingerprintAlgorithm.SHA256;
        return switch (value.toUpperCase()) {
            case "SHA256" -> FingerprintAlgorithm.SHA256;
            case "BLAKE2" -> FingerprintAlgorithm.BLAKE2;
            case "XXHASH" -> FingerprintAlgorithm.XXHASH;
            default -> throw new IllegalStateException(
                    "Unknown fingerprint algorithm '" + value + "' in " + source);
        };
    }

    private static CompressionConfig mapCompression(StoragePolicyYaml.CompressionYaml yaml, String source) {
        CompressionAlgorithm algorithm = switch (yaml.algorithm == null ? "ZSTD" : yaml.algorithm.toUpperCase()) {
            case "GZIP" -> CompressionAlgorithm.GZIP;
            case "ZSTD" -> CompressionAlgorithm.ZSTD;
            case "LZ4"  -> CompressionAlgorithm.LZ4;
            default -> throw new IllegalStateException(
                    "Unknown compression algorithm '" + yaml.algorithm + "' in " + source);
        };
        return CompressionConfig.of(algorithm, yaml.level);
    }

    private static EncryptionPolicy mapEncryption(StoragePolicyYaml.EncryptionYaml yaml, String source) {
        EncryptionAlgorithm algorithm = switch (yaml.mode == null ? "SSE_S3" : yaml.mode.toUpperCase()) {
            case "SSE_S3"  -> EncryptionAlgorithm.SSE_S3;
            case "SSE_KMS" -> EncryptionAlgorithm.SSE_KMS;
            default -> throw new IllegalStateException(
                    "Unknown encryption mode '" + yaml.mode + "' in " + source);
        };
        return EncryptionPolicy.of(algorithm, Optional.empty());
    }
}
