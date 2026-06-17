package com.example.magrathea.storageengine.infrastructure.yaml.config;

import com.example.magrathea.storageengine.application.port.StoragePolicyCatalog;
import com.example.magrathea.storageengine.infrastructure.yaml.YamlDiskSetCatalog;
import com.example.magrathea.storageengine.infrastructure.yaml.YamlStorageDeviceCatalog;
import com.example.magrathea.storageengine.infrastructure.yaml.YamlStoragePolicyCatalog;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;

/**
 * Spring configuration that registers YAML-backed catalog beans when the
 * {@code storage-engine} Spring profile is active.
 *
 * <p>Each catalog reads its YAML files from a configurable external directory
 * (via Spring properties) or falls back to the classpath bundled resources.
 *
 * <h2>Property keys</h2>
 * <ul>
 *   <li>{@code storage.engine.policies.dir} — filesystem directory for policy YAML files</li>
 *   <li>{@code storage.engine.devices.dir}  — filesystem directory for device YAML files</li>
 *   <li>{@code storage.engine.disksets.dir} — filesystem directory for disk-set YAML files</li>
 * </ul>
 *
 * <p>When a property is absent or blank, the catalog falls back to the bundled classpath
 * resources ({@code storage-policies/}, {@code storage-devices/}, {@code disk-sets/}).
 */
@Configuration
@Profile("storage-engine")
public class StorageEngineYamlCatalogConfig {

    /**
     * YAML-backed {@link StoragePolicyCatalog}.
     *
     * <p>Loads from {@code storage.engine.policies.dir} if set, otherwise from
     * classpath {@code storage-policies/}.
     */
    @Bean
    public StoragePolicyCatalog storagePolicyCatalog(
            @Value("${storage.engine.policies.dir:}") String policiesDir) {
        return YamlStoragePolicyCatalog.create(policiesDir);
    }

    /**
     * YAML-backed {@link StorageDeviceCatalog}.
     *
     * <p>Loads from {@code storage.engine.devices.dir} if set, otherwise from
     * classpath {@code storage-devices/}.
     */
    @Bean
    public YamlStorageDeviceCatalog storageDeviceCatalog(
            @Value("${storage.engine.devices.dir:}") String devicesDir) {
        return YamlStorageDeviceCatalog.create(devicesDir);
    }

    /**
     * YAML-backed {@link DiskSetCatalog}.
     *
     * <p>Loads from {@code storage.engine.disksets.dir} if set, otherwise from
     * classpath {@code disk-sets/}.
     */
    @Bean
    public YamlDiskSetCatalog diskSetCatalog(
            @Value("${storage.engine.disksets.dir:}") String disksetsDir,
            YamlStorageDeviceCatalog storageDeviceCatalog) {
        YamlDiskSetCatalog catalog = YamlDiskSetCatalog.create(disksetsDir);
        catalog.validateDeviceReferences(storageDeviceCatalog.loadedDeviceIds());
        return catalog;
    }
}
