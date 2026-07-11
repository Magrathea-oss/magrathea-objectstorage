#!/usr/bin/env python3
"""Generate the evidence-based S3 operation coverage matrix.

The canonical inventory is the 111-operation AWS CLI/botocore S3 model snapshot
used by this repository. Route mappings and Gherkin status tags are evidence;
handler existence alone never upgrades semantic status.
"""
from __future__ import annotations

import argparse
import re
from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]
ROUTER = ROOT / "s3-reactive-api-adapter/src/main/java/com/example/magrathea/s3api/adapter/web/S3PathRouter.java"
FEATURE_ROOT = ROOT / "s3-reactive-api-adapter/src/test/features"
OUTPUT = ROOT / "docs/api-coverage.md"

OPERATIONS = """AbortMultipartUpload
CompleteMultipartUpload
CopyObject
CreateBucket
CreateBucketMetadataConfiguration
CreateBucketMetadataTableConfiguration
CreateMultipartUpload
CreateSession
DeleteBucket
DeleteBucketAnalyticsConfiguration
DeleteBucketCors
DeleteBucketEncryption
DeleteBucketIntelligentTieringConfiguration
DeleteBucketInventoryConfiguration
DeleteBucketLifecycle
DeleteBucketMetadataConfiguration
DeleteBucketMetadataTableConfiguration
DeleteBucketMetricsConfiguration
DeleteBucketOwnershipControls
DeleteBucketPolicy
DeleteBucketReplication
DeleteBucketTagging
DeleteBucketWebsite
DeleteObject
DeleteObjectTagging
DeleteObjects
DeletePublicAccessBlock
GetBucketAbac
GetBucketAccelerateConfiguration
GetBucketAcl
GetBucketAnalyticsConfiguration
GetBucketCors
GetBucketEncryption
GetBucketIntelligentTieringConfiguration
GetBucketInventoryConfiguration
GetBucketLifecycle
GetBucketLifecycleConfiguration
GetBucketLocation
GetBucketLogging
GetBucketMetadataConfiguration
GetBucketMetadataTableConfiguration
GetBucketMetricsConfiguration
GetBucketNotification
GetBucketNotificationConfiguration
GetBucketOwnershipControls
GetBucketPolicy
GetBucketPolicyStatus
GetBucketReplication
GetBucketRequestPayment
GetBucketTagging
GetBucketVersioning
GetBucketWebsite
GetObject
GetObjectAcl
GetObjectAttributes
GetObjectLegalHold
GetObjectLockConfiguration
GetObjectRetention
GetObjectTagging
GetObjectTorrent
GetPublicAccessBlock
HeadBucket
HeadObject
ListBucketAnalyticsConfigurations
ListBucketIntelligentTieringConfigurations
ListBucketInventoryConfigurations
ListBucketMetricsConfigurations
ListBuckets
ListDirectoryBuckets
ListMultipartUploads
ListObjectVersions
ListObjects
ListObjectsV2
ListParts
PutBucketAbac
PutBucketAccelerateConfiguration
PutBucketAcl
PutBucketAnalyticsConfiguration
PutBucketCors
PutBucketEncryption
PutBucketIntelligentTieringConfiguration
PutBucketInventoryConfiguration
PutBucketLifecycle
PutBucketLifecycleConfiguration
PutBucketLogging
PutBucketMetricsConfiguration
PutBucketNotification
PutBucketNotificationConfiguration
PutBucketOwnershipControls
PutBucketPolicy
PutBucketReplication
PutBucketRequestPayment
PutBucketTagging
PutBucketVersioning
PutBucketWebsite
PutObject
PutObjectAcl
PutObjectLegalHold
PutObjectLockConfiguration
PutObjectRetention
PutObjectTagging
PutPublicAccessBlock
RenameObject
RestoreObject
SelectObjectContent
UpdateBucketMetadataInventoryTableConfiguration
UpdateBucketMetadataJournalTableConfiguration
UpdateObjectEncryption
UploadPart
UploadPartCopy
WriteGetObjectResponse""".splitlines()

NEGATIVE_TAGS = ("@placeholder", "@not-implemented", "@config-only", "@partial")
ALIASES = {
    "initiateMultipartUpload": ("CreateMultipartUpload",),
    "listBucketsXml": ("ListBuckets",),
    "listBucketsJson": ("ListBuckets",),
    "listObjectsXml": ("ListObjects",),
    "listObjectsV2Xml": ("ListObjectsV2",),
    "getBucketAccelerate": ("GetBucketAccelerateConfiguration",),
    "putBucketAccelerate": ("PutBucketAccelerateConfiguration",),
    "getBucketAnalytics": ("GetBucketAnalyticsConfiguration",),
    "putBucketAnalytics": ("PutBucketAnalyticsConfiguration",),
    "deleteBucketAnalytics": ("DeleteBucketAnalyticsConfiguration",),
    "getBucketInventory": ("GetBucketInventoryConfiguration",),
    "putBucketInventory": ("PutBucketInventoryConfiguration",),
    "deleteBucketInventory": ("DeleteBucketInventoryConfiguration",),
    "getBucketMetrics": ("GetBucketMetricsConfiguration",),
    "putBucketMetrics": ("PutBucketMetricsConfiguration",),
    "deleteBucketMetrics": ("DeleteBucketMetricsConfiguration",),
    "getBucketIntelligentTiering": ("GetBucketIntelligentTieringConfiguration",),
    "putBucketIntelligentTiering": ("PutBucketIntelligentTieringConfiguration",),
    "deleteBucketIntelligentTiering": ("DeleteBucketIntelligentTieringConfiguration",),
    "putBucketMetadataConfiguration": ("CreateBucketMetadataConfiguration",),
    "putBucketMetadataTableConfiguration": ("CreateBucketMetadataTableConfiguration",),
    "putBucketInventoryTableConfiguration": ("UpdateBucketMetadataInventoryTableConfiguration",),
    "putBucketJournalTableConfiguration": ("UpdateBucketMetadataJournalTableConfiguration",),
    "getBucketLifecycle": ("GetBucketLifecycle", "GetBucketLifecycleConfiguration"),
    "putBucketLifecycle": ("PutBucketLifecycle", "PutBucketLifecycleConfiguration"),
    "getBucketNotification": ("GetBucketNotification", "GetBucketNotificationConfiguration"),
    "putBucketNotification": ("PutBucketNotification", "PutBucketNotificationConfiguration"),
}


def route_map() -> dict[str, set[str]]:
    handlers = re.findall(r"::([A-Za-z0-9_]+)\)", ROUTER.read_text(encoding="utf-8"))
    mapped: dict[str, set[str]] = {operation: set() for operation in OPERATIONS}
    for handler in handlers:
        candidates = ALIASES.get(handler, (handler[0].upper() + handler[1:],))
        for operation in candidates:
            if operation in mapped:
                mapped[operation].add(handler)
    return mapped


def scenarios() -> list[tuple[str, str, set[str], str]]:
    result = []
    for path in sorted(FEATURE_ROOT.rglob("*.feature")):
        lines = path.read_text(encoding="utf-8").splitlines()
        pending_tags: list[str] = []
        index = 0
        while index < len(lines):
            stripped = lines[index].strip()
            if stripped.startswith("@"):
                pending_tags.extend(stripped.split())
                index += 1
                continue
            match = re.match(r"Scenario(?: Outline)?:\s*(.+)", stripped)
            if match:
                title = match.group(1)
                body = [stripped]
                cursor = index + 1
                while cursor < len(lines):
                    candidate = lines[cursor].strip()
                    if candidate.startswith("@") or re.match(r"Scenario(?: Outline)?:", candidate) or candidate.startswith("Rule:"):
                        break
                    body.append(candidate)
                    cursor += 1
                result.append((str(path.relative_to(ROOT)), title, set(pending_tags), "\n".join(body)))
                pending_tags = []
                index = cursor
                continue
            if stripped and not stripped.startswith("#"):
                pending_tags = []
            index += 1
    return result


def evidence_for(operation: str, all_scenarios):
    pattern = re.compile(rf"\b{re.escape(operation)}\b", re.IGNORECASE)
    matches = [scenario for scenario in all_scenarios if pattern.search(scenario[3])]
    validated = [s for s in matches if "@implemented-and-validated" in s[2] and not any(tag in s[2] for tag in NEGATIVE_TAGS)]
    if validated:
        status = "implemented-and-validated"
        selected = validated
    else:
        tags = {tag for s in matches for tag in s[2]}
        status = next((tag[1:] for tag in NEGATIVE_TAGS if tag in tags), None)
        if status is None and "@implemented-not-e2e-validated" in tags:
            status = "implemented-not-e2e-validated"
        if status is None:
            status = "pending-evidence"
        selected = matches
    modes = set()
    for _, _, tags, _ in selected:
        if "@webclient-required" in tags:
            modes.add("WebTestClient")
        if "@awscli-required" in tags:
            modes.add("AWS CLI")
        if "@full-process-restart-required" in tags or "@ep2-full-process-restart" in tags:
            modes.add("full-process restart")
    references = sorted({f"{path}: {title}" for path, title, _, _ in selected})
    return status, ", ".join(sorted(modes)) or "—", references


def render() -> str:
    if len(OPERATIONS) != 111 or len(set(OPERATIONS)) != 111:
        raise SystemExit("Canonical S3 operation inventory must contain 111 distinct operations")
    mapped = route_map()
    all_scenarios = scenarios()
    rows = []
    counts: dict[str, int] = {}
    mapped_count = 0
    for operation in OPERATIONS:
        handlers = mapped[operation]
        if handlers:
            mapped_count += 1
        status, modes, references = evidence_for(operation, all_scenarios)
        counts[status] = counts.get(status, 0) + 1
        evidence = "<br>".join(ref.replace("|", "\\|") for ref in references[:3]) or "—"
        rows.append(f"| `{operation}` | {'Yes' if handlers else 'No'} | {', '.join(sorted(handlers)) or '—'} | `{status}` | {modes} | {evidence} |")
    validated = counts.get("implemented-and-validated", 0)
    pending = len(OPERATIONS) - validated
    summary = "\n".join(f"| `{status}` | {count} |" for status, count in sorted(counts.items()))
    return f"""# S3 API semantic coverage

Generated by `scripts/generate-s3-api-coverage.py`. Do not edit manually.

## Interpretation

The canonical inventory contains **111 official S3 operations** from the AWS CLI/botocore S3 service-model snapshot used for this baseline. Route presence is inventory evidence only. An operation is counted as **implemented-and-validated** only when an executable scenario names that operation and carries `@implemented-and-validated` without `@partial`, `@config-only`, `@placeholder`, or `@not-implemented`.

This is deliberately conservative: `pending-evidence` can mean absent, placeholder, partial, config-only, implemented without operation-linked semantic evidence, or simply not yet classified. It must not be reported as implemented.

- Official operations: **{len(OPERATIONS)}**
- Operations with a mapped router handler: **{mapped_count}**
- Implemented-and-validated with explicit operation-linked evidence: **{validated}**
- Not yet eligible for a 100% completion claim: **{pending}**

## Semantic status summary

| Status | Operations |
|---|---:|
{summary}

## Operation matrix

| Official operation | Route mapped | Handler evidence | Semantic status | Explicit validation modes | Requirement evidence |
|---|---|---|---|---|---|
{chr(10).join(rows)}

## Guardrails

- A route or handler never upgrades semantic status.
- Status-only protocol smoke tests never upgrade semantic status.
- Configuration persistence does not imply enforcement or background processing.
- Missing exact operation-linked evidence remains pending until reviewed or covered by a semantic requirement.
- The matrix reports repository evidence, not complete parity with every AWS edge case.
"""


def main() -> None:
    parser = argparse.ArgumentParser()
    parser.add_argument("--check", action="store_true")
    args = parser.parse_args()
    generated = render()
    if args.check:
        if not OUTPUT.exists() or OUTPUT.read_text(encoding="utf-8") != generated:
            raise SystemExit(f"STALE: {OUTPUT.relative_to(ROOT)}")
        print(f"OK: {OUTPUT.relative_to(ROOT)} is fresh")
    else:
        OUTPUT.write_text(generated, encoding="utf-8")
        print(f"Generated {OUTPUT.relative_to(ROOT)}")


if __name__ == "__main__":
    main()
