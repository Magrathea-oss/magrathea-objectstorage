#!/usr/bin/env bash
# Resolve the one authoritative identity used by EP-8 supply-chain evidence.
# Source this file, then call resolve_supply_chain_identity <repository-root>.

resolve_supply_chain_identity() {
  local root="$1" dirty_status declared_revision declared_version declared_epoch stamp mode acceptance
  local override_name="SUPPLY_CHAIN_ALLOW_DIRTY_NON_ACCEPTANCE_DIAGNOSTICS"

  command -v git >/dev/null || { echo "Git is required to derive supply-chain identity" >&2; return 2; }
  command -v python3 >/dev/null || { echo "Python 3 is required to derive supply-chain identity" >&2; return 2; }
  git -C "$root" rev-parse --is-inside-work-tree >/dev/null 2>&1 || {
    echo "Supply-chain evidence must run from a Git working tree" >&2; return 2;
  }

  declared_revision="$(git -C "$root" rev-parse --verify 'HEAD^{commit}')"
  declared_epoch="$(git -C "$root" show -s --format=%ct "$declared_revision")"
  declared_version="$(python3 - "$root/pom.xml" <<'PY'
import sys
import xml.etree.ElementTree as ET
root = ET.parse(sys.argv[1]).getroot()
ns = {"m": "http://maven.apache.org/POM/4.0.0"}
version = root.findtext("m:version", namespaces=ns)
if not version or "${" in version:
    raise SystemExit("Root pom.xml must declare a concrete application version")
print(version.strip())
PY
)"

  [[ "$declared_revision" =~ ^[0-9a-f]{40}$ ]] || {
    echo "HEAD did not resolve to a full Git revision" >&2; return 2;
  }
  [[ "$declared_epoch" =~ ^[0-9]+$ ]] || {
    echo "HEAD did not provide a commit-associated Unix timestamp" >&2; return 2;
  }

  if [[ -n "${SUPPLY_CHAIN_REVISION:-}" && "$SUPPLY_CHAIN_REVISION" != "$declared_revision" ]]; then
    echo "SUPPLY_CHAIN_REVISION does not match checked-out HEAD ($declared_revision)" >&2; return 2
  fi
  if [[ -n "${SUPPLY_CHAIN_VERSION:-}" && "$SUPPLY_CHAIN_VERSION" != "$declared_version" ]]; then
    echo "SUPPLY_CHAIN_VERSION does not match root Maven version ($declared_version)" >&2; return 2
  fi
  if [[ -n "${SOURCE_DATE_EPOCH:-}" && "$SOURCE_DATE_EPOCH" != "$declared_epoch" ]]; then
    echo "SOURCE_DATE_EPOCH does not match HEAD commit timestamp ($declared_epoch)" >&2; return 2
  fi

  case "${!override_name:-false}" in
    true|false) ;;
    *) echo "$override_name must be exactly true or false" >&2; return 2 ;;
  esac
  dirty_status="$(git -C "$root" status --porcelain --untracked-files=all)"
  if [[ -n "$dirty_status" && "${!override_name:-false}" != true ]]; then
    echo "Refusing supply-chain evidence generation: the working tree is dirty." >&2
    echo "Commit or clean all tracked and untracked content so it cannot be labeled as HEAD." >&2
    echo "For development diagnostics only (never REQ-SUPPLY acceptance), explicitly set $override_name=true." >&2
    return 1
  fi

  if [[ "${!override_name:-false}" == true ]]; then
    mode="development-dirty-non-acceptance-diagnostics"
    acceptance=false
    echo "WARNING: $override_name=true; generated diagnostics are ineligible for REQ-SUPPLY acceptance." >&2
  else
    mode="clean-release-evidence"
    acceptance=true
  fi
  stamp="$(python3 - "$declared_epoch" <<'PY'
import datetime as dt, sys
print(dt.datetime.fromtimestamp(int(sys.argv[1]), dt.timezone.utc).strftime("%Y-%m-%dT%H:%M:%SZ"))
PY
)"

  export SUPPLY_CHAIN_REVISION="$declared_revision"
  export SUPPLY_CHAIN_VERSION="$declared_version"
  export SOURCE_DATE_EPOCH="$declared_epoch"
  export SUPPLY_CHAIN_EVIDENCE_MODE="$mode"
  export SUPPLY_CHAIN_ACCEPTANCE_ELIGIBLE="$acceptance"

  mkdir -p "$root/target/supply-chain"
  python3 - "$root/target/supply-chain/evidence-identity.json" \
    "$declared_revision" "$declared_version" "$declared_epoch" "$stamp" \
    "$mode" "$acceptance" "$override_name" "${!override_name:-false}" "$( [[ -n "$dirty_status" ]] && echo true || echo false )" <<'PY'
import json, sys
from pathlib import Path
path, revision, version, epoch, stamp, mode, acceptance, override_name, override_enabled, dirty = sys.argv[1:]
document = {
    "schema": "magrathea-supply-chain-identity-1.0",
    "application": {"name": "magrathea-objectstorage", "version": version},
    "source": {"revision": revision, "sourceDateEpoch": int(epoch), "timestamp": stamp,
               "dirtyTree": dirty == "true"},
    "evidenceMode": mode,
    "acceptanceEligible": acceptance == "true",
    "developmentOverride": {
        "name": override_name,
        "enabled": override_enabled == "true",
        "constraint": "Non-acceptance diagnostics only; cannot satisfy any REQ-SUPPLY requirement."
    }
}
Path(path).write_text(json.dumps(document, indent=2, sort_keys=True) + "\n", encoding="utf-8")
PY
}
