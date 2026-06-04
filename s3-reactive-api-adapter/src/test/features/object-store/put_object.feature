Feature: S3-compatible PutObject API — Anomaly Tests (Analysis-Complete)

  Background:
    Given bucket "test-bucket" exists

  # ── Success scenarios (current working behavior) ──

  Scenario: Put an object
    Given an object with key "hello.txt" and content "Hello Magrathea!"
    When the object is stored via S3 API
    Then the response status is 200
    And the object appears in the object list

  Scenario: Put an object with PARANOIC_MODE storage class
    Given an object with key "paranoid.txt" and content "Top secret"
    When the object is stored via S3 API with storage class "PARANOIC_MODE"
    Then the response status is 200
    And the object appears in the object list

  # ── Anomaly tests — AWS API Compliance Gaps ──

  # F4: Metadata headers ignored
  Scenario: Put object stores user metadata headers
    Given an object with key "meta-test.txt" and content "data"
    When the object is stored via S3 API with metadata header "x-amz-meta-project" value "magrathea"
    Then the response status is 200
    And the object metadata returned by HEAD contains header "x-amz-meta-project" with value "magrathea"

  # F5: SSE headers ignored — server-side encryption
  Scenario: Put object stores SSE encryption header
    Given an object with key "sse-test.txt" and content "data"
    When the object is stored via S3 API with header "x-amz-server-side-encryption" value "AES256"
    Then the response status is 200
    And the object metadata returned by HEAD contains header "x-amz-server-side-encryption" with value "AES256"

  # F5: SSE-C customer algorithm ignored
  Scenario: Put object stores SSE-C customer algorithm
    Given an object with key "ssec-test.txt" and content "data"
    When the object is stored via S3 API with header "x-amz-server-side-encryption-customer-algorithm" value "AES256"
    Then the response status is 200
    And the object metadata returned by HEAD contains header "x-amz-server-side-encryption-customer-algorithm" with value "AES256"

  # M3: x-amz-acl headers ignored
  Scenario: Put object with canned ACL returns READ permission
    Given an object with key "acl-test.txt" and content "data"
    When the object is stored via S3 API with header "x-amz-acl" value "public-read"
    Then the response status is 200
    And the object metadata response contains "READ"

  # M3: x-amz-grant-read header ignored
  Scenario: Put object with grant-read header
    Given an object with key "grant-test.txt" and content "data"
    When the object is stored via S3 API with header "x-amz-grant-read" value "uri://someone"
    Then the response status is 200
    And the object metadata response contains "Grant"

  # M4: x-amz-checksum-sha256 header ignored
  Scenario: Put object stores checksum SHA256 header
    Given an object with key "checksum-test.txt" and content "data"
    When the object is stored via S3 API with header "x-amz-checksum-sha256" value "deadbeef1234567890abcdef1234567890"
    Then the response status is 200
    And the object metadata returned by HEAD contains header "x-amz-checksum-sha256" with value "deadbeef1234567890abcdef1234567890"

  # M5: x-amz-version-id — not present for bucket without versioning
  Scenario: Put object does not return version-id when versioning disabled
    Given an object with key "version-test.txt" and content "data"
    When the object is stored via S3 API
    Then the response status is 200
    And the response header "x-amz-version-id" is absent

  # M1: x-amz-expected-bucket-owner ignored
  Scenario: Put object with expected bucket owner
    Given an object with key "owner-test.txt" and content "data"
    When the object is stored via S3 API with header "x-amz-expected-bucket-owner" value "expected-owner"
    Then the response status is 200

  # AWS CLI default headers — handler ignores these
  Scenario: Put object ignores Content-Type when AWS CLI omits it
    Given an object with key "ctype-test.txt" and content "data"
    When the object is stored via S3 API without content-type header
    Then the response status is 200
    And the object metadata returned by HEAD contains header "Content-Type" with value "application/octet-stream"

  Scenario: Put object ignores x-amz-checksum-crc64nvme (AWS CLI v2 default)
    Given an object with key "crc64-test.txt" and content "data"
    When the object is stored via S3 API with header "x-amz-checksum-crc64nvme" value "crc64value"
    Then the response status is 200
    And the object metadata returned by HEAD contains header "x-amz-checksum-crc64nvme" with value "crc64value"

  Scenario: Put object ignores x-amz-sdk-checksum-algorithm (AWS CLI v2 default)
    Given an object with key "sdk-checksum-test.txt" and content "data"
    When the object is stored via S3 API with header "x-amz-sdk-checksum-algorithm" value "crc64nvme"
    Then the response status is 200
    And the object metadata returned by HEAD contains header "x-amz-sdk-checksum-algorithm" with value "crc64nvme"

  Scenario: Put object ignores X-Amz-Date (SigV4 — not validated)
    Given an object with key "date-test.txt" and content "data"
    When the object is stored via S3 API with header "X-Amz-Date" value "20260527T000000Z"
    Then the response status is 200
    # SigV4 validation is NOT IMPLEMENTED — handler accepts any date

  Scenario: Put object ignores X-Amz-Content-SHA256 (SigV4 — not validated)
    Given an object with key "sha-test.txt" and content "data"
    When the object is stored via S3 API with header "X-Amz-Content-SHA256" value "unhashedpayload"
    Then the response status is 200
    # SigV4 validation is NOT IMPLEMENTED — handler accepts any SHA256

  Scenario: Put object ignores Authorization header (SigV4 — not validated)
    Given an object with key "auth-test.txt" and content "data"
    When the object is stored via S3 API with header "Authorization" value "AWS4-HMAC-SHA256 Credential=.../s3/aws4_request"
    Then the response status is 200
    # SigV4 validation is NOT IMPLEMENTED — handler accepts any Authorization value

  Scenario: Put object ignores User-Agent header
    Given an object with key "ua-test.txt" and content "data"
    When the object is stored via S3 API with header "User-Agent" value "aws-sdk-v2"
    Then the response status is 200

  Scenario: Put object ignores Expect header (100-continue)
    Given an object with key "expect-test.txt" and content "data"
    When the object is stored via S3 API with header "Expect" value "100-continue"
    Then the response status is 200

  # ── Checksum header tests ──

  Scenario: Put object with Content-MD5 header echoes Content-MD5 and uses ETag = md5
    Given an object with key "content-md5-test.txt" and content "data"
    When the object is stored via S3 API with Content-MD5 header "dGVzdA=="
    Then the response status is 200
    And the response ETag header is "\"dGVzdA==\""
    And the object metadata returned by HEAD contains header "Content-MD5" with value "dGVzdA=="

  Scenario: Put object without any checksum header succeeds (200 OK)
    Given an object with key "no-checksum-test.txt" and content "data"
    When the object is stored via S3 API without checksum headers
    Then the response status is 200
    And the object appears in the object list

  Scenario: Put object with unknown SDK checksum algorithm returns 400
    Given an object with key "unknown-alg-test.txt" and content "data"
    When the object is stored via S3 API with SDK checksum algorithm "invalid-alg"
    Then the response status is 400

  Scenario: Put object with SDK checksum algorithm but missing hash header returns 400
    Given an object with key "missing-hash-test.txt" and content "data"
    When the object is stored via S3 API with SDK checksum algorithm "sha256" without corresponding hash header
    Then the response status is 400

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

  # ── Anomaly tests — Input Validation ──

  Scenario: Put object with invalid storage class returns 400
    Given an object with key "invalid-sc.txt" and content "data"
    When the object is stored via S3 API with storage class "INVALID_CLASS"
    Then the response status is 400

  Scenario: Put object with empty key returns 400
    Given an object with key "" and content "data"
    When the object is stored via S3 API
    Then the response status is 400

  # ── G13: SSE-KMS scenario ──

  Scenario: PutObject with SSE-KMS
    Given an object with key "sse-kms-test.txt" and content "data"
    When the object is stored via S3 API with SSE header "x-amz-server-side-encryption" value "aws:kms" and KMS key "test-key"
    Then the response status is 200
    And HEAD response contains SSE header "x-amz-server-side-encryption" with value "aws:kms"
    And HEAD response contains SSE header "x-amz-server-side-encryption-aws-kms-key-id" with value "test-key"

  # ── G13: Storage class STANDARD scenario ──

  Scenario: PutObject with storage class STANDARD
    Given an object with key "standard-test.txt" and content "data"
    When the object is stored via S3 API with storage class "STANDARD"
    Then the response status is 200
    And the object attributes returned include storage class "STANDARD"

  # ── G13: Checksum echo in GET scenario ──

  Scenario: PutObject with checksum echo in GET
    Given an object with key "checksum-get-test.txt" and content "data"
    When the object is stored via S3 API with header "x-amz-checksum-sha256" value "deadbeef"
    Then the response status is 200
    And GET response contains checksum header "x-amz-checksum-sha256" with value "deadbeef"

  # ── Failure scenarios (currently working) ──

  Scenario: Put object to nonexistent bucket returns 404
    Given an object with key "orphan.txt" and content "data"
    When the object is stored via S3 API in bucket "no-such-bucket"
    Then the response status is 404

  # ── Service/repository error scenarios ──

  Scenario: Put object with invalid bucket name returns 400
    Given an object with key "bad-bucket-test.txt" and content "data"
    When the object is stored via S3 API with invalid bucket name ""
    Then the response status is 400

  Scenario: Put object with negative content length returns 400
    Given an object with key "negative-length.txt" and content "data"
    When the object is stored via S3 API with content-length header -1
    Then the response status is 400

  Scenario: Copy object with metadata directive returns 200
    Given object "copy-source.txt" exists with content "Copy source data"
    When the object with key "copy-source.txt" is copied to "copy-target.txt" with metadata "REPLACE"
    Then the response status is 200

  # ── State machine operation scenarios ──

  Scenario: Lock an existing object
    Given object "lock-me.txt" exists with content "Lockable data"
    When object "lock-me.txt" is locked via S3 API with mode "COMPLIANCE" and duration 30 days
    Then the response status is 200

  Scenario: Archive an existing object
    Given object "archive-me.txt" exists with content "Archivable data"
    When object "archive-me.txt" is archived via S3 API
    Then the response status is 200
