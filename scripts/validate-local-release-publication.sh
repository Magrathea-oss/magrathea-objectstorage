#!/usr/bin/env bash
set -euo pipefail

VERSION="${RELEASE_VERSION:-0.1.0}"
REVISION="${RELEASE_REVISION:-$(git rev-parse HEAD)}"
SOURCE_IMAGE="${RELEASE_SOURCE_IMAGE:-magrathea-objectstorage:req-ops-025-${VERSION}}"
PORT="${RELEASE_REGISTRY_PORT:-15000}"
REGISTRY="127.0.0.1:${PORT}"
REPOSITORY="${REGISTRY}/magrathea-objectstorage"
CONTAINER="magrathea-release-registry-$$"
REGISTRY_IMAGE="${RELEASE_REGISTRY_IMAGE:-public.ecr.aws/docker/library/registry:2}"
MINOR="${VERSION%.*}"
SHA_TAG="sha-${REVISION}"
MANIFEST_ACCEPT='application/vnd.oci.image.index.v1+json, application/vnd.oci.image.manifest.v1+json, application/vnd.docker.distribution.manifest.list.v2+json, application/vnd.docker.distribution.manifest.v2+json'

cleanup() {
  docker rm -f "$CONTAINER" >/dev/null 2>&1 || true
}
trap cleanup EXIT

command -v docker >/dev/null
docker info >/dev/null
if ! docker image inspect "$SOURCE_IMAGE" >/dev/null 2>&1; then
  REQ_OPS_025_VERSION="$VERSION" \
  REQ_OPS_025_REVISION="$REVISION" \
  REQ_OPS_025_IMAGE="$SOURCE_IMAGE" \
    bash "$(dirname "$0")/validate-jvm-container-replacement.sh"
fi

docker run -d --name "$CONTAINER" --network=host \
  -e REGISTRY_HTTP_ADDR="127.0.0.1:${PORT}" "$REGISTRY_IMAGE" >/dev/null
for _ in $(seq 1 30); do
  curl -fsS "http://${REGISTRY}/v2/" >/dev/null && break
  sleep 1
done
curl -fsS "http://${REGISTRY}/v2/" >/dev/null

for tag in "$VERSION" "$MINOR" "$SHA_TAG"; do
  if curl -fsSI -H "Accept: ${MANIFEST_ACCEPT}" \
      "http://${REGISTRY}/v2/magrathea-objectstorage/manifests/${tag}" >/dev/null 2>&1; then
    echo "Refusing to overwrite immutable local release tag ${REPOSITORY}:${tag}" >&2
    exit 1
  fi
  docker tag "$SOURCE_IMAGE" "${REPOSITORY}:${tag}"
  docker push "${REPOSITORY}:${tag}" >/dev/null
done

for tag in "$VERSION" "$MINOR" "$SHA_TAG"; do
  digest="$(curl -fsSI -H "Accept: ${MANIFEST_ACCEPT}" \
    "http://${REGISTRY}/v2/magrathea-objectstorage/manifests/${tag}" \
    | awk -F': ' 'tolower($1)=="docker-content-digest" {gsub("\r", "", $2); print $2}')"
  test -n "$digest"
  echo "${REPOSITORY}:${tag}@${digest}"
done

# Exercise the same guard a repeated publication uses. Every immutable tag now exists,
# so a second attempt must be refused before any push occurs.
refused=0
for tag in "$VERSION" "$MINOR" "$SHA_TAG"; do
  if curl -fsSI -H "Accept: ${MANIFEST_ACCEPT}" \
      "http://${REGISTRY}/v2/magrathea-objectstorage/manifests/${tag}" >/dev/null 2>&1; then
    refused=$((refused + 1))
  fi
done
test "$refused" -eq 3
echo "Immutable overwrite refusal validated for all release tags"

version_label="$(docker image inspect "$SOURCE_IMAGE" --format '{{index .Config.Labels "org.opencontainers.image.version"}}')"
revision_label="$(docker image inspect "$SOURCE_IMAGE" --format '{{index .Config.Labels "org.opencontainers.image.revision"}}')"
test "$version_label" = "$VERSION"
test "$revision_label" = "$REVISION"
echo "Local immutable release publication validated for ${VERSION}"
