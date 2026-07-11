package com.example.magrathea.s3api.adapter.web;

import org.springframework.core.io.buffer.DataBuffer;
import org.springframework.core.io.buffer.DataBufferUtils;
import org.springframework.core.io.buffer.DefaultDataBufferFactory;
import reactor.core.publisher.Flux;

import java.util.Arrays;
import java.util.concurrent.atomic.AtomicLong;

/**
 * Shared finite-demand boundary for S3 request and response body streams.
 *
 * <p>This adapter safeguard limits upstream DataBuffer demand while preserving downstream
 * backpressure. It is intentionally smaller in scope than the staged storage pipeline: it
 * does not provide stage ordering, persistence windows, or lifecycle instrumentation.</p>
 */
public final class S3StreamingBody {

    private static final int DEMAND_WINDOW = 4;
    private static final int UPSTREAM_REQUEST_BATCH = DEMAND_WINDOW - 1;

    private S3StreamingBody() {
    }

    public static int demandWindow() {
        return DEMAND_WINDOW;
    }

    public static Flux<DataBuffer> bounded(Flux<DataBuffer> content) {
        // Reserve one slot for the buffer actively crossing the adapter/consumer boundary.
        // Three queued requests plus that active buffer stay within the four-buffer ceiling.
        return content.limitRate(UPSTREAM_REQUEST_BATCH, 1);
    }

    public static Flux<DataBuffer> sliceRange(
            Flux<DataBuffer> content, long start, long endInclusive) {
        var position = new AtomicLong(0);
        return bounded(content).handle((buffer, sink) -> {
            long bufferStart = position.get();
            int readable = buffer.readableByteCount();
            long bufferEndExclusive = bufferStart + readable;
            try {
                if (bufferStart > endInclusive) {
                    sink.complete();
                    return;
                }
                if (bufferEndExclusive > start && bufferStart <= endInclusive) {
                    byte[] bufferBytes = new byte[readable];
                    buffer.read(bufferBytes);
                    int sliceStart = (int) Math.max(0, start - bufferStart);
                    int sliceEndExclusive = (int) Math.min(readable, endInclusive - bufferStart + 1);
                    byte[] rangeSlice = Arrays.copyOfRange(bufferBytes, sliceStart, sliceEndExclusive);
                    sink.next(DefaultDataBufferFactory.sharedInstance.wrap(rangeSlice));
                }
                if (bufferEndExclusive > endInclusive) {
                    sink.complete();
                }
            } finally {
                position.addAndGet(readable);
                DataBufferUtils.release(buffer);
            }
        });
    }
}
