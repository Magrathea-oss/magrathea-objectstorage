@requirement @phase-ep1 @s3-api @security @identity @auth @business-need
Business Need: EP-1 S3 security and identity
  Magrathea must authenticate S3 requests, authorize actions deny-by-default,
  audit decisions, and encrypt protected object data at rest before it can be
  exposed to untrusted networks or claim production S3 compatibility.

  Rule: SigV4 authentication is required in secured mode

    @REQ-SEC-001 @functional-requirement @security @sigv4 @authentication @webclient-required @awscli-required @implemented-and-validated
    Scenario: Anonymous PutObject is rejected when secured S3 mode is enabled
      Given secured S3 mode is enabled with credential profile "tenant-a-dev"
      And bucket "secure-ingest" exists for account "111122223333"
      When an unsigned S3 PutObject request stores object "incoming/report.csv" with body "tenant data"
      Then the response status is 403
      And the S3 error code is "AccessDenied"
      And object "incoming/report.csv" is not stored in bucket "secure-ingest"

    @REQ-SEC-002 @functional-requirement @security @sigv4 @authentication @webclient-required @awscli-required @implemented-and-validated
    Scenario: Correctly signed SigV4 PutObject is accepted for a known access key
      Given secured S3 mode is enabled with access key "AKIAMAGRATHEATEST1" for principal "tenant-a-writer"
      And bucket "secure-ingest" exists for account "111122223333"
      When a SigV4 signed S3 PutObject request stores object "incoming/report.csv" with body "tenant data"
      Then the response status is 200
      And the response ETag header is present
      And a subsequent signed GetObject for "incoming/report.csv" returns body "tenant data"

    @REQ-SEC-002A @functional-requirement @security @sigv4 @authentication @payload-integrity @webclient-required @awscli-required @implemented-and-validated
    Scenario: Correctly signed exact payload hash PutObject is accepted without losing the request body
      Given secured S3 mode is enabled with access key "AKIAMAGRATHEATEST1" for principal "tenant-a-writer"
      And bucket "secure-ingest" exists for account "111122223333"
      When a SigV4 signed S3 PutObject request with an exact payload hash stores object "incoming/exact-hash.csv" with body "signed payload data"
      Then the response status is 200
      And the response ETag header is present
      And a subsequent signed GetObject for "incoming/exact-hash.csv" returns body "signed payload data"

    @REQ-SEC-003 @functional-requirement @security @sigv4 @authentication @webclient-required @awscli-required @implemented-and-validated
    Scenario Outline: Invalid SigV4 authentication is rejected before object mutation
      Given secured S3 mode is enabled with access key "AKIAMAGRATHEATEST1" for principal "tenant-a-writer"
      And bucket "secure-ingest" exists for account "111122223333"
      When a SigV4 signed S3 PutObject request for object "incoming/bad-auth.txt" is sent with authentication defect "<defect>"
      Then the response status is 403
      And the S3 error code is "<errorCode>"
      And object "incoming/bad-auth.txt" is not stored in bucket "secure-ingest"

      Examples:
        | defect             | errorCode             |
        | unknown-access-key | InvalidAccessKeyId    |
        | bad-signature      | SignatureDoesNotMatch |
        | stale-x-amz-date   | RequestTimeTooSkewed  |

    @REQ-SEC-003A @functional-requirement @security @sigv4 @authentication @payload-integrity @webclient-required @awscli-required @implemented-and-validated
    Scenario: Payload hash mismatch is rejected before object mutation
      Given secured S3 mode is enabled with access key "AKIAMAGRATHEATEST1" for principal "tenant-a-writer"
      And bucket "secure-ingest" exists for account "111122223333"
      When a SigV4 signed S3 PutObject request for object "incoming/bad-auth.txt" is sent with authentication defect "payload-hash-mismatch"
      Then the response status is 403
      And the S3 error code is "XAmzContentSHA256Mismatch"
      And object "incoming/bad-auth.txt" is not stored in bucket "secure-ingest"

  Rule: S3 authorization is deny-by-default and policy driven

    @REQ-SEC-004 @functional-requirement @security @authorization @bucket-policy @webclient-required @awscli-required @implemented-and-validated
    Scenario: Authenticated principal without an allow policy is denied by default
      Given secured S3 mode is enabled with access key "AKIAMAGRATHEAREAD1" for principal "tenant-a-reader"
      And bucket "secure-ingest" exists for account "111122223333"
      And no bucket policy or ACL grants principal "tenant-a-reader" action "s3:GetObject" on object "incoming/report.csv"
      When principal "tenant-a-reader" sends a correctly signed GetObject request for "incoming/report.csv"
      Then the response status is 403
      And the S3 error code is "AccessDenied"
      And an audit event records decision "deny" with reason "no-allowing-policy"

    @REQ-SEC-005 @functional-requirement @security @authorization @bucket-policy @webclient-required @awscli-required @implemented-and-validated
    Scenario: Explicit bucket policy deny overrides an allow
      Given secured S3 mode is enabled with principal "tenant-a-writer"
      And bucket policy for "secure-ingest" allows principal "tenant-a-writer" action "s3:PutObject" on prefix "incoming/"
      And bucket policy for "secure-ingest" denies principal "tenant-a-writer" action "s3:PutObject" on object "incoming/blocked.csv"
      When principal "tenant-a-writer" sends a correctly signed PutObject request for "incoming/blocked.csv" with body "blocked"
      Then the response status is 403
      And the S3 error code is "AccessDenied"
      And object "incoming/blocked.csv" is not stored in bucket "secure-ingest"
      And an audit event records decision "deny" with reason "explicit-deny"

    @REQ-SEC-006 @functional-requirement @security @authorization @acl @public-access-block @webclient-required @awscli-required @implemented-and-validated
    Scenario: PublicAccessBlock prevents public ACL access even when an object ACL is public-read
      Given secured S3 mode is enabled
      And bucket "secure-public-block" has PublicAccessBlock setting "BlockPublicAcls" enabled
      And object "docs/public.txt" has ACL "public-read"
      When an unsigned GetObject request reads "docs/public.txt" from bucket "secure-public-block"
      Then the response status is 403
      And the S3 error code is "AccessDenied"
      And an audit event records decision "deny" with reason "public-access-block"

    @REQ-SEC-007 @functional-requirement @security @authorization @expected-owner @webclient-required @awscli-required @implemented-and-validated
    Scenario: Expected bucket owner mismatch rejects an otherwise authorized request
      Given secured S3 mode is enabled with principal "tenant-a-reader"
      And bucket "secure-ingest" is owned by account "111122223333"
      And principal "tenant-a-reader" is allowed action "s3:GetObject" on bucket "secure-ingest"
      When principal "tenant-a-reader" sends a signed GetObject request with header "x-amz-expected-bucket-owner" set to "999900001111"
      Then the response status is 403
      And the S3 error code is "AccessDenied"
      And an audit event records decision "deny" with reason "expected-owner-mismatch"

  Rule: Security side effects are observable and safe

    @REQ-SEC-008 @functional-requirement @non-functional-requirement @security @audit @observability @webclient-required @awscli-required @implemented-and-validated
    Scenario: Audit events are durable and redact secrets and object bodies
      Given secured S3 mode is enabled with principal "tenant-a-writer"
      When principal "tenant-a-writer" sends a signed PutObject request for "incoming/secret.txt" with body "do-not-log-this-body"
      Then an audit event is durably recorded with request id, principal, action, bucket, object key, decision, and response status
      And the audit event does not contain the secret access key
      And the audit event does not contain object body text "do-not-log-this-body"
      And the audit event remains available after an application restart

    @REQ-SEC-009 @functional-requirement @non-functional-requirement @security @encryption @sse @durability @webclient-required @awscli-required @implemented-and-validated
    Scenario: SSE-S3 encrypted object bytes are not stored as plaintext at rest
      Given secured S3 mode is enabled with SSE-S3 default encryption for bucket "secure-encrypted"
      When a signed PutObject request stores object "records/pii.json" with body "{\"ssn\":\"123-45-6789\"}"
      Then the response status is 200
      And a signed GetObject request returns body "{\"ssn\":\"123-45-6789\"}"
      And filesystem inspection under storage root "target/storage-engine-it/REQ-SEC-009-sse" finds no plaintext occurrence of "123-45-6789"
      And an audit event records encryption mode "SSE-S3"
