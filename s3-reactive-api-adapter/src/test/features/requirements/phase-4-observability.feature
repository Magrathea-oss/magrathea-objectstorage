@requirement @phase-4 @observability @storage-engine @implemented-not-e2e-validated
Ability: Phase 4 storage observability for reactive pipelines and recovery
  As a storage-engine operator, S3-compatible client, and system owner,
  I want storage pipeline activity, failures, recovery findings, and redaction decisions
  to be observable through stable events, metrics, traces, and operational logs,
  So that production incidents can be diagnosed without exposing object payloads,
  sensitive user metadata, or backend-specific implementation details.

  This feature is the single source of truth for Phase 4 observability requirements.
  It builds on the Phase 3 StorageEvent and staged pipeline abstractions. Phase 4
  adapters must translate safe pipeline and recovery observations into listener
  events, Micrometer metrics, OpenTelemetry spans, and operational logs without
  coupling core storage logic to a single telemetry backend.

  These scenarios are requirements for future implementation and runner glue. They
  remain @not-implemented until production observability adapters and executable
  validation steps exist. Keep every @REQ-OBS-* requirement ID unchanged when the
  scenarios become executable.

  Validation roles:
    - Pipeline unit runner captures StorageEvent and listener records for successful,
      failed, and cleaned-up write/read pipeline executions using deterministic clocks.
    - WebTestClient integration runner exercises the S3 RouterFunction endpoints with
      storage-engine profile enabled and observes externally triggered pipeline activity.
    - Metrics inspection reads a Micrometer MeterRegistry or scrape endpoint and asserts
      metric names, tags, counters, distribution summaries, and timers.
    - Tracing inspection reads exported OpenTelemetry spans and asserts span names,
      attributes, parent-child relationships, safe identifiers, and timing attributes.
    - Log inspection captures structured operational logs for recovery, corruption,
      cleanup, and redaction outcomes.

  Safe observability fields:
    | field                    | exposure rule                                                                 |
    | correlation id           | required in events, logs, metrics tags when cardinality policy allows, and traces |
    | request id               | required in events, logs, and traces                                           |
    | bucket                   | allowed when not marked sensitive by redaction policy                          |
    | object key               | allowed for non-sensitive test keys; otherwise expose a stable redacted hash    |
    | manifest id              | allowed as an internal safe identifier                                         |
    | stage name               | required for pipeline stage events, metrics, logs, and spans                   |
    | stage duration           | required as a duration value or timer sample                                   |
    | byte count               | allowed as numeric totals only                                                 |
    | chunk count              | allowed as numeric totals only                                                 |
    | failure classification   | required as an enum-like low-cardinality value                                 |
    | recovery finding type    | required as an enum-like low-cardinality value                                 |

  Forbidden observability fields:
    | field category              | examples that must not appear in events, metrics, traces, or logs              |
    | object payload              | body text, raw bytes, chunk content, payload snippets                          |
    | sensitive user metadata     | x-amz-meta-secret, x-amz-meta-token, x-amz-meta-customer-email                 |
    | authorization material      | Authorization header, access key, signatures, session tokens                   |
    | checksum source material    | raw secret fixture content used to compute a checksum                          |
    | unbounded high-cardinality  | complete exception stack as a metric tag, arbitrary metadata keys as tags      |

  Reusable fixtures for Phase 4 observability requirements:
    | fixture id         | resource path                          | project path                                                                      | intended content                                  |
    | small-object       | fixtures/upload/small-object.txt       | s3-reactive-api-adapter/src/test/resources/fixtures/upload/small-object.txt       | UTF-8 text: Hello durable Magrathea!              |
    | corruptible-object | fixtures/upload/corruptible-object.bin | s3-reactive-api-adapter/src/test/resources/fixtures/upload/corruptible-object.bin | deterministic 4 KiB repeating-byte binary payload |
    | secret-object      | fixtures/observability/secret-body.txt | s3-reactive-api-adapter/src/test/resources/fixtures/observability/secret-body.txt | text containing SECRET-PAYLOAD-MUST-NOT-LEAK      |

  Requirement buckets, object keys, and observability probes:
    | requirement | bucket                       | key                                              | fixture resource path                    | primary probes                                 |
    | REQ-OBS-001 | obs-pipeline-success-bucket  | observability/2026/events/success-object.txt    | fixtures/upload/small-object.txt         | event listener, pipeline unit, WebTestClient   |
    | REQ-OBS-002 | obs-pipeline-failure-bucket  | observability/2026/events/failed-object.bin     | fixtures/upload/corruptible-object.bin   | event listener, cleanup event, log inspection  |
    | REQ-OBS-003 | obs-metrics-bucket           | observability/2026/metrics/measured-object.txt  | fixtures/upload/small-object.txt         | Micrometer registry, scrape inspection         |
    | REQ-OBS-004 | obs-tracing-bucket           | observability/2026/traces/traced-object.txt     | fixtures/upload/small-object.txt         | OpenTelemetry span exporter                    |
    | REQ-OBS-005 | obs-recovery-bucket          | observability/2026/recovery/healthy-object.txt  | fixtures/upload/small-object.txt         | recovery scanner, metrics, logs, events        |
    | REQ-OBS-006 | obs-redaction-bucket         | observability/2026/redaction/secret-object.txt  | fixtures/observability/secret-body.txt   | events, metrics, traces, logs redaction checks |

  Background:
    Given the S3 API is configured with profile "storage-engine-it" and backend "storage-engine"
    And the storage engine stores bytes, manifests, and object references on a real filesystem
    And each scenario uses a clean storage-engine filesystem root "target/storage-engine-it/<scenario-id>"
    And observability listener capture, metrics inspection, tracing inspection, and log inspection can be enabled independently
    And the observability redaction policy is configured with the forbidden observability fields

  Rule: Successful pipelines publish safe stage lifecycle events
    A successful write or read pipeline MUST publish stage start and success events with
    correlation identifiers, operation name, bucket/key identity, manifest identity where
    known, and per-stage durations. The event stream MUST allow an operator to connect a
    client request to storage stages without reading payload bytes.

    @REQ-OBS-001 @functional-requirement @non-functional-requirement @observability @events @stage-timing @correlation @pipeline-unit-required @webclient-required @implemented-and-validated
    Scenario Outline: Successful write and read pipelines emit correlated stage events with durations
      Given validation mode "<validation_mode>" is selected for requirement "<requirement_id>"
      And the storage engine operator uses filesystem root "<storage_root>"
      And bucket "<bucket>" exists
      And an observability listener is registered for storage pipeline events
      And an S3 client has object content from fixture file "<fixture_file>" for bucket "<bucket>" and key "<object_key>"
      When the selected validation runner uploads fixture file "<fixture_file>" through the staged PutObject pipeline
      And the selected validation runner reads bucket "<bucket>" and key "<object_key>" through the staged GetObject pipeline
      Then the listener receives one correlation identifier shared by the write pipeline events for bucket "<bucket>" and key "<object_key>"
      And the listener receives one correlation identifier shared by the read pipeline events for bucket "<bucket>" and key "<object_key>"
      And write pipeline events include start and success outcomes for validation, policy-resolution, chunking, dedup-lookup, chunk-persistence, manifest-persistence, and object-index-persistence
      And read pipeline events include start and success outcomes for validation, policy-resolution, read-planning, chunk-reading, and response-streaming
      And each success event includes a non-negative duration for its stage and an operation value of "put-object" or "get-object"
      And manifest-persistence and later write events include the committed manifest identifier "<expected_manifest_identity>"
      And the event payload contains object identity and numeric byte or chunk totals but does not contain object payload bytes or complete chunk contents

      @pipeline-unit
      Examples: Pipeline unit validation
        | requirement_id | validation_mode | bucket                      | object_key                                    | fixture_file                     | storage_root                              | expected_manifest_identity |
        | REQ-OBS-001    | pipeline-unit   | obs-pipeline-success-bucket | observability/2026/events/success-object.txt | fixtures/upload/small-object.txt | target/storage-engine-it/REQ-OBS-001-unit | present                    |

      @webclient
      Examples: WebTestClient integration validation
        | requirement_id | validation_mode | bucket                      | object_key                                    | fixture_file                     | storage_root                                   | expected_manifest_identity |
        | REQ-OBS-001    | webclient       | obs-pipeline-success-bucket | observability/2026/events/success-object.txt | fixtures/upload/small-object.txt | target/storage-engine-it/REQ-OBS-001-webclient | present                    |

  Rule: Failed pipelines publish classified failures and cleanup observations
    A failed pipeline MUST publish exactly one classified failure for the failed stage,
    publish cleanup events for resources it owns, and suppress sensitive request details.
    Operators must be able to distinguish validation, integrity, storage I/O, cancellation,
    and recovery-related failures from the event stream and operational logs.

    @REQ-OBS-002 @functional-requirement @non-functional-requirement @observability @events @failure-classification @cleanup @redaction @pipeline-unit-required @webclient-required @log-inspection-required @implemented-and-validated
    Scenario Outline: Failed write pipeline emits a classified failure and cleanup event without payload leakage
      Given validation mode "<validation_mode>" is selected for requirement "<requirement_id>"
      And the storage engine operator uses filesystem root "<storage_root>"
      And bucket "<bucket>" exists
      And no object exists in bucket "<bucket>" for key "<object_key>"
      And an observability listener and operational log capture are registered
      And an S3 client has object content from fixture file "<fixture_file>" for bucket "<bucket>" and key "<object_key>"
      And the pipeline failure injector causes stage "<failing_stage>" to fail with classification "<failure_classification>"
      When the selected validation runner uploads fixture file "<fixture_file>" through the staged PutObject pipeline
      Then the listener receives exactly one failure event for stage "<failing_stage>" with classification "<failure_classification>"
      And the failure event includes correlation id, request id, bucket, object key, stage name, operation "put-object", and failure classification
      And the listener receives cleanup events for pipeline-owned temporary chunks, manifests, buffers, or publication handles created before the failure
      And no success event is emitted for any stage after "<failing_stage>" for the same correlation identifier
      And the operational log contains one structured warning or error with the same correlation id and failure classification
      And bucket "<bucket>" and key "<object_key>" do not resolve to a committed manifest after the failure
      And neither events nor logs contain object payload bytes, raw chunk content, Authorization headers, or sensitive user metadata

      @pipeline-unit
      Examples: Pipeline unit validation
        | requirement_id | validation_mode | failing_stage     | failure_classification | bucket                      | object_key                                   | fixture_file                         | storage_root                              |
        | REQ-OBS-002    | pipeline-unit   | chunk-persistence | storage-io-failure     | obs-pipeline-failure-bucket | observability/2026/events/failed-object.bin | fixtures/upload/corruptible-object.bin | target/storage-engine-it/REQ-OBS-002-unit |

      @webclient @log-inspection
      Examples: WebTestClient and log inspection validation
        | requirement_id | validation_mode | failing_stage     | failure_classification | bucket                      | object_key                                   | fixture_file                         | storage_root                                   |
        | REQ-OBS-002    | webclient       | chunk-persistence | storage-io-failure     | obs-pipeline-failure-bucket | observability/2026/events/failed-object.bin | fixtures/upload/corruptible-object.bin | target/storage-engine-it/REQ-OBS-002-webclient |

  Rule: Metrics expose storage activity, reliability signals, deduplication outcomes, and latency
    Micrometer metrics MUST expose low-cardinality counters, summaries, and timers for
    bytes, chunks, manifests, failures, recovery findings, dedup hits/misses, and stage
    latency. Metrics MUST be inspectable in integration tests without requiring a specific
    external monitoring system.

    @REQ-OBS-003 @non-functional-requirement @observability @metrics @micrometer @latency @dedup @recovery @webclient-required @metrics-inspection-required @implemented-not-e2e-validated
    Scenario Outline: Micrometer metrics report storage bytes, chunks, manifests, failures, recovery, deduplication, and latency
      Given validation mode "<validation_mode>" is selected for requirement "<requirement_id>"
      And the storage engine operator uses filesystem root "<storage_root>"
      And bucket "<bucket>" exists
      And Micrometer metrics inspection is enabled with an empty registry for this scenario
      And an S3 client has object content from fixture file "<fixture_file>" for bucket "<bucket>" and key "<object_key>"
      When the selected validation runner uploads fixture file "<fixture_file>" to bucket "<bucket>" and key "<object_key>"
      And the selected validation runner reads bucket "<bucket>" and key "<object_key>"
      And the selected validation runner performs a second upload of the same fixture to create a dedup lookup outcome
      And the recovery scanner is triggered for filesystem root "<storage_root>" with no corrupt artifacts
      Then metrics inspection reports an object byte counter or distribution summary incremented by the uploaded byte count
      And metrics inspection reports chunk and manifest counters incremented for persisted or reused storage artifacts
      And metrics inspection reports dedup lookup counters with hit and miss outcomes using low-cardinality tags
      And metrics inspection reports pipeline failure counters with zero or no increment for this successful scenario
      And metrics inspection reports recovery scan and recovery finding counters for the scanner execution
      And metrics inspection reports timers for write stages, read stages, and end-to-end put-object and get-object latency
      And metric tags include operation, stage, outcome, backend, and failure classification only where applicable
      And metric tags do not include object payload bytes, raw user metadata values, Authorization headers, or unbounded exception text

      @webclient @metrics-inspection
      Examples: WebTestClient metrics inspection validation
        | requirement_id | validation_mode     | bucket             | object_key                                     | fixture_file                     | storage_root                                       |
        | REQ-OBS-003    | metrics-inspection  | obs-metrics-bucket | observability/2026/metrics/measured-object.txt | fixtures/upload/small-object.txt | target/storage-engine-it/REQ-OBS-003-metrics       |

  Rule: OpenTelemetry spans identify requests and stages using safe attributes
    Traces MUST connect the S3 request span to storage pipeline stage spans. Span names
    and attributes MUST show operation, request id, correlation id, object identity,
    manifest id, backend, stage name, outcome, and timing while respecting the redaction
    policy and avoiding payload or sensitive metadata leakage.

    @REQ-OBS-004 @non-functional-requirement @observability @tracing @opentelemetry @stage-timing @correlation @webclient-required @tracing-inspection-required @redaction @implemented-and-validated
    Scenario Outline: OpenTelemetry spans include safe identifiers and stage timing without user metadata or body leakage
      Given validation mode "<validation_mode>" is selected for requirement "<requirement_id>"
      And the storage engine operator uses filesystem root "<storage_root>"
      And bucket "<bucket>" exists
      And OpenTelemetry tracing inspection is enabled with an in-memory exporter for this scenario
      And an S3 client has object content from fixture file "<fixture_file>" for bucket "<bucket>" and key "<object_key>"
      And the S3 client applies user metadata "x-amz-meta-secret=do-not-trace; x-amz-meta-project=magrathea"
      When the selected validation runner uploads fixture file "<fixture_file>" to bucket "<bucket>" and key "<object_key>"
      And the selected validation runner reads bucket "<bucket>" and key "<object_key>"
      Then tracing inspection finds a request span for PutObject and child spans for validation, policy-resolution, chunking, dedup-lookup, chunk-persistence, manifest-persistence, and object-index-persistence
      And tracing inspection finds a request span for GetObject and child spans for validation, policy-resolution, read-planning, chunk-reading, and response-streaming
      And each storage stage span includes correlation id, request id, operation, backend, stage name, outcome, and stage duration attributes
      And spans at or after manifest-persistence include a manifest identifier attribute when a manifest is committed
      And spans identify bucket "<bucket>" and object key "<object_key>" according to the safe identifier policy for this non-sensitive test key
      And no span name, span attribute, span event, or exception attribute contains object body content, raw chunk content, Authorization headers, or the value "do-not-trace"

      @webclient @tracing-inspection
      Examples: WebTestClient tracing inspection validation
        | requirement_id | validation_mode    | bucket             | object_key                                  | fixture_file                     | storage_root                                     |
        | REQ-OBS-004    | tracing-inspection | obs-tracing-bucket | observability/2026/traces/traced-object.txt | fixtures/upload/small-object.txt | target/storage-engine-it/REQ-OBS-004-tracing     |

  Rule: Recovery and corruption handling produce operational observability signals
    The recovery scanner and corruption detection paths MUST emit operational events,
    logs, and metrics that identify finding type, affected artifact category, action,
    outcome, and correlation where available. These signals must prove whether corrupt
    or orphaned artifacts were quarantined, left untouched, or require operator action.

    @REQ-OBS-005 @functional-requirement @non-functional-requirement @observability @recovery @corruption @metrics @logging @events @webclient-required @metrics-inspection-required @log-inspection-required @implemented-and-validated
    Scenario Outline: Recovery scanner emits events, logs, and metrics for quarantine and corruption findings
      Given validation mode "<validation_mode>" is selected for requirement "<requirement_id>"
      And the storage engine operator uses filesystem root "<storage_root>"
      And bucket "<bucket>" exists
      And observability listener capture, Micrometer metrics inspection, and operational log inspection are enabled
      And a valid committed object exists in bucket "<bucket>" at key "<object_key>" uploaded from fixture file "<fixture_file>"
      And an orphaned chunk, an incomplete manifest, a broken object reference, and a corrupted chunk checksum exist in filesystem root "<storage_root>"
      When the recovery scanner is triggered for filesystem root "<storage_root>"
      Then the listener receives recovery finding events for artifact types "orphaned-chunk", "incomplete-manifest", "broken-reference", and "checksum-mismatch"
      And each recovery event includes scan id, backend, storage root identifier, artifact type, action, outcome, and a safe artifact reference
      And operational logs contain structured recovery entries with scan id, finding count, quarantine count, artifact type, action, and outcome
      And Micrometer metrics include recovery scan, recovery finding, quarantine, and corruption counters tagged by backend, artifact type, action, and outcome
      And the valid committed object remains readable through the S3 HTTP GetObject API after the scanner completes
      And recovery events, metrics, and logs do not include object payload bytes, raw chunk content, Authorization headers, or sensitive user metadata

      @webclient @metrics-inspection @log-inspection
      Examples: WebTestClient recovery observability validation
        | requirement_id | validation_mode      | bucket              | object_key                                         | fixture_file                     | storage_root                                      |
        | REQ-OBS-005    | recovery-inspection  | obs-recovery-bucket | observability/2026/recovery/healthy-object.txt     | fixtures/upload/small-object.txt | target/storage-engine-it/REQ-OBS-005-recovery     |

  Rule: Redaction policy is enforced consistently across all observability channels
    The same redaction policy MUST apply to listener events, metric names and tags,
    tracing spans, and operational logs. A field forbidden in one observability channel
    MUST NOT leak through another channel. Redacted output should remain diagnostically
    useful by keeping safe identifiers, classification enums, numeric totals, and timing.

    @REQ-OBS-006 @non-functional-requirement @observability @redaction @security @privacy @metrics @tracing @logging @events @pipeline-unit-required @webclient-required @metrics-inspection-required @tracing-inspection-required @log-inspection-required @implemented-and-validated
    Scenario Outline: Redaction policy prevents sensitive metadata and payload content from leaking through observability signals
      Given validation mode "<validation_mode>" is selected for requirement "<requirement_id>"
      And the storage engine operator uses filesystem root "<storage_root>"
      And bucket "<bucket>" exists
      And observability listener capture, Micrometer metrics inspection, OpenTelemetry tracing inspection, and operational log inspection are enabled
      And an S3 client has object content from fixture file "<fixture_file>" for bucket "<bucket>" and key "<object_key>"
      And the object content contains the sentinel text "SECRET-PAYLOAD-MUST-NOT-LEAK"
      And the S3 client applies user metadata "x-amz-meta-secret=top-secret; x-amz-meta-token=token-123; x-amz-meta-customer-email=customer@example.test"
      And the S3 client sends authorization material that contains "AWS4-HMAC-SHA256 Credential=AKIA-MUST-NOT-LEAK"
      When the selected validation runner uploads fixture file "<fixture_file>" to bucket "<bucket>" and key "<object_key>"
      And the selected validation runner reads bucket "<bucket>" and key "<object_key>"
      Then listener events include correlation id, request id, operation, stage, outcome, numeric byte count, numeric chunk count, and timing
      And metrics include low-cardinality operation, stage, backend, outcome, and classification tags only
      And tracing spans include safe request, correlation, stage, backend, outcome, manifest, and timing attributes only
      And operational logs include structured safe fields needed to troubleshoot the request and stage outcomes
      And events, metrics, traces, and logs do not contain "SECRET-PAYLOAD-MUST-NOT-LEAK", "top-secret", "token-123", "customer@example.test", or "AKIA-MUST-NOT-LEAK"
      And redacted signals consistently replace forbidden metadata values with "<redacted-marker>" or omit the field according to the documented policy
      And the S3 client still receives the original object body through GetObject, proving redaction affects observability signals and not stored object content

      @pipeline-unit
      Examples: Pipeline unit redaction validation
        | requirement_id | validation_mode | bucket                | object_key                                       | fixture_file                            | storage_root                                   | redacted-marker |
        | REQ-OBS-006    | pipeline-unit   | obs-redaction-bucket | observability/2026/redaction/secret-object.txt   | fixtures/observability/secret-body.txt | target/storage-engine-it/REQ-OBS-006-unit      | [REDACTED]      |

      @webclient @metrics-inspection @tracing-inspection @log-inspection
      Examples: WebTestClient full observability redaction validation
        | requirement_id | validation_mode          | bucket                | object_key                                       | fixture_file                            | storage_root                                        | redacted-marker |
        | REQ-OBS-006    | observability-inspection | obs-redaction-bucket | observability/2026/redaction/secret-object.txt   | fixtures/observability/secret-body.txt | target/storage-engine-it/REQ-OBS-006-observability | [REDACTED]      |
