package com.example.magrathea.storageengine.application.service;

import com.example.magrathea.storageengine.domain.valueobject.ChunkId;
import org.springframework.core.io.buffer.DataBuffer;
import org.springframework.core.io.buffer.DataBufferUtils;
import reactor.core.publisher.Flux;

import java.io.ByteArrayOutputStream;

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
     * Emits each full chunk as it accumulates — no full-object buffering (REQ-UPLOAD-003).
     */
    public Flux<ApplicationChunkPayload> chunk(Flux<DataBuffer> dataBuffers) {
        return Flux.create(sink -> {
            ByteArrayOutputStream current = new ByteArrayOutputStream(chunkSize);
            dataBuffers.subscribe(
                buffer -> {
                    try {
                        byte[] bytes = new byte[buffer.readableByteCount()];
                        buffer.read(bytes);
                        DataBufferUtils.release(buffer);
                        int offset = 0;
                        while (offset < bytes.length) {
                            int space = chunkSize - current.size();
                            if (space <= 0) {
                                sink.next(new ApplicationChunkPayload(
                                    ChunkId.generate(), current.toByteArray()));
                                current.reset();
                                space = chunkSize;
                            }
                            int toCopy = Math.min(space, bytes.length - offset);
                            current.write(bytes, offset, toCopy);
                            offset += toCopy;
                        }
                    } catch (Exception e) {
                        sink.error(e);
                    }
                },
                sink::error,
                () -> {
                    if (current.size() > 0) {
                        sink.next(new ApplicationChunkPayload(
                            ChunkId.generate(), current.toByteArray()));
                    }
                    sink.complete();
                }
            );
        });
    }
}
