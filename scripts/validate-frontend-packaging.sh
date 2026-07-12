#!/usr/bin/env bash
set -euo pipefail

ROOT=$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)
UI="$ROOT/magrathea-ui"
STATIC="$ROOT/bootstrap-application/src/main/resources/static"
SNAPSHOTS=$(mktemp -d)
export SOURCE_DATE_EPOCH=${SOURCE_DATE_EPOCH:-0}

cleanup_generated() {
  npm run clean --prefix "$UI" >/dev/null
  rm -rf "$STATIC/assets" "$STATIC/docs"
  rm -f "$STATIC/index.html" "$STATIC/favicon.svg" "$STATIC/icons.svg" "$STATIC/_redirects" "$STATIC/product-manifest.json"
}
trap 'cleanup_generated; rm -rf "$SNAPSHOTS"' EXIT

build_once() {
  local pass=$1
  cleanup_generated
  mkdir -p "$STATIC/docs/c4/images" "$STATIC/docs/arc42/images"
  cp "$ROOT"/docs/c4/images/*.png "$STATIC/docs/c4/images/"
  cp -R "$ROOT/docs/arc42/images/." "$STATIC/docs/arc42/images/"
  node "$ROOT/bootstrap-application/src/main/scripts/asciidoc-to-json.mjs"
  node "$ROOT/bootstrap-application/src/main/scripts/asciidoc-to-arc42-json.mjs"
  node "$ROOT/bootstrap-application/src/main/scripts/markdown-to-json.mjs"
  node "$ROOT/bootstrap-application/src/main/scripts/adr-to-json.mjs"
  npm run build:products --prefix "$UI"
  cp -R "$UI/dist/object-storage/." "$STATIC/"

  test -f "$UI/dist/object-storage/product-manifest.json"
  test -f "$UI/dist/magrathea-example/product-manifest.json"
  test -f "$STATIC/index.html"
  test -f "$STATIC/docs/arc42.json"

  {
    cd "$ROOT"
    find magrathea-ui/dist bootstrap-application/src/main/resources/static \
      -type f -print0 | LC_ALL=C sort -z | xargs -0 sha256sum
  } > "$SNAPSHOTS/$pass.sha256"
}

build_once first
build_once second
if ! cmp -s "$SNAPSHOTS/first.sha256" "$SNAPSHOTS/second.sha256"; then
  echo 'Frontend/documentation reproducibility check failed:' >&2
  diff -u "$SNAPSHOTS/first.sha256" "$SNAPSHOTS/second.sha256" >&2 || true
  exit 1
fi

echo 'Reproducible packaging validated: both clean product builds and normalized frontend/documentation asset bytes are identical.'
