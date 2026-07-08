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
import com.example.magrathea.objectstore.reactive.repository.application.StorageObjectIntegrityException;
import com.example.magrathea.storageengine.application.exception.ChunkIntegrityException;
import com.example.magrathea.storageengine.application.exception.ManifestIntegrityException;
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
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
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

    // Durable per-object configuration (legal hold, encryption, lock, retention, restore)
    private final ObjectConfigMetadataStore objectConfigStore;

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
        Path metadataRoot = resolveStorageRoot(storageRoot).resolve("metadata");
        this.referenceStore = new S3ObjectManifestReferenceStore(
            metadataRoot.resolve("s3-object-references"));
        this.objectConfigStore = new ObjectConfigMetadataStore(
            metadataRoot.resolve("object-config"));
    }

    // ── Aggregate operations ──

    @Override
    public Mono<CommandResult<S3Object>> save(S3Object object) {
        return Mono.defer(() -> {
            var storeKey = storeKey(object.key().bucket(), object.key().key());
            boolean exists = storeByKey.containsKey(storeKey);
            S3Object clean = object.clearEvents();
            long version = versionCounter.getAndIncrement();
            CommandResult<S3Object> result = exists
                ? new CommandResult.Updated<>(clean, object.domainEvents(), version)
                : new CommandResult.Created<>(clean, object.domainEvents(), version);
            // The whole read-compose-write of the latest reference (and the in-memory
            // view) runs inside one serialized per-key commit — no find/save window.
            return referenceStore.commitLatest(
                    object.key().bucket(), object.key().key(),
                    current -> {
                        if (current.isEmpty()) {
                            // No committed content reference — metadata-only aggregate.
                            storeByKey.put(storeKey, clean);
                            return current;
                        }
                        var committed = current.get();
                        if (!belongsToCommittedUpload(committed, clean)) {
                            // This metadata save belongs to an upload that lost a
                            // concurrent same-key race. Last writer wins: keep the
                            // winner's self-consistent reference and in-memory view
                            // untouched — never mix fields from different uploads.
                            return current;
                        }
                        storeByKey.put(storeKey, clean);
                        manifestByKey.put(storeKey, committed.manifestId());
                        // Overlay this save's metadata while keeping the committed
                        // (manifestId, versionId) pair together from ONE source —
                        // never merged with a possibly-stale in-memory manifest.
                        return Optional.of(new S3ObjectManifestReferenceStore.Reference(
                            committed.bucket(),
                            committed.key(),
                            clean.storageClass(),
                            clean.etag() != null ? clean.etag() : committed.etag(),
                            clean.userMetadata(),
                            clean.objectTags(),
                            clean.size(),
                            committed.manifestId(),
                            committed.versionId(),
                            clean.createdAt()));
                    })
                .thenReturn(result);
        });
    }

    /**
     * A metadata save may update the committed reference only when it provably belongs
     * to the same upload as the committed content (ETag correlation). A save carrying a
     * different ETag lost a concurrent same-key race and must not touch the winner.
     */
    private static boolean belongsToCommittedUpload(
            S3ObjectManifestReferenceStore.Reference committed, S3Object object) {
        return committed.etag() == null
            || object.etag() == null
            || committed.etag().equals(object.etag());
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
            .then(Mono.fromRunnable(() ->
                objectConfigStore.delete(object.key().bucket(), object.key().key()))
                .subscribeOn(reactor.core.scheduler.Schedulers.boundedElastic()))
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

        var effectiveStorageClass = effectiveStorageClass(storageClass, object.storageClass());

        var command = translator.translatePutObject(
            object.key(),
            effectiveStorageClass,
            object.userMetadata(),
            object.size(),
            "application/octet-stream",
            checksumOpt,
            encryptionOpt);

        // Use orchestrator to store content. The upload's own bytes are measured while
        // streaming so the committed reference is composed entirely from THIS upload's
        // result (versionId + manifestId + etag + size) — single-writer compose.
        return Mono.defer(() -> {
            UploadMeasurement measurement = new UploadMeasurement();
            return orchestrator.store(command, content.doOnNext(measurement::update))
                .flatMap(storedObject -> {
                    var storeKey = storeKey(object.key().bucket(), object.key().key());
                    S3Object committed =
                        committedObject(object, effectiveStorageClass, measurement);
                    long version = versionCounter.getAndIncrement();
                    CommandResult<S3Object> result =
                        new CommandResult.Created<>(committed, object.domainEvents(), version);
                    return referenceStore.commitLatest(
                            object.key().bucket(), object.key().key(),
                            current -> {
                                storeByKey.put(storeKey, committed);
                                manifestByKey.put(storeKey, storedObject.manifestId());
                                return Optional.of(S3ObjectManifestReferenceStore.Reference.from(
                                    committed, storedObject.manifestId(), storedObject.versionId()));
                            })
                        .thenReturn(result);
                });
        });
    }

    @Override
    public Mono<CommandResult<S3Object>> saveWithContent(
            com.example.magrathea.objectstore.domain.valueobject.ObjectKey objectKey,
            Flux<DataBuffer> content, String storageClass) {
        // Build a CompleteUploadCommand from just the key and content
        var command = translator.translatePutObject(
            objectKey,
            effectiveStorageClass(storageClass, null),
            Map.of(),
            -1L, // size unknown — will be determined from content
            "application/octet-stream",
            Optional.empty(),
            Optional.empty());

        return Mono.defer(() -> {
            UploadMeasurement measurement = new UploadMeasurement();
            return orchestrator.store(command, content.doOnNext(measurement::update))
                .flatMap(storedObject -> {
                    // Create an ActiveS3Object from the stored result, completed with
                    // the ETag and size measured from THIS upload's own bytes.
                    var restored = translator.translateBack(storedObject, null);
                    var committed = ActiveS3Object.restoreActive(
                            restored.key(),
                            restored.storageClass(),
                            restored.userMetadata(),
                            restored.encryption(),
                            restored.checksum(),
                            measurement.size(),
                            restored.createdAt(),
                            java.util.List.of())
                        .withEtag(measurement.etag());
                    var storeKey = storeKey(
                        committed.key().bucket(), committed.key().key());
                    long version = versionCounter.getAndIncrement();
                    CommandResult<S3Object> result =
                        new CommandResult.Created<>(
                            committed, committed.domainEvents(), version);
                    return referenceStore.commitLatest(
                            committed.key().bucket(), committed.key().key(),
                            current -> {
                                storeByKey.put(storeKey, committed);
                                manifestByKey.put(storeKey, storedObject.manifestId());
                                return Optional.of(S3ObjectManifestReferenceStore.Reference.from(
                                    committed, storedObject.manifestId(), storedObject.versionId()));
                            })
                        .thenReturn(result);
                });
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
            .<DataBuffer>map(DATA_BUFFER_FACTORY::wrap)
            .onErrorMap(
                e -> e instanceof ChunkIntegrityException || e instanceof ManifestIntegrityException,
                e -> new StorageObjectIntegrityException(e.getMessage(), e));
    }

    // ── Phase F object config queries ──

    @Override
    public Mono<LegalHold> findLegalHold(String bucketName,
                                          com.example.magrathea.objectstore.domain.valueobject.ObjectKey key) {
        return findObjectConfig(bucketName, key)
            .flatMap(config -> Mono.justOrEmpty(config.legalHold()));
    }

    @Override
    public Mono<ObjectLockConfiguration> findObjectLockConfiguration(
            String bucketName,
            com.example.magrathea.objectstore.domain.valueobject.ObjectKey key) {
        return findObjectConfig(bucketName, key)
            .flatMap(config -> Mono.justOrEmpty(config.lockConfiguration()));
    }

    @Override
    public Mono<ObjectLockConfiguration.RetentionPeriod> findRetention(
            String bucketName,
            com.example.magrathea.objectstore.domain.valueobject.ObjectKey key) {
        return findObjectConfig(bucketName, key)
            .flatMap(config -> Mono.justOrEmpty(config.retention()));
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
        return updateObjectConfig(bucketName, key, config -> config.withLegalHold(hold));
    }

    @Override
    public Mono<Void> saveObjectLockConfiguration(
            String bucketName,
            com.example.magrathea.objectstore.domain.valueobject.ObjectKey key,
            ObjectLockConfiguration config) {
        return updateObjectConfig(bucketName, key, doc -> doc.withLockConfiguration(config));
    }

    @Override
    public Mono<Void> saveRetention(
            String bucketName,
            com.example.magrathea.objectstore.domain.valueobject.ObjectKey key,
            ObjectLockConfiguration.RetentionPeriod retention) {
        return updateObjectConfig(bucketName, key, config -> config.withRetention(retention));
    }

    @Override
    public Mono<Void> saveRestore(String bucketName,
                                   com.example.magrathea.objectstore.domain.valueobject.ObjectKey key,
                                   RestoreConfiguration config) {
        return updateObjectConfig(bucketName, key, doc -> doc.withRestore(config));
    }

    @Override
    public Mono<Void> saveEncryption(String bucketName,
                                      com.example.magrathea.objectstore.domain.valueobject.ObjectKey key,
                                      EncryptionConfiguration encryption) {
        return updateObjectConfig(bucketName, key, config -> config.withEncryption(encryption));
    }

    private Mono<ObjectConfigMetadataStore.StoredObjectConfig> findObjectConfig(
            String bucketName,
            com.example.magrathea.objectstore.domain.valueobject.ObjectKey key) {
        return Mono.fromCallable(() -> objectConfigStore.find(bucketName, key.key()))
            .subscribeOn(reactor.core.scheduler.Schedulers.boundedElastic())
            .flatMap(Mono::justOrEmpty);
    }

    private Mono<Void> updateObjectConfig(
            String bucketName,
            com.example.magrathea.objectstore.domain.valueobject.ObjectKey key,
            java.util.function.UnaryOperator<ObjectConfigMetadataStore.StoredObjectConfig> mutation) {
        return Mono.fromRunnable(() ->
                objectConfigStore.update(bucketName, key.key(), mutation))
            .subscribeOn(reactor.core.scheduler.Schedulers.boundedElastic())
            .then();
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
            effectiveStorageClass(object.storageClass(), null),
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

    private static String effectiveStorageClass(String primaryStorageClass, String fallbackStorageClass) {
        if (primaryStorageClass != null && !primaryStorageClass.isBlank()) {
            return primaryStorageClass.trim();
        }
        if (fallbackStorageClass != null && !fallbackStorageClass.isBlank()) {
            return fallbackStorageClass.trim();
        }
        return "STANDARD";
    }

    private static S3Object committedObject(
            S3Object object, String effectiveStorageClass, UploadMeasurement measurement) {
        return ActiveS3Object.restoreActive(
                object.key(),
                effectiveStorageClass,
                object.userMetadata(),
                object.encryption(),
                object.checksum(),
                measurement.size(),
                object.createdAt(),
                java.util.List.of())
            .withEtag(measurement.etag());
    }

    /**
     * Measures the ETag (MD5) and byte length of an upload's own content stream, so the
     * committed reference is composed entirely from ONE upload's result and never needs
     * to be patched later from possibly-stale state.
     */
    private static final class UploadMeasurement {
        private final MessageDigest digest = md5();
        private final AtomicLong size = new AtomicLong();

        void update(DataBuffer buffer) {
            size.addAndGet(buffer.readableByteCount());
            try (DataBuffer.ByteBufferIterator iterator = buffer.readableByteBuffers()) {
                while (iterator.hasNext()) {
                    digest.update(iterator.next());
                }
            }
        }

        long size() {
            return size.get();
        }

        String etag() {
            byte[] bytes = digest.digest();
            StringBuilder hex = new StringBuilder(bytes.length * 2 + 2).append('"');
            for (byte b : bytes) {
                hex.append(Character.forDigit((b >> 4) & 0xF, 16))
                    .append(Character.forDigit(b & 0xF, 16));
            }
            return hex.append('"').toString();
        }

        private static MessageDigest md5() {
            try {
                return MessageDigest.getInstance("MD5");
            } catch (NoSuchAlgorithmException e) {
                throw new IllegalStateException("MD5 algorithm unavailable", e);
            }
        }
    }

    /**
     * Reset the in-memory store (for testing).
     */
    public void reset() {
        storeByKey.clear();
        manifestByKey.clear();
        versionCounter.set(1);
        objectConfigStore.wipe();
    }

    /**
     * Restart simulation (for testing) — discards all in-memory caches while
     * keeping every durable file (object references and per-object config), so
     * subsequent reads must reload state from the filesystem exactly like a
     * process restart would.
     */
    public void reloadFromDisk() {
        storeByKey.clear();
        manifestByKey.clear();
    }
}
