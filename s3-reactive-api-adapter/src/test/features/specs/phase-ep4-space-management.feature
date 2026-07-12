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
    @REQ-GC-001 @functional-requirement @durability @restart-safety @storage-layout @garbage-collection @implemented-and-validated
    Scenario: Deleting or overwriting a plain object durably reclaims its whole-object unit without chunk accounting
      Given a committed plain object uses storage class "PLAIN" with multipart, deduplication, and erasure coding disabled
      And its manifest references one whole-object storage unit and zero chunk artifacts
      When the owning S3 object reference is deleted or replaced and durable reclamation runs
      Then the whole-object storage unit and obsolete manifest are removed
      And no chunk reference count, dedup index entry, multipart part, or EC shard is created or decremented
      And an idempotent second reclamation run reports no additional deletion

  Rule: Dedup chunks remain until their final owner is deleted
    @REQ-GC-002 @functional-requirement @dedup @reference-counting @garbage-collection @implemented-and-validated
    Scenario: Shared dedup chunks are reclaimed only after the final manifest reference disappears
      Given two committed objects reference the same dedup chunk fingerprint in one bucket scope
      When the first object is deleted and reclamation runs
      Then the shared dedup chunk, checksum sidecar, and content-address entry remain readable by the second object
      When the second object is deleted and reclamation runs
      Then the final reference count reaches zero
      And the dedup chunk, checksum sidecar, and content-address entry are removed as one durable reclamation unit

  Rule: Multipart and EC artifacts have independent reclamation rules
    @REQ-GC-003 @functional-requirement @durability @multipart @garbage-collection @implemented-and-validated
    Scenario Outline: Aborted or expired multipart uploads reclaim uncommitted parts
      Given a multipart upload has persisted parts but has no committed object manifest
      When the upload is <reclamation_reason> and multipart reclamation runs
      Then every uncommitted part and temporary checksum artifact is removed
      And chunks belonging to committed dedup or EC objects are unchanged
      And a repeated multipart reclamation run is idempotent

      Examples:
        | reclamation_reason |
        | aborted            |
        | expired            |

    @REQ-GC-004 @functional-requirement @durability @erasure-coding @garbage-collection @implemented-and-validated
    Scenario: EC object deletion reclaims all policy-derived shards after the final object reference
      Given a committed EC object manifest references four data shards and two parity shards
      When the final owning object is deleted and EC reclamation runs
      Then all six shard artifacts and checksum metadata are removed
      And no whole-object unit or unrelated dedup chunk is removed

  Rule: Periodic scrubbing verifies typed and transformed artifacts without changing valid data
    Scrubbing MUST hash the final persisted representation before any read-time decompression or
    decryption. The manifest transformation chain identifies compressed and encrypted artifacts,
    while the final checksum protects compressed bytes, authenticated ciphertext, and EC shards
    without requiring plaintext or encryption keys in the background job.

    @REQ-SCRUB-001 @non-functional-requirement @integrity @scrubbing @observability @compression @encryption @implemented-and-validated
    Scenario Outline: Scrubbing reports and quarantines corrupt typed artifacts after compression and encryption
      Given a committed "<artifact_type>" artifact with applied transformations "<transformations>" has a mismatching final persisted checksum
      And another committed healthy artifact exists in the same manifest
      When the periodic scrub job inspects the configured storage root with repair policy "QUARANTINE"
      Then one integrity finding identifies artifact type, identifier, owning manifest, checksum failure, and transformations "<transformations>"
      And the corrupt artifact and checksum sidecar are quarantined according to the configured repair policy
      And the healthy artifact remains byte-for-byte unchanged
      And a second scrub run is deterministic and does not duplicate the quarantined finding

      Examples:
        | artifact_type  | transformations |
        | whole-object   | NONE            |
        | dedup-chunk    | COMPRESS        |
        | multipart-part | CRYPT           |
        | ec-data-shard  | COMPRESS,CRYPT  |

    @REQ-SCRUB-002 @non-functional-requirement @integrity @scrubbing @scheduling @implemented-and-validated
    Scenario: Periodic scrubbing is opt-in and retains its latest report
      Given periodic integrity scrubbing is disabled by default
      When property "storage.engine.integrity.scrub.enabled" is set to true
      Then the storage-engine scheduler runs the scrub job with configurable initial delay, interval, and repair policy
      And each completed run atomically replaces the latest operator-readable scrub report
