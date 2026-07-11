@spec @non-functional-requirement @operability @schema-migration @storage-engine
Ability: EP-5 storage-engine manifest schema versioning
  Maintainers need explicit manifest schema versions and bounded compatibility behavior
  so storage-engine files can be migrated safely instead of being parsed as ambiguous formats.

  Rule: Manifest files declare supported schema versions and retain controlled legacy compatibility

    @implemented-and-validated @REQ-OPS-007 @manifest-versioning @migration @durability
    Scenario: Storage-engine manifests declare the current schema and reject unsupported future schemas
      Given a sample storage-engine object manifest is saved through the filesystem manifest repository
      Then the committed manifest file declares schema version "1"
      And the repository can read a legacy manifest that omits the schema version as compatibility version "0"
      And the repository rejects a manifest that declares unsupported schema version "999"

    @implemented-and-validated @REQ-OPS-016 @multipart-versioning @migration @durability @restart-safety
    Scenario: Multipart upload state declares the current schema and rejects unsupported future schemas
      Given a sample multipart upload session is saved through the storage-engine repository
      Then the committed multipart upload state declares schema version "1"
      And the repository can read legacy multipart state that omits the schema version as compatibility version "0"
      And the repository rejects multipart state that declares unsupported schema version "999"

    @implemented-and-validated @REQ-OPS-017 @bucket-registry-versioning @migration @durability @restart-safety
    Scenario: Bucket registry state declares the current schema and rejects unsupported future schemas
      Given a sample bucket is saved through the storage-engine repository
      Then the committed bucket registry state declares schema version "1"
      And the repository can read legacy bucket state that omits the schema version as compatibility version "0"
      And the repository rejects bucket state that declares unsupported schema version "999"

    @implemented-and-validated @REQ-OPS-018 @object-config-versioning @migration @durability @restart-safety
    Scenario: Object configuration state declares the current schema and rejects unsupported future schemas
      Given sample object configuration is saved through the storage-engine repository
      Then the committed object configuration state declares schema version "1"
      And the repository can read legacy object configuration that omits the schema version as compatibility version "0"
      And the repository rejects object configuration that declares unsupported schema version "999"

    @implemented-and-validated @REQ-OPS-019 @object-reference-versioning @migration @durability @restart-safety
    Scenario: Object manifest references declare the current schema and reject unsupported future schemas
      Given a sample object manifest reference is saved through the storage-engine S3 path
      Then the committed object manifest reference declares schema version "1"
      And the repository can read a legacy object reference that omits the schema version as compatibility version "0"
      And the repository rejects an object reference that declares unsupported schema version "999"
