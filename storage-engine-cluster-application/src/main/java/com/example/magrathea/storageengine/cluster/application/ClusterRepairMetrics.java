package com.example.magrathea.storageengine.cluster.application;

import java.util.concurrent.atomic.AtomicLong;

/** Process-local execution metrics; consensus remains the source of repair truth. */
public final class ClusterRepairMetrics {
    private final AtomicLong ensures = new AtomicLong();
    private final AtomicLong claims = new AtomicLong();
    private final AtomicLong fetches = new AtomicLong();
    private final AtomicLong publications = new AtomicLong();
    private final AtomicLong alreadyValid = new AtomicLong();
    private final AtomicLong integrityFailures = new AtomicLong();
    private final AtomicLong obsolete = new AtomicLong();

    public void ensured() { ensures.incrementAndGet(); }
    public void claimed() { claims.incrementAndGet(); }
    public void fetched() { fetches.incrementAndGet(); }
    public void published() { publications.incrementAndGet(); }
    public void alreadyValid() { alreadyValid.incrementAndGet(); }
    public void integrityFailure() { integrityFailures.incrementAndGet(); }
    public void obsolete() { obsolete.incrementAndGet(); }

    public Snapshot snapshot() {
        return new Snapshot(ensures.get(), claims.get(), fetches.get(), publications.get(),
                alreadyValid.get(), integrityFailures.get(), obsolete.get());
    }

    public record Snapshot(long ensures, long claims, long fetches, long publications,
                           long alreadyValid, long integrityFailures, long obsolete) { }
}
