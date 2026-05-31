package com.example.magrathea.storageengine.infrastructure.filesystem;

import com.example.magrathea.storageengine.application.port.DataTransformPort;
import com.example.magrathea.storageengine.application.port.ECOutcome;
import com.example.magrathea.storageengine.domain.valueobject.CompressionConfig;
import com.example.magrathea.storageengine.domain.valueobject.EncryptionConfig;
import com.example.magrathea.storageengine.domain.valueobject.ErasureCodingConfig;
import com.example.magrathea.storageengine.domain.valueobject.NodeId;
import reactor.core.publisher.Mono;

import java.util.List;

/**
 * Composite DataTransformPort implementation that delegates to individual
 * transformation adapters.
 */
public class FileSystemDataTransformPort implements DataTransformPort {

    private final ZstdCompressionAdapter compressionAdapter;
    private final AesGcmEncryptionAdapter encryptionAdapter;
    private final ReedSolomonECAdapter ecAdapter;
    private final SimpleReplicationAdapter replicationAdapter;

    public FileSystemDataTransformPort(
            ZstdCompressionAdapter compressionAdapter,
            AesGcmEncryptionAdapter encryptionAdapter,
            ReedSolomonECAdapter ecAdapter,
            SimpleReplicationAdapter replicationAdapter) {
        this.compressionAdapter = java.util.Objects.requireNonNull(
                compressionAdapter, "compressionAdapter must not be null");
        this.encryptionAdapter = java.util.Objects.requireNonNull(
                encryptionAdapter, "encryptionAdapter must not be null");
        this.ecAdapter = java.util.Objects.requireNonNull(
                ecAdapter, "ecAdapter must not be null");
        this.replicationAdapter = java.util.Objects.requireNonNull(
                replicationAdapter, "replicationAdapter must not be null");
    }

    @Override
    public byte[] compress(byte[] data, CompressionConfig config) {
        return compressionAdapter.compress(data, config);
    }

    @Override
    public byte[] encrypt(byte[] data, EncryptionConfig config) {
        return encryptionAdapter.encrypt(data, config);
    }

    @Override
    public Mono<ECOutcome> erasureEncode(byte[] data, ErasureCodingConfig config) {
        return ecAdapter.erasureEncode(data, config);
    }

    @Override
    public Mono<List<NodeId>> replicate(byte[] data, int factor) {
        return replicationAdapter.replicate(data, factor);
    }
}
