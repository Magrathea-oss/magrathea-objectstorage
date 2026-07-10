@spec @phase-ep1 @security @identity @audit @encryption
Ability: EP-1 production security backing services
  Maintainers need durable local security services behind the Spring Security
  Reactive S3 boundary so that the validated S3 authentication, authorization,
  audit, and SSE behavior is not backed only by test properties or in-memory
  spike components.

  Rule: Access keys are stored durably and can be revoked

    @REQ-SEC-010 @functional-requirement @security @identity @credential-store @component-spec-required @implemented-and-validated
    Scenario: Durable encrypted access-key store survives reload and rejects a revoked key
      Given a local security master key file "target/security-services-spec/master.key"
      And a durable credential store file "target/security-services-spec/credentials.tsv" contains access key "AKIAPRODUCTION1" for principal "tenant-prod-writer" with secret "prod-secret-key"
      When the durable credential store is reloaded
      Then access key "AKIAPRODUCTION1" resolves to principal "tenant-prod-writer" and secret "prod-secret-key"
      And the credential store file does not contain plaintext secret "prod-secret-key"
      When access key "AKIAPRODUCTION1" is revoked and the durable credential store is reloaded
      Then access key "AKIAPRODUCTION1" is rejected as revoked

  Rule: Authorization policy is durable and explicit deny remains authoritative

    @REQ-SEC-011 @functional-requirement @security @authorization @bucket-policy @component-spec-required @implemented-and-validated
    Scenario: Durable policy store reload preserves allow and explicit deny decisions
      Given a durable policy store file "target/security-services-spec/policies.tsv"
      And the policy store allows principal "tenant-prod-writer" action "s3:PutObject" on bucket "secure-prod" prefix "incoming/"
      And the policy store denies principal "tenant-prod-writer" action "s3:PutObject" on bucket "secure-prod" prefix "incoming/blocked.csv"
      When the durable policy store is reloaded
      Then authorization allows principal "tenant-prod-writer" action "s3:PutObject" on object "incoming/report.csv" in bucket "secure-prod"
      And authorization denies principal "tenant-prod-writer" action "s3:PutObject" on object "incoming/blocked.csv" in bucket "secure-prod" with reason "explicit-deny"

  Rule: Audit files are durable, redacted, and tamper evident

    @REQ-SEC-012 @functional-requirement @non-functional-requirement @security @audit @observability @component-spec-required @implemented-and-validated
    Scenario: Durable audit log survives reload and detects tampering
      Given a durable audit file "target/security-services-spec/audit.tsv"
      When an audit event is recorded for principal "tenant-prod-writer" action "s3:PutObject" bucket "secure-prod" key "incoming/report.csv"
      Then the audit event is readable after audit sink reload
      And the durable audit file integrity check passes
      When the durable audit file is tampered with
      Then the durable audit file integrity check fails

  Rule: SSE uses durable key-management material instead of embedded constants

    @REQ-SEC-013 @functional-requirement @non-functional-requirement @security @encryption @sse @key-management @component-spec-required @implemented-and-validated
    Scenario: Durable key-management service encrypts and decrypts object bytes after restart
      Given a local security master key file "target/security-services-spec/sse-master.key"
      When object bytes "sensitive production payload" are encrypted for bucket "secure-prod" key "incoming/report.csv"
      Then the encrypted bytes differ from plaintext "sensitive production payload"
      And a restarted key-management service decrypts the bytes to "sensitive production payload"
