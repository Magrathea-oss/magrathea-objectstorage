package com.example.magrathea.storageengine.infrastructure.filesystem.config;

import com.example.magrathea.storageengine.application.port.AlterationPort;
import com.example.magrathea.storageengine.application.port.BucketCapacityPort;
import com.example.magrathea.storageengine.application.port.ChunkStorePort;
import com.example.magrathea.storageengine.application.port.ContentAddressIndex;
import com.example.magrathea.storageengine.application.port.DataTransformPort;
import com.example.magrathea.storageengine.application.port.ObjectManifestRepository;
import com.example.magrathea.storageengine.application.pipeline.ReadPipelineObserver;
import com.example.magrathea.storageengine.application.port.StoragePolicyCatalog;
import com.example.magrathea.storageengine.application.port.StoredObjectRepository;
import com.example.magrathea.storageengine.application.pipeline.DataProcessingPipelinePort;
import com.example.magrathea.storageengine.application.pipeline.StorageEventPublisher;
import com.example.magrathea.storageengine.application.service.ReactiveStorageOrchestrator;
import com.example.magrathea.storageengine.domain.service.CompleteUploadService;
import com.example.magrathea.storageengine.domain.service.EffectivePolicyResolver;
import com.example.magrathea.storageengine.domain.service.PersistencePlanner;
import com.example.magrathea.storageengine.domain.service.VirtualDeviceResolver;
import com.example.magrathea.storageengine.infrastructure.filesystem.AesGcmEncryptionAdapter;
import com.example.magrathea.storageengine.infrastructure.filesystem.FileSystemBucketCapacityStore;
import com.example.magrathea.storageengine.infrastructure.filesystem.FileSystemChunkStorePort;
import com.example.magrathea.storageengine.infrastructure.filesystem.FileSystemDataTransformPort;
import com.example.magrathea.storageengine.infrastructure.filesystem.FileSystemStorageCluster;
import com.example.magrathea.storageengine.infrastructure.filesystem.PropertyControlledFileSystemWriteFaultInjector;
import com.example.magrathea.storageengine.infrastructure.filesystem.NoOpAlterationPort;
import com.example.magrathea.storageengine.infrastructure.filesystem.ReedSolomonECAdapter;
import com.example.magrathea.storageengine.infrastructure.filesystem.SimpleReplicationAdapter;
import com.example.magrathea.storageengine.infrastructure.filesystem.ZstdCompressionAdapter;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;

import java.nio.file.Path;

/**
 * Filesystem-backed Storage Engine runtime beans.
 */
@Configuration
@Profile("storage-engine")
public class StorageEngineFilesystemConfig {

    @Value("${storage.engine.chunk-size-bytes:65536}")
    private int defaultChunkSizeBytes;

    @Bean
    public FileSystemStorageCluster fileSystemStorageCluster(
            @Value("${storage.engine.filesystem.root:}") String root,
            @Value("${storage.engine.filesystem.node-count:1}") int nodeCount,
            @Value("${storage.engine.filesystem.fault-injection.interrupt-after-chunk-temp-write:false}")
                    boolean interruptAfterChunkTempWrite,
            @Value("${storage.engine.filesystem.fault-injection.interrupt-after-manifest-temp-write:false}")
                    boolean interruptAfterManifestTempWrite,
            @Value("${storage.engine.filesystem.fault-injection.leave-partial-temporary-artifacts:true}")
                    boolean leavePartialTemporaryArtifacts,
            @Value("${storage.engine.filesystem.fault-injection.enospc-on-chunk-write-attempt:-1}")
                    long enospcOnChunkWriteAttempt) {
        Path clusterRoot = (root == null || root.isBlank())
            ? Path.of(System.getProperty("java.io.tmpdir"), "magrathea-objectstorage", "storage-engine")
            : Path.of(root);
        return new FileSystemStorageCluster(
                clusterRoot,
                nodeCount,
                new PropertyControlledFileSystemWriteFaultInjector(
                        interruptAfterChunkTempWrite,
                        interruptAfterManifestTempWrite,
                        leavePartialTemporaryArtifacts,
                        enospcOnChunkWriteAttempt));
    }

    @Bean
    public BucketCapacityPort bucketCapacityPort(
            @Value("${storage.engine.filesystem.root:}") String root) {
        Path storageRoot = (root == null || root.isBlank())
                ? Path.of(System.getProperty("java.io.tmpdir"), "magrathea-objectstorage", "storage-engine")
                : Path.of(root);
        return new FileSystemBucketCapacityStore(storageRoot);
    }

    @Bean
    public CompleteUploadService completeUploadService() {
        return new CompleteUploadService();
    }

    @Bean
    public EffectivePolicyResolver effectivePolicyResolver() {
        return new EffectivePolicyResolver();
    }

    @Bean
    public VirtualDeviceResolver virtualDeviceResolver() {
        return new VirtualDeviceResolver();
    }

    @Bean
    public PersistencePlanner persistencePlanner() {
        return new PersistencePlanner();
    }

    @Bean
    public ZstdCompressionAdapter zstdCompressionAdapter() {
        return new ZstdCompressionAdapter();
    }

    @Bean
    public AesGcmEncryptionAdapter aesGcmEncryptionAdapter() {
        return new AesGcmEncryptionAdapter();
    }

    @Bean
    public ReedSolomonECAdapter reedSolomonECAdapter() {
        return new ReedSolomonECAdapter();
    }

    @Bean
    public SimpleReplicationAdapter simpleReplicationAdapter() {
        return new SimpleReplicationAdapter();
    }

    @Bean
    public DataTransformPort dataTransformPort(
            ZstdCompressionAdapter compressionAdapter,
            AesGcmEncryptionAdapter encryptionAdapter,
            ReedSolomonECAdapter ecAdapter,
            SimpleReplicationAdapter replicationAdapter) {
        return new FileSystemDataTransformPort(
            compressionAdapter, encryptionAdapter, ecAdapter, replicationAdapter);
    }

    @Bean
    public AlterationPort alterationPort() {
        return new NoOpAlterationPort();
    }

    @Bean
    public ContentAddressIndex contentAddressIndex(FileSystemStorageCluster cluster) {
        return cluster.addressIndex();
    }

    @Bean
    public ChunkStorePort chunkStorePort(FileSystemStorageCluster cluster) {
        return new FileSystemChunkStorePort(cluster);
    }

    @Bean
    public StoredObjectRepository storedObjectRepository(FileSystemStorageCluster cluster) {
        return cluster.storedObjectRepository();
    }

    @Bean
    public ObjectManifestRepository objectManifestRepository(FileSystemStorageCluster cluster) {
        return cluster.manifestRepository();
    }

    @Bean
    public ReactiveStorageOrchestrator reactiveStorageOrchestrator(
            CompleteUploadService completeUploadService,
            StoragePolicyCatalog storagePolicyCatalog,
            EffectivePolicyResolver effectivePolicyResolver,
            VirtualDeviceResolver virtualDeviceResolver,
            PersistencePlanner persistencePlanner,
            ContentAddressIndex contentAddressIndex,
            ChunkStorePort chunkStorePort,
            StoredObjectRepository storedObjectRepository,
            ObjectManifestRepository objectManifestRepository,
            StorageEventPublisher storageEventPublisher,
            DataProcessingPipelinePort dataPipelinePort,
            ObjectProvider<ReadPipelineObserver> readPipelineObserver) {
        return new ReactiveStorageOrchestrator(
            completeUploadService,
            storagePolicyCatalog,
            effectivePolicyResolver,
            virtualDeviceResolver,
            persistencePlanner,
            contentAddressIndex,
            chunkStorePort,
            storedObjectRepository,
            objectManifestRepository,
            defaultChunkSizeBytes,
            storageEventPublisher,
            dataPipelinePort,
            readPipelineObserver.getIfAvailable(() -> ReadPipelineObserver.NO_OP));
    }
}
