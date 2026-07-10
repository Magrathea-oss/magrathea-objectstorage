# Runbook: Storage-engine manifest schema migration

Status: first validated EP-5 slice (`REQ-OPS-007`).

## Current manifest compatibility contract

| Format | Version | Runtime behavior |
|---|---:|---|
| Legacy manifest without `manifest.schemaVersion` | `0` compatibility mode | Readable for backward compatibility. |
| Current manifest properties format | `1` | Written by current `FileSystemManifestRepository`; readable. |
| Future/unknown manifest schema | `> 1` or invalid | Rejected instead of being parsed ambiguously. |

The manifest checksum trailer remains mandatory. If operators edit a manifest manually, the checksum no longer matches and the read path rejects it as an integrity failure.

## Operator procedure before upgrades

1. Complete an offline backup of `storage.engine.filesystem.root`.
2. Confirm `/admin/ready` returns `status=ready` on the old version.
3. Stop the process gracefully.
4. Upgrade the application.
5. Start the process and confirm `/admin/ready` returns `status=ready`.
6. Validate representative S3 reads.
7. Keep the pre-upgrade backup until rollback is no longer needed.

## Validation evidence

`PhaseEp5StorageMigrationSpecsCucumberTest` validates `REQ-OPS-007` by saving a sample storage-engine object manifest, asserting the committed file declares schema version `1`, verifying a legacy manifest with the schema version omitted remains readable as compatibility version `0`, and verifying a manifest that declares unsupported schema version `999` is rejected.

Command:

```bash
mvn -pl s3-reactive-api-adapter -am \
  -Dtest=PhaseEp5StorageMigrationSpecsCucumberTest \
  -Dsurefire.failIfNoSpecifiedTests=false test
```

The validation log must not contain `Using generated security password`.

## Open gaps

- No cross-version binary migration tool exists yet.
- No online migration is claimed.
- Only object manifest schema versioning is covered by this first slice; other metadata families still need explicit schema-version contracts.
