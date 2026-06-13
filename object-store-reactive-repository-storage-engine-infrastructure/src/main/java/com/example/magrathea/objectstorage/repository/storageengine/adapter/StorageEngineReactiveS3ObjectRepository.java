package com.example.magrathea.objectstorage.repository.storageengine.adapter;

import com.example.magrathea.objectstore.domain.aggregate.ActiveS3Object;
import com.example.magrathea.objectstore.domain.aggregate.Bucket;
import com.example.magrathea.objectstore.domain.aggregate.S3Object;
import com.example.magrathea.objectstore.domain.valueobject.ChecksumValue;
import com.example.magrathea.objectstore.domain.valueobject.EncryptionConfiguration;
import com.example.magrathea.objectstore.domain.valueobject.LegalHold;
import com.example.magrathea.objectstore.domain.valueobject.ObjectChecksum;
import com.example.magrathea.objectstore.domain.valueobject.ObjectLockConfiguration;
import com.example.magrathea.objectstore.domain.valueobject.RestoreConfiguration;
import com.example.magrathea.objectstore.reactive.repository.application.S3ObjectCommandRepository;
import com.example.magrathea.objectstore.reactive.repository.application.S3ObjectQueryRepository;
import com.example.magrathea.objectstore.reactive.repository.application.CommandResult;
import com.example.magrathea.objectstorage.repository.storageengine.acl.ChecksumDescriptor;
import com.example.magrathea.objectstorage.repository.storageengine.acl.EncryptionDescriptor;
import com.example.magrathea.objectstorage.repository.storageengine.acl.ObjectStoreToStorageEngineTranslator;
import com.example.magrathea.storageengine.application.service.ReactiveStorageOrchestrator;
import com.example.magrathea.storageengine.domain.aggregate.ObjectState;
import com.example.magrathea.storageengine.domain.aggregate.StoredObject;
import com.example.magrathea.storageengine.domain.valueobject.BucketId;
import com.example.magrathea.storageengine.domain.valueobject.BucketRef;
import com.example.magrathea.storageengine.domain.valueobject.CompleteUploadCommand;
import com.example.magrathea.storageengine.domain.valueobject.ManifestId;
import com.example.magrathea.storageengine.domain.valueobject.ObjectId;
import com.example.magrathea.storageengine.domain.valueobject.ObjectManifest;
import com.example.magrathea.storageengine.domain.valueobject.VersionId;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.buffer.DataBuffer;
import org.springframework.core.io.buffer.DefaultDataBufferFactory;
import org.springframework.stereotype.Repository;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;
import org.springframework.context.annotation.Profile;

/**
 * Implementation of S3Object repository interfaces backed by the Storage Engine.
 * <p>
 * Uses {@link ObjectStoreToStorageEngineTranslator} to convert between bounded contexts
 * and {@link ReactiveStorageOrchestrator} to persist objects.
 * Active only when the {@code storage-engine} Spring profile is enabled.
 * </p>
 */
@Repository
@Profile("storage-engine")
public class StorageEngineReactiveS3ObjectRepository
        implements S3ObjectCommandRepository, S3ObjectQueryRepository {

    private final ObjectStoreToStorageEngineTranslator translator;
    private final ReactiveStorageOrchestrator orchestrator;
    private final S3ObjectManifestReferenceStore referenceStore;
    private final Map<String, S3Object> storeByKey = new ConcurrentHashMap<>();
    private final Map<String, ManifestId> manifestByKey = new ConcurrentHashMap<>();
    private final AtomicLong versionCounter = new AtomicLong(1);
    private static final DefaultDataBufferFactory DATA_BUFFER_FACTORY =
        new DefaultDataBufferFactory();

    // Separate maps for Phase F data
    private final Map<String, LegalHold> legalHoldByKey = new ConcurrentHashMap<>();
    private final Map<String, EncryptionConfiguration> encryptionByKey = new ConcurrentHashMap<>();
    private final Map<String, ObjectLockConfiguration> lockConfigByKey = new ConcurrentHashMap<>();
    private final Map<String, ObjectLockConfiguration.RetentionPeriod> retentionByKey = new ConcurrentHashMap<>();
    private final Map<String, RestoreConfiguration> restoreByKey = new ConcurrentHashMap<>();

    public StorageEngineReactiveS3ObjectRepository(
            ObjectStoreToStorageEngineTranslator translator,
            ReactiveStorageOrchestrator orchestrator) {
        this(translator, orchestrator, "");
    }

    @Autowired
    public StorageEngineReactiveS3ObjectRepository(
            ObjectStoreToStorageEngineTranslator translator,
            ReactiveStorageOrchestrator orchestrator,
            @Value("${storage.engine.filesystem.root:}") String storageRoot) {
        this.translator = translator;
        this.orchestrator = orchestrator;
        this.referenceStore = new S3ObjectManifestReferenceStore(
            resolveStorageRoot(storageRoot).resolve("metadata").resolve("s3-object-references"));
    }

    // ── Aggregate operations ──

    @Override
    public Mono<CommandResult<S3Object>> save(S3Object object) {
        return Mono.defer(() -> {
            var storeKey = storeKey(object.key().bucket(), object.key().key());
            boolean exists = storeByKey.containsKey(storeKey);
            S3Object clean = object.clearEvents();
            storeByKey.put(storeKey, clean);
            long version = versionCounter.getAndIncrement();
            CommandResult<S3Object> result = exists
                ? new CommandResult.Updated<>(clean, object.domainEvents(), version)
                : new CommandResult.Created<>(clean, object.domainEvents(), version);
            return Mono.just(result);
        });
    }

    @Override
    public Mono<CommandResult<S3Object>> delete(S3Object object) {
        return Mono.defer(() -> {
            var storeKey = storeKey(object.key().bucket(), object.key().key());
            S3Object removed = storeByKey.remove(storeKey);
            manifestByKey.remove(storeKey);
            if (removed == null) {
                return Mono.error(new RuntimeException(
                    "S3Object not found: " + object.key().bucket() + "/" + object.key().key()));
            }
            long version = versionCounter.getAndIncrement();
            return Mono.just(new CommandResult.Deleted<>(removed, object.domainEvents(), version));
        }).flatMap(result -> referenceStore
            .delete(object.key().bucket(), object.key().key())
            .thenReturn(result));
    }

    @Override
    public Mono<S3Object> findByBucketAndKey(Bucket.Id bucketId,
                                              com.example.magrathea.objectstore.domain.valueobject.ObjectKey key) {
        return findObjectByBucketNameAndKey(key.bucket(), key);
    }

    @Override
    public Mono<S3Object> findByBucketAndKey(
            com.example.magrathea.objectstore.domain.valueobject.ObjectKey key) {
        return findObjectByBucketNameAndKey(key.bucket(), key);
    }

    @Override
    public Flux<S3Object> findByBucket(String bucketName) {
        return Flux.defer(() -> Flux.fromIterable(storeByKey.values())
                .filter(obj -> obj.key().bucket().equals(bucketName)))
            .switchIfEmpty(referenceStore.findByBucket(bucketName)
                .map(this::cacheAndRestore));
    }

    @Override
    public Mono<CommandResult<S3Object>> saveWithContent(
            S3Object object, Flux<DataBuffer> content, String storageClass) {
        // Translate to Storage Engine CompleteUploadCommand
        var checksumOpt = object.checksum() != null
            ? Optional.of(convertChecksum(object.checksum()))
            : Optional.<ChecksumDescriptor>empty();
        var encryptionOpt = object.encryption() != null
            ? Optional.of(convertEncryption(object.encryption()))
            : Optional.<EncryptionDescriptor>empty();

        var effectiveStorageClass = storageClass != null
            ? storageClass : object.storageClass();

        var command = translator.translatePutObject(
            object.key(),
            effectiveStorageClass,
            object.userMetadata(),
            object.size(),
            "application/octet-stream",
            checksumOpt,
            encryptionOpt);

        // Use orchestrator to store content
        return orchestrator.store(command, content)
            .flatMap(storedObject -> {
                var storeKey = storeKey(object.key().bucket(), object.key().key());
                S3Object clean = object.clearEvents();
                storeByKey.put(storeKey, clean);
                manifestByKey.put(storeKey, storedObject.manifestId());
                long version = versionCounter.getAndIncrement();
                CommandResult<S3Object> result =
                    new CommandResult.Created<>(clean, object.domainEvents(), version);
                return referenceStore.save(clean, storedObject.manifestId(), storedObject.versionId())
                    .thenReturn(result);
            });
    }

    @Override
    public Mono<CommandResult<S3Object>> saveWithContent(
            com.example.magrathea.objectstore.domain.valueobject.ObjectKey objectKey,
            Flux<DataBuffer> content, String storageClass) {
        // Build a CompleteUploadCommand from just the key and content
        var command = translator.translatePutObject(
            objectKey,
            storageClass,
            Map.of(),
            -1L, // size unknown — will be determined from content
            "application/octet-stream",
            Optional.empty(),
            Optional.empty());

        return orchestrator.store(command, content)
            .flatMap(storedObject -> {
                // Create an ActiveS3Object from the stored result
                var activeObject = translator.translateBack(storedObject, null);
                var storeKey = storeKey(
                    activeObject.key().bucket(), activeObject.key().key());
                storeByKey.put(storeKey, activeObject);
                manifestByKey.put(storeKey, storedObject.manifestId());
                long version = versionCounter.getAndIncrement();
                CommandResult<S3Object> result =
                    new CommandResult.Created<>(
                        activeObject, activeObject.domainEvents(), version);
                return referenceStore.save(activeObject, storedObject.manifestId(), storedObject.versionId())
                    .thenReturn(result);
            });
    }

    @Override
    public Flux<DataBuffer> getContent(
            com.example.magrathea.objectstore.domain.valueobject.ObjectKey key) {
        return Mono.defer(() -> {
                ManifestId manifestId = manifestByKey.get(storeKey(key.bucket(), key.key()));
                if (manifestId != null) {
                    return Mono.just(manifestId);
                }
                return referenceStore.find(key.bucket(), key.key())
                    .flatMap(optional -> optional
                        .map(reference -> {
                            cacheAndRestore(reference);
                            return Mono.just(reference.manifestId());
                        })
                        .orElseGet(Mono::empty));
            })
            .flatMapMany(orchestrator::read)
            .map(DATA_BUFFER_FACTORY::wrap);
    }

    // ── Phase F object config queries ──

    @Override
    public Mono<LegalHold> findLegalHold(String bucketName,
                                          com.example.magrathea.objectstore.domain.valueobject.ObjectKey key) {
        return Mono.defer(() -> Mono.justOrEmpty(
            legalHoldByKey.get(storeKey(bucketName, key))
        ));
    }

    @Override
    public Mono<ObjectLockConfiguration> findObjectLockConfiguration(
            String bucketName,
            com.example.magrathea.objectstore.domain.valueobject.ObjectKey key) {
        return Mono.defer(() -> Mono.justOrEmpty(
            lockConfigByKey.get(storeKey(bucketName, key))
        ));
    }

    @Override
    public Mono<ObjectLockConfiguration.RetentionPeriod> findRetention(
            String bucketName,
            com.example.magrathea.objectstore.domain.valueobject.ObjectKey key) {
        return Mono.defer(() -> Mono.justOrEmpty(
            retentionByKey.get(storeKey(bucketName, key))
        ));
    }

    @Override
    public Flux<DataBuffer> findTorrent(String bucketName,
                                         com.example.magrathea.objectstore.domain.valueobject.ObjectKey key) {
        return findObjectByBucketNameAndKey(bucketName, key)
            .flatMapMany(obj -> {
                var torrentContent = "Placeholder torrent file for %s/%s%nThis is a mock torrent response for S3 API compatibility."
                    .formatted(bucketName, key.key());
                var dataBuffer = DATA_BUFFER_FACTORY.wrap(
                    torrentContent.getBytes(StandardCharsets.UTF_8));
                return Flux.just(dataBuffer);
            });
    }

    // ── Phase F object config writes ──

    @Override
    public Mono<Void> saveLegalHold(String bucketName,
                                     com.example.magrathea.objectstore.domain.valueobject.ObjectKey key,
                                     LegalHold hold) {
        legalHoldByKey.put(storeKey(bucketName, key), hold);
        return Mono.empty();
    }

    @Override
    public Mono<Void> saveObjectLockConfiguration(
            String bucketName,
            com.example.magrathea.objectstore.domain.valueobject.ObjectKey key,
            ObjectLockConfiguration config) {
        lockConfigByKey.put(storeKey(bucketName, key), config);
        return Mono.empty();
    }

    @Override
    public Mono<Void> saveRetention(
            String bucketName,
            com.example.magrathea.objectstore.domain.valueobject.ObjectKey key,
            ObjectLockConfiguration.RetentionPeriod retention) {
        retentionByKey.put(storeKey(bucketName, key), retention);
        return Mono.empty();
    }

    @Override
    public Mono<Void> saveRestore(String bucketName,
                                   com.example.magrathea.objectstore.domain.valueobject.ObjectKey key,
                                   RestoreConfiguration config) {
        restoreByKey.put(storeKey(bucketName, key), config);
        return Mono.empty();
    }

    @Override
    public Mono<Void> saveEncryption(String bucketName,
                                      com.example.magrathea.objectstore.domain.valueobject.ObjectKey key,
                                      EncryptionConfiguration encryption) {
        encryptionByKey.put(storeKey(bucketName, key), encryption);
        return Mono.empty();
    }

    @Override
    public Mono<Void> renameObject(String bucketName,
                                    com.example.magrathea.objectstore.domain.valueobject.ObjectKey oldKey,
                                    com.example.magrathea.objectstore.domain.valueobject.ObjectKey newKey) {
        return findObjectByBucketNameAndKey(bucketName, oldKey)
            .flatMap(obj -> {
                var storedObject = ActiveS3Object.restoreActive(
                    newKey, obj.storageClass(), obj.userMetadata(),
                    obj.encryption(), obj.checksum(), obj.size(),
                    obj.createdAt(), obj.domainEvents());
                var clean = storedObject.clearEvents();
                var oldStoreKey = storeKey(bucketName, oldKey);
                var newStoreKey = storeKey(bucketName, newKey);
                storeByKey.remove(oldStoreKey);
                storeByKey.put(newStoreKey, clean);
                ManifestId manifestId = manifestByKey.remove(oldStoreKey);
                if (manifestId != null) {
                    manifestByKey.put(newStoreKey, manifestId);
                }
                return Mono.<Void>empty();
            })
            .switchIfEmpty(Mono.<Void>error(
                new RuntimeException(
                    "S3Object not found for rename: " + bucketName + "/" + oldKey.key())));
    }

    // ── Helper ──

    private static String storeKey(String bucketName,
                                    com.example.magrathea.objectstore.domain.valueobject.ObjectKey key) {
        return bucketName + "/" + key.key();
    }

    private static String storeKey(String bucketName, String keyName) {
        return bucketName + "/" + keyName;
    }

    private Mono<S3Object> findObjectByBucketNameAndKey(
            String bucketName,
            com.example.magrathea.objectstore.domain.valueobject.ObjectKey key) {
        return Mono.defer(() -> Mono.justOrEmpty(
                storeByKey.get(storeKey(bucketName, key))))
            .switchIfEmpty(referenceStore.find(bucketName, key.key())
                .flatMap(optional -> optional
                    .map(reference -> Mono.just((S3Object) cacheAndRestore(reference)))
                    .orElseGet(Mono::empty)));
    }

    private S3Object cacheAndRestore(S3ObjectManifestReferenceStore.Reference reference) {
        S3Object object = reference.toS3Object().clearEvents();
        String storeKey = storeKey(reference.bucket(), reference.key());
        storeByKey.put(storeKey, object);
        manifestByKey.put(storeKey, reference.manifestId());
        return object;
    }

    private static Path resolveStorageRoot(String configuredRoot) {
        if (configuredRoot == null || configuredRoot.isBlank()) {
            return Path.of(System.getProperty("java.io.tmpdir"),
                "magrathea-objectstorage", "storage-engine");
        }
        return Path.of(configuredRoot);
    }

    /**
     * Build a CompleteUploadCommand from an S3Object's metadata (no content).
     */
    private CompleteUploadCommand buildCommandFromS3Object(S3Object object) {
        var checksumOpt = object.checksum() != null
            ? Optional.of(convertChecksum(object.checksum()))
            : Optional.<ChecksumDescriptor>empty();
        var encryptionOpt = object.encryption() != null
            ? Optional.of(convertEncryption(object.encryption()))
            : Optional.<EncryptionDescriptor>empty();

        return translator.translatePutObject(
            object.key(),
            object.storageClass(),
            object.userMetadata(),
            object.size(),
            "application/octet-stream",
            checksumOpt,
            encryptionOpt);
    }

    private ChecksumDescriptor convertChecksum(ObjectChecksum checksum) {
        if (checksum.checksums().isEmpty()) {
            return ChecksumDescriptor.of(
                com.example.magrathea.objectstore.domain.valueobject.ChecksumAlgorithm.SHA256,
                "not-available");
        }
        var first = checksum.checksums().iterator().next();
        return ChecksumDescriptor.fromChecksumValue(first);
    }

    private EncryptionDescriptor convertEncryption(EncryptionConfiguration encryption) {
        return EncryptionDescriptor.of(
            encryption.algorithm(),
            encryption.keyReference(),
            encryption.encryptionContext());
    }

    /**
     * Reset the in-memory store (for testing).
     */
    public void reset() {
        storeByKey.clear();
        manifestByKey.clear();
        versionCounter.set(1);
        legalHoldByKey.clear();
        encryptionByKey.clear();
        lockConfigByKey.clear();
        retentionByKey.clear();
        restoreByKey.clear();
    }
}
