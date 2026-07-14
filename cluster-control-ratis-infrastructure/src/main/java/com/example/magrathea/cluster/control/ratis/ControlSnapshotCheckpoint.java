package com.example.magrathea.cluster.control.ratis;

import java.io.IOException;
import java.nio.file.Path;

/**
 * Focused checkpoint after a versioned control snapshot is written but before atomic installation.
 * Production constructors use {@link #open()} and therefore never pause or fail snapshot creation.
 */
@FunctionalInterface
public interface ControlSnapshotCheckpoint {
    void versionedStateWritten(Path temporarySnapshot) throws IOException;

    static ControlSnapshotCheckpoint open() {
        return ignored -> { };
    }
}
