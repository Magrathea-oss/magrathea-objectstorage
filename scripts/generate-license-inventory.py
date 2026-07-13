#!/usr/bin/env python3
"""Create a conservative SPDX-normalized license inventory from CycloneDX metadata."""
import argparse
import datetime as dt
import hashlib
import html
import json
from pathlib import Path

# Deliberately conservative: only unambiguous, commonly emitted Maven names are mapped.
NAME_TO_SPDX = {
    "Apache License, Version 2.0": "Apache-2.0",
    "Apache License 2.0": "Apache-2.0",
    "The Apache Software License, Version 2.0": "Apache-2.0",
    "BSD-2-Clause": "BSD-2-Clause",
    "BSD-3-Clause": "BSD-3-Clause",
    "Eclipse Public License 1.0": "EPL-1.0",
    "Eclipse Public License 2.0": "EPL-2.0",
    "MIT License": "MIT",
    "Mozilla Public License 2.0": "MPL-2.0",
    "GNU Lesser General Public License v2.1": "LGPL-2.1-only",
}
COPYLEFT_PREFIXES = ("AGPL-", "GPL-", "LGPL-", "EPL-", "MPL-", "CDDL-")


def classify(licenses):
    evidence = []
    for wrapper in licenses or []:
        if "expression" in wrapper:
            evidence.append({"kind": "expression", "value": wrapper["expression"]})
        license_data = wrapper.get("license")
        if license_data:
            if license_data.get("id"):
                evidence.append({"kind": "spdx-id", "value": license_data["id"]})
            elif license_data.get("name"):
                evidence.append({"kind": "name", "value": license_data["name"]})
    unique = {(e["kind"], e["value"]) for e in evidence}
    evidence = [{"kind": k, "value": v} for k, v in sorted(unique)]
    if not evidence:
        return "NOASSERTION", "unknown", evidence
    if len(evidence) != 1:
        return "NOASSERTION", "ambiguous", evidence
    item = evidence[0]
    if item["kind"] in ("spdx-id", "expression"):
        return item["value"], "recognized", evidence
    mapped = NAME_TO_SPDX.get(item["value"])
    if mapped:
        return mapped, "recognized", evidence
    return "NOASSERTION", "unknown", evidence


def main():
    parser = argparse.ArgumentParser()
    parser.add_argument("--sbom", type=Path, required=True)
    parser.add_argument("--json-output", type=Path, required=True)
    parser.add_argument("--html-output", type=Path, required=True)
    parser.add_argument("--revision", required=True)
    parser.add_argument("--version", required=True)
    parser.add_argument("--source-date-epoch", type=int, required=True)
    args = parser.parse_args()
    stamp = dt.datetime.fromtimestamp(args.source_date_epoch, dt.timezone.utc).strftime("%Y-%m-%dT%H:%M:%SZ")
    bom = json.loads(args.sbom.read_text(encoding="utf-8"))
    entries = []
    for component in bom.get("components", []):
        conclusion, status, evidence = classify(component.get("licenses", []))
        expression = conclusion
        entries.append({
            "bomRef": component.get("bom-ref", ""),
            "package": component.get("purl") or f"{component.get('group', '')}:{component.get('name', '')}",
            "name": component.get("name", ""),
            "version": component.get("version", ""),
            "source": "CycloneDX component.licenses",
            "detectedLicenseEvidence": evidence,
            "concludedSpdxExpression": expression,
            "reviewStatus": status,
            "copyleftDetected": expression.startswith(COPYLEFT_PREFIXES),
            "exceptionBearing": " WITH " in expression,
            "manualConclusion": False,
        })
    entries.sort(key=lambda e: (e["package"], e["version"], e["bomRef"]))
    counts = {status: sum(e["reviewStatus"] == status for e in entries)
              for status in ("recognized", "unknown", "ambiguous")}
    report = {
        "schema": "magrathea-license-inventory-1.0",
        "sourceSbom": str(args.sbom),
        "sourceSbomSha256": hashlib.sha256(args.sbom.read_bytes()).hexdigest(),
        "sourceRevision": args.revision,
        "applicationVersion": args.version,
        "sourceDateEpoch": args.source_date_epoch,
        "timestamp": stamp,
        "complianceConclusion": "NOASSERTION",
        "notice": "This inventory is evidence for review; it is not a compliance approval or compatibility decision.",
        "summary": {"components": len(entries), **counts},
        "components": entries,
    }
    args.json_output.parent.mkdir(parents=True, exist_ok=True)
    args.json_output.write_text(json.dumps(report, indent=2, sort_keys=True) + "\n", encoding="utf-8")
    rows = []
    for entry in entries:
        detected = "; ".join(f"{x['kind']}: {x['value']}" for x in entry["detectedLicenseEvidence"]) or "none"
        rows.append("<tr>" + "".join(f"<td>{html.escape(str(value))}</td>" for value in (
            entry["package"], entry["version"], detected, entry["concludedSpdxExpression"], entry["reviewStatus"])) + "</tr>")
    document = f"""<!doctype html>
<html lang=\"en\"><head><meta charset=\"utf-8\"><title>Magrathea license inventory</title>
<style>body{{font-family:sans-serif}}table{{border-collapse:collapse}}th,td{{border:1px solid #999;padding:.35rem;text-align:left}}.warning{{font-weight:bold;color:#8a3b00}}</style></head>
<body><h1>License inventory</h1><p class=\"warning\">No compliance conclusion is asserted. Unknown and ambiguous evidence requires human review.</p>
<dl><dt>Source revision</dt><dd>{html.escape(args.revision)}</dd><dt>Application version</dt><dd>{html.escape(args.version)}</dd><dt>SOURCE_DATE_EPOCH</dt><dd>{args.source_date_epoch}</dd><dt>Evidence timestamp</dt><dd>{stamp}</dd></dl>
<ul><li>Components: {len(entries)}</li><li>Recognized: {counts['recognized']}</li><li>Unknown: {counts['unknown']}</li><li>Ambiguous: {counts['ambiguous']}</li></ul>
<table><thead><tr><th>Package</th><th>Version</th><th>Detected evidence</th><th>SPDX conclusion</th><th>Review</th></tr></thead><tbody>
{''.join(rows)}</tbody></table></body></html>
"""
    args.html_output.write_text(document, encoding="utf-8")
    print(f"License inventory components={len(entries)} recognized={counts['recognized']} unknown={counts['unknown']} ambiguous={counts['ambiguous']}")
    for entry in entries:
        if entry["reviewStatus"] != "recognized":
            print(f"{entry['reviewStatus']}: {entry['package']}@{entry['version']} evidence={entry['detectedLicenseEvidence']}")

if __name__ == "__main__":
    main()
