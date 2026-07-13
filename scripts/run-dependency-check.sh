#!/usr/bin/env bash
set -uo pipefail

ROOT=$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)
TARGET="$ROOT/target"
LOG="$TARGET/dependency-check-scan.log"
REPORT="$TARGET/dependency-check-report.json"
ANALYSIS="$TARGET/site/dependency-check-analysis.json"
ERROR_ARTIFACT="$TARGET/dependency-check-scan-error.json"

mkdir -p "$TARGET/site"
# A previous successful report must never make a failed update look clean.
rm -f "$TARGET/dependency-check-report.json" \
      "$TARGET/dependency-check-report.html" \
      "$ANALYSIS" "$ERROR_ARTIFACT" "$LOG"

cd "$ROOT"
echo "Dependency-Check data cache: $ROOT/.cache/dependency-check"
if [[ -z "${NVD_API_KEY:-}" ]]; then
  printf '%s\n' \
    'NVD_API_KEY is not set; refusing to report an unauthenticated assessment as complete.' \
    'Dependency-Check vulnerability status is unknown and this monitoring gate fails closed.' \
    | tee "$LOG" >&2
  MAVEN_STATUS=2
else
  set +e
  mvn -Pdependency-check -DskipTests verify "$@" 2>&1 | tee "$LOG"
  MAVEN_STATUS=${PIPESTATUS[0]}
  set -e
fi

ANALYZE_ARGS=(--report "$REPORT" --log "$LOG" --output "$ANALYSIS"
  --suppression-file "$ROOT/config/dependency-check-suppressions.xml")
if [[ $MAVEN_STATUS -ne 0 ]]; then
  ANALYZE_ARGS+=(--command-exit-code "$MAVEN_STATUS")
fi
set +e
python3 "$ROOT/scripts/analyze-dependency-check.py" "${ANALYZE_ARGS[@]}"
ANALYZER_STATUS=$?
set -e

if [[ $MAVEN_STATUS -ne 0 ]]; then
  echo "Dependency-Check Maven verification failed (exit $MAVEN_STATUS). Evidence: $ANALYSIS" >&2
  exit "$MAVEN_STATUS"
fi
if [[ $ANALYZER_STATUS -ne 0 ]]; then
  echo "Dependency-Check evidence is incomplete. Evidence: $ANALYSIS" >&2
  exit "$ANALYZER_STATUS"
fi

echo "Dependency-Check reports: $TARGET/dependency-check-report.{json,html}"
echo "Dependency-Check analysis: $ANALYSIS"
