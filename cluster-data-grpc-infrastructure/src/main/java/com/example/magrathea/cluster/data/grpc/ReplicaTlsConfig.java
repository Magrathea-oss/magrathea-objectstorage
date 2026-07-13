package com.example.magrathea.cluster.data.grpc;

import com.example.magrathea.storageengine.cluster.application.NodeIdentity;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.cert.CertificateFactory;
import java.security.cert.X509Certificate;
import java.util.List;
import java.util.Objects;
import java.util.Set;

/** Mandatory production TLS material and stable identity policy; certificate provisioning is external. */
public record ReplicaTlsConfig(Path certificateChain, Path privateKey, Path trustCertificate,
                               NodeIdentity localIdentity, Set<NodeIdentity> acceptedPeers) {
    private static final String NODE_URI_PREFIX = "urn:magrathea:node:";
    private static final String IDENTITY_FAILURE =
            "replica-data certificate does not bind configured local identity";

    public ReplicaTlsConfig {
        Objects.requireNonNull(certificateChain);
        Objects.requireNonNull(privateKey);
        Objects.requireNonNull(trustCertificate);
        Objects.requireNonNull(localIdentity);
        acceptedPeers = Set.copyOf(acceptedPeers);
        if (acceptedPeers.isEmpty()) {
            throw new IllegalArgumentException("at least one expected peer identity is mandatory");
        }
        requireCertificateIdentity(certificateChain, localIdentity);
    }

    private static void requireCertificateIdentity(Path certificatePath, NodeIdentity expectedIdentity) {
        try (InputStream input = Files.newInputStream(certificatePath)) {
            X509Certificate certificate = (X509Certificate) CertificateFactory.getInstance("X.509")
                    .generateCertificate(input);
            NodeIdentity certificateIdentity = null;
            var names = certificate.getSubjectAlternativeNames();
            if (names != null) {
                for (List<?> name : names) {
                    if (name.size() > 1 && Integer.valueOf(6).equals(name.get(0))
                            && name.get(1) instanceof String uri && uri.startsWith(NODE_URI_PREFIX)) {
                        if (certificateIdentity != null) {
                            throw identityFailure();
                        }
                        certificateIdentity = NodeIdentity.parse(uri.substring(NODE_URI_PREFIX.length()));
                    }
                }
            }
            if (!expectedIdentity.equals(certificateIdentity)) {
                throw identityFailure();
            }
        } catch (Exception failure) {
            throw identityFailure();
        }
    }

    private static IllegalArgumentException identityFailure() {
        return new IllegalArgumentException(IDENTITY_FAILURE);
    }
}
