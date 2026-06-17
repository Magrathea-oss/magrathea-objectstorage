package com.example.magrathea.storageengine.infrastructure.filesystem;

import com.example.magrathea.storageengine.domain.valueobject.ChunkId;
import com.example.magrathea.storageengine.domain.valueobject.ManifestId;
import com.example.magrathea.storageengine.domain.valueobject.NodeId;

import java.nio.file.Path;

/**
 * Internal filesystem write fault-injection hook.
 *
 * <p>The default injector is disabled and has no production effect. Tests or a
 * dedicated fault-injection profile/property may supply an implementation that throws
 * {@link FileSystemWriteInterruptedException} after temporary bytes are written and
 * before the atomic rename publishes a committed chunk or manifest.</p>
 */
public interface FileSystemWriteFaultInjector {

    default void afterChunkTempFileWritten(ChunkWriteContext context) {
        // disabled by default
    }

    default void afterManifestTempFileWritten(ManifestWriteContext context) {
        // disabled by default
    }

    static FileSystemWriteFaultInjector disabled() {
        return Disabled.INSTANCE;
    }

    record ChunkWriteContext(
            NodeId nodeId,
            ChunkId chunkId,
            Path tempFile,
            Path finalFile,
            long expectedBytes) {
    }

    record ManifestWriteContext(
            ManifestId manifestId,
            Path tempFile,
            Path finalFile,
            long expectedBytes) {
    }

    enum Disabled implements FileSystemWriteFaultInjector {
        INSTANCE
    }
}
