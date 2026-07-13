#!/usr/bin/env bash
set -euo pipefail

ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
# shellcheck source=resolve-supply-chain-identity.sh
source "$ROOT/scripts/resolve-supply-chain-identity.sh"
resolve_supply_chain_identity "$ROOT"
VERSION="$SUPPLY_CHAIN_VERSION"
REVISION="$SUPPLY_CHAIN_REVISION"
DERIVED_SOURCE_URL="$(git -C "$ROOT" remote get-url origin)"
if [[ -n "${SUPPLY_CHAIN_SOURCE_URL:-}" && "$SUPPLY_CHAIN_SOURCE_URL" != "$DERIVED_SOURCE_URL" ]]; then
  echo "SUPPLY_CHAIN_SOURCE_URL does not match the repository origin" >&2; exit 2
fi
SOURCE_URL="$DERIVED_SOURCE_URL"
IMAGE="${SUPPLY_CHAIN_IMAGE:-magrathea-objectstorage:${VERSION}}"
SCANNER="${SUPPLY_CHAIN_SCANNER_IMAGE:-ghcr.io/anchore/syft:v1.31.0}"
BUILD_NETWORK="${SUPPLY_CHAIN_BUILD_NETWORK:-default}"
OUT="$ROOT/target/supply-chain"
command -v docker >/dev/null || { echo "Docker is required" >&2; exit 2; }
docker info >/dev/null
mkdir -p "$OUT"
rm -f "$OUT/image.raw.cdx.json" "$OUT/image.cdx.json" "$OUT/image-identity.json" \
  "$OUT/hardening-evidence.json" "$OUT/evidence-manifest.json"

cd "$ROOT"
docker build --pull --network "$BUILD_NETWORK" --build-arg "SOURCE_DATE_EPOCH=$SOURCE_DATE_EPOCH" \
  --build-arg "OCI_VERSION=$VERSION" --build-arg "OCI_REVISION=$REVISION" \
  --build-arg "OCI_SOURCE=$SOURCE_URL" -t "$IMAGE" -f Dockerfile .
IMAGE_ID="$(docker image inspect --format '{{.Id}}' "$IMAGE")"
[[ "$IMAGE_ID" == sha256:* ]] || { echo "Docker returned a non-content-addressed image identity: $IMAGE_ID" >&2; exit 1; }
[[ "$(docker image inspect --format '{{index .Config.Labels "org.opencontainers.image.revision"}}' "$IMAGE_ID")" == "$REVISION" ]]
[[ "$(docker image inspect --format '{{.Config.User}}' "$IMAGE_ID")" == "10001:10001" ]]

docker pull "$SCANNER" >/dev/null
SCANNER_ID="$(docker image inspect --format '{{.Id}}' "$SCANNER")"
docker run --rm --network none -e SYFT_CHECK_FOR_APP_UPDATE=false \
  -v /var/run/docker.sock:/var/run/docker.sock:ro -v "$OUT:/out" \
  "$SCANNER_ID" "docker:$IMAGE_ID" -o cyclonedx-json=/out/image.raw.cdx.json
[[ "$(docker image inspect --format '{{.Id}}' "$IMAGE")" == "$IMAGE_ID" ]] || {
  echo "Image tag moved while evidence was generated" >&2; exit 1;
}
python3 "$ROOT/scripts/normalize-image-sbom.py" --input "$OUT/image.raw.cdx.json" \
  --output "$OUT/image.cdx.json" --image-id "$IMAGE_ID" --image-reference "$IMAGE" \
  --revision "$REVISION" --version "$VERSION" --source-date-epoch "$SOURCE_DATE_EPOCH"
rm "$OUT/image.raw.cdx.json"
python3 - "$OUT/image-identity.json" "$IMAGE" "$IMAGE_ID" "$SCANNER" "$SCANNER_ID" \
  "$REVISION" "$VERSION" "$SOURCE_DATE_EPOCH" <<'PY'
import datetime as dt
import json, sys
from pathlib import Path
path, reference, image_id, scanner, scanner_id, revision, version, epoch = sys.argv[1:]
stamp = dt.datetime.fromtimestamp(int(epoch), dt.timezone.utc).strftime("%Y-%m-%dT%H:%M:%SZ")
Path(path).write_text(json.dumps({
    "reference": reference, "id": image_id,
    "sourceRevision": revision, "applicationVersion": version,
    "sourceDateEpoch": int(epoch), "timestamp": stamp,
    "scanner": {"image": scanner, "id": scanner_id, "version": scanner.rsplit(":", 1)[-1]},
    "published": False
}, indent=2, sort_keys=True) + "\n")
PY
python3 "$ROOT/scripts/update-supply-chain-manifest.py" \
  --root "$ROOT" --revision "$REVISION" --version "$VERSION" --source-date-epoch "$SOURCE_DATE_EPOCH"
printf 'image-reference=%s\nimage-id=%s\nscanner=%s\nscanner-image-id=%s\nimage-sbom-sha256=%s\n' \
  "$IMAGE" "$IMAGE_ID" "$SCANNER" "$SCANNER_ID" "$(sha256sum "$OUT/image.cdx.json" | cut -d' ' -f1)"
