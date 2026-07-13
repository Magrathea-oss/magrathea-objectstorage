package com.example.magrathea.storageengine.cluster.application;

import java.io.IOException;
import java.nio.ByteBuffer;

/** Local immutable-artifact boundary used by data-plane infrastructure, independent of gRPC. */
public interface LocalArtifactPort {
    Source openPublished(String artifactId) throws IOException;
    boolean publishedExists(String artifactId);
    ArtifactProbe probePublished(String artifactId, long expectedLength, String expectedSha256)
            throws IOException;
    Sink beginUnpublished(TransferRequest request) throws IOException;
    RepairSink beginRepair(TransferRequest request, RepairToken token) throws IOException;
    IncomingSink beginIncoming(String operationId, String artifactId) throws IOException;

    record RepairToken(RepairJobId jobId, long claimGeneration) {
        public RepairToken {
            if (jobId == null) throw new IllegalArgumentException("repair job ID is required");
            if (claimGeneration < 1) {
                throw new IllegalArgumentException("claim generation must be positive");
            }
        }
    }

    record ArtifactProbe(Status status, long actualLength, String actualSha256) {
        public enum Status { MISSING, EXACT, INVALID }

        public ArtifactProbe {
            if (status == null) throw new IllegalArgumentException("probe status is required");
            if (actualLength < -1) throw new IllegalArgumentException("actual length is invalid");
            actualSha256 = actualSha256 == null ? "" : actualSha256;
        }

        public boolean exact() { return status == Status.EXACT; }
    }

    interface Source extends AutoCloseable {
        int read(ByteBuffer target) throws IOException;
        long length();
        @Override void close() throws IOException;
    }

    interface IncomingSink extends AutoCloseable {
        void accept(ByteBuffer bytes) throws IOException;
        IncomingArtifact publish() throws IOException;
        void abort();
        @Override void close() throws IOException;
    }

    record IncomingArtifact(PreparedArtifact artifact, String md5) {
        public IncomingArtifact {
            if (artifact == null) throw new IllegalArgumentException("prepared artifact is required");
            if (md5 == null || !md5.matches("[0-9a-f]{32}")) {
                throw new IllegalArgumentException("lowercase MD5 is required");
            }
        }
    }

    interface Sink extends AutoCloseable {
        void accept(long offset, ByteBuffer bytes) throws IOException;
        TransferResult publish() throws IOException;
        void abort();
        @Override void close() throws IOException;
    }

    /** Token-specific repair staging; verification and durable replacement are separate fences. */
    interface RepairSink extends AutoCloseable {
        void accept(long offset, ByteBuffer bytes) throws IOException;
        TransferResult verify() throws IOException;
        TransferResult publishVerified() throws IOException;
        void abort();
        @Override void close() throws IOException;
    }
}
