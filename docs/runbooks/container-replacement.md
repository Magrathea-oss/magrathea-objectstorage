# Canonical JVM container replacement runbook

This runbook covers the supported `0.1.0` single-node JVM 21 OCI image. It validates replacement of the container while retaining the named volume mounted at `/app/data`. It does not cover replacement or migration of the volume itself.

## Preconditions

- Docker and `curl` are available.
- S3 port `8080` and Admin port `8081` are free.
- The checked-out source revision is the revision intended for the image.
- The release version is exactly `0.1.0`.

The image must identify itself with OCI labels `org.opencontainers.image.version`, `org.opencontainers.image.revision`, and `org.opencontainers.image.source`. The runtime user must be the non-root `magrathea` user.

## Deterministic validation

Run the Docker-required Cucumber requirement (excluded from ordinary Maven test execution):

```bash
mvn -B --no-transfer-progress -Pdocker-cucumber-tests \
  -pl s3-reactive-api-adapter -am \
  -Dsurefire.failIfNoSpecifiedTests=false \
  -Dtest=PhaseEp5JvmContainerReplacementRequirementsCucumberIT test
```

The Cucumber glue invokes:

```bash
bash scripts/validate-jvm-container-replacement.sh
```

The script builds the canonical `Dockerfile` with the expected OCI identity, creates a fresh named volume, and then:

1. starts the first container with that volume mounted at `/app/data`;
2. proves the image and process are non-root;
3. requires `/admin/live` and `/admin/ready` to succeed, with readiness status `ready`;
4. creates bucket `ep5-release-volume-bucket` and writes `release/persistent.txt` through the S3 API;
5. sends `SIGTERM` directly and refuses to use `SIGKILL` as a successful result;
6. removes the stopped container, retaining the volume;
7. creates a different container from the same image ID and named volume;
8. rechecks liveness and readiness;
9. reads the object and byte-compares it with the original body;
10. checks exact version, source revision, and source URL OCI labels.

The default uses host networking because that is required by the local Docker sandbox. Ports, image name, version, revision, source URL, and network mode can be supplied through the `REQ_OPS_025_*` environment variables defined at the top of the script.

## Release gate

`.github/workflows/release.yml` runs this Cucumber requirement after version coherence and the complete Maven/source-freshness gates, but before registry login, image publication, or GitHub release creation. A failed replacement, identity, non-root, readiness, SIGTERM, or byte-comparison assertion blocks publication.

## Operator replacement procedure

1. Confirm a current backup and successful `/admin/ready` response.
2. Stop routing requests to the single node.
3. send `SIGTERM` and wait for graceful process exit; do not immediately force removal.
4. Remove only the stopped container. Do not delete its persistent volume.
5. Create the replacement from the intended immutable image digest and mount the same volume at `/app/data`.
6. Require HTTP success from `/admin/live` and `/admin/ready`, and require readiness status `ready`.
7. Read a representative committed object and compare its expected bytes or checksum.
8. Verify the running image digest and OCI version/revision/source labels against the release record.

Deleting the volume deletes the durable single-node state. This procedure is not a high-availability or rolling-upgrade procedure.
