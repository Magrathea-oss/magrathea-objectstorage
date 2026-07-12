package com.example.magrathea.s3api.cucumber.connectioncap;

import com.example.magrathea.s3api.cucumber.requirements.RequirementsTestApp;
import com.example.magrathea.s3api.cucumber.support.ChildProcessSupport;
import io.cucumber.java.After;
import io.cucumber.java.en.And;
import io.cucumber.java.en.Given;
import io.cucumber.java.en.Then;
import io.cucumber.java.en.When;

import java.io.IOException;
import java.net.ServerSocket;
import java.net.Socket;
import java.net.SocketException;
import java.net.SocketTimeoutException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.fail;

public class PhaseEp6ConnectionCapSteps {

    private static final int TEST_CAP = 4;
    private static final Duration CLOSE_DEADLINE = Duration.ofSeconds(2);

    private final List<Socket> accepted = new ArrayList<>();
    private Process process;
    private ChildProcessSupport child;
    private Path log;
    private int port;
    private Socket excess;
    private boolean excessClosed;

    @Given("a real 0.1.x single-node S3 child process has a configured TCP connection cap")
    public void startChildProcess() throws Exception {
        port = availablePort();
        log = Files.createTempFile("ep6-connection-cap-", ".log");
        String java = Path.of(System.getProperty("java.home"), "bin", "java").toString();
        String classpath = System.getProperty("surefire.test.class.path", System.getProperty("java.class.path"));
        child = ChildProcessSupport.start(List.of(
            java, "-cp", classpath, RequirementsTestApp.class.getName(),
            "--server.port=" + port,
            "--spring.main.banner-mode=off",
            "--s3.security.enabled=false",
            "--s3.capacity.enabled=true",
            "--s3.capacity.max-tcp-connections=" + TEST_CAP), log);
        process = child.process();
        child.awaitLog("Netty started on port " + port, Duration.ofSeconds(30));
    }

    @And("real TCP clients hold every allowed connection open")
    public void holdEveryConnection() throws Exception {
        for (int i = 0; i < TEST_CAP; i++) {
            accepted.add(connect());
        }
        Thread.sleep(200);
        for (Socket socket : accepted) {
            assertStillOpen(socket, "an admitted connection was closed while filling the cap");
        }
    }

    @When("one additional TCP client attempts to connect")
    public void connectOneAdditionalClient() throws Exception {
        excess = connect();
        excessClosed = awaitClosed(excess, CLOSE_DEADLINE);
    }

    @Then("the additional connection is refused or closed within the connection validation deadline")
    public void additionalConnectionCloses() throws Exception {
        assertTrue(excessClosed, "the fifth TCP connection remained open beyond " + CLOSE_DEADLINE);
        awaitRejectedLog();
        String rejection = Files.readAllLines(log).stream()
            .filter(line -> line.contains("S3 TCP connection rejected"))
            .findFirst().orElseThrow();
        assertFalse(rejection.contains("127.0.0.1"), "rejection log must not expose the client address");
    }

    @And("the S3 process remains live")
    public void processRemainsLive() throws Exception {
        assertTrue(process.isAlive(), "S3 child process exited:\n" + readLog());
        for (Socket socket : accepted) {
            assertStillOpen(socket, "rejecting an excess socket disturbed an existing connection");
        }
    }

    @And("closing one accepted connection permits a subsequent TCP client connection")
    public void capacityRecovers() throws Exception {
        accepted.removeFirst().close();
        Socket recovered = null;
        long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(3);
        while (System.nanoTime() < deadline) {
            Socket candidate = connect();
            if (!awaitClosed(candidate, Duration.ofMillis(200))) {
                recovered = candidate;
                break;
            }
            candidate.close();
            Thread.sleep(50);
        }
        assertTrue(recovered != null, "capacity did not recover after an admitted connection closed");
        accepted.add(recovered);
        for (Socket socket : accepted) {
            assertStillOpen(socket, "capacity recovery disturbed an existing connection");
        }
    }

    @After
    public void cleanup() throws Exception {
        if (excess != null) excess.close();
        for (Socket socket : accepted) socket.close();
        accepted.clear();
        if (child != null) child.close();
    }

    private Socket connect() throws IOException {
        Socket socket = new Socket("127.0.0.1", port);
        socket.setSoTimeout(200);
        return socket;
    }

    private static boolean awaitClosed(Socket socket, Duration timeout) throws IOException {
        long deadline = System.nanoTime() + timeout.toNanos();
        while (System.nanoTime() < deadline) {
            try {
                int value = socket.getInputStream().read();
                if (value < 0) return true;
            } catch (SocketTimeoutException ignored) {
                // An admitted idle socket has no bytes to read; keep waiting until the deadline.
            } catch (SocketException closed) {
                return true;
            }
        }
        return false;
    }

    private static void assertStillOpen(Socket socket, String message) throws IOException {
        assertFalse(awaitClosed(socket, Duration.ofMillis(250)), message);
    }

    private void awaitRejectedLog() throws Exception {
        long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(2);
        while (System.nanoTime() < deadline) {
            if (readLog().contains("S3 TCP connection rejected")) return;
            Thread.sleep(25);
        }
        fail("redacted TCP rejection log was not emitted:\n" + readLog());
    }

    private String readLog() throws IOException {
        return child == null ? "" : child.readLog();
    }

    private static int availablePort() throws IOException {
        try (ServerSocket socket = new ServerSocket(0)) {
            return socket.getLocalPort();
        }
    }
}
