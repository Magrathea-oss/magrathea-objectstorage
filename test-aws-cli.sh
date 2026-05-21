#!/usr/bin/env bash
set -u

# AWS CLI compatibility test for Magrathea ObjectStorage.
# Scope: every S3 operation currently implemented by s3-api.
# Requires: AWS CLI installed and Magrathea running (default: http://localhost:8080).
# Also writes docs/test-report.md with AWS CLI, Surefire, and Clover coverage summaries.

ENDPOINT="${ENDPOINT_URL:-http://localhost:8080}"
BUCKET="magrathea-cli-test-$(date +%s)-$$"
KEY="hello.txt"
OUTDIR="${OUTDIR:-/tmp/magrathea-s3-cli-test}"
BODY_FILE="$OUTDIR/input.txt"
OUTPUT_FILE="$OUTDIR/output.txt"
DOCS_DIR="${DOCS_DIR:-docs}"
REPORT="$DOCS_DIR/test-report.md"

export AWS_ACCESS_KEY_ID="${AWS_ACCESS_KEY_ID:-test}"
export AWS_SECRET_ACCESS_KEY="${AWS_SECRET_ACCESS_KEY:-test}"
export AWS_DEFAULT_REGION="${AWS_DEFAULT_REGION:-us-east-1}"
export AWS_PAGER=""

mkdir -p "$OUTDIR" "$DOCS_DIR"
printf 'Hello, Magrathea!\n' > "$BODY_FILE"
rm -f "$OUTPUT_FILE"

PASS=0
FAIL=0
TOTAL=0
FAILED_TESTS=()
TEST_ROWS=""

aws_s3api() {
    aws --endpoint-url="$ENDPOINT" s3api "$@"
}

add_result() {
    local name="$1"
    local status="$2"
    local note="$3"
    TEST_ROWS+="| $name | $status | $note |"$'\n'
}

run_success() {
    local name="$1"
    shift
    TOTAL=$((TOTAL + 1))
    echo ""
    echo "--- $TOTAL: $name ---"
    if "$@"; then
        echo "  ✅ $name"
        PASS=$((PASS + 1))
        add_result "$name" "✅ Passed" "Expected success"
    else
        local code=$?
        echo "  ❌ $name (exit code: $code)"
        FAIL=$((FAIL + 1))
        FAILED_TESTS+=("$name")
        add_result "$name" "❌ Failed" "Expected success, exit code $code"
    fi
}

run_failure() {
    local name="$1"
    shift
    TOTAL=$((TOTAL + 1))
    echo ""
    echo "--- $TOTAL: $name ---"
    if "$@"; then
        echo "  ❌ $name (unexpected success)"
        FAIL=$((FAIL + 1))
        FAILED_TESTS+=("$name")
        add_result "$name" "❌ Failed" "Expected failure, command succeeded"
    else
        echo "  ✅ $name"
        PASS=$((PASS + 1))
        add_result "$name" "✅ Passed" "Expected failure"
    fi
}

cleanup() {
    aws_s3api delete-object --bucket "$BUCKET" --key "$KEY" >/dev/null 2>&1 || true
    aws_s3api delete-bucket --bucket "$BUCKET" >/dev/null 2>&1 || true
}
trap cleanup EXIT

# Implemented S3 operations in s3-api:
# ListBuckets, CreateBucket, HeadBucket, ListObjects, PutObject, GetObject,
# HeadObject, DeleteObject, DeleteBucket.

echo "=============================================="
echo "Magrathea ObjectStorage — AWS CLI S3 Test Suite"
echo "Endpoint: $ENDPOINT"
echo "Bucket:   $BUCKET"
echo "=============================================="

if ! command -v aws >/dev/null 2>&1; then
    echo "AWS CLI is not installed or not in PATH."
    exit 2
fi

run_success "ListBuckets" aws_s3api list-buckets --output json
run_success "CreateBucket" aws_s3api create-bucket --bucket "$BUCKET" --output json
run_success "HeadBucket existing" aws_s3api head-bucket --bucket "$BUCKET"
run_success "PutObject" aws_s3api put-object --bucket "$BUCKET" --key "$KEY" --body "$BODY_FILE" --content-type text/plain --output json
run_success "HeadObject existing" aws_s3api head-object --bucket "$BUCKET" --key "$KEY" --output json
run_success "GetObject" aws_s3api get-object --bucket "$BUCKET" --key "$KEY" "$OUTPUT_FILE" --output json
run_success "GetObject content matches" cmp -s "$BODY_FILE" "$OUTPUT_FILE"
run_success "ListObjects" aws_s3api list-objects --bucket "$BUCKET" --output json
run_success "DeleteObject" aws_s3api delete-object --bucket "$BUCKET" --key "$KEY" --output json
run_failure "HeadObject after DeleteObject" aws_s3api head-object --bucket "$BUCKET" --key "$KEY" --output json
run_success "DeleteBucket" aws_s3api delete-bucket --bucket "$BUCKET" --output json
run_failure "HeadBucket after DeleteBucket" aws_s3api head-bucket --bucket "$BUCKET"

SUREFIRE_SUMMARY=$(python3 <<'PY'
from pathlib import Path
import re
rows=[]
totals=[0,0,0,0]
for p in sorted(Path('.').glob('*/target/surefire-reports/*.txt')):
    text=p.read_text(errors='ignore')
    m=re.search(r'Tests run: (\d+), Failures: (\d+), Errors: (\d+), Skipped: (\d+)', text)
    if not m:
        continue
    vals=list(map(int,m.groups()))
    totals=[a+b for a,b in zip(totals,vals)]
    module=p.parts[0]
    status='✅ Passed' if vals[1]==0 and vals[2]==0 else '❌ Failed'
    rows.append(f'| {module} | {p.name} | {vals[0]} | {vals[1]} | {vals[2]} | {vals[3]} | {status} |')
print('| Module | Report | Tests | Failures | Errors | Skipped | Status |')
print('|---|---|---:|---:|---:|---:|---|')
if rows:
    print('\n'.join(rows))
    status='✅ Passed' if totals[1]==0 and totals[2]==0 else '❌ Failed'
    print(f'| **Total** |  | **{totals[0]}** | **{totals[1]}** | **{totals[2]}** | **{totals[3]}** | **{status}** |')
else:
    print('| _No Surefire reports found_ |  | 0 | 0 | 0 | 0 | ⚠️ Not available |')
PY
)

CLOVER_SUMMARY=$(python3 <<'PY'
from pathlib import Path
import xml.etree.ElementTree as ET
p=Path('target/site/clover/clover.xml')
print('| Metric | Covered | Total | Coverage |')
print('|---|---:|---:|---:|')
if not p.exists():
    print('| Clover | - | - | ⚠️ Not generated |')
else:
    root=ET.parse(p).getroot()
    metrics=None
    for elem in root.iter('metrics'):
        metrics=elem.attrib
        break
    if not metrics:
        print('| Clover | - | - | ⚠️ Metrics missing |')
    else:
        pairs=[
            ('Elements','coveredelements','elements'),
            ('Statements','coveredstatements','statements'),
            ('Methods','coveredmethods','methods'),
            ('Conditionals','coveredconditionals','conditionals'),
        ]
        for label,c,t in pairs:
            cv=int(metrics.get(c,0)); tv=int(metrics.get(t,0))
            pct=(cv/tv*100) if tv else 0.0
            print(f'| {label} | {cv} | {tv} | {pct:.2f}% |')
        print(f'| NCLOC | - | {metrics.get("ncloc","0")} | - |')
PY
)

cat > "$REPORT" <<EOF
# Magrathea ObjectStorage Test Report

Generated: $(date -Iseconds)

## Summary

| Suite | Passed | Failed | Total | Notes |
|---|---:|---:|---:|---|
| AWS CLI S3 compatibility | $PASS | $FAIL | $TOTAL | Endpoint: \`$ENDPOINT\` |
| Maven Surefire | See section | See section | See section | Latest reports under \`*/target/surefire-reports\` |
| Clover coverage | See section | - | - | Latest report under \`target/site/clover\` |

## AWS CLI S3 Compatibility

Bucket: \`$BUCKET\`

| Check | Status | Notes |
|---|---|---|
$TEST_ROWS
## Maven Surefire Results

$SUREFIRE_SUMMARY

## Clover Coverage

$CLOVER_SUMMARY

Report HTML: \`target/site/clover/index.html\`

## Implemented S3 Operation Coverage

| Operation | CLI command | Covered by AWS CLI script |
|---|---|---|
| ListBuckets | \`aws s3api list-buckets\` | ✅ |
| CreateBucket | \`aws s3api create-bucket\` | ✅ |
| HeadBucket | \`aws s3api head-bucket\` | ✅ |
| PutObject | \`aws s3api put-object\` | ✅ |
| HeadObject | \`aws s3api head-object\` | ✅ |
| GetObject | \`aws s3api get-object\` | ✅ |
| ListObjects | \`aws s3api list-objects\` | ✅ |
| DeleteObject | \`aws s3api delete-object\` | ✅ |
| DeleteBucket | \`aws s3api delete-bucket\` | ✅ |

## Not Implemented Yet

Operations not listed above are intentionally outside the current implementation and are tracked in \`PLAN.md\`.
EOF

if [ "$FAIL" -ne 0 ]; then
    echo ""
    echo "Failed tests:"
    printf ' - %s\n' "${FAILED_TESTS[@]}"
fi

echo ""
echo "=============================================="
echo "RESULTS: $PASS passed, $FAIL failed, $TOTAL total"
echo "Report: $REPORT"
echo "=============================================="

exit "$FAIL"
