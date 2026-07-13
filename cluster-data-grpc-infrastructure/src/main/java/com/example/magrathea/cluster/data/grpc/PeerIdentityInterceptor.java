package com.example.magrathea.cluster.data.grpc;

import com.example.magrathea.storageengine.cluster.application.NodeIdentity;
import io.grpc.*;
import java.security.cert.X509Certificate;
import java.util.List;
import java.util.Set;

/** Rejects a trusted mTLS chain unless its URI SAN is an explicitly admitted stable node UUID. */
final class PeerIdentityInterceptor implements ServerInterceptor {
    static final Context.Key<NodeIdentity> PEER_IDENTITY = Context.key("verified-cluster-peer");
    private static final String PREFIX = "urn:magrathea:node:";
    private final Set<NodeIdentity> expected;
    PeerIdentityInterceptor(Set<NodeIdentity> expected) { this.expected = expected; }

    @Override public <ReqT, RespT> ServerCall.Listener<ReqT> interceptCall(
            ServerCall<ReqT, RespT> call, Metadata headers, ServerCallHandler<ReqT, RespT> next) {
        try {
            var session = call.getAttributes().get(Grpc.TRANSPORT_ATTR_SSL_SESSION);
            if (session == null) throw new SecurityException("TLS session required");
            X509Certificate certificate = (X509Certificate) session.getPeerCertificates()[0];
            NodeIdentity identity = uriIdentity(certificate);
            if (!expected.contains(identity)) throw new SecurityException("unexpected node identity " + identity);
            return Contexts.interceptCall(Context.current().withValue(PEER_IDENTITY, identity), call, headers, next);
        } catch (Exception rejected) {
            call.close(Status.PERMISSION_DENIED.withDescription("mutual TLS node identity rejected"), new Metadata());
            return new ServerCall.Listener<>() { };
        }
    }

    private static NodeIdentity uriIdentity(X509Certificate certificate) throws Exception {
        var sans = certificate.getSubjectAlternativeNames();
        if (sans != null) for (List<?> san : sans) {
            if (((Integer) san.get(0)) == 6 && san.get(1).toString().startsWith(PREFIX)) {
                return NodeIdentity.parse(san.get(1).toString().substring(PREFIX.length()));
            }
        }
        throw new SecurityException("stable node URI SAN missing");
    }
}
