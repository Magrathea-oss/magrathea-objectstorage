package com.example.magrathea.storageengine.cluster.application;

import java.net.InetSocketAddress;
import java.util.Objects;

/** Transport-neutral fixed voter declaration with distinct control and replica-data endpoints. */
public record ClusterMember(
        String name,
        NodeIdentity identity,
        String host,
        int controlPort,
        String dataHost,
        int dataPort,
        String failureDomain) {
    public ClusterMember {
        if (name == null || name.isBlank()) throw new IllegalArgumentException("name is required");
        Objects.requireNonNull(identity, "identity");
        if (host == null || host.isBlank()) throw new IllegalArgumentException("host is required");
        if (controlPort < 1 || controlPort > 65535) throw new IllegalArgumentException("invalid control port");
        if (dataHost == null || dataHost.isBlank()) throw new IllegalArgumentException("data host is required");
        if (dataPort < 1 || dataPort > 65535) throw new IllegalArgumentException("invalid data port");
        if (failureDomain == null || failureDomain.isBlank()) {
            throw new IllegalArgumentException("failure domain is required");
        }
    }

    /** Preserves the fixed A/B/C bootstrap constructor and its 19801/19901 port convention. */
    public ClusterMember(String name, NodeIdentity identity, String host, int controlPort) {
        this(name, identity, host, controlPort, host, controlPort + 100, name);
    }

    public InetSocketAddress controlAddress() {
        return new InetSocketAddress(host, controlPort);
    }

    public InetSocketAddress dataAddress() {
        return new InetSocketAddress(dataHost, dataPort);
    }
}
