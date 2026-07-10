package com.example.magrathea.s3api.security;

import io.cucumber.java.Before;
import io.cucumber.java.en.Given;
import io.cucumber.java.en.Then;
import io.cucumber.java.en.When;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

public class Ep1SecurityServicesSpecSteps {

    private LocalS3KeyManagementService keyManagementService;
    private S3CredentialStore credentialStore;
    private DurableS3SecurityPolicyStore policyStore;
    private S3SecurityAuthorizer authorizer;
    private FileS3SecurityAuditSink auditSink;
    private byte[] encryptedBytes;
    private String bucket;
    private String key;

    @Before
    public void cleanSpecState() throws Exception {
        deleteRecursively(Path.of("target/security-services-spec"));
    }

    @Given("a local security master key file {string}")
    public void aLocalSecurityMasterKeyFile(String keyFile) {
        keyManagementService = new LocalS3KeyManagementService(Path.of(keyFile));
    }

    @Given("a durable credential store file {string} contains access key {string} for principal {string} with secret {string}")
    public void aDurableCredentialStoreFileContainsAccessKeyForPrincipalWithSecret(String file, String accessKey,
                                                                                    String principal, String secret) {
        S3SecurityProperties properties = new S3SecurityProperties();
        properties.setCredentialFile(file);
        credentialStore = new S3CredentialStore(properties, keyManagementService);
        credentialStore.upsertCredential(accessKey, secret, principal, false);
    }

    @When("the durable credential store is reloaded")
    public void theDurableCredentialStoreIsReloaded() {
        S3SecurityProperties properties = new S3SecurityProperties();
        properties.setCredentialFile(Path.of("target/security-services-spec/credentials.tsv").toString());
        credentialStore = new S3CredentialStore(properties, keyManagementService);
    }

    @Then("access key {string} resolves to principal {string} and secret {string}")
    public void accessKeyResolvesToPrincipalAndSecret(String accessKey, String principal, String secret) {
        S3CredentialStore.Credential credential = credentialStore.findByAccessKey(accessKey).orElseThrow();
        assertEquals(principal, credential.principal());
        assertEquals(secret, credential.secretKey());
    }

    @Then("the credential store file does not contain plaintext secret {string}")
    public void theCredentialStoreFileDoesNotContainPlaintextSecret(String secret) throws Exception {
        String raw = Files.readString(Path.of("target/security-services-spec/credentials.tsv"));
        assertFalse(raw.contains(secret), "credential file must not contain plaintext secret");
    }

    @When("access key {string} is revoked and the durable credential store is reloaded")
    public void accessKeyIsRevokedAndTheDurableCredentialStoreIsReloaded(String accessKey) {
        credentialStore.revoke(accessKey);
        theDurableCredentialStoreIsReloaded();
    }

    @Then("access key {string} is rejected as revoked")
    public void accessKeyIsRejectedAsRevoked(String accessKey) {
        assertTrue(credentialStore.findByAccessKey(accessKey).isEmpty());
        assertTrue(credentialStore.findIncludingRevoked(accessKey).orElseThrow().revoked());
    }

    @Given("a durable policy store file {string}")
    public void aDurablePolicyStoreFile(String file) {
        policyStore = new DurableS3SecurityPolicyStore(Path.of(file));
    }

    @Given("the policy store allows principal {string} action {string} on bucket {string} prefix {string}")
    public void thePolicyStoreAllowsPrincipalActionOnBucketPrefix(String principal, String action, String bucket, String prefix) {
        policyStore.append(new DurableS3SecurityPolicyStore.PolicyRule("allow", principal, action, bucket, prefix));
    }

    @Given("the policy store denies principal {string} action {string} on bucket {string} prefix {string}")
    public void thePolicyStoreDeniesPrincipalActionOnBucketPrefix(String principal, String action, String bucket, String prefix) {
        policyStore.append(new DurableS3SecurityPolicyStore.PolicyRule("deny", principal, action, bucket, prefix));
    }

    @When("the durable policy store is reloaded")
    public void theDurablePolicyStoreIsReloaded() {
        S3SecurityProperties properties = new S3SecurityProperties();
        policyStore = new DurableS3SecurityPolicyStore(Path.of("target/security-services-spec/policies.tsv"));
        authorizer = new S3SecurityAuthorizer(properties, policyStore);
    }

    @Then("authorization allows principal {string} action {string} on object {string} in bucket {string}")
    public void authorizationAllowsPrincipalActionOnObjectInBucket(String principal, String action, String key, String bucket) {
        S3SecurityAuthorizer.AuthorizationResult result = authorizer.authorize(principal, action, bucket, key);
        assertTrue(result.allowed(), "expected allow but got " + result);
    }

    @Then("authorization denies principal {string} action {string} on object {string} in bucket {string} with reason {string}")
    public void authorizationDeniesPrincipalActionOnObjectInBucketWithReason(String principal, String action, String key,
                                                                              String bucket, String reason) {
        S3SecurityAuthorizer.AuthorizationResult result = authorizer.authorize(principal, action, bucket, key);
        assertFalse(result.allowed(), "expected deny but got " + result);
        assertEquals(reason, result.reason());
    }

    @Given("a durable audit file {string}")
    public void aDurableAuditFile(String file) {
        auditSink = new FileS3SecurityAuditSink(Path.of(file));
    }

    @When("an audit event is recorded for principal {string} action {string} bucket {string} key {string}")
    public void anAuditEventIsRecordedForPrincipalActionBucketKey(String principal, String action, String bucket, String key) {
        this.bucket = bucket;
        this.key = key;
        auditSink.record(new S3SecurityAuditSink.AuditEvent(
            Instant.now(), "request-1", principal, action, bucket, key, "allow", "allowing-policy", 200, "SSE-S3"));
    }

    @Then("the audit event is readable after audit sink reload")
    public void theAuditEventIsReadableAfterAuditSinkReload() {
        auditSink = new FileS3SecurityAuditSink(Path.of("target/security-services-spec/audit.tsv"));
        assertTrue(auditSink.events().stream().anyMatch(event -> bucket.equals(event.bucket()) && key.equals(event.key())));
    }

    @Then("the durable audit file integrity check passes")
    public void theDurableAuditFileIntegrityCheckPasses() {
        assertTrue(auditSink.verifyIntegrity());
    }

    @When("the durable audit file is tampered with")
    public void theDurableAuditFileIsTamperedWith() throws Exception {
        Path file = Path.of("target/security-services-spec/audit.tsv");
        String raw = Files.readString(file);
        Files.writeString(file, raw.replace("allowing-policy", "tampered-policy"));
        // Base64 fields hide the reason text, so also append a byte-level mutation when text replacement was a no-op.
        if (raw.equals(Files.readString(file))) {
            Files.writeString(file, raw.replaceFirst("[A-Za-z0-9+/]", "A"));
        }
    }

    @Then("the durable audit file integrity check fails")
    public void theDurableAuditFileIntegrityCheckFails() {
        assertFalse(auditSink.verifyIntegrity());
    }

    @When("object bytes {string} are encrypted for bucket {string} key {string}")
    public void objectBytesAreEncryptedForBucketKey(String plaintext, String bucket, String key) {
        this.bucket = bucket;
        this.key = key;
        encryptedBytes = keyManagementService.encryptObject(bucket, key, plaintext.getBytes(StandardCharsets.UTF_8));
    }

    @Then("the encrypted bytes differ from plaintext {string}")
    public void theEncryptedBytesDifferFromPlaintext(String plaintext) {
        assertFalse(java.util.Arrays.equals(plaintext.getBytes(StandardCharsets.UTF_8), encryptedBytes));
    }

    @Then("a restarted key-management service decrypts the bytes to {string}")
    public void aRestartedKeyManagementServiceDecryptsTheBytesTo(String plaintext) {
        LocalS3KeyManagementService restarted = new LocalS3KeyManagementService(keyManagementService.keyFile());
        assertArrayEquals(plaintext.getBytes(StandardCharsets.UTF_8), restarted.decryptObject(bucket, key, encryptedBytes));
    }

    private static void deleteRecursively(Path root) throws Exception {
        if (!Files.exists(root)) {
            return;
        }
        try (var paths = Files.walk(root)) {
            for (Path path : paths.sorted(java.util.Comparator.reverseOrder()).toList()) {
                Files.deleteIfExists(path);
            }
        }
    }
}
