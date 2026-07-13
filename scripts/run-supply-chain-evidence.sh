#!/usr/bin/env bash
set -euo pipefail

ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
# Fail before invoking any producer, so a dirty full run cannot publish partial HEAD-labeled evidence.
# shellcheck source=resolve-supply-chain-identity.sh
source "$ROOT/scripts/resolve-supply-chain-identity.sh"
resolve_supply_chain_identity "$ROOT"
REVISION="$SUPPLY_CHAIN_REVISION"
VERSION="$SUPPLY_CHAIN_VERSION"

rm -rf "$ROOT/target/supply-chain" "$ROOT/target/site/supply-chain"
resolve_supply_chain_identity "$ROOT"

"$ROOT/scripts/generate-application-supply-chain.sh" "$@"
if [[ "${SUPPLY_CHAIN_SKIP_IMAGE:-false}" != true ]]; then
  "$ROOT/scripts/build-image-supply-chain.sh"
fi
if [[ "${SUPPLY_CHAIN_SKIP_RUNTIME:-false}" != true ]]; then
  "$ROOT/scripts/validate-hardened-runtime.sh"
fi

OWASP_STATUS=0
if [[ "${SUPPLY_CHAIN_SKIP_OWASP:-false}" != true ]]; then
  set +e
  "$ROOT/scripts/run-dependency-check.sh"
  OWASP_STATUS=$?
  set -e
fi
# Always consolidate the OWASP complete/error evidence, even when its fail-closed gate failed.
python3 "$ROOT/scripts/update-supply-chain-manifest.py" --root "$ROOT" \
  --revision "$REVISION" --version "$VERSION" --source-date-epoch "$SOURCE_DATE_EPOCH"
if (( OWASP_STATUS != 0 )); then
  echo "Supply-chain evidence failed closed because OWASP Dependency-Check exited $OWASP_STATUS" >&2
  exit "$OWASP_STATUS"
fi
printf 'Supply-chain evidence complete: %s\n' "$ROOT/target/supply-chain/evidence-manifest.json"
