#!/usr/bin/env python3
"""Summarize Dependency-Check JSON without turning scan failures into clean results."""

from __future__ import annotations

import argparse
import datetime as dt
import hashlib
import json
import re
import subprocess
import xml.etree.ElementTree as ET
from pathlib import Path
from typing import Any

ERROR_PATTERNS = re.compile(
    r"(?i)(NoDataException|UpdateException|Unable to update|NVD_API_KEY is not set|NVD.*(?:403|429)|"
    r"rate.?limit|connection (?:refused|reset)|timed? out|database.*(?:corrupt|error)|"
    r"fatal exception)"
)
DATE_PATTERN = re.compile(r"(?i)\b(?:expires|review-by)\s*:\s*(\d{4}-\d{2}-\d{2})\b")


def utc_now() -> str:
    return dt.datetime.now(dt.timezone.utc).isoformat().replace("+00:00", "Z")


def revision() -> str | None:
    try:
        return subprocess.check_output(
            ["git", "rev-parse", "HEAD"], text=True, stderr=subprocess.DEVNULL
        ).strip()
    except (OSError, subprocess.CalledProcessError):
        return None


def as_list(value: Any) -> list[Any]:
    return value if isinstance(value, list) else []


def suppression_policy(path: Path) -> tuple[list[dict[str, Any]], list[str]]:
    entries: list[dict[str, Any]] = []
    errors: list[str] = []
    if not path.is_file():
        return entries, [f"Committed suppression file is missing: {path}"]
    try:
        root = ET.parse(path).getroot()
    except ET.ParseError as exc:
        return entries, [f"Suppression file is invalid XML: {exc}"]
    today = dt.date.today()
    for number, element in enumerate(list(root), start=1):
        children = {child.tag.rsplit("}", 1)[-1]: (child.text or "").strip() for child in element}
        notes = children.get("notes", "")
        selectors = {key: value for key, value in children.items() if key != "notes"}
        issues: list[str] = []
        for label in ("Rationale:", "Owner:"):
            if label.lower() not in notes.lower():
                issues.append(f"missing {label[:-1]}")
        date_match = DATE_PATTERN.search(notes)
        if not date_match:
            issues.append("missing Expires or Review-by date")
        else:
            try:
                if dt.date.fromisoformat(date_match.group(1)) < today:
                    issues.append(f"expired on {date_match.group(1)}")
            except ValueError:
                issues.append("invalid expiry/review date")
        dependency_selectors = {"sha1", "filePath", "packageUrl", "gav"}
        vulnerability_selectors = {"cve", "cwe", "vulnerabilityName"}
        if not dependency_selectors.intersection(selectors):
            issues.append("missing narrow dependency identity (sha1, filePath, packageUrl, or gav)")
        if not vulnerability_selectors.intersection(selectors):
            issues.append("missing vulnerability identifier (cve, cwe, or vulnerabilityName)")
        if any(value in {".*", "^.*$", ".+", "^.+$"} for value in selectors.values()):
            issues.append("obviously broad selector")
        entry = {"number": number, "selectors": selectors, "notes": notes, "policyIssues": issues}
        entries.append(entry)
        errors.extend(f"Suppression {number}: {issue}" for issue in issues)
    return entries, errors


def inventory_identity(dependencies: list[dict[str, Any]]) -> tuple[str, int]:
    inventory = []
    for dep in dependencies:
        inventory.append({
            "fileName": dep.get("fileName"),
            "filePath": dep.get("filePath"),
            "sha256": dep.get("sha256"),
            "packages": sorted(
                str(pkg.get("id")) for pkg in as_list(dep.get("packages")) if isinstance(pkg, dict)
            ),
        })
    encoded = json.dumps(inventory, sort_keys=True, separators=(",", ":")).encode()
    return hashlib.sha256(encoded).hexdigest(), len(inventory)


def main() -> int:
    parser = argparse.ArgumentParser()
    parser.add_argument("--report", type=Path, required=True)
    parser.add_argument("--log", type=Path, required=True)
    parser.add_argument("--output", type=Path, required=True)
    parser.add_argument("--suppression-file", type=Path, required=True)
    parser.add_argument("--command-exit-code", type=int)
    args = parser.parse_args()

    generated_at = utc_now()
    scan_errors: list[str] = []
    report: dict[str, Any] = {}
    if args.report.is_file():
        try:
            report = json.loads(args.report.read_text(encoding="utf-8"))
        except (OSError, json.JSONDecodeError) as exc:
            scan_errors.append(f"Dependency-Check JSON is unreadable: {exc}")
    else:
        scan_errors.append(f"Dependency-Check JSON was not generated: {args.report}")

    log_matches: list[str] = []
    if args.log.is_file():
        for line in args.log.read_text(encoding="utf-8", errors="replace").splitlines():
            if ERROR_PATTERNS.search(line):
                log_matches.append(line.strip())
        # Preserve actionable evidence without making the analysis artifact unbounded.
        log_matches = list(dict.fromkeys(log_matches))[-50:]
    scan_info = report.get("scanInfo") if isinstance(report.get("scanInfo"), dict) else {}
    data_sources = as_list(scan_info.get("dataSource"))
    if report and not data_sources:
        scan_errors.append("Dependency-Check JSON has no vulnerability data-source metadata")
    # A valid cached database may legitimately be used while the network is unavailable.
    # Log update diagnostics become scan errors only when complete report evidence is absent;
    # otherwise they remain visible as warnings and do not masquerade as findings.
    if not report or not data_sources:
        scan_errors.extend(log_matches)
    dependencies = [dep for dep in as_list(report.get("dependencies")) if isinstance(dep, dict)]
    unsuppressed: list[dict[str, Any]] = []
    suppressed: list[dict[str, Any]] = []
    for dep in dependencies:
        identity = {"fileName": dep.get("fileName"), "filePath": dep.get("filePath")}
        for finding in as_list(dep.get("vulnerabilities")):
            if isinstance(finding, dict):
                record = {**identity, "finding": finding}
                is_suppressed = finding.get("suppressed") is True or finding.get("isSuppressed") is True
                (suppressed if is_suppressed else unsuppressed).append(record)
        for finding in as_list(dep.get("suppressedVulnerabilities")):
            if isinstance(finding, dict):
                suppressed.append({**identity, "finding": finding})
    for finding in as_list(report.get("suppressedVulnerabilities")):
        if isinstance(finding, dict):
            suppressed.append({"fileName": finding.get("fileName"), "finding": finding})

    if args.command_exit_code not in (None, 0):
        # A completed report with unsuppressed findings may exit nonzero solely because
        # failBuildOnCVSS enforced the configured gate. Scanner/update diagnostics are
        # different: they keep the assessment explicitly incomplete even if stale cached
        # report bytes happen to exist.
        if report and data_sources and log_matches:
            scan_errors.extend(log_matches)
        if not unsuppressed and not scan_errors:
            scan_errors.append(
                f"Dependency-Check command exited {args.command_exit_code}; the assessment fails closed"
            )

    suppression_entries, suppression_errors = suppression_policy(args.suppression_file)
    scan_errors.extend(suppression_errors)
    inventory_hash, dependency_count = inventory_identity(dependencies)
    vulnerability_timestamps = [
        source.get("timestamp") for source in data_sources
        if isinstance(source, dict) and source.get("timestamp")
    ]
    complete = bool(report) and bool(data_sources) and not scan_errors
    result = {
        "schemaVersion": 1,
        "generatedAt": generated_at,
        "sourceRevision": revision(),
        "scanStatus": "complete" if complete else "error",
        "policy": {"failBuildOnCVSS": 7.0, "suppressionFile": str(args.suppression_file)},
        "dependencyCheck": {
            "engineVersion": scan_info.get("engineVersion"),
            "reportDate": report.get("projectInfo", {}).get("reportDate") if isinstance(report.get("projectInfo"), dict) else None,
            "dataSources": data_sources,
            "vulnerabilityDataTimestamps": vulnerability_timestamps,
            "jsonReport": str(args.report),
            "htmlReport": str(args.report.with_suffix(".html")),
        },
        "dependencyInventory": {"count": dependency_count, "sha256": inventory_hash},
        "unsuppressedFindings": {"count": len(unsuppressed), "items": unsuppressed},
        "suppressedFindings": {"count": len(suppressed), "items": suppressed},
        "configuredSuppressions": {"count": len(suppression_entries), "items": suppression_entries},
        "scanErrors": {"count": len(scan_errors), "items": scan_errors},
        "scanWarnings": {"count": len(log_matches) if complete else 0, "items": log_matches if complete else []},
        "commandExitCode": args.command_exit_code,
        "clean": complete and not unsuppressed and not suppressed,
    }
    # Never use "clean" for an incomplete scan.
    if not complete:
        result["clean"] = None
    args.output.parent.mkdir(parents=True, exist_ok=True)
    args.output.write_text(json.dumps(result, indent=2) + "\n", encoding="utf-8")

    error_artifact = args.report.parent / "dependency-check-scan-error.json"
    if not complete:
        error_artifact.write_text(json.dumps({
            "generatedAt": generated_at,
            "scanStatus": "error",
            "scanErrors": result["scanErrors"],
            "analysis": str(args.output),
            "log": str(args.log),
        }, indent=2) + "\n", encoding="utf-8")
        print(f"Incomplete Dependency-Check scan; error artifact: {error_artifact}")
    elif error_artifact.exists():
        error_artifact.unlink()
    print(json.dumps({
        "scanStatus": result["scanStatus"],
        "dependencies": dependency_count,
        "unsuppressedFindings": len(unsuppressed),
        "suppressedFindings": len(suppressed),
        "scanErrors": len(scan_errors),
        "analysis": str(args.output),
    }))
    return 0 if complete else 2


if __name__ == "__main__":
    raise SystemExit(main())
