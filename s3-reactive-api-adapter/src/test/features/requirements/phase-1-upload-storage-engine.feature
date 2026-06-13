@requirement @phase-1 @upload @storage-engine @not-implemented
Feature: Phase 1 upload reliability requirements for the storage-engine backend
  These backlog scenarios describe required Phase 1 behavior for PutObject and GetObject
  when the S3 HTTP API is backed by the durable storage engine.

  The scenarios are intentionally stored outside the currently selected Cucumber
  feature folders. They are requirements only and must not be executed until
  matching implementation and step definitions are added.

  Background:
    Given the S3 API is configured to use the storage-engine backend
    And the storage engine stores bytes, manifests, and object references on a real filesystem
    And bucket "phase-1-upload-bucket" exists

  @REQ-UPLOAD-001 @restart-safety
  Scenario: PutObject remains readable after application restart
    Given a client has object content "Hello durable Magrathea!" for key "restart-safety/hello.txt"
    And the client includes user metadata:
      | name       | value       |
      | project    | magrathea   |
      | durability | restart     |
    And the client requests storage class "STANDARD"
    When the client uploads the object through the S3 HTTP PutObject API
    Then the upload result records a committed manifest identifier for the stored bytes
    And the object reference for key "restart-safety/hello.txt" points to that manifest identifier
    And the visible object attributes include storage class "STANDARD"
    And the visible object metadata includes "project" with value "magrathea"
    When the application process is stopped
    And all in-memory repositories and caches are discarded
    And the application process is started again using the same storage-engine filesystem
    And the client reads key "restart-safety/hello.txt" through the S3 HTTP GetObject API
    Then the response body is exactly "Hello durable Magrathea!"
    And the response metadata includes "project" with value "magrathea"
    And the response storage class is "STANDARD"
    And the read succeeds without reconstructing any object state from memory

  @REQ-UPLOAD-002 @manifest-durability
  Scenario: A committed upload persists a manifest and an object-to-manifest reference
    Given a client has object content "manifest durable payload" for key "manifest-durability/payload.bin"
    When the client uploads the object through the S3 HTTP PutObject API
    Then the storage engine has a durable committed manifest for the uploaded bytes
    And the manifest records the object byte length, chunk list, checksum metadata, and creation time
    And the S3 object repository has a durable reference from bucket "phase-1-upload-bucket" and key "manifest-durability/payload.bin" to the manifest identifier
    When the object repository is reloaded from the filesystem
    And the manifest repository is reloaded from the filesystem
    Then bucket "phase-1-upload-bucket" and key "manifest-durability/payload.bin" still resolve to the same manifest identifier
    And the resolved manifest can be used to stream the original object bytes

  @REQ-UPLOAD-003 @large-object @streaming
  Scenario: A large upload is streamed with bounded memory and exact read-back
    Given a client has a deterministic 256 MiB object for key "large-object/streamed.bin"
    And the storage engine chunk size is configured to a bounded value smaller than the object
    When the client uploads the object through the S3 HTTP PutObject API using a streaming request body
    Then the storage engine writes the object as multiple durable chunks
    And no component materializes the complete 256 MiB object in memory at once
    And upload processing respects downstream backpressure while chunks are written
    And the committed manifest contains the ordered chunk references needed to reconstruct the object
    When the client reads key "large-object/streamed.bin" through the S3 HTTP GetObject API
    Then the streamed response bytes exactly match the deterministic uploaded object
    And the read path emits chunks in manifest order without loading the complete object into memory

  @REQ-UPLOAD-004 @atomicity @recovery
  Scenario: A failed partial upload does not publish a readable object or dangling committed manifest
    Given no object exists for key "atomicity/partial.bin"
    And a client starts uploading object content for key "atomicity/partial.bin"
    When the upload fails before all bytes are durably written and committed
    Then key "atomicity/partial.bin" is not visible through the S3 HTTP GetObject API
    And bucket "phase-1-upload-bucket" and key "atomicity/partial.bin" do not resolve to a committed manifest identifier
    And no committed manifest references missing or partial chunks for that failed upload
    When the application process is stopped
    And the application process is started again using the same storage-engine filesystem
    Then key "atomicity/partial.bin" remains unreadable
    And recovery either removes uncommitted upload artifacts or keeps them isolated from committed object references

  @REQ-UPLOAD-005 @read-after-write
  Scenario: A successful PutObject is immediately readable from real filesystem persistence
    Given a client has object content "read after write payload" for key "read-after-write/object.txt"
    And the storage-engine repositories are backed by a real filesystem location
    When the client uploads the object through the S3 HTTP PutObject API
    Then the upload is committed before the PutObject response is returned to the client
    And the filesystem contains the durable chunks, manifest, and S3 object reference for key "read-after-write/object.txt"
    When the same client immediately reads key "read-after-write/object.txt" through the S3 HTTP GetObject API
    Then the response body is exactly "read after write payload"
    And the response is produced from the storage-engine filesystem state rather than an in-memory-only object cache

  @REQ-UPLOAD-006 @checksum @integrity
  Scenario: Stored chunks and manifests include verifiable integrity metadata and detect corruption on read
    Given a client has object content "integrity protected payload" for key "integrity/checksummed.txt"
    When the client uploads the object through the S3 HTTP PutObject API
    Then each stored chunk has verifiable integrity metadata
    And the committed manifest records integrity metadata for the complete object and each chunk reference
    When one durable chunk for key "integrity/checksummed.txt" is corrupted outside the application
    And the client reads key "integrity/checksummed.txt" through the S3 HTTP GetObject API
    Then the storage engine detects the integrity mismatch before returning corrupted bytes as a successful object
    And the observable read result reports an object integrity failure
