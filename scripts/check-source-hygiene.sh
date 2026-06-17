#!/usr/bin/env bash
set -euo pipefail

ROOT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
cd "$ROOT_DIR"

find_generated() {
  find . \
    \( -path './.git' \
       -o -path './target' \
       -o -path './*/target' \
       -o -path './node_modules' \
       -o -path './*/node_modules' \
       -o -path './docs/arc42/generated' \
    \) -prune -o "$@"
}

failures=0

report_block() {
  local title="$1"
  local content="$2"
  if [[ -n "$content" ]]; then
    printf '\n%s\n' "$title" >&2
    printf '%s\n' "$content" >&2
    failures=1
  fi
}

class_files="$(find_generated -type f -name '*.class' -print | sort || true)"
report_block "Source-tree .class files found outside ignored build output directories:" "$class_files"

root_outputs=""
for path in ./com ./META-INF; do
  if [[ -e "$path" ]]; then
    root_outputs+="$path"$'\n'
  fi
done
report_block "Generated Java/JAR output directories found at repository root:" "${root_outputs%$'\n'}"

meta_inf_maven="$(find_generated -type d -path '*/META-INF/maven' -print | sort || true)"
report_block "Generated META-INF/maven directories found outside build output directories:" "$meta_inf_maven"

if [[ "$failures" -ne 0 ]]; then
  printf '\nSource hygiene check failed. Remove generated outputs or keep them under target/.\n' >&2
  exit 1
fi

printf 'Source hygiene check passed: no .class files or generated root/META-INF outputs outside ignored build directories.\n'
