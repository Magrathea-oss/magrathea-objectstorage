package com.example.magrathea.storageengine.application.pipeline;

import com.example.magrathea.storageengine.domain.aggregate.StoredObject;
import com.example.magrathea.storageengine.domain.valueobject.BucketRef;
import com.example.magrathea.storageengine.domain.valueobject.ChunkPersistenceTrace;
import com.example.magrathea.storageengine.domain.valueobject.StorageArtifactReferenceDescriptor;
import com.example.magrathea.storageengine.domain.valueobject.CompleteUploadCommand;
import com.example.magrathea.storageengine.domain.valueobject.EffectiveStoragePolicy;
import com.example.magrathea.storageengine.domain.valueobject.ManifestId;
import com.example.magrathea.storageengine.domain.valueobject.ObjectId;
import com.example.magrathea.storageengine.domain.valueobject.ObjectManifest;
import com.example.magrathea.storageengine.domain.valueobject.PersistencePlan;
import com.example.magrathea.storageengine.domain.valueobject.UploadCompletionTrace;
import com.example.magrathea.storageengine.domain.valueobject.VersionId;
import com.example.magrathea.storageengine.domain.valueobject.VirtualDevice;
import org.springframework.core.io.buffer.DataBuffer;
import reactor.core.publisher.Flux;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;

public record StorageContext(
        StorageOperation operation,
        String correlationId,
        Optional<CompleteUploadCommand> command,
        Optional<Flux<DataBuffer>> uploadData,
        Optional<ManifestId> requestedManifestId,
        Optional<UploadCompletionTrace> uploadTrace,
        Optional<EffectiveStoragePolicy> effectivePolicy,
        Optional<VirtualDevice> device,
        Optional<PersistencePlan> plan,
        Optional<Integer> chunkSizeBytes,
        List<ChunkPersistenceTrace> chunkTraces,
        List<StorageArtifactReferenceDescriptor> chunkDescriptors,
        Optional<ManifestId> manifestId,
        Optional<ObjectId> objectId,
        Optional<VersionId> versionId,
        Optional<ObjectManifest> manifest,
        Optional<StoredObject> storedObject,
        Optional<Flux<byte[]>> responseContent,
        Map<String, String> stageDecisions,
        List<StorageCleanupHandle> cleanupHandles) {

    public StorageContext {
        Objects.requireNonNull(operation, "operation must not be null");
        Objects.requireNonNull(correlationId, "correlationId must not be null");
        Objects.requireNonNull(command, "command must not be null");
        Objects.requireNonNull(uploadData, "uploadData must not be null");
        Objects.requireNonNull(requestedManifestId, "requestedManifestId must not be null");
        Objects.requireNonNull(uploadTrace, "uploadTrace must not be null");
        Objects.requireNonNull(effectivePolicy, "effectivePolicy must not be null");
        Objects.requireNonNull(device, "device must not be null");
        Objects.requireNonNull(plan, "plan must not be null");
        Objects.requireNonNull(chunkSizeBytes, "chunkSizeBytes must not be null");
        chunkTraces = List.copyOf(Objects.requireNonNull(chunkTraces, "chunkTraces must not be null"));
        chunkDescriptors = List.copyOf(Objects.requireNonNull(chunkDescriptors, "chunkDescriptors must not be null"));
        Objects.requireNonNull(manifestId, "manifestId must not be null");
        Objects.requireNonNull(objectId, "objectId must not be null");
        Objects.requireNonNull(versionId, "versionId must not be null");
        Objects.requireNonNull(manifest, "manifest must not be null");
        Objects.requireNonNull(storedObject, "storedObject must not be null");
        Objects.requireNonNull(responseContent, "responseContent must not be null");
        stageDecisions = Map.copyOf(Objects.requireNonNull(stageDecisions, "stageDecisions must not be null"));
        cleanupHandles = List.copyOf(Objects.requireNonNull(cleanupHandles, "cleanupHandles must not be null"));
    }

    public static StorageContext write(CompleteUploadCommand command, Flux<DataBuffer> uploadData) {
        return empty(StorageOperation.WRITE)
                .withCommand(command)
                .withUploadData(uploadData);
    }

    public static StorageContext read(ManifestId manifestId) {
        return empty(StorageOperation.READ)
                .withRequestedManifestId(manifestId)
                .withManifestId(manifestId);
    }

    private static StorageContext empty(StorageOperation operation) {
        return new StorageContext(
                operation,
                UUID.randomUUID().toString(),
                Optional.empty(),
                Optional.empty(),
                Optional.empty(),
                Optional.empty(),
                Optional.empty(),
                Optional.empty(),
                Optional.empty(),
                Optional.empty(),
                List.of(),
                List.of(),
                Optional.empty(),
                Optional.empty(),
                Optional.empty(),
                Optional.empty(),
                Optional.empty(),
                Optional.empty(),
                Map.of(),
                List.of());
    }

    public Optional<String> bucketName() {
        if (command.isPresent()) {
            BucketRef bucket = command.get().context().bucket();
            return Optional.of(bucket.bucketName());
        }
        return objectId.map(id -> splitObjectId(id.value())[0]);
    }

    public Optional<String> objectKey() {
        if (command.isPresent()) {
            return Optional.of(command.get().context().objectKey().key());
        }
        return objectId.map(id -> splitObjectId(id.value())[1]);
    }

    public StorageContext withCommand(CompleteUploadCommand value) {
        return copy(Optional.of(value), uploadData, requestedManifestId, uploadTrace, effectivePolicy, device, plan,
                chunkSizeBytes, chunkTraces, chunkDescriptors, manifestId, objectId, versionId,
                manifest, storedObject, responseContent, stageDecisions, cleanupHandles);
    }

    public StorageContext withUploadData(Flux<DataBuffer> value) {
        return copy(command, Optional.of(value), requestedManifestId, uploadTrace, effectivePolicy, device, plan,
                chunkSizeBytes, chunkTraces, chunkDescriptors, manifestId, objectId, versionId,
                manifest, storedObject, responseContent, stageDecisions, cleanupHandles);
    }

    public StorageContext withRequestedManifestId(ManifestId value) {
        return copy(command, uploadData, Optional.of(value), uploadTrace, effectivePolicy, device, plan,
                chunkSizeBytes, chunkTraces, chunkDescriptors, manifestId, objectId, versionId,
                manifest, storedObject, responseContent, stageDecisions, cleanupHandles);
    }

    public StorageContext withManifestId(ManifestId value) {
        return copy(command, uploadData, requestedManifestId, uploadTrace, effectivePolicy, device, plan,
                chunkSizeBytes, chunkTraces, chunkDescriptors, Optional.of(value), objectId, versionId,
                manifest, storedObject, responseContent, stageDecisions, cleanupHandles);
    }

    public StorageContext withUploadTrace(UploadCompletionTrace value) {
        return copy(command, uploadData, requestedManifestId, Optional.of(value), effectivePolicy, device, plan,
                chunkSizeBytes, chunkTraces, chunkDescriptors, manifestId, objectId, versionId,
                manifest, storedObject, responseContent, stageDecisions, cleanupHandles);
    }

    public StorageContext withPolicyDecision(EffectiveStoragePolicy policy, VirtualDevice resolvedDevice,
                                             PersistencePlan persistencePlan, int resolvedChunkSizeBytes) {
        return copy(command, uploadData, requestedManifestId, uploadTrace, Optional.of(policy), Optional.of(resolvedDevice),
                Optional.of(persistencePlan), Optional.of(resolvedChunkSizeBytes), chunkTraces,
                chunkDescriptors, manifestId, objectId, versionId, manifest, storedObject, responseContent,
                stageDecisions, cleanupHandles);
    }

    public StorageContext withChunkTraces(List<ChunkPersistenceTrace> traces) {
        return copy(command, uploadData, requestedManifestId, uploadTrace, effectivePolicy, device, plan,
                chunkSizeBytes, traces, chunkDescriptors, manifestId, objectId, versionId,
                manifest, storedObject, responseContent, stageDecisions, cleanupHandles);
    }

    public StorageContext withChunkDescriptors(List<StorageArtifactReferenceDescriptor> descriptors) {
        return copy(command, uploadData, requestedManifestId, uploadTrace, effectivePolicy, device, plan,
                chunkSizeBytes, chunkTraces, descriptors, manifestId, objectId, versionId,
                manifest, storedObject, responseContent, stageDecisions, cleanupHandles);
    }

    public StorageContext withManifestIdentity(ManifestId newManifestId, ObjectId newObjectId, VersionId newVersionId) {
        return copy(command, uploadData, requestedManifestId, uploadTrace, effectivePolicy, device, plan,
                chunkSizeBytes, chunkTraces, chunkDescriptors, Optional.of(newManifestId),
                Optional.of(newObjectId), Optional.of(newVersionId), manifest, storedObject, responseContent,
                stageDecisions, cleanupHandles);
    }

    public StorageContext withManifest(ObjectManifest value) {
        return copy(command, uploadData, requestedManifestId, uploadTrace, effectivePolicy, device, plan,
                chunkSizeBytes, chunkTraces, chunkDescriptors, Optional.of(value.manifestId()),
                Optional.of(value.objectId()), Optional.of(value.versionId()), Optional.of(value), storedObject,
                responseContent, stageDecisions, cleanupHandles);
    }

    public StorageContext withStoredObject(StoredObject value) {
        return copy(command, uploadData, requestedManifestId, uploadTrace, effectivePolicy, device, plan,
                chunkSizeBytes, chunkTraces, chunkDescriptors, manifestId, objectId, versionId,
                manifest, Optional.of(value), responseContent, stageDecisions, cleanupHandles);
    }

    public StorageContext withResponseContent(Flux<byte[]> value) {
        return copy(command, uploadData, requestedManifestId, uploadTrace, effectivePolicy, device, plan,
                chunkSizeBytes, chunkTraces, chunkDescriptors, manifestId, objectId, versionId,
                manifest, storedObject, Optional.of(value), stageDecisions, cleanupHandles);
    }

    public StorageContext withStageDecision(String key, String value) {
        LinkedHashMap<String, String> updated = new LinkedHashMap<>(stageDecisions);
        updated.put(key, value);
        return copy(command, uploadData, requestedManifestId, uploadTrace, effectivePolicy, device, plan,
                chunkSizeBytes, chunkTraces, chunkDescriptors, manifestId, objectId, versionId,
                manifest, storedObject, responseContent, updated, cleanupHandles);
    }

    public StorageContext addCleanupHandle(StorageCleanupHandle handle) {
        List<StorageCleanupHandle> updated = new java.util.ArrayList<>(cleanupHandles);
        updated.add(handle);
        return copy(command, uploadData, requestedManifestId, uploadTrace, effectivePolicy, device, plan,
                chunkSizeBytes, chunkTraces, chunkDescriptors, manifestId, objectId, versionId,
                manifest, storedObject, responseContent, stageDecisions, updated);
    }

    private StorageContext copy(
            Optional<CompleteUploadCommand> newCommand,
            Optional<Flux<DataBuffer>> newUploadData,
            Optional<ManifestId> newRequestedManifestId,
            Optional<UploadCompletionTrace> newUploadTrace,
            Optional<EffectiveStoragePolicy> newEffectivePolicy,
            Optional<VirtualDevice> newDevice,
            Optional<PersistencePlan> newPlan,
            Optional<Integer> newChunkSizeBytes,
            List<ChunkPersistenceTrace> newChunkTraces,
            List<StorageArtifactReferenceDescriptor> newChunkDescriptors,
            Optional<ManifestId> newManifestId,
            Optional<ObjectId> newObjectId,
            Optional<VersionId> newVersionId,
            Optional<ObjectManifest> newManifest,
            Optional<StoredObject> newStoredObject,
            Optional<Flux<byte[]>> newResponseContent,
            Map<String, String> newStageDecisions,
            List<StorageCleanupHandle> newCleanupHandles) {
        return new StorageContext(operation, correlationId, newCommand, newUploadData, newRequestedManifestId,
                newUploadTrace, newEffectivePolicy, newDevice, newPlan, newChunkSizeBytes,
                newChunkTraces, newChunkDescriptors, newManifestId, newObjectId, newVersionId, newManifest,
                newStoredObject, newResponseContent, newStageDecisions, newCleanupHandles);
    }

    private static String[] splitObjectId(String value) {
        int separator = value.indexOf('/');
        if (separator < 0) {
            return new String[]{value, ""};
        }
        return new String[]{value.substring(0, separator), value.substring(separator + 1)};
    }
}
