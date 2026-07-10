# Runbook: Storage-engine metadata schema migration

Status: validated object-manifest and multipart-session slices (`REQ-OPS-007`, `REQ-OPS-016`).

## Current manifest compatibility contract

| Format | Version | Runtime behavior |
|---|---:|---|
| Legacy manifest without `manifest.schemaVersion` | `0` compatibility mode | Readable for backward compatibility. |
| Current manifest properties format | `1` | Written by current `FileSystemManifestRepository`; readable. |
| Future/unknown manifest schema | `> 1` or invalid | Rejected instead of being parsed ambiguously. |

The manifest checksum trailer remains mandatory. If operators edit a manifest manually, the checksum no longer matches and the read path rejects it as an integrity failure.

## Multipart upload state compatibility contract

| Format | Version | Runtime behavior |
|---|---:|---|
| Legacy multipart JSON without `schemaVersion` | `0` compatibility mode | Readable for backward compatibility. |
| Current multipart upload state JSON | `1` | Written by `MultipartUploadStateStore`; readable after restart. |
| Future/unknown multipart schema | `> 1` or invalid | Rejected during repository startup/reload instead of being interpreted ambiguously. |

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

The same runner validates `REQ-OPS-016` for durable multipart upload session JSON: current writes declare version `1`, omitted versions load as legacy version `0`, and future version `999` is rejected.

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
- Object manifests and multipart upload sessions are covered; bucket registries, object references, ACL/tag sidecars, and other durable metadata families still need explicit schema-version contracts.
