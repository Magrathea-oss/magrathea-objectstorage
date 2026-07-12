import { describe, expect, it } from 'vitest'
import { storagePolicyDetailRoute, storagePolicyLookupId } from '../src/storage-policy-routes'

describe('storage-policy route identity', () => {
  it('maps MINIO_STANDARD explicitly to the required stable lookup slug', () => {
    expect(storagePolicyLookupId('MINIO_STANDARD')).toBe('minio-standard')
    expect(storagePolicyDetailRoute('MINIO_STANDARD')).toBe('/admin/storage-policies/minio-standard')
  })

  it('preserves unknown catalog identities instead of applying a generic lowercase transform', () => {
    expect(storagePolicyLookupId('ARCHIVE_EC')).toBe('ARCHIVE_EC')
    expect(storagePolicyDetailRoute('ARCHIVE_EC')).toBe('/admin/storage-policies/ARCHIVE_EC')
  })
})
