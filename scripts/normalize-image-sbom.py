#!/usr/bin/env python3
"""Normalize a Syft CycloneDX image SBOM and bind it to an immutable local image ID."""
import argparse
import datetime as dt
import json
from pathlib import Path


def main():
    parser = argparse.ArgumentParser()
    parser.add_argument("--input", type=Path, required=True)
    parser.add_argument("--output", type=Path, required=True)
    parser.add_argument("--image-id", required=True)
    parser.add_argument("--image-reference", required=True)
    parser.add_argument("--revision", required=True)
    parser.add_argument("--version", required=True)
    parser.add_argument("--source-date-epoch", type=int, required=True)
    args = parser.parse_args()
    bom = json.loads(args.input.read_text(encoding="utf-8"))
    stamp = dt.datetime.fromtimestamp(args.source_date_epoch, dt.timezone.utc).strftime("%Y-%m-%dT%H:%M:%SZ")
    bom.pop("serialNumber", None)
    metadata = bom.setdefault("metadata", {})
    metadata["timestamp"] = stamp
    subject = metadata.setdefault("component", {"type": "container"})
    subject.update({"type": "container", "name": args.image_id, "version": args.version, "bom-ref": args.image_id})
    values = {p.get("name"): p.get("value", "") for p in subject.get("properties", [])}
    values.update({
        "magrathea:evidence:image-id": args.image_id,
        "magrathea:evidence:image-reference": args.image_reference,
        "magrathea:evidence:revision": args.revision,
        "magrathea:evidence:application-version": args.version,
        "magrathea:evidence:source-date-epoch": str(args.source_date_epoch),
        "magrathea:evidence:timestamp": stamp,
    })
    subject["properties"] = [{"name": key, "value": values[key]} for key in sorted(values)]
    bom["components"] = sorted(bom.get("components", []), key=lambda c: c.get("bom-ref", c.get("purl", "")))
    bom["dependencies"] = sorted(
        ({"ref": d.get("ref", ""), "dependsOn": sorted(d.get("dependsOn", []))}
         for d in bom.get("dependencies", [])), key=lambda d: d["ref"])
    args.output.write_text(json.dumps(bom, indent=2, sort_keys=True, ensure_ascii=False) + "\n", encoding="utf-8")
    if metadata["component"].get("bom-ref") != args.image_id:
        raise SystemExit("Image subject identity was not retained")
    print(f"Image CycloneDX schema={bom.get('specVersion')} components={len(bom.get('components', []))} subject={args.image_id}")

if __name__ == "__main__":
    main()
