package com.example.magrathea.storageengine.cluster.application;

import java.io.IOException;
import java.nio.ByteBuffer;

/** Local immutable-artifact boundary used by data-plane infrastructure, independent of gRPC. */
public interface LocalArtifactPort {
    Source openPublished(String artifactId) throws IOException;
    Sink beginUnpublished(TransferRequest request) throws IOException;
    IncomingSink beginIncoming(String operationId, String artifactId) throws IOException;

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
}
