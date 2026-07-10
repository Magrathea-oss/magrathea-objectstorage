# Runbook: Single-node disaster recovery

Status: first validated EP-5 slice (`REQ-OPS-006`).

## Declared objective for the validated slice

| Objective | Value | Meaning |
|---|---:|---|
| RTO | 30 seconds | From starting restore from an available offline backup to `/admin/ready` and readable representative S3 data in the rehearsal harness. |
| RPO | Last completed offline backup | Data committed before the completed backup must be recoverable. Data written after that backup is outside this first single-node offline DR slice. |

## Assumptions

- Deployment uses the `storage-engine` profile.
- Storage catalogs are available and match the restored deployment.
- A completed offline backup of `storage.engine.filesystem.root` exists.
- The process is restarted on a compatible Magrathea version and storage-engine schema.

## Rehearsal procedure

1. Stop the failed or degraded primary process if it is still running.
2. Declare the backup generation to restore.
3. Recreate or empty the primary `storage.engine.filesystem.root` location.
4. Copy the chosen backup into the primary storage root.
5. Start Magrathea with the same storage-engine catalog configuration.
6. Wait for `/admin/ready` to return `status=ready`.
7. Validate representative S3 reads from the restored recovery point.
8. Record elapsed recovery time and compare it to the RTO.

## Validation evidence

`PhaseEp5OperabilityRequirementsCucumberTest` validates `REQ-OPS-006` by declaring RTO `30 seconds` and RPO `last completed offline backup`, writing an S3 object, completing an offline backup, deleting the primary root, restoring from backup, starting the process, checking the RTO, and reading the object back through S3.

Command:

```bash
mvn -pl s3-reactive-api-adapter -am \
  -Dtest=PhaseEp5OperabilityRequirementsCucumberTest \
  -Dsurefire.failIfNoSpecifiedTests=false test
```

The validation log must not contain `Using generated security password`.

## Open gaps

- Online or incremental backup RPO is not declared.
- Multi-node/quorum DR is not in scope until EP-10.
- Cross-version schema migration is a separate EP-5 slice.
- External alerting and paging workflow are not yet validated.
