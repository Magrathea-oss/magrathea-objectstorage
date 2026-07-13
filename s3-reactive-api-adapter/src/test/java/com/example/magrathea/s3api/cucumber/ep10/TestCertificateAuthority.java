package com.example.magrathea.s3api.cucumber.ep10;

import com.example.magrathea.storageengine.cluster.application.NodeIdentity;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

/** Test-only mounted-PKI generator shared by the composed Ratis and replica-data runtimes. */
final class TestCertificateAuthority {
    private final Path root;

    TestCertificateAuthority(Path root) {
        this.root = root;
    }

    Material create(String name, NodeIdentity identity) throws Exception {
        Path ca = root.resolve("ca");
        Files.createDirectories(ca);
        Path caKey = ca.resolve("ca.key");
        Path caCertificate = ca.resolve("ca.crt");
        if (!Files.exists(caCertificate)) {
            run("openssl", "req", "-x509", "-newkey", "rsa:2048", "-nodes", "-days", "2",
                    "-subj", "/CN=EP10 Coordination Test CA", "-keyout", caKey.toString(),
                    "-out", caCertificate.toString());
        }
        Path node = root.resolve("nodes").resolve(name);
        Files.createDirectories(node);
        Path key = node.resolve("tls.key");
        Path request = node.resolve("tls.csr");
        Path certificate = node.resolve("tls.crt");
        Path extensions = node.resolve("tls.ext");
        Files.writeString(extensions,
                "basicConstraints=CA:FALSE\n"
                        + "keyUsage=digitalSignature,keyEncipherment\n"
                        + "extendedKeyUsage=serverAuth,clientAuth\n"
                        + "subjectAltName=URI:urn:magrathea:node:" + identity + ",DNS:" + identity
                        + ",DNS:localhost,IP:127.0.0.1\n");
        run("openssl", "req", "-new", "-newkey", "rsa:2048", "-nodes", "-subj", "/CN=" + name,
                "-keyout", key.toString(), "-out", request.toString());
        run("openssl", "x509", "-req", "-days", "2", "-in", request.toString(),
                "-CA", caCertificate.toString(), "-CAkey", caKey.toString(), "-CAcreateserial",
                "-extfile", extensions.toString(), "-out", certificate.toString());
        return new Material(certificate, key, caCertificate);
    }

    private static void run(String... command) throws Exception {
        Process process = new ProcessBuilder(command).redirectErrorStream(true).start();
        String output = new String(process.getInputStream().readAllBytes());
        if (process.waitFor() != 0) {
            throw new IOException(String.join(" ", command) + " failed: " + output);
        }
    }

    record Material(Path certificate, Path key, Path ca) { }
}
