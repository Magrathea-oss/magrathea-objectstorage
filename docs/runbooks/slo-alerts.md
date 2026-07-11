# SLO and alert rules runbook

This runbook documents the first EP-5 alerting bundle for single-node Magrathea Object Storage deployments.
It complements the validated `/admin/live`, `/admin/ready`, offline backup/restore, single-node disaster recovery,
and manifest schema-versioning rehearsals.

The shipped rule files are:

- Prometheus rules: `ops/prometheus/magrathea-objectstorage-alerts.yml`
- Loki log-safety rules: `ops/loki/magrathea-objectstorage-log-alerts.yml`

These rules are intended as a portable starter pack. The baseline Cucumber validation proves that the rule bundle is present,
contains the expected objectives, has actionable runbook links, and keeps the generated-password regression visible.
The opt-in `REQ-OPS-021` validation additionally proves live Prometheus evaluation and Alertmanager delivery to an operator webhook. Live Loki ruler delivery and external paging integrations are not yet validated.

## Service-level objectives

| Objective | Target | Primary signal | Notes |
| --- | --- | --- | --- |
| `admin-liveness` | 99.9% monthly success | Black-box probe of `GET /admin/live` | Detects process or listener failure. |
| `admin-readiness` | 99.5% monthly success | Black-box probe of `GET /admin/ready` | Must fail closed when required storage catalogs are unavailable. |
| `s3-smoke-availability` | 99.0% monthly success | Black-box S3 ListBuckets/PutObject/GetObject smoke probe | Confirms the selected backend can serve minimal S3 traffic. |
| `storage-capacity` | 15% minimum free filesystem ratio | Storage-engine filesystem capacity metric | Requires platform-specific filesystem metric wiring. |
| `backup-rpo` | Last completed offline backup not older than 24h | Backup timestamp metric | Aligns with the currently validated single-node RPO: last completed offline backup. |
| `security-mode-safety` | 0 generated-password banners | Spring Boot generated-password banner log search | Any occurrence is a release-blocking configuration regression. |
| `schema-migration-safety` | 0 unsupported manifest schema errors | Unsupported manifest schema version logs | Indicates a storage root contains files from a future or invalid manifest schema. |

## Alert response

### MagratheaAdminLivenessProbeDown

1. Confirm the process is running and bound to the expected Admin API port.
2. Inspect container logs for startup failures and generated-password banners.
3. Restart only after collecting logs if the process is wedged.

### MagratheaAdminReadinessProbeNotReady

1. Request `/admin/ready` and identify the not-ready component.
2. Verify `storage.engine.policies.dir`, `storage.engine.devices.dir`, and `storage.engine.disksets.dir` point to readable catalogs.
3. Confirm packaged containers are running with `SPRING_PROFILES_ACTIVE=storage-engine` and `MAGRATHEA_OBJECT_STORE_BACKEND=storage-engine`.

### MagratheaS3SmokeProbeFailing

1. Check `/admin/ready` before investigating S3 routes.
2. Confirm the S3 smoke probe uses the expected security mode and credentials.
3. Validate bucket/object PUT and GET against a known temporary key.
4. Inspect storage-engine logs and filesystem root for recent I/O errors.

### MagratheaStorageEngineFilesystemNearFull

1. Stop nonessential writes if free capacity continues to fall.
2. Add capacity or move the storage root to a larger filesystem.
3. Re-run the S3 smoke probe and backup procedure after capacity is restored.

### MagratheaOfflineBackupTooOld

1. Run the offline backup procedure in `docs/runbooks/backup-restore.md`.
2. Confirm the backup location contains the storage-engine `metadata` directory and object data.
3. Update the backup timestamp metric only after a successful copy and verification.

### MagratheaGeneratedSecurityPasswordBannerDetected

1. Treat this as a release-blocking security-mode regression.
2. Confirm `MagratheaApplication` still excludes default Spring Boot user-details auto-configuration for packaged runtimes.
3. Confirm `s3.security.enabled=false` is explicit for unsecured smoke tests, or real S3 credentials are configured for secured mode.
4. Do not route production traffic until new runtime logs are free of `Using generated security password`.

### MagratheaUnsupportedManifestSchemaDetected

1. Stop writes to the affected storage-engine root.
2. Follow `docs/runbooks/schema-migration.md` and identify the manifest path and schema version.
3. Restore from the last compatible backup if the files were produced by an unsupported future version.

## Live delivery validation

Run the Docker-dependent validation separately from the default focused gate:

```bash
mvn -pl s3-reactive-api-adapter -am \
  -Dtest=PhaseEp5LiveAlertDeliveryRequirementsCucumberTest \
  -Dsurefire.failIfNoSpecifiedTests=false test
```

The runner uses `scripts/validate-live-alert-delivery.sh`, Quay-hosted Prometheus `v3.5.0` and Alertmanager `v0.28.1`, and host networking. It validates the exact shipped Prometheus rule file with `promtool`, exposes a deterministic failing Admin liveness probe, evaluates the liveness alert with an immediate test threshold, and verifies Alertmanager posts `MagratheaAdminLivenessProbeDown` to a temporary operator webhook receiver. Containers and temporary files are removed on exit.

## Open gaps

- Live Loki ruler delivery and external notification/paging receivers are not yet end-to-end validated.
- Capacity and backup timestamp metrics require environment-specific exporters or future native Magrathea metrics.
- Online/incremental DR and multi-node alerting remain future EP-5 work.
