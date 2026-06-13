@requirement @phase-1 @upload @storage-engine @not-implemented
Feature: Phase 1 upload reliability requirements for the storage-engine backend
  These backlog scenarios describe required Phase 1 behavior for PutObject and GetObject
  when the S3 HTTP API is backed by the durable storage engine.

  The scenarios are intentionally stored outside the currently selected Cucumber
  feature folders. They are requirements only and must not be executed until
  matching implementation and step definitions are added.

  # Shared requirement contract for future executable implementations:
  # - Keep each @REQ-UPLOAD-* requirement ID unchanged.
  # - Implement two concrete runner scenarios or scenario outlines per requirement:
  #   one under the object-store/WebTestClient suite and one under the awscli/AWS CLI suite.
  # - Reuse the same bucket, key, fixture file, and storage root contract in both runner modes.
  # - WebTestClient steps may call RouterFunction endpoints directly; AWS CLI steps must use
  #   the same S3-compatible API through aws s3api/aws s3 commands where supported.
  #
  # Reusable fixtures for all Phase 1 upload requirements:
  # | fixture id         | resource path                                  | project path                                                                       | intended content                                    |
  # | small-object       | fixtures/upload/small-object.txt              | s3-reactive-api-adapter/src/test/resources/fixtures/upload/small-object.txt        | UTF-8 text: Hello durable Magrathea!                |
  # | large-object       | fixtures/upload/large-object.bin              | s3-reactive-api-adapter/src/test/resources/fixtures/upload/large-object.bin        | deterministic 256 MiB binary payload               |
  # | corruptible-object | fixtures/upload/corruptible-object.bin        | s3-reactive-api-adapter/src/test/resources/fixtures/upload/corruptible-object.bin  | deterministic binary payload for corruption checks  |
  #
  # Reusable storage-engine filesystem root:
  # | purpose              | path                                      |
  # | scenario filesystem  | target/storage-engine-it/<scenario-id>    |
  #
  # Requirement buckets, nested object keys, and fixture choices:
  # | requirement     | bucket                               | key                                             | fixture resource path                     |
  # | REQ-UPLOAD-001  | req-upload-restart-bucket            | documents/2026/restart-safe-object.txt         | fixtures/upload/small-object.txt          |
  # | REQ-UPLOAD-002  | req-upload-manifest-bucket           | manifests/2026/payload.bin                     | fixtures/upload/small-object.txt          |
  # | REQ-UPLOAD-003  | req-upload-large-bucket              | archive/2026/large/streamed-object.bin         | fixtures/upload/large-object.bin          |
  # | REQ-UPLOAD-004  | req-upload-atomicity-bucket          | uploads/2026/failed/partial-object.bin         | fixtures/upload/large-object.bin          |
  # | REQ-UPLOAD-005  | req-upload-read-after-write-bucket   | documents/2026/read-after-write/object.txt     | fixtures/upload/small-object.txt          |
  # | REQ-UPLOAD-006  | req-upload-integrity-bucket          | integrity/2026/corruptible-object.bin          | fixtures/upload/corruptible-object.bin    |

  Background:
    Given the S3 API is configured with profile "storage-engine-it" and backend "storage-engine"
    And the storage engine stores bytes, manifests, and object references on a real filesystem
    And each scenario uses a clean storage-engine filesystem root "target/storage-engine-it/<scenario-id>"

  @REQ-UPLOAD-001 @restart-safety @webclient-required @awscli-required @not-implemented
  Scenario: PutObject remains readable after application restart
    Given bucket "req-upload-restart-bucket" exists
    And the fixture file "fixtures/upload/small-object.txt" contains "Hello durable Magrathea!"
    And a client has object content from fixture file "fixtures/upload/small-object.txt" for bucket "req-upload-restart-bucket" and key "documents/2026/restart-safe-object.txt"
    And the client includes user metadata:
      | name       | value       |
      | project    | magrathea   |
      | durability | restart     |
    And the client requests storage class "STANDARD"
    When the client uploads fixture file "fixtures/upload/small-object.txt" to bucket "req-upload-restart-bucket" and key "documents/2026/restart-safe-object.txt" through the S3 HTTP PutObject API
    Then the upload result records a committed manifest identifier for the stored bytes
    And the object reference for bucket "req-upload-restart-bucket" and key "documents/2026/restart-safe-object.txt" points to that manifest identifier
    And the visible object attributes include storage class "STANDARD"
    And the visible object metadata includes "project" with value "magrathea"
    When the application process is stopped
    And all in-memory repositories and caches are discarded
    And the application process is started again using storage-engine filesystem root "target/storage-engine-it/REQ-UPLOAD-001"
    And the client reads bucket "req-upload-restart-bucket" and key "documents/2026/restart-safe-object.txt" through the S3 HTTP GetObject API
    Then the response body matches fixture file "fixtures/upload/small-object.txt"
    And the response metadata includes "project" with value "magrathea"
    And the response storage class is "STANDARD"
    And the read succeeds without reconstructing any object state from memory

  @REQ-UPLOAD-002 @manifest-durability @webclient-required @awscli-required @not-implemented
  Scenario: A committed upload persists a manifest and an object-to-manifest reference
    Given bucket "req-upload-manifest-bucket" exists
    And a client has object content from fixture file "fixtures/upload/small-object.txt" for bucket "req-upload-manifest-bucket" and key "manifests/2026/payload.bin"
    When the client uploads fixture file "fixtures/upload/small-object.txt" to bucket "req-upload-manifest-bucket" and key "manifests/2026/payload.bin" through the S3 HTTP PutObject API
    Then the storage engine has a durable committed manifest for the uploaded bytes
    And the manifest records the object byte length, chunk list, checksum metadata, and creation time
    And the S3 object repository has a durable reference from bucket "req-upload-manifest-bucket" and key "manifests/2026/payload.bin" to the manifest identifier
    When the object repository is reloaded from storage-engine filesystem root "target/storage-engine-it/REQ-UPLOAD-002"
    And the manifest repository is reloaded from storage-engine filesystem root "target/storage-engine-it/REQ-UPLOAD-002"
    Then bucket "req-upload-manifest-bucket" and key "manifests/2026/payload.bin" still resolve to the same manifest identifier
    And the resolved manifest can be used to stream the original bytes from fixture file "fixtures/upload/small-object.txt"

  @REQ-UPLOAD-003 @large-object @streaming @webclient-required @awscli-required @not-implemented
  Scenario: A large upload is streamed with bounded memory and exact read-back
    Given bucket "req-upload-large-bucket" exists
    And fixture file "fixtures/upload/large-object.bin" is a deterministic 256 MiB object
    And a client has object content from fixture file "fixtures/upload/large-object.bin" for bucket "req-upload-large-bucket" and key "archive/2026/large/streamed-object.bin"
    And the storage engine chunk size is configured to a bounded value smaller than the object
    When the client uploads fixture file "fixtures/upload/large-object.bin" to bucket "req-upload-large-bucket" and key "archive/2026/large/streamed-object.bin" through the S3 HTTP PutObject API using a streaming request body
    Then the storage engine writes the object as multiple durable chunks
    And no component materializes the complete 256 MiB object in memory at once
    And upload processing respects downstream backpressure while chunks are written
    And the committed manifest contains the ordered chunk references needed to reconstruct the object
    When the client reads bucket "req-upload-large-bucket" and key "archive/2026/large/streamed-object.bin" through the S3 HTTP GetObject API
    Then the streamed response bytes exactly match fixture file "fixtures/upload/large-object.bin"
    And the read path emits chunks in manifest order without loading the complete object into memory

  @REQ-UPLOAD-004 @atomicity @recovery @webclient-required @awscli-required @not-implemented
  Scenario: A failed partial upload does not publish a readable object or dangling committed manifest
    Given bucket "req-upload-atomicity-bucket" exists
    And no object exists in bucket "req-upload-atomicity-bucket" for key "uploads/2026/failed/partial-object.bin"
    And a client starts uploading fixture file "fixtures/upload/large-object.bin" to bucket "req-upload-atomicity-bucket" and key "uploads/2026/failed/partial-object.bin"
    When the upload fails before all bytes are durably written and committed
    Then bucket "req-upload-atomicity-bucket" and key "uploads/2026/failed/partial-object.bin" are not visible through the S3 HTTP GetObject API
    And bucket "req-upload-atomicity-bucket" and key "uploads/2026/failed/partial-object.bin" do not resolve to a committed manifest identifier
    And no committed manifest references missing or partial chunks for that failed upload
    When the application process is stopped
    And the application process is started again using storage-engine filesystem root "target/storage-engine-it/REQ-UPLOAD-004"
    Then bucket "req-upload-atomicity-bucket" and key "uploads/2026/failed/partial-object.bin" remain unreadable
    And recovery either removes uncommitted upload artifacts or keeps them isolated from committed object references

  @REQ-UPLOAD-005 @read-after-write @webclient-required @awscli-required @not-implemented
  Scenario: A successful PutObject is immediately readable from real filesystem persistence
    Given bucket "req-upload-read-after-write-bucket" exists
    And a client has object content from fixture file "fixtures/upload/small-object.txt" for bucket "req-upload-read-after-write-bucket" and key "documents/2026/read-after-write/object.txt"
    And the storage-engine repositories are backed by storage-engine filesystem root "target/storage-engine-it/REQ-UPLOAD-005"
    When the client uploads fixture file "fixtures/upload/small-object.txt" to bucket "req-upload-read-after-write-bucket" and key "documents/2026/read-after-write/object.txt" through the S3 HTTP PutObject API
    Then the upload is committed before the PutObject response is returned to the client
    And the filesystem contains the durable chunks, manifest, and S3 object reference for bucket "req-upload-read-after-write-bucket" and key "documents/2026/read-after-write/object.txt"
    When the same client immediately reads bucket "req-upload-read-after-write-bucket" and key "documents/2026/read-after-write/object.txt" through the S3 HTTP GetObject API
    Then the response body matches fixture file "fixtures/upload/small-object.txt"
    And the response is produced from the storage-engine filesystem state rather than an in-memory-only object cache

  @REQ-UPLOAD-006 @checksum @integrity @webclient-required @awscli-required @not-implemented
  Scenario: Stored chunks and manifests include verifiable integrity metadata and detect corruption on read
    Given bucket "req-upload-integrity-bucket" exists
    And a client has object content from fixture file "fixtures/upload/corruptible-object.bin" for bucket "req-upload-integrity-bucket" and key "integrity/2026/corruptible-object.bin"
    When the client uploads fixture file "fixtures/upload/corruptible-object.bin" to bucket "req-upload-integrity-bucket" and key "integrity/2026/corruptible-object.bin" through the S3 HTTP PutObject API
    Then each stored chunk has verifiable integrity metadata
    And the committed manifest records integrity metadata for the complete object and each chunk reference
    When one durable chunk for bucket "req-upload-integrity-bucket" and key "integrity/2026/corruptible-object.bin" is corrupted outside the application
    And the client reads bucket "req-upload-integrity-bucket" and key "integrity/2026/corruptible-object.bin" through the S3 HTTP GetObject API
    Then the storage engine detects the integrity mismatch before returning corrupted bytes as a successful object
    And the observable read result reports an object integrity failure
