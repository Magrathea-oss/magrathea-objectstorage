package com.example.magrathea.storageengine.infrastructure.filesystem;

/**
 * Test-controllable interruption used by filesystem write fault injection.
 *
 * <p>Production defaults never throw this exception. When enabled through a
 * profile/property-controlled or test-supplied {@link FileSystemWriteFaultInjector},
 * it simulates a process interruption after temporary bytes have been written but
 * before the temporary artifact is atomically renamed to its committed path.</p>
 */
public final class FileSystemWriteInterruptedException extends RuntimeException {

    private final boolean preserveTemporaryArtifacts;

    private FileSystemWriteInterruptedException(String message, boolean preserveTemporaryArtifacts) {
        super(message);
        this.preserveTemporaryArtifacts = preserveTemporaryArtifacts;
    }

    public static FileSystemWriteInterruptedException preservingTemporaryArtifacts(String message) {
        return new FileSystemWriteInterruptedException(message, true);
    }

    public static FileSystemWriteInterruptedException cleaningTemporaryArtifacts(String message) {
        return new FileSystemWriteInterruptedException(message, false);
    }

    public boolean preserveTemporaryArtifacts() {
        return preserveTemporaryArtifacts;
    }
}
