# Runbook: Storage-engine metadata schema migration

Status: validated object-manifest, multipart-session, bucket-registry, object-configuration, object-reference, object-ACL, and bucket-capacity-ledger slices (`REQ-OPS-007`, `REQ-OPS-016..020`, `REQ-OPS-024`).

## Current manifest compatibility contract

| Format | Version | Runtime behavior |
|---|---:|---|
| Legacy chunk-only manifest without `manifest.schemaVersion` | `0` compatibility mode | Readable for backward compatibility. |
| Previous chunk-only manifest properties format | `1` | Readable for backward compatibility. |
| Current typed-artifact manifest properties format | `2` | Written by current `FileSystemManifestRepository`; readable. |
| Future/unknown manifest schema | `> 2` or invalid | Rejected instead of being parsed ambiguously. |

The manifest checksum trailer remains mandatory. If operators edit a manifest manually, the checksum no longer matches and the read path rejects it as an integrity failure.

## Multipart upload state compatibility contract

| Format | Version | Runtime behavior |
|---|---:|---|
| Legacy multipart JSON without `schemaVersion` | `0` compatibility mode | Readable for backward compatibility. |
| Current multipart upload state JSON | `1` | Written by `MultipartUploadStateStore`; readable after restart. |
| Future/unknown multipart schema | `> 1` or invalid | Rejected during repository startup/reload instead of being interpreted ambiguously. |

## Bucket registry compatibility contract

| Format | Version | Runtime behavior |
|---|---:|---|
| Legacy bucket JSON without `schemaVersion` | `0` compatibility mode | Readable for backward compatibility. |
| Current bucket registry JSON | `1` | Written by `BucketStore`; readable after restart. |
| Future/unknown bucket schema | `> 1` or invalid | Rejected during repository startup/reload instead of being interpreted ambiguously. |

## Object configuration compatibility contract

| Format | Version | Runtime behavior |
|---|---:|---|
| Legacy object configuration JSON without `schemaVersion` | `0` compatibility mode | Readable for backward compatibility. |
| Current object configuration JSON | `1` | Written by `ObjectConfigMetadataStore`; readable after restart. |
| Future/unknown object configuration schema | `> 1` or invalid | Rejected when the document is loaded instead of being interpreted ambiguously. |

The object configuration document contains legal hold, encryption, object-lock, retention, and restore state as one atomic record.

## Object manifest reference compatibility contract

| Format | Version | Runtime behavior |
|---|---:|---|
| Legacy reference properties without `reference.schemaVersion` | `0` compatibility mode | Readable for backward compatibility. |
| Current object manifest reference properties | `1` | Written by `S3ObjectManifestReferenceStore`; readable after restart. |
| Future/unknown object reference schema | `> 1` or invalid | Rejected when the reference is loaded instead of being interpreted ambiguously. |

The reference atomically maps an S3 bucket/key to one committed manifest/version pair and its associated object metadata and tags.

## Object ACL sidecar compatibility contract

| Format | Version | Runtime behavior |
|---|---:|---|
| Legacy ACL properties without `acl.schemaVersion` | `0` compatibility mode | Readable for backward compatibility. |
| Current object ACL sidecar properties | `1` | Written by `S3ObjectAclStore`; readable after restart. |
| Future/unknown object ACL schema | `> 1` or invalid | Rejected when the sidecar is loaded instead of being interpreted ambiguously. |

## Bucket capacity ledger compatibility contract

| Format | Version | Runtime behavior |
|---|---:|---|
| Legacy capacity properties without `capacity.schemaVersion` | `0` compatibility mode | Used bytes, quota, and rejection counters remain readable; persisted in-flight reservations are cleared on reopen. |
| Current bucket capacity properties | `1` | Written atomically by `FileSystemBucketCapacityStore`; committed usage, quota, and rejection accounting remain readable after restart. |
| Future/unknown capacity ledger schema | Any value other than `0` or `1`, or malformed text | Rejected during store construction instead of being interpreted ambiguously. |

Reservations represent process-local in-flight writes. Although their aggregate was written by legacy/current ledgers, reopening deliberately resets reserved bytes to zero so interrupted writes cannot leak quota after restart.

## Operator procedure before upgrades

1. Complete an offline backup of `storage.engine.filesystem.root`.
2. Confirm `/admin/ready` returns `status=ready` on the old version.
3. Stop the process gracefully.
4. Upgrade the application.
5. Start the process and confirm `/admin/ready` returns `status=ready`.
6. Validate representative S3 reads.
7. Keep the pre-upgrade backup until rollback is no longer needed.

## Validation evidence

`PhaseEp5StorageMigrationSpecsCucumberTest` validates `REQ-OPS-007` by saving a sample storage-engine object manifest, asserting the committed typed-artifact file declares schema version `2`, verifying the previous chunk-only schema version `1` and an unversioned legacy manifest as compatibility version `0`, and verifying unsupported schema version `999` is rejected.

The same runner validates `REQ-OPS-016` for durable multipart upload session JSON, `REQ-OPS-017` for bucket registry JSON, `REQ-OPS-018` for object configuration JSON, `REQ-OPS-019` for object manifest reference properties, and `REQ-OPS-020` for object ACL sidecars: current writes declare version `1`, omitted versions load as legacy version `0`, and future version `999` is rejected.

For `REQ-OPS-024`, the runner commits bucket usage, configures a quota, records a rejected reservation, and leaves an in-flight reservation before reopening the capacity ledger. It verifies current schema version `1`, unversioned legacy schema `0`, deterministic rejection of future version `999` and malformed version text, preservation of used/quota/rejection accounting, and clearing of stale reserved bytes.

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
- The declared EP-2 durable metadata families and the bucket capacity ledger now have explicit schema-version contracts. Any newly introduced durable metadata family must add its own current/legacy/future-version contract before it can be considered migration-safe.
