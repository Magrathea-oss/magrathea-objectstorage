const POLICY_LOOKUP_IDS: Readonly<Record<string, string>> = {
  MINIO_STANDARD: 'minio-standard',
}

/** Maps catalog storage-class identity to the Admin API's stable lookup identity. */
export function storagePolicyLookupId(storageClassId: string): string {
  return POLICY_LOOKUP_IDS[storageClassId] ?? storageClassId
}

export function storagePolicyDetailRoute(storageClassId: string): string {
  return `/admin/storage-policies/${encodeURIComponent(storagePolicyLookupId(storageClassId))}`
}
