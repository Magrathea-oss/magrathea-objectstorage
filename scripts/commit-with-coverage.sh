#!/usr/bin/env bash
set -u

# ──────────────────────────────────────────────────────────────
# commit-with-coverage.sh
# 
# 1. Runs full test suite with Clover coverage
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
echo "  Running tests with Clover coverage..."
echo "=============================================="
mvn -Pcoverage clover:setup test clover:aggregate clover:clover -q || {
    echo "❌ Tests failed — aborting commit"
    exit 1
}
echo "  ✅ Tests passed"
echo ""

# ── Step 2: Extract coverage metrics ──
CLOVER_XML="$PROJECT_DIR/target/site/clover/clover.xml"
if [ ! -f "$CLOVER_XML" ]; then
    echo "⚠️  Clover report not found — commit without coverage data"
    COVERAGE_LINE=""
else
    COVERAGE_LINE=$(python3 -c "
import xml.etree.ElementTree as ET
p = '$CLOVER_XML'
root = ET.parse(p).getroot()
for elem in root.iter('metrics'):
    m = elem.attrib
    break
pairs = [
    ('Elements', 'coveredelements', 'elements'),
    ('Statements', 'coveredstatements', 'statements'),
    ('Methods', 'coveredmethods', 'methods'),
]
parts = []
for label, c, t in pairs:
    cv = int(m.get(c, 0)); tv = int(m.get(t, 0))
    pct = (cv/tv*100) if tv else 0.0
    parts.append(f'{label}={pct:.1f}%')
print(f'Coverage: {\" | \".join(parts)}')
    ")
    echo "  $COVERAGE_LINE"
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
        COMMIT_MSG="update $(git diff --cached --name-only | head -1 | sed 's/.*\///') — $COVERAGE_LINE"
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
