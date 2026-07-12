package com.example.magrathea.s3api.cucumber.support;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.time.Duration;
import java.util.List;
import java.util.concurrent.TimeUnit;

/** Streams child output without allowing an OS pipe to fill and keeps a durable validation log. */
public final class ChildProcessSupport implements AutoCloseable {
    private final Process process;
    private final Path log;
    private final Thread outputPump;

    private ChildProcessSupport(Process process, Path log) {
        this.process = process;
        this.log = log;
        this.outputPump = Thread.ofVirtual().name("child-output-" + process.pid()).start(this::pump);
    }

    public static ChildProcessSupport start(List<String> command, Path log) throws IOException {
        Files.createDirectories(log.toAbsolutePath().getParent());
        Files.deleteIfExists(log);
        Process process = new ProcessBuilder(command).redirectErrorStream(true).start();
        return new ChildProcessSupport(process, log);
    }

    public Process process() {
        return process;
    }

    public long pid() {
        return process.pid();
    }

    public String readLog() throws IOException {
        return Files.exists(log) ? Files.readString(log, StandardCharsets.UTF_8) : "";
    }

    public void awaitLog(String marker, Duration timeout) throws Exception {
        long deadline = System.nanoTime() + timeout.toNanos();
        while (System.nanoTime() < deadline) {
            if (!process.isAlive()) {
                throw new AssertionError("Child process exited before '" + marker + "':\n" + readLog());
            }
            if (readLog().contains(marker)) return;
            Thread.sleep(100);
        }
        throw new AssertionError("Child process did not emit '" + marker + "' within " + timeout + ":\n" + readLog());
    }

    private void pump() {
        try (BufferedReader reader = new BufferedReader(new InputStreamReader(process.getInputStream(), StandardCharsets.UTF_8));
             var writer = Files.newBufferedWriter(log, StandardCharsets.UTF_8,
                 StandardOpenOption.CREATE, StandardOpenOption.APPEND)) {
            String line;
            while ((line = reader.readLine()) != null) {
                writer.write(line);
                writer.newLine();
                writer.flush();
            }
        } catch (IOException ignored) {
            // Process teardown can close the pipe before isAlive changes; the durable log is already flushed.
        }
    }

    @Override
    public void close() throws Exception {
        if (process.isAlive()) process.destroy();
        if (!process.waitFor(10, TimeUnit.SECONDS)) {
            process.destroyForcibly();
            process.waitFor(10, TimeUnit.SECONDS);
        }
        outputPump.join(Duration.ofSeconds(5));
    }
}
