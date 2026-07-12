#!/usr/bin/env bash
set -euo pipefail

PROMETHEUS_IMAGE="${PROMETHEUS_IMAGE:-quay.io/prometheus/prometheus:v3.5.0}"
ALERTMANAGER_IMAGE="${ALERTMANAGER_IMAGE:-quay.io/prometheus/alertmanager:v0.28.1}"
PROMETHEUS_PORT="${PROMETHEUS_PORT:-19090}"
ALERTMANAGER_PORT="${ALERTMANAGER_PORT:-19093}"
RECEIVER_PORT="${RECEIVER_PORT:-19094}"
ROOT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
WORK_DIR="$(mktemp -d "${TMPDIR:-/tmp}/magrathea-live-alert.XXXXXX")"
RUN_ID="$$"
PROM_CONTAINER="magrathea-prometheus-${RUN_ID}"
AM_CONTAINER="magrathea-alertmanager-${RUN_ID}"
RECEIVER_PID=""

cleanup() {
  docker rm -f "$PROM_CONTAINER" "$AM_CONTAINER" >/dev/null 2>&1 || true
  if [[ -n "$RECEIVER_PID" ]]; then
    kill "$RECEIVER_PID" >/dev/null 2>&1 || true
    wait "$RECEIVER_PID" 2>/dev/null || true
  fi
  rm -rf "$WORK_DIR"
}
trap cleanup EXIT

command -v docker >/dev/null 2>&1 || { echo "Docker is required" >&2; exit 1; }
docker info >/dev/null 2>&1 || { echo "Docker daemon is unavailable" >&2; exit 1; }

cat >"$WORK_DIR/receiver.py" <<'PY'
import json
import os
from http.server import BaseHTTPRequestHandler, ThreadingHTTPServer

output = os.environ["ALERT_OUTPUT"]
class Handler(BaseHTTPRequestHandler):
    def do_GET(self):
        if self.path == "/metrics":
            body = b'# TYPE probe_success gauge\nprobe_success{job="magrathea-admin-live"} 0\n'
            self.send_response(200)
            self.send_header("Content-Type", "text/plain; version=0.0.4")
            self.send_header("Content-Length", str(len(body)))
            self.end_headers()
            self.wfile.write(body)
        else:
            self.send_response(404)
            self.end_headers()
    def do_POST(self):
        length = int(self.headers.get("Content-Length", "0"))
        payload = self.rfile.read(length)
        with open(output, "ab") as stream:
            stream.write(payload + b"\n")
        self.send_response(200)
        self.end_headers()
    def log_message(self, fmt, *args):
        pass

ThreadingHTTPServer(("127.0.0.1", int(os.environ["RECEIVER_PORT"])), Handler).serve_forever()
PY

cat >"$WORK_DIR/alertmanager.yml" <<EOF
route:
  receiver: operator-webhook
  group_wait: 0s
  group_interval: 1s
  repeat_interval: 1m
receivers:
  - name: operator-webhook
    webhook_configs:
      - url: http://127.0.0.1:${RECEIVER_PORT}/alerts
        send_resolved: true
EOF

# Exercise the shipped rule pack itself. Only evaluation/hold durations are shortened
# in this test copy; alert names, expressions, labels, annotations, and runbook links
# remain byte-derived from the released configuration.
sed -E \
  -e 's/interval: 30s/interval: 1s/' \
  -e 's/for: [0-9]+[smh]/for: 0s/' \
  "$ROOT_DIR/ops/prometheus/magrathea-objectstorage-alerts.yml" \
  >"$WORK_DIR/delivery-rules.yml"

cat >"$WORK_DIR/prometheus.yml" <<EOF
global:
  scrape_interval: 1s
  evaluation_interval: 1s
alerting:
  alertmanagers:
    - static_configs:
        - targets: ["127.0.0.1:${ALERTMANAGER_PORT}"]
rule_files:
  - /etc/prometheus/delivery-rules.yml
scrape_configs:
  - job_name: live-alert-fixture
    honor_labels: true
    static_configs:
      - targets: ["127.0.0.1:${RECEIVER_PORT}"]
EOF

touch "$WORK_DIR/received-alerts.jsonl"
ALERT_OUTPUT="$WORK_DIR/received-alerts.jsonl" RECEIVER_PORT="$RECEIVER_PORT" \
  python3 "$WORK_DIR/receiver.py" &
RECEIVER_PID=$!

# Validate the exact shipped rules with the same Prometheus version used for delivery.
docker run --rm --network=host --entrypoint=/bin/promtool \
  -v "$ROOT_DIR/ops/prometheus:/rules:ro" \
  "$PROMETHEUS_IMAGE" check rules /rules/magrathea-objectstorage-alerts.yml

docker run -d --name "$AM_CONTAINER" --network=host \
  -v "$WORK_DIR/alertmanager.yml:/etc/alertmanager/alertmanager.yml:ro" \
  "$ALERTMANAGER_IMAGE" \
  --config.file=/etc/alertmanager/alertmanager.yml \
  --web.listen-address="127.0.0.1:${ALERTMANAGER_PORT}" >/dev/null

docker run -d --name "$PROM_CONTAINER" --network=host \
  -v "$WORK_DIR/prometheus.yml:/etc/prometheus/prometheus.yml:ro" \
  -v "$WORK_DIR/delivery-rules.yml:/etc/prometheus/delivery-rules.yml:ro" \
  "$PROMETHEUS_IMAGE" \
  --config.file=/etc/prometheus/prometheus.yml \
  --web.listen-address="127.0.0.1:${PROMETHEUS_PORT}" >/dev/null

for _ in $(seq 1 60); do
  if grep -q 'MagratheaAdminLivenessProbeDown' "$WORK_DIR/received-alerts.jsonl"; then
    echo "Live alert delivery validated: Prometheus -> Alertmanager -> operator webhook"
    exit 0
  fi
  if ! docker inspect -f '{{.State.Running}}' "$PROM_CONTAINER" 2>/dev/null | grep -q true; then
    docker logs "$PROM_CONTAINER" >&2 || true
    exit 1
  fi
  if ! docker inspect -f '{{.State.Running}}' "$AM_CONTAINER" 2>/dev/null | grep -q true; then
    docker logs "$AM_CONTAINER" >&2 || true
    exit 1
  fi
  sleep 1
done

echo "Timed out waiting for live alert delivery" >&2
docker logs "$PROM_CONTAINER" >&2 || true
docker logs "$AM_CONTAINER" >&2 || true
exit 1
