#!/usr/bin/env bash
set -u

# ──────────────────────────────────────────────────────────────
# commit-with-coverage.sh
# 
# 1. Runs full test suite with JaCoCo coverage
# 2. Extracts coverage metrics
# 3. Commits with coverage summary in message
# 4. Pushes
#
# Usage: bash scripts/commit-with-coverage.sh [-m "commit message"]
#        If no -m flag, prompts for commit message.
# ──────────────────────────────────────────────────────────────

SCRIPT_DIR="$(cd "$(dirname "$0")" && pwd)"
PROJECT_DIR="$(cd "$SCRIPT_DIR/.." && pwd)"
cd "$PROJECT_DIR" || exit 1

MSG=""
while [[ $# -gt 0 ]]; do
    case "$1" in
        -m) MSG="$2"; shift 2 ;;
        *) echo "Usage: $0 [-m \"commit message\"]"; exit 1 ;;
    esac
done

# ── Step 1: Run tests with coverage ──
echo ""
echo "=============================================="
echo "  Cleaning stale build artifacts..."
echo "=============================================="
mvn clean -q || {
    echo "❌ Clean failed — aborting commit"
    exit 1
}

echo ""
echo "=============================================="
echo "  Running tests with JaCoCo coverage..."
echo "=============================================="
mvn -Pcoverage test jacoco:report -q || {
    echo "❌ Tests failed — aborting commit"
    exit 1
}
echo "  ✅ Tests passed"
echo ""

# ── Step 1b: Generate JaCoCo HTML reports ──
echo "=============================================="
echo "  Generating coverage reports..."
echo "=============================================="
mvn -Pcoverage jacoco:report -q || {
    echo "⚠️  Report generation failed — continuing without HTML"
}
echo "  ✅ Reports generated"
echo ""

# ── Step 1c: Run AWS CLI compatibility tests ──
echo "=============================================="
echo "  Running AWS CLI compatibility tests..."
echo "=============================================="
if command -v aws >/dev/null 2>&1; then
    bash test-aws-cli.sh 2>&1 || {
        echo "⚠️  AWS CLI tests had failures — check test-aws-cli.sh output"
    }
    echo "  ✅ AWS CLI tests completed"
else
    echo "  ⚠️  AWS CLI not installed — skipping CLI tests"
    echo "  Update test report manually: bash test-aws-cli.sh"
fi
echo ""

# ── Step 2: Extract coverage metrics ──
COVERAGE_LINE=""
for MODULE in "s3-reactive-api-adapter" "object-store-domain"; do
    XML="$PROJECT_DIR/$MODULE/target/site/jacoco/jacoco.xml"
    if [ ! -f "$XML" ]; then
        echo "⚠️  JaCoCo report not found for $MODULE — skipping"
        continue
    fi
    METRICS=$(python3 -c "
import xml.etree.ElementTree as ET
p = '$XML'
root = ET.parse(p).getroot()
counters = root.findall('.//counter')
totals = {}
for c in counters:
    typ = c.get('type')
    missed = int(c.get('missed', 0))
    covered = int(c.get('covered', 0))
    if typ not in totals:
        totals[typ] = {'missed': 0, 'covered': 0}
    totals[typ]['missed'] += missed
    totals[typ]['covered'] += covered
instr = totals.get('INSTRUCTION', {'covered': 0, 'missed': 0})
branch = totals.get('BRANCH', {'covered': 0, 'missed': 0})
line = totals.get('LINE', {'covered': 0, 'missed': 0})
i_pct = instr['covered']/(instr['covered']+instr['missed'])*100 if (instr['covered']+instr['missed']) else 0
b_pct = branch['covered']/(branch['covered']+branch['missed'])*100 if (branch['covered']+branch['missed']) else 0
l_pct = line['covered']/(line['covered']+line['missed'])*100 if (line['covered']+line['missed']) else 0
print(f'Instruction={i_pct:.1f}% | Branch={b_pct:.1f}% | Line={l_pct:.1f}%')
    ")
    COVERAGE_LINE="$COVERAGE_LINE$MODULE: $METRICS
"
done
if [ -n "$COVERAGE_LINE" ]; then
    echo "  $COVERAGE_LINE"
else
    echo "⚠️  No coverage data found — commit without coverage"
fi

# ── Step 3: Stage all changes ──
git add . || exit 1

# ── Step 4: Build commit message ──
if [ -n "$MSG" ]; then
    COMMIT_MSG="$MSG"
else
    echo ""
    echo "Enter commit message (leave empty for auto-generated):"
    read -r USER_MSG
    if [ -n "$USER_MSG" ]; then
        COMMIT_MSG="$USER_MSG"
    elif [ -n "$COVERAGE_LINE" ]; then
        COMMIT_MSG="update $(git diff --cached --name-only | head -1 | sed 's/.*\///') — $(echo "$COVERAGE_LINE" | head -1)"
    else
        COMMIT_MSG="update"
    fi
fi

# Append coverage summary to commit message
if [ -n "$COVERAGE_LINE" ]; then
    COMMIT_MSG="$COMMIT_MSG

$COVERAGE_LINE"
fi

# ── Step 5: Commit and push ──
git commit -m "$COMMIT_MSG" || exit 1
git push || exit 1

echo ""
echo "✅ Committed and pushed."
