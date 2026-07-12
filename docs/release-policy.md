# Release policy

## Versioning and current scope

Magrathea Object Storage uses [Semantic Versioning 2.0.0](https://semver.org/). The first planned release is `v0.1.0`; its Maven and OCI version is `0.1.0`. While the major version is zero, every release is a preview: behavior, configuration, and durable-data schemas may change incompatibly between minor releases. Patch releases in a minor line are reserved for backward-compatible fixes to that preview line.

`v0.1.0` supports one Linux JVM 21 OCI image for a **single-node preview**. The supported container path uses the `storage-engine` profile and a persistent volume mounted at `/app/data`. Replacing the container image must preserve that volume; deleting or replacing the volume deletes the committed data. This preview is not a claim of high availability, distributed durability, rolling upgrades, or complete S3 compatibility.

The GraalVM native image remains experimental. It is built only by the explicit manual CI job, is not published by the release workflow, and is not a supported `v0.1.0` release artifact.

## Release identity and immutability

A release starts only from a signed-off repository state tagged with an exact `vMAJOR.MINOR.PATCH` tag. The tag-driven workflow converts the reactor version to the matching non-SNAPSHOT Maven version and refuses a mismatch before testing or publishing.

The JVM image is published to GitHub Container Registry with these tags:

- exact version, for example `0.1.0`;
- immutable preview minor line, for example `0.1`;
- full commit identity, for example `sha-0123456789abcdef...`.

For the preview line, the workflow refuses to overwrite any of those tags. Consequently, publishing another patch into an already-published immutable minor tag requires an explicit future policy change rather than silently moving the tag.

Every published image carries OCI labels for version, source revision, source URL, MIT license, and creation time. The GitHub release records the registry-qualified image digest. Consumers that require byte-for-byte identity should deploy the digest, not a human-readable tag.

## Required gate

Before the canonical JVM image can be built or published, automation runs the complete root `mvn test` reactor gate with normal fail-fast Maven failure semantics, verifies the generated Gherkin appendix and S3 API coverage report are fresh, and runs source hygiene checks. Focused Cucumber execution remains a supplementary early-feedback job and does not replace the complete root gate.

The release workflow must also pass the Docker-required `REQ-OPS-023` local immutable-registry rehearsal, `REQ-OPS-025`, and the live Prometheus-to-Alertmanager delivery gate before registry login or publication. The local registry rehearsal publishes the version, minor-line, and commit-SHA tags, verifies they resolve to one digest, and proves the overwrite guard refuses a repeated attempt.

`REQ-OPS-025` This Docker-required Cucumber gate builds the versioned JVM image with the tagged source identity, runs it non-root with a named volume at `/app/data`, writes a bucket and object through S3, stops with `SIGTERM`, replaces the container from the same image and volume, requires `/admin/live` and ready `/admin/ready`, byte-compares the persisted object, and verifies OCI version/revision/source labels. Ordinary Maven execution excludes this Docker-required scenario; use the `docker-cucumber-tests` profile. See [`runbooks/container-replacement.md`](runbooks/container-replacement.md).
