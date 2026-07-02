@webclient
Feature: S3-compatible PutObject API — Anomaly Tests (Analysis-Complete)

  Background:
    Given bucket "test-bucket" exists

  # ── Success scenarios (current working behavior) ──

  @webclient
  Scenario: Put an object
    Given an object with key "hello.txt" and content "Hello Magrathea!"
    When the object is stored via S3 API
    Then the response status is 200
    And the object appears in the object list

  @webclient
  Scenario: Put, read, list, and delete an object with a key containing slashes
    Given an object with key "folder/webclient-slash.txt" and content "nested WebTestClient key"
    When the object is stored via S3 API using an explicit slash-preserving URI
    Then the response status is 200
    When the object with key "folder/webclient-slash.txt" is retrieved via S3 API using an explicit slash-preserving URI
    Then the response status is 200
    And the content is "nested WebTestClient key"
    When the objects are listed via S3 API V2
    Then the response status is 200
    And the object appears in the object list V2
    When the object with key "folder/webclient-slash.txt" is deleted via S3 API using an explicit slash-preserving URI
    Then the response status is 204
    And object "folder/webclient-slash.txt" does not appear in the object list

  @webclient
  Scenario: Put an object with PARANOIC_MODE storage class
    Given an object with key "paranoid.txt" and content "Top secret"
    When the object is stored via S3 API with storage class "PARANOIC_MODE"
    Then the response status is 200
    And the object appears in the object list

  # ── Anomaly tests — AWS API Compliance Gaps ──

  # F4: Metadata headers ignored
  @webclient
  Scenario: Put object stores user metadata headers
    Given an object with key "meta-test.txt" and content "data"
    When the object is stored via S3 API with metadata header "x-amz-meta-project" value "magrathea"
    Then the response status is 200
    And the object metadata returned by HEAD contains header "x-amz-meta-project" with value "magrathea"

  # F5: SSE headers ignored — server-side encryption
  @webclient
  Scenario: Put object stores SSE encryption header
    Given an object with key "sse-test.txt" and content "data"
    When the object is stored via S3 API with header "x-amz-server-side-encryption" value "AES256"
    Then the response status is 200
    And the object metadata returned by HEAD contains header "x-amz-server-side-encryption" with value "AES256"

  # F5: SSE-C customer algorithm ignored
  @webclient
  Scenario: Put object stores SSE-C customer algorithm
    Given an object with key "ssec-test.txt" and content "data"
    When the object is stored via S3 API with header "x-amz-server-side-encryption-customer-algorithm" value "AES256"
    Then the response status is 200
    And the object metadata returned by HEAD contains header "x-amz-server-side-encryption-customer-algorithm" with value "AES256"

  # M4: x-amz-checksum-sha256 header ignored
  @webclient
  Scenario: Put object stores checksum SHA256 header
    Given an object with key "checksum-test.txt" and content "data"
    When the object is stored via S3 API with header "x-amz-checksum-sha256" value "deadbeef1234567890abcdef1234567890"
    Then the response status is 200
    And the object metadata returned by HEAD contains header "x-amz-checksum-sha256" with value "deadbeef1234567890abcdef1234567890"

  # M5: x-amz-version-id — not present for bucket without versioning
  @webclient
  Scenario: Put object does not return version-id when versioning disabled
    Given an object with key "version-test.txt" and content "data"
    When the object is stored via S3 API
    Then the response status is 200
    And the response header "x-amz-version-id" is absent

  # M1: x-amz-expected-bucket-owner ignored
  @webclient
  Scenario: Put object with expected bucket owner
    Given an object with key "owner-test.txt" and content "data"
    When the object is stored via S3 API with header "x-amz-expected-bucket-owner" value "expected-owner"
    Then the response status is 200

  # AWS CLI default headers — handler ignores these
  @webclient
  Scenario: Put object ignores x-amz-checksum-crc64nvme (AWS CLI v2 default)
    Given an object with key "crc64-test.txt" and content "data"
    When the object is stored via S3 API with header "x-amz-checksum-crc64nvme" value "crc64value"
    Then the response status is 200
    And the object metadata returned by HEAD contains header "x-amz-checksum-crc64nvme" with value "crc64value"

  @webclient
  Scenario: Put object ignores x-amz-sdk-checksum-algorithm (AWS CLI v2 default)
    Given an object with key "sdk-checksum-test.txt" and content "data"
    When the object is stored via S3 API with header "x-amz-sdk-checksum-algorithm" value "crc64nvme"
    Then the response status is 200
    And the object metadata returned by HEAD contains header "x-amz-sdk-checksum-algorithm" with value "crc64nvme"

  @webclient
  Scenario: Put object ignores X-Amz-Date (SigV4 — not validated)
    Given an object with key "date-test.txt" and content "data"
    When the object is stored via S3 API with header "X-Amz-Date" value "20260527T000000Z"
    Then the response status is 200
    # SigV4 validation is NOT IMPLEMENTED — handler accepts any date

  @webclient
  Scenario: Put object ignores X-Amz-Content-SHA256 (SigV4 — not validated)
    Given an object with key "sha-test.txt" and content "data"
    When the object is stored via S3 API with header "X-Amz-Content-SHA256" value "unhashedpayload"
    Then the response status is 200
    # SigV4 validation is NOT IMPLEMENTED — handler accepts any SHA256

  @webclient
  Scenario: Put object ignores Authorization header (SigV4 — not validated)
    Given an object with key "auth-test.txt" and content "data"
    When the object is stored via S3 API with header "Authorization" value "AWS4-HMAC-SHA256 Credential=.../s3/aws4_request"
    Then the response status is 200
    # SigV4 validation is NOT IMPLEMENTED — handler accepts any Authorization value

  @webclient
  Scenario: Put object ignores User-Agent header
    Given an object with key "ua-test.txt" and content "data"
    When the object is stored via S3 API with header "User-Agent" value "aws-sdk-v2"
    Then the response status is 200

  @webclient
  Scenario: Put object ignores Expect header (100-continue)
    Given an object with key "expect-test.txt" and content "data"
    When the object is stored via S3 API with header "Expect" value "100-continue"
    Then the response status is 200

  # ── Checksum header tests ──

  @webclient
  Scenario: Put object with Content-MD5 header stores it as metadata while ETag is the MD5 hex of the stored bytes
    # Since the single-pass streaming upload refactor, the ETag is always the MD5 hex
    # digest computed from the actual stored body bytes (S3-compatible semantics,
    # see REQ-UPLOAD-006). The client-supplied Content-MD5 header is retained as
    # object metadata and echoed by HEAD, but it is never echoed back as the ETag.
    # MD5("data") = 8d777f385d3dfec8815d20f7496026dc.
    Given an object with key "content-md5-test.txt" and content "data"
    When the object is stored via S3 API with Content-MD5 header "dGVzdA=="
    Then the response status is 200
    And the response ETag header is "\"8d777f385d3dfec8815d20f7496026dc\""
    And the object metadata returned by HEAD contains header "Content-MD5" with value "dGVzdA=="

  @webclient
  Scenario: Put object without any checksum header succeeds (200 OK)
    Given an object with key "no-checksum-test.txt" and content "data"
    When the object is stored via S3 API without checksum headers
    Then the response status is 200
    And the object appears in the object list

  @webclient
  Scenario: Put object with direct x-amz-checksum header (no SDK hint) succeeds and echoes in HEAD
    Given an object with key "direct-checksum-test.txt" and content "data"
    When the object is stored via S3 API with direct checksum header "x-amz-checksum-crc32c" value "AAAAAA=="
    Then the response status is 200
    And the object metadata returned by HEAD contains header "x-amz-checksum-crc32c" with value "AAAAAA=="

  # ── Anomaly tests — DDD Compliance Violations ──

  # D1: Handler now delegates to service for putObject aggregate creation.
  # The handler generates the S3Object.Id and passes it to objectService.saveObjectWithContent(),
  # which creates the aggregate via S3Object.create().
  # For copyObject, the handler still creates a CreatingS3Object directly (partial D1).
  @webclient
  Scenario: Handler delegates to service for object creation — D1 resolved for putObject
    Given an object with key "d1-resolved.txt" and content "data"
    When the object is stored via S3 API with multiple headers
      | x-amz-storage-class | STANDARD |
      | x-amz-acl | public-read |
      | x-amz-meta-custom | test-value |
    Then the response status is 200
    # The handler delegates to objectService.saveObjectWithContent() which creates the aggregate.
    # Service validates storage class and metadata before creating the domain object.
    And the handler delegates to service for aggregate creation — verified by code review

  # ── G13: SSE-KMS scenario ──

  @webclient
  Scenario: PutObject with SSE-KMS
    Given an object with key "sse-kms-test.txt" and content "data"
    When the object is stored via S3 API with SSE header "x-amz-server-side-encryption" value "aws:kms" and KMS key "test-key"
    Then the response status is 200
    And HEAD response contains SSE header "x-amz-server-side-encryption" with value "aws:kms"
    And HEAD response contains SSE header "x-amz-server-side-encryption-aws-kms-key-id" with value "test-key"

  # ── G13: Storage class STANDARD scenario ──

  @webclient
  Scenario: PutObject with storage class STANDARD
    Given an object with key "standard-test.txt" and content "data"
    When the object is stored via S3 API with storage class "STANDARD"
    Then the response status is 200
    And the object attributes returned include storage class "STANDARD"

  # ── G13: Checksum echo in GET scenario ──

  @webclient
  Scenario: PutObject with checksum echo in GET
    Given an object with key "checksum-get-test.txt" and content "data"
    When the object is stored via S3 API with header "x-amz-checksum-sha256" value "deadbeef"
    Then the response status is 200
    And GET response contains checksum header "x-amz-checksum-sha256" with value "deadbeef"

  # ── Failure scenarios ──

  @webclient
  Scenario: Copy object with metadata directive returns 200
    Given object "copy-source.txt" exists with content "Copy source data"
    When the object with key "copy-source.txt" is copied to "copy-target.txt" with metadata "REPLACE"
    Then the response status is 200

  # ── State machine operation scenarios ──

  @webclient
  Scenario: Lock an existing object
    Given object "lock-me.txt" exists with content "Lockable data"
    When object "lock-me.txt" is locked via S3 API with mode "COMPLIANCE" and duration 30 days
    Then the response status is 200

  @webclient
  Scenario: Archive an existing object
    Given object "archive-me.txt" exists with content "Archivable data"
    When object "archive-me.txt" is archived via S3 API
    Then the response status is 200

  # ── S3Object write state machine integration ──

  @webclient
  Scenario: S3Object lifecycle — create, read, overwrite, delete
    Given an object with key "sm-lifecycle.txt" and content "First version"
    When the object is stored via S3 API
    Then the response status is 200
    When HEAD request is sent for object "sm-lifecycle.txt"
    Then the response status is 200
    When the object with key "sm-lifecycle.txt" is retrieved via S3 API
    Then the response status is 200
    And the content is "First version"
    When the object is stored via S3 API with header "x-amz-checksum-sha256" value "overwrite-hash"
    Then the response status is 200
    When HEAD request is sent for object "sm-lifecycle.txt"
    Then the response status is 200
    When the object is deleted via S3 API
    Then the response status is 204
    When HEAD request is sent for object "sm-lifecycle.txt"
    Then the response status is 404
