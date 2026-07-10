@spec @security @sigv4 @authentication @maintainer
Ability: SigV4 verifier and secured-mode filter contract
  Maintainers need an executable specification for the low-level SigV4 verifier
  and WebFilter gate so refactors preserve the authentication decisions that the
  EP-1 S3 security Business Need relies on.

  Rule: Canonical SigV4 verifier decisions are deterministic

    @REQ-SEC-SPEC-001 @functional-requirement @component-spec-required @implemented-and-validated
    Scenario: Valid SigV4 canonical request resolves the configured principal
      Given an in-process SigV4 verifier with region "us-east-1" and fixed clock "2026-07-09T12:00:00Z"
      And configured credential access key "AKIAMAGRATHEATEST1" secret key "test-secret-key" principal "tenant-a-writer"
      When the verifier evaluates a signed PUT request for "/secure-ingest/incoming/report.csv" at "2026-07-09T12:00:00Z" using access key "AKIAMAGRATHEATEST1" secret key "test-secret-key" and payload hash "UNSIGNED-PAYLOAD"
      Then the verifier allows the request for principal "tenant-a-writer"

    @REQ-SEC-SPEC-002 @functional-requirement @component-spec-required @implemented-and-validated
    Scenario: Anonymous verifier input is rejected with AccessDenied
      Given an in-process SigV4 verifier with region "us-east-1" and fixed clock "2026-07-09T12:00:00Z"
      And configured credential access key "AKIAMAGRATHEATEST1" secret key "test-secret-key" principal "tenant-a-writer"
      When the verifier evaluates an unsigned PUT request for "/secure-ingest/incoming/report.csv"
      Then the verifier denies the request with S3 error code "AccessDenied"

    @REQ-SEC-SPEC-003 @functional-requirement @component-spec-required @implemented-and-validated
    Scenario Outline: Invalid verifier input maps to the expected S3 authentication error
      Given an in-process SigV4 verifier with region "us-east-1" and fixed clock "2026-07-09T12:00:00Z"
      And configured credential access key "AKIAMAGRATHEATEST1" secret key "test-secret-key" principal "tenant-a-writer"
      When the verifier evaluates a signed PUT request with authentication defect "<defect>"
      Then the verifier denies the request with S3 error code "<errorCode>"

      Examples:
        | defect             | errorCode             |
        | unknown-access-key | InvalidAccessKeyId    |
        | bad-signature      | SignatureDoesNotMatch |
        | stale-x-amz-date   | RequestTimeTooSkewed  |

  Rule: The WebFilter enforces secured-mode opt-in behavior

    @REQ-SEC-SPEC-004 @functional-requirement @component-spec-required @implemented-and-validated
    Scenario: Disabled secured mode bypasses the SigV4 filter
      Given a secured-mode WebFilter with security enabled false
      When the WebFilter receives an unsigned PUT request for "/bucket/key"
      Then the downstream chain is invoked

    @REQ-SEC-SPEC-005 @functional-requirement @component-spec-required @implemented-and-validated
    Scenario: Enabled secured mode rejects an unsigned request before the downstream chain
      Given a secured-mode WebFilter with security enabled true
      When the WebFilter receives an unsigned PUT request for "/bucket/key"
      Then the downstream chain is not invoked
      And the WebFilter response status is 403
