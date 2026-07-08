@requirement @single-node-backend @put-object @headers @webclient-required
Business Need: PutObject header, metadata, and checksum handling against the single-node in-memory backend
  As an S3-compatible client using WebTestClient against a single-node Magrathea
  deployment,
  I want to know precisely which PutObject request headers are persisted and echoed
  back, which are silently ignored, and which security-relevant headers are not yet
  validated,
  So that client integrations do not assume server-side enforcement (encryption,
  signature validation) that Magrathea does not yet provide, while still relying on
  the headers that are genuinely persisted (user metadata, checksums, storage class).

  This feature shares the single-node-backend validation scope described in
  single-node-backend-bucket-operations.feature (no "storage-engine" profile active,
  InMemoryReactiveS3ObjectRepository backs the object repository). It was extracted
  from the legacy features/object-store/put_object.feature ("PutObject API — Anomaly
  Tests"), which mixed genuine header-handling requirements with plain CRUD
  duplicates (now in single-node-backend-object-crud.feature) and one non-observable
  pseudo-scenario asserting behavior "verified by code review," which has been
  dropped: it described no runtime-observable outcome and does not belong in an
  executable requirement per AGENTS.md §A.2.

  Rule: Storage class values are accepted without validation against a fixed AWS enum

    @REQ-SINGLENODE-PUTHDR-001 @functional-requirement @webclient-required
    Scenario: Put an object with a nonstandard storage class value
      Given bucket "test-bucket" exists
      And an object with key "paranoid.txt" and content "Top secret"
      When the object is stored via S3 API with storage class "PARANOIC_MODE"
      Then the response status is 200
      And the object appears in the object list
      # Documents that the storage class field is not validated against a fixed
      # enum of AWS-defined values (STANDARD, GLACIER, ...); any string is accepted.

  Rule: User metadata and checksum headers are persisted and echoed back by HEAD

    @REQ-SINGLENODE-PUTHDR-002 @functional-requirement @webclient-required
    Scenario: Put object stores user metadata headers
      Given bucket "test-bucket" exists
      And an object with key "meta-test.txt" and content "data"
      When the object is stored via S3 API with metadata header "x-amz-meta-project" value "magrathea"
      Then the response status is 200
      And the object metadata returned by HEAD contains header "x-amz-meta-project" with value "magrathea"

    @REQ-SINGLENODE-PUTHDR-003 @functional-requirement @webclient-required
    Scenario: Put object stores checksum SHA256 header
      Given bucket "test-bucket" exists
      And an object with key "checksum-test.txt" and content "data"
      When the object is stored via S3 API with header "x-amz-checksum-sha256" value "deadbeef1234567890abcdef1234567890"
      Then the response status is 200
      And the object metadata returned by HEAD contains header "x-amz-checksum-sha256" with value "deadbeef1234567890abcdef1234567890"

    @REQ-SINGLENODE-PUTHDR-004 @functional-requirement @webclient-required
    Scenario: Put object ignores x-amz-checksum-crc64nvme (AWS CLI v2 default)
      Given bucket "test-bucket" exists
      And an object with key "crc64-test.txt" and content "data"
      When the object is stored via S3 API with header "x-amz-checksum-crc64nvme" value "crc64value"
      Then the response status is 200
      And the object metadata returned by HEAD contains header "x-amz-checksum-crc64nvme" with value "crc64value"

    @REQ-SINGLENODE-PUTHDR-005 @functional-requirement @webclient-required
    Scenario: Put object ignores x-amz-sdk-checksum-algorithm (AWS CLI v2 default)
      Given bucket "test-bucket" exists
      And an object with key "sdk-checksum-test.txt" and content "data"
      When the object is stored via S3 API with header "x-amz-sdk-checksum-algorithm" value "crc64nvme"
      Then the response status is 200
      And the object metadata returned by HEAD contains header "x-amz-sdk-checksum-algorithm" with value "crc64nvme"

    @REQ-SINGLENODE-PUTHDR-006 @functional-requirement @webclient-required
    Scenario: Put object with direct x-amz-checksum header (no SDK hint) succeeds and echoes in HEAD
      Given bucket "test-bucket" exists
      And an object with key "direct-checksum-test.txt" and content "data"
      When the object is stored via S3 API with direct checksum header "x-amz-checksum-crc32c" value "AAAAAA=="
      Then the response status is 200
      And the object metadata returned by HEAD contains header "x-amz-checksum-crc32c" with value "AAAAAA=="

    @REQ-SINGLENODE-PUTHDR-007 @functional-requirement @webclient-required
    Scenario: PutObject with checksum echo in GET
      Given bucket "test-bucket" exists
      And an object with key "checksum-get-test.txt" and content "data"
      When the object is stored via S3 API with header "x-amz-checksum-sha256" value "deadbeef"
      Then the response status is 200
      And GET response contains checksum header "x-amz-checksum-sha256" with value "deadbeef"

  Rule: ETag is always the MD5 hex digest of the stored bytes; a client-supplied Content-MD5 is retained as metadata, not echoed as ETag

    @REQ-SINGLENODE-PUTHDR-008 @functional-requirement @webclient-required
    Scenario: Put object with Content-MD5 header stores it as metadata while ETag is the MD5 hex of the stored bytes
      # Since the single-pass streaming upload refactor, the ETag is always the MD5 hex
      # digest computed from the actual stored body bytes (S3-compatible semantics,
      # see REQ-UPLOAD-006 in phase-1-upload-storage-engine.feature). The client-supplied
      # Content-MD5 header is retained as object metadata and echoed by HEAD, but it is
      # never echoed back as the ETag. MD5("data") = 8d777f385d3dfec8815d20f7496026dc.
      Given bucket "test-bucket" exists
      And an object with key "content-md5-test.txt" and content "data"
      When the object is stored via S3 API with Content-MD5 header "dGVzdA=="
      Then the response status is 200
      And the response ETag header is "\"8d777f385d3dfec8815d20f7496026dc\""
      And the object metadata returned by HEAD contains header "Content-MD5" with value "dGVzdA=="

  Rule: SSE headers are retained as metadata but encryption-at-rest is not enforced

    @REQ-SINGLENODE-PUTHDR-009 @placeholder @webclient-required
    Scenario: Put object stores SSE encryption header
      Given bucket "test-bucket" exists
      And an object with key "sse-test.txt" and content "data"
      When the object is stored via S3 API with header "x-amz-server-side-encryption" value "AES256"
      Then the response status is 200
      And the object metadata returned by HEAD contains header "x-amz-server-side-encryption" with value "AES256"
      # Header is stored and echoed; the object bytes are not actually encrypted at
      # rest. Real SSE enforcement is tracked in PLAN.md EP-1.

    @REQ-SINGLENODE-PUTHDR-010 @placeholder @webclient-required
    Scenario: Put object stores SSE-C customer algorithm
      Given bucket "test-bucket" exists
      And an object with key "ssec-test.txt" and content "data"
      When the object is stored via S3 API with header "x-amz-server-side-encryption-customer-algorithm" value "AES256"
      Then the response status is 200
      And the object metadata returned by HEAD contains header "x-amz-server-side-encryption-customer-algorithm" with value "AES256"

    @REQ-SINGLENODE-PUTHDR-011 @functional-requirement @webclient-required
    Scenario: PutObject with SSE-KMS
      Given bucket "test-bucket" exists
      And an object with key "sse-kms-test.txt" and content "data"
      When the object is stored via S3 API with SSE header "x-amz-server-side-encryption" value "aws:kms" and KMS key "test-key"
      Then the response status is 200
      And HEAD response contains SSE header "x-amz-server-side-encryption" with value "aws:kms"
      And HEAD response contains SSE header "x-amz-server-side-encryption-aws-kms-key-id" with value "test-key"

  Rule: SigV4 request signature headers are accepted without validation

    SigV4 signature verification is a documented, planner-verified gap tracked as
    part of PLAN.md EP-1 ("Authentication, authorization, audit, and real
    encryption" — "no SigV4 signature verification, no Spring Security dependency,
    the S3 API accepts anonymous requests"). These scenarios document the current,
    intentionally unauthenticated behavior so that client integrations do not
    mistake header presence for signature enforcement.

    @REQ-SINGLENODE-PUTHDR-012 @protocol-smoke @not-implemented @webclient-required
    Scenario Outline: Put object accepts SigV4-related headers without validating them
      Given bucket "test-bucket" exists
      And an object with key "<key>" and content "data"
      When the object is stored via S3 API with header "<header>" value "<value>"
      Then the response status is 200

      Examples: SigV4 headers accepted without signature validation (PLAN.md EP-1)
        | key         | header               | value                                                     |
        | date-test.txt | X-Amz-Date          | 20260527T000000Z                                          |
        | sha-test.txt  | X-Amz-Content-SHA256 | unhashedpayload                                            |
        | auth-test.txt | Authorization        | AWS4-HMAC-SHA256 Credential=.../s3/aws4_request            |

  Rule: Non-S3 protocol headers do not affect the PutObject outcome

    @REQ-SINGLENODE-PUTHDR-013 @protocol-smoke @webclient-required
    Scenario Outline: Put object ignores non-S3 protocol headers
      Given bucket "test-bucket" exists
      And an object with key "<key>" and content "data"
      When the object is stored via S3 API with header "<header>" value "<value>"
      Then the response status is 200

      Examples:
        | key            | header      | value           |
        | ua-test.txt     | User-Agent  | aws-sdk-v2       |
        | expect-test.txt | Expect      | 100-continue     |

  Rule: PutObject does not return a version ID header when bucket versioning is disabled

    @REQ-SINGLENODE-PUTHDR-014 @functional-requirement @webclient-required
    Scenario: Put object does not return version-id when versioning disabled
      Given bucket "test-bucket" exists
      And an object with key "version-test.txt" and content "data"
      When the object is stored via S3 API
      Then the response status is 200
      And the response header "x-amz-version-id" is absent

  Rule: x-amz-expected-bucket-owner is accepted without cross-account validation

    @REQ-SINGLENODE-PUTHDR-015 @protocol-smoke @not-implemented @webclient-required
    Scenario: Put object with expected bucket owner
      Given bucket "test-bucket" exists
      And an object with key "owner-test.txt" and content "data"
      When the object is stored via S3 API with header "x-amz-expected-bucket-owner" value "expected-owner"
      Then the response status is 200

  Rule: CopyObject accepts a metadata directive without failing

    @REQ-SINGLENODE-PUTHDR-016 @protocol-smoke @webclient-required
    Scenario: Copy object with metadata directive returns 200
      Given bucket "test-bucket" exists
      And object "copy-source.txt" exists with content "Copy source data"
      When the object with key "copy-source.txt" is copied to "copy-target.txt" with metadata "REPLACE"
      Then the response status is 200

  Rule: Object-level Lock and Archive query parameters are not routed to any dedicated handler

    Planner-verified finding (2026-07-07): `S3PathRouter` registers no route
    predicate for the `?lock` or `?archive` query parameters at the object level.
    Both fall through to the final catch-all `.PUT(OBJECT_PATH, objectOperations::putObject)`.
    A request such as `PUT /{bucket}/{key}?lock` is therefore executed as a plain
    PutObject: the request body (here, the ObjectLockConfiguration XML, or an empty
    body for archive) silently **overwrites the object's stored content**. The 200
    response does not indicate that any lock or archive semantics ran; it indicates
    only that an unrelated PutObject succeeded. This is a correctness hazard, not
    merely a missing feature: a client intending to lock or archive an object would
    silently corrupt its content instead of receiving a clear error or the intended
    effect. These scenarios are tagged `@absent` (no dedicated capability exists) and
    document the observed side effect explicitly rather than only checking the
    misleading 200 status.

    @REQ-SINGLENODE-PUTHDR-017 @absent @webclient-required
    Scenario: PUT with a lock query parameter silently overwrites object content instead of applying an Object Lock configuration
      Given bucket "test-bucket" exists
      And object "lock-me.txt" exists with content "Lockable data"
      When object "lock-me.txt" is locked via S3 API with mode "COMPLIANCE" and duration 30 days
      Then the response status is 200
      # No dedicated ?lock route exists; the request falls through to the generic
      # PutObject handler and the ObjectLockConfiguration XML body becomes the new
      # object content, replacing "Lockable data". See REQ-S3-007-C in
      # phase-5-s3-semantic-compatibility.feature for the bucket-level
      # GetObjectLockConfiguration route, which is a separate, real capability.

    @REQ-SINGLENODE-PUTHDR-018 @absent @webclient-required
    Scenario: PUT with an archive query parameter silently overwrites object content instead of transitioning storage class
      Given bucket "test-bucket" exists
      And object "archive-me.txt" exists with content "Archivable data"
      When object "archive-me.txt" is archived via S3 API
      Then the response status is 200
      # No dedicated ?archive route exists; the request falls through to the generic
      # PutObject handler with an empty body, replacing "Archivable data" with an
      # empty object.
