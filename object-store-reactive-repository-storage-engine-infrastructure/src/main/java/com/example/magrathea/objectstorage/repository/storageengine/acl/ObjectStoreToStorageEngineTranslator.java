package com.example.magrathea.objectstorage.repository.storageengine.acl;

import com.example.magrathea.objectstore.domain.aggregate.ActiveS3Object;
import com.example.magrathea.objectstore.domain.aggregate.Bucket;
import com.example.magrathea.objectstore.domain.aggregate.S3Object;
import com.example.magrathea.objectstore.domain.valueobject.ChecksumValue;
import com.example.magrathea.objectstore.domain.valueobject.EncryptionConfiguration;
import com.example.magrathea.objectstore.domain.valueobject.EncryptionKeyReference;
import com.example.magrathea.objectstore.domain.valueobject.EncryptionContext;
import com.example.magrathea.objectstore.domain.valueobject.ObjectChecksum;
import com.example.magrathea.objectstore.domain.valueobject.UserMetadata;
import com.example.magrathea.storageengine.domain.aggregate.StoredObject;
import com.example.magrathea.storageengine.domain.valueobject.BucketId;
import com.example.magrathea.storageengine.domain.valueobject.BucketRef;
import com.example.magrathea.storageengine.domain.valueobject.CompleteUploadCommand;
import com.example.magrathea.storageengine.domain.valueobject.ContentHash;
import com.example.magrathea.storageengine.domain.valueobject.DeclaredChecksum;
import com.example.magrathea.storageengine.domain.valueobject.EncryptionMode;
import com.example.magrathea.storageengine.domain.valueobject.EncryptionRequest;
import com.example.magrathea.storageengine.domain.valueobject.KeyReference;
import com.example.magrathea.storageengine.domain.valueobject.ObjectContentDescriptor;
import com.example.magrathea.storageengine.domain.valueobject.ObjectId;
import com.example.magrathea.storageengine.domain.valueobject.ObjectManifest;
import com.example.magrathea.storageengine.domain.valueobject.ObjectMetadataDescriptor;
import com.example.magrathea.storageengine.domain.valueobject.PartDescriptor;
import com.example.magrathea.storageengine.domain.valueobject.StorageClassId;
import com.example.magrathea.storageengine.domain.valueobject.UploadMode;
import com.example.magrathea.storageengine.domain.valueobject.UploadRequestContext;
import com.example.magrathea.storageengine.domain.valueobject.VersionId;

import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;

import java.time.ZonedDateTime;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * ACL (Anti-Corruption Layer) translator between Object Store concepts and Storage Engine concepts.
 * <p>
 * Lives in the infrastructure module which depends on both bounded contexts.
 * Translates Object Store domain aggregates/value objects to Storage Engine commands
 * and translates Storage Engine results back to Object Store formats.
 * Active only when the {@code storage-engine} Spring profile is enabled.
 * </p>
 *
 * <p>NOTE: Both bounded contexts define a {@code ChecksumAlgorithm} and {@code ObjectKey} class.
 * To avoid name collisions, this class uses fully qualified names for these types.</p>
 */
@Component
@Profile("storage-engine")
public class ObjectStoreToStorageEngineTranslator {

    // ── ObjectStore → StorageEngine: CompleteUploadCommand ──

    /**
     * Translates a PutObject request to a Storage Engine CompleteUploadCommand with SINGLE_OBJECT mode.
     *
     * @param objectKey     the Object Store object key (bucket/key composite)
     * @param storageClass  the storage class string
     * @param userMetadata  the user metadata map
     * @param objectSize    the object size in bytes
     * @param mimeType      the MIME type
     * @param checksum      optional checksum descriptor
     * @param encryption    optional encryption descriptor
     * @return the Storage Engine CompleteUploadCommand
     */
    public CompleteUploadCommand translatePutObject(
            com.example.magrathea.objectstore.domain.valueobject.ObjectKey objectKey,
            String storageClass,
            Map<String, String> userMetadata,
            long objectSize,
            String mimeType,
            Optional<ChecksumDescriptor> checksum,
            Optional<EncryptionDescriptor> encryption) {

        // Convert Object Store ObjectKey to Storage Engine ObjectKey
        com.example.magrathea.storageengine.domain.valueobject.ObjectKey seKey =
            com.example.magrathea.storageengine.domain.valueobject.ObjectKey.of(
                objectKey.bucket(), objectKey.key());

        // Convert bucket name to Storage Engine BucketRef
        BucketRef bucketRef = BucketRef.of(
            BucketId.of(objectKey.bucket()),
            objectKey.bucket());

        // Convert storage class
        StorageClassId storageClassId = StorageClassId.of(storageClass);

        // Convert content descriptor
        ObjectContentDescriptor contentDescriptor =
            ObjectContentDescriptor.of(mimeType, objectSize);

        // Convert metadata
        ObjectMetadataDescriptor metadataDescriptor =
            ObjectMetadataDescriptor.of(userMetadata);

        // Convert encryption
        EncryptionRequest encryptionRequest = encryption
            .map(ed -> translateEncryption(ed))
            .orElse(EncryptionRequest.none());

        // Convert checksum
        Optional<DeclaredChecksum> declaredChecksum = checksum
            .map(cd -> DeclaredChecksum.of(
                translateChecksumAlgorithm(cd.algorithm()),
                cd.base64Value()));

        // Build context
        UploadRequestContext context = UploadRequestContext.of(
            seKey,
            bucketRef,
            storageClassId,
            contentDescriptor,
            metadataDescriptor,
            encryptionRequest,
            declaredChecksum);

        return new CompleteUploadCommand(
            context,
            UploadMode.SINGLE_OBJECT,
            Optional.empty());
    }

    /**
     * Translates a multipart completion request to a Storage Engine CompleteUploadCommand with MULTIPART mode.
     *
     * @param objectKey     the Object Store object key
     * @param storageClass  the storage class string
     * @param userMetadata  the user metadata map
     * @param objectSize    the object size in bytes
     * @param mimeType      the MIME type
     * @param checksum      optional checksum descriptor
     * @param encryption    optional encryption descriptor
     * @param parts         the S3 parts list
     * @return the Storage Engine CompleteUploadCommand
     */
    public CompleteUploadCommand translateCompleteMultipart(
            com.example.magrathea.objectstore.domain.valueobject.ObjectKey objectKey,
            String storageClass,
            Map<String, String> userMetadata,
            long objectSize,
            String mimeType,
            Optional<ChecksumDescriptor> checksum,
            Optional<EncryptionDescriptor> encryption,
            List<PartDescriptorS3> parts) {

        // Same context as PutObject
        com.example.magrathea.storageengine.domain.valueobject.ObjectKey seKey =
            com.example.magrathea.storageengine.domain.valueobject.ObjectKey.of(
                objectKey.bucket(), objectKey.key());
        BucketRef bucketRef = BucketRef.of(
            BucketId.of(objectKey.bucket()),
            objectKey.bucket());
        StorageClassId storageClassId = StorageClassId.of(storageClass);
        ObjectContentDescriptor contentDescriptor =
            ObjectContentDescriptor.of(mimeType, objectSize);
        ObjectMetadataDescriptor metadataDescriptor =
            ObjectMetadataDescriptor.of(userMetadata);
        EncryptionRequest encryptionRequest = encryption
            .map(ed -> translateEncryption(ed))
            .orElse(EncryptionRequest.none());
        Optional<DeclaredChecksum> declaredChecksum = checksum
            .map(cd -> DeclaredChecksum.of(
                translateChecksumAlgorithm(cd.algorithm()),
                cd.base64Value()));

        UploadRequestContext context = UploadRequestContext.of(
            seKey,
            bucketRef,
            storageClassId,
            contentDescriptor,
            metadataDescriptor,
            encryptionRequest,
            declaredChecksum);

        // Translate parts
        List<PartDescriptor> seParts = parts.stream()
            .map(p -> PartDescriptor.of(
                p.partNumber(),
                p.partSize(),
                p.partChecksum()
                    .map(cd -> DeclaredChecksum.of(
                        translateChecksumAlgorithm(cd.algorithm()),
                        cd.base64Value()))))
            .toList();

        return new CompleteUploadCommand(
            context,
            UploadMode.MULTIPART,
            Optional.of(seParts));
    }

    // ── StorageEngine → ObjectStore: Result translation ──

    /**
     * Translates a Storage Engine StoredObject + ObjectManifest back to an Object Store ActiveS3Object.
     *
     * @param storedObject the Storage Engine stored object aggregate
     * @param manifest     the Storage Engine manifest (may be null if not yet available)
     * @return a reconstructed ActiveS3Object
     */
    public ActiveS3Object translateBack(
            StoredObject storedObject,
            ObjectManifest manifest) {

        // Reconstruct ObjectKey from StoredObject's ObjectId value
        String objectIdValue = storedObject.objectId().value();
        String bucketName = objectIdValue.contains("/")
            ? objectIdValue.substring(0, objectIdValue.indexOf('/'))
            : objectIdValue;
        String keyName = objectIdValue.contains("/")
            ? objectIdValue.substring(objectIdValue.indexOf('/') + 1)
            : "";
        com.example.magrathea.objectstore.domain.valueobject.ObjectKey objectKey =
            com.example.magrathea.objectstore.domain.valueobject.ObjectKey.of(bucketName, keyName);

        // Reconstruct storage class
        String storageClass = storedObject.storageClassId().value();

        // Reconstruct user metadata (empty — metadata is stored in Storage Engine metadata)
        Map<String, String> userMetadata = Map.of();

        // Reconstruct encryption (none from Storage Engine — stored as metadata)
        EncryptionConfiguration encryption = null;

        // Reconstruct checksum from manifest
        ObjectChecksum checksum = manifest != null && !manifest.chunks().isEmpty()
            ? buildChecksumFromManifest(manifest)
            : null;

        // Size from manifest or StoredObject (use manifest if available)
        long size = manifest != null
            ? manifest.totalOriginalSize()
            : 0L;

        // Reconstruct timestamps
        ZonedDateTime createdAt = storedObject.createdAt();
        ZonedDateTime lastModified = storedObject.lastModified();

        // Build events (empty — this is a restore)
        List<com.example.magrathea.objectstore.domain.event.ObjectStoreEvent> events = List.of();

        // Return restored ActiveS3Object
        return ActiveS3Object.restoreActive(
            objectKey,
            storageClass,
            userMetadata,
            encryption,
            checksum,
            size,
            createdAt,
            events);
    }

    // ── Helper translations ──

    /**
     * Translates an Object Store EncryptionDescriptor to a Storage Engine EncryptionRequest.
     */
    public EncryptionRequest translateEncryption(EncryptionDescriptor descriptor) {
        EncryptionMode mode;
        switch (descriptor.algorithm()) {
            case AES256 -> mode = EncryptionMode.SSE_S3;
            case AWS_KMS -> mode = EncryptionMode.SSE_KMS;
            case SSE_C -> mode = EncryptionMode.SSE_C;
            default -> mode = EncryptionMode.NONE;
        }

        Optional<KeyReference> keyRef = descriptor.keyReference() != null
            ? Optional.of(KeyReference.of(descriptor.keyReference().keyId()))
            : Optional.empty();

        return EncryptionRequest.of(mode, keyRef);
    }

    /**
     * Translates a Storage Engine EncryptionRequest back to an EncryptionDescriptor.
     */
    public EncryptionDescriptor translateEncryptionBack(EncryptionRequest request) {
        com.example.magrathea.objectstore.domain.valueobject.EncryptionAlgorithm algorithm;
        switch (request.mode()) {
            case SSE_S3 -> algorithm = com.example.magrathea.objectstore.domain.valueobject.EncryptionAlgorithm.AES256;
            case SSE_KMS -> algorithm = com.example.magrathea.objectstore.domain.valueobject.EncryptionAlgorithm.AWS_KMS;
            case SSE_C -> algorithm = com.example.magrathea.objectstore.domain.valueobject.EncryptionAlgorithm.SSE_C;
            default -> algorithm = com.example.magrathea.objectstore.domain.valueobject.EncryptionAlgorithm.AES256;
        }

        EncryptionKeyReference keyRef = request.keyReference()
            .map(kr -> EncryptionKeyReference.of(kr.keyId()))
            .orElse(null);

        EncryptionContext context = EncryptionContext.of(Map.of());

        return EncryptionDescriptor.of(algorithm, keyRef, context);
    }

    /**
     * Translates an Object Store ChecksumAlgorithm to a Storage Engine ChecksumAlgorithm.
     * Both contexts define a ChecksumAlgorithm enum with potentially different values.
     */
    public com.example.magrathea.storageengine.domain.valueobject.ChecksumAlgorithm translateChecksumAlgorithm(
            com.example.magrathea.objectstore.domain.valueobject.ChecksumAlgorithm algorithm) {
        // Map common algorithms
        switch (algorithm) {
            case SHA256:
            case SHA512:
                return com.example.magrathea.storageengine.domain.valueobject.ChecksumAlgorithm.SHA256;
            case CRC32C:
                return com.example.magrathea.storageengine.domain.valueobject.ChecksumAlgorithm.CRC32C;
            default:
                return com.example.magrathea.storageengine.domain.valueobject.ChecksumAlgorithm.SHA256;
        }
    }

    /**
     * Translates a Storage Engine DeclaredChecksum back to a ChecksumDescriptor.
     */
    public ChecksumDescriptor translateChecksumBack(DeclaredChecksum declared) {
        com.example.magrathea.objectstore.domain.valueobject.ChecksumAlgorithm algorithm;
        switch (declared.algorithm()) {
            case SHA256 -> algorithm = com.example.magrathea.objectstore.domain.valueobject.ChecksumAlgorithm.SHA256;
            case CRC32C -> algorithm = com.example.magrathea.objectstore.domain.valueobject.ChecksumAlgorithm.CRC32C;
            case BLAKE2 -> algorithm = com.example.magrathea.objectstore.domain.valueobject.ChecksumAlgorithm.SHA256;
            default -> algorithm = com.example.magrathea.objectstore.domain.valueobject.ChecksumAlgorithm.SHA256;
        }
        return ChecksumDescriptor.of(algorithm, declared.value());
    }

    /**
     * Builds an ObjectChecksum from the first chunk's final checksum in a manifest.
     */
    private ObjectChecksum buildChecksumFromManifest(ObjectManifest manifest) {
        if (manifest.chunks().isEmpty()) {
            return null;
        }
        var firstChunk = manifest.chunks().get(0);
        var finalChecksum = firstChunk.finalChecksum();

        com.example.magrathea.objectstore.domain.valueobject.ChecksumAlgorithm algorithm;
        switch (finalChecksum.algorithm()) {
            case SHA256 -> algorithm = com.example.magrathea.objectstore.domain.valueobject.ChecksumAlgorithm.SHA256;
            case CRC32C -> algorithm = com.example.magrathea.objectstore.domain.valueobject.ChecksumAlgorithm.CRC32C;
            case BLAKE2 -> algorithm = com.example.magrathea.objectstore.domain.valueobject.ChecksumAlgorithm.SHA256;
            default -> algorithm = com.example.magrathea.objectstore.domain.valueobject.ChecksumAlgorithm.SHA256;
        }

        var checksumValue = new ChecksumValue(
            algorithm,
            finalChecksum.value());

        return ObjectChecksum.of(
            java.util.Set.of(checksumValue),
            null);
    }
}
