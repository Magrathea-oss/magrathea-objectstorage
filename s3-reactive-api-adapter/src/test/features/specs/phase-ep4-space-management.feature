@spec @ep-4 @space-management @storage-engine
Ability: Type-aware storage reclamation and integrity hygiene
  As a storage-engine operator and maintainer,
  I want reclamation and scrubbing to understand each physical storage-artifact type,
  So that deleted data is reclaimed without corrupting shared content or inventing chunks
  for plain objects.

  Chunks are valid only for multipart parts, deduplication windows, and erasure-coding
  stripes or shards. A plain object with all three mechanisms disabled is one streamed
  whole-object storage unit. GC, reference accounting, recovery, metrics, and reports must
  distinguish these artifact types.

  Background:
    Given the storage engine uses filesystem root "target/storage-engine-it/<scenario-id>"
    And the scenario starts with no pending reclamation or scrub findings

  Rule: Plain objects are reclaimed as whole-object units
    @REQ-GC-001 @functional-requirement @storage-layout @garbage-collection @not-implemented
    Scenario: Deleting a plain object reclaims its whole-object unit without chunk accounting
      Given a committed plain object uses storage class "PLAIN" with multipart, deduplication, and erasure coding disabled
      And its manifest references one whole-object storage unit and zero chunk artifacts
      When the owning S3 object reference is deleted and reclamation runs
      Then the whole-object storage unit and obsolete manifest are removed
      And no chunk reference count, dedup index entry, multipart part, or EC shard is created or decremented
      And an idempotent second reclamation run reports no additional deletion

  Rule: Dedup chunks remain until their final owner is deleted
    @REQ-GC-002 @functional-requirement @dedup @reference-counting @garbage-collection @not-implemented
    Scenario: Shared dedup chunks are reclaimed only after the final manifest reference disappears
      Given two committed objects reference the same dedup chunk fingerprint in one bucket scope
      When the first object is deleted and reclamation runs
      Then the shared dedup chunk, checksum sidecar, and content-address entry remain readable by the second object
      When the second object is deleted and reclamation runs
      Then the final reference count reaches zero
      And the dedup chunk, checksum sidecar, and content-address entry are removed atomically

  Rule: Multipart and EC artifacts have independent reclamation rules
    @REQ-GC-003 @functional-requirement @multipart @garbage-collection @not-implemented
    Scenario: Aborted or expired multipart uploads reclaim uncommitted parts
      Given a multipart upload has persisted parts but has no committed object manifest
      When the upload is aborted or expires and multipart reclamation runs
      Then every uncommitted part and temporary checksum artifact is removed
      And chunks belonging to committed dedup or EC objects are unchanged

    @REQ-GC-004 @functional-requirement @erasure-coding @garbage-collection @not-implemented
    Scenario: EC object deletion reclaims all policy-derived shards after the final object reference
      Given a committed EC object manifest references four data shards and two parity shards
      When the final owning object is deleted and EC reclamation runs
      Then all six shard artifacts and checksum metadata are removed
      And no whole-object unit or unrelated dedup chunk is removed

  Rule: Periodic scrubbing verifies typed artifacts without changing valid data
    @REQ-SCRUB-001 @non-functional-requirement @integrity @scrubbing @observability @not-implemented
    Scenario Outline: Scrubbing reports and quarantines corrupt storage artifacts by type
      Given a committed "<artifact_type>" artifact has a mismatching checksum
      When the periodic scrub job inspects the configured storage root
      Then one integrity finding identifies artifact type, identifier, owning manifest when known, and checksum failure
      And the corrupt artifact is quarantined according to the configured repair policy
      And healthy artifacts remain byte-for-byte unchanged
      And a second scrub run is deterministic and does not duplicate the finding

      Examples:
        | artifact_type    |
        | whole-object     |
        | dedup-chunk      |
        | multipart-part   |
        | ec-shard         |
