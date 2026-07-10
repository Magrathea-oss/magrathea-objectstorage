package com.example.magrathea.s3api.cucumber.security;

import com.example.magrathea.s3api.security.FileS3SecurityAuditSink;
import com.example.magrathea.s3api.security.S3SecurityAuditSink;
import com.example.magrathea.s3api.security.S3SecurityProperties;
import io.cucumber.java.Before;
import io.cucumber.java.en.Given;
import io.cucumber.java.en.Then;
import io.cucumber.java.en.When;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.test.web.reactive.server.EntityExchangeResult;
import org.springframework.test.web.reactive.server.WebTestClient;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.time.Instant;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.HexFormat;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

public class Ep1SecurityIdentitySteps {

    private static final Instant REQUEST_TIME = Instant.now();
    private static final Instant STALE_REQUEST_TIME = REQUEST_TIME.minusSeconds(3600);
    private static final String ACCESS_KEY = "AKIAMAGRATHEATEST1";
    private static final String SECRET_KEY = "test-secret-key";
    private static final String READER_ACCESS_KEY = "AKIAMAGRATHEAREAD1";
    private static final String READER_SECRET_KEY = "reader-secret-key";
    private static final String UNKNOWN_ACCESS_KEY = "AKIAUNKNOWN";
    private static final String UNKNOWN_SECRET_KEY = "unknown-secret-key";
    private static final String REGION = "us-east-1";
    private static final String HOST = "localhost";
    private static final String UNSIGNED_PAYLOAD = "UNSIGNED-PAYLOAD";

    @LocalServerPort
    private int port;

    @Autowired
    private S3SecurityAuditSink auditSink;

    @Autowired
    private S3SecurityProperties securityProperties;

    private WebTestClient webTestClient;
    private EntityExchangeResult<String> lastResponse;
    private String currentBucket;
    private String currentKey;
    private String currentBody;
    private String retrievedBody;

    @Before
    public void resetSecurityState() {
        if (auditSink != null) {
            auditSink.clear();
        }
        deleteRecursively(Path.of("target/storage-engine-it/REQ-SEC-009-sse"));
    }

    @Given("secured S3 mode is enabled")
    public void securedS3ModeIsEnabled() {
        webTestClient = client();
    }

    @Given("secured S3 mode is enabled with credential profile {string}")
    public void securedS3ModeIsEnabledWithCredentialProfile(String profile) {
        webTestClient = client();
    }

    @Given("secured S3 mode is enabled with access key {string} for principal {string}")
    public void securedS3ModeIsEnabledWithAccessKeyForPrincipal(String accessKey, String principal) {
        boolean configuredWriter = ACCESS_KEY.equals(accessKey) && "tenant-a-writer".equals(principal);
        boolean configuredReader = READER_ACCESS_KEY.equals(accessKey) && "tenant-a-reader".equals(principal);
        assertTrue(configuredWriter || configuredReader, "Cucumber security profile must match test configuration");
        webTestClient = client();
    }

    @Given("secured S3 mode is enabled with principal {string}")
    public void securedS3ModeIsEnabledWithPrincipal(String principal) {
        assertTrue("tenant-a-writer".equals(principal) || "tenant-a-reader".equals(principal),
            "Unsupported test principal: " + principal);
        webTestClient = client();
    }

    @Given("bucket {string} has PublicAccessBlock setting {string} enabled")
    public void bucketHasPublicAccessBlockSettingEnabled(String bucket, String setting) {
        currentBucket = bucket;
        assertEquals("BlockPublicAcls", setting);
        S3SecurityProperties.BucketRule rule = ensureBucketRule(bucket);
        rule.setBlockPublicAcls(true);
    }

    @Given("object {string} has ACL {string}")
    public void objectHasAcl(String key, String acl) {
        currentKey = key;
        assertEquals("public-read", acl);
        S3SecurityProperties.BucketRule rule = ensureBucketRule(currentBucket);
        if (!rule.getPublicReadKeys().contains(key)) {
            rule.getPublicReadKeys().add(key);
        }
    }

    @Given("bucket {string} is owned by account {string}")
    public void bucketIsOwnedByAccount(String bucket, String account) {
        currentBucket = bucket;
        S3SecurityProperties.BucketRule rule = ensureBucketRule(bucket);
        rule.setOwner(account);
    }

    @Given("principal {string} is allowed action {string} on bucket {string}")
    public void principalIsAllowedActionOnBucket(String principal, String action, String bucket) {
        S3SecurityProperties.AllowRule allowRule = new S3SecurityProperties.AllowRule();
        allowRule.setPrincipal(principal);
        allowRule.setAction(action);
        allowRule.setBucket(bucket);
        allowRule.setKeyPrefix("");
        securityProperties.getAllowRules().add(allowRule);
    }

    @Given("secured S3 mode is enabled with SSE-S3 default encryption for bucket {string}")
    public void securedS3ModeIsEnabledWithSseS3DefaultEncryptionForBucket(String bucket) {
        securedS3ModeIsEnabled();
        currentBucket = bucket;
        S3SecurityProperties.BucketRule rule = ensureBucketRule(bucket);
        rule.setDefaultSseS3(true);
        bucketExistsForAccount(bucket, "111122223333");
    }

    @Given("bucket {string} exists for account {string}")
    public void bucketExistsForAccount(String bucket, String account) {
        currentBucket = bucket;
        var result = signedRequest("PUT", "/" + bucket, ACCESS_KEY, SECRET_KEY, REQUEST_TIME)
            .exchange()
            .expectBody(String.class)
            .returnResult();
        assertTrue(
            result.getStatus().is2xxSuccessful() || result.getStatus().value() == HttpStatus.CONFLICT.value(),
            "bucket setup should create or find an existing bucket, got " + result.getStatus()
        );
    }

    @When("an unsigned S3 PutObject request stores object {string} with body {string}")
    public void unsignedS3PutObjectRequestStoresObjectWithBody(String key, String body) {
        currentKey = key;
        currentBody = body;
        lastResponse = client().put()
            .uri(objectPath(key))
            .header(HttpHeaders.HOST, HOST)
            .contentType(MediaType.TEXT_PLAIN)
            .bodyValue(body)
            .exchange()
            .expectBody(String.class)
            .returnResult();
    }

    @When("a SigV4 signed S3 PutObject request stores object {string} with body {string}")
    public void sigV4SignedS3PutObjectRequestStoresObjectWithBody(String key, String body) {
        currentKey = key;
        currentBody = body;
        lastResponse = signedRequest("PUT", objectPath(key), ACCESS_KEY, SECRET_KEY, REQUEST_TIME)
            .contentType(MediaType.TEXT_PLAIN)
            .bodyValue(body)
            .exchange()
            .expectBody(String.class)
            .returnResult();
    }

    @When("a SigV4 signed S3 PutObject request with an exact payload hash stores object {string} with body {string}")
    public void sigV4SignedS3PutObjectRequestWithExactPayloadHashStoresObjectWithBody(String key, String body) {
        currentKey = key;
        currentBody = body;
        lastResponse = signedRequest("PUT", objectPath(key), ACCESS_KEY, SECRET_KEY, REQUEST_TIME, sha256Hex(body))
            .contentType(MediaType.TEXT_PLAIN)
            .bodyValue(body)
            .exchange()
            .expectBody(String.class)
            .returnResult();
    }

    @When("a SigV4 signed S3 PutObject request for object {string} is sent with authentication defect {string}")
    public void sigV4SignedS3PutObjectRequestForObjectIsSentWithAuthenticationDefect(String key, String defect) {
        currentKey = key;
        currentBody = "blocked";
        String accessKey = ACCESS_KEY;
        String secretKey = SECRET_KEY;
        Instant requestTime = REQUEST_TIME;
        if ("unknown-access-key".equals(defect)) {
            accessKey = UNKNOWN_ACCESS_KEY;
            secretKey = UNKNOWN_SECRET_KEY;
        } else if ("bad-signature".equals(defect)) {
            secretKey = "wrong-secret-key";
        } else if ("stale-x-amz-date".equals(defect)) {
            requestTime = STALE_REQUEST_TIME;
        } else if ("payload-hash-mismatch".equals(defect)) {
            String declaredPayloadHash = sha256Hex("different request body");
            lastResponse = signedRequest("PUT", objectPath(key), accessKey, secretKey, requestTime, declaredPayloadHash)
                .contentType(MediaType.TEXT_PLAIN)
                .bodyValue(currentBody)
                .exchange()
                .expectBody(String.class)
                .returnResult();
            return;
        } else {
            throw new AssertionError("Unsupported implemented SigV4 defect in this runner: " + defect);
        }
        lastResponse = signedRequest("PUT", objectPath(key), accessKey, secretKey, requestTime)
            .contentType(MediaType.TEXT_PLAIN)
            .bodyValue(currentBody)
            .exchange()
            .expectBody(String.class)
            .returnResult();
    }

    @Given("no bucket policy or ACL grants principal {string} action {string} on object {string}")
    public void noBucketPolicyOrAclGrantsPrincipalActionOnObject(String principal, String action, String key) {
        assertEquals("tenant-a-reader", principal);
        assertEquals("s3:GetObject", action);
        assertEquals("incoming/report.csv", key);
    }

    @Given("bucket policy for {string} allows principal {string} action {string} on prefix {string}")
    public void bucketPolicyAllowsPrincipalActionOnPrefix(String bucket, String principal, String action, String prefix) {
        currentBucket = bucket;
        assertEquals("secure-ingest", bucket);
        assertEquals("tenant-a-writer", principal);
        assertEquals("s3:PutObject", action);
        assertEquals("incoming/", prefix);
    }

    @Given("bucket policy for {string} denies principal {string} action {string} on object {string}")
    public void bucketPolicyDeniesPrincipalActionOnObject(String bucket, String principal, String action, String key) {
        currentBucket = bucket;
        assertEquals("secure-ingest", bucket);
        assertEquals("tenant-a-writer", principal);
        assertEquals("s3:PutObject", action);
        assertEquals("incoming/blocked.csv", key);
    }

    @When("an unsigned GetObject request reads {string} from bucket {string}")
    public void unsignedGetObjectRequestReadsFromBucket(String key, String bucket) {
        currentBucket = bucket;
        currentKey = key;
        lastResponse = client().get()
            .uri("/" + bucket + "/" + key)
            .header(HttpHeaders.HOST, HOST)
            .exchange()
            .expectBody(String.class)
            .returnResult();
    }

    @When("principal {string} sends a correctly signed GetObject request for {string}")
    public void principalSendsCorrectlySignedGetObjectRequestFor(String principal, String key) {
        currentKey = key;
        String accessKey = "tenant-a-reader".equals(principal) ? READER_ACCESS_KEY : ACCESS_KEY;
        String secretKey = "tenant-a-reader".equals(principal) ? READER_SECRET_KEY : SECRET_KEY;
        lastResponse = signedRequest("GET", objectPath(key), accessKey, secretKey, REQUEST_TIME)
            .exchange()
            .expectBody(String.class)
            .returnResult();
    }

    @When("principal {string} sends a signed GetObject request with header {string} set to {string}")
    public void principalSendsSignedGetObjectRequestWithHeaderSetTo(String principal, String header, String value) {
        currentKey = "incoming/report.csv";
        String accessKey = "tenant-a-reader".equals(principal) ? READER_ACCESS_KEY : ACCESS_KEY;
        String secretKey = "tenant-a-reader".equals(principal) ? READER_SECRET_KEY : SECRET_KEY;
        lastResponse = signedRequest("GET", objectPath(currentKey), accessKey, secretKey, REQUEST_TIME)
            .header(header, value)
            .exchange()
            .expectBody(String.class)
            .returnResult();
    }

    @When("principal {string} sends a signed PutObject request for {string} with body {string}")
    public void principalSendsSignedPutObjectRequestForWithBody(String principal, String key, String body) {
        principalSendsCorrectlySignedPutObjectRequestForWithBody(principal, key, body);
    }

    @When("a signed PutObject request stores object {string} with body {string}")
    public void signedPutObjectRequestStoresObjectWithBody(String key, String body) {
        sigV4SignedS3PutObjectRequestStoresObjectWithBody(key, body);
    }

    @When("principal {string} sends a correctly signed PutObject request for {string} with body {string}")
    public void principalSendsCorrectlySignedPutObjectRequestForWithBody(String principal, String key, String body) {
        assertEquals("tenant-a-writer", principal);
        if (currentBucket == null) {
            bucketExistsForAccount("secure-ingest", "111122223333");
        }
        currentKey = key;
        currentBody = body;
        lastResponse = signedRequest("PUT", objectPath(key), ACCESS_KEY, SECRET_KEY, REQUEST_TIME)
            .contentType(MediaType.TEXT_PLAIN)
            .bodyValue(body)
            .exchange()
            .expectBody(String.class)
            .returnResult();
    }

    @Then("the response status is {int}")
    public void theResponseStatusIs(int status) {
        assertEquals(status, lastResponse.getStatus().value());
    }

    @Then("the S3 error code is {string}")
    public void theS3ErrorCodeIs(String errorCode) {
        String body = lastResponse.getResponseBody();
        assertNotNull(body, "S3 error response body should be present");
        assertTrue(body.contains(errorCode), "Expected S3 error code " + errorCode + " in body: " + body);
    }

    @Then("object {string} is not stored in bucket {string}")
    public void objectIsNotStoredInBucket(String key, String bucket) {
        var result = signedRequest("GET", "/" + bucket + "/" + key, ACCESS_KEY, SECRET_KEY, REQUEST_TIME)
            .exchange()
            .expectBody(String.class)
            .returnResult();
        assertEquals(HttpStatus.NOT_FOUND.value(), result.getStatus().value());
    }

    @Then("the response ETag header is present")
    public void theResponseETagHeaderIsPresent() {
        assertNotNull(lastResponse.getResponseHeaders().getETag());
    }

    @Then("a subsequent signed GetObject for {string} returns body {string}")
    public void subsequentSignedGetObjectForReturnsBody(String key, String body) {
        var result = signedRequest("GET", objectPath(key), ACCESS_KEY, SECRET_KEY, REQUEST_TIME)
            .exchange()
            .expectBody(String.class)
            .returnResult();
        retrievedBody = result.getResponseBody();
        assertEquals(HttpStatus.OK.value(), result.getStatus().value());
        assertEquals(body, retrievedBody);
    }

    @Then("an audit event is durably recorded with request id, principal, action, bucket, object key, decision, and response status")
    public void auditEventIsDurablyRecordedWithRequestIdPrincipalActionBucketObjectKeyDecisionAndResponseStatus() {
        boolean found = auditSink.events().stream().anyMatch(event ->
            event.requestId() != null && !event.requestId().isBlank()
                && "tenant-a-writer".equals(event.principal())
                && "s3:PutObject".equals(event.action())
                && currentBucket.equals(event.bucket())
                && currentKey.equals(event.key())
                && "allow".equals(event.decision())
                && event.responseStatus() >= 200
        );
        assertTrue(found, "Expected durable allow audit event for " + currentBucket + "/" + currentKey
            + ", events=" + auditSink.events());
    }

    @Then("the audit event does not contain the secret access key")
    public void auditEventDoesNotContainTheSecretAccessKey() throws Exception {
        String rawAudit = rawAuditText();
        assertFalse(rawAudit.contains(SECRET_KEY), "Audit log must not contain writer secret key");
        assertFalse(rawAudit.contains(READER_SECRET_KEY), "Audit log must not contain reader secret key");
    }

    @Then("the audit event does not contain object body text {string}")
    public void auditEventDoesNotContainObjectBodyText(String bodyText) throws Exception {
        assertFalse(rawAuditText().contains(bodyText), "Audit log must not contain object body text");
    }

    @Then("the audit event remains available after an application restart")
    public void auditEventRemainsAvailableAfterApplicationRestart() {
        S3SecurityAuditSink restartedSink = new FileS3SecurityAuditSink(Path.of(securityProperties.getAuditFile()));
        boolean found = restartedSink.events().stream().anyMatch(event ->
            currentBucket.equals(event.bucket()) && currentKey.equals(event.key()) && "allow".equals(event.decision())
        );
        assertTrue(found, "Expected audit event to survive sink restart, events=" + restartedSink.events());
    }

    @Then("a signed GetObject request returns body {string}")
    public void signedGetObjectRequestReturnsBody(String body) {
        subsequentSignedGetObjectForReturnsBody(currentKey, body);
    }

    @Then("filesystem inspection under storage root {string} finds no plaintext occurrence of {string}")
    public void filesystemInspectionUnderStorageRootFindsNoPlaintextOccurrenceOf(String root, String plaintext) throws Exception {
        Path rootPath = Path.of(root);
        assertTrue(Files.exists(rootPath), "Inspection root should exist: " + rootPath);
        boolean foundPlaintext = Files.walk(rootPath)
            .filter(Files::isRegularFile)
            .anyMatch(path -> {
                try {
                    return Files.readString(path).contains(plaintext);
                } catch (Exception binaryOrIo) {
                    try {
                        return new String(Files.readAllBytes(path), StandardCharsets.UTF_8).contains(plaintext);
                    } catch (Exception e) {
                        throw new IllegalStateException(e);
                    }
                }
            });
        assertFalse(foundPlaintext, "Plaintext must not appear under " + rootPath);
    }

    @Then("an audit event records encryption mode {string}")
    public void auditEventRecordsEncryptionMode(String encryptionMode) {
        boolean found = auditSink.events().stream().anyMatch(event ->
            encryptionMode.equals(event.encryptionMode())
                && currentBucket.equals(event.bucket())
                && currentKey.equals(event.key())
        );
        assertTrue(found, "Expected audit event encryptionMode=" + encryptionMode
            + " for " + currentBucket + "/" + currentKey + ", events=" + auditSink.events());
    }

    @Then("an audit event records decision {string} with reason {string}")
    public void auditEventRecordsDecisionWithReason(String decision, String reason) {
        boolean found = auditSink.events().stream().anyMatch(event ->
            decision.equals(event.decision())
                && reason.equals(event.reason())
                && currentBucket.equals(event.bucket())
                && currentKey.equals(event.key())
        );
        assertTrue(found, "Expected audit event decision=" + decision + " reason=" + reason
            + " for " + currentBucket + "/" + currentKey + ", events=" + auditSink.events());
    }

    private String rawAuditText() throws Exception {
        Path auditPath = Path.of(securityProperties.getAuditFile());
        return Files.exists(auditPath) ? Files.readString(auditPath) : "";
    }

    private S3SecurityProperties.BucketRule ensureBucketRule(String bucket) {
        S3SecurityProperties.BucketRule existing = securityProperties.bucketRule(bucket);
        if (existing != null) {
            return existing;
        }
        S3SecurityProperties.BucketRule created = new S3SecurityProperties.BucketRule();
        created.setBucket(bucket);
        securityProperties.getBucketRules().add(created);
        return created;
    }

    private static void deleteRecursively(Path root) {
        if (!Files.exists(root)) {
            return;
        }
        try (var paths = Files.walk(root)) {
            paths.sorted(java.util.Comparator.reverseOrder()).forEach(path -> {
                try {
                    Files.deleteIfExists(path);
                } catch (Exception e) {
                    throw new IllegalStateException(e);
                }
            });
        } catch (Exception e) {
            throw new IllegalStateException(e);
        }
    }

    private WebTestClient client() {
        if (webTestClient == null) {
            webTestClient = WebTestClient.bindToServer()
                .baseUrl("http://localhost:" + port)
                .build();
        }
        return webTestClient;
    }

    private String objectPath(String key) {
        return "/" + currentBucket + "/" + key;
    }

    private WebTestClient.RequestBodySpec signedRequest(String method, String path, String accessKey,
                                                        String secretKey, Instant instant) {
        return signedRequest(method, path, accessKey, secretKey, instant, UNSIGNED_PAYLOAD);
    }

    private WebTestClient.RequestBodySpec signedRequest(String method, String path, String accessKey,
                                                        String secretKey, Instant instant, String payloadHash) {
        SignedHeaders signed = signedHeaders(method, path, accessKey, secretKey, instant, payloadHash);
        return client().method(org.springframework.http.HttpMethod.valueOf(method))
            .uri(path)
            .header(HttpHeaders.HOST, HOST)
            .header("X-Amz-Date", signed.amzDate())
            .header("X-Amz-Content-SHA256", signed.payloadHash())
            .header(HttpHeaders.AUTHORIZATION, signed.authorization());
    }

    private static SignedHeaders signedHeaders(String method, String path, String accessKey, String secretKey,
                                               Instant instant, String payloadHash) {
        String amzDate = DateTimeFormatter.ofPattern("yyyyMMdd'T'HHmmss'Z'").withZone(ZoneOffset.UTC).format(instant);
        String shortDate = DateTimeFormatter.BASIC_ISO_DATE.withZone(ZoneOffset.UTC).format(instant);
        String signedHeaders = "host;x-amz-content-sha256;x-amz-date";
        String canonicalRequest = String.join("\n",
            method,
            path,
            "",
            "host:" + HOST + "\n" +
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
        return new SignedHeaders(amzDate, payloadHash, authorization);
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

    private record SignedHeaders(String amzDate, String payloadHash, String authorization) {
    }
}
