#!/usr/bin/env python3
"""Write the consolidated EP-8 supply-chain evidence manifest from generated artifacts."""
import argparse
import datetime as dt
import hashlib
import json
import subprocess
import xml.etree.ElementTree as ET
from pathlib import Path


def sha(path: Path) -> str:
    return hashlib.sha256(path.read_bytes()).hexdigest()


def main():
    parser = argparse.ArgumentParser()
    parser.add_argument("--root", type=Path, required=True)
    parser.add_argument("--revision", required=True)
    parser.add_argument("--version", required=True)
    parser.add_argument("--source-date-epoch", required=True, type=int)
    args = parser.parse_args()
    root = args.root.resolve()
    out = root / "target/supply-chain"
    out.mkdir(parents=True, exist_ok=True)
    stamp = dt.datetime.fromtimestamp(args.source_date_epoch, dt.timezone.utc).strftime("%Y-%m-%dT%H:%M:%SZ")
    identity_path = out / "evidence-identity.json"
    if not identity_path.exists():
        raise SystemExit("Missing authoritative evidence-identity.json")
    evidence_identity = json.loads(identity_path.read_text(encoding="utf-8"))
    expected_identity = (args.revision, args.version, args.source_date_epoch, stamp)
    recorded_identity = (
        evidence_identity.get("source", {}).get("revision"),
        evidence_identity.get("application", {}).get("version"),
        evidence_identity.get("source", {}).get("sourceDateEpoch"),
        evidence_identity.get("source", {}).get("timestamp"),
    )
    if recorded_identity != expected_identity:
        raise SystemExit(f"Evidence identity mismatch: recorded={recorded_identity!r} expected={expected_identity!r}")
    dirty = bool(subprocess.run(
        ["git", "-C", str(root), "status", "--porcelain"], check=True,
        capture_output=True, text=True).stdout.strip())
    if dirty != evidence_identity.get("source", {}).get("dirtyTree"):
        raise SystemExit("Working-tree state changed after supply-chain identity was recorded")
    if dirty and evidence_identity.get("acceptanceEligible"):
        raise SystemExit("Dirty diagnostics cannot be marked acceptance eligible")

    def require_properties(properties, artifact):
        values = {item.get("name"): item.get("value") for item in properties}
        expected = {
            "magrathea:evidence:revision": args.revision,
            "magrathea:evidence:application-version": args.version,
            "magrathea:evidence:source-date-epoch": str(args.source_date_epoch),
            "magrathea:evidence:timestamp": stamp,
        }
        if any(values.get(key) != value for key, value in expected.items()):
            raise SystemExit(f"Identity metadata mismatch in {artifact}")

    application_json = out / "application.cdx.json"
    if application_json.exists():
        bom = json.loads(application_json.read_text(encoding="utf-8"))
        if bom.get("metadata", {}).get("timestamp") != stamp:
            raise SystemExit("Timestamp mismatch in application.cdx.json")
        require_properties(bom.get("metadata", {}).get("component", {}).get("properties", []), application_json.name)
    application_xml = out / "application.cdx.xml"
    if application_xml.exists():
        xml_root = ET.parse(application_xml).getroot()
        namespace = xml_root.tag.split("}", 1)[0].lstrip("{")
        q = lambda name: f"{{{namespace}}}{name}"
        metadata = xml_root.find(q("metadata"))
        component = metadata.find(q("component")) if metadata is not None else None
        xml_properties = [{"name": p.attrib.get("name"), "value": p.text}
                          for p in component.findall(f"{q('properties')}/{q('property')}")] if component is not None else []
        if metadata is None or metadata.findtext(q("timestamp")) != stamp:
            raise SystemExit("Timestamp mismatch in application.cdx.xml")
        require_properties(xml_properties, application_xml.name)
    image_json = out / "image.cdx.json"
    if image_json.exists():
        bom = json.loads(image_json.read_text(encoding="utf-8"))
        if bom.get("metadata", {}).get("timestamp") != stamp:
            raise SystemExit("Timestamp mismatch in image.cdx.json")
        require_properties(bom.get("metadata", {}).get("component", {}).get("properties", []), image_json.name)
    license_json = out / "license-inventory.json"
    if license_json.exists():
        license_identity = json.loads(license_json.read_text(encoding="utf-8"))
        actual = (license_identity.get("sourceRevision"), license_identity.get("applicationVersion"),
                  license_identity.get("sourceDateEpoch"), license_identity.get("timestamp"))
        if actual != expected_identity:
            raise SystemExit(f"Identity metadata mismatch in {license_json.name}")

    candidates = [
        identity_path, out / "application.cdx.json", out / "application.cdx.xml", out / "image.cdx.json",
        out / "license-inventory.json", out / "license-inventory.html", out / "image-identity.json",
        out / "hardening-evidence.json", root / "target/dependency-check-report.json",
        root / "target/dependency-check-report.html", root / "target/site/dependency-check-analysis.json",
    ]
    artifacts = []
    for path in candidates:
        if path.exists():
            artifacts.append({"path": str(path.relative_to(root)), "sha256": sha(path), "bytes": path.stat().st_size})
    image_identity_path = out / "image-identity.json"
    identity = json.loads(image_identity_path.read_text()) if image_identity_path.exists() else None
    if identity:
        actual = (identity.get("sourceRevision"), identity.get("applicationVersion"),
                  identity.get("sourceDateEpoch"), identity.get("timestamp"))
        if actual != expected_identity:
            raise SystemExit(f"Identity metadata mismatch in {image_identity_path.name}")
        image_id = identity.get("id")
        if not isinstance(image_id, str) or not image_id.startswith("sha256:"):
            raise SystemExit("Image identity is not an immutable content-addressed ID")
        if image_json.exists():
            subject = json.loads(image_json.read_text(encoding="utf-8")).get("metadata", {}).get("component", {})
            if subject.get("bom-ref") != image_id:
                raise SystemExit("Image SBOM subject does not match image-identity.json")
        hardening_path = out / "hardening-evidence.json"
        if hardening_path.exists() and json.loads(hardening_path.read_text()).get("imageId") != image_id:
            raise SystemExit("Hardened-runtime evidence does not match image-identity.json")
    analysis_path = root / "target/site/dependency-check-analysis.json"
    owasp = {"status": "not-run", "failClosed": True, "analysis": "target/site/dependency-check-analysis.json"}
    if analysis_path.exists():
        analysis = json.loads(analysis_path.read_text())
        owasp.update({
            "status": analysis.get("scanStatus", "unknown"),
            "sourceRevision": analysis.get("sourceRevision"),
            "currentRevision": analysis.get("sourceRevision") == args.revision,
            "unsuppressedFindings": analysis.get("unsuppressedFindings", {}).get("count"),
            "suppressedFindings": analysis.get("suppressedFindings", {}).get("count"),
            "scanErrors": analysis.get("scanErrors", {}).get("count"),
            "commandExitCode": analysis.get("commandExitCode"),
        })
    config_files = [root / "pom.xml", root / "config/dependency-check-suppressions.xml"]
    manifest = {
        "schema": "magrathea-supply-chain-evidence-1.0",
        "application": evidence_identity["application"],
        "source": evidence_identity["source"],
        "evidenceMode": evidence_identity["evidenceMode"],
        "acceptanceEligible": evidence_identity["acceptanceEligible"],
        "developmentOverride": evidence_identity["developmentOverride"],
        "tools": {
            "cyclonedxMavenPlugin": "2.9.1",
            "imageScanner": identity.get("scanner", None) if identity else None,
            "owaspDependencyCheckMaven": "12.2.2",
        },
        "image": identity,
        "owaspDependencyCheck": owasp,
        "configuration": [{"path": str(p.relative_to(root)), "sha256": sha(p)} for p in config_files],
        "artifacts": sorted(artifacts, key=lambda item: item["path"]),
        "limitations": [
            "The license inventory asserts no compliance or compatibility conclusion.",
            "OWASP status is not current unless currentRevision is true and scan status is complete.",
            "No artifact or image publication is performed by this evidence workflow.",
        ],
    }
    target = out / "evidence-manifest.json"
    target.write_text(json.dumps(manifest, indent=2, sort_keys=True) + "\n", encoding="utf-8")
    site = root / "target/site/supply-chain"
    site.mkdir(parents=True, exist_ok=True)
    for file in out.iterdir():
        if file.is_file():
            (site / file.name).write_bytes(file.read_bytes())
    print(f"Evidence manifest artifacts={len(artifacts)} owasp-status={owasp['status']} owasp-current={owasp.get('currentRevision', False)}")

if __name__ == "__main__":
    main()
