package com.example.magrathea.cluster.control.ratis;

import com.example.magrathea.storageengine.cluster.application.NodeIdentity;
import org.apache.ratis.grpc.GrpcTlsConfig;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Objects;
import java.util.Set;

/** Mandatory mounted mTLS material and stable peer-identity policy for one Ratis process. */
public record RatisTlsConfig(Path certificateChain, Path privateKey, Path trustCertificate,
                             NodeIdentity localIdentity, Set<NodeIdentity> acceptedPeers) {
    public RatisTlsConfig {
        Objects.requireNonNull(certificateChain, "Ratis certificate chain path is required");
        Objects.requireNonNull(privateKey, "Ratis private key path is required");
        Objects.requireNonNull(trustCertificate, "Ratis trust certificate path is required");
        Objects.requireNonNull(localIdentity, "Ratis local node identity is required");
        acceptedPeers = Set.copyOf(Objects.requireNonNull(acceptedPeers, "Ratis accepted peers are required"));
        if (acceptedPeers.isEmpty()) {
            throw new IllegalArgumentException("Ratis mTLS requires at least one accepted stable node identity");
        }
        requireReadable(certificateChain, "certificate chain");
        requireReadable(privateKey, "private key");
        requireReadable(trustCertificate, "trust certificate");
        RatisPeerIdentity.requireCertificateIdentity(certificateChain, localIdentity);
    }

    GrpcTlsConfig grpcTlsConfig() {
        return new GrpcTlsConfig(privateKey.toFile(), certificateChain.toFile(), trustCertificate.toFile(), true);
    }

    private static void requireReadable(Path path, String description) {
        if (!Files.isRegularFile(path) || !Files.isReadable(path)) {
            throw new IllegalArgumentException("Ratis mTLS " + description + " is not a readable file: " + path);
        }
    }
}
