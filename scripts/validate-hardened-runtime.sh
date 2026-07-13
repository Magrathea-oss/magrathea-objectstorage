#!/usr/bin/env bash
set -euo pipefail

ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
# Reconcile runtime evidence with the same clean/diagnostic identity as the image.
# shellcheck source=resolve-supply-chain-identity.sh
source "$ROOT/scripts/resolve-supply-chain-identity.sh"
resolve_supply_chain_identity "$ROOT"
OUT="$ROOT/target/supply-chain"
IDENTITY="$OUT/image-identity.json"
FIXTURE="$ROOT/s3-reactive-api-adapter/src/test/resources/fixtures/upload/corruptible-object.bin"
EXPECTED_SHA="178ba39b2e4e92264f35dafbd416ba3c8beb0dc87b395415f068c181d837def0"
RUN_ID="$$"
FIRST="magrathea-ep8-hardened-first-$RUN_ID"
SECOND="magrathea-ep8-hardened-second-$RUN_ID"
WORK="$(mktemp -d "${TMPDIR:-/tmp}/magrathea-ep8-hardening.XXXXXX")"
DATA_VOLUME="magrathea-ep8-hardened-data-$RUN_ID"
ACTIVE=""

cleanup() {
  docker rm -f "$FIRST" "$SECOND" >/dev/null 2>&1 || true
  docker volume rm -f "$DATA_VOLUME" >/dev/null 2>&1 || true
  rm -rf "$WORK"
}
trap cleanup EXIT
fail() { echo "$*" >&2; [[ -z "$ACTIVE" ]] || docker logs "$ACTIVE" >&2 2>/dev/null || true; exit 1; }
# Daemon-level remapping is optional in the portable baseline. Record only what
# Docker reports; container-level host userns mode remains forbidden below.
ENGINE_SECURITY_OPTIONS="$(docker info --format '{{json .SecurityOptions}}')"
if grep -q 'name=userns' <<<"$ENGINE_SECURITY_OPTIONS"; then
  USER_NAMESPACE_REMAPPING="enabled"
else
  USER_NAMESPACE_REMAPPING="unavailable"
fi
[[ -f "$IDENTITY" ]] || fail "Run scripts/build-image-supply-chain.sh first"
python3 - "$IDENTITY" "$SUPPLY_CHAIN_REVISION" "$SUPPLY_CHAIN_VERSION" "$SOURCE_DATE_EPOCH" <<'PY'
import json, sys
identity = json.load(open(sys.argv[1], encoding="utf-8"))
actual = (identity.get("sourceRevision"), identity.get("applicationVersion"), identity.get("sourceDateEpoch"))
expected = (sys.argv[2], sys.argv[3], int(sys.argv[4]))
if actual != expected:
    raise SystemExit(f"Recorded image identity {actual!r} does not match current evidence identity {expected!r}")
PY
IMAGE_ID="$(python3 -c 'import json,sys; print(json.load(open(sys.argv[1]))["id"])' "$IDENTITY")"
[[ "$(docker image inspect --format '{{.Id}}' "$IMAGE_ID")" == "$IMAGE_ID" ]] || fail "Recorded immutable image is unavailable"
[[ "$(sha256sum "$FIXTURE" | cut -d' ' -f1)" == "$EXPECTED_SHA" ]] || fail "Fixture checksum changed"
[[ "$(wc -c < "$FIXTURE" | tr -d ' ')" == 129 ]] || fail "Fixture length changed"
docker volume create "$DATA_VOLUME" >/dev/null
# Prepare only the explicit persistent data volume for the numeric runtime user.
docker run --rm --network none --user 0:0 --entrypoint sh \
  --mount "type=volume,src=$DATA_VOLUME,dst=/var/lib/magrathea" "$IMAGE_ID" \
  -c 'mkdir -p /var/lib/magrathea/storage-engine && chown -R 10001:10001 /var/lib/magrathea'

run_container() {
  local name="$1"
  docker run -d --name "$name" --hostname magrathea-ep8 \
    --user 10001:10001 --read-only --security-opt no-new-privileges=true --cap-drop ALL \
    --network none --ipc private \
    --mount "type=volume,src=$DATA_VOLUME,dst=/var/lib/magrathea" \
    --tmpfs /tmp/magrathea:rw,noexec,nosuid,nodev,uid=10001,gid=10001,mode=0770 \
    -e JAVA_TOOL_OPTIONS=-Djava.io.tmpdir=/tmp/magrathea \
    -e STORAGE_ENGINE_FILESYSTEM_ROOT=/var/lib/magrathea/storage-engine \
    "$IMAGE_ID"
}
wait_ready() {
  local name="$1"
  ACTIVE="$name"
  for _ in $(seq 1 120); do
    if docker exec "$name" wget -qO- "http://127.0.0.1:8081/admin/ready" > "$WORK/ready.json" \
      && grep -q '"status":"ready"' "$WORK/ready.json"; then
      docker exec "$name" wget -qO- "http://127.0.0.1:8081/admin/backend-status" > "$WORK/backend.json"
      grep -q '"selectedBackend":"storage-engine"' "$WORK/backend.json" || fail "Unexpected backend status"
      grep -q '"availability":"available"' "$WORK/backend.json" || fail "Storage root is not available"
      return
    fi
    [[ "$(docker inspect --format '{{.State.Running}}' "$name")" == true ]] || fail "$name exited before readiness"
    sleep 1
  done
  fail "$name did not become ready"
}
assert_hardening() {
  local name="$1"
  [[ "$(docker exec "$name" id -u)" == 10001 ]]
  [[ "$(docker exec "$name" id -g)" == 10001 ]]
  [[ "$(docker inspect --format '{{.HostConfig.ReadonlyRootfs}}' "$name")" == true ]]
  [[ "$(docker inspect --format '{{.HostConfig.Privileged}}' "$name")" == false ]]
  [[ "$(docker inspect --format '{{json .HostConfig.CapDrop}}' "$name")" == '["ALL"]' ]]
  [[ "$(docker inspect --format '{{json .HostConfig.CapAdd}}' "$name")" =~ ^(null|\[\])$ ]]
  [[ "$(docker inspect --format '{{json .HostConfig.SecurityOpt}}' "$name")" == *no-new-privileges* ]]
  [[ "$(docker inspect --format '{{.HostConfig.NetworkMode}}' "$name")" != host ]]
  [[ "$(docker inspect --format '{{.HostConfig.PidMode}}' "$name")" != host ]]
  [[ "$(docker inspect --format '{{.HostConfig.IpcMode}}' "$name")" != host ]]
  [[ "$(docker inspect --format '{{.HostConfig.UTSMode}}' "$name")" != host ]]
  local userns_mode mount_inventory
  userns_mode="$(docker inspect --format '{{.HostConfig.UsernsMode}}' "$name")"
  [[ "$userns_mode" != host ]] || fail "$name explicitly uses forbidden host userns mode"
  mount_inventory="$(docker inspect --format '{{range .Mounts}}{{.Source}}:{{.Destination}} {{end}}' "$name")"
  [[ "$mount_inventory" != *docker.sock* ]]
  [[ "$mount_inventory" == *':/var/lib/magrathea '* ]]
  [[ "$(docker inspect --format '{{json .HostConfig.Tmpfs}}' "$name")" == *'/tmp/magrathea'* ]]
}

run_container "$FIRST" >/dev/null
wait_ready "$FIRST"
assert_hardening "$FIRST"
docker exec "$FIRST" wget -q --method=PUT -O /dev/null \
  "http://127.0.0.1:8080/ep8-hardened-runtime"
docker exec -i "$FIRST" sh -c \
  'cat > /tmp/magrathea/upload.bin && wget -q --server-response --method=PUT --body-file=/tmp/magrathea/upload.bin -O /dev/null http://127.0.0.1:8080/ep8-hardened-runtime/evidence/persistent/corruptible-object.bin' \
  < "$FIXTURE" 2> "$WORK/put.headers"
ETAG="$(awk 'BEGIN{IGNORECASE=1} /etag:/{gsub("\r|\"", "", $2); print $2}' "$WORK/put.headers")"
[[ -n "$ETAG" ]] || fail "PutObject returned no ETag"
docker exec "$FIRST" wget -q --server-response -O- \
  "http://127.0.0.1:8080/ep8-hardened-runtime/evidence/persistent/corruptible-object.bin" \
  > "$WORK/first.bin" 2> "$WORK/get.headers"
cmp "$FIXTURE" "$WORK/first.bin"
[[ "$(sha256sum "$WORK/first.bin" | cut -d' ' -f1)" == "$EXPECTED_SHA" ]]
grep -Eiq 'content-length:[[:space:]]*129([[:space:]]|$)' "$WORK/get.headers" || fail "GetObject content length is not 129"
docker stop --time 45 "$FIRST" >/dev/null
docker rm "$FIRST" >/dev/null
ACTIVE=""

run_container "$SECOND" >/dev/null
wait_ready "$SECOND"
assert_hardening "$SECOND"
docker exec "$SECOND" wget -q --server-response -O- \
  "http://127.0.0.1:8080/ep8-hardened-runtime/evidence/persistent/corruptible-object.bin" \
  > "$WORK/restart.bin" 2> "$WORK/restart.headers"
cmp "$FIXTURE" "$WORK/restart.bin"
grep -Eiq 'content-length:[[:space:]]*129([[:space:]]|$)' "$WORK/restart.headers" || fail "Restart GetObject content length is not 129"
RESTART_ETAG="$(awk 'BEGIN{IGNORECASE=1} /etag:/{gsub("\r|\"", "", $2); print $2}' "$WORK/restart.headers")"
[[ "$RESTART_ETAG" == "$ETAG" ]] || fail "ETag changed after restart"

python3 - "$OUT/hardening-evidence.json" "$IMAGE_ID" "$EXPECTED_SHA" "$ETAG" \
  "$USER_NAMESPACE_REMAPPING" "$ENGINE_SECURITY_OPTIONS" <<'PY'
import json, sys
from pathlib import Path
path, image, digest, etag, userns_remapping, engine_security_options = sys.argv[1:]
evidence = {
  "requirement": "REQ-SUPPLY-005", "status": "passed", "imageId": image,
  "runtime": {"uid": 10001, "gid": 10001, "readOnlyRootFilesystem": True,
    "writableApplicationPaths": ["/var/lib/magrathea", "/tmp/magrathea"],
    "noNewPrivileges": True, "capabilitiesDropped": ["ALL"], "hostNamespaces": [],
    "containerEngineSocketMounted": False,
    "userNamespaceRemapping": userns_remapping,
    "engineSecurityOptions": json.loads(engine_security_options)},
  "admin": {"ready": True, "backend": "storage-engine", "filesystemRootAvailable": True},
  "s3": {"bucket": "ep8-hardened-runtime", "key": "evidence/persistent/corruptible-object.bin",
    "bytes": 129, "sha256": digest, "etag": etag, "exactBytes": True, "restartPersistence": True}
}
Path(path).write_text(json.dumps(evidence, indent=2, sort_keys=True) + "\n")
PY
python3 "$ROOT/scripts/update-supply-chain-manifest.py" --root "$ROOT" \
  --revision "$SUPPLY_CHAIN_REVISION" \
  --version "$SUPPLY_CHAIN_VERSION" \
  --source-date-epoch "$SOURCE_DATE_EPOCH"
printf 'REQ-SUPPLY-005 passed image-id=%s uid=10001 gid=10001 readonly=true no-new-privileges=true cap-drop=ALL userns-remapping=%s\n' \
  "$IMAGE_ID" "$USER_NAMESPACE_REMAPPING"
printf 'admin-ready=true backend=storage-engine fixture-bytes=129 sha256=%s etag=%s restart-persistence=true\n' "$EXPECTED_SHA" "$ETAG"
