package com.example.magrathea.storageengine.infrastructure.filesystem.config;

import com.example.magrathea.storageengine.application.pipeline.BoundedEcReconstructionPort;
import com.example.magrathea.storageengine.application.pipeline.CompressionStep;
import com.example.magrathea.storageengine.application.pipeline.EncryptionStep;
import com.example.magrathea.storageengine.application.pipeline.ErasureCodingStep;
import com.example.magrathea.storageengine.application.pipeline.StorePort;
import com.example.magrathea.storageengine.application.port.ContentAddressIndex;
import com.example.magrathea.storageengine.domain.valueobject.NodeId;
import com.example.magrathea.storageengine.infrastructure.filesystem.FileSystemStorageCluster;
import com.example.magrathea.storageengine.infrastructure.filesystem.FileSystemWriteFaultInjector;
import com.example.magrathea.storageengine.infrastructure.pipeline.BoundedEcReconstructionAdapter;
import com.example.magrathea.storageengine.infrastructure.pipeline.DataProcessingPipelineFactory;
import com.example.magrathea.storageengine.infrastructure.pipeline.DataProcessingSpecBuilder;
import com.example.magrathea.storageengine.infrastructure.pipeline.FileSystemStorePort;
import com.example.magrathea.storageengine.infrastructure.pipeline.NoOpCompressionStep;
import com.example.magrathea.storageengine.infrastructure.pipeline.NoOpEncryptionStep;
import com.example.magrathea.storageengine.infrastructure.pipeline.NoOpErasureCodingStep;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import reactor.core.scheduler.Scheduler;
import reactor.core.scheduler.Schedulers;

import java.nio.file.Path;

/**
 * Spring configuration for the DataProcessingPipeline infrastructure.
 * Compression and encryption remain pass-through implementations in this phase;
 * deduplication and policy-derived fixed-stripe erasure coding are physical steps.
 *
 * <p>There is intentionally NO singleton {@code DeduplicationStep} bean declared here.
 * Deduplication chunk size is a per-policy value carried by
 * {@link com.example.magrathea.storageengine.domain.valueobject.DedupConfig#chunkSize()}.
 * {@link DataProcessingPipelineFactory#build(com.example.magrathea.storageengine.domain.pipeline.DataProcessingSpec)}
 * creates a fresh {@link com.example.magrathea.storageengine.infrastructure.pipeline.FixedWindowDedupStep}
 * for every pipeline build, using the spec-level chunk size, so the singleton-bean approach
 * would silently hardcode a single chunk size across all policies.
 * When {@link com.example.magrathea.storageengine.application.port.ContentAddressIndex} is absent
 * (e.g. non-storage-engine profiles or tests without a real cluster), the factory falls back to
 * {@link com.example.magrathea.storageengine.infrastructure.pipeline.NoOpDeduplicationStep} automatically.
 *
 * TODO: Replace remaining NoOp transforms with real Zstd and AES-GCM implementations.
 * TODO (criticality 3): PartUnit storage is not yet implemented.
 * TODO (criticality 4): Cross-cutting cleanup on mid-pipeline failure not yet implemented.
 */
@Configuration
public class StorageEnginePipelineConfig {

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
        Path nodeRoot = Path.of(root).resolve("nodes/node-001");
        Path wholeObjectsDir = nodeRoot.resolve("whole-objects");
        Path chunksDir = nodeRoot.resolve("chunks");
        // Reuse the cluster's fault injector (if a cluster bean is available) so that
        // filesystem-reliability tests that enable fault injection see the same behaviour
        // in the new streaming pipeline path as in the old ChunkStorePort path.
        FileSystemStorageCluster cluster = clusterProvider.getIfAvailable();
        FileSystemWriteFaultInjector injector = cluster != null
                ? cluster.faultInjector()
                : FileSystemWriteFaultInjector.disabled();
        return new FileSystemStorePort(
                wholeObjectsDir, chunksDir, injector, NodeId.of("node-001"));
    }

    /** Finite, caller-owned execution boundary for one-stripe GF(256) reconstruction. */
    @Bean(name = "boundedEcReconstructionScheduler", destroyMethod = "dispose")
    public Scheduler boundedEcReconstructionScheduler() {
        return Schedulers.newBoundedElastic(
                1, 16, "bounded-ec-reconstruction");
    }

    /** Local bounded reconstruction only; this bean has no publication dependency. */
    @Bean
    public BoundedEcReconstructionPort boundedEcReconstructionPort(
            @Qualifier("boundedEcReconstructionScheduler") Scheduler scheduler) {
        return new BoundedEcReconstructionAdapter(scheduler);
    }

    @Bean
    public DataProcessingSpecBuilder dataProcessingSpecBuilder() {
        return new DataProcessingSpecBuilder();
    }

    @Bean
    public DataProcessingPipelineFactory dataProcessingPipelineFactory(
            ObjectProvider<ContentAddressIndex> contentAddressIndexProvider,
            CompressionStep compress,
            EncryptionStep encrypt,
            ErasureCodingStep ec,
            StorePort store) {
        // Pass ContentAddressIndex so build() can create FixedWindowDedupStep with
        // the chunk size from the spec (DedupConfig.chunkSize()) rather than a hardcoded default.
        ContentAddressIndex index = contentAddressIndexProvider.getIfAvailable();
        return new DataProcessingPipelineFactory(index, compress, encrypt, ec, store);
    }
}
