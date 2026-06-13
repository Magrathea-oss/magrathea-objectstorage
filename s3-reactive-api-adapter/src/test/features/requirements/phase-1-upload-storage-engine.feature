@requirement @phase-1 @upload @storage-engine @not-implemented
Feature: Phase 1 upload reliability requirements for the storage-engine backend
  As an S3-compatible client, storage-engine operator, and system owner,
  I want PutObject and GetObject through the storage-engine backend to be durable,
  restart-safe, bounded-memory, and integrity-checked,
  So that uploaded objects survive process restarts and can be trusted in production.

  These backlog scenarios define the Phase 1 requirement set for upload persistence,
  manifest durability, large-object streaming, failed-upload recovery, immediate
  read-after-write visibility, and corruption detection when the S3 HTTP API is
  backed by the durable storage engine.

  The scenarios are intentionally stored outside the currently selected Cucumber
  feature folders. They are requirements only and must not be executed until
  matching implementation and step definitions are added.

  # Requirement roles used by this feature:
  # - S3 client: validates behavior in both WebTestClient and AWS CLI validation modes.
  #   WebTestClient exercises object-store RouterFunction endpoints directly; AWS CLI
  #   exercises the same S3-compatible API through aws s3api/aws s3 commands where supported.
  # - Storage engine operator: configures the filesystem-backed storage root, chunking,
  #   manifest persistence, object references, and integrity metadata expected in production.
  # - Recovery process/application restart: stops the application, discards process memory,
  #   reloads repositories from the storage-engine filesystem, and verifies only durable
  #   state is used after restart.
  #
  # Shared requirement contract for future executable implementations:
  # - Keep each @REQ-UPLOAD-* requirement ID unchanged.
  # - Future executable specs must instantiate each requirement twice: once in the
  #   object-store WebTestClient feature set and once in the awscli feature set,
  #   preserving the same requirement ID tag in both concrete specs.
  # - Reuse the same bucket, object key, fixture file, storage root, storage class,
  #   and observable behavior contract in both validation modes.
  # - WebTestClient steps may call RouterFunction endpoints directly; AWS CLI steps must use
  #   the same S3-compatible API through aws s3api/aws s3 commands where supported.
  #
  # Reusable fixtures for all Phase 1 upload requirements:
  # | fixture id         | resource path                                  | project path                                                                       | intended content                                    |
  # | small-object       | fixtures/upload/small-object.txt              | s3-reactive-api-adapter/src/test/resources/fixtures/upload/small-object.txt        | UTF-8 text: Hello durable Magrathea!                |
  # | large-object       | fixtures/upload/large-object.bin              | s3-reactive-api-adapter/src/test/resources/fixtures/upload/large-object.bin        | deterministic 256 MiB binary payload               |
  # | corruptible-object | fixtures/upload/corruptible-object.bin        | s3-reactive-api-adapter/src/test/resources/fixtures/upload/corruptible-object.bin  | deterministic binary payload for corruption checks  |
  #
  # Reusable storage-engine filesystem root pattern:
  # | purpose              | path                                                        |
  # | scenario filesystem  | target/storage-engine-it/<requirement-id>-<validation-mode> |
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
  Scenario Outline: S3 client reads an uploaded object after application restart
    Given validation mode "<validation_mode>" is selected for requirement "<requirement_id>"
    And the storage engine operator uses filesystem root "<storage_root>"
    And bucket "<bucket>" exists
    And the fixture file "<fixture_file>" contains "Hello durable Magrathea!"
    And an S3 client has object content from fixture file "<fixture_file>" for bucket "<bucket>" and key "<object_key>"
    And the S3 client includes user metadata:
      | name       | value       |
      | project    | magrathea   |
      | durability | restart     |
    And the S3 client requests storage class "<expected_storage_class>"
    When the S3 client uploads fixture file "<fixture_file>" to bucket "<bucket>" and key "<object_key>" through the S3 HTTP PutObject API
    Then the upload result records a committed manifest identifier for the stored bytes
    And the object reference for bucket "<bucket>" and key "<object_key>" points to that manifest identifier
    And the visible object attributes include storage class "<expected_storage_class>"
    And the visible object metadata includes "project" with value "magrathea"
    When the recovery process stops the application process
    And all in-memory repositories and caches are discarded
    And the recovery process starts the application again using storage-engine filesystem root "<storage_root>"
    And the S3 client reads bucket "<bucket>" and key "<object_key>" through the S3 HTTP GetObject API
    Then the response body matches fixture file "<fixture_file>"
    And the response metadata includes "project" with value "magrathea"
    And the response storage class is "<expected_storage_class>"
    And the read succeeds without reconstructing any object state from memory

    Examples:
      | requirement_id | validation_mode | bucket                    | object_key                              | fixture_file                     | storage_root                                      | expected_storage_class |
      | REQ-UPLOAD-001 | webclient       | req-upload-restart-bucket | documents/2026/restart-safe-object.txt | fixtures/upload/small-object.txt | target/storage-engine-it/REQ-UPLOAD-001-webclient | STANDARD               |
      | REQ-UPLOAD-001 | awscli          | req-upload-restart-bucket | documents/2026/restart-safe-object.txt | fixtures/upload/small-object.txt | target/storage-engine-it/REQ-UPLOAD-001-awscli    | STANDARD               |

  @REQ-UPLOAD-002 @manifest-durability @webclient-required @awscli-required @not-implemented
  Scenario Outline: Storage engine operator can reload committed upload manifests and object references
    Given validation mode "<validation_mode>" is selected for requirement "<requirement_id>"
    And the storage engine operator uses filesystem root "<storage_root>"
    And bucket "<bucket>" exists
    And an S3 client has object content from fixture file "<fixture_file>" for bucket "<bucket>" and key "<object_key>"
    When the S3 client uploads fixture file "<fixture_file>" to bucket "<bucket>" and key "<object_key>" through the S3 HTTP PutObject API
    Then the storage engine has a durable committed manifest for the uploaded bytes
    And the manifest records the object byte length, chunk list, checksum metadata, and creation time
    And the manifest records storage class "<expected_storage_class>" for the uploaded object
    And the S3 object repository has a durable reference from bucket "<bucket>" and key "<object_key>" to the manifest identifier
    When the object repository is reloaded from storage-engine filesystem root "<storage_root>"
    And the manifest repository is reloaded from storage-engine filesystem root "<storage_root>"
    Then bucket "<bucket>" and key "<object_key>" still resolve to the same manifest identifier
    And the resolved manifest can be used to stream the original bytes from fixture file "<fixture_file>"

    Examples:
      | requirement_id | validation_mode | bucket                     | object_key                 | fixture_file                     | storage_root                                      | expected_storage_class |
      | REQ-UPLOAD-002 | webclient       | req-upload-manifest-bucket | manifests/2026/payload.bin | fixtures/upload/small-object.txt | target/storage-engine-it/REQ-UPLOAD-002-webclient | STANDARD               |
      | REQ-UPLOAD-002 | awscli          | req-upload-manifest-bucket | manifests/2026/payload.bin | fixtures/upload/small-object.txt | target/storage-engine-it/REQ-UPLOAD-002-awscli    | STANDARD               |

  @REQ-UPLOAD-003 @large-object @streaming @webclient-required @awscli-required @not-implemented
  Scenario Outline: S3 client streams a large upload with bounded memory and exact read-back
    Given validation mode "<validation_mode>" is selected for requirement "<requirement_id>"
    And the storage engine operator uses filesystem root "<storage_root>"
    And bucket "<bucket>" exists
    And fixture file "<fixture_file>" is a deterministic 256 MiB object
    And an S3 client has object content from fixture file "<fixture_file>" for bucket "<bucket>" and key "<object_key>"
    And the storage engine chunk size is configured to a bounded value smaller than the object
    When the S3 client uploads fixture file "<fixture_file>" to bucket "<bucket>" and key "<object_key>" through the S3 HTTP PutObject API using a streaming request body
    Then the storage engine writes the object as multiple durable chunks
    And no component materializes the complete 256 MiB object in memory at once
    And upload processing respects downstream backpressure while chunks are written
    And the committed manifest contains the ordered chunk references needed to reconstruct the object
    And the committed manifest records storage class "<expected_storage_class>"
    When the S3 client reads bucket "<bucket>" and key "<object_key>" through the S3 HTTP GetObject API
    Then the streamed response bytes exactly match fixture file "<fixture_file>"
    And the read path emits chunks in manifest order without loading the complete object into memory

    Examples:
      | requirement_id | validation_mode | bucket                  | object_key                              | fixture_file                     | storage_root                                      | expected_storage_class |
      | REQ-UPLOAD-003 | webclient       | req-upload-large-bucket | archive/2026/large/streamed-object.bin | fixtures/upload/large-object.bin | target/storage-engine-it/REQ-UPLOAD-003-webclient | STANDARD               |
      | REQ-UPLOAD-003 | awscli          | req-upload-large-bucket | archive/2026/large/streamed-object.bin | fixtures/upload/large-object.bin | target/storage-engine-it/REQ-UPLOAD-003-awscli    | STANDARD               |

  @REQ-UPLOAD-004 @atomicity @recovery @webclient-required @awscli-required @not-implemented
  Scenario Outline: Recovery process does not publish a failed partial upload
    Given validation mode "<validation_mode>" is selected for requirement "<requirement_id>"
    And the storage engine operator uses filesystem root "<storage_root>"
    And bucket "<bucket>" exists
    And no object exists in bucket "<bucket>" for key "<object_key>"
    And an S3 client starts uploading fixture file "<fixture_file>" to bucket "<bucket>" and key "<object_key>"
    When the upload fails before all bytes are durably written and committed
    Then bucket "<bucket>" and key "<object_key>" are not visible through the S3 HTTP GetObject API
    And bucket "<bucket>" and key "<object_key>" do not resolve to a committed manifest identifier
    And no committed manifest references missing or partial chunks for that failed upload
    When the recovery process stops the application process
    And the recovery process starts the application again using storage-engine filesystem root "<storage_root>"
    Then bucket "<bucket>" and key "<object_key>" remain unreadable
    And recovery either removes uncommitted upload artifacts or keeps them isolated from committed object references

    Examples:
      | requirement_id | validation_mode | bucket                       | object_key                              | fixture_file                     | storage_root                                      | expected_storage_class |
      | REQ-UPLOAD-004 | webclient       | req-upload-atomicity-bucket  | uploads/2026/failed/partial-object.bin | fixtures/upload/large-object.bin | target/storage-engine-it/REQ-UPLOAD-004-webclient | STANDARD               |
      | REQ-UPLOAD-004 | awscli          | req-upload-atomicity-bucket  | uploads/2026/failed/partial-object.bin | fixtures/upload/large-object.bin | target/storage-engine-it/REQ-UPLOAD-004-awscli    | STANDARD               |

  @REQ-UPLOAD-005 @read-after-write @webclient-required @awscli-required @not-implemented
  Scenario Outline: S3 client immediately reads a successful PutObject from filesystem persistence
    Given validation mode "<validation_mode>" is selected for requirement "<requirement_id>"
    And the storage-engine repositories are backed by storage-engine filesystem root "<storage_root>"
    And bucket "<bucket>" exists
    And an S3 client has object content from fixture file "<fixture_file>" for bucket "<bucket>" and key "<object_key>"
    When the S3 client uploads fixture file "<fixture_file>" to bucket "<bucket>" and key "<object_key>" through the S3 HTTP PutObject API
    Then the upload is committed before the PutObject response is returned to the client
    And the filesystem contains the durable chunks, manifest, and S3 object reference for bucket "<bucket>" and key "<object_key>"
    And the committed object records storage class "<expected_storage_class>"
    When the same S3 client immediately reads bucket "<bucket>" and key "<object_key>" through the S3 HTTP GetObject API
    Then the response body matches fixture file "<fixture_file>"
    And the response is produced from the storage-engine filesystem state rather than an in-memory-only object cache

    Examples:
      | requirement_id | validation_mode | bucket                             | object_key                                  | fixture_file                     | storage_root                                      | expected_storage_class |
      | REQ-UPLOAD-005 | webclient       | req-upload-read-after-write-bucket | documents/2026/read-after-write/object.txt | fixtures/upload/small-object.txt | target/storage-engine-it/REQ-UPLOAD-005-webclient | STANDARD               |
      | REQ-UPLOAD-005 | awscli          | req-upload-read-after-write-bucket | documents/2026/read-after-write/object.txt | fixtures/upload/small-object.txt | target/storage-engine-it/REQ-UPLOAD-005-awscli    | STANDARD               |

  @REQ-UPLOAD-006 @checksum @integrity @webclient-required @awscli-required @not-implemented
  Scenario Outline: Storage engine detects chunk corruption before returning uploaded bytes
    Given validation mode "<validation_mode>" is selected for requirement "<requirement_id>"
    And the storage engine operator uses filesystem root "<storage_root>"
    And bucket "<bucket>" exists
    And an S3 client has object content from fixture file "<fixture_file>" for bucket "<bucket>" and key "<object_key>"
    When the S3 client uploads fixture file "<fixture_file>" to bucket "<bucket>" and key "<object_key>" through the S3 HTTP PutObject API
    Then each stored chunk has verifiable integrity metadata
    And the committed manifest records integrity metadata for the complete object and each chunk reference
    And the committed manifest records storage class "<expected_storage_class>"
    When one durable chunk for bucket "<bucket>" and key "<object_key>" is corrupted outside the application
    And the S3 client reads bucket "<bucket>" and key "<object_key>" through the S3 HTTP GetObject API
    Then the storage engine detects the integrity mismatch before returning corrupted bytes as a successful object
    And the observable read result reports an object integrity failure

    Examples:
      | requirement_id | validation_mode | bucket                      | object_key                             | fixture_file                           | storage_root                                      | expected_storage_class |
      | REQ-UPLOAD-006 | webclient       | req-upload-integrity-bucket | integrity/2026/corruptible-object.bin | fixtures/upload/corruptible-object.bin | target/storage-engine-it/REQ-UPLOAD-006-webclient | STANDARD               |
      | REQ-UPLOAD-006 | awscli          | req-upload-integrity-bucket | integrity/2026/corruptible-object.bin | fixtures/upload/corruptible-object.bin | target/storage-engine-it/REQ-UPLOAD-006-awscli    | STANDARD               |
