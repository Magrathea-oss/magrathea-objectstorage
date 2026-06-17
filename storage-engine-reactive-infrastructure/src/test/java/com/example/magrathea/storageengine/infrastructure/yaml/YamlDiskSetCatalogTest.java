package com.example.magrathea.storageengine.infrastructure.yaml;

import com.example.magrathea.storageengine.domain.valueobject.DiskSet;
import com.example.magrathea.storageengine.domain.valueobject.FailureDomain;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import reactor.test.StepVerifier;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Integration tests for {@link YamlDiskSetCatalog}.
 * No Spring context — uses {@link TempDir} for filesystem-level YAML directories.
 */
class YamlDiskSetCatalogTest {

    // -------------------------------------------------------------------------
    // Single disk set load
    // -------------------------------------------------------------------------

    @Test
    void loadsDiskSetFromYaml(@TempDir Path dir) throws IOException {
        Files.writeString(dir.resolve("default.yaml"), """
                diskSetId: default-diskset
                failureDomain: HOST
                deviceIds:
                  - node-1-disk-0
                  - node-1-disk-1
                """);

        YamlDiskSetCatalog catalog = new YamlDiskSetCatalog(dir);

        StepVerifier.create(catalog.findById("default-diskset"))
                .assertNext(set -> {
                    assertThat(set.name()).isEqualTo("default-diskset");
                    assertThat(set.failureDomain()).isEqualTo(FailureDomain.HOST);
                    assertThat(set.size()).isEqualTo(2);
                    assertThat(set.devices())
                            .extracting(id -> id.value())
                            .containsExactly("node-1-disk-0", "node-1-disk-1");
                })
                .verifyComplete();
    }

    // -------------------------------------------------------------------------
    // Disk set with zero devices must be rejected
    // -------------------------------------------------------------------------

    @Test
    void rejectsDiskSetWithZeroDevices(@TempDir Path dir) throws IOException {
        Files.writeString(dir.resolve("empty.yaml"), """
                diskSetId: empty-diskset
                failureDomain: DISK
                deviceIds: []
                """);

        assertThatThrownBy(() -> new YamlDiskSetCatalog(dir))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("empty-diskset");
    }

    // -------------------------------------------------------------------------
    // Duplicate disk-set ID
    // -------------------------------------------------------------------------

    @Test
    void rejectsDuplicateDiskSetIds(@TempDir Path dir) throws IOException {
        Files.writeString(dir.resolve("a.yaml"), """
                diskSetId: shared-id
                failureDomain: HOST
                deviceIds:
                  - disk-a
                """);
        Files.writeString(dir.resolve("b.yaml"), """
                diskSetId: shared-id
                failureDomain: RACK
                deviceIds:
                  - disk-b
                """);

        assertThatThrownBy(() -> new YamlDiskSetCatalog(dir))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("shared-id");
    }

    // -------------------------------------------------------------------------
    // Unknown ID
    // -------------------------------------------------------------------------

    @Test
    void returnsEmptyForUnknownDiskSetId(@TempDir Path dir) throws IOException {
        Files.writeString(dir.resolve("real.yaml"), """
                diskSetId: real-set
                failureDomain: DISK
                deviceIds:
                  - disk-0
                """);

        YamlDiskSetCatalog catalog = new YamlDiskSetCatalog(dir);

        StepVerifier.create(catalog.findById("no-such-set"))
                .verifyComplete();
    }

    // -------------------------------------------------------------------------
    // findAll
    // -------------------------------------------------------------------------

    @Test
    void findsAllReturnsAllLoadedSets(@TempDir Path dir) throws IOException {
        Files.writeString(dir.resolve("rack-a.yaml"), """
                diskSetId: rack-a
                failureDomain: RACK
                deviceIds:
                  - disk-0
                  - disk-1
                """);
        Files.writeString(dir.resolve("rack-b.yaml"), """
                diskSetId: rack-b
                failureDomain: RACK
                deviceIds:
                  - disk-2
                """);

        YamlDiskSetCatalog catalog = new YamlDiskSetCatalog(dir);

        StepVerifier.create(catalog.findAll().collectList())
                .assertNext(list -> {
                    assertThat(list).hasSize(2);
                    assertThat(list)
                            .extracting(DiskSet::name)
                            .containsExactlyInAnyOrder("rack-a", "rack-b");
                })
                .verifyComplete();
    }

    // -------------------------------------------------------------------------
    // Failure domain mapping
    // -------------------------------------------------------------------------

    @Test
    void mapsFailureDomainsCorrectly(@TempDir Path dir) throws IOException {
        Files.writeString(dir.resolve("rack.yaml"), """
                diskSetId: rack-set
                failureDomain: RACK
                deviceIds:
                  - disk-0
                """);
        Files.writeString(dir.resolve("host.yaml"), """
                diskSetId: host-set
                failureDomain: HOST
                deviceIds:
                  - disk-1
                """);
        Files.writeString(dir.resolve("disk.yaml"), """
                diskSetId: disk-set
                failureDomain: DISK
                deviceIds:
                  - disk-2
                """);

        YamlDiskSetCatalog catalog = new YamlDiskSetCatalog(dir);

        StepVerifier.create(catalog.findById("rack-set"))
                .assertNext(s -> assertThat(s.failureDomain()).isEqualTo(FailureDomain.RACK))
                .verifyComplete();

        StepVerifier.create(catalog.findById("host-set"))
                .assertNext(s -> assertThat(s.failureDomain()).isEqualTo(FailureDomain.HOST))
                .verifyComplete();

        StepVerifier.create(catalog.findById("disk-set"))
                .assertNext(s -> assertThat(s.failureDomain()).isEqualTo(FailureDomain.DISK))
                .verifyComplete();
    }

    @Test
    void validatesDeviceReferencesAgainstKnownStorageDevices(@TempDir Path dir) throws IOException {
        Files.writeString(dir.resolve("default.yaml"), """
                diskSetId: default-diskset
                failureDomain: HOST
                deviceIds:
                  - disk-a
                  - missing-disk
                """);

        YamlDiskSetCatalog catalog = new YamlDiskSetCatalog(dir);

        assertThatThrownBy(() -> catalog.validateDeviceReferences(Set.of("disk-a")))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("unresolved storage device references")
                .hasMessageContaining("default-diskset")
                .hasMessageContaining("missing-disk");
    }

    @Test
    void acceptsResolvedDeviceReferences(@TempDir Path dir) throws IOException {
        Files.writeString(dir.resolve("default.yaml"), """
                diskSetId: default-diskset
                failureDomain: HOST
                deviceIds:
                  - disk-a
                  - disk-b
                """);

        YamlDiskSetCatalog catalog = new YamlDiskSetCatalog(dir);

        catalog.validateDeviceReferences(Set.of("disk-a", "disk-b"));
    }

    @Test
    void rejectsUnknownFailureDomain(@TempDir Path dir) throws IOException {
        Files.writeString(dir.resolve("bad-domain.yaml"), """
                diskSetId: bad-domain
                failureDomain: CHASSIS
                deviceIds:
                  - disk-a
                """);

        assertThatThrownBy(() -> new YamlDiskSetCatalog(dir))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("Unknown failure domain 'CHASSIS'");
    }

    @Test
    void rejectsBlankDeviceReference(@TempDir Path dir) throws IOException {
        Files.writeString(dir.resolve("blank-device.yaml"), """
                diskSetId: blank-device
                failureDomain: HOST
                deviceIds:
                  - disk-a
                  - ""
                """);

        assertThatThrownBy(() -> new YamlDiskSetCatalog(dir))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("Blank deviceId in disk-set 'blank-device'");
    }

    // -------------------------------------------------------------------------
    // Malformed YAML
    // -------------------------------------------------------------------------

    @Test
    void rejectsMalformedYaml(@TempDir Path dir) throws IOException {
        Files.writeString(dir.resolve("bad.yaml"), """
                diskSetId: [unclosed
                failureDomain: :::broken:::
                  - invalid
                """);

        assertThatThrownBy(() -> new YamlDiskSetCatalog(dir))
                .isInstanceOf(IllegalStateException.class);
    }

    // -------------------------------------------------------------------------
    // Empty directory
    // -------------------------------------------------------------------------

    @Test
    void emptyDirectoryProducesEmptyCatalog(@TempDir Path dir) {
        YamlDiskSetCatalog catalog = new YamlDiskSetCatalog(dir);

        StepVerifier.create(catalog.findAll())
                .verifyComplete();
    }
}
