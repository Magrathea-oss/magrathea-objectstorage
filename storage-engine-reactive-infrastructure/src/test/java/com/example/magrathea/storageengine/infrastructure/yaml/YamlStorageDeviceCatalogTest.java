package com.example.magrathea.storageengine.infrastructure.yaml;

import com.example.magrathea.storageengine.domain.valueobject.DeviceHealth;
import com.example.magrathea.storageengine.domain.valueobject.StorageDeviceId;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import reactor.test.StepVerifier;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Integration tests for {@link YamlStorageDeviceCatalog}.
 * No Spring context — uses {@link TempDir} for filesystem-level YAML directories.
 */
class YamlStorageDeviceCatalogTest {

    // -------------------------------------------------------------------------
    // Single device load
    // -------------------------------------------------------------------------

    @Test
    void loadsDeviceFromYaml(@TempDir Path dir) throws IOException {
        Files.writeString(dir.resolve("dev0.yaml"), """
                deviceId: node-1-disk-0
                storagePath: /data/node-1/disk-0
                totalCapacityBytes: 107374182400
                availableCapacityBytes: 107374182400
                health: HEALTHY
                failureDomain: DISK
                """);

        YamlStorageDeviceCatalog catalog = new YamlStorageDeviceCatalog(dir);

        StepVerifier.create(catalog.findById(StorageDeviceId.of("node-1-disk-0")))
                .assertNext(device -> {
                    assertThat(device.id().value()).isEqualTo("node-1-disk-0");
                    assertThat(device.storagePath()).isEqualTo("/data/node-1/disk-0");
                    assertThat(device.totalCapacityBytes()).isEqualTo(107_374_182_400L);
                    assertThat(device.availableCapacityBytes()).isEqualTo(107_374_182_400L);
                    assertThat(device.health()).isEqualTo(DeviceHealth.HEALTHY);
                })
                .verifyComplete();
    }

    // -------------------------------------------------------------------------
    // Health filtering
    // -------------------------------------------------------------------------

    @Test
    void findEligibleForWriteFiltersUnhealthyDevices(@TempDir Path dir) throws IOException {
        Files.writeString(dir.resolve("healthy.yaml"), """
                deviceId: disk-healthy
                storagePath: /data/healthy
                totalCapacityBytes: 1073741824
                health: HEALTHY
                """);
        Files.writeString(dir.resolve("degraded.yaml"), """
                deviceId: disk-degraded
                storagePath: /data/degraded
                totalCapacityBytes: 1073741824
                health: DEGRADED
                """);
        Files.writeString(dir.resolve("unavailable.yaml"), """
                deviceId: disk-unavailable
                storagePath: /data/unavailable
                totalCapacityBytes: 1073741824
                health: UNAVAILABLE
                """);

        YamlStorageDeviceCatalog catalog = new YamlStorageDeviceCatalog(dir);

        // findAll returns all three
        StepVerifier.create(catalog.findAll().count())
                .expectNext(3L)
                .verifyComplete();

        // findEligibleForWrite returns only HEALTHY
        StepVerifier.create(catalog.findEligibleForWrite().collectList())
                .assertNext(list -> {
                    assertThat(list).hasSize(1);
                    assertThat(list.get(0).id().value()).isEqualTo("disk-healthy");
                    assertThat(list.get(0).health()).isEqualTo(DeviceHealth.HEALTHY);
                })
                .verifyComplete();
    }

    // -------------------------------------------------------------------------
    // Unknown device
    // -------------------------------------------------------------------------

    @Test
    void returnsEmptyForUnknownDevice(@TempDir Path dir) throws IOException {
        Files.writeString(dir.resolve("dev.yaml"), """
                deviceId: known-device
                storagePath: /data/known
                totalCapacityBytes: 1073741824
                health: HEALTHY
                """);

        YamlStorageDeviceCatalog catalog = new YamlStorageDeviceCatalog(dir);

        StepVerifier.create(catalog.findById(StorageDeviceId.of("unknown-device")))
                .verifyComplete();
    }

    // -------------------------------------------------------------------------
    // Duplicate device ID
    // -------------------------------------------------------------------------

    @Test
    void rejectsDuplicateDeviceIds(@TempDir Path dir) throws IOException {
        Files.writeString(dir.resolve("a.yaml"), """
                deviceId: duplicate-device
                storagePath: /data/a
                totalCapacityBytes: 1073741824
                health: HEALTHY
                """);
        Files.writeString(dir.resolve("b.yaml"), """
                deviceId: duplicate-device
                storagePath: /data/b
                totalCapacityBytes: 1073741824
                health: HEALTHY
                """);

        assertThatThrownBy(() -> new YamlStorageDeviceCatalog(dir))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("duplicate-device");
    }

    // -------------------------------------------------------------------------
    // Malformed and domain-invalid YAML
    // -------------------------------------------------------------------------

    @Test
    void rejectsMalformedYaml(@TempDir Path dir) throws IOException {
        Files.writeString(dir.resolve("bad.yaml"), """
                deviceId: [unclosed
                storagePath: :::broken:::
                  - invalid
                """);

        assertThatThrownBy(() -> new YamlStorageDeviceCatalog(dir))
                .isInstanceOf(IllegalStateException.class);
    }

    @Test
    void rejectsMissingStoragePath(@TempDir Path dir) throws IOException {
        Files.writeString(dir.resolve("missing-path.yaml"), """
                deviceId: missing-path
                totalCapacityBytes: 1073741824
                health: HEALTHY
                """);

        assertThatThrownBy(() -> new YamlStorageDeviceCatalog(dir))
                .isInstanceOf(NullPointerException.class)
                .hasMessageContaining("storagePath must not be null");
    }

    @Test
    void rejectsAvailableCapacityGreaterThanTotal(@TempDir Path dir) throws IOException {
        Files.writeString(dir.resolve("over-capacity.yaml"), """
                deviceId: over-capacity
                storagePath: /data/over-capacity
                totalCapacityBytes: 10
                availableCapacityBytes: 11
                health: HEALTHY
                """);

        assertThatThrownBy(() -> new YamlStorageDeviceCatalog(dir))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("must not exceed totalCapacityBytes");
    }

    @Test
    void rejectsUnknownHealth(@TempDir Path dir) throws IOException {
        Files.writeString(dir.resolve("unknown-health.yaml"), """
                deviceId: unknown-health
                storagePath: /data/unknown-health
                totalCapacityBytes: 1073741824
                health: RETIRED
                """);

        assertThatThrownBy(() -> new YamlStorageDeviceCatalog(dir))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("Unknown device health 'RETIRED'");
    }

    // -------------------------------------------------------------------------
    // Available capacity defaults to totalCapacityBytes when not specified
    // -------------------------------------------------------------------------

    @Test
    void availableCapacityDefaultsToTotalWhenOmitted(@TempDir Path dir) throws IOException {
        Files.writeString(dir.resolve("noavail.yaml"), """
                deviceId: dev-no-avail
                storagePath: /data/noavail
                totalCapacityBytes: 536870912
                health: HEALTHY
                """);

        YamlStorageDeviceCatalog catalog = new YamlStorageDeviceCatalog(dir);

        StepVerifier.create(catalog.findById(StorageDeviceId.of("dev-no-avail")))
                .assertNext(device -> assertThat(device.availableCapacityBytes())
                        .isEqualTo(device.totalCapacityBytes()))
                .verifyComplete();
    }

    // -------------------------------------------------------------------------
    // Empty directory
    // -------------------------------------------------------------------------

    @Test
    void emptyDirectoryProducesEmptyCatalog(@TempDir Path dir) {
        YamlStorageDeviceCatalog catalog = new YamlStorageDeviceCatalog(dir);

        StepVerifier.create(catalog.findAll())
                .verifyComplete();
    }
}
