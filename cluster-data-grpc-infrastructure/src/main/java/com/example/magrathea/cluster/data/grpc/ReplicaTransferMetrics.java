package com.example.magrathea.cluster.data.grpc;

import java.util.concurrent.atomic.AtomicInteger;

/** Small lifecycle-owned observation surface for bounded-demand acceptance evidence. */
public final class ReplicaTransferMetrics {
    private final AtomicInteger inboundOutstanding = new AtomicInteger();
    private final AtomicInteger maxInboundOutstanding = new AtomicInteger();
    private final AtomicInteger maxPayloadFrame = new AtomicInteger();
    private final AtomicInteger readyGatedFrames = new AtomicInteger();
    void requested() { int n=inboundOutstanding.incrementAndGet(); maxInboundOutstanding.accumulateAndGet(n, Math::max); }
    void accepted() { inboundOutstanding.updateAndGet(value -> Math.max(0, value - 1)); }
    void closed() { inboundOutstanding.updateAndGet(value -> Math.max(0, value - 1)); }
    void frame(int bytes) { maxPayloadFrame.accumulateAndGet(bytes, Math::max); }
    void readyFrame() { readyGatedFrames.incrementAndGet(); }
    public int maximumInboundOutstanding() { return maxInboundOutstanding.get(); }
    public int maximumPayloadFrameBytes() { return maxPayloadFrame.get(); }
    public int readyGatedFrames() { return readyGatedFrames.get(); }
}
