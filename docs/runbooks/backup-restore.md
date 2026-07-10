# Runbook: Storage-engine offline backup and restore

Status: first validated EP-5 slice (`REQ-OPS-005`).

## Scope

This runbook covers a single-node storage-engine deployment where Magrathea stores data under `storage.engine.filesystem.root`. It is an offline backup procedure: stop or drain the S3 process before copying the storage root.

Out of scope for this slice: online snapshots, incremental backups, remote object replication, multi-node quorum recovery, and declared RTO/RPO.

## Backup procedure

1. Confirm `/admin/ready` returns `status=ready`.
2. Stop the S3 process gracefully, for example with `SIGTERM`.
3. Confirm the process exits without forced termination.
4. Copy the full storage-engine filesystem root to the backup location, preserving all files and directories.
5. Keep the backup immutable until restore validation completes.

The root must include metadata and data directories such as `metadata/**`, `nodes/**`, and `devices/**` when present.

## Restore procedure

1. Keep the S3 process stopped.
2. Restore the backup contents to the configured `storage.engine.filesystem.root`.
3. Start the S3 process with the same storage-engine catalog configuration.
4. Confirm `/admin/ready` returns `status=ready`.
5. Validate representative S3 reads for restored buckets and objects.

## Validation evidence

`PhaseEp5OperabilityRequirementsCucumberTest` validates `REQ-OPS-005` by writing a bucket/object through S3, stopping the storage-engine process, copying the storage root to a backup location, deleting the primary root, restoring the backup, restarting the process, and reading the object back through S3.

Command:

```bash
mvn -pl s3-reactive-api-adapter -am \
  -Dtest=PhaseEp5OperabilityRequirementsCucumberTest \
  -Dsurefire.failIfNoSpecifiedTests=false test
```

The validation log must not contain `Using generated security password`.
