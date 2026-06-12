package com.example.magrathea.storageengine.infrastructure.yaml;

import com.example.magrathea.storageengine.domain.valueobject.StorageClassId;
import com.example.magrathea.storageengine.domain.valueobject.StoragePolicy;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import reactor.test.StepVerifier;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Integration tests for {@link YamlStoragePolicyCatalog}.
 * No Spring context — uses {@link TempDir} for filesystem-level YAML directories.
 */
class YamlStoragePolicyCatalogTest {

    // -------------------------------------------------------------------------
    // Single policy load
    // -------------------------------------------------------------------------

    @Test
    void loadsSinglePolicyFromYaml(@TempDir Path dir) throws IOException {
        Files.writeString(dir.resolve("standard.yaml"), """
                policyId: standard-policy
                version: "1.0"
                storageClassId: STANDARD
                replication:
                  factor: 1
                """);

        YamlStoragePolicyCatalog catalog = new YamlStoragePolicyCatalog(dir);

        StepVerifier.create(catalog.findById("standard-policy"))
                .assertNext(policy -> {
                    assertThat(policy.id().value()).isEqualTo("STANDARD");
                    assertThat(policy.replication().factor()).isEqualTo(1);
                    assertThat(policy.dedup()).isEmpty();
                    assertThat(policy.compression()).isEmpty();
                    assertThat(policy.encryption()).isEmpty();
                    assertThat(policy.erasureCoding()).isEmpty();
                })
                .verifyComplete();
    }

    @Test
    void loadsSinglePolicyFromYaml_findByStorageClassId(@TempDir Path dir) throws IOException {
        Files.writeString(dir.resolve("standard-ec.yaml"), """
                policyId: standard-ec
                version: "1.0"
                storageClassId: STANDARD
                erasureCoding:
                  enabled: true
                  dataBlocks: 4
                  parityBlocks: 2
                replication:
                  factor: 1
                """);

        YamlStoragePolicyCatalog catalog = new YamlStoragePolicyCatalog(dir);

        StepVerifier.create(catalog.findBy(StorageClassId.STANDARD))
                .assertNext(policy -> {
                    assertThat(policy.id().value()).isEqualTo("STANDARD");
                    assertThat(policy.dedup()).isEmpty();
                    assertThat(policy.erasureCoding()).isPresent();
                    assertThat(policy.erasureCoding().get().dataBlocks()).isEqualTo(4);
                    assertThat(policy.erasureCoding().get().parityBlocks()).isEqualTo(2);
                })
                .verifyComplete();
    }

    // -------------------------------------------------------------------------
    // Multiple policies
    // -------------------------------------------------------------------------

    @Test
    void loadsMultiplePoliciesFromDirectory(@TempDir Path dir) throws IOException {
        Files.writeString(dir.resolve("policy-a.yaml"), """
                policyId: policy-a
                storageClassId: STANDARD
                replication:
                  factor: 1
                """);
        Files.writeString(dir.resolve("policy-b.yaml"), """
                policyId: policy-b
                storageClassId: REDUCED_REDUNDANCY
                replication:
                  factor: 1
                """);

        YamlStoragePolicyCatalog catalog = new YamlStoragePolicyCatalog(dir);

        StepVerifier.create(catalog.findAll().collectList())
                .assertNext(list -> {
                    assertThat(list).hasSize(2);
                    assertThat(list).extracting(p -> p.id().value())
                            .containsExactlyInAnyOrder("STANDARD", "REDUCED_REDUNDANCY");
                })
                .verifyComplete();
    }

    // -------------------------------------------------------------------------
    // Duplicate policyId validation
    // -------------------------------------------------------------------------

    @Test
    void rejectsDuplicatePolicyIds(@TempDir Path dir) throws IOException {
        Files.writeString(dir.resolve("dup-a.yaml"), """
                policyId: same-id
                storageClassId: STANDARD
                replication:
                  factor: 1
                """);
        Files.writeString(dir.resolve("dup-b.yaml"), """
                policyId: same-id
                storageClassId: REDUCED_REDUNDANCY
                replication:
                  factor: 1
                """);

        assertThatThrownBy(() -> new YamlStoragePolicyCatalog(dir))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("same-id");
    }

    // -------------------------------------------------------------------------
    // Malformed YAML
    // -------------------------------------------------------------------------

    @Test
    void rejectsMalformedYaml(@TempDir Path dir) throws IOException {
        Files.writeString(dir.resolve("bad.yaml"), """
                policyId: [unclosed
                storageClassId: :::broken:::
                  - invalid
                """);

        assertThatThrownBy(() -> new YamlStoragePolicyCatalog(dir))
                .isInstanceOf(IllegalStateException.class);
    }

    // -------------------------------------------------------------------------
    // Unknown ID
    // -------------------------------------------------------------------------

    @Test
    void returnsEmptyForUnknownId(@TempDir Path dir) throws IOException {
        Files.writeString(dir.resolve("real.yaml"), """
                policyId: real-policy
                storageClassId: STANDARD
                replication:
                  factor: 1
                """);

        YamlStoragePolicyCatalog catalog = new YamlStoragePolicyCatalog(dir);

        StepVerifier.create(catalog.findById("no-such-policy"))
                .verifyComplete();
    }

    // -------------------------------------------------------------------------
    // findAll
    // -------------------------------------------------------------------------

    @Test
    void findsAllReturnsCatalogContents(@TempDir Path dir) throws IOException {
        Files.writeString(dir.resolve("p1.yaml"), """
                policyId: p1
                storageClassId: STANDARD
                replication:
                  factor: 1
                """);
        Files.writeString(dir.resolve("p2.yaml"), """
                policyId: p2
                storageClassId: INTELLIGENT_TIERING
                replication:
                  factor: 1
                """);
        Files.writeString(dir.resolve("p3.yaml"), """
                policyId: p3
                storageClassId: GLACIER
                replication:
                  factor: 1
                """);

        YamlStoragePolicyCatalog catalog = new YamlStoragePolicyCatalog(dir);

        StepVerifier.create(catalog.findAll().count())
                .expectNext(3L)
                .verifyComplete();
    }

    // -------------------------------------------------------------------------
    // exists()
    // -------------------------------------------------------------------------

    @Test
    void existsReturnsTrueForKnownPolicy(@TempDir Path dir) throws IOException {
        Files.writeString(dir.resolve("exists-test.yaml"), """
                policyId: exists-test
                storageClassId: STANDARD
                replication:
                  factor: 1
                """);

        YamlStoragePolicyCatalog catalog = new YamlStoragePolicyCatalog(dir);

        StepVerifier.create(catalog.exists("exists-test"))
                .expectNext(true)
                .verifyComplete();

        StepVerifier.create(catalog.exists("does-not-exist"))
                .expectNext(false)
                .verifyComplete();
    }

    // -------------------------------------------------------------------------
    // Dedup policy
    // -------------------------------------------------------------------------

    @Test
    void loadsDedupPolicyFromYaml(@TempDir Path dir) throws IOException {
        Files.writeString(dir.resolve("dedup.yaml"), """
                policyId: dedup-policy
                storageClassId: STANDARD
                dedup:
                  enabled: true
                  scope: GLOBAL
                  chunkSizeBytes: 4096
                  algorithm: BLAKE2
                  alignment: BLOCK_BOUNDARY
                replication:
                  factor: 1
                """);

        YamlStoragePolicyCatalog catalog = new YamlStoragePolicyCatalog(dir);

        StepVerifier.create(catalog.findById("dedup-policy"))
                .assertNext(policy -> {
                    assertThat(policy.dedup()).isPresent();
                    var dedup = policy.dedup().get();
                    assertThat(dedup.chunkSize()).isEqualTo(4096L);
                    assertThat(dedup.algorithm().name()).isEqualTo("BLAKE2");
                })
                .verifyComplete();
    }

    // -------------------------------------------------------------------------
    // Empty directory
    // -------------------------------------------------------------------------

    @Test
    void emptyDirectoryProducesEmptyCatalog(@TempDir Path dir) {
        YamlStoragePolicyCatalog catalog = new YamlStoragePolicyCatalog(dir);

        StepVerifier.create(catalog.findAll())
                .verifyComplete();
    }
}
