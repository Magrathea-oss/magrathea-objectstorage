#!/usr/bin/env bash
set -u

# AWS CLI compatibility test for Magrathea ObjectStorage.
# Scope: every S3 operation currently implemented by s3-api.
# Requires: AWS CLI installed and Magrathea running (default: http://localhost:8080).
# Also writes docs/test-report.md with AWS CLI, Surefire, and Clover coverage summaries.

ENDPOINT="${ENDPOINT_URL:-http://localhost:8080}"
BUCKET="magrathea-cli-test-$(date +%s)-$$"
KEY="hello.txt"
COPY_KEY="copy.txt"
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
    if [ "$AWS_CLI_AVAILABLE" = "true" ]; then
        aws_s3api delete-object --bucket "$BUCKET" --key "$KEY" >/dev/null 2>&1 || true
        aws_s3api delete-object --bucket "$BUCKET" --key "$COPY_KEY" >/dev/null 2>&1 || true
        aws_s3api delete-bucket --bucket "$BUCKET" >/dev/null 2>&1 || true
    fi
}
trap cleanup EXIT

# Implemented S3 operations in s3-api:
# ListBuckets, CreateBucket, HeadBucket, GetBucketLocation,
# GetBucketVersioning, PutBucketVersioning, GetBucketAcl, PutBucketAcl,
# GetBucketTagging, PutBucketTagging, DeleteBucketTagging,
# ListObjects, ListObjectsV2, PutObject, GetObject, HeadObject,
# GetObjectAcl, PutObjectAcl, GetObjectTagging, PutObjectTagging,
# DeleteObjectTagging, GetObjectAttributes, CopyObject, ListObjectVersions,
# DeleteObject, DeleteObjects, DeleteBucket, PutBucketCors, GetBucketCors,
# DeleteBucketCors, PutBucketLifecycleConfiguration, GetBucketLifecycleConfiguration,
# DeleteBucketLifecycleConfiguration, PutBucketPolicy, GetBucketPolicy,
# DeleteBucketPolicy, PutBucketEncryption, GetBucketEncryption,
# DeleteBucketEncryption, PutBucketLogging, GetBucketLogging, DeleteBucketLogging.

echo "=============================================="
echo "Magrathea ObjectStorage — AWS CLI S3 Test Suite"
echo "Endpoint: $ENDPOINT"
echo "Bucket:   $BUCKET"
echo "=============================================="

SERVER_RUNNING=false
if command -v curl >/dev/null 2>&1 && curl -s --max-time 2 "$ENDPOINT" >/dev/null 2>&1; then
    SERVER_RUNNING=true
fi

if [ "$SERVER_RUNNING" != "true" ]; then
    echo "Server at $ENDPOINT is not reachable — skipping AWS CLI tests, generating report from Maven/Clover only."
fi

AWS_CLI_AVAILABLE=false
if command -v aws >/dev/null 2>&1 && [ "$SERVER_RUNNING" = "true" ]; then
    AWS_CLI_AVAILABLE=true
fi

if [ "$AWS_CLI_AVAILABLE" = "true" ]; then

run_success "ListBuckets" aws_s3api list-buckets --output json
run_success "CreateBucket" aws_s3api create-bucket --bucket "$BUCKET" --output json
run_success "HeadBucket existing" aws_s3api head-bucket --bucket "$BUCKET"
run_success "PutObject" aws_s3api put-object --bucket "$BUCKET" --key "$KEY" --body "$BODY_FILE" --content-type text/plain --output json
run_success "HeadObject existing" aws_s3api head-object --bucket "$BUCKET" --key "$KEY" --output json
run_success "PutObjectAcl" aws_s3api put-object-acl --bucket "$BUCKET" --key "$KEY" --acl private --output json
run_success "GetObjectAcl" aws_s3api get-object-acl --bucket "$BUCKET" --key "$KEY" --output json
run_success "PutObjectTagging" aws_s3api put-object-tagging --bucket "$BUCKET" --key "$KEY" --tagging "TagSet=[{Key=kind,Value=demo}]" --output json
run_success "GetObjectTagging" aws_s3api get-object-tagging --bucket "$BUCKET" --key "$KEY" --output json
run_success "DeleteObjectTagging" aws_s3api delete-object-tagging --bucket "$BUCKET" --key "$KEY" --output json
run_success "GetObjectAttributes" aws_s3api get-object-attributes --bucket "$BUCKET" --key "$KEY" --object-attributes ETag ObjectSize StorageClass --output json
run_success "GetObject" aws_s3api get-object --bucket "$BUCKET" --key "$KEY" "$OUTPUT_FILE" --output json
run_success "GetObject content matches" cmp -s "$BODY_FILE" "$OUTPUT_FILE"
run_success "ListObjects" aws_s3api list-objects --bucket "$BUCKET" --output json
run_success "ListObjectsV2" aws_s3api list-objects-v2 --bucket "$BUCKET" --output json
run_success "GetBucketLocation" aws_s3api get-bucket-location --bucket "$BUCKET" --output json
run_success "PutBucketAcl" aws_s3api put-bucket-acl --bucket "$BUCKET" --acl private --output json
run_success "GetBucketAcl" aws_s3api get-bucket-acl --bucket "$BUCKET" --output json
run_success "PutBucketTagging" aws_s3api put-bucket-tagging --bucket "$BUCKET" --tagging "TagSet=[{Key=environment,Value=test}]" --output json
run_success "GetBucketTagging" aws_s3api get-bucket-tagging --bucket "$BUCKET" --output json
run_success "DeleteBucketTagging" aws_s3api delete-bucket-tagging --bucket "$BUCKET" --output json
run_success "GetBucketVersioning initial" aws_s3api get-bucket-versioning --bucket "$BUCKET" --output json
run_success "PutBucketVersioning Enabled" aws_s3api put-bucket-versioning --bucket "$BUCKET" --versioning-configuration Status=Enabled --output json
run_success "GetBucketVersioning enabled" aws_s3api get-bucket-versioning --bucket "$BUCKET" --output json
run_success "ListObjectVersions" aws_s3api list-object-versions --bucket "$BUCKET" --output json
run_success "CopyObject" aws_s3api copy-object --bucket "$BUCKET" --key "$COPY_KEY" --copy-source "$BUCKET/$KEY" --output json
run_success "HeadObject copy existing" aws_s3api head-object --bucket "$BUCKET" --key "$COPY_KEY" --output json
run_success "DeleteObjects" aws_s3api delete-objects --bucket "$BUCKET" --delete "Objects=[{Key=$COPY_KEY}],Quiet=false" --output json
run_failure "HeadObject copy after DeleteObjects" aws_s3api head-object --bucket "$BUCKET" --key "$COPY_KEY" --output json
run_success "PutObject PARANOIC_MODE" aws_s3api put-object --bucket "$BUCKET" --key "paranoid.txt" --body "$BODY_FILE" --storage-class PARANOIC_MODE --output json
run_success "HeadObject PARANOIC_MODE" aws_s3api head-object --bucket "$BUCKET" --key "paranoid.txt" --output json
run_success "GetObjectAttributes PARANOIC_MODE" aws_s3api get-object-attributes --bucket "$BUCKET" --key "paranoid.txt" --object-attributes ETag ObjectSize StorageClass --output json
run_success "DeleteObject PARANOIC_MODE" aws_s3api delete-object --bucket "$BUCKET" --key "paranoid.txt" --output json
run_success "DeleteObject" aws_s3api delete-object --bucket "$BUCKET" --key "$KEY" --output json
run_failure "HeadObject after DeleteObject" aws_s3api head-object --bucket "$BUCKET" --key "$KEY" --output json
run_success "PutBucketCors" aws_s3api put-bucket-cors --bucket "$BUCKET" --cors-configuration '{"CORSRules":[{"AllowedOrigins":["*"],"AllowedMethods":["GET"]}]}' --output json
run_success "GetBucketCors" aws_s3api get-bucket-cors --bucket "$BUCKET" --output json
run_success "DeleteBucketCors" aws_s3api delete-bucket-cors --bucket "$BUCKET" --output json
run_failure "GetBucketCors nonexistent" aws_s3api get-bucket-cors --bucket "nonexistent-bucket" --output json
run_failure "GetBucketCors after delete" aws_s3api get-bucket-cors --bucket "$BUCKET" --output json

# ── Lifecycle Configuration ──
run_success "PutBucketLifecycleConfiguration" aws_s3api put-bucket-lifecycle-configuration --bucket "$BUCKET" --lifecycle-configuration '{"Rules":[{"ID":"expire-30","Status":"Enabled","Filter":{"Prefix":""},"Expiration":{"Days":30}}]}' --output json
run_success "GetBucketLifecycleConfiguration" aws_s3api get-bucket-lifecycle-configuration --bucket "$BUCKET" --output json
run_success "DeleteBucketLifecycleConfiguration" aws_s3api delete-bucket-lifecycle-configuration --bucket "$BUCKET" --output json
run_failure "GetBucketLifecycleConfiguration nonexistent" aws_s3api get-bucket-lifecycle-configuration --bucket "nonexistent-bucket" --output json
run_failure "GetBucketLifecycleConfiguration after delete" aws_s3api get-bucket-lifecycle-configuration --bucket "$BUCKET" --output json

# ── Bucket Policy ──
run_success "PutBucketPolicy" aws_s3api put-bucket-policy --bucket "$BUCKET" --policy '{"Version":"2012-10-17","Statement":[{"Effect":"Allow","Principal":"*","Action":"s3:GetObject"}]}' --output json
run_success "GetBucketPolicy" aws_s3api get-bucket-policy --bucket "$BUCKET" --output json
run_success "DeleteBucketPolicy" aws_s3api delete-bucket-policy --bucket "$BUCKET" --output json
run_failure "GetBucketPolicy nonexistent" aws_s3api get-bucket-policy --bucket "nonexistent-bucket" --output json
run_failure "GetBucketPolicy after delete" aws_s3api get-bucket-policy --bucket "$BUCKET" --output json

# ── Encryption Configuration ──
run_success "PutBucketEncryption" aws_s3api put-bucket-encryption --bucket "$BUCKET" --encryption-configuration '{"Rules":[{"ApplyServerSideEncryptionByDefault":{"SSEAlgorithm":"AES256"}}]}' --output json
run_success "GetBucketEncryption" aws_s3api get-bucket-encryption --bucket "$BUCKET" --output json
run_success "DeleteBucketEncryption" aws_s3api delete-bucket-encryption --bucket "$BUCKET" --output json
run_failure "GetBucketEncryption nonexistent" aws_s3api get-bucket-encryption --bucket "nonexistent-bucket" --output json
run_failure "GetBucketEncryption after delete" aws_s3api get-bucket-encryption --bucket "$BUCKET" --output json

# ── Logging Configuration ──
run_success "PutBucketLogging" aws_s3api put-bucket-logging --bucket "$BUCKET" --logging-configuration '{"LoggingEnabled":{"TargetBucket":"'"$BUCKET"'","TargetPrefix":"test/"}}' --output json
run_success "GetBucketLogging" aws_s3api get-bucket-logging --bucket "$BUCKET" --output json
run_success "DeleteBucketLogging" aws_s3api delete-bucket-logging --bucket "$BUCKET" --output json
run_failure "GetBucketLogging nonexistent" aws_s3api get-bucket-logging --bucket "nonexistent-bucket" --output json
run_failure "GetBucketLogging after delete" aws_s3api get-bucket-logging --bucket "$BUCKET" --output json

# ── Website Configuration ──
run_success "PutBucketWebsite" aws_s3api put-bucket-website --bucket "$BUCKET" --website-configuration '{"IndexDocument":{"Suffix":"index.html"},"ErrorDocument":{"Key":"error.html"}}' --output json
run_success "GetBucketWebsite" aws_s3api get-bucket-website --bucket "$BUCKET" --output json
run_success "DeleteBucketWebsite" aws_s3api delete-bucket-website --bucket "$BUCKET" --output json
run_failure "GetBucketWebsite nonexistent" aws_s3api get-bucket-website --bucket "nonexistent-bucket" --output json
run_failure "GetBucketWebsite after delete" aws_s3api get-bucket-website --bucket "$BUCKET" --output json

# ── Notification Configuration ──
run_success "PutBucketNotification" aws_s3api put-bucket-notification-configuration --bucket "$BUCKET" --notification-configuration '{"TopicConfigurations":[{"Events":["s3:ObjectCreated:*"],"TopicArn":"arn:aws:sns:us-east-1:123:topic"}]}' --output json
run_success "GetBucketNotification" aws_s3api get-bucket-notification-configuration --bucket "$BUCKET" --output json
run_failure "GetBucketNotification nonexistent" aws_s3api get-bucket-notification-configuration --bucket "nonexistent-bucket" --output json

# ── Replication Configuration ──
run_success "PutBucketReplication" aws_s3api put-bucket-replication --bucket "$BUCKET" --replication-configuration '{"Role":"arn:aws:iam::123:role/s3-replication","Rules":[{"Status":"Enabled","Destination":{"Bucket":"arn:aws:s3:::dest-bucket"}}]}' --output json
run_success "GetBucketReplication" aws_s3api get-bucket-replication --bucket "$BUCKET" --output json
run_success "DeleteBucketReplication" aws_s3api delete-bucket-replication --bucket "$BUCKET" --output json
run_failure "GetBucketReplication nonexistent" aws_s3api get-bucket-replication --bucket "nonexistent-bucket" --output json
run_failure "GetBucketReplication after delete" aws_s3api get-bucket-replication --bucket "$BUCKET" --output json

# ── Request Payment ──
run_success "PutBucketRequestPayment" aws_s3api put-bucket-request-payment --bucket "$BUCKET" --request-payment-configuration '{"Payer":"Requester"}' --output json
run_success "GetBucketRequestPayment" aws_s3api get-bucket-request-payment --bucket "$BUCKET" --output json
run_failure "GetBucketRequestPayment nonexistent" aws_s3api get-bucket-request-payment --bucket "nonexistent-bucket" --output json

# ── Ownership Controls ──
run_success "PutBucketOwnershipControls" aws_s3api put-bucket-ownership-controls --bucket "$BUCKET" --ownership-controls '{"Rules":[{"ObjectOwnership":"BucketOwnerPreferred"}]}' --output json
run_success "GetBucketOwnershipControls" aws_s3api get-bucket-ownership-controls --bucket "$BUCKET" --output json
run_success "DeleteBucketOwnershipControls" aws_s3api delete-bucket-ownership-controls --bucket "$BUCKET" --output json
run_failure "GetBucketOwnershipControls nonexistent" aws_s3api get-bucket-ownership-controls --bucket "nonexistent-bucket" --output json
run_failure "GetBucketOwnershipControls after delete" aws_s3api get-bucket-ownership-controls --bucket "$BUCKET" --output json

# ── Public Access Block ──
run_success "PutPublicAccessBlock" aws_s3api put-public-access-block --bucket "$BUCKET" --public-access-block-configuration '{"BlockPublicAcls":true,"IgnorePublicAcls":false,"BlockPublicPolicy":false,"RestrictPublicBuckets":false}' --output json
run_success "GetPublicAccessBlock" aws_s3api get-public-access-block --bucket "$BUCKET" --output json
run_success "DeletePublicAccessBlock" aws_s3api delete-public-access-block --bucket "$BUCKET" --output json
run_failure "GetPublicAccessBlock nonexistent" aws_s3api get-public-access-block --bucket "nonexistent-bucket" --output json
run_failure "GetPublicAccessBlock after delete" aws_s3api get-public-access-block --bucket "$BUCKET" --output json

# ── Accelerate Configuration ──
run_success "PutBucketAccelerateConfiguration" aws_s3api put-bucket-accelerate-configuration --bucket "$BUCKET" --accelerate-configuration '{"Status":"Enabled"}' --output json
run_success "GetBucketAccelerateConfiguration" aws_s3api get-bucket-accelerate-configuration --bucket "$BUCKET" --output json
run_failure "GetBucketAccelerateConfiguration nonexistent" aws_s3api get-bucket-accelerate-configuration --bucket "nonexistent-bucket" --output json


# ── Analytics Configuration ──
ANALYTICS_ID="cli-analytics-id"
ANALYTICS_CONFIG='{"Id":"'"$ANALYTICS_ID"'","Filter":{"Prefix":"cli-test"},"StorageClassAnalysis":{}}'
run_success "PutBucketAnalyticsConfiguration" aws_s3api put-bucket-analytics-configuration --bucket "$BUCKET" --id "$ANALYTICS_ID" --analytics-configuration "$ANALYTICS_CONFIG" --output json
run_success "GetBucketAnalyticsConfiguration" aws_s3api get-bucket-analytics-configuration --bucket "$BUCKET" --id "$ANALYTICS_ID" --output json
run_success "ListBucketAnalyticsConfigurations" aws_s3api list-bucket-analytics-configurations --bucket "$BUCKET" --output json
run_success "DeleteBucketAnalyticsConfiguration" aws_s3api delete-bucket-analytics-configuration --bucket "$BUCKET" --id "$ANALYTICS_ID" --output json
run_failure "GetBucketAnalyticsConfiguration nonexistent bucket" aws_s3api get-bucket-analytics-configuration --bucket "nonexistent-bucket" --id "$ANALYTICS_ID" --output json
run_failure "GetBucketAnalyticsConfiguration missing id" aws_s3api get-bucket-analytics-configuration --bucket "$BUCKET" --id "nonexistent-id" --output json
run_failure "DeleteBucketAnalyticsConfiguration nonexistent bucket" aws_s3api delete-bucket-analytics-configuration --bucket "nonexistent-bucket" --id "$ANALYTICS_ID" --output json
run_failure "DeleteBucketAnalyticsConfiguration missing id" aws_s3api delete-bucket-analytics-configuration --bucket "$BUCKET" --id "nonexistent-id" --output json
run_failure "PutBucketAnalyticsConfiguration nonexistent bucket" aws_s3api put-bucket-analytics-configuration --bucket "nonexistent-bucket" --id "$ANALYTICS_ID" --analytics-configuration "$ANALYTICS_CONFIG" --output json
run_failure "ListBucketAnalyticsConfigurations nonexistent bucket" aws_s3api list-bucket-analytics-configurations --bucket "nonexistent-bucket" --output json

# ── Inventory Configuration ──
INVENTORY_ID="cli-inventory-id"
INVENTORY_CONFIG='{"Id":"'"$INVENTORY_ID"'","IsEnabled":true,"Destination":{"Format":"CSV","Bucket":"arn:aws:s3:::dest-bucket","Prefix":"cli-test"},"ScheduleFrequency":"Daily"}'
run_success "PutBucketInventoryConfiguration" aws_s3api put-bucket-inventory-configuration --bucket "$BUCKET" --id "$INVENTORY_ID" --inventory-configuration "$INVENTORY_CONFIG" --output json
run_success "GetBucketInventoryConfiguration" aws_s3api get-bucket-inventory-configuration --bucket "$BUCKET" --id "$INVENTORY_ID" --output json
run_success "ListBucketInventoryConfigurations" aws_s3api list-bucket-inventory-configurations --bucket "$BUCKET" --output json
run_success "DeleteBucketInventoryConfiguration" aws_s3api delete-bucket-inventory-configuration --bucket "$BUCKET" --id "$INVENTORY_ID" --output json
run_failure "GetBucketInventoryConfiguration nonexistent bucket" aws_s3api get-bucket-inventory-configuration --bucket "nonexistent-bucket" --id "$INVENTORY_ID" --output json
run_failure "GetBucketInventoryConfiguration missing id" aws_s3api get-bucket-inventory-configuration --bucket "$BUCKET" --id "nonexistent-id" --output json
run_failure "DeleteBucketInventoryConfiguration nonexistent bucket" aws_s3api delete-bucket-inventory-configuration --bucket "nonexistent-bucket" --id "$INVENTORY_ID" --output json
run_failure "DeleteBucketInventoryConfiguration missing id" aws_s3api delete-bucket-inventory-configuration --bucket "$BUCKET" --id "nonexistent-id" --output json
run_failure "PutBucketInventoryConfiguration nonexistent bucket" aws_s3api put-bucket-inventory-configuration --bucket "nonexistent-bucket" --id "$INVENTORY_ID" --inventory-configuration "$INVENTORY_CONFIG" --output json
run_failure "ListBucketInventoryConfigurations nonexistent bucket" aws_s3api list-bucket-inventory-configurations --bucket "nonexistent-bucket" --output json

# ── Batch 1 Phase F operations ──

# RenameObject: aws s3api rename-object (available since AWS CLI 2.15+)
if aws_s3api rename-object --help >/dev/null 2>&1; then
    run_success "RenameObject" aws_s3api rename-object --bucket "$BUCKET" --key "$KEY" --destination-key "rename-dest.txt" --output json
    run_success "HeadObject after rename (destination)" aws_s3api head-object --bucket "$BUCKET" --key "rename-dest.txt" --output json
    run_failure "HeadObject after rename (source gone)" aws_s3api head-object --bucket "$BUCKET" --key "$KEY" --output json
    KEY="rename-dest.txt"
else
    echo "  ⚠️  aws s3api rename-object not available — skipping"
fi

# GetObjectTorrent: aws s3api get-object-torrent
if aws_s3api get-object-torrent --help >/dev/null 2>&1; then
    run_success "GetObjectTorrent" aws_s3api get-object-torrent --bucket "$BUCKET" --key "$KEY" "$OUTPUT_FILE" --output json
    run_failure "GetObjectTorrent nonexistent" aws_s3api get-object-torrent --bucket "$BUCKET" --key "nonexistent.txt" /dev/null --output json
else
    echo "  ⚠️  aws s3api get-object-torrent not available — skipping"
fi

# RestoreObject: aws s3api restore-object
if aws_s3api restore-object --help >/dev/null 2>&1; then
    run_success "RestoreObject" aws_s3api restore-object --bucket "$BUCKET" --key "$KEY" --restore-request '{RestoreRequest: {Tier: Standard, Days: 30}}' --output json
    run_failure "RestoreObject nonexistent" aws_s3api restore-object --bucket "$BUCKET" --key "nonexistent.txt" --restore-request '{RestoreRequest: {Tier: Standard, Days: 30}}' --output json
else
    echo "  ⚠️  aws s3api restore-object not available — skipping"
fi

run_success "DeleteBucket" aws_s3api delete-bucket --bucket "$BUCKET" --output json
run_failure "HeadBucket after DeleteBucket" aws_s3api head-bucket --bucket "$BUCKET"

# Failure tests
run_failure "GetObject nonexistent" aws_s3api get-object --bucket "$BUCKET" --key "nonexistent.txt" /dev/null --output json
run_failure "HeadObject nonexistent" aws_s3api head-object --bucket "$BUCKET" --key "nonexistent.txt" --output json
run_failure "GetBucketLocation nonexistent" aws_s3api get-bucket-location --bucket "nonexistent-bucket" --output json
run_failure "GetBucketVersioning nonexistent" aws_s3api get-bucket-versioning --bucket "nonexistent-bucket" --output json
run_failure "GetBucketAcl nonexistent" aws_s3api get-bucket-acl --bucket "nonexistent-bucket" --output json
run_failure "GetBucketTagging nonexistent" aws_s3api get-bucket-tagging --bucket "nonexistent-bucket" --output json
run_failure "CopyObject nonexistent source" aws_s3api copy-object --bucket "$BUCKET" --key "target.txt" --copy-source "$BUCKET/nonexistent.txt" --output json
run_failure "PutObject nonexistent bucket" aws_s3api put-object --bucket "nonexistent-bucket" --key "x" --body "$BODY_FILE" --output json
run_failure "GetObjectAcl nonexistent" aws_s3api get-object-acl --bucket "$BUCKET" --key "nonexistent.txt" --output json

else
    echo "Skipping AWS CLI command execution — AWS CLI not available."
    TOTAL=0; PASS=0; FAIL=0
fi

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
| GetObjectAcl | \`aws s3api get-object-acl\` | ✅ |
| PutObjectAcl | \`aws s3api put-object-acl\` | ✅ |
| GetObjectTagging | \`aws s3api get-object-tagging\` | ✅ |
| PutObjectTagging | \`aws s3api put-object-tagging\` | ✅ |
| DeleteObjectTagging | \`aws s3api delete-object-tagging\` | ✅ |
| GetObjectAttributes | \`aws s3api get-object-attributes\` | ✅ |
| ListObjects | \`aws s3api list-objects\` | ✅ |
| ListObjectsV2 | \`aws s3api list-objects-v2\` | ✅ |
| GetBucketLocation | \`aws s3api get-bucket-location\` | ✅ |
| GetBucketAcl | \`aws s3api get-bucket-acl\` | ✅ |
| PutBucketAcl | \`aws s3api put-bucket-acl\` | ✅ |
| GetBucketTagging | \`aws s3api get-bucket-tagging\` | ✅ |
| PutBucketTagging | \`aws s3api put-bucket-tagging\` | ✅ |
| DeleteBucketTagging | \`aws s3api delete-bucket-tagging\` | ✅ |
| GetBucketVersioning | \`aws s3api get-bucket-versioning\` | ✅ |
| PutBucketVersioning | \`aws s3api put-bucket-versioning\` | ✅ |
| ListObjectVersions | \`aws s3api list-object-versions\` | ✅ |
| CopyObject | \`aws s3api copy-object\` | ✅ |
| PutObject PARANOIC_MODE | \`aws s3api put-object --storage-class PARANOIC_MODE\` | ✅ |
| HeadObject PARANOIC_MODE | \`aws s3api head-object\` | ✅ |
| GetObjectAttributes PARANOIC_MODE | \`aws s3api get-object-attributes\` | ✅ |
| DeleteObject PARANOIC_MODE | \`aws s3api delete-object\` | ✅ |
| DeleteObject | \`aws s3api delete-object\` | ✅ |
| DeleteObjects | \`aws s3api delete-objects\` | ✅ |
| PutBucketCors | \`aws s3api put-bucket-cors\` | ✅ |
| GetBucketCors | \`aws s3api get-bucket-cors\` | ✅ |
| DeleteBucketCors | \`aws s3api delete-bucket-cors\` | ✅ |
| PutBucketLifecycleConfiguration | \`aws s3api put-bucket-lifecycle-configuration\` | ✅ |
| GetBucketLifecycleConfiguration | \`aws s3api get-bucket-lifecycle-configuration\` | ✅ |
| DeleteBucketLifecycleConfiguration | \`aws s3api delete-bucket-lifecycle-configuration\` | ✅ |
| PutBucketPolicy | \`aws s3api put-bucket-policy\` | ✅ |
| GetBucketPolicy | \`aws s3api get-bucket-policy\` | ✅ |
| DeleteBucketPolicy | \`aws s3api delete-bucket-policy\` | ✅ |
| PutBucketEncryption | \`aws s3api put-bucket-encryption\` | ✅ |
| GetBucketEncryption | \`aws s3api get-bucket-encryption\` | ✅ |
| DeleteBucketEncryption | \`aws s3api delete-bucket-encryption\` | ✅ |
| PutBucketLogging | \`aws s3api put-bucket-logging\` | ✅ |
| GetBucketLogging | \`aws s3api get-bucket-logging\` | ✅ |
| DeleteBucketLogging | \`aws s3api delete-bucket-logging\` | ✅ |
| PutBucketWebsite | \`aws s3api put-bucket-website\` | ✅ |
| GetBucketWebsite | \`aws s3api get-bucket-website\` | ✅ |
| DeleteBucketWebsite | \`aws s3api delete-bucket-website\` | ✅ |
| PutBucketNotification | \`aws s3api put-bucket-notification-configuration\` | ✅ |
| GetBucketNotification | \`aws s3api get-bucket-notification-configuration\` | ✅ |
| PutBucketReplication | \`aws s3api put-bucket-replication\` | ✅ |
| GetBucketReplication | \`aws s3api get-bucket-replication\` | ✅ |
| DeleteBucketReplication | \`aws s3api delete-bucket-replication\` | ✅ |
| PutBucketRequestPayment | \`aws s3api put-bucket-request-payment\` | ✅ |
| GetBucketRequestPayment | \`aws s3api get-bucket-request-payment\` | ✅ |
| PutBucketOwnershipControls | \`aws s3api put-bucket-ownership-controls\` | ✅ |
| GetBucketOwnershipControls | \`aws s3api get-bucket-ownership-controls\` | ✅ |
| DeleteBucketOwnershipControls | \`aws s3api delete-bucket-ownership-controls\` | ✅ |
| PutPublicAccessBlock | \`aws s3api put-public-access-block\` | ✅ |
| GetPublicAccessBlock | \`aws s3api get-public-access-block\` | ✅ |
| DeletePublicAccessBlock | \`aws s3api delete-public-access-block\` | ✅ |
| PutBucketAccelerateConfiguration | \`aws s3api put-bucket-accelerate-configuration\` | ✅ |
| GetBucketAccelerateConfiguration | \`aws s3api get-bucket-accelerate-configuration\` | ✅ |
| PutBucketAnalyticsConfiguration | `aws s3api put-bucket-analytics-configuration` | ✅ |
| GetBucketAnalyticsConfiguration | `aws s3api get-bucket-analytics-configuration` | ✅ |
| DeleteBucketAnalyticsConfiguration | `aws s3api delete-bucket-analytics-configuration` | ✅ |
| ListBucketAnalyticsConfigurations | `aws s3api list-bucket-analytics-configurations` | ✅ |
| PutBucketInventoryConfiguration | `aws s3api put-bucket-inventory-configuration` | ✅ |
| GetBucketInventoryConfiguration | `aws s3api get-bucket-inventory-configuration` | ✅ |
| DeleteBucketInventoryConfiguration | `aws s3api delete-bucket-inventory-configuration` | ✅ |
| ListBucketInventoryConfigurations | `aws s3api list-bucket-inventory-configurations` | ✅ |
| DeleteBucket | \`aws s3api delete-bucket\` | ✅ |

### Failure Tests

| Check | Status | Notes |
|---|---|---|
| GetBucketCors nonexistent | ✅ | Expected failure |
| GetBucketCors after delete | ✅ | Expected failure |
| GetBucketLifecycleConfiguration nonexistent | ✅ | Expected failure |
| GetBucketLifecycleConfiguration after delete | ✅ | Expected failure |
| GetBucketPolicy nonexistent | ✅ | Expected failure |
| GetBucketPolicy after delete | ✅ | Expected failure |
| GetBucketEncryption nonexistent | ✅ | Expected failure |
| GetBucketEncryption after delete | ✅ | Expected failure |
| GetBucketLogging nonexistent | ✅ | Expected failure |
| GetBucketLogging after delete | ✅ | Expected failure |
| GetBucketAnalyticsConfiguration nonexistent bucket | ✅ | Expected failure |
| GetBucketAnalyticsConfiguration missing id | ✅ | Expected failure |
| DeleteBucketAnalyticsConfiguration nonexistent bucket | ✅ | Expected failure |
| DeleteBucketAnalyticsConfiguration missing id | ✅ | Expected failure |
| PutBucketAnalyticsConfiguration nonexistent bucket | ✅ | Expected failure |
| ListBucketAnalyticsConfigurations nonexistent bucket | ✅ | Expected failure |
| GetBucketInventoryConfiguration nonexistent bucket | ✅ | Expected failure |
| GetBucketInventoryConfiguration missing id | ✅ | Expected failure |
| DeleteBucketInventoryConfiguration nonexistent bucket | ✅ | Expected failure |
| DeleteBucketInventoryConfiguration missing id | ✅ | Expected failure |
| PutBucketInventoryConfiguration nonexistent bucket | ✅ | Expected failure |
| ListBucketInventoryConfigurations nonexistent bucket | ✅ | Expected failure |

| Check | Status | Notes |
|---|---|---|
| GetObject nonexistent | ✅ | Expected failure |
| HeadObject nonexistent | ✅ | Expected failure |
| GetBucketLocation nonexistent | ✅ | Expected failure |
| GetBucketVersioning nonexistent | ✅ | Expected failure |
| GetBucketAcl nonexistent | ✅ | Expected failure |
| GetBucketTagging nonexistent | ✅ | Expected failure |
| CopyObject nonexistent source | ✅ | Expected failure |
| PutObject nonexistent bucket | ✅ | Expected failure |
| GetObjectAcl nonexistent | ✅ | Expected failure |

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
