package com.example.magrathea.cluster.control.ratis;

import com.example.magrathea.storageengine.cluster.application.ControlPlaneException;
import com.example.magrathea.storageengine.cluster.application.NodeIdentity;

import java.io.IOException;
import java.nio.channels.FileChannel;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.nio.file.StandardOpenOption;

/** Atomically creates a node UUID once and rejects later bootstrap attempts to rewrite it. */
public final class PersistedNodeIdentityStore {
    private static final String FILE_NAME = "node.uuid";

    public NodeIdentity initializeOrRecover(Path identityRoot, NodeIdentity declared) {
        try {
            Files.createDirectories(identityRoot);
            Path identityFile = identityRoot.resolve(FILE_NAME);
            if (Files.exists(identityFile)) return verify(identityFile, declared);
            Path temporary = Files.createTempFile(identityRoot, ".node-", ".tmp");
            try {
                Files.writeString(temporary, declared + "\n", StandardCharsets.US_ASCII,
                        StandardOpenOption.TRUNCATE_EXISTING, StandardOpenOption.WRITE);
                try (FileChannel channel = FileChannel.open(temporary, StandardOpenOption.WRITE)) {
                    channel.force(true);
                }
                try {
                    Files.move(temporary, identityFile, StandardCopyOption.ATOMIC_MOVE);
                } catch (java.nio.file.FileAlreadyExistsException conflict) {
                    return verify(identityFile, declared);
                }
                forceDirectory(identityRoot);
                return declared;
            } finally {
                Files.deleteIfExists(temporary);
            }
        } catch (IOException failure) {
            throw new ControlPlaneException(ControlPlaneException.Code.INTERNAL_FAILURE,
                    "cannot persist node identity beneath " + identityRoot, failure);
        }
    }

    private static NodeIdentity verify(Path file, NodeIdentity declared) throws IOException {
        NodeIdentity persisted = NodeIdentity.parse(Files.readString(file, StandardCharsets.US_ASCII).trim());
        if (!persisted.equals(declared)) {
            throw new ControlPlaneException(ControlPlaneException.Code.IDENTITY_CONFLICT,
                    "persisted identity " + persisted + " does not match declared identity " + declared);
        }
        return persisted;
    }

    private static void forceDirectory(Path directory) {
        try (FileChannel channel = FileChannel.open(directory, StandardOpenOption.READ)) {
            channel.force(true);
        } catch (IOException ignoredOnFileSystemsWithoutDirectoryFsync) {
            // The file itself was forced and atomically renamed; directory fsync is best effort across providers.
        }
    }
}
