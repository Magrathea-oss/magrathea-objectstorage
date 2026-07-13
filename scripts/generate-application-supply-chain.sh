#!/usr/bin/env bash
set -euo pipefail

ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
# shellcheck source=resolve-supply-chain-identity.sh
source "$ROOT/scripts/resolve-supply-chain-identity.sh"
resolve_supply_chain_identity "$ROOT"
VERSION="$SUPPLY_CHAIN_VERSION"
REVISION="$SUPPLY_CHAIN_REVISION"

mkdir -p "$ROOT/target/supply-chain" "$ROOT/target/site/supply-chain"
# Standalone application generation must not retain image/runtime artifacts from another identity.
rm -f "$ROOT/target/supply-chain/application.cdx.json" \
  "$ROOT/target/supply-chain/application.cdx.xml" \
  "$ROOT/target/supply-chain/license-inventory.json" \
  "$ROOT/target/supply-chain/license-inventory.html" \
  "$ROOT/target/supply-chain/image.raw.cdx.json" \
  "$ROOT/target/supply-chain/image.cdx.json" \
  "$ROOT/target/supply-chain/image-identity.json" \
  "$ROOT/target/supply-chain/hardening-evidence.json" \
  "$ROOT/target/supply-chain/evidence-manifest.json"
(
  cd "$ROOT"
  mvn -B --no-transfer-progress -Psupply-chain -DskipTests \
    -Dproject.build.outputTimestamp="$SOURCE_DATE_EPOCH" package "$@"
)
python3 "$ROOT/scripts/normalize-application-sbom.py" \
  --directory "$ROOT/target/supply-chain" \
  --site-directory "$ROOT/target/site/supply-chain" \
  --revision "$REVISION" --version "$VERSION" --source-date-epoch "$SOURCE_DATE_EPOCH"
python3 "$ROOT/scripts/generate-license-inventory.py" \
  --sbom "$ROOT/target/supply-chain/application.cdx.json" \
  --json-output "$ROOT/target/supply-chain/license-inventory.json" \
  --html-output "$ROOT/target/supply-chain/license-inventory.html" \
  --revision "$REVISION" --version "$VERSION" --source-date-epoch "$SOURCE_DATE_EPOCH"
python3 "$ROOT/scripts/update-supply-chain-manifest.py" \
  --root "$ROOT" --revision "$REVISION" --version "$VERSION" --source-date-epoch "$SOURCE_DATE_EPOCH"
sha256sum "$ROOT/target/supply-chain/application.cdx.json" \
  "$ROOT/target/supply-chain/application.cdx.xml" \
  "$ROOT/target/supply-chain/license-inventory.json"
