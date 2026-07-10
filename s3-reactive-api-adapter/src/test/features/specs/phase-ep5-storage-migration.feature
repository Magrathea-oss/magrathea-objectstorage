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
