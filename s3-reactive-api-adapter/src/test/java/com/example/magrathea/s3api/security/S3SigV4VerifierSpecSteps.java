package com.example.magrathea.s3api.security;

import io.cucumber.java.en.Given;
import io.cucumber.java.en.Then;
import io.cucumber.java.en.When;
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

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

public class S3SigV4VerifierSpecSteps {

    private static final String HOST = "localhost";
    private static final String SERVICE = "s3";

    private S3SecurityProperties properties;
    private S3SigV4Verifier verifier;
    private S3SigV4Verifier.VerificationResult verificationResult;
    private S3SecurityWebFilter webFilter;
    private MockServerWebExchange exchange;
    private final AtomicBoolean downstreamInvoked = new AtomicBoolean(false);

    @Given("an in-process SigV4 verifier with region {string} and fixed clock {string}")
    public void inProcessSigV4VerifierWithRegionAndFixedClock(String region, String fixedClock) {
        properties = new S3SecurityProperties();
        properties.setEnabled(true);
        properties.setRegion(region);
        properties.setAllowedClockSkewSeconds(900);
        verifier = new S3SigV4Verifier(properties, new S3CredentialStore(properties),
            Clock.fixed(Instant.parse(fixedClock), ZoneOffset.UTC));
    }

    @Given("configured credential access key {string} secret key {string} principal {string}")
    public void configuredCredential(String accessKey, String secretKey, String principal) {
        var credential = new S3SecurityProperties.Credential();
        credential.setAccessKey(accessKey);
        credential.setSecretKey(secretKey);
        credential.setPrincipal(principal);
        properties.setCredentials(List.of(credential));
        verifier = new S3SigV4Verifier(properties, new S3CredentialStore(properties),
            Clock.fixed(Instant.parse("2026-07-09T12:00:00Z"), ZoneOffset.UTC));
    }

    @When("the verifier evaluates a signed PUT request for {string} at {string} using access key {string} secret key {string} and payload hash {string}")
    public void verifierEvaluatesSignedPut(String path, String requestTime, String accessKey,
                                           String secretKey, String payloadHash) {
        verificationResult = verifier.verify(signedRequest(path, accessKey, secretKey,
            Instant.parse(requestTime), payloadHash));
    }

    @When("the verifier evaluates an unsigned PUT request for {string}")
    public void verifierEvaluatesUnsignedPut(String path) {
        verificationResult = verifier.verify(MockServerHttpRequest.put("http://localhost" + path)
            .header(HttpHeaders.HOST, HOST)
            .build());
    }

    @When("the verifier evaluates a signed PUT request with authentication defect {string}")
    public void verifierEvaluatesSignedPutWithAuthenticationDefect(String defect) {
        String accessKey = "AKIAMAGRATHEATEST1";
        String secretKey = "test-secret-key";
        Instant requestTime = Instant.parse("2026-07-09T12:00:00Z");
        if ("unknown-access-key".equals(defect)) {
            accessKey = "AKIAUNKNOWN";
            secretKey = "unknown-secret";
        } else if ("bad-signature".equals(defect)) {
            secretKey = "wrong-secret";
        } else if ("stale-x-amz-date".equals(defect)) {
            requestTime = Instant.parse("2026-07-09T11:00:00Z");
        } else {
            throw new AssertionError("Unsupported SigV4 verifier defect: " + defect);
        }
        verificationResult = verifier.verify(signedRequest(
            "/secure-ingest/incoming/report.csv", accessKey, secretKey, requestTime, "UNSIGNED-PAYLOAD"));
    }

    @Then("the verifier allows the request for principal {string}")
    public void verifierAllowsRequestForPrincipal(String principal) {
        assertTrue(verificationResult.allowed());
        assertEquals(principal, verificationResult.principal());
    }

    @Then("the verifier denies the request with S3 error code {string}")
    public void verifierDeniesRequestWithS3ErrorCode(String errorCode) {
        assertFalse(verificationResult.allowed());
        assertEquals(errorCode, verificationResult.errorCode());
    }

    @Given("a secured-mode WebFilter with security enabled {word}")
    public void securedModeWebFilterWithSecurityEnabled(String enabled) {
        properties = new S3SecurityProperties();
        properties.setEnabled(Boolean.parseBoolean(enabled));
        properties.setRegion("us-east-1");
        properties.setAllowedClockSkewSeconds(900);
        webFilter = new S3SecurityWebFilter(properties,
            new S3SigV4Verifier(properties, new S3CredentialStore(properties),
                Clock.fixed(Instant.parse("2026-07-09T12:00:00Z"), ZoneOffset.UTC)));
        downstreamInvoked.set(false);
    }

    @When("the WebFilter receives an unsigned PUT request for {string}")
    public void webFilterReceivesUnsignedPut(String path) {
        exchange = MockServerWebExchange.from(MockServerHttpRequest.put("http://localhost" + path).build());
        WebFilterChain chain = e -> {
            downstreamInvoked.set(true);
            return Mono.empty();
        };
        webFilter.filter(exchange, chain).block();
    }

    @Then("the downstream chain is invoked")
    public void downstreamChainIsInvoked() {
        assertTrue(downstreamInvoked.get());
    }

    @Then("the downstream chain is not invoked")
    public void downstreamChainIsNotInvoked() {
        assertFalse(downstreamInvoked.get());
    }

    @Then("the WebFilter response status is {int}")
    public void webFilterResponseStatusIs(int status) {
        assertEquals(status, exchange.getResponse().getStatusCode().value());
    }

    private static org.springframework.http.server.reactive.ServerHttpRequest signedRequest(
            String path, String accessKey, String secretKey, Instant instant, String payloadHash) {
        String amzDate = DateTimeFormatter.ofPattern("yyyyMMdd'T'HHmmss'Z'").withZone(ZoneOffset.UTC).format(instant);
        String shortDate = DateTimeFormatter.BASIC_ISO_DATE.withZone(ZoneOffset.UTC).format(instant);
        String signedHeaders = "host;x-amz-content-sha256;x-amz-date";
        String canonicalRequest = String.join("\n",
            "PUT",
            path,
            "",
            "host:" + HOST + "\n" +
                "x-amz-content-sha256:" + payloadHash + "\n" +
                "x-amz-date:" + amzDate + "\n",
            signedHeaders,
            payloadHash);
        String credentialScope = shortDate + "/us-east-1/" + SERVICE + "/aws4_request";
        String stringToSign = String.join("\n",
            "AWS4-HMAC-SHA256",
            amzDate,
            credentialScope,
            sha256Hex(canonicalRequest));
        String signature = hmacHex(signingKey(secretKey, shortDate, "us-east-1", SERVICE), stringToSign);
        String authorization = "AWS4-HMAC-SHA256 Credential=" + accessKey + "/" + credentialScope
            + ", SignedHeaders=" + signedHeaders
            + ", Signature=" + signature;
        return MockServerHttpRequest.put("http://localhost" + path)
            .header(HttpHeaders.HOST, HOST)
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
