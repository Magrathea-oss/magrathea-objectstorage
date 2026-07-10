@spec @phase-2 @filesystem-reliability @storage-engine @implemented-and-validated
Ability: Phase 2 filesystem reliability for the storage-engine backend
  As a storage-engine operator, S3-compatible client, and system owner,
  I want filesystem-backed chunks, manifests, and object references to be protected
  against partial writes, corruption, process crashes, and concurrent access,
  So that stored objects remain durable and correct under failure conditions,
  recovery scenarios, and concurrent upload load.

  This feature covers internal filesystem reliability abilities (atomic write protocol,
  recovery scanner) that directly support the S3-visible durability promise, and
  S3-visible behaviors (checksum mismatch detection, concurrent-write isolation) that
  manifest as observable GetObject errors or clean PUT outcomes.

  REQ-FS-003 and REQ-FS-004 produce directly observable S3 client outcomes: a corrupted
  chunk or corrupted manifest causes GetObject to return an integrity error rather than
  corrupt bytes. These are classified as @functional-requirement and validated in both
  required webclient and awscli validation modes.

  REQ-FS-001, REQ-FS-002, and REQ-FS-005 are internal filesystem reliability abilities:
  they are validated by filesystem inspection and process-restart probes using the
  WebTestClient runner with storage-engine filesystem root access. They do not require
  AWS CLI validation because the invariant is at the filesystem layer, not the S3 API.

  REQ-FS-006 is observable through the S3 API: concurrent PUTs for different keys must
  all succeed cleanly; concurrent PUTs for the same key must produce a clean single winner
  without corruption. These validate in both @webclient-required and @awscli-required modes.

  This feature is the single source of truth for Phase 2 filesystem reliability requirements.
  Future WebTestClient and AWS CLI runners must reuse this same shared feature resource.
  Runner-specific behavior belongs in step definitions, glue, runner configuration, profiles,
  and validation adapters — not in duplicated feature text.

  Until matching implementation and runner glue are added, this shared requirement resource
  remains outside the currently selected Cucumber runners. Keep every @REQ-FS-* requirement
  ID unchanged when the scenarios become executable.

  Validation roles:
    - Storage-engine operator validates filesystem atomicity, checksum persistence, and
      recovery scanner behavior by inspecting filesystem state before and after each step.
    - S3 client validates corruption detection through GetObject responses and concurrent
      write outcomes through PutObject/GetObject sequences using WebTestClient and AWS CLI.
    - Recovery process/application restart stops the application, discards process memory,
      starts the application again using a filesystem root, and verifies only durable
      committed state is visible after restart.
    - Corruption injector writes arbitrary bytes into a chunk or manifest file on disk
      outside the running application, simulating storage media corruption.
    - Concurrency harness issues multiple parallel PutObject requests and collects all
      responses before asserting isolation and integrity invariants.

  Reusable fixtures for all Phase 2 filesystem reliability requirements:
    | fixture id         | resource path                          | project path                                                                      | intended content                                   |
    | small-object       | fixtures/upload/small-object.txt       | s3-reactive-api-adapter/src/test/resources/fixtures/upload/small-object.txt       | UTF-8 text: Hello durable Magrathea!               |
    | corruptible-object | fixtures/upload/corruptible-object.bin | s3-reactive-api-adapter/src/test/resources/fixtures/upload/corruptible-object.bin | deterministic 4 KiB repeating-byte binary payload  |

  Reusable storage-engine filesystem root pattern:
    | purpose             | path                                                        |
    | scenario filesystem | target/storage-engine-it/<requirement-id>-<validation-mode> |

  Requirement buckets, nested object keys, and fixture choices:
    | requirement | bucket                       | key                                             | fixture resource path                    |
    | REQ-FS-001  | fs-atomicity-chunk-bucket    | chunks/2026/atomic-write/chunk-object.bin       | fixtures/upload/small-object.txt         |
    | REQ-FS-002  | fs-atomicity-manifest-bucket | manifests/2026/atomic-write/manifest-object.bin | fixtures/upload/small-object.txt         |
    | REQ-FS-003  | fs-checksum-chunk-bucket     | integrity/2026/chunk-checksum/object.bin        | fixtures/upload/corruptible-object.bin   |
    | REQ-FS-004  | fs-checksum-manifest-bucket  | integrity/2026/manifest-checksum/object.bin     | fixtures/upload/corruptible-object.bin   |
    | REQ-FS-005  | fs-recovery-bucket           | recovery/2026/scan/orphaned-object.bin          | fixtures/upload/small-object.txt         |
    | REQ-FS-006  | fs-concurrency-bucket        | concurrent/2026/put/object.bin                  | fixtures/upload/small-object.txt         |

  Background:
    Given the S3 API is configured with profile "storage-engine-it" and backend "storage-engine"
    And the storage engine stores bytes, manifests, and object references on a real filesystem
    And each scenario uses a clean storage-engine filesystem root "target/storage-engine-it/<scenario-id>"

  Rule: Chunk writes use a temporary-file-then-rename protocol
    The storage engine MUST write each chunk to a uniquely named temporary file in the
    same directory as the final chunk path, then atomically rename it to the final chunk
    path only when the chunk bytes are fully written and fsynced. An interrupted write
    MUST NOT leave a partial file at the final chunk path. A partial temporary file MAY
    remain as a recovery artifact but MUST NOT be served as a committed chunk or
    referenced by any committed manifest.

    @REQ-FS-001 @functional-requirement @non-functional-requirement @atomicity @durability @webclient-required @implemented-and-validated
    Scenario Outline: Interrupted chunk write does not expose a partial chunk at the committed path
      Given validation mode "<validation_mode>" is selected for requirement "<requirement_id>"
      And the storage engine operator uses filesystem root "<storage_root>"
      And bucket "<bucket>" exists
      And no object exists in bucket "<bucket>" for key "<object_key>"
      And the storage engine is configured to write chunks via temp-file-then-rename in filesystem root "<storage_root>"
      And an S3 client has object content from fixture file "<fixture_file>" for bucket "<bucket>" and key "<object_key>"
      When the S3 client starts uploading fixture file "<fixture_file>" to bucket "<bucket>" and key "<object_key>" through the S3 HTTP PutObject API
      And the upload process is interrupted after some chunk bytes are written but before the chunk rename is complete
      Then the final committed chunk path in filesystem root "<storage_root>" for bucket "<bucket>" and key "<object_key>" does not contain a partial chunk file
      And if a temporary chunk file remains in filesystem root "<storage_root>", it is isolated from committed chunk paths and not served as a committed chunk
      And bucket "<bucket>" and key "<object_key>" are not visible through the S3 HTTP GetObject API
      When the application is restarted using storage-engine filesystem root "<storage_root>"
      Then bucket "<bucket>" and key "<object_key>" remain unreadable after restart
      And the final committed chunk path in filesystem root "<storage_root>" still does not contain a partial or incomplete chunk file
      And any remaining temporary chunk files are not referenced by any committed manifest or object reference

      @webclient
      Examples: WebTestClient validation
        | requirement_id | validation_mode | bucket                    | object_key                                  | fixture_file                     | storage_root                                  |
        | REQ-FS-001     | webclient       | fs-atomicity-chunk-bucket | chunks/2026/atomic-write/chunk-object.bin   | fixtures/upload/small-object.txt | target/storage-engine-it/REQ-FS-001-webclient |

  Rule: Manifest writes use a temporary-file-then-rename protocol
    The storage engine MUST write each upload manifest to a uniquely named temporary file,
    then atomically rename it to the final committed manifest path only after all chunk
    references, checksums, and metadata are fully written and fsynced. An interrupted
    manifest write MUST NOT expose a partial or unparseable manifest at the committed path.
    A file present at a committed manifest path MUST always be a complete, parseable manifest.

    @REQ-FS-002 @functional-requirement @non-functional-requirement @atomicity @durability @webclient-required @implemented-and-validated
    Scenario Outline: Interrupted manifest write does not expose a partial manifest at the committed path
      Given validation mode "<validation_mode>" is selected for requirement "<requirement_id>"
      And the storage engine operator uses filesystem root "<storage_root>"
      And bucket "<bucket>" exists
      And no object exists in bucket "<bucket>" for key "<object_key>"
      And the storage engine is configured to write manifests via temp-file-then-rename in filesystem root "<storage_root>"
      And an S3 client has object content from fixture file "<fixture_file>" for bucket "<bucket>" and key "<object_key>"
      When the S3 client starts uploading fixture file "<fixture_file>" to bucket "<bucket>" and key "<object_key>" through the S3 HTTP PutObject API
      And all chunk files are successfully written and renamed to their committed paths in filesystem root "<storage_root>"
      And the manifest write is interrupted after manifest bytes are partially written but before the manifest rename is complete
      Then the committed manifest path in filesystem root "<storage_root>" for bucket "<bucket>" and key "<object_key>" does not contain a partial or unparseable manifest file
      And if a temporary manifest file remains in filesystem root "<storage_root>", it is not used to drive reads or referenced by any object reference
      And bucket "<bucket>" and key "<object_key>" are not visible through the S3 HTTP GetObject API
      When the application is restarted using storage-engine filesystem root "<storage_root>"
      Then bucket "<bucket>" and key "<object_key>" remain unreadable after restart
      And the committed manifest path in filesystem root "<storage_root>" still does not contain a partial or unparseable manifest file
      And any remaining temporary manifest file is not referenced by any committed object reference

      @webclient
      Examples: WebTestClient validation
        | requirement_id | validation_mode | bucket                       | object_key                                      | fixture_file                     | storage_root                                  |
        | REQ-FS-002     | webclient       | fs-atomicity-manifest-bucket | manifests/2026/atomic-write/manifest-object.bin | fixtures/upload/small-object.txt | target/storage-engine-it/REQ-FS-002-webclient |

  Rule: Every stored chunk carries a verifiable checksum
    The storage engine MUST compute and persist a checksum for each chunk at write time.
    The read path MUST verify each chunk's checksum before emitting any bytes to the calling
    layer. A chunk whose bytes do not match the stored checksum MUST be rejected; the read path
    MUST NOT return corrupted bytes as a successful object. The S3 HTTP GetObject response MUST
    signal an integrity failure when a chunk checksum mismatch is detected.

    @REQ-FS-003 @functional-requirement @non-functional-requirement @checksum @integrity @webclient-required @awscli-required @implemented-and-validated
    Scenario Outline: Storage engine detects a corrupted chunk and rejects it before returning bytes to S3 clients
      Given validation mode "<validation_mode>" is selected for requirement "<requirement_id>"
      And the storage engine operator uses filesystem root "<storage_root>"
      And bucket "<bucket>" exists
      And an S3 client has object content from fixture file "<fixture_file>" for bucket "<bucket>" and key "<object_key>"
      When the S3 client uploads fixture file "<fixture_file>" to bucket "<bucket>" and key "<object_key>" through the S3 HTTP PutObject API
      Then the upload is committed and every chunk file in filesystem root "<storage_root>" for bucket "<bucket>" and key "<object_key>" carries a verifiable checksum
      And the S3 client can immediately read bucket "<bucket>" and key "<object_key>" and receive the exact original bytes from fixture file "<fixture_file>"
      When the corruption injector overwrites bytes inside a committed chunk file in filesystem root "<storage_root>" for bucket "<bucket>" and key "<object_key>" outside the running application
      And the S3 client reads bucket "<bucket>" and key "<object_key>" through the S3 HTTP GetObject API
      Then the storage engine detects the checksum mismatch for the corrupted chunk before returning any response bytes
      And the S3 HTTP GetObject response signals an object integrity failure rather than returning corrupted bytes as a successful object body

      @webclient
      Examples: WebTestClient validation
        | requirement_id | validation_mode | bucket                   | object_key                               | fixture_file                           | storage_root                                  |
        | REQ-FS-003     | webclient       | fs-checksum-chunk-bucket | integrity/2026/chunk-checksum/object.bin | fixtures/upload/corruptible-object.bin | target/storage-engine-it/REQ-FS-003-webclient |

      @awscli
      Examples: AWS CLI validation
        | requirement_id | validation_mode | bucket                   | object_key                               | fixture_file                           | storage_root                               |
        | REQ-FS-003     | awscli          | fs-checksum-chunk-bucket | integrity/2026/chunk-checksum/object.bin | fixtures/upload/corruptible-object.bin | target/storage-engine-it/REQ-FS-003-awscli |

  Rule: Committed manifests carry a checksum or digest over their content
    The storage engine MUST compute and persist a checksum or content digest for each
    committed manifest at write time. The read path MUST verify the manifest checksum
    before using the manifest to drive any object read. A manifest whose content does not
    match its stored checksum MUST be rejected; the object store MUST NOT serve bytes
    guided by a corrupted manifest. The S3 HTTP GetObject response MUST signal an integrity
    failure when a manifest checksum mismatch is detected.

    @REQ-FS-004 @functional-requirement @non-functional-requirement @checksum @integrity @webclient-required @awscli-required @implemented-and-validated
    Scenario Outline: Storage engine detects a corrupted manifest and rejects it before driving any object read
      Given validation mode "<validation_mode>" is selected for requirement "<requirement_id>"
      And the storage engine operator uses filesystem root "<storage_root>"
      And bucket "<bucket>" exists
      And an S3 client has object content from fixture file "<fixture_file>" for bucket "<bucket>" and key "<object_key>"
      When the S3 client uploads fixture file "<fixture_file>" to bucket "<bucket>" and key "<object_key>" through the S3 HTTP PutObject API
      Then the upload is committed and the manifest file in filesystem root "<storage_root>" for bucket "<bucket>" and key "<object_key>" carries a verifiable checksum or digest
      And the S3 client can immediately read bucket "<bucket>" and key "<object_key>" and receive the exact original bytes from fixture file "<fixture_file>"
      When the corruption injector overwrites bytes inside the committed manifest file in filesystem root "<storage_root>" for bucket "<bucket>" and key "<object_key>" outside the running application
      And the S3 client reads bucket "<bucket>" and key "<object_key>" through the S3 HTTP GetObject API
      Then the storage engine detects the manifest checksum mismatch before loading any chunk or returning any response bytes
      And the S3 HTTP GetObject response signals a manifest integrity failure rather than returning bytes guided by a corrupted manifest

      @webclient
      Examples: WebTestClient validation
        | requirement_id | validation_mode | bucket                      | object_key                                  | fixture_file                           | storage_root                                  |
        | REQ-FS-004     | webclient       | fs-checksum-manifest-bucket | integrity/2026/manifest-checksum/object.bin | fixtures/upload/corruptible-object.bin | target/storage-engine-it/REQ-FS-004-webclient |

      @awscli
      Examples: AWS CLI validation
        | requirement_id | validation_mode | bucket                      | object_key                                  | fixture_file                           | storage_root                               |
        | REQ-FS-004     | awscli          | fs-checksum-manifest-bucket | integrity/2026/manifest-checksum/object.bin | fixtures/upload/corruptible-object.bin | target/storage-engine-it/REQ-FS-004-awscli |

  Rule: Recovery scanner discovers and quarantines all incomplete filesystem artifacts deterministically
    A recovery scan MUST traverse the configured filesystem root and identify all of:
    orphaned chunk files not referenced by any committed manifest; incomplete or unparseable
    manifests; broken object references pointing to absent, incomplete, or invalid manifests;
    and chunk or manifest checksum mismatches. The scanner MUST report all findings
    deterministically — the same filesystem state produces the same findings on every run.
    The scanner MUST remove or quarantine each incomplete or corrupt artifact without modifying
    or removing any valid committed object, its manifest, its chunks, or its object reference.
    After the scan completes, all valid committed objects remain readable through the S3 API
    and all quarantined or removed artifacts are no longer accessible through the S3 API.

    @REQ-FS-005 @functional-requirement @non-functional-requirement @recovery @integrity @webclient-required @implemented-and-validated
    Scenario Outline: Recovery scanner discovers orphaned chunks, incomplete manifests, broken references, and checksum mismatches without touching committed objects
      Given validation mode "<validation_mode>" is selected for requirement "<requirement_id>"
      And the storage engine operator uses filesystem root "<storage_root>"
      And bucket "<bucket>" exists
      And a valid committed object exists in bucket "<bucket>" at key "<object_key>" uploaded from fixture file "<fixture_file>"
      And the committed object is readable through the S3 HTTP GetObject API and returns the exact bytes from fixture file "<fixture_file>" before the scanner runs
      And orphaned chunk files from a failed upload exist in the chunk directory of filesystem root "<storage_root>" with no committed manifest referencing them
      And an incomplete manifest with at least one missing required field exists in the manifest directory of filesystem root "<storage_root>"
      And a broken object reference pointing to an absent manifest exists in the object reference directory of filesystem root "<storage_root>"
      And a committed chunk file with a corrupted checksum mismatch exists in filesystem root "<storage_root>"
      When the recovery scanner is triggered for filesystem root "<storage_root>"
      Then the scanner report lists at least "<minimum_finding_count>" findings
      And the scanner report includes the orphaned chunk artifact paths with artifact type "orphaned-chunk" and a descriptive failure reason
      And the scanner report includes the incomplete manifest artifact path with artifact type "incomplete-manifest" and a descriptive failure reason
      And the scanner report includes the broken object reference artifact path with artifact type "broken-reference" and a descriptive failure reason
      And the scanner report includes the corrupted chunk artifact path with artifact type "checksum-mismatch" and a descriptive failure reason
      And the scanner removes or quarantines all reported incomplete and corrupt artifacts
      And the scanner does not remove or modify the valid committed object's chunks, manifest, or object reference in filesystem root "<storage_root>"
      And after the scan, the S3 client reads bucket "<bucket>" and key "<object_key>" and receives the exact bytes from fixture file "<fixture_file>"
      And after the scan, the orphaned, incomplete, and broken artifacts are no longer served through the S3 HTTP GetObject API
      When the recovery scanner is triggered again for filesystem root "<storage_root>" with no new artifacts introduced
      Then the scanner report lists zero findings, confirming deterministic idempotent reporting

      @webclient
      Examples: WebTestClient validation
        | requirement_id | validation_mode | bucket             | object_key                             | fixture_file                     | storage_root                                  | minimum_finding_count |
        | REQ-FS-005     | webclient       | fs-recovery-bucket | recovery/2026/scan/orphaned-object.bin | fixtures/upload/small-object.txt | target/storage-engine-it/REQ-FS-005-webclient | 4                     |

  Rule: Concurrent PUT requests for different keys are fully isolated; concurrent PUT requests for the same key produce exactly one clean committed winner
    The storage engine MUST ensure that chunk files and manifest files written by concurrent
    PutObject requests for different object keys are isolated: they MUST NOT overwrite,
    corrupt, or intermix each other's bytes or metadata. Concurrent PutObject requests for
    the same object key MUST be serialized or produce exactly one clean winner: the committed
    object reference MUST point to exactly one complete, parseable, checksum-valid manifest;
    the GetObject response MUST return bytes that exactly match one of the uploaded fixtures
    without mixing bytes from multiple concurrent uploads.

    @REQ-FS-006 @functional-requirement @non-functional-requirement @concurrency @atomicity @integrity @webclient-required @awscli-required @implemented-and-validated
    Scenario Outline: Concurrent PUT requests for different keys complete successfully without corrupting each other
      Given validation mode "<validation_mode>" is selected for requirement "<requirement_id>"
      And the storage engine operator uses filesystem root "<storage_root>"
      And bucket "<bucket>" exists
      And no objects exist for key "<key_a>" and key "<key_b>" in bucket "<bucket>"
      And an S3 client prepares a PutObject for bucket "<bucket>" key "<key_a>" with content from fixture file "<fixture_file_a>"
      And an S3 client prepares a PutObject for bucket "<bucket>" key "<key_b>" with content from fixture file "<fixture_file_b>"
      When both PutObject requests are issued concurrently to the S3 HTTP API
      Then both PutObject requests complete with HTTP 200
      And the S3 HTTP GetObject for bucket "<bucket>" key "<key_a>" returns the exact bytes from fixture file "<fixture_file_a>"
      And the S3 HTTP GetObject for bucket "<bucket>" key "<key_b>" returns the exact bytes from fixture file "<fixture_file_b>"
      And the chunk files for key "<key_a>" and key "<key_b>" in filesystem root "<storage_root>" are not mixed, overwritten, or corrupted
      And the committed manifest for key "<key_a>" in filesystem root "<storage_root>" is parseable and has a valid checksum
      And the committed manifest for key "<key_b>" in filesystem root "<storage_root>" is parseable and has a valid checksum

      @webclient
      Examples: WebTestClient validation
        | requirement_id | validation_mode | bucket                | key_a                            | key_b                            | fixture_file_a                   | fixture_file_b                         | storage_root                                               |
        | REQ-FS-006     | webclient       | fs-concurrency-bucket | concurrent/2026/put/object-a.bin | concurrent/2026/put/object-b.bin | fixtures/upload/small-object.txt | fixtures/upload/corruptible-object.bin | target/storage-engine-it/REQ-FS-006-webclient-diff-keys    |

      @awscli
      Examples: AWS CLI validation
        | requirement_id | validation_mode | bucket                | key_a                            | key_b                            | fixture_file_a                   | fixture_file_b                         | storage_root                                            |
        | REQ-FS-006     | awscli          | fs-concurrency-bucket | concurrent/2026/put/object-a.bin | concurrent/2026/put/object-b.bin | fixtures/upload/small-object.txt | fixtures/upload/corruptible-object.bin | target/storage-engine-it/REQ-FS-006-awscli-diff-keys   |

    @REQ-FS-006 @functional-requirement @non-functional-requirement @concurrency @atomicity @integrity @webclient-required @awscli-required @implemented-and-validated
    Scenario Outline: Concurrent PUT requests for the same key produce exactly one clean committed winner
      Given validation mode "<validation_mode>" is selected for requirement "<requirement_id>"
      And the storage engine operator uses filesystem root "<storage_root>"
      And bucket "<bucket>" exists
      And no object exists in bucket "<bucket>" for key "<object_key>"
      And two S3 clients each prepare a concurrent PutObject for bucket "<bucket>" key "<object_key>" with different fixture files "<fixture_file_a>" and "<fixture_file_b>"
      When both PutObject requests are issued concurrently to the S3 HTTP API
      Then at least one PutObject request completes with HTTP 200
      And after all concurrent requests complete, the S3 HTTP GetObject for bucket "<bucket>" key "<object_key>" returns a complete non-corrupted object body
      And the returned object body exactly matches either fixture file "<fixture_file_a>" or fixture file "<fixture_file_b>" — not a mix of bytes from both
      And the committed manifest for bucket "<bucket>" key "<object_key>" in filesystem root "<storage_root>" is parseable and has a valid checksum
      And the object reference for bucket "<bucket>" key "<object_key>" in filesystem root "<storage_root>" points to exactly one committed manifest identifier

      @webclient
      Examples: WebTestClient validation
        | requirement_id | validation_mode | bucket                | object_key                     | fixture_file_a                   | fixture_file_b                         | storage_root                                              |
        | REQ-FS-006     | webclient       | fs-concurrency-bucket | concurrent/2026/put/object.bin | fixtures/upload/small-object.txt | fixtures/upload/corruptible-object.bin | target/storage-engine-it/REQ-FS-006-webclient-same-key    |

      @awscli
      Examples: AWS CLI validation
        | requirement_id | validation_mode | bucket                | object_key                     | fixture_file_a                   | fixture_file_b                         | storage_root                                           |
        | REQ-FS-006     | awscli          | fs-concurrency-bucket | concurrent/2026/put/object.bin | fixtures/upload/small-object.txt | fixtures/upload/corruptible-object.bin | target/storage-engine-it/REQ-FS-006-awscli-same-key   |
