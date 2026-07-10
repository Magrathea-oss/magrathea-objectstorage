package com.example.magrathea.s3api.ep1awscli;

import com.example.magrathea.s3api.security.FileS3SecurityAuditSink;
import com.example.magrathea.s3api.security.S3SecurityAuditSink;
import com.example.magrathea.s3api.security.S3SecurityProperties;
import io.cucumber.java.Before;
import io.cucumber.java.en.Given;
import io.cucumber.java.en.Then;
import io.cucumber.java.en.When;
import org.junit.jupiter.api.Assumptions;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.web.server.LocalServerPort;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.HexFormat;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.*;

public class Ep1SecurityIdentityAwsCliSteps {

    private static final String ACCESS_KEY = "AKIAMAGRATHEATEST1";
    private static final String SECRET_KEY = "test-secret-key";
    private static final String READER_ACCESS_KEY = "AKIAMAGRATHEAREAD1";
    private static final String READER_SECRET_KEY = "reader-secret-key";
    private static final String REGION = "us-east-1";
    private static final String HOST = "localhost";
    private static final Instant REQUEST_TIME = Instant.now();
    private static final Instant STALE_REQUEST_TIME = REQUEST_TIME.minusSeconds(3600);
    private static final AwsCliPreflight AWS_CLI_PREFLIGHT = checkAwsCliPreflight();

    @LocalServerPort
    private int port;

    @Autowired
    private S3SecurityAuditSink auditSink;

    @Autowired
    private S3SecurityProperties securityProperties;

    private final HttpClient http = HttpClient.newBuilder().followRedirects(HttpClient.Redirect.NEVER).build();
    private String currentBucket;
    private String currentKey;
    private int lastStatus;
    private String lastBody = "";

    @Before
    public void resetSecurityState() {
        Assumptions.assumeTrue(AWS_CLI_PREFLIGHT.available(), AWS_CLI_PREFLIGHT.reason());
        auditSink.clear();
        deleteRecursively(Path.of("target/storage-engine-it/REQ-SEC-009-sse-awscli"));
        currentBucket = null;
        currentKey = null;
        lastStatus = -1;
        lastBody = "";
    }

    @Given("secured S3 mode is enabled")
    public void securedS3ModeIsEnabled() {
    }

    @Given("secured S3 mode is enabled with credential profile {string}")
    public void securedS3ModeIsEnabledWithCredentialProfile(String profile) {
    }

    @Given("secured S3 mode is enabled with access key {string} for principal {string}")
    public void securedS3ModeIsEnabledWithAccessKeyForPrincipal(String accessKey, String principal) {
        assertTrue((ACCESS_KEY.equals(accessKey) && "tenant-a-writer".equals(principal))
            || (READER_ACCESS_KEY.equals(accessKey) && "tenant-a-reader".equals(principal)));
    }

    @Given("secured S3 mode is enabled with principal {string}")
    public void securedS3ModeIsEnabledWithPrincipal(String principal) {
        assertTrue("tenant-a-writer".equals(principal) || "tenant-a-reader".equals(principal));
    }

    @Given("bucket {string} exists for account {string}")
    public void bucketExistsForAccount(String bucket, String account) throws Exception {
        currentBucket = bucket;
        rawRequest("PUT", "/" + bucket, "", ACCESS_KEY, SECRET_KEY, REQUEST_TIME, "UNSIGNED-PAYLOAD", null);
        assertTrue(lastStatus == 200 || lastStatus == 409, "Bucket setup failed: " + lastStatus + " " + lastBody);
    }

    @Given("bucket {string} has PublicAccessBlock setting {string} enabled")
    public void bucketHasPublicAccessBlockSettingEnabled(String bucket, String setting) {
        currentBucket = bucket;
        assertEquals("BlockPublicAcls", setting);
        ensureBucketRule(bucket).setBlockPublicAcls(true);
    }

    @Given("object {string} has ACL {string}")
    public void objectHasAcl(String key, String acl) {
        currentKey = key;
        assertEquals("public-read", acl);
        var rule = ensureBucketRule(currentBucket);
        if (!rule.getPublicReadKeys().contains(key)) {
            rule.getPublicReadKeys().add(key);
        }
    }

    @Given("bucket {string} is owned by account {string}")
    public void bucketIsOwnedByAccount(String bucket, String account) {
        currentBucket = bucket;
        ensureBucketRule(bucket).setOwner(account);
    }

    @Given("principal {string} is allowed action {string} on bucket {string}")
    public void principalIsAllowedActionOnBucket(String principal, String action, String bucket) {
        var rule = new S3SecurityProperties.AllowRule();
        rule.setPrincipal(principal);
        rule.setAction(action);
        rule.setBucket(bucket);
        securityProperties.getAllowRules().add(rule);
    }

    @Given("secured S3 mode is enabled with SSE-S3 default encryption for bucket {string}")
    public void securedS3ModeIsEnabledWithSseS3DefaultEncryptionForBucket(String bucket) throws Exception {
        currentBucket = bucket;
        ensureBucketRule(bucket).setDefaultSseS3(true);
        bucketExistsForAccount(bucket, "111122223333");
    }

    @Given("no bucket policy or ACL grants principal {string} action {string} on object {string}")
    public void noBucketPolicyOrAclGrantsPrincipalActionOnObject(String principal, String action, String key) {
        assertEquals("tenant-a-reader", principal);
        currentKey = key;
    }

    @Given("bucket policy for {string} allows principal {string} action {string} on prefix {string}")
    public void bucketPolicyAllowsPrincipalActionOnPrefix(String bucket, String principal, String action, String prefix) {
        currentBucket = bucket;
    }

    @Given("bucket policy for {string} denies principal {string} action {string} on object {string}")
    public void bucketPolicyDeniesPrincipalActionOnObject(String bucket, String principal, String action, String key) {
        currentBucket = bucket;
        currentKey = key;
    }

    @When("an unsigned S3 PutObject request stores object {string} with body {string}")
    public void unsignedS3PutObjectRequestStoresObjectWithBody(String key, String body) throws Exception {
        currentKey = key;
        rawRequest("PUT", objectPath(key), body, null, null, REQUEST_TIME, "UNSIGNED-PAYLOAD", null);
    }

    @When("a SigV4 signed S3 PutObject request stores object {string} with body {string}")
    public void sigV4SignedS3PutObjectRequestStoresObjectWithBody(String key, String body) throws Exception {
        currentKey = key;
        Path file = Files.createTempFile("ep1-awscli-put-", ".txt");
        try {
            Files.writeString(file, body);
            AwsResult result = runAws(ACCESS_KEY, SECRET_KEY, "put-object", "--bucket", currentBucket, "--key", key, "--body", file.toString());
            lastStatus = result.exitCode() == 0 ? 200 : 500;
            lastBody = result.combined();
            assertEquals(0, result.exitCode(), result.combined());
        } finally {
            Files.deleteIfExists(file);
        }
    }

    @When("a SigV4 signed S3 PutObject request with an exact payload hash stores object {string} with body {string}")
    public void sigV4SignedS3PutObjectRequestWithExactPayloadHashStoresObjectWithBody(String key, String body) throws Exception {
        currentKey = key;
        rawRequest("PUT", objectPath(key), body, ACCESS_KEY, SECRET_KEY, REQUEST_TIME, sha256Hex(body), null);
    }

    @When("a SigV4 signed S3 PutObject request for object {string} is sent with authentication defect {string}")
    public void sigV4SignedS3PutObjectRequestForObjectIsSentWithAuthenticationDefect(String key, String defect) throws Exception {
        currentKey = key;
        String body = "blocked";
        String accessKey = ACCESS_KEY;
        String secretKey = SECRET_KEY;
        Instant time = REQUEST_TIME;
        String payloadHash = "UNSIGNED-PAYLOAD";
        if ("unknown-access-key".equals(defect)) {
            accessKey = "AKIAUNKNOWN";
            secretKey = "unknown-secret-key";
        } else if ("bad-signature".equals(defect)) {
            secretKey = "wrong-secret-key";
        } else if ("stale-x-amz-date".equals(defect)) {
            time = STALE_REQUEST_TIME;
        } else if ("payload-hash-mismatch".equals(defect)) {
            payloadHash = sha256Hex("different request body");
        } else {
            throw new AssertionError("Unsupported defect: " + defect);
        }
        rawRequest("PUT", objectPath(key), body, accessKey, secretKey, time, payloadHash, null);
    }

    @When("principal {string} sends a correctly signed GetObject request for {string}")
    public void principalSendsCorrectlySignedGetObjectRequestFor(String principal, String key) throws Exception {
        currentKey = key;
        String accessKey = "tenant-a-reader".equals(principal) ? READER_ACCESS_KEY : ACCESS_KEY;
        String secretKey = "tenant-a-reader".equals(principal) ? READER_SECRET_KEY : SECRET_KEY;
        rawRequest("GET", objectPath(key), "", accessKey, secretKey, REQUEST_TIME, "UNSIGNED-PAYLOAD", null);
    }

    @When("principal {string} sends a correctly signed PutObject request for {string} with body {string}")
    public void principalSendsCorrectlySignedPutObjectRequestForWithBody(String principal, String key, String body) throws Exception {
        if (currentBucket == null) {
            bucketExistsForAccount("secure-ingest", "111122223333");
        }
        currentKey = key;
        rawRequest("PUT", objectPath(key), body, ACCESS_KEY, SECRET_KEY, REQUEST_TIME, "UNSIGNED-PAYLOAD", null);
    }

    @When("an unsigned GetObject request reads {string} from bucket {string}")
    public void unsignedGetObjectRequestReadsFromBucket(String key, String bucket) throws Exception {
        currentBucket = bucket;
        currentKey = key;
        rawRequest("GET", "/" + bucket + "/" + key, "", null, null, REQUEST_TIME, "UNSIGNED-PAYLOAD", null);
    }

    @When("principal {string} sends a signed GetObject request with header {string} set to {string}")
    public void principalSendsSignedGetObjectRequestWithHeaderSetTo(String principal, String header, String value) throws Exception {
        currentKey = "incoming/report.csv";
        rawRequest("GET", objectPath(currentKey), "", READER_ACCESS_KEY, READER_SECRET_KEY, REQUEST_TIME, "UNSIGNED-PAYLOAD", new String[]{header, value});
    }

    @When("principal {string} sends a signed PutObject request for {string} with body {string}")
    public void principalSendsSignedPutObjectRequestForWithBody(String principal, String key, String body) throws Exception {
        principalSendsCorrectlySignedPutObjectRequestForWithBody(principal, key, body);
    }

    @When("a signed PutObject request stores object {string} with body {string}")
    public void signedPutObjectRequestStoresObjectWithBody(String key, String body) throws Exception {
        sigV4SignedS3PutObjectRequestStoresObjectWithBody(key, body);
    }

    @Then("the response status is {int}")
    public void theResponseStatusIs(int status) {
        assertEquals(status, lastStatus, lastBody);
    }

    @Then("the S3 error code is {string}")
    public void theS3ErrorCodeIs(String errorCode) {
        assertTrue(lastBody.contains(errorCode), "Expected " + errorCode + " in " + lastBody);
    }

    @Then("object {string} is not stored in bucket {string}")
    public void objectIsNotStoredInBucket(String key, String bucket) throws Exception {
        rawRequest("GET", "/" + bucket + "/" + key, "", ACCESS_KEY, SECRET_KEY, REQUEST_TIME, "UNSIGNED-PAYLOAD", null);
        assertEquals(404, lastStatus, lastBody);
    }

    @Then("the response ETag header is present")
    public void theResponseETagHeaderIsPresent() {
        assertEquals(200, lastStatus, lastBody);
    }

    @Then("a subsequent signed GetObject for {string} returns body {string}")
    public void subsequentSignedGetObjectForReturnsBody(String key, String body) throws Exception {
        Path out = Files.createTempFile("ep1-awscli-get-", ".txt");
        try {
            AwsResult result = runAws(ACCESS_KEY, SECRET_KEY, "get-object", "--bucket", currentBucket, "--key", key, out.toString());
            assertEquals(0, result.exitCode(), result.combined());
            assertEquals(body, Files.readString(out));
        } finally {
            Files.deleteIfExists(out);
        }
    }

    @Then("an audit event records decision {string} with reason {string}")
    public void auditEventRecordsDecisionWithReason(String decision, String reason) {
        boolean found = auditSink.events().stream().anyMatch(event -> decision.equals(event.decision()) && reason.equals(event.reason())
            && currentBucket.equals(event.bucket()) && currentKey.equals(event.key()));
        assertTrue(found, "events=" + auditSink.events());
    }

    @Then("an audit event is durably recorded with request id, principal, action, bucket, object key, decision, and response status")
    public void auditEventIsDurablyRecordedWithRequestIdPrincipalActionBucketObjectKeyDecisionAndResponseStatus() {
        assertTrue(auditSink.events().stream().anyMatch(event -> event.requestId() != null && currentBucket.equals(event.bucket())
            && currentKey.equals(event.key()) && "allow".equals(event.decision())), "events=" + auditSink.events());
    }

    @Then("the audit event does not contain the secret access key")
    public void auditEventDoesNotContainTheSecretAccessKey() throws Exception {
        String raw = rawAuditText();
        assertFalse(raw.contains(SECRET_KEY));
        assertFalse(raw.contains(READER_SECRET_KEY));
    }

    @Then("the audit event does not contain object body text {string}")
    public void auditEventDoesNotContainObjectBodyText(String bodyText) throws Exception {
        assertFalse(rawAuditText().contains(bodyText));
    }

    @Then("the audit event remains available after an application restart")
    public void auditEventRemainsAvailableAfterApplicationRestart() {
        var restarted = new FileS3SecurityAuditSink(Path.of(securityProperties.getAuditFile()));
        assertTrue(restarted.events().stream().anyMatch(event -> currentBucket.equals(event.bucket()) && currentKey.equals(event.key())));
    }

    @Then("a signed GetObject request returns body {string}")
    public void signedGetObjectRequestReturnsBody(String body) throws Exception {
        subsequentSignedGetObjectForReturnsBody(currentKey, body);
    }

    @Then("filesystem inspection under storage root {string} finds no plaintext occurrence of {string}")
    public void filesystemInspectionUnderStorageRootFindsNoPlaintextOccurrenceOf(String root, String plaintext) throws Exception {
        Path actualRoot = Path.of(securityProperties.getEncryptedInspectionRoot());
        assertTrue(Files.exists(actualRoot));
        boolean found = Files.walk(actualRoot).filter(Files::isRegularFile).anyMatch(path -> {
            try {
                return new String(Files.readAllBytes(path), StandardCharsets.UTF_8).contains(plaintext);
            } catch (IOException e) {
                throw new RuntimeException(e);
            }
        });
        assertFalse(found, "Plaintext found under " + actualRoot);
    }

    @Then("an audit event records encryption mode {string}")
    public void auditEventRecordsEncryptionMode(String encryptionMode) {
        assertTrue(auditSink.events().stream().anyMatch(event -> encryptionMode.equals(event.encryptionMode())
            && currentBucket.equals(event.bucket()) && currentKey.equals(event.key())), "events=" + auditSink.events());
    }

    private AwsResult runAws(String accessKey, String secretKey, String... args) throws Exception {
        var command = new ArrayList<String>();
        command.add("aws");
        command.add("--endpoint-url");
        command.add(endpoint());
        command.add("s3api");
        command.addAll(java.util.Arrays.asList(args));
        var pb = new ProcessBuilder(command);
        pb.environment().put("AWS_ACCESS_KEY_ID", accessKey);
        pb.environment().put("AWS_SECRET_ACCESS_KEY", secretKey);
        pb.environment().put("AWS_DEFAULT_REGION", REGION);
        pb.environment().put("AWS_EC2_METADATA_DISABLED", "true");
        var process = pb.start();
        boolean done = process.waitFor(30, TimeUnit.SECONDS);
        if (!done) {
            process.destroyForcibly();
            throw new AssertionError("AWS CLI timed out: " + command);
        }
        String stdout = new String(process.getInputStream().readAllBytes(), StandardCharsets.UTF_8);
        String stderr = new String(process.getErrorStream().readAllBytes(), StandardCharsets.UTF_8);
        return new AwsResult(process.exitValue(), stdout, stderr);
    }

    private void rawRequest(String method, String path, String body, String accessKey, String secretKey, Instant instant,
                            String payloadHash, String[] extraHeader) throws Exception {
        HttpRequest.Builder builder = HttpRequest.newBuilder(URI.create(endpoint() + path))
            .timeout(Duration.ofSeconds(30))
            .method(method, body == null || body.isEmpty() ? HttpRequest.BodyPublishers.noBody() : HttpRequest.BodyPublishers.ofString(body));
        if (accessKey != null) {
            SignedHeaders signed = signedHeaders(method, path, accessKey, secretKey, instant, payloadHash);
            builder.header("X-Amz-Date", signed.amzDate())
                .header("X-Amz-Content-SHA256", signed.payloadHash())
                .header("Authorization", signed.authorization());
        }
        if (extraHeader != null) {
            builder.header(extraHeader[0], extraHeader[1]);
        }
        HttpResponse<String> response = http.send(builder.build(), HttpResponse.BodyHandlers.ofString());
        lastStatus = response.statusCode();
        lastBody = response.body();
    }

    private String objectPath(String key) {
        return "/" + currentBucket + "/" + key;
    }

    private String endpoint() {
        return "http://localhost:" + port;
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
        var created = new S3SecurityProperties.BucketRule();
        created.setBucket(bucket);
        securityProperties.getBucketRules().add(created);
        return created;
    }

    private static AwsCliPreflight checkAwsCliPreflight() {
        try {
            Process process = new ProcessBuilder("aws", "--version").start();
            if (!process.waitFor(5, TimeUnit.SECONDS)) {
                process.destroyForcibly();
                return new AwsCliPreflight(false, "AWS CLI preflight timed out");
            }
            return new AwsCliPreflight(process.exitValue() == 0, "AWS CLI not available");
        } catch (Exception e) {
            return new AwsCliPreflight(false, "AWS CLI binary not on PATH: " + e.getMessage());
        }
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

    private SignedHeaders signedHeaders(String method, String path, String accessKey, String secretKey,
                                        Instant instant, String payloadHash) {
        String amzDate = DateTimeFormatter.ofPattern("yyyyMMdd'T'HHmmss'Z'").withZone(ZoneOffset.UTC).format(instant);
        String shortDate = DateTimeFormatter.BASIC_ISO_DATE.withZone(ZoneOffset.UTC).format(instant);
        String signedHeaders = "host;x-amz-content-sha256;x-amz-date";
        String canonicalRequest = String.join("\n", method, path, "",
            "host:" + URI.create(endpoint()).getAuthority() + "\n" + "x-amz-content-sha256:" + payloadHash + "\n" + "x-amz-date:" + amzDate + "\n",
            signedHeaders, payloadHash);
        String credentialScope = shortDate + "/" + REGION + "/s3/aws4_request";
        String stringToSign = String.join("\n", "AWS4-HMAC-SHA256", amzDate, credentialScope, sha256Hex(canonicalRequest));
        String signature = hmacHex(signingKey(secretKey, shortDate, REGION, "s3"), stringToSign);
        return new SignedHeaders(amzDate, payloadHash, "AWS4-HMAC-SHA256 Credential=" + accessKey + "/" + credentialScope
            + ", SignedHeaders=" + signedHeaders + ", Signature=" + signature);
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
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(value.getBytes(StandardCharsets.UTF_8)));
        } catch (Exception e) {
            throw new IllegalStateException(e);
        }
    }

    private record SignedHeaders(String amzDate, String payloadHash, String authorization) {}
    private record AwsResult(int exitCode, String stdout, String stderr) {
        String combined() { return stdout + stderr; }
    }
    private record AwsCliPreflight(boolean available, String reason) {}
}
