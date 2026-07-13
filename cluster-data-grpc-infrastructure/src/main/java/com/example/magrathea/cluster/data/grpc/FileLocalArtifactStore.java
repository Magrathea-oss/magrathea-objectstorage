package com.example.magrathea.cluster.data.grpc;

import com.example.magrathea.storageengine.cluster.application.*;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.channels.FileChannel;
import java.nio.file.*;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import java.util.Locale;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicLong;

/** Filesystem adapter that publishes only verified, fsynced immutable artifacts. */
public final class FileLocalArtifactStore implements LocalArtifactPort {
    private final Path publishedRoot;
    private final Path temporaryRoot;
    private final NodeIdentity node;
    private final ConcurrentHashMap<String, AtomicLong> publishedOpens = new ConcurrentHashMap<>();

    public FileLocalArtifactStore(Path publishedRoot, Path temporaryRoot, NodeIdentity node) throws IOException {
        this.publishedRoot = publishedRoot; this.temporaryRoot = temporaryRoot; this.node = node;
        Files.createDirectories(publishedRoot); Files.createDirectories(temporaryRoot);
    }

    public Path publishedPath(String artifactId) { return publishedRoot.resolve(safe(artifactId) + ".artifact"); }
    public long temporaryFileCount() throws IOException {
        try (var files = Files.walk(temporaryRoot)) { return files.filter(Files::isRegularFile).count(); }
    }
    public long publishedOpenCount(String artifactId) {
        AtomicLong count = publishedOpens.get(safe(artifactId));
        return count == null ? 0 : count.get();
    }

    @Override public Source openPublished(String artifactId) throws IOException {
        Path path = publishedPath(artifactId);
        FileChannel channel = FileChannel.open(path, StandardOpenOption.READ);
        publishedOpens.computeIfAbsent(safe(artifactId), ignored -> new AtomicLong()).incrementAndGet();
        long length = channel.size();
        return new Source() {
            public int read(ByteBuffer target) throws IOException { return channel.read(target); }
            public long length() { return length; }
            public void close() throws IOException { channel.close(); }
        };
    }

    @Override public boolean publishedExists(String artifactId) {
        return Files.isRegularFile(publishedPath(artifactId));
    }

    @Override public ArtifactProbe probePublished(
            String artifactId, long expectedLength, String expectedSha256) throws IOException {
        Path path = publishedPath(artifactId);
        if (!Files.isRegularFile(path)) return new ArtifactProbe(ArtifactProbe.Status.MISSING, -1, "");
        long length = Files.size(path);
        String sha = HexFormat.of().formatHex(digest(path));
        boolean exact = length == expectedLength && sha.equals(expectedSha256.toLowerCase(Locale.ROOT));
        return new ArtifactProbe(exact ? ArtifactProbe.Status.EXACT : ArtifactProbe.Status.INVALID,
                length, sha);
    }

    @Override public IncomingSink beginIncoming(String operationId, String artifactId) throws IOException {
        Path destination = publishedPath(artifactId);
        if (Files.exists(destination)) {
            throw new TransferException(TransferError.ARTIFACT_CONFLICT,
                    "incoming immutable artifact already exists");
        }
        Path temporary = temporaryRoot.resolve(
                safe(operationId) + "-" + UUID.randomUUID() + ".incoming");
        FileChannel channel = FileChannel.open(temporary, StandardOpenOption.CREATE_NEW, StandardOpenOption.WRITE);
        return new IncomingStagingSink(
                operationId, artifactId, node, destination, temporary, channel);
    }

    @Override public RepairSink beginRepair(
            TransferRequest request, RepairToken token) throws IOException {
        Path tokenRoot = temporaryRoot.resolve("repair").resolve(safe(token.jobId().toString()))
                .resolve(Long.toString(token.claimGeneration()));
        Files.createDirectories(tokenRoot);
        Path temporary = tokenRoot.resolve("payload.part");
        Files.deleteIfExists(temporary);
        FileChannel channel = FileChannel.open(temporary, StandardOpenOption.CREATE_NEW,
                StandardOpenOption.WRITE);
        return new RepairStagingSink(request, node, publishedPath(request.artifactId()),
                tokenRoot, temporary, channel);
    }

    @Override public Sink beginUnpublished(TransferRequest request) throws IOException {
        Path destination = publishedPath(request.artifactId());
        if (Files.exists(destination)) {
            byte[] actual = digest(destination);
            if (Files.size(destination) == request.expectedLength() && MessageDigest.isEqual(actual, request.expectedSha256())) {
                return new ExistingSink(request, node);
            }
            throw new TransferException(TransferError.ARTIFACT_CONFLICT, "immutable artifact already differs");
        }
        Path temporary = temporaryRoot.resolve(safe(request.operationId()) + "-" + UUID.randomUUID() + ".part");
        FileChannel channel = FileChannel.open(temporary, StandardOpenOption.CREATE_NEW, StandardOpenOption.WRITE);
        return new StagingSink(request, node, destination, temporary, channel);
    }

    private static final class IncomingStagingSink implements IncomingSink {
        private final String operationId;
        private final String artifactId;
        private final NodeIdentity node;
        private final Path destination;
        private final Path temporary;
        private final FileChannel channel;
        private final MessageDigest sha256 = sha256();
        private final MessageDigest md5 = md5();
        private final AtomicBoolean terminal = new AtomicBoolean();
        private long accepted;

        IncomingStagingSink(String operationId, String artifactId, NodeIdentity node,
                            Path destination, Path temporary, FileChannel channel) {
            this.operationId = operationId;
            this.artifactId = artifactId;
            this.node = node;
            this.destination = destination;
            this.temporary = temporary;
            this.channel = channel;
        }

        @Override public synchronized void accept(ByteBuffer bytes) throws IOException {
            if (terminal.get()) throw new IOException("incoming sink terminated");
            if (bytes.remaining() > GrpcReplicaServer.FRAME_BYTES) {
                throw new TransferException(TransferError.FRAME_TOO_LARGE,
                        "incoming filesystem write exceeds 64KiB");
            }
            ByteBuffer shaView = bytes.asReadOnlyBuffer();
            ByteBuffer md5View = bytes.asReadOnlyBuffer();
            sha256.update(shaView);
            md5.update(md5View);
            int count = bytes.remaining();
            while (bytes.hasRemaining()) channel.write(bytes);
            accepted += count;
        }

        @Override public synchronized IncomingArtifact publish() throws IOException {
            if (!terminal.compareAndSet(false, true)) throw new IOException("incoming sink terminated");
            try {
                String sha = HexFormat.of().formatHex(sha256.digest());
                String etag = HexFormat.of().formatHex(md5.digest());
                channel.force(true);
                channel.close();
                Files.move(temporary, destination, StandardCopyOption.ATOMIC_MOVE);
                forceDirectory(destination.getParent());
                return new IncomingArtifact(
                        new PreparedArtifact(operationId, artifactId, node, accepted, sha), etag);
            } catch (IOException | RuntimeException failure) {
                cleanup();
                throw failure;
            }
        }

        @Override public void abort() {
            if (terminal.compareAndSet(false, true)) cleanup();
        }

        @Override public void close() {
            if (!terminal.get()) abort();
        }

        private void cleanup() {
            try { channel.close(); } catch (IOException ignored) { }
            try { Files.deleteIfExists(temporary); } catch (IOException ignored) { }
        }
    }

    private static final class RepairStagingSink implements RepairSink {
        private final TransferRequest request;
        private final NodeIdentity node;
        private final Path destination;
        private final Path tokenRoot;
        private final Path temporary;
        private final FileChannel channel;
        private final MessageDigest digest = sha256();
        private final AtomicBoolean terminal = new AtomicBoolean();
        private long accepted;
        private byte[] verifiedDigest;
        private boolean verified;

        RepairStagingSink(TransferRequest request, NodeIdentity node, Path destination,
                          Path tokenRoot, Path temporary, FileChannel channel) {
            this.request = request;
            this.node = node;
            this.destination = destination;
            this.tokenRoot = tokenRoot;
            this.temporary = temporary;
            this.channel = channel;
        }

        @Override public synchronized void accept(long offset, ByteBuffer bytes) throws IOException {
            if (terminal.get() || verified) throw new IOException("repair sink terminated");
            if (offset != accepted) {
                throw new TransferException(TransferError.OFFSET_MISMATCH,
                        "expected offset " + accepted + " but got " + offset);
            }
            int count = bytes.remaining();
            if (count > GrpcReplicaServer.FRAME_BYTES) {
                throw new TransferException(TransferError.FRAME_TOO_LARGE,
                        "repair frame exceeds 64KiB");
            }
            if (accepted + count > request.expectedLength()) {
                throw new TransferException(TransferError.LENGTH_MISMATCH,
                        "repair payload exceeds declared length");
            }
            digest.update(bytes.asReadOnlyBuffer());
            while (bytes.hasRemaining()) channel.write(bytes);
            accepted += count;
        }

        @Override public synchronized TransferResult verify() throws IOException {
            if (terminal.get()) throw new IOException("repair sink terminated");
            if (verified) return result(request, node, false);
            if (accepted != request.expectedLength()) {
                throw new TransferException(TransferError.LENGTH_MISMATCH,
                        "declared and repaired length differ");
            }
            verifiedDigest = digest.digest();
            if (!MessageDigest.isEqual(verifiedDigest, request.expectedSha256())) {
                throw new TransferException(TransferError.CHECKSUM_MISMATCH,
                        "repair SHA-256 mismatch");
            }
            channel.force(true);
            channel.close();
            verified = true;
            return result(request, node, false);
        }

        @Override public synchronized TransferResult publishVerified() throws IOException {
            if (!verified) throw new IOException("repair staging is not verified");
            if (!terminal.compareAndSet(false, true)) throw new IOException("repair sink terminated");
            try {
                Files.move(temporary, destination, StandardCopyOption.ATOMIC_MOVE,
                        StandardCopyOption.REPLACE_EXISTING);
                forceDirectoryStrict(destination.getParent());
                cleanupDirectories();
                return result(request, node, false);
            } catch (IOException | RuntimeException failure) {
                cleanup();
                throw failure;
            }
        }

        @Override public void abort() {
            if (terminal.compareAndSet(false, true)) cleanup();
        }

        @Override public void close() {
            if (!terminal.get()) abort();
        }

        private void cleanup() {
            try { channel.close(); } catch (IOException ignored) { }
            try { Files.deleteIfExists(temporary); } catch (IOException ignored) { }
            cleanupDirectories();
        }

        private void cleanupDirectories() {
            try { Files.deleteIfExists(tokenRoot); } catch (IOException ignored) { }
            try { Files.deleteIfExists(tokenRoot.getParent()); } catch (IOException ignored) { }
        }
    }

    private static final class ExistingSink implements Sink {
        private final TransferRequest request; private final NodeIdentity node; private long accepted;
        ExistingSink(TransferRequest request, NodeIdentity node) { this.request = request; this.node = node; }
        public void accept(long offset, ByteBuffer bytes) {
            if (offset != accepted || accepted + bytes.remaining() > request.expectedLength()) {
                throw new TransferException(TransferError.OFFSET_MISMATCH, "invalid idempotent retry frame");
            }
            accepted += bytes.remaining(); bytes.position(bytes.limit());
        }
        public TransferResult publish() { return result(request, node, true); }
        public void abort() { }
        public void close() { }
    }

    private static final class StagingSink implements Sink {
        private final TransferRequest request; private final NodeIdentity node; private final Path destination; private final Path temporary;
        private final FileChannel channel; private final MessageDigest digest = sha256(); private final AtomicBoolean terminal = new AtomicBoolean();
        private long accepted;
        StagingSink(TransferRequest request, NodeIdentity node, Path destination, Path temporary, FileChannel channel) {
            this.request=request; this.node=node; this.destination=destination; this.temporary=temporary; this.channel=channel;
        }
        public synchronized void accept(long offset, ByteBuffer bytes) throws IOException {
            if (terminal.get()) throw new IOException("sink terminated");
            if (offset != accepted) throw new TransferException(TransferError.OFFSET_MISMATCH, "expected offset " + accepted + " but got " + offset);
            int count = bytes.remaining();
            if (accepted + count > request.expectedLength()) throw new TransferException(TransferError.LENGTH_MISMATCH, "payload exceeds declared length");
            ByteBuffer hashView = bytes.asReadOnlyBuffer();
            digest.update(hashView);
            while (bytes.hasRemaining()) channel.write(bytes);
            accepted += count;
        }
        public synchronized TransferResult publish() throws IOException {
            if (!terminal.compareAndSet(false, true)) throw new IOException("sink terminated");
            try {
                if (accepted != request.expectedLength()) throw new TransferException(TransferError.LENGTH_MISMATCH, "declared and received length differ");
                byte[] actual = digest.digest();
                if (!MessageDigest.isEqual(actual, request.expectedSha256())) throw new TransferException(TransferError.CHECKSUM_MISMATCH, "SHA-256 mismatch");
                channel.force(true); channel.close();
                try { Files.move(temporary, destination, StandardCopyOption.ATOMIC_MOVE); }
                catch (FileAlreadyExistsException duplicate) {
                    if (Files.size(destination) != request.expectedLength() || !MessageDigest.isEqual(digest(destination), actual)) throw duplicate;
                    Files.deleteIfExists(temporary);
                }
                forceDirectory(destination.getParent());
                return result(request, node, false);
            } catch (IOException | RuntimeException failure) { cleanup(); throw failure; }
        }
        public void abort() { if (terminal.compareAndSet(false, true)) cleanup(); }
        public void close() { if (!terminal.get()) abort(); }
        private void cleanup() { try { channel.close(); } catch (IOException ignored) { } try { Files.deleteIfExists(temporary); } catch (IOException ignored) { } }
    }

    private static TransferResult result(TransferRequest r, NodeIdentity node, boolean retry) {
        return new TransferResult(r.operationId(), r.artifactId(), node, r.expectedLength(), r.expectedSha256(), r.topologyEpoch(), r.policyEpoch(), retry);
    }
    private static String safe(String value) {
        if (!value.matches("[A-Za-z0-9._-]{1,160}")) throw new IllegalArgumentException("unsafe artifact identifier");
        return value;
    }
    private static MessageDigest sha256() { try { return MessageDigest.getInstance("SHA-256"); } catch (NoSuchAlgorithmException e) { throw new AssertionError(e); } }
    private static MessageDigest md5() { try { return MessageDigest.getInstance("MD5"); } catch (NoSuchAlgorithmException e) { throw new AssertionError(e); } }
    private static byte[] digest(Path path) throws IOException {
        MessageDigest digest=sha256(); try (var in=Files.newInputStream(path)) { byte[] b=new byte[65536]; for(int n;(n=in.read(b))>=0;) digest.update(b,0,n); } return digest.digest();
    }
    private static void forceDirectory(Path directory) { try (FileChannel c=FileChannel.open(directory, StandardOpenOption.READ)) { c.force(true); } catch (IOException ignored) { } }
    private static void forceDirectoryStrict(Path directory) throws IOException {
        try (FileChannel channel = FileChannel.open(directory, StandardOpenOption.READ)) {
            channel.force(true);
        }
    }
}
