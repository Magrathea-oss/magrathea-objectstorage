package com.example.magrathea.storageengine.application.port;

import com.example.magrathea.storageengine.domain.valueobject.StorageClassId;
import com.example.magrathea.storageengine.domain.valueobject.StoragePolicy;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

/**
 * Application port — reactive catalog for looking up storage policies.
 *
 * <p>Policies are identified by two orthogonal keys:
 * <ul>
 *   <li>{@code policyId} — the catalog entry ID (e.g. {@code "minio-standard"}),
 *       used as the YAML file key and for human-readable lookups.</li>
 *   <li>{@link StorageClassId} — the S3 storage class identifier (e.g. {@code "STANDARD"},
 *       {@code "MINIO_STANDARD"}), used by the orchestrator during object upload.</li>
 * </ul>
 */
public interface StoragePolicyCatalog {

    /**
     * Finds a policy by its catalog entry ID (the {@code policyId} YAML field).
     *
     * @param policyId the catalog key, e.g. {@code "minio-standard"}
     * @return the policy, or empty if not found
     */
    Mono<StoragePolicy> findById(String policyId);

    /**
     * Finds a policy by its S3 storage class identifier.
     * Used by the orchestrator to resolve the policy during an object upload.
     *
     * @param id the storage class identifier
     * @return the policy, or empty if not found
     */
    Mono<StoragePolicy> findBy(StorageClassId id);

    /**
     * Returns all loaded policies.
     *
     * @return all policies in the catalog
     */
    Flux<StoragePolicy> findAll();

    /**
     * Returns {@code true} if a policy with the given catalog entry ID exists.
     *
     * @param policyId the catalog key
     * @return {@code true} when the policy is present
     */
    default Mono<Boolean> exists(String policyId) {
        return findById(policyId).map(p -> true).defaultIfEmpty(false);
    }
}
