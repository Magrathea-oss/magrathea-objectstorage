package com.example.magrathea.objectstorage.infrastructure.adapter.persistence;

import com.example.magrathea.objectstorage.domain.aggregate.Bucket;
import com.example.magrathea.objectstorage.domain.repository.BucketRepository;
import com.example.magrathea.objectstorage.domain.valueobject.BucketAccelerateConfiguration;
import com.example.magrathea.objectstorage.domain.valueobject.BucketConfiguration;
import com.example.magrathea.objectstorage.domain.valueobject.BucketEncryptionConfiguration;
import com.example.magrathea.objectstorage.domain.valueobject.BucketLifecycleConfiguration;
import com.example.magrathea.objectstorage.domain.valueobject.BucketLoggingConfiguration;
import com.example.magrathea.objectstorage.domain.valueobject.BucketNotificationConfiguration;
import com.example.magrathea.objectstorage.domain.valueobject.BucketOwnershipControls;
import com.example.magrathea.objectstorage.domain.valueobject.BucketPolicy;
import com.example.magrathea.objectstorage.domain.valueobject.BucketReplicationConfiguration;
import com.example.magrathea.objectstorage.domain.valueobject.BucketRequestPaymentConfiguration;
import com.example.magrathea.objectstorage.domain.valueobject.BucketWebsiteConfiguration;
import com.example.magrathea.objectstorage.domain.valueobject.PublicAccessBlockConfiguration;

import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;

/**
 * In-memory implementation of BucketRepository.
 * Repository interface returns CompletableFuture — implementation fulfills async.
 */
@Repository
public class InMemoryBucketRepository implements BucketRepository {

    private final ConcurrentHashMap<String, Bucket> store = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<String, BucketConfiguration> configStore = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<String, BucketLifecycleConfiguration> lifecycleStore = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<String, BucketPolicy> policyStore = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<String, BucketEncryptionConfiguration> encryptionStore = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<String, BucketLoggingConfiguration> loggingStore = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<String, BucketWebsiteConfiguration> websiteStore = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<String, BucketNotificationConfiguration> notificationStore = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<String, BucketReplicationConfiguration> replicationStore = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<String, BucketRequestPaymentConfiguration> requestPaymentStore = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<String, BucketOwnershipControls> ownershipControlsStore = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<String, PublicAccessBlockConfiguration> publicAccessBlockStore = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<String, BucketAccelerateConfiguration> accelerateStore = new ConcurrentHashMap<>();

    @Override
    public CompletableFuture<Optional<Bucket>> findById(Bucket.Id id) {
        return CompletableFuture.completedFuture(Optional.ofNullable(store.get(id.value())));
    }

    @Override
    public CompletableFuture<Optional<Bucket>> findByName(String name) {
        var found = store.values().stream()
            .filter(b -> b.name().equals(name))
            .findFirst();
        return CompletableFuture.completedFuture(found);
    }

    @Override
    public CompletableFuture<List<Bucket>> findAll() {
        return CompletableFuture.completedFuture(List.copyOf(store.values()));
    }

    @Override
    public CompletableFuture<Void> save(Bucket bucket) {
        store.put(bucket.id().value(), bucket);
        return CompletableFuture.completedFuture(null);
    }

    @Override
    public CompletableFuture<Void> delete(Bucket.Id id) {
        store.remove(id.value());
        return CompletableFuture.completedFuture(null);
    }

    // ── CORS ──

    @Override
    public CompletableFuture<Optional<BucketConfiguration>> findConfiguration(String bucketName) {
        return CompletableFuture.completedFuture(Optional.ofNullable(configStore.get(bucketName)));
    }

    @Override
    public CompletableFuture<Void> saveConfiguration(BucketConfiguration configuration) {
        configStore.put(configuration.bucketName(), configuration);
        return CompletableFuture.completedFuture(null);
    }

    @Override
    public CompletableFuture<Void> deleteConfiguration(String bucketName) {
        configStore.remove(bucketName);
        return CompletableFuture.completedFuture(null);
    }

    // ── Lifecycle ──

    @Override
    public CompletableFuture<Optional<BucketLifecycleConfiguration>> findLifecycleConfiguration(String bucketName) {
        return CompletableFuture.completedFuture(Optional.ofNullable(lifecycleStore.get(bucketName)));
    }

    @Override
    public CompletableFuture<Void> saveLifecycleConfiguration(BucketLifecycleConfiguration configuration) {
        lifecycleStore.put(configuration.bucketName(), configuration);
        return CompletableFuture.completedFuture(null);
    }

    @Override
    public CompletableFuture<Void> deleteLifecycleConfiguration(String bucketName) {
        lifecycleStore.remove(bucketName);
        return CompletableFuture.completedFuture(null);
    }

    // ── Policy ──

    @Override
    public CompletableFuture<Optional<BucketPolicy>> findPolicy(String bucketName) {
        return CompletableFuture.completedFuture(Optional.ofNullable(policyStore.get(bucketName)));
    }

    @Override
    public CompletableFuture<Void> savePolicy(BucketPolicy policy) {
        policyStore.put(policy.bucketName(), policy);
        return CompletableFuture.completedFuture(null);
    }

    @Override
    public CompletableFuture<Void> deletePolicy(String bucketName) {
        policyStore.remove(bucketName);
        return CompletableFuture.completedFuture(null);
    }

    // ── Encryption ──

    @Override
    public CompletableFuture<Optional<BucketEncryptionConfiguration>> findEncryptionConfiguration(String bucketName) {
        return CompletableFuture.completedFuture(Optional.ofNullable(encryptionStore.get(bucketName)));
    }

    @Override
    public CompletableFuture<Void> saveEncryptionConfiguration(BucketEncryptionConfiguration configuration) {
        encryptionStore.put(configuration.bucketName(), configuration);
        return CompletableFuture.completedFuture(null);
    }

    @Override
    public CompletableFuture<Void> deleteEncryptionConfiguration(String bucketName) {
        encryptionStore.remove(bucketName);
        return CompletableFuture.completedFuture(null);
    }

    // ── Logging ──

    @Override
    public CompletableFuture<Optional<BucketLoggingConfiguration>> findLoggingConfiguration(String bucketName) {
        return CompletableFuture.completedFuture(Optional.ofNullable(loggingStore.get(bucketName)));
    }

    @Override
    public CompletableFuture<Void> saveLoggingConfiguration(BucketLoggingConfiguration configuration) {
        loggingStore.put(configuration.bucketName(), configuration);
        return CompletableFuture.completedFuture(null);
    }

    @Override
    public CompletableFuture<Void> deleteLoggingConfiguration(String bucketName) {
        loggingStore.remove(bucketName);
        return CompletableFuture.completedFuture(null);
    }

    // ── Website ──

    @Override
    public CompletableFuture<Optional<BucketWebsiteConfiguration>> findWebsiteConfiguration(String bucketName) {
        return CompletableFuture.completedFuture(Optional.ofNullable(websiteStore.get(bucketName)));
    }

    @Override
    public CompletableFuture<Void> saveWebsiteConfiguration(BucketWebsiteConfiguration configuration) {
        websiteStore.put(configuration.bucketName(), configuration);
        return CompletableFuture.completedFuture(null);
    }

    @Override
    public CompletableFuture<Void> deleteWebsiteConfiguration(String bucketName) {
        websiteStore.remove(bucketName);
        return CompletableFuture.completedFuture(null);
    }

    // ── Notification ──

    @Override
    public CompletableFuture<Optional<BucketNotificationConfiguration>> findNotificationConfiguration(String bucketName) {
        return CompletableFuture.completedFuture(Optional.ofNullable(notificationStore.get(bucketName)));
    }

    @Override
    public CompletableFuture<Void> saveNotificationConfiguration(BucketNotificationConfiguration configuration) {
        notificationStore.put(configuration.bucketName(), configuration);
        return CompletableFuture.completedFuture(null);
    }

    @Override
    public CompletableFuture<Void> deleteNotificationConfiguration(String bucketName) {
        notificationStore.remove(bucketName);
        return CompletableFuture.completedFuture(null);
    }

    // ── Replication ──

    @Override
    public CompletableFuture<Optional<BucketReplicationConfiguration>> findReplicationConfiguration(String bucketName) {
        return CompletableFuture.completedFuture(Optional.ofNullable(replicationStore.get(bucketName)));
    }

    @Override
    public CompletableFuture<Void> saveReplicationConfiguration(BucketReplicationConfiguration configuration) {
        replicationStore.put(configuration.bucketName(), configuration);
        return CompletableFuture.completedFuture(null);
    }

    @Override
    public CompletableFuture<Void> deleteReplicationConfiguration(String bucketName) {
        replicationStore.remove(bucketName);
        return CompletableFuture.completedFuture(null);
    }

    // ── Request Payment ──

    @Override
    public CompletableFuture<Optional<BucketRequestPaymentConfiguration>> findRequestPaymentConfiguration(String bucketName) {
        return CompletableFuture.completedFuture(Optional.ofNullable(requestPaymentStore.get(bucketName)));
    }

    @Override
    public CompletableFuture<Void> saveRequestPaymentConfiguration(BucketRequestPaymentConfiguration configuration) {
        requestPaymentStore.put(configuration.bucketName(), configuration);
        return CompletableFuture.completedFuture(null);
    }

    @Override
    public CompletableFuture<Void> deleteRequestPaymentConfiguration(String bucketName) {
        requestPaymentStore.remove(bucketName);
        return CompletableFuture.completedFuture(null);
    }

    // ── Ownership Controls ──

    @Override
    public CompletableFuture<Optional<BucketOwnershipControls>> findOwnershipControls(String bucketName) {
        return CompletableFuture.completedFuture(Optional.ofNullable(ownershipControlsStore.get(bucketName)));
    }

    @Override
    public CompletableFuture<Void> saveOwnershipControls(BucketOwnershipControls controls) {
        ownershipControlsStore.put(controls.bucketName(), controls);
        return CompletableFuture.completedFuture(null);
    }

    @Override
    public CompletableFuture<Void> deleteOwnershipControls(String bucketName) {
        ownershipControlsStore.remove(bucketName);
        return CompletableFuture.completedFuture(null);
    }

    // ── Public Access Block ──

    @Override
    public CompletableFuture<Optional<PublicAccessBlockConfiguration>> findPublicAccessBlockConfiguration(String bucketName) {
        return CompletableFuture.completedFuture(Optional.ofNullable(publicAccessBlockStore.get(bucketName)));
    }

    @Override
    public CompletableFuture<Void> savePublicAccessBlockConfiguration(PublicAccessBlockConfiguration configuration) {
        publicAccessBlockStore.put(configuration.bucketName(), configuration);
        return CompletableFuture.completedFuture(null);
    }

    @Override
    public CompletableFuture<Void> deletePublicAccessBlockConfiguration(String bucketName) {
        publicAccessBlockStore.remove(bucketName);
        return CompletableFuture.completedFuture(null);
    }

    // ── Accelerate ──

    @Override
    public CompletableFuture<Optional<BucketAccelerateConfiguration>> findAccelerateConfiguration(String bucketName) {
        return CompletableFuture.completedFuture(Optional.ofNullable(accelerateStore.get(bucketName)));
    }

    @Override
    public CompletableFuture<Void> saveAccelerateConfiguration(BucketAccelerateConfiguration configuration) {
        accelerateStore.put(configuration.bucketName(), configuration);
        return CompletableFuture.completedFuture(null);
    }

    @Override
    public CompletableFuture<Void> deleteAccelerateConfiguration(String bucketName) {
        accelerateStore.remove(bucketName);
        return CompletableFuture.completedFuture(null);
    }

    /** Reset state — used by test cleanup hooks. */
    public void reset() {
        store.clear();
        configStore.clear();
        lifecycleStore.clear();
        policyStore.clear();
        encryptionStore.clear();
        loggingStore.clear();
        websiteStore.clear();
        notificationStore.clear();
        replicationStore.clear();
        requestPaymentStore.clear();
        ownershipControlsStore.clear();
        publicAccessBlockStore.clear();
        accelerateStore.clear();
    }
}
