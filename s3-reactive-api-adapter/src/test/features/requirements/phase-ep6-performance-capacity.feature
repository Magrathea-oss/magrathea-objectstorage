@requirement @phase-ep6 @performance @capacity @single-node @business-need
Business Need: EP-6 bounded S3 service envelope for the 0.1.x single-node preview
  S3 clients and operators need a reproducible capacity envelope for the 0.1.x single-node preview
  so they can distinguish supported bounded operation from production-scale or competitive benchmark claims.

  The CI load validation mode runs for 45 seconds and is suitable for the mandatory build gate.
  The soak validation mode is an explicit 15-minute opt-in profile and is not part of the default test gate.
  The connection validation mode uses real TCP clients against a child process rather than an in-process router.

  Rule: The supported object-size envelope is explicit and fails without publishing partial state

    @implemented-and-validated @REQ-PERF-001 @functional-requirement @non-functional-requirement @capacity @streaming @integrity @ci-load-required
    Scenario: A client stores and reads the maximum supported single object
      Given the 0.1.x single-node S3 process runs with maximum heap "256m"
      And bucket "ep6-object-limit-bucket" exists on filesystem root "target/ep6/object-limit"
      And fixture "fixtures/upload/large-object.bin" contains 268435456 deterministic bytes
      When the client uploads the fixture as object "limits/single-256-mib.bin"
      Then PutObject succeeds without an out-of-memory error
      And GetObject returns 268435456 bytes with the fixture checksum
      And the committed object has no truncated or unpublished storage artifacts

    @implemented-and-validated @REQ-PERF-002 @functional-requirement @non-functional-requirement @capacity @streaming @fail-closed @ci-load-required
    Scenario: A single object larger than 256 MiB is rejected before publication
      Given the 0.1.x single-node S3 process limits a single object to 268435456 bytes
      And bucket "ep6-object-rejection-bucket" exists on filesystem root "target/ep6/object-rejection"
      When the client attempts PutObject for "limits/single-too-large.bin" with content length 268435457
      Then the S3 response reports "EntityTooLarge"
      And object "limits/single-too-large.bin" does not exist
      And no temporary whole-object, deduplication, or erasure-coding artifact remains for the rejected request

    @implemented-and-validated @REQ-PERF-003 @functional-requirement @non-functional-requirement @capacity @multipart @streaming @integrity @ci-load-required
    Scenario: Multipart limits allow 64 MiB parts and a 256 MiB assembled object
      Given the 0.1.x single-node S3 process limits each multipart part to 67108864 bytes
      And the assembled multipart object limit is 268435456 bytes
      And bucket "ep6-multipart-limit-bucket" exists on filesystem root "target/ep6/multipart-limit"
      When the client uploads four deterministic 67108864-byte parts for object "limits/multipart-256-mib.bin"
      And the client completes the multipart upload in part-number order
      Then CompleteMultipartUpload succeeds without an out-of-memory error
      And GetObject returns 268435456 bytes with the checksum of the ordered parts

    @implemented-and-validated @REQ-PERF-004 @functional-requirement @non-functional-requirement @capacity @multipart @fail-closed @ci-load-required
    Scenario Outline: Multipart requests beyond the declared limits are rejected without a partial object
      Given the 0.1.x single-node multipart limits are 67108864 bytes per part and 268435456 bytes assembled
      And bucket "ep6-multipart-rejection-bucket" exists on filesystem root "target/ep6/multipart-rejection"
      When the client attempts <operation> for object "<object-key>" with <bytes> bytes
      Then the S3 response reports "EntityTooLarge"
      And object "<object-key>" does not exist
      And no committed multipart part or assembled-object artifact remains from the rejected operation

      Examples:
        | operation                   | object-key                         | bytes     |
        | UploadPart                  | limits/part-too-large.bin          | 67108865  |
        | CompleteMultipartUpload     | limits/assembled-too-large.bin     | 268435457 |

  Rule: Overload controls return observable S3 outcomes instead of silently queueing work

    @implemented-and-validated @REQ-PERF-005 @functional-requirement @non-functional-requirement @timeout @fail-closed @ci-load-required
    Scenario: A request that exceeds the configured request timeout is cancelled and reported
      Given the 0.1.x single-node S3 process has a finite request timeout
      And bucket "ep6-timeout-bucket" exists on filesystem root "target/ep6/timeout"
      When a client deliberately stalls PutObject for "limits/timed-out.bin" beyond the configured timeout
      Then the S3 response reports "RequestTimeout"
      And the timed-out request releases its admission permit
      And object "limits/timed-out.bin" and its temporary artifacts do not exist

    @implemented-and-validated @REQ-PERF-006 @functional-requirement @non-functional-requirement @concurrency @backpressure @fail-fast @ci-load-required
    Scenario: Work above the configured concurrency limit is rejected immediately without a queue
      Given the 0.1.x single-node S3 process has a configured active-request concurrency limit
      And every concurrency permit is held by an active streaming S3 request
      When one additional S3 request arrives
      Then the additional request receives S3 error "SlowDown"
      And the rejection occurs without entering a pending request queue
      And releasing one active request allows a subsequent request to acquire the freed permit

    @implemented-and-validated @REQ-PERF-007 @functional-requirement @non-functional-requirement @rate-limiting @token-bucket @ci-load-required
    Scenario: Token-bucket rate limiting permits the burst and throttles excess requests
      Given the 0.1.x single-node S3 process has a configured token-bucket refill rate and burst capacity
      When one client sends a deterministic burst that consumes the available tokens and then sends one excess request
      Then requests covered by available tokens are admitted
      And the excess request receives S3 error "SlowDown" with a retry hint
      And a request is admitted again after the configured token refill interval

    @implemented-and-validated @REQ-PERF-008 @functional-requirement @non-functional-requirement @connection-limit @fail-fast @connection-validation-required
    Scenario: The TCP connection cap refuses excess sockets and recovers capacity
      Given a real 0.1.x single-node S3 child process has a configured TCP connection cap
      And real TCP clients hold every allowed connection open
      When one additional TCP client attempts to connect
      Then the additional connection is refused or closed within the connection validation deadline
      And the S3 process remains live
      And closing one accepted connection permits a subsequent TCP client connection
