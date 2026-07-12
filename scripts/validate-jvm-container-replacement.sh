#!/usr/bin/env bash
set -euo pipefail

ROOT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
VERSION="${REQ_OPS_025_VERSION:-0.1.0}"
REVISION="${REQ_OPS_025_REVISION:-$(git -C "$ROOT_DIR" rev-parse HEAD)}"
SOURCE_URL="${REQ_OPS_025_SOURCE_URL:-}"
IMAGE="${REQ_OPS_025_IMAGE:-magrathea-objectstorage:req-ops-025-${VERSION}}"
NETWORK_MODE="${REQ_OPS_025_NETWORK_MODE:-host}"
S3_PORT="${REQ_OPS_025_S3_PORT:-8080}"
ADMIN_PORT="${REQ_OPS_025_ADMIN_PORT:-8081}"
BUCKET="${REQ_OPS_025_BUCKET:-ep5-release-volume-bucket}"
OBJECT_KEY="${REQ_OPS_025_OBJECT_KEY:-release/persistent.txt}"
OBJECT_BODY="${REQ_OPS_025_OBJECT_BODY:-survives container replacement}"
RUN_ID="${REQ_OPS_025_RUN_ID:-$$}"
VOLUME="magrathea-req-ops-025-${RUN_ID}"
FIRST_CONTAINER="magrathea-req-ops-025-first-${RUN_ID}"
SECOND_CONTAINER="magrathea-req-ops-025-second-${RUN_ID}"
WORK_DIR="$(mktemp -d "${TMPDIR:-/tmp}/magrathea-req-ops-025.XXXXXX")"
ACTIVE_CONTAINER=""

normalize_source_url() {
  local url="$1"
  url="${url%.git}"
  if [[ "$url" =~ ^git@github.com:(.+)$ ]]; then
    printf 'https://github.com/%s\n' "${BASH_REMATCH[1]}"
  else
    printf '%s\n' "$url"
  fi
}

if [[ -z "$SOURCE_URL" ]]; then
  SOURCE_URL="$(normalize_source_url "$(git -C "$ROOT_DIR" remote get-url origin)")"
fi

cleanup() {
  docker rm -f "$FIRST_CONTAINER" "$SECOND_CONTAINER" >/dev/null 2>&1 || true
  docker volume rm -f "$VOLUME" >/dev/null 2>&1 || true
  rm -rf "$WORK_DIR"
}
trap cleanup EXIT

fail_with_logs() {
  local message="$1"
  echo "$message" >&2
  if [[ -n "$ACTIVE_CONTAINER" ]]; then
    docker logs "$ACTIVE_CONTAINER" >&2 2>/dev/null || true
  fi
  exit 1
}

wait_ready() {
  local container="$1"
  ACTIVE_CONTAINER="$container"
  for attempt in $(seq 1 90); do
    if curl -fsS "http://127.0.0.1:${ADMIN_PORT}/admin/ready" >"$WORK_DIR/ready.json" \
        && grep -q '"status":"ready"' "$WORK_DIR/ready.json"; then
      curl -fsS "http://127.0.0.1:${ADMIN_PORT}/admin/live" >"$WORK_DIR/live.json"
      return
    fi
    if [[ "$(docker inspect --format '{{.State.Running}}' "$container" 2>/dev/null || true)" != "true" ]]; then
      fail_with_logs "Container $container exited before readiness"
    fi
    if [[ "$attempt" = 90 ]]; then
      fail_with_logs "Container $container did not become ready within 90 seconds"
    fi
    sleep 1
  done
}

stop_with_sigterm() {
  local container="$1"
  docker kill --signal=TERM "$container" >/dev/null
  for attempt in $(seq 1 45); do
    if [[ "$(docker inspect --format '{{.State.Running}}' "$container")" = "false" ]]; then
      local exit_code
      exit_code="$(docker inspect --format '{{.State.ExitCode}}' "$container")"
      [[ "$exit_code" = "143" ]] || fail_with_logs "SIGTERM exit code was $exit_code, expected 143"
      return
    fi
    if [[ "$attempt" = 45 ]]; then
      fail_with_logs "Container $container did not stop after SIGTERM; refusing to use SIGKILL as validation"
    fi
    sleep 1
  done
}

command -v docker >/dev/null 2>&1 || { echo "Docker is required" >&2; exit 1; }
command -v curl >/dev/null 2>&1 || { echo "curl is required" >&2; exit 1; }
docker info >/dev/null 2>&1 || { echo "Docker daemon is unavailable" >&2; exit 1; }
[[ "$VERSION" =~ ^[0-9]+\.[0-9]+\.[0-9]+$ ]] || { echo "Version must be exact MAJOR.MINOR.PATCH: $VERSION" >&2; exit 1; }
[[ "$REVISION" =~ ^[0-9a-f]{40}$ ]] || { echo "Expected source revision must be a full Git SHA: $REVISION" >&2; exit 1; }

cd "$ROOT_DIR"
docker rm -f "$FIRST_CONTAINER" "$SECOND_CONTAINER" >/dev/null 2>&1 || true
docker volume rm -f "$VOLUME" >/dev/null 2>&1 || true

docker build --network="$NETWORK_MODE" -f Dockerfile \
  --build-arg "OCI_VERSION=$VERSION" \
  --build-arg "OCI_REVISION=$REVISION" \
  --build-arg "OCI_SOURCE=$SOURCE_URL" \
  -t "$IMAGE" .

IMAGE_ID="$(docker image inspect --format '{{.Id}}' "$IMAGE")"
[[ "$(docker image inspect --format '{{index .Config.Labels "org.opencontainers.image.version"}}' "$IMAGE")" = "$VERSION" ]]
[[ "$(docker image inspect --format '{{index .Config.Labels "org.opencontainers.image.revision"}}' "$IMAGE")" = "$REVISION" ]]
[[ "$(docker image inspect --format '{{index .Config.Labels "org.opencontainers.image.source"}}' "$IMAGE")" = "$SOURCE_URL" ]]
[[ "$(docker image inspect --format '{{.Config.User}}' "$IMAGE")" = "magrathea" ]]

docker volume create "$VOLUME" >/dev/null
docker run -d --name "$FIRST_CONTAINER" --network="$NETWORK_MODE" \
  -v "$VOLUME:/app/data" "$IMAGE" >/dev/null
wait_ready "$FIRST_CONTAINER"
[[ "$(docker exec "$FIRST_CONTAINER" id -u)" != "0" ]]
[[ "$(docker inspect --format '{{range .Mounts}}{{if eq .Destination "/app/data"}}{{.Type}}:{{.Name}}{{end}}{{end}}' "$FIRST_CONTAINER")" = "volume:$VOLUME" ]]

curl -fsS -X PUT "http://127.0.0.1:${S3_PORT}/${BUCKET}" >"$WORK_DIR/put-bucket.out"
printf '%s' "$OBJECT_BODY" | curl -fsS -X PUT --data-binary @- \
  "http://127.0.0.1:${S3_PORT}/${BUCKET}/${OBJECT_KEY}" >"$WORK_DIR/put-object.out"
FIRST_ID="$(docker inspect --format '{{.Id}}' "$FIRST_CONTAINER")"
stop_with_sigterm "$FIRST_CONTAINER"
docker rm "$FIRST_CONTAINER" >/dev/null
ACTIVE_CONTAINER=""

docker run -d --name "$SECOND_CONTAINER" --network="$NETWORK_MODE" \
  -v "$VOLUME:/app/data" "$IMAGE" >/dev/null
wait_ready "$SECOND_CONTAINER"
SECOND_ID="$(docker inspect --format '{{.Id}}' "$SECOND_CONTAINER")"
[[ "$SECOND_ID" != "$FIRST_ID" ]]
[[ "$(docker inspect --format '{{.Image}}' "$SECOND_CONTAINER")" = "$IMAGE_ID" ]]
[[ "$(docker inspect --format '{{range .Mounts}}{{if eq .Destination "/app/data"}}{{.Type}}:{{.Name}}{{end}}{{end}}' "$SECOND_CONTAINER")" = "volume:$VOLUME" ]]
printf '%s' "$OBJECT_BODY" >"$WORK_DIR/expected.bin"
curl -fsS "http://127.0.0.1:${S3_PORT}/${BUCKET}/${OBJECT_KEY}" >"$WORK_DIR/actual.bin"
cmp "$WORK_DIR/expected.bin" "$WORK_DIR/actual.bin"

printf 'REQ-OPS-025 validated\n'
printf 'runtime-user=non-root\n'
printf 'persistent-volume=%s\n' "$VOLUME"
printf 'shutdown-signal=SIGTERM exit-code=143\n'
printf 'container-recreated=true image-id=%s\n' "$IMAGE_ID"
printf 'admin-live=healthy admin-ready=ready\n'
printf 's3-object=%s/%s exact-bytes=%s\n' "$BUCKET" "$OBJECT_KEY" "$(wc -c <"$WORK_DIR/actual.bin" | tr -d ' ')"
printf 'image-version=%s\n' "$VERSION"
printf 'image-revision=%s\n' "$REVISION"
printf 'image-source=%s\n' "$SOURCE_URL"
