package com.example.magrathea.cluster.control.ratis;

import com.example.magrathea.storageengine.cluster.application.NodeIdentity;
import org.apache.ratis.thirdparty.io.grpc.ForwardingServerCallListener;
import org.apache.ratis.thirdparty.io.grpc.Grpc;
import org.apache.ratis.thirdparty.io.grpc.Metadata;
import org.apache.ratis.thirdparty.io.grpc.ServerCall;
import org.apache.ratis.thirdparty.io.grpc.ServerCallHandler;
import org.apache.ratis.thirdparty.io.grpc.ServerInterceptor;
import org.apache.ratis.thirdparty.io.grpc.Status;

import javax.net.ssl.SSLSession;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.cert.CertificateFactory;
import java.security.cert.X509Certificate;
import java.util.List;
import java.util.Set;

/**
 * URI-SAN authorization layered on Ratis' chain-authenticating {@code GrpcTlsConfig}.
 * Server-protocol requests are additionally bound to their protobuf requestor UUID. Ratis 3.2.2 does not
 * expose an expected-peer certificate callback on outbound channels, so outbound endpoint authorization is
 * limited to CA trust plus hostname verification; startup validation still binds every mounted local
 * certificate to its configured UUID and inbound authorization admits only the fixed voter UUID set.
 */
final class RatisPeerIdentity {
    private static final String URI_PREFIX = "urn:magrathea:node:";

    private RatisPeerIdentity() { }

    static void requireCertificateIdentity(Path certificatePath, NodeIdentity expected) {
        try (InputStream input = Files.newInputStream(certificatePath)) {
            X509Certificate certificate = (X509Certificate) CertificateFactory.getInstance("X.509")
                    .generateCertificate(input);
            NodeIdentity actual = uriIdentity(certificate);
            if (!expected.equals(actual)) {
                throw new IllegalArgumentException("Ratis certificate identity " + actual
                        + " does not match configured stable node UUID " + expected);
            }
        } catch (IllegalArgumentException failure) {
            throw failure;
        } catch (Exception failure) {
            throw new IllegalArgumentException("cannot validate Ratis certificate identity from " + certificatePath, failure);
        }
    }

    static ServerInterceptor allowOnly(Set<NodeIdentity> acceptedPeers) {
        Set<NodeIdentity> accepted = Set.copyOf(acceptedPeers);
        return new ServerInterceptor() {
            @Override
            public <ReqT, RespT> ServerCall.Listener<ReqT> interceptCall(
                    ServerCall<ReqT, RespT> call, Metadata headers, ServerCallHandler<ReqT, RespT> next) {
                try {
                    SSLSession session = call.getAttributes().get(Grpc.TRANSPORT_ATTR_SSL_SESSION);
                    if (session == null) {
                        throw new SecurityException("Ratis mutual TLS session is required");
                    }
                    NodeIdentity actual = uriIdentity((X509Certificate) session.getPeerCertificates()[0]);
                    if (!accepted.contains(actual)) {
                        throw new SecurityException("Ratis peer stable UUID is not admitted: " + actual);
                    }
                    ServerCall.Listener<ReqT> delegate = next.startCall(call, headers);
                    return new ForwardingServerCallListener.SimpleForwardingServerCallListener<>(delegate) {
                        @Override public void onMessage(ReqT message) {
                            try {
                                NodeIdentity requestor = requestorIdentity(message);
                                if (requestor != null && !requestor.equals(actual)) {
                                    call.close(Status.PERMISSION_DENIED.withDescription(
                                            "Ratis protobuf requestor UUID does not match authenticated certificate"), new Metadata());
                                    return;
                                }
                                super.onMessage(message);
                            } catch (Exception rejected) {
                                call.close(Status.PERMISSION_DENIED.withDescription(
                                        "Ratis authenticated request identity rejected"), new Metadata());
                            }
                        }
                    };
                } catch (Exception rejected) {
                    call.close(Status.PERMISSION_DENIED.withDescription("Ratis mutual TLS node identity rejected"),
                            new Metadata());
                    return new ServerCall.Listener<>() { };
                }
            }
        };
    }

    private static NodeIdentity requestorIdentity(Object message) throws Exception {
        java.lang.reflect.Method serverRequest;
        try {
            serverRequest = message.getClass().getMethod("getServerRequest");
        } catch (NoSuchMethodException notServerProtocol) {
            return null;
        }
        Object request = serverRequest.invoke(message);
        Object bytes = request.getClass().getMethod("getRequestorId").invoke(request);
        String value = (String) bytes.getClass().getMethod("toStringUtf8").invoke(bytes);
        return value.isEmpty() ? null : NodeIdentity.parse(value);
    }

    private static NodeIdentity uriIdentity(X509Certificate certificate) throws Exception {
        var names = certificate.getSubjectAlternativeNames();
        if (names != null) {
            for (List<?> name : names) {
                if (((Integer) name.get(0)) == 6 && name.get(1).toString().startsWith(URI_PREFIX)) {
                    return NodeIdentity.parse(name.get(1).toString().substring(URI_PREFIX.length()));
                }
            }
        }
        throw new SecurityException("Ratis certificate has no stable node UUID URI SAN");
    }
}
