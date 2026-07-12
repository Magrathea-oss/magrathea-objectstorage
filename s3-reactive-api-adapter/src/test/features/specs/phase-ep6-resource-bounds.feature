@spec @phase-ep6 @performance @capacity @single-node @resource-bounds
Ability: Reproducible EP-6 resource-bound validation
  As a maintainer of the 0.1.x single-node preview,
  I want deterministic CI and opt-in soak evidence with redacted telemetry,
  So that capacity regressions are reproducible without presenting this validation as a production or competitive benchmark.

  The CI load validation mode runs for 45 seconds in the mandatory build envelope.
  The soak validation mode runs for 15 minutes only when explicitly selected.
  Connection-limit behavior is owned by the separate real-socket connection validation mode.

  Rule: Deterministic mixed load remains bounded by the declared JVM envelope

    @implemented-and-validated @REQ-PERF-009 @non-functional-requirement @load @bounded-memory @streaming @ci @ci-load-required
    Scenario: Eight deterministic workers sustain mixed S3 load for 45 seconds under Xmx256m
      Given a storage-engine S3 child process runs with "-Xmx256m" and filesystem root "target/ep6/ci-load"
      And load seed "ep6-ci-0.1.x" assigns these repeating operations to eight workers:
        | workers | operation                  | fixture or target                         |
        | 1,2     | PutObject                  | deterministic 1 MiB unique objects        |
        | 3,4     | GetObject                  | deterministic 2 MiB read fixtures         |
        | 5       | HeadObject                 | an existing read fixture                  |
        | 6       | ranged GetObject           | bytes 262144-786431 of a read fixture     |
        | 7       | Create and complete upload | two deterministic 5 MiB multipart parts   |
        | 8       | DeleteObject               | a worker-owned existing object            |
      When the eight workers execute the seeded schedule for 45 seconds
      Then the child process exits the load window without an out-of-memory error or forced restart
      And observed heap usage never exceeds the configured 268435456-byte maximum
      And every acknowledged write remains checksum-readable
      And every failed operation is classified in the result manifest rather than discarded

    @implemented-and-validated @REQ-PERF-010 @non-functional-requirement @soak @bounded-memory @streaming @opt-in @soak-required
    Scenario: The opt-in 15-minute soak detects resource growth and preserves acknowledged data
      Given the same eight-worker workload and seed family used by CI validation
      And a storage-engine S3 child process runs with "-Xmx256m" and filesystem root "target/ep6/soak"
      When the opt-in soak profile runs the mixed workload for 15 minutes
      Then the process completes without an out-of-memory error, deadlock, or forced restart
      And heap, active requests, open connections, and temporary storage return to their declared idle bounds after the load stops
      And every sampled acknowledged write remains checksum-readable
      And the result manifest identifies validation mode "soak" and duration 900 seconds

  Rule: Capacity controls expose useful metrics without leaking client data

    @implemented-and-validated @REQ-PERF-011 @non-functional-requirement @observability @metrics @redaction @ci-load-required
    Scenario: Load and overload metrics explain resource use and rejection decisions
      Given CI load records accepted, concurrency-rejected, rate-limited, and timed-out request counts
      When the validation captures process and application metrics
      Then the evidence includes heap usage, active requests, open TCP connections, accepted requests, concurrency rejections, rate-limit rejections, and request timeouts
      And counters distinguish operation and outcome without recording access keys, authorization signatures, object bodies, user metadata, bucket names, or object keys
      And logs and metric labels comply with the existing telemetry redaction policy

  Rule: Capacity-control components are deterministic before load validation

    @implemented-and-validated @REQ-PERF-COMP-001 @component-spec-required @configuration @capacity
    Scenario: The S3 capacity envelope has safe preview defaults
      Given a default S3 capacity configuration
      Then capacity controls are enabled with 268435456 single PUT bytes, 67108864 multipart part bytes, and 268435456 assembled bytes
      And the defaults allow 16 concurrent requests with a 300 second timeout
      And the default token bucket refills 100 requests per second with burst 200

    @implemented-and-validated @REQ-PERF-COMP-002 @component-spec-required @streaming @fail-closed
    Scenario: Body limits reject declared and streamed excess without aggregation
      Given a component capacity filter with a 4 byte single PUT limit
      When PUT declares a 5 byte Content-Length
      Then the filter rejects it as "EntityTooLarge" without invoking downstream storage
      When an unknown-length PUT streams chunks of 3 and 2 bytes
      Then the filter rejects it incrementally as "EntityTooLarge" after observing the excess chunk

    @implemented-and-validated @REQ-PERF-COMP-003 @component-spec-required @concurrency @timeout @rate-limiting @metrics @redaction
    Scenario: Admission controls fail fast, recover permits, and emit bounded telemetry
      Given deterministic capacity controls with one concurrency permit, a 50 millisecond timeout, and a two-token burst
      Then a third immediate token request is rejected as "SlowDown" with Retry-After "1"
      And advancing one refill interval admits another token request
      When one request holds the only concurrency permit
      Then another request is rejected immediately as "SlowDown" without a pending subscription
      And cancelling the holder permits the next request
      When an admitted request remains stalled beyond 50 milliseconds
      Then it is cancelled as "RequestTimeout" and its permit is reusable
      And capacity metric tags contain only operation and outcome vocabularies

  Rule: Every result is reproducible and explicitly not a benchmark claim

    @implemented-and-validated @REQ-PERF-012 @non-functional-requirement @reproducibility @reporting
    Scenario Outline: A validation run writes a machine-readable result manifest
      Given EP-6 runs in validation mode "<mode>" for <duration> seconds
      When the run finishes
      Then its result directory contains a machine-readable manifest
      And the manifest records Git revision, release line "0.1.x", dirty-tree state, validation mode, workload seed, worker count 8, duration, JVM vendor and version, maximum heap "256m", operating system, processor count, backend, filesystem root, object and multipart limits, timeout, concurrency limit, rate-limit settings, TCP connection cap, operation counts, error counts, latency summary, peak heap, and artifact checksums
      And rerunning with the recorded configuration and seed reproduces the operation schedule
      And the manifest states "Capacity validation only; not a production sizing, comparative, or competitive benchmark"

      @ci-load-required
      Examples: CI load
        | mode | duration |
        | ci   | 45       |

      @soak-required
      Examples: Opt-in soak
        | mode | duration |
        | soak | 900      |

    @implemented-and-validated @REQ-PERF-013 @non-functional-requirement @reporting @benchmark-disclaimer @ci-load-required @soak-required @connection-validation-required
    Scenario: Human-readable EP-6 results preserve the non-benchmark scope
      Given CI, soak, or connection validation has produced a result manifest
      When maintainers publish the EP-6 result summary
      Then the summary links to the exact result manifest and its artifact checksums
      And it identifies the tested 0.1.x single-node hardware and software envelope
      And it separates observed values from configured limits and pass criteria
      And it states that the result is not production sizing guidance and not a comparison with another object store
