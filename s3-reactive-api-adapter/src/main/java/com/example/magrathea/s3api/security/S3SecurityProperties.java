package com.example.magrathea.s3api.security;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.util.ArrayList;
import java.util.List;

@ConfigurationProperties(prefix = "s3.security")
public class S3SecurityProperties {

    private boolean enabled = false;
    private String region = "us-east-1";
    private long allowedClockSkewSeconds = 900;
    private List<Credential> credentials = new ArrayList<>();
    private List<AllowRule> allowRules = new ArrayList<>();
    private List<AllowRule> denyRules = new ArrayList<>();
    private List<BucketRule> bucketRules = new ArrayList<>();
    private String credentialFile;
    private String policyFile;
    private String keyFile;
    private String auditFile;
    private String encryptedInspectionRoot;

    public boolean isEnabled() {
        return enabled;
    }

    public void setEnabled(boolean enabled) {
        this.enabled = enabled;
    }

    public String getRegion() {
        return region;
    }

    public void setRegion(String region) {
        this.region = region;
    }

    public long getAllowedClockSkewSeconds() {
        return allowedClockSkewSeconds;
    }

    public void setAllowedClockSkewSeconds(long allowedClockSkewSeconds) {
        this.allowedClockSkewSeconds = allowedClockSkewSeconds;
    }

    public List<Credential> getCredentials() {
        return credentials;
    }

    public void setCredentials(List<Credential> credentials) {
        this.credentials = credentials == null ? new ArrayList<>() : credentials;
    }

    public List<AllowRule> getAllowRules() {
        return allowRules;
    }

    public void setAllowRules(List<AllowRule> allowRules) {
        this.allowRules = allowRules == null ? new ArrayList<>() : allowRules;
    }

    public List<AllowRule> getDenyRules() {
        return denyRules;
    }

    public void setDenyRules(List<AllowRule> denyRules) {
        this.denyRules = denyRules == null ? new ArrayList<>() : denyRules;
    }

    public List<BucketRule> getBucketRules() {
        return bucketRules;
    }

    public void setBucketRules(List<BucketRule> bucketRules) {
        this.bucketRules = bucketRules == null ? new ArrayList<>() : bucketRules;
    }

    public String getCredentialFile() {
        return credentialFile;
    }

    public void setCredentialFile(String credentialFile) {
        this.credentialFile = credentialFile;
    }

    public String getPolicyFile() {
        return policyFile;
    }

    public void setPolicyFile(String policyFile) {
        this.policyFile = policyFile;
    }

    public String getKeyFile() {
        return keyFile;
    }

    public void setKeyFile(String keyFile) {
        this.keyFile = keyFile;
    }

    public String getAuditFile() {
        return auditFile;
    }

    public void setAuditFile(String auditFile) {
        this.auditFile = auditFile;
    }

    public String getEncryptedInspectionRoot() {
        return encryptedInspectionRoot;
    }

    public void setEncryptedInspectionRoot(String encryptedInspectionRoot) {
        this.encryptedInspectionRoot = encryptedInspectionRoot;
    }

    public static class Credential {
        private String accessKey;
        private String secretKey;
        private String principal;
        private boolean revoked;

        public String getAccessKey() {
            return accessKey;
        }

        public void setAccessKey(String accessKey) {
            this.accessKey = accessKey;
        }

        public String getSecretKey() {
            return secretKey;
        }

        public void setSecretKey(String secretKey) {
            this.secretKey = secretKey;
        }

        public String getPrincipal() {
            return principal;
        }

        public void setPrincipal(String principal) {
            this.principal = principal;
        }

        public boolean isRevoked() {
            return revoked;
        }

        public void setRevoked(boolean revoked) {
            this.revoked = revoked;
        }
    }

    public BucketRule bucketRule(String bucket) {
        return bucketRules.stream()
            .filter(rule -> rule.getBucket() != null && rule.getBucket().equals(bucket))
            .findFirst()
            .orElse(null);
    }

    public static class AllowRule {
        private String principal;
        private String action;
        private String bucket;
        private String keyPrefix = "";

        public String getPrincipal() {
            return principal;
        }

        public void setPrincipal(String principal) {
            this.principal = principal;
        }

        public String getAction() {
            return action;
        }

        public void setAction(String action) {
            this.action = action;
        }

        public String getBucket() {
            return bucket;
        }

        public void setBucket(String bucket) {
            this.bucket = bucket;
        }

        public String getKeyPrefix() {
            return keyPrefix;
        }

        public void setKeyPrefix(String keyPrefix) {
            this.keyPrefix = keyPrefix == null ? "" : keyPrefix;
        }

        boolean matches(String candidatePrincipal, String candidateAction, String candidateBucket, String candidateKey) {
            return matchesValue(principal, candidatePrincipal)
                && matchesValue(action, candidateAction)
                && matchesValue(bucket, candidateBucket)
                && (keyPrefix == null || keyPrefix.isBlank() || candidateKey.startsWith(keyPrefix));
        }

        private static boolean matchesValue(String configured, String actual) {
            return "*".equals(configured) || (configured != null && configured.equals(actual));
        }
    }

    public static class BucketRule {
        private String bucket;
        private String owner;
        private boolean blockPublicAcls;
        private List<String> publicReadKeys = new ArrayList<>();
        private boolean defaultSseS3;

        public String getBucket() {
            return bucket;
        }

        public void setBucket(String bucket) {
            this.bucket = bucket;
        }

        public String getOwner() {
            return owner;
        }

        public void setOwner(String owner) {
            this.owner = owner;
        }

        public boolean isBlockPublicAcls() {
            return blockPublicAcls;
        }

        public void setBlockPublicAcls(boolean blockPublicAcls) {
            this.blockPublicAcls = blockPublicAcls;
        }

        public List<String> getPublicReadKeys() {
            return publicReadKeys;
        }

        public void setPublicReadKeys(List<String> publicReadKeys) {
            this.publicReadKeys = publicReadKeys == null ? new ArrayList<>() : publicReadKeys;
        }

        public boolean isDefaultSseS3() {
            return defaultSseS3;
        }

        public void setDefaultSseS3(boolean defaultSseS3) {
            this.defaultSseS3 = defaultSseS3;
        }
    }
}
