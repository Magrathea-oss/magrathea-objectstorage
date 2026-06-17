package com.example.magrathea.storageengine.infrastructure.filesystem.config;

import com.example.magrathea.storageengine.application.pipeline.CompressionStep;
import com.example.magrathea.storageengine.application.pipeline.DeduplicationStep;
import com.example.magrathea.storageengine.application.pipeline.EncryptionStep;
import com.example.magrathea.storageengine.application.pipeline.ErasureCodingStep;
import com.example.magrathea.storageengine.application.pipeline.StorePort;
import com.example.magrathea.storageengine.infrastructure.filesystem.FileSystemStorageCluster;
import com.example.magrathea.storageengine.infrastructure.filesystem.FileSystemWriteFaultInjector;
import com.example.magrathea.storageengine.infrastructure.pipeline.DataProcessingPipelineFactory;
import com.example.magrathea.storageengine.infrastructure.pipeline.DataProcessingSpecBuilder;
import com.example.magrathea.storageengine.infrastructure.pipeline.FileSystemStorePort;
import com.example.magrathea.storageengine.infrastructure.pipeline.NoOpCompressionStep;
import com.example.magrathea.storageengine.application.port.ContentAddressIndex;
import com.example.magrathea.storageengine.infrastructure.pipeline.FixedWindowDedupStep;
import com.example.magrathea.storageengine.infrastructure.pipeline.NoOpDeduplicationStep;
import com.example.magrathea.storageengine.infrastructure.pipeline.NoOpEncryptionStep;
import com.example.magrathea.storageengine.infrastructure.pipeline.NoOpErasureCodingStep;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.nio.file.Path;

/**
 * Spring configuration for the new DataProcessingPipeline infrastructure.
 * All step implementations are NoOp (pass-through) in this phase.
 *
 * FixedWindowDedupStep is the real dedup implementation.
 * TODO: Replace remaining NoOp steps with real implementations (Zstd, AES-GCM, Reed-Solomon).
 * TODO (criticality 3): ECStripeUnit and PartUnit storage not yet implemented.
 * TODO (criticality 4): Cross-cutting cleanup on mid-pipeline failure not yet implemented.
 */
@Configuration
public class StorageEnginePipelineConfig {

    @Bean
    public DeduplicationStep deduplicationStep(ObjectProvider<ContentAddressIndex> contentAddressIndexProvider) {
        ContentAddressIndex index = contentAddressIndexProvider.getIfAvailable();
        if (index != null) {
            return new FixedWindowDedupStep(index);
        }
        return new NoOpDeduplicationStep();
    }

    @Bean
    public CompressionStep compressionStep() {
        return new NoOpCompressionStep();
    }

    @Bean
    public EncryptionStep encryptionStep() {
        return new NoOpEncryptionStep();
    }

    @Bean
    public ErasureCodingStep erasureCodingStep() {
        return new NoOpErasureCodingStep();
    }

    @Bean
    public StorePort storePort(
            @Value("${storage.engine.filesystem.root:target/storage-engine}") String root,
            ObjectProvider<FileSystemStorageCluster> clusterProvider) {
        // Both FileUnit (non-dedup streaming path) and ChunkUnit (dedup path) are stored
        // in the same chunks directory so that FileSystemStorageNode.read(ChunkId) can
        // locate them regardless of which write path was used.
        Path chunksDir = Path.of(root).resolve("nodes/node-001/chunks");
        // Reuse the cluster's fault injector (if a cluster bean is available) so that
        // filesystem-reliability tests that enable fault injection see the same behaviour
        // in the new streaming pipeline path as in the old ChunkStorePort path.
        FileSystemStorageCluster cluster = clusterProvider.getIfAvailable();
        FileSystemWriteFaultInjector injector = cluster != null
                ? cluster.faultInjector()
                : FileSystemWriteFaultInjector.disabled();
        return new FileSystemStorePort(chunksDir, chunksDir, injector);
    }

    @Bean
    public DataProcessingSpecBuilder dataProcessingSpecBuilder() {
        return new DataProcessingSpecBuilder();
    }

    @Bean
    public DataProcessingPipelineFactory dataProcessingPipelineFactory(
            DeduplicationStep dedup,
            CompressionStep compress,
            EncryptionStep encrypt,
            ErasureCodingStep ec,
            StorePort store) {
        return new DataProcessingPipelineFactory(dedup, compress, encrypt, ec, store);
    }
}
