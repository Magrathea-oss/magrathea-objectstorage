#!/usr/bin/env bash
set -euo pipefail

ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"

failures=0

check_absent_dependency() {
  local module="$1"
  local forbidden="$2"
  local pom="$ROOT/$module/pom.xml"
  if [[ ! -f "$pom" ]]; then
    echo "ERROR: Missing POM: $module/pom.xml" >&2
    failures=$((failures + 1))
    return
  fi
  if python3 - "$pom" "$forbidden" <<'PY'
import sys
import xml.etree.ElementTree as ET
pom, forbidden = sys.argv[1], sys.argv[2]
ns = {'m': 'http://maven.apache.org/POM/4.0.0'}
root = ET.parse(pom).getroot()
for dep in root.findall('./m:dependencies/m:dependency', ns):
    artifact = dep.findtext('m:artifactId', default='', namespaces=ns).strip()
    if artifact == forbidden:
        sys.exit(0)
sys.exit(1)
PY
  then
    echo "ERROR: $module must not depend on infrastructure module $forbidden" >&2
    failures=$((failures + 1))
  fi
}

check_absent_dependency "object-store-reactive-application" "object-store-reactive-infrastructure"
check_absent_dependency "object-store-reactive-application" "object-store-reactive-repository-storage-engine-infrastructure"
check_absent_dependency "storage-engine-reactive-application" "storage-engine-reactive-infrastructure"

for module in object-store-reactive-repository-application storage-engine-reactive-repository-application; do
  for forbidden in \
    object-store-reactive-infrastructure \
    object-store-reactive-repository-storage-engine-infrastructure \
    storage-engine-reactive-infrastructure; do
    check_absent_dependency "$module" "$forbidden"
  done
done

scan_source="$ROOT/bootstrap-application/src/main/java/com/example/magrathea/bootstrap/MagratheaApplication.java"
if [[ ! -f "$scan_source" ]]; then
  echo "ERROR: Missing bootstrap application source: $scan_source" >&2
  failures=$((failures + 1))
else
  for package_root in \
    '"com.example.magrathea.bootstrap"' \
    '"com.example.magrathea.objectstore"' \
    '"com.example.magrathea.objectstorage"' \
    '"com.example.magrathea.reactive"' \
    '"com.example.magrathea.storageengine"' \
    '"com.example.magrathea.admin"'; do
    if ! grep -Fq "$package_root" "$scan_source"; then
      echo "ERROR: MagratheaApplication component scan must explicitly include $package_root" >&2
      failures=$((failures + 1))
    fi
  done
  if grep -Fq '"com.example.magrathea"' "$scan_source"; then
    echo 'ERROR: MagratheaApplication must not use broad top-level scan "com.example.magrathea"' >&2
    failures=$((failures + 1))
  fi
fi

check_source_contains() {
  local relative_path="$1"
  local expected="$2"
  local file="$ROOT/$relative_path"
  if [[ ! -f "$file" ]]; then
    echo "ERROR: Missing source file: $relative_path" >&2
    failures=$((failures + 1))
    return
  fi
  if ! grep -Fq "$expected" "$file"; then
    echo "ERROR: $relative_path must contain: $expected" >&2
    failures=$((failures + 1))
  fi
}

for in_memory_repo in \
  object-store-reactive-infrastructure/src/main/java/com/example/magrathea/reactive/infrastructure/adapter/persistence/InMemoryReactiveS3ObjectRepository.java \
  object-store-reactive-infrastructure/src/main/java/com/example/magrathea/reactive/infrastructure/adapter/persistence/InMemoryReactiveBucketRepository.java \
  object-store-reactive-infrastructure/src/main/java/com/example/magrathea/reactive/infrastructure/adapter/persistence/InMemoryReactiveMultipartUploadRepository.java; do
  check_source_contains "$in_memory_repo" '@Profile({"single-node", "default"})'
done

for storage_engine_repo in \
  object-store-reactive-repository-storage-engine-infrastructure/src/main/java/com/example/magrathea/objectstorage/repository/storageengine/adapter/StorageEngineReactiveS3ObjectRepository.java \
  object-store-reactive-repository-storage-engine-infrastructure/src/main/java/com/example/magrathea/objectstorage/repository/storageengine/adapter/StorageEngineReactiveBucketRepository.java \
  object-store-reactive-repository-storage-engine-infrastructure/src/main/java/com/example/magrathea/objectstorage/repository/storageengine/adapter/StorageEngineReactiveMultipartUploadRepository.java; do
  check_source_contains "$storage_engine_repo" '@Profile("storage-engine")'
done

check_source_contains \
  bootstrap-application/src/main/java/com/example/magrathea/bootstrap/ObjectStoreBackendStatusConfig.java \
  'magrathea.object-store.backend=in-memory conflicts with active storage-engine profile'
check_source_contains \
  bootstrap-application/src/main/java/com/example/magrathea/bootstrap/ObjectStoreBackendStatusConfig.java \
  'magrathea.object-store.backend=storage-engine requires the storage-engine Spring profile'

if [[ "$failures" -ne 0 ]]; then
  exit 1
fi

echo "Module layering guard passed."
