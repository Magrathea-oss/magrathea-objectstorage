package com.example.magrathea.storageengine.application.service;

import com.example.magrathea.storageengine.domain.valueobject.ChunkId;
import org.springframework.core.io.buffer.DataBuffer;
import org.springframework.core.io.buffer.DataBufferUtils;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

/**
 * Chunker — splits a Flux<DataBuffer> into a Flux<ApplicationChunkPayload>
 * based on a fixed chunk size in bytes.
 * <p>
 * Does NOT use buffer(int) (which groups DataBuffer count, not bytes).
 * Instead, accumulates bytes up to chunkSize before emitting each payload.
 */
public class Chunker {

    private final int chunkSize;

    public Chunker(int chunkSize) {
        if (chunkSize <= 0) {
            throw new IllegalArgumentException("chunkSize must be positive: " + chunkSize);
        }
        this.chunkSize = chunkSize;
    }

    /**
     * Splits the incoming DataBuffer flux into chunks of at most chunkSize bytes.
     * Each chunk gets a new ChunkId and carries the accumulated byte array.
     */
    public Flux<ApplicationChunkPayload> chunk(Flux<DataBuffer> dataBuffers) {
        return dataBuffers
                .reduce(
                        new ChunkAccumulator(chunkSize),
                        (accumulator, buffer) -> {
                            accumulator.feed(buffer);
                            return accumulator;
                        })
                .flatMapMany(accumulator -> {
                    List<ApplicationChunkPayload> payloads = new ArrayList<>();
                    for (byte[] chunk : accumulator.getChunks()) {
                        payloads.add(new ApplicationChunkPayload(ChunkId.generate(), chunk));
                    }
                    // If there's a remaining partial chunk
                    byte[] remainder = accumulator.getRemainder();
                    if (remainder.length > 0) {
                        payloads.add(new ApplicationChunkPayload(ChunkId.generate(), remainder));
                    }
                    return Flux.fromIterable(payloads);
                });
    }

    /**
     * Internal accumulator that accumulates bytes up to chunkSize before flushing.
     */
    private static class ChunkAccumulator {
        private final int chunkSize;
        private final List<byte[]> chunks = new ArrayList<>();
        private ByteArrayOutputStream partial = new ByteArrayOutputStream();

        ChunkAccumulator(int chunkSize) {
            this.chunkSize = chunkSize;
        }

        void feed(DataBuffer buffer) {
            byte[] bytes = new byte[buffer.readableByteCount()];
            buffer.read(bytes);
            org.springframework.core.io.buffer.DataBufferUtils.release(buffer);
            int offset = 0;
            while (offset < bytes.length) {
                int remaining = chunkSize - partial.size();
                if (remaining <= 0) {
                    flushPartial();
                    remaining = chunkSize;
                }
                int toCopy = Math.min(remaining, bytes.length - offset);
                partial.write(bytes, offset, toCopy);
                offset += toCopy;
            }
        }

        void flushPartial() {
            try {
                partial.close();
            } catch (IOException e) {
                throw new RuntimeException("Failed to close ByteArrayOutputStream", e);
            }
            byte[] chunk = partial.toByteArray();
            if (chunk.length > 0) {
                chunks.add(chunk);
            }
            partial = new ByteArrayOutputStream();
        }

        List<byte[]> getChunks() {
            return chunks;
        }

        byte[] getRemainder() {
            try {
                partial.close();
            } catch (IOException e) {
                throw new RuntimeException("Failed to close ByteArrayOutputStream", e);
            }
            return partial.toByteArray();
        }
    }
}
