@spec @phase-3 @reactive-pipeline @storage-engine
Ability: Phase 3 staged reactive read and write pipeline
  As a storage-engine operator, S3-compatible client, and system owner,
  I want object writes and reads to flow through explicit reactive stages with shared
  context and typed events,
  So that large objects are persisted and streamed with bounded memory, deterministic
  failure handling, cleanup on cancellation, and independently observable stage behavior.

  This feature is the single source of truth for Phase 3 reactive pipeline requirements.
  It complements the Phase 1 upload and Phase 2 filesystem reliability requirements; it
  does not restate their durable-upload or checksum contracts. Instead, it defines the
  staged pipeline contracts required to satisfy those externally visible S3 behaviors
  without a monolithic orchestrator or whole-object aggregation.

  The Phase 3 implementation must introduce the production abstractions StorageStage,
  StorageContext, and StorageEvent. StorageContext carries request identity, object
  metadata, conditional segmentation decisions, resource handles, and cleanup state
  between stages. StorageEvent records stage start, success, failure, retry, cleanup,
  cancellation, and timing without carrying object payload bytes.

  Pipeline-unit scenarios may be promoted independently when production
  StorageStage/StorageContext/StorageEvent behavior is executable through a focused runner.
  End-to-end promotion additionally requires WebTestClient evidence that the storage-engine
  S3 adapter delegates through the same staged pipeline and publishes committed artifacts.
  Keep every requirement ID with the REQ-PIPELINE-* prefix unchanged when scenarios become executable.

  Validation roles:
    - Pipeline unit runner validates StorageStage ordering, StorageContext evolution,
      backpressure, cancellation, failure propagation, and StorageEvent sequences using
      reactive test publishers and StepVerifier-style demand control.
    - WebTestClient runner validates S3-observable PutObject and GetObject behavior
      through RouterFunction endpoints with streaming request and response bodies.
    - Storage-engine filesystem inspection validates temporary chunk files, committed
      manifests, object references, and cleanup artifacts in the configured storage root.
    - Instrumentation observer captures typed StorageEvent records and validates that
      observers can be attached per stage without changing payload demand or object bytes.

  Required write pipeline stage order:
    | order | stage name               | purpose                                                                    |
    | 1     | validation               | validate bucket, key, headers, metadata, checksum declarations, and length  |
    | 2     | policy-resolution        | resolve storage backend, storage policy, chunk size, and durability options |
    | 3     | chunking                 | pass through a plain object, or segment only for multipart, dedup, or EC     |
    | 4     | dedup-lookup             | identify dedup chunks only when deduplication is enabled                     |
    | 5     | chunk-persistence        | persist whole units or allowed segmented artifacts with bounded demand       |
    | 6     | manifest-persistence     | publish a manifest only after every referenced storage artifact is durable   |
    | 7     | object-index-persistence | publish the object reference only after the manifest is committed           |

  Required read pipeline stage order:
    | order | stage name         | purpose                                                                    |
    | 1     | validation         | validate bucket, key, range and conditional headers where applicable        |
    | 2     | policy-resolution  | resolve storage backend, storage policy, and read options                   |
    | 3     | read-planning      | load and verify the object reference and manifest before reading chunks     |
    | 4     | chunk-reading      | read verified chunks according to the manifest order                        |
    | 5     | response-streaming | stream response bytes and metadata to the S3 HTTP response with backpressure |

  Reusable fixtures for Phase 3 pipeline requirements:
    | fixture id              | resource path                                      | project path                                                       | intended content                                  |
    | small-object            | fixtures/upload/small-object.txt                  | s3-reactive-api-adapter/src/test/resources/fixtures/upload/small-object.txt | UTF-8 text: Hello durable Magrathea!              |
    | corruptible-object      | fixtures/upload/corruptible-object.bin            | s3-reactive-api-adapter/src/test/resources/fixtures/upload/corruptible-object.bin | deterministic 4 KiB repeating-byte binary payload |
    | generated-large-object  | target/test-fixtures/pipeline/large-object-256m.bin | target/test-fixtures/pipeline/large-object-256m.bin                | deterministic 256 MiB binary payload generated by the validation runner |

  Reusable storage-engine filesystem root pattern:
    | purpose             | path                                                        |
    | scenario filesystem | target/storage-engine-it/<requirement-id>-<validation-mode> |

  Requirement buckets, nested object keys, and fixture choices:
    | requirement      | bucket                         | key                                             | fixture resource path                                      |
    | REQ-PIPELINE-001 | pipeline-stage-order-bucket    | pipeline/2026/write/stage-order-object.txt      | fixtures/upload/small-object.txt                          |
    | REQ-PIPELINE-002 | pipeline-backpressure-bucket   | pipeline/2026/write/large-streamed-object.bin   | target/test-fixtures/pipeline/large-object-256m.bin        |
    | REQ-PIPELINE-003 | pipeline-read-order-bucket     | pipeline/2026/read/manifest-ordered-object.bin  | target/test-fixtures/pipeline/large-object-256m.bin        |
    | REQ-PIPELINE-004 | pipeline-failure-bucket        | pipeline/2026/failure/stage-failure-object.bin  | fixtures/upload/corruptible-object.bin                    |
    | REQ-PIPELINE-005 | pipeline-cancellation-bucket   | pipeline/2026/cancel/cancelled-object.bin       | target/test-fixtures/pipeline/large-object-256m.bin        |
    | REQ-PIPELINE-006 | pipeline-instrumentation-bucket | pipeline/2026/events/instrumented-object.txt    | fixtures/upload/small-object.txt                          |

  Background:
    Given the S3 API is configured with profile "storage-engine-it" and backend "storage-engine"
    And the storage engine stores bytes, manifests, and object references on a real filesystem
    And each scenario uses a clean storage-engine filesystem root "target/storage-engine-it/<scenario-id>"
    And reactive pipeline event capture is enabled for the selected validation mode

  Rule: Plain uploads remain whole-object storage units
    A single-object PutObject with multipart, deduplication, and erasure coding disabled
    MUST remain one streamed whole-object storage unit. It MUST NOT be split into fixed
    windows or create dedup-index entries, EC shards, or multipart parts. The current
    chunk-compatible manifest naming is tracked separately from this no-forced-splitting
    regression contract.

    @REQ-PIPELINE-014 @functional-requirement @non-functional-requirement @streaming @pipeline-unit-required @pipeline-unit @implemented-and-validated
    Scenario: Plain pipeline processing does not reintroduce forced upload chunking
      Given validation mode "pipeline-unit" is selected for requirement "REQ-PIPELINE-014"
      And the storage engine operator uses filesystem root "target/storage-engine-it/REQ-PIPELINE-014-no-split-unit"
      And bucket "plain-storage-unit-bucket" exists
      And storage class "PLAIN" disables multipart, deduplication, and erasure coding
      And fixture file "target/test-fixtures/pipeline/plain-object-8m.bin" is a deterministic 8 MiB object
      When the pipeline unit runner uploads the plain fixture to key "pipeline/2026/plain/whole-object.bin"
      Then the chunking stage records a whole-object pass-through decision
      And persistence receives one FileUnit for the complete 8 MiB stream rather than fixed-size ChunkUnit windows
      And no dedup content-address entry, EC shard, or multipart part is created
      And exact streamed readback matches the 8 MiB fixture

    @REQ-PIPELINE-014 @functional-requirement @non-functional-requirement @storage-layout @pipeline-unit-required @webclient-required @not-implemented
    Scenario Outline: Plain manifest and filesystem layout use explicit whole-object artifact terminology
      Given validation mode "<validation_mode>" is selected for requirement "<requirement_id>"
      And the storage engine operator uses filesystem root "<storage_root>"
      And bucket "plain-storage-unit-bucket" exists
      And storage class "PLAIN" disables multipart, deduplication, and erasure coding
      And fixture file "target/test-fixtures/pipeline/plain-object-8m.bin" is a deterministic 8 MiB object
      When the selected validation runner uploads the plain fixture to key "pipeline/2026/plain/whole-object.bin"
      Then the chunking stage records a whole-object pass-through decision
      And the manifest references one whole-object storage unit and zero chunk artifacts
      And no dedup content-address entry, EC shard, or multipart part is created
      And the S3 client reads the exact 8 MiB fixture bytes through the production read path

      @pipeline-unit @not-implemented
      Examples: Pipeline unit validation
        | requirement_id     | validation_mode | storage_root                                        |
        | REQ-PIPELINE-014   | pipeline-unit   | target/storage-engine-it/REQ-PIPELINE-014-unit     |

      @webclient @not-implemented
      Examples: WebTestClient validation
        | requirement_id     | validation_mode | storage_root                                        |
        | REQ-PIPELINE-014   | webclient       | target/storage-engine-it/REQ-PIPELINE-014-webclient |

  Rule: Erasure coding owns stripe and shard chunking
    When erasure coding is enabled, segmentation MUST be derived from the EC policy and
    produce ordered data/parity shard artifacts. EC-disabled plain uploads MUST produce no
    EC chunks, and modeled plans alone MUST NOT be reported as physical shard persistence.

    @REQ-PIPELINE-015 @functional-requirement @non-functional-requirement @integrity @erasure-coding @storage-layout @pipeline-unit-required @webclient-required @not-implemented
    Scenario Outline: EC-enabled PutObject persists policy-derived data and parity shards
      Given validation mode "<validation_mode>" is selected for requirement "<requirement_id>"
      And the storage engine operator uses filesystem root "<storage_root>"
      And bucket "ec-storage-unit-bucket" exists
      And storage class "EC_4_2" selects four data shards and two parity shards
      And fixture file "target/test-fixtures/pipeline/ec-object-8m.bin" is a deterministic 8 MiB object
      When the selected validation runner uploads the EC fixture to key "pipeline/2026/ec/sharded-object.bin"
      Then the EC stage persists ordered data and parity shard artifacts derived from the policy
      And the manifest distinguishes EC shards from dedup chunks and whole-object units
      And every shard checksum is validated before exact S3 readback

      @pipeline-unit @not-implemented
      Examples: Pipeline unit validation
        | requirement_id     | validation_mode | storage_root                                        |
        | REQ-PIPELINE-015   | pipeline-unit   | target/storage-engine-it/REQ-PIPELINE-015-unit     |

      @webclient @not-implemented
      Examples: WebTestClient validation
        | requirement_id     | validation_mode | storage_root                                        |
        | REQ-PIPELINE-015   | webclient       | target/storage-engine-it/REQ-PIPELINE-015-webclient |

  Rule: Write objects pass through explicit stages in a deterministic order
    A PutObject write MUST be composed from StorageStage instances connected by a
    StorageContext. The write pipeline MUST publish its whole-object unit or allowed
    multipart/dedup/EC artifacts, then the manifest, then the object reference in that
    order. Later stages MUST NOT run before earlier required stages have succeeded.

    @implemented-and-validated @REQ-PIPELINE-001 @functional-requirement @integrity @stage-ordering @pipeline-unit-required @webclient-required
    Scenario Outline: Write pipeline records the required stage order before publishing an object reference
      Given validation mode "<validation_mode>" is selected for requirement "<requirement_id>"
      And the storage engine operator uses filesystem root "<storage_root>"
      And bucket "<bucket>" exists
      And an S3 client has object content from fixture file "<fixture_file>" for bucket "<bucket>" and key "<object_key>"
      When the selected validation runner submits the fixture stream to the staged PutObject pipeline
      Then the write pipeline is assembled from StorageStage instances named in the required write pipeline stage order
      And a single StorageContext carries bucket "<bucket>", key "<object_key>", request metadata, chunk decisions, manifest identifier, and cleanup handles across those stages
      And StorageEvent records show start and success for the write stages in this exact order:
        | stage name               |
        | validation               |
        | policy-resolution        |
        | chunking                 |
        | dedup-lookup             |
        | chunk-persistence        |
        | manifest-persistence     |
        | object-index-persistence |
      And no manifest is committed before chunk-persistence succeeds for every referenced chunk
      And no object reference is committed before manifest-persistence succeeds
      And no content-address entry is published when the selected policy produces no dedup chunks
      And every committed manifest chunk reference uses a canonical UUID filename with a matching SHA-256 sidecar readable by the canonical filesystem node
      And a recovery scan of filesystem root "<storage_root>" reports no incomplete chunk artifacts after publication
      And after object-index-persistence succeeds, the selected validation runner reads the committed object through its declared production read entry point and receives the exact bytes from fixture file "<fixture_file>"

      @pipeline-unit
      Examples: Pipeline unit validation
        | requirement_id   | validation_mode | bucket                      | object_key                                | fixture_file                     | storage_root                                   |
        | REQ-PIPELINE-001 | pipeline-unit   | pipeline-stage-order-bucket | pipeline/2026/write/stage-order-object.txt | fixtures/upload/small-object.txt | target/storage-engine-it/REQ-PIPELINE-001-unit |

      @webclient
      Examples: WebTestClient end-to-end validation
        | requirement_id   | validation_mode | bucket                      | object_key                                | fixture_file                     | storage_root                                        |
        | REQ-PIPELINE-001 | webclient       | pipeline-stage-order-bucket | pipeline/2026/write/stage-order-object.txt | fixtures/upload/small-object.txt | target/storage-engine-it/REQ-PIPELINE-001-webclient |

  Rule: Dedup chunk persistence preserves backpressure and bounded memory
    When deduplication is enabled, the write pipeline MUST convert object content into
    bounded dedup windows and persist them according to downstream demand. It MUST NOT use
    a global reduce, collectList, or byte array assembly over the complete object body in
    production object-content paths. This rule does not authorize chunking plain uploads.

    @implemented-and-validated @REQ-PIPELINE-002 @non-functional-requirement @streaming @backpressure @bounded-memory @pipeline-unit-required @webclient-required
    Scenario Outline: Large PutObject persists chunks with bounded demand and without whole-object aggregation
      Given validation mode "<validation_mode>" is selected for requirement "<requirement_id>"
      And the storage engine operator uses filesystem root "<storage_root>"
      And bucket "<bucket>" exists
      And fixture file "<fixture_file>" is a deterministic 256 MiB object
      And storage class "PIPELINE" selects the bounded streaming policy for this upload
      And the write pipeline chunk size is "1 MiB" with at most "4" in-flight chunks
      And the upload body is supplied as a demand-controlled stream for bucket "<bucket>" and key "<object_key>"
      When the selected validation runner uploads fixture file "<fixture_file>" through the staged PutObject pipeline
      Then chunking emits ordered chunks no larger than the configured chunk size
      And chunk-persistence requests more chunks only as downstream capacity becomes available
      And the number of payload buffers retained in memory never exceeds the configured in-flight chunk limit
      And the measured payload memory retained by the pipeline remains bounded by the configured chunk window plus codec overhead, not by total object size
      And the committed manifest references all chunks in write order with the correct total object length
      And production object-content stages do not perform a global reduce, collectList, or whole-object byte-array assembly over the 256 MiB body
      And the S3 client can read bucket "<bucket>" and key "<object_key>" and receive the exact bytes from fixture file "<fixture_file>"

      @pipeline-unit
      Examples: Pipeline unit validation
        | requirement_id   | validation_mode | bucket                       | object_key                                  | fixture_file                                       | storage_root                                      |
        | REQ-PIPELINE-002 | pipeline-unit   | pipeline-backpressure-bucket | pipeline/2026/write/large-streamed-object.bin | target/test-fixtures/pipeline/large-object-256m.bin | target/storage-engine-it/REQ-PIPELINE-002-unit    |

      @webclient
      Examples: WebTestClient validation
        | requirement_id   | validation_mode | bucket                       | object_key                                  | fixture_file                                       | storage_root                                         |
        | REQ-PIPELINE-002 | webclient       | pipeline-backpressure-bucket | pipeline/2026/write/large-streamed-object.bin | target/test-fixtures/pipeline/large-object-256m.bin | target/storage-engine-it/REQ-PIPELINE-002-webclient |

  Rule: Upload ETag computation and dedup windowing preserve streaming request-body paths
    PutObject MUST compute ETag and supported request checksums as a tee over the incoming
    DataBuffer stream while forwarding the same logical bytes into the selected storage path.
    It MUST NOT aggregate the complete request body with DataBufferUtils.join or re-wrap a
    whole-object byte array before calling storage. Fixed-window deduplication MUST consume
    FileUnit DataBuffers incrementally and emit configured-size windows without first
    materializing the complete FileUnit.

    @implemented-and-validated @REQ-PIPELINE-007 @functional-requirement @non-functional-requirement @streaming @integrity @bounded-memory @static-architecture-required @s3-api
    Scenario: PutObject computes ETag while teeing the request body into storage
      Given validation mode "static-architecture" is selected for requirement "REQ-PIPELINE-007"
      And production source path "s3-reactive-api-adapter/src/main/java/com/example/magrathea/s3api/adapter/web/S3ObjectOperationsHandler.java" implements the S3 PutObject route
      And bucket "pipeline-backpressure-bucket" exists for key "pipeline/2026/write/large-streamed-object.bin"
      When the static architecture runner inspects method "putObject" in the production source path
      Then method "putObject" does not invoke "DataBufferUtils.join"
      And method "putObject" does not materialize the request body into a whole-object "byte[] bytes" array
      And method "putObject" does not pass storage a Flux created by re-wrapping a whole-object byte array
      And the implementation computes the single-part ETag and supported checksum headers while teeing the DataBuffer stream into saveObjectWithContent
      And the S3 PutObject response can expose the computed ETag without requiring a second full-body aggregation

    @implemented-and-validated @REQ-PIPELINE-008 @non-functional-requirement @streaming @deduplication @bounded-memory @static-architecture-required @storage-engine
    Scenario: Fixed-window deduplication emits configured windows without joining the FileUnit
      Given validation mode "static-architecture" is selected for requirement "REQ-PIPELINE-008"
      And production source path "storage-engine-reactive-infrastructure/src/main/java/com/example/magrathea/storageengine/infrastructure/pipeline/FixedWindowDedupStep.java" implements fixed-window deduplication
      And the configured dedup window size is "1 MiB"
      When the static architecture runner inspects the production source path
      Then FixedWindowDedupStep does not invoke "DataBufferUtils.join"
      And FixedWindowDedupStep does not materialize the complete FileUnit into a whole-object "byte[] allBytes" array
      And FixedWindowDedupStep fingerprints and looks up each configured window as DataBuffers are consumed incrementally
      And emitted ChunkUnit data is bounded by the configured dedup window size rather than total FileUnit size

  Rule: Read objects stream chunks in manifest order without aggregating the object
    GetObject MUST use a staged read pipeline that loads the object reference and manifest,
    verifies read preconditions, reads chunks in manifest order, and streams them to the
    S3 response according to subscriber demand. The read path MUST NOT collect all chunks
    before response streaming begins.

    @implemented-and-validated @REQ-PIPELINE-009 @functional-requirement @non-functional-requirement @streaming @bounded-memory @static-architecture-required @s3-api
    Scenario: Non-range GetObject attaches the content Flux directly to the S3 response without collecting chunks
      Given validation mode "static-architecture" is selected for requirement "REQ-PIPELINE-009"
      And production source path "s3-reactive-api-adapter/src/main/java/com/example/magrathea/s3api/adapter/web/S3ObjectOperationsHandler.java" implements the S3 GetObject route
      When the static architecture runner inspects method "getObject" in the production source path
      Then method "getObject" does not collect object content before a non-range response
      And method "getObject" passes "oc.content()" through the shared finite-demand boundary to "S3ResponseBuilder.okWithBody"
      And range handling streams through the explicit Range header branch

    @implemented-and-validated @REQ-PIPELINE-010 @functional-requirement @non-functional-requirement @streaming @bounded-memory @range @static-architecture-required @s3-api
    Scenario: Ranged GetObject slices the content Flux without collecting the full object
      Given validation mode "static-architecture" is selected for requirement "REQ-PIPELINE-010"
      And production source path "s3-reactive-api-adapter/src/main/java/com/example/magrathea/s3api/adapter/web/S3ObjectOperationsHandler.java" implements the S3 GetObject route
      When the static architecture runner inspects method "getObject" in the production source path
      Then method "getObject" does not invoke "collectList"
      And method "getObject" passes "oc.content()" directly to range helper "serveRange"
      When the static architecture runner inspects the production source path
      Then helper "serveRange" accepts streaming content as "Flux<DataBuffer> content" instead of a collected buffer list
      And helper "serveRange" attaches "S3StreamingBody.sliceRange(content, start, effectiveEnd)" to "BodyInserters.fromDataBuffers"
      And shared helper "S3StreamingBody.sliceRange" emits finite-demand per-buffer range slices without building a full-object array

    @partial @REQ-PIPELINE-003 @functional-requirement @non-functional-requirement @streaming @backpressure @integrity @pipeline-unit-required
    Scenario Outline: Pipeline-unit GetObject streams manifest-ordered chunks under controlled demand
      Given validation mode "<validation_mode>" is selected for requirement "<requirement_id>"
      And the storage engine operator uses filesystem root "<storage_root>"
      And bucket "<bucket>" exists
      And storage class "PIPELINE" selects the bounded streaming policy for this upload
      And the write pipeline chunk size is "1 MiB" with at most "4" in-flight chunks
      And a committed object exists in bucket "<bucket>" at key "<object_key>" uploaded from fixture file "<fixture_file>"
      And the committed manifest lists multiple chunks with stable ordinal positions and checksums
      When the selected validation runner reads bucket "<bucket>" and key "<object_key>" through the staged GetObject pipeline
      Then StorageEvent records show start and success for the read stages in this exact order:
        | stage name         |
        | validation         |
        | policy-resolution  |
        | read-planning      |
        | chunk-reading      |
        | response-streaming |
      And chunk-reading emits chunks in the same ordinal order recorded in the committed manifest
      And response-streaming begins after the first verified chunk is available, without waiting for every chunk to be read
      And downstream response demand controls how many chunks are read ahead
      And production read stages do not perform a global reduce, collectList, or whole-object byte-array assembly over the object content
      And the streamed S3 response bytes exactly match fixture file "<fixture_file>"

      @pipeline-unit
      Examples: Pipeline unit validation
        | requirement_id   | validation_mode | bucket                     | object_key                                      | fixture_file                                       | storage_root                                      |
        | REQ-PIPELINE-003 | pipeline-unit   | pipeline-read-order-bucket | pipeline/2026/read/manifest-ordered-object.bin | target/test-fixtures/pipeline/large-object-256m.bin | target/storage-engine-it/REQ-PIPELINE-003-unit    |

  Rule: HTTP read integrity preflight preserves deterministic S3 errors
    The HTTP adapter additionally preserves REQ-FS-003/004 deterministic S3 XML errors by
    completing a bounded integrity preflight before committing response headers. This preflight
    does not retain object bytes, but it reads persisted chunks before opening the separately
    backpressured response stream; therefore WebTestClient evidence does not claim first-byte
    latency or single-pass filesystem I/O.

    @partial @REQ-PIPELINE-003 @functional-requirement @non-functional-requirement @streaming @integrity @webclient-required
    Scenario Outline: WebTestClient GetObject preserves exact bytes and deterministic integrity preflight
      Given validation mode "<validation_mode>" is selected for requirement "<requirement_id>"
      And the storage engine operator uses filesystem root "<storage_root>"
      And bucket "<bucket>" exists
      And storage class "PIPELINE" selects the bounded streaming policy for this upload
      And the write pipeline chunk size is "1 MiB" with at most "4" in-flight chunks
      And a committed object exists in bucket "<bucket>" at key "<object_key>" uploaded from fixture file "<fixture_file>"
      And the committed manifest lists multiple chunks with stable ordinal positions and checksums
      When the selected validation runner reads bucket "<bucket>" and key "<object_key>" through the staged GetObject pipeline
      Then StorageEvent records show start and success for the read stages in this exact order:
        | stage name         |
        | validation         |
        | policy-resolution  |
        | read-planning      |
        | chunk-reading      |
        | response-streaming |
      And chunk-reading emits chunks in the same ordinal order recorded in the committed manifest
      And the HTTP adapter performs a bounded integrity preflight before response commitment without retaining object bytes
      And production read stages do not perform a global reduce, collectList, or whole-object byte-array assembly over the object content
      And the streamed S3 response bytes exactly match fixture file "<fixture_file>"

      @webclient
      Examples: WebTestClient validation
        | requirement_id   | validation_mode | bucket                     | object_key                                      | fixture_file                                       | storage_root                                         |
        | REQ-PIPELINE-003 | webclient       | pipeline-read-order-bucket | pipeline/2026/read/manifest-ordered-object.bin | target/test-fixtures/pipeline/large-object-256m.bin | target/storage-engine-it/REQ-PIPELINE-003-webclient |

  Rule: Multipart part persistence streams upload and copy inputs without complete-part aggregation
    UploadPart and UploadPartCopy MUST persist part bytes as a stream while computing the
    part ETag and size incrementally. The multipart part store MUST write and read part
    files as DataBuffer streams so CompleteMultipartUpload can concatenate part streams
    without loading each part file as one byte array.

    @implemented-and-validated @REQ-PIPELINE-011 @functional-requirement @non-functional-requirement @streaming @bounded-memory @multipart @static-architecture-required @s3-api
    Scenario: UploadPart persists request DataBuffers without joining the complete part body
      Given validation mode "static-architecture" is selected for requirement "REQ-PIPELINE-011"
      And production source path "s3-reactive-api-adapter/src/main/java/com/example/magrathea/s3api/adapter/web/S3MultipartHandler.java" implements the S3 multipart handler
      When the static architecture runner inspects method "uploadPart" in the production source path
      Then method "uploadPart" does not invoke "DataBufferUtils.join"
      And method "uploadPart" does not materialize the request body into a whole-object "byte[] bytes" array
      And method "uploadPart" passes "request.bodyToFlux(DataBuffer.class)" directly to multipart part storage
      When the static architecture runner inspects method "uploadPartCopy" in the production source path
      Then method "uploadPartCopy" does not invoke "DataBufferUtils.join"
      And method "uploadPartCopy" streams copied source content directly to multipart part storage

    @implemented-and-validated @REQ-PIPELINE-012 @non-functional-requirement @streaming @bounded-memory @multipart @static-architecture-required @s3-api
    Scenario: Multipart part store writes and reads part files as bounded DataBuffer streams
      Given validation mode "static-architecture" is selected for requirement "REQ-PIPELINE-012"
      And production source path "s3-reactive-api-adapter/src/main/java/com/example/magrathea/s3api/adapter/web/S3MultipartPartStore.java" implements multipart part body storage
      When the static architecture runner inspects method "savePart" in the production source path
      Then method "savePart" accepts multipart content as "Flux<DataBuffer> content"
      And method "savePart" writes multipart content with "DataBufferUtils.write"
      And method "savePart" computes part measurements incrementally for each DataBuffer
      And method "savePart" does not materialize the request body into a whole-object "byte[] bytes" array
      When the static architecture runner inspects method "readPart" in the production source path
      Then method "readPart" reads multipart content with "DataBufferUtils.read"
      And method "readPart" does not invoke "Files.readAllBytes"

  Rule: Existing S3 streaming boundaries apply a finite runtime demand window
    Before the complete staged StorageStage pipeline is claimed end to end, the existing
    PutObject, GetObject, Range, UploadPart, UploadPartCopy, and multipart part-file
    boundaries MUST apply a shared finite Reactor demand window. Runtime evidence for this
    boundary does not validate stage ordering, manifest publication, or StorageEvent behavior.

    @implemented-and-validated @REQ-PIPELINE-013 @non-functional-requirement @streaming @backpressure @bounded-memory @runtime-backpressure-required @s3-api @multipart
    Scenario: S3 object and multipart streaming boundaries cap upstream DataBuffer demand at runtime
      Given validation mode "runtime-backpressure" is selected for requirement "REQ-PIPELINE-013"
      And a demand-controlled source contains 12 ordered DataBuffers of 65536 bytes
      When the runtime runner streams the source through each production S3 object body boundary
      Then PutObject, GetObject, and ranged GetObject retain at most the shared demand-window number of source DataBuffers
      And each object path emits the expected ordered bytes without whole-body aggregation
      When the runtime runner saves the source through multipart part storage
      Then UploadPart and UploadPartCopy retain at most the shared demand-window number of source DataBuffers
      And multipart part-file reads emit buffers no larger than 65536 bytes and reproduce the expected ordered bytes
      And this evidence does not claim the complete staged StorageStage pipeline

  Rule: Stage failures propagate deterministically and clean up completed resources
    When any required write stage fails, the pipeline MUST emit a typed failure event,
    stop later stages, execute cleanup for resources owned by completed stages, and return
    a deterministic S3 error without publishing a readable object.

    @REQ-PIPELINE-004 @functional-requirement @non-functional-requirement @integrity @failure-propagation @cleanup @pipeline-unit-required @webclient-required @partial
    Scenario Outline: Failed write stage reports one deterministic failure and leaves no published object
      Given validation mode "<validation_mode>" is selected for requirement "<requirement_id>"
      And the storage engine operator uses filesystem root "<storage_root>"
      And bucket "<bucket>" exists
      And no object exists in bucket "<bucket>" for key "<object_key>"
      And an S3 client has object content from fixture file "<fixture_file>" for bucket "<bucket>" and key "<object_key>"
      And the pipeline failure injector causes stage "<failing_stage>" to fail with reason "<failure_reason>"
      When the selected validation runner uploads fixture file "<fixture_file>" through the staged PutObject pipeline
      Then the pipeline emits exactly one StorageEvent failure for stage "<failing_stage>" with reason "<failure_reason>"
      And no stage after "<failing_stage>" emits a success event for this StorageContext
      And cleanup events run for every completed stage that owns temporary files, open buffers, or object publication handles
      And all temporary files created for the failed write are removed or quarantined in filesystem root "<storage_root>"
      And no committed manifest or object reference is published for bucket "<bucket>" and key "<object_key>"
      And the S3 PutObject response exposes a deterministic storage failure rather than a partial success
      And a later S3 GetObject for bucket "<bucket>" and key "<object_key>" reports that the object is absent

      @pipeline-unit
      Examples: Pipeline unit validation
        | requirement_id   | validation_mode | failing_stage       | failure_reason             | bucket                  | object_key                                      | fixture_file                           | storage_root                                      |
        | REQ-PIPELINE-004 | pipeline-unit   | chunk-persistence   | simulated chunk write fault | pipeline-failure-bucket | pipeline/2026/failure/stage-failure-object.bin | fixtures/upload/corruptible-object.bin | target/storage-engine-it/REQ-PIPELINE-004-unit    |
        | REQ-PIPELINE-004 | pipeline-unit   | manifest-persistence | simulated manifest fault    | pipeline-failure-bucket | pipeline/2026/failure/stage-failure-object.bin | fixtures/upload/corruptible-object.bin | target/storage-engine-it/REQ-PIPELINE-004-unit-2  |

      @webclient
      Examples: WebTestClient validation
        | requirement_id   | validation_mode | failing_stage     | failure_reason             | bucket                  | object_key                                      | fixture_file                           | storage_root                                         |
        | REQ-PIPELINE-004 | webclient       | chunk-persistence | simulated chunk write fault | pipeline-failure-bucket | pipeline/2026/failure/stage-failure-object.bin | fixtures/upload/corruptible-object.bin | target/storage-engine-it/REQ-PIPELINE-004-webclient |

    @REQ-PIPELINE-004 @functional-requirement @non-functional-requirement @integrity @failure-propagation @cleanup @pipeline-unit-required @partial @pipeline-unit
    Scenario: Upstream upload failure removes in-progress atomic chunk artifacts
      Given validation mode "pipeline-unit" is selected for requirement "REQ-PIPELINE-004"
      And the storage engine operator uses filesystem root "target/storage-engine-it/REQ-PIPELINE-004-upstream-unit"
      And bucket "pipeline-upstream-failure-bucket" exists
      And no object exists in bucket "pipeline-upstream-failure-bucket" for key "pipeline/2026/failure/upstream-body-object.bin"
      And an upload body emits one DataBuffer and then fails with reason "simulated upstream body failure"
      When the pipeline unit runner submits the failing upload body to the staged PutObject pipeline
      Then the pipeline emits exactly one StorageEvent failure for stage "chunk-persistence" with reason "simulated upstream body failure"
      And no stage after "chunk-persistence" emits a success event for this StorageContext
      And all temporary files created for the failed write are removed or quarantined in filesystem root "target/storage-engine-it/REQ-PIPELINE-004-upstream-unit"
      And no committed manifest or object reference is published for bucket "pipeline-upstream-failure-bucket" and key "pipeline/2026/failure/upstream-body-object.bin"
      And a later S3 GetObject for bucket "pipeline-upstream-failure-bucket" and key "pipeline/2026/failure/upstream-body-object.bin" reports that the object is absent

  Rule: Cancellation before publication cleans up pipeline-owned resources
    If a client cancels a PutObject stream before manifest or object-reference publication,
    the pipeline MUST propagate cancellation to active stages, release buffers, clean up
    unpublished chunks or temporary files, and leave no readable object behind.

    @REQ-PIPELINE-005 @functional-requirement @non-functional-requirement @streaming @cancellation @cleanup @pipeline-unit-required @webclient-required @implemented-and-validated
    Scenario Outline: Cancelled upload releases resources before manifest or object reference publication
      Given validation mode "<validation_mode>" is selected for requirement "<requirement_id>"
      And the storage engine operator uses filesystem root "<storage_root>"
      And bucket "<bucket>" exists
      And no object exists in bucket "<bucket>" for key "<object_key>"
      And fixture file "<fixture_file>" is a deterministic 256 MiB object
      And storage class "PIPELINE" selects the bounded streaming policy for this upload
      And the write pipeline chunk size is "1 MiB" with at most "4" in-flight chunks
      And the staged PutObject pipeline has persisted at least "<persisted_chunk_count>" unpublished chunks for bucket "<bucket>" and key "<object_key>"
      When the selected validation runner cancels the upload subscription before manifest-persistence starts
      Then the pipeline emits a cancellation StorageEvent for the active StorageContext
      And cancellation cleanup is owned by the reactive pipeline lifecycle rather than a detached subscription
      And active upstream publishers stop receiving additional demand after cancellation
      And all retained DataBuffer instances and open file handles owned by the cancelled pipeline are released
      And cleanup events remove or quarantine unpublished chunks and temporary files in filesystem root "<storage_root>"
      And no manifest is committed for the cancelled upload
      And no object reference is committed for bucket "<bucket>" and key "<object_key>"
      And a later S3 GetObject for bucket "<bucket>" and key "<object_key>" reports that the object is absent

      @pipeline-unit
      Examples: Pipeline unit validation
        | requirement_id   | validation_mode | persisted_chunk_count | bucket                       | object_key                                | fixture_file                                       | storage_root                                      |
        | REQ-PIPELINE-005 | pipeline-unit   | 2                     | pipeline-cancellation-bucket | pipeline/2026/cancel/cancelled-object.bin | target/test-fixtures/pipeline/large-object-256m.bin | target/storage-engine-it/REQ-PIPELINE-005-unit    |

      @webclient
      Examples: WebTestClient validation
        | requirement_id   | validation_mode | persisted_chunk_count | bucket                       | object_key                                | fixture_file                                       | storage_root                                         |
        | REQ-PIPELINE-005 | webclient       | 2                     | pipeline-cancellation-bucket | pipeline/2026/cancel/cancelled-object.bin | target/test-fixtures/pipeline/large-object-256m.bin | target/storage-engine-it/REQ-PIPELINE-005-webclient |

  Rule: Each pipeline stage emits independent typed instrumentation events
    StorageStage implementations MUST emit StorageEvent records for lifecycle and timing
    observations. Event observers MUST be attachable per stage without changing payload
    demand, buffering object bytes, or coupling one stage's instrumentation to another.

    @REQ-PIPELINE-006 @non-functional-requirement @observability @instrumentation @pipeline-unit-required @implemented-and-validated
    Scenario Outline: StorageEvent records allow per-stage instrumentation without observing object payload bytes
      Given validation mode "<validation_mode>" is selected for requirement "<requirement_id>"
      And the storage engine operator uses filesystem root "<storage_root>"
      And bucket "<bucket>" exists
      And an instrumentation observer subscribes only to stage "<observed_stage>"
      And an S3 client has object content from fixture file "<fixture_file>" for bucket "<bucket>" and key "<object_key>"
      When the pipeline unit runner writes and then reads bucket "<bucket>" and key "<object_key>" through the staged pipelines
      Then the observer receives StorageEvent records only for stage "<observed_stage>" and the matching StorageContext correlation identifier
      And each observed StorageEvent includes event type, operation, stage name, bucket, key, timing information, and outcome
      And observed StorageEvent records do not include object payload bytes or complete chunk contents
      And enabling the observer does not increase requested upstream payload demand beyond the demand requested by the storage stages
      And other stages can be observed by adding their own observers without changing the stage implementation or the S3 API behavior
      And the S3 client reads bucket "<bucket>" and key "<object_key>" and receives the exact bytes from fixture file "<fixture_file>"

      @pipeline-unit
      Examples: Pipeline unit validation
        | requirement_id   | validation_mode | observed_stage      | bucket                          | object_key                                  | fixture_file                     | storage_root                                      |
        | REQ-PIPELINE-006 | pipeline-unit   | chunk-persistence   | pipeline-instrumentation-bucket | pipeline/2026/events/instrumented-object.txt | fixtures/upload/small-object.txt | target/storage-engine-it/REQ-PIPELINE-006-unit    |
        | REQ-PIPELINE-006 | pipeline-unit   | response-streaming  | pipeline-instrumentation-bucket | pipeline/2026/events/instrumented-object.txt | fixtures/upload/small-object.txt | target/storage-engine-it/REQ-PIPELINE-006-unit-2  |
