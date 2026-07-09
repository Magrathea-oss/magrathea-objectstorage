package com.example.magrathea.s3api.security;

import org.junit.jupiter.api.Test;
import org.springframework.http.HttpHeaders;
import org.springframework.mock.http.server.reactive.MockServerHttpRequest;
import org.springframework.mock.web.server.MockServerWebExchange;
import org.springframework.web.server.WebFilterChain;
import reactor.core.publisher.Mono;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.HexFormat;
import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;

import static org.junit.jupiter.api.Assertions.*;

class S3SigV4VerifierTest {

    private static final Instant NOW = Instant.parse("2026-07-09T12:00:00Z");
    private static final String ACCESS_KEY = "AKIAMAGRATHEATEST1";
    private static final String SECRET_KEY = "test-secret-key";
    private static final String REGION = "us-east-1";

    @Test
    void validSigV4RequestIsAllowed() {
        var verifier = verifier();
        var request = signedRequest(ACCESS_KEY, SECRET_KEY, NOW, "UNSIGNED-PAYLOAD");

        var result = verifier.verify(request);

        assertTrue(result.allowed());
        assertEquals("tenant-a-writer", result.principal());
    }

    @Test
    void anonymousRequestIsRejectedInSecuredMode() {
        var verifier = verifier();
        var request = MockServerHttpRequest.put("http://localhost/secure-ingest/incoming/report.csv")
            .header(HttpHeaders.HOST, "localhost")
            .build();

        var result = verifier.verify(request);

        assertFalse(result.allowed());
        assertEquals("AccessDenied", result.errorCode());
    }

    @Test
    void unknownAccessKeyIsRejected() {
        var verifier = verifier();
        var request = signedRequest("AKIAUNKNOWN", "unknown-secret", NOW, "UNSIGNED-PAYLOAD");

        var result = verifier.verify(request);

        assertFalse(result.allowed());
        assertEquals("InvalidAccessKeyId", result.errorCode());
    }

    @Test
    void badSignatureIsRejected() {
        var verifier = verifier();
        var request = signedRequest(ACCESS_KEY, "wrong-secret", NOW, "UNSIGNED-PAYLOAD");

        var result = verifier.verify(request);

        assertFalse(result.allowed());
        assertEquals("SignatureDoesNotMatch", result.errorCode());
    }

    @Test
    void staleDateIsRejected() {
        var verifier = verifier();
        var request = signedRequest(ACCESS_KEY, SECRET_KEY, NOW.minusSeconds(3600), "UNSIGNED-PAYLOAD");

        var result = verifier.verify(request);

        assertFalse(result.allowed());
        assertEquals("RequestTimeTooSkewed", result.errorCode());
    }

    @Test
    void securityFilterBypassesWhenDisabled() {
        var properties = securityProperties(false);
        var filter = new S3SecurityWebFilter(properties, verifier(properties));
        var exchange = MockServerWebExchange.from(MockServerHttpRequest.put("http://localhost/bucket/key").build());
        AtomicBoolean called = new AtomicBoolean(false);
        WebFilterChain chain = e -> {
            called.set(true);
            return Mono.empty();
        };

        filter.filter(exchange, chain).block();

        assertTrue(called.get());
    }

    @Test
    void securityFilterRejectsUnsignedWhenEnabled() {
        var properties = securityProperties(true);
        var filter = new S3SecurityWebFilter(properties, verifier(properties));
        var exchange = MockServerWebExchange.from(MockServerHttpRequest.put("http://localhost/bucket/key").build());
        AtomicBoolean called = new AtomicBoolean(false);
        WebFilterChain chain = e -> {
            called.set(true);
            return Mono.empty();
        };

        filter.filter(exchange, chain).block();

        assertFalse(called.get());
        assertEquals(403, exchange.getResponse().getStatusCode().value());
    }

    private static S3SigV4Verifier verifier() {
        var properties = securityProperties(true);
        return verifier(properties);
    }

    private static S3SigV4Verifier verifier(S3SecurityProperties properties) {
        return new S3SigV4Verifier(properties, new S3CredentialStore(properties), Clock.fixed(NOW, ZoneOffset.UTC));
    }

    private static S3SecurityProperties securityProperties(boolean enabled) {
        var properties = new S3SecurityProperties();
        properties.setEnabled(enabled);
        properties.setRegion(REGION);
        properties.setAllowedClockSkewSeconds(900);
        var credential = new S3SecurityProperties.Credential();
        credential.setAccessKey(ACCESS_KEY);
        credential.setSecretKey(SECRET_KEY);
        credential.setPrincipal("tenant-a-writer");
        properties.setCredentials(List.of(credential));
        return properties;
    }

    private static org.springframework.http.server.reactive.ServerHttpRequest signedRequest(
            String accessKey, String secretKey, Instant instant, String payloadHash) {
        String amzDate = DateTimeFormatter.ofPattern("yyyyMMdd'T'HHmmss'Z'").withZone(ZoneOffset.UTC).format(instant);
        String shortDate = DateTimeFormatter.BASIC_ISO_DATE.withZone(ZoneOffset.UTC).format(instant);
        String signedHeaders = "host;x-amz-content-sha256;x-amz-date";
        String canonicalRequest = String.join("\n",
            "PUT",
            "/secure-ingest/incoming/report.csv",
            "",
            "host:localhost\n" +
                "x-amz-content-sha256:" + payloadHash + "\n" +
                "x-amz-date:" + amzDate + "\n",
            signedHeaders,
            payloadHash);
        String credentialScope = shortDate + "/" + REGION + "/s3/aws4_request";
        String stringToSign = String.join("\n",
            "AWS4-HMAC-SHA256",
            amzDate,
            credentialScope,
            sha256Hex(canonicalRequest));
        String signature = hmacHex(signingKey(secretKey, shortDate, REGION, "s3"), stringToSign);
        String authorization = "AWS4-HMAC-SHA256 Credential=" + accessKey + "/" + credentialScope
            + ", SignedHeaders=" + signedHeaders
            + ", Signature=" + signature;
        return MockServerHttpRequest.put("http://localhost/secure-ingest/incoming/report.csv")
            .header(HttpHeaders.HOST, "localhost")
            .header("X-Amz-Date", amzDate)
            .header("X-Amz-Content-SHA256", payloadHash)
            .header(HttpHeaders.AUTHORIZATION, authorization)
            .build();
    }

    private static byte[] signingKey(String secretKey, String date, String region, String service) {
        byte[] kDate = hmac(("AWS4" + secretKey).getBytes(StandardCharsets.UTF_8), date);
        byte[] kRegion = hmac(kDate, region);
        byte[] kService = hmac(kRegion, service);
        return hmac(kService, "aws4_request");
    }

    private static String hmacHex(byte[] key, String data) {
        return HexFormat.of().formatHex(hmac(key, data));
    }

    private static byte[] hmac(byte[] key, String data) {
        try {
            Mac mac = Mac.getInstance("HmacSHA256");
            mac.init(new SecretKeySpec(key, "HmacSHA256"));
            return mac.doFinal(data.getBytes(StandardCharsets.UTF_8));
        } catch (Exception e) {
            throw new IllegalStateException(e);
        }
    }

    private static String sha256Hex(String value) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            return HexFormat.of().formatHex(digest.digest(value.getBytes(StandardCharsets.UTF_8)));
        } catch (Exception e) {
            throw new IllegalStateException(e);
        }
    }
}
