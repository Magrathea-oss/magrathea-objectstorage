#!/usr/bin/env python3
"""Generate the canonical, threshold-free coverage evidence summary from JaCoCo XML."""

from __future__ import annotations

import argparse
import datetime as dt
import json
import os
import subprocess
from pathlib import Path
import xml.etree.ElementTree as ET

COUNTERS = ("INSTRUCTION", "BRANCH", "LINE", "COMPLEXITY", "METHOD", "CLASS")


def git(root: Path, *args: str) -> str:
    return subprocess.run(
        ["git", *args], cwd=root, check=True, text=True, stdout=subprocess.PIPE
    ).stdout.strip()


def counters(element: ET.Element) -> dict[str, dict[str, int | float | None]]:
    found = {node.attrib["type"]: node for node in element.findall("counter")}
    result: dict[str, dict[str, int | float | None]] = {}
    for counter_type in COUNTERS:
        node = found.get(counter_type)
        missed = int(node.attrib["missed"]) if node is not None else 0
        covered = int(node.attrib["covered"]) if node is not None else 0
        total = missed + covered
        result[counter_type.lower()] = {
            "covered": covered,
            "missed": missed,
            "total": total,
            "percentage": round(covered * 100.0 / total, 2) if total else None,
        }
    return result


def main() -> None:
    parser = argparse.ArgumentParser()
    parser.add_argument("--root", type=Path, required=True)
    args = parser.parse_args()
    root = args.root.resolve()
    report_dir = root / "target/site/jacoco-aggregate"
    xml_path = report_dir / "jacoco.xml"
    csv_path = report_dir / "jacoco.csv"
    html_path = report_dir / "index.html"
    for path in (xml_path, csv_path, html_path):
        if not path.is_file():
            raise SystemExit(f"Missing canonical JaCoCo report: {path}")

    report = ET.parse(xml_path).getroot()
    modules = [
        {"module": group.attrib["name"], "counters": counters(group)}
        for group in report.findall("group")
    ]
    modules.sort(key=lambda item: item["module"])
    if not modules:
        raise SystemExit("Canonical JaCoCo XML contains no production module groups")

    revision = git(root, "rev-parse", "HEAD")
    revision_time = git(root, "show", "-s", "--format=%cI", revision)
    dirty = bool(git(root, "status", "--porcelain"))
    exec_paths = sorted(
        path.relative_to(root).as_posix()
        for path in root.glob("*/target/jacoco.exec")
        if path.is_file()
    )
    source_date_epoch = os.environ.get("SOURCE_DATE_EPOCH")
    if source_date_epoch is not None:
        generated = dt.datetime.fromtimestamp(int(source_date_epoch), dt.timezone.utc)
        timestamp_source = "SOURCE_DATE_EPOCH"
    else:
        generated = dt.datetime.now(dt.timezone.utc)
        timestamp_source = "system-clock"
    generated_at = generated.replace(microsecond=0).isoformat()
    relative = lambda path: path.relative_to(root).as_posix()
    modules_with_exec = {Path(path).parts[0] for path in exec_paths}
    modules_without_exec = sorted(
        item["module"] for item in modules if item["module"] not in modules_with_exec
    )

    summary = {
        "schemaVersion": 1,
        "evidence": {
            "generatedAt": generated_at,
            "generationTimestampSource": timestamp_source,
            "revision": revision,
            "revisionTimestamp": revision_time,
            "workingTreeDirty": dirty,
            "sourcePaths": {
                "jacocoXml": relative(xml_path),
                "jacocoCsv": relative(csv_path),
                "jacocoHtml": relative(html_path),
                "executionData": exec_paths,
            },
            "modulesWithoutLocalExecutionData": modules_without_exec,
        },
        "assessment": {
            "thresholdEnforced": False,
            "thresholds": {},
            "classification": "descriptive-baseline",
            "note": "Report generation is evidence collection, not a claim that coverage is sufficient.",
        },
        "totals": counters(report),
        "modules": modules,
    }
    output = root / "target/site/quality-evidence/summary.json"
    output.parent.mkdir(parents=True, exist_ok=True)
    output.write_text(json.dumps(summary, indent=2, sort_keys=True) + "\n", encoding="utf-8")
    print(f"Coverage evidence summary: {output}")


if __name__ == "__main__":
    main()
