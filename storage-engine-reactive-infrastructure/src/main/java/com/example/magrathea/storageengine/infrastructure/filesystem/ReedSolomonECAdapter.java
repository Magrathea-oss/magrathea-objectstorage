package com.example.magrathea.storageengine.infrastructure.filesystem;

import com.example.magrathea.storageengine.application.port.ECOutcome;
import com.example.magrathea.storageengine.domain.valueobject.ErasureCodingConfig;
import com.example.magrathea.storageengine.domain.valueobject.NodeId;
import reactor.core.publisher.Mono;

import java.util.ArrayList;
import java.util.List;

/**
 * Mock Reed-Solomon erasure coding adapter.
 * <p>
 * Splits data into k shards and computes m parity shards.
 * This is a simplified mock — actual Reed-Solomon implementation would
 * use a library like Backblaze's JavaReedSolomon or similar.
 */
public class ReedSolomonECAdapter {

    /**
     * Erasure encodes the data using a mock implementation.
     * Splits data into k data shards and creates m parity shards.
     *
     * @param data   the input data
     * @param config erasure coding configuration (k data blocks, m parity blocks)
     * @return Mono of ECOutcome with data nodes, parity nodes, and encoded data
     */
    public Mono<ECOutcome> erasureEncode(byte[] data, ErasureCodingConfig config) {
        return Mono.fromCallable(() -> {
            int k = config.dataBlocks();
            int m = config.parityBlocks();

            // Calculate shard size
            int shardSize = (int) Math.ceil((double) data.length / k);
            byte[][] dataShards = new byte[k][];

            // Split data into k shards
            for (int i = 0; i < k; i++) {
                int start = i * shardSize;
                int end = Math.min(start + shardSize, data.length);
                byte[] shard = new byte[end - start];
                System.arraycopy(data, start, shard, 0, shard.length);
                dataShards[i] = shard;
            }

            // Mock parity shards (XOR-based parity)
            byte[][] parityShards = new byte[m][];
            for (int j = 0; j < m; j++) {
                byte[] parity = new byte[shardSize];
                // Simple mock: XOR all data shards for first parity, use data shard pattern for others
                for (int i = 0; i < k; i++) {
                    for (int b = 0; b < Math.min(dataShards[i].length, shardSize); b++) {
                        parity[b] ^= dataShards[i][b];
                    }
                }
                // Rotate parity for subsequent shards (mock)
                if (j > 0) {
                    for (int b = 0; b < shardSize; b++) {
                        parity[b] = (byte) (parity[b] ^ (j + 1));
                    }
                }
                parityShards[j] = parity;
            }

            // Build node lists
            List<NodeId> dataNodes = new ArrayList<>();
            for (int i = 0; i < k; i++) {
                dataNodes.add(NodeId.of("ec-data-node-" + (i + 1)));
            }
            List<NodeId> parityNodes = new ArrayList<>();
            for (int j = 0; j < m; j++) {
                parityNodes.add(NodeId.of("ec-parity-node-" + (j + 1)));
            }

            // Combine all shards into encoded data
            byte[] encodedData = new byte[(k + m) * shardSize];
            for (int i = 0; i < k; i++) {
                System.arraycopy(dataShards[i], 0, encodedData, i * shardSize, dataShards[i].length);
            }
            for (int j = 0; j < m; j++) {
                System.arraycopy(parityShards[j], 0, encodedData, (k + j) * shardSize, parityShards[j].length);
            }

            return new ECOutcome(dataNodes, parityNodes, encodedData);
        });
    }
}
