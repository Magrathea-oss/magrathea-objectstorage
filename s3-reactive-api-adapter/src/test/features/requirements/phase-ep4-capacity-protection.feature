@business-need @ep-4 @capacity-protection @storage-engine
Business Need: Capacity failures reject writes without corrupting committed S3 objects
  As an S3 client and storage administrator,
  I want quotas and exhausted storage to fail predictably,
  So that capacity limits never publish partial objects or damage previously committed data.

  Background:
    Given the S3 API is configured with profile "storage-engine-it" and backend "storage-engine"
    And bucket "capacity-protection-bucket" exists
    And object key "capacity/2026/existing.bin" contains committed fixture "fixtures/upload/small-object.txt"

  Rule: Bucket quotas are enforced before object publication
    @REQ-QUOTA-001 @functional-requirement @quota @s3-api @admin-api @webclient-required @awscli-required @not-implemented
    Scenario Outline: PutObject exceeding a bucket byte quota is rejected atomically
      Given validation mode "<validation_mode>" is selected for requirement "REQ-QUOTA-001"
      And the administrator configures bucket "capacity-protection-bucket" with byte quota 1048576
      And fixture "target/test-fixtures/capacity/quota-overflow-2m.bin" is exactly 2097152 bytes
      When the S3 client uploads the fixture to key "capacity/2026/quota-overflow.bin"
      Then PutObject returns the documented S3 quota-exceeded error
      And no object reference, manifest, whole-object unit, multipart part, dedup chunk, or EC shard is published for the rejected key
      And the previously committed object remains readable with its original checksum
      And the Admin API reports used bytes, reserved bytes, quota bytes, and the rejected reservation

      Examples:
        | validation_mode |
        | webclient       |
        | awscli          |

  Rule: Concurrent reservations cannot oversubscribe a quota
    @REQ-QUOTA-002 @functional-requirement @non-functional-requirement @quota @concurrency @s3-api @webclient-required @awscli-required @not-implemented
    Scenario Outline: Concurrent uploads reserve capacity atomically
      Given validation mode "<validation_mode>" is selected for requirement "REQ-QUOTA-002"
      And bucket "capacity-protection-bucket" has quota for exactly one 2 MiB object beyond current usage
      When two clients concurrently upload distinct 2 MiB keys
      Then exactly one upload commits and exactly one receives the documented quota-exceeded error
      And reported used plus reserved bytes never exceeds the configured quota
      And restart preserves the committed usage and releases the failed reservation

      Examples:
        | validation_mode |
        | webclient       |
        | awscli          |

  Rule: Filesystem exhaustion fails closed
    @REQ-CAPACITY-001 @functional-requirement @non-functional-requirement @enospc @integrity @s3-api @webclient-required @awscli-required @not-implemented
    Scenario Outline: ENOSPC during artifact persistence returns a deterministic S3 error and cleans partial state
      Given validation mode "<validation_mode>" is selected for requirement "REQ-CAPACITY-001"
      And deterministic fault injection reports ENOSPC after at least one temporary artifact is written
      When the S3 client uploads fixture "target/test-fixtures/capacity/enospc-object-8m.bin" to key "capacity/2026/enospc.bin"
      Then PutObject returns the documented storage-capacity S3 error rather than success
      And every temporary file and unpublished storage artifact for the failed key is removed or quarantined
      And no manifest or object reference is committed for the failed key
      And the previously committed object remains readable with its original checksum
      And a capacity failure event exposes backend, storage root, requested bytes, and available bytes without payload content

      Examples:
        | validation_mode |
        | webclient       |
        | awscli          |
