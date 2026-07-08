Feature: Filesystem concurrency — torn reference prevention (REQ-FS-006)
  Ability
  Business need: concurrent PUTs to the same object key must not produce a torn
  reference that mixes fields from different writes. The storage-engine backend
  guards each per-key commit with a striped ReentrantLock and atomic temp-file
  rename so that concurrent writes are serialised per key and the on-disk
  reference is always consistent.

  @spec @webclient @concurrency @storage-engine @functional-requirement
  Scenario: Concurrent PUTs to same key produce consistent final reference
    Given the storage-engine profile is active
    And a bucket named "concurrency-bucket" exists
    When 5 concurrent PUT requests are sent for object "doc/report.pdf" with distinct content bodies 1 KB each
    Then all 5 PUT requests complete with HTTP 200
    And a subsequent GET for "doc/report.pdf" returns content matching exactly one of the uploaded bodies
    And the ETag header matches the expected ETag for that body

  @spec @webclient @concurrency @storage-engine @functional-requirement
  Scenario: Concurrent PUTs preserve full object metadata
    Given the storage-engine profile is active
    And a bucket named "concurrency-meta-bucket" exists
    When 3 concurrent PUT requests are sent for object "cfg.yaml" with distinct content bodies and distinct user metadata headers
    Then all 3 PUT requests complete with HTTP 200
    And a subsequent HEAD for "cfg.yaml" returns
      | Header           | Condition                              |
      | Content-Length   | matches exactly one of the uploaded sizes |
      | ETag             | matches exactly one of the uploaded ETags |
      | x-amz-meta-owner | is one of the submitted metadata values   |
    And the user metadata is not a mix of values from different uploads

  @spec @webclient @concurrency @storage-engine @non-functional-requirement @durability
  Scenario: Concurrent PUTs survive restart without torn references
    Given the storage-engine profile is active
    And a bucket named "concurrency-restart-bucket" exists
    When 3 concurrent PUT requests are sent for object "state.dat" with distinct content bodies
    And the application is restarted
    Then a GET for "state.dat" returns content matching exactly one of the uploaded bodies
    And the ETag header matches the expected ETag for that body
