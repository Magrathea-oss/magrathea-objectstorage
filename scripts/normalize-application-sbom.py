#!/usr/bin/env python3
"""Normalize and reconcile the canonical aggregate CycloneDX application SBOM."""
import argparse
import datetime as dt
import json
import shutil
import sys
import xml.etree.ElementTree as ET
from pathlib import Path

PRODUCTION_MODULES = {
    "admin-api-adapter", "s3-reactive-api-adapter", "object-store-domain",
    "storage-engine-domain", "storage-engine-reactive-repository-application",
    "storage-engine-reactive-application", "storage-engine-reactive-infrastructure",
    "bootstrap-application", "object-store-reactive-repository-application",
    "object-store-reactive-application", "object-store-reactive-infrastructure",
    "object-store-reactive-repository-storage-engine-infrastructure",
}


def timestamp(epoch: int) -> str:
    return dt.datetime.fromtimestamp(epoch, dt.timezone.utc).strftime("%Y-%m-%dT%H:%M:%SZ")


def properties(revision: str, version: str, epoch: int, stamp: str):
    return {
        "magrathea:evidence:revision": revision,
        "magrathea:evidence:application-version": version,
        "magrathea:evidence:source-date-epoch": str(epoch),
        "magrathea:evidence:timestamp": stamp,
    }


def normalize_json(path: Path, revision: str, version: str, epoch: int, stamp: str):
    bom = json.loads(path.read_text(encoding="utf-8"))
    bom.pop("serialNumber", None)
    metadata = bom.setdefault("metadata", {})
    metadata["timestamp"] = stamp
    component = metadata.setdefault("component", {})
    component["name"] = "magrathea-objectstorage"
    component["version"] = version
    existing = {p.get("name"): p.get("value", "") for p in component.get("properties", [])}
    existing.update(properties(revision, version, epoch, stamp))
    component["properties"] = [{"name": k, "value": existing[k]} for k in sorted(existing)]
    bom["components"] = sorted(bom.get("components", []), key=lambda c: c.get("bom-ref", c.get("purl", "")))
    bom["dependencies"] = sorted(
        ({"ref": d.get("ref", ""), "dependsOn": sorted(d.get("dependsOn", []))}
         for d in bom.get("dependencies", [])), key=lambda d: d["ref"])
    path.write_text(json.dumps(bom, indent=2, sort_keys=True, ensure_ascii=False) + "\n", encoding="utf-8")
    return bom


def normalize_xml(path: Path, revision: str, version: str, epoch: int, stamp: str):
    tree = ET.parse(path)
    root = tree.getroot()
    namespace = root.tag.split("}", 1)[0].lstrip("{")
    ET.register_namespace("", namespace)
    q = lambda name: f"{{{namespace}}}{name}"
    root.attrib.pop("serialNumber", None)
    metadata = root.find(q("metadata"))
    if metadata is None:
        raise ValueError("CycloneDX XML has no metadata")
    ts = metadata.find(q("timestamp"))
    if ts is None:
        ts = ET.Element(q("timestamp"))
        metadata.insert(0, ts)
    ts.text = stamp
    component = metadata.find(q("component"))
    if component is None:
        raise ValueError("CycloneDX XML has no metadata component")
    name = component.find(q("name"))
    ver = component.find(q("version"))
    if name is not None:
        name.text = "magrathea-objectstorage"
    if ver is not None:
        ver.text = version
    props = component.find(q("properties"))
    if props is None:
        props = ET.SubElement(component, q("properties"))
    by_name = {p.attrib.get("name"): p for p in props.findall(q("property"))}
    for key, value in properties(revision, version, epoch, stamp).items():
        element = by_name.get(key)
        if element is None:
            element = ET.SubElement(props, q("property"), {"name": key})
        element.text = value
    props[:] = sorted(props, key=lambda p: p.attrib.get("name", ""))
    ET.indent(tree, space="  ")
    tree.write(path, encoding="utf-8", xml_declaration=True, short_empty_elements=True)
    with path.open("ab") as handle:
        handle.write(b"\n")
    return root, q


def main():
    parser = argparse.ArgumentParser()
    parser.add_argument("--directory", type=Path, required=True)
    parser.add_argument("--site-directory", type=Path, required=True)
    parser.add_argument("--revision", required=True)
    parser.add_argument("--version", required=True)
    parser.add_argument("--source-date-epoch", required=True, type=int)
    args = parser.parse_args()
    json_path = args.directory / "application.cdx.json"
    xml_path = args.directory / "application.cdx.xml"
    stamp = timestamp(args.source_date_epoch)
    bom = normalize_json(json_path, args.revision, args.version, args.source_date_epoch, stamp)
    root, q = normalize_xml(xml_path, args.revision, args.version, args.source_date_epoch, stamp)

    json_refs = {c.get("bom-ref") for c in bom.get("components", []) if c.get("bom-ref")}
    xml_refs = {c.attrib.get("bom-ref") for c in root.findall(f".//{q('components')}/{q('component')}") if c.attrib.get("bom-ref")}
    if json_refs != xml_refs:
        print(f"JSON/XML component mismatch: json-only={sorted(json_refs-xml_refs)} xml-only={sorted(xml_refs-json_refs)}", file=sys.stderr)
        return 1
    json_deps = {(d["ref"], tuple(d.get("dependsOn", []))) for d in bom.get("dependencies", [])}
    xml_deps = {(d.attrib.get("ref", ""), tuple(sorted(x.attrib["ref"] for x in d.findall(q("dependency")))))
                for d in root.findall(f".//{q('dependencies')}/{q('dependency')}")}
    if json_deps != xml_deps:
        print("JSON/XML dependency graph mismatch", file=sys.stderr)
        return 1
    names = {c.get("name") for c in bom.get("components", [])}
    missing = sorted(PRODUCTION_MODULES - names)
    if missing:
        print(f"Aggregate SBOM is missing production reactor modules: {', '.join(missing)}", file=sys.stderr)
        return 1
    forbidden = sorted({"coverage-report"} & names)
    if forbidden:
        print(f"Aggregate SBOM contains excluded modules: {', '.join(forbidden)}", file=sys.stderr)
        return 1

    args.site_directory.mkdir(parents=True, exist_ok=True)
    shutil.copy2(json_path, args.site_directory / json_path.name)
    shutil.copy2(xml_path, args.site_directory / xml_path.name)
    print(f"CycloneDX schema={bom.get('specVersion')} components={len(json_refs)} dependencies={len(json_deps)}")
    print(f"Production reactor modules reconciled={len(PRODUCTION_MODULES)} timestamp={stamp}")
    return 0

if __name__ == "__main__":
    raise SystemExit(main())
