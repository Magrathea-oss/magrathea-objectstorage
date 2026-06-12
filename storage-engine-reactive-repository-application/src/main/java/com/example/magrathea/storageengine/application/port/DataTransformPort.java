package com.example.magrathea.storageengine.application.port;

import com.example.magrathea.storageengine.domain.valueobject.CompressionConfig;
import com.example.magrathea.storageengine.domain.valueobject.EncryptionConfig;
import com.example.magrathea.storageengine.domain.valueobject.ErasureCodingConfig;
import com.example.magrathea.storageengine.domain.valueobject.NodeId;
import reactor.core.publisher.Mono;

import java.util.List;

/**
 * Application port — data transformation operations (compress, encrypt, EC, replicate).
 * Compression and encryption are synchronous (CPU-bound byte[] operations).
 * Erasure encoding and replication may involve I/O (node distribution).
 */
public interface DataTransformPort {
    byte[] compress(byte[] data, CompressionConfig config);
    byte[] encrypt(byte[] data, EncryptionConfig config);
    Mono<ECOutcome> erasureEncode(byte[] data, ErasureCodingConfig config);
    Mono<List<NodeId>> replicate(byte[] data, int factor);
}
