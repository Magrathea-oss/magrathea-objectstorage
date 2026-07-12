import { flushPromises, mount } from '@vue/test-utils'
import { createMemoryHistory, createRouter } from 'vue-router'
import { describe, expect, it, vi } from 'vitest'
import type { AdminApiClient } from '../src/adapters/admin-api-client'
import { AdminClientError } from '../src/adapters/admin-api-client'
import type { S3HeadObjectClient } from '../src/adapters/s3-head-object-client'
import AdminPage from '../src/AdminPage.vue'
import { adminApiClientKey, s3HeadObjectClientKey } from '../src/context'
import { createObjectStorageExtension } from '../src/extension'

const policy = {
  storageClassId: 'MINIO_STANDARD',
  dedup: { scope: 'GLOBAL', algorithm: 'SHA-256', chunkSize: 1048576, alignment: 'CONTENT_DEFINED' },
  compression: { algorithm: 'ZSTD', level: 3 },
  encryption: { algorithm: 'AES-256-GCM', defaultKeyReference: 'storage-default' },
  erasureCoding: { dataBlocks: 4, parityBlocks: 2 },
  replication: { factor: 1 },
  _links: {},
} as const

function adminClient(overrides: Partial<AdminApiClient> = {}): AdminApiClient {
  return {
    getHealth: vi.fn(async () => ({ status: 'ok', message: 'Admin API is live', mode: 'configuration-as-code', _links: {} })),
    getLiveness: vi.fn(async () => ({ status: 'ok', probe: 'liveness', message: 'Process accepts requests', _links: {} })),
    getReadiness: vi.fn(async () => ({ status: 'ready', probe: 'readiness', mode: 'configuration-as-code', components: [], _links: {} })),
    getBackendStatus: vi.fn(),
    listPolicies: vi.fn(async () => ({ count: 1, storagePolicies: [policy], _links: {} })),
    getPolicy: vi.fn(async () => policy),
    validatePolicy: vi.fn(),
    listDevices: vi.fn(),
    getDevice: vi.fn(),
    listDiskSets: vi.fn(),
    getDiskSet: vi.fn(),
    getBucketCapacity: vi.fn(),
    configureBucketQuota: vi.fn(),
    getOperationalReport: vi.fn(),
    ...overrides,
  } as AdminApiClient
}

async function mountRoute(path: string, client: AdminApiClient, s3Client?: S3HeadObjectClient) {
  const routes = createObjectStorageExtension().routes!.map(({ route }) => route)
  const router = createRouter({ history: createMemoryHistory(), routes })
  await router.push(path)
  await router.isReady()
  const wrapper = mount(AdminPage, {
    global: {
      plugins: [router],
      provide: {
        [adminApiClientKey as symbol]: client,
        [s3HeadObjectClientKey as symbol]: s3Client,
      },
    },
  })
  await flushPromises()
  return wrapper
}

describe('EP-7 Object Storage administration pages', () => {
  it('shows live health and the real not-ready dependency without substituting a healthy default', async () => {
    const client = adminClient({
      getReadiness: vi.fn(async () => ({
        status: 'not-ready',
        probe: 'readiness',
        mode: 'configuration-as-code',
        components: [{ name: 'storage-device-catalog', status: 'not-configured', reason: 'Catalog directory is not configured' }],
        _links: {},
      })),
    })

    const wrapper = await mountRoute('/admin', client)

    expect(wrapper.text()).toContain('Admin API is live')
    expect(wrapper.text()).toContain('not-ready')
    expect(wrapper.text()).toContain('storage-device-catalog')
    expect(wrapper.text()).toContain('not-configured')
    expect(wrapper.text()).not.toContain('All dependencies are ready')
  })

  it('links the MINIO_STANDARD catalog identity through its stable Admin API lookup slug', async () => {
    const wrapper = await mountRoute('/admin/storage-policies', adminClient())

    expect(wrapper.get('a.resource-link').attributes('href')).toBe('/admin/storage-policies/minio-standard')
    expect(wrapper.get('a.resource-link').text()).toContain('MINIO_STANDARD')
  })

  it('restores the canonical policy deep link with its unchanged Admin API lookup identity', async () => {
    const client = adminClient()
    const wrapper = await mountRoute('/admin/storage-policies/minio-standard', client)

    expect(client.getPolicy).toHaveBeenCalledWith('minio-standard')
    expect(wrapper.text()).toContain('MINIO_STANDARD')
    for (const stage of ['dedup', 'compression', 'encryption', 'erasureCoding', 'replication']) {
      expect(wrapper.text()).toContain(stage)
    }
    expect(wrapper.text()).toContain('Read-only configuration-as-code')
    expect(wrapper.text()).toContain('YAML configuration followed by catalog reload or redeployment')
    const offeredActions = wrapper.findAll('button, input[type="submit"]').map((element) => element.text().trim())
    expect(offeredActions.join(' ')).not.toMatch(/\b(create|edit|save|delete)\b/i)
  })

  it('labels provider-not-configured operational reports unavailable instead of fabricating evidence', async () => {
    const getOperationalReport = vi.fn(async (report: string) => {
      throw new AdminClientError('http', `No real operational report provider is configured for ${report}`, {
        method: 'GET',
        url: `https://admin.example.test/admin/reports/${report}`,
        status: 503,
        code: 'report-provider-not-configured',
        details: { availability: 'not-configured' },
      })
    })
    const wrapper = await mountRoute('/admin/data-hygiene', adminClient({ getOperationalReport }))

    expect(getOperationalReport).toHaveBeenCalledTimes(3)
    for (const report of ['recovery', 'garbage-collection', 'scrub']) {
      expect(wrapper.text()).toContain(report)
      expect(wrapper.text()).toContain(`No real operational report provider is configured for ${report}`)
    }
    expect(wrapper.findAll('h2').filter((heading) => heading.text() === 'Provider not configured')).toHaveLength(3)
    expect(wrapper.text()).not.toContain('Healthy')
    expect(wrapper.findAll('button').map((button) => button.text()).join(' ')).not.toMatch(
      /\b(run|start|repair|recover|scrub|garbage collection)\b/i,
    )
  })

  it('renders backend selection, catalog sources, roots, and explicitly unavailable values', async () => {
    const getBackendStatus = vi.fn(async () => ({
      selectedBackend: 'storage-engine',
      selection: {
        profile: 'storage-engine',
        property: { name: 'magrathea.object-store.backend', value: 'storage-engine' },
      },
      catalogs: {
        policies: { itemCount: 2, sourceDirectory: 'target/ep7-admin-api/config/storage-policies', availability: 'available' },
        devices: { itemCount: 2, sourceDirectory: 'target/ep7-admin-api/config/storage-devices', availability: 'available' },
        diskSets: { itemCount: 1, sourceDirectory: '', availability: 'not-configured' },
      },
      storageRoots: {
        primary: { path: 'target/ep7-admin-api/storage-engine', availability: 'available' },
      },
    }))
    const wrapper = await mountRoute('/admin/backend-status', adminClient({ getBackendStatus }))

    expect(wrapper.text()).toContain('storage-engine')
    expect(wrapper.text()).toContain('magrathea.object-store.backend = storage-engine')
    expect(wrapper.text()).toContain('Policy catalog')
    expect(wrapper.text()).toContain('Device catalog')
    expect(wrapper.text()).toContain('Disk-set catalog')
    expect(wrapper.text()).toMatch(/Policy catalog[\s\S]*Catalog count[\s\S]*2[\s\S]*Source path[\s\S]*target\/ep7-admin-api\/config\/storage-policies/)
    expect(wrapper.text()).toMatch(/Device catalog[\s\S]*Catalog count[\s\S]*2[\s\S]*Source path[\s\S]*target\/ep7-admin-api\/config\/storage-devices/)
    expect(wrapper.text()).toMatch(/Disk-set catalog[\s\S]*Catalog count[\s\S]*1[\s\S]*Source path[\s\S]*Unavailable — not returned by the Admin API/)
    expect(wrapper.text()).toContain('target/ep7-admin-api/storage-engine — available')
  })

  it('validates a realistic policy proposal as explicitly non-persistent without catalog mutation calls', async () => {
    const validatePolicy = vi.fn(async () => ({ valid: false, errors: [{ field: 'replication.factor', message: 'must be positive' }] }))
    const client = adminClient({ validatePolicy })
    const wrapper = await mountRoute('/admin/storage-policies/validate', client)
    const inputs = wrapper.findAll('input')
    await inputs[0]!.setValue('ARCHIVE_EC')
    await inputs[1]!.setValue('8')
    await inputs[2]!.setValue('4')
    await inputs[3]!.setValue('0')
    await wrapper.get('form').trigger('submit')
    await flushPromises()

    expect(validatePolicy).toHaveBeenCalledWith({
      storageClassId: 'ARCHIVE_EC',
      erasureCoding: { dataBlocks: 8, parityBlocks: 4 },
      replication: { factor: 0 },
    })
    expect(wrapper.text()).toContain('Validation only — non-persistent')
    expect(wrapper.text()).toContain('Validation report: Invalid')
    expect(wrapper.text()).toContain('replication.factor: must be positive')
    expect(wrapper.text()).toContain('never written to the storage-policy catalog')
    expect(wrapper.text()).toContain('Storage-policy catalog refreshed after validation')
    expect(wrapper.text()).toContain('Catalog unchanged — the proposal was not persisted.')
    expect(wrapper.text()).toContain('Before: MINIO_STANDARD')
    expect(wrapper.text()).toContain('After refresh: MINIO_STANDARD')
    expect(client.listPolicies).toHaveBeenCalledTimes(2)
  })

  it('replaces a failed policy catalog with a truthful retry state and never renders stale rows', async () => {
    const listPolicies = vi.fn()
      .mockRejectedValueOnce(new AdminClientError('http', 'storage-policy catalog is unavailable', {
        method: 'GET', url: '/admin/storage-policies', status: 503, code: 'catalog-unavailable',
      }))
      .mockResolvedValueOnce({ count: 1, storagePolicies: [policy], _links: {} })
    const wrapper = await mountRoute('/admin/storage-policies', adminClient({ listPolicies }))

    expect(wrapper.text()).toContain('storage-policy catalog is unavailable')
    expect(wrapper.text()).not.toContain('MINIO_STANDARD')
    const retry = wrapper.get('button')
    expect(retry.attributes('type')).toBe('button')
    await retry.trigger('keydown', { key: 'Enter' })
    await retry.trigger('click')
    await flushPromises()
    expect(listPolicies).toHaveBeenCalledTimes(2)
    expect(wrapper.text()).toContain('MINIO_STANDARD')
  })

  it('preserves read-only device and disk-set topology fixtures with navigable membership', async () => {
    const device = {
      id: 'node-1-disk-0', storagePath: '/data/node-1/disk-0', health: 'DEGRADED',
      totalCapacityBytes: 107374182400, availableCapacityBytes: 26843545600,
      readEligible: true, writeEligible: false,
    }
    const devicePage = await mountRoute('/admin/storage-devices/node-1-disk-0', adminClient({ getDevice: vi.fn(async () => device) }))
    expect(devicePage.text()).toContain('/data/node-1/disk-0')
    expect(devicePage.text()).toContain('DEGRADED')
    expect(devicePage.text()).toMatch(/107[.,]374[.,]182[.,]400 bytes/)
    expect(devicePage.text()).toContain('Read-only configuration-as-code')
    expect(devicePage.text()).toMatch(/Read eligibility\s*Eligible/)
    expect(devicePage.text()).toMatch(/Write eligibility\s*Not eligible/)
    expect(devicePage.findAll('button')).toHaveLength(0)

    const unknownEligibilityPage = await mountRoute('/admin/storage-devices/node-2-disk-0', adminClient({
      getDevice: vi.fn(async () => ({
        id: 'node-2-disk-0', storagePath: '/data/node-2/disk-0', health: 'UNKNOWN',
        totalCapacityBytes: 0, availableCapacityBytes: 0,
      })),
    }))
    expect(unknownEligibilityPage.text()).toMatch(/Read eligibility\s*Unknown — not returned by the Admin API/)
    expect(unknownEligibilityPage.text()).toMatch(/Write eligibility\s*Unknown — not returned by the Admin API/)

    const diskSetPage = await mountRoute('/admin/disk-sets/rack-a', adminClient({
      getDiskSet: vi.fn(async () => ({ name: 'rack-a', failureDomain: 'RACK', devices: ['node-1-disk-0', 'node-2-disk-0'] })),
    }))
    expect(diskSetPage.text()).toContain('Failure domain: RACK')
    expect(diskSetPage.get('a[href="/admin/storage-devices/node-1-disk-0"]')).toBeTruthy()
    expect(diskSetPage.get('a[href="/admin/storage-devices/node-2-disk-0"]')).toBeTruthy()
    expect(diskSetPage.findAll('button')).toHaveLength(0)
  })

  it('shows observability providers as unavailable without examples or healthy defaults', async () => {
    const getOperationalReport = vi.fn(async (report: string) => {
      throw new AdminClientError('http', `${report} provider not configured`, {
        method: 'GET', url: `/admin/reports/${report}`, status: 503,
        code: 'report-provider-not-configured', details: { availability: 'not-configured' },
      })
    })
    const wrapper = await mountRoute('/admin/observability', adminClient({ getOperationalReport }))
    for (const report of ['audit', 'metrics', 'traces']) expect(wrapper.text()).toContain(`${report} provider not configured`)
    expect(wrapper.findAll('h2').filter((heading) => heading.text() === 'Provider not configured')).toHaveLength(3)
    expect(wrapper.text()).not.toMatch(/sample event|generated chart|healthy/i)
  })

  it('runs HeadObject only through the injected S3 diagnostic port and displays returned evidence', async () => {
    const headObject = vi.fn(async () => ({
      request: { method: 'HEAD' as const, target: 'https://s3.example.test/data/diagnostics-2026/probes/readiness.txt' },
      ok: true, status: 200, statusText: 'OK', requestId: 'request-7', eTag: '"abc123"', contentLength: 42,
    }))
    const wrapper = await mountRoute('/admin/s3-diagnostics', adminClient(), { headObject } as S3HeadObjectClient)
    const inputs = wrapper.findAll('input')
    await inputs[0]!.setValue('tenant-a-readonly')
    await inputs[1]!.setValue('diagnostics-2026')
    await inputs[2]!.setValue('probes/readiness.txt')
    await wrapper.get('form').trigger('submit')
    await flushPromises()

    expect(headObject).toHaveBeenCalledWith({
      credentialProfile: 'tenant-a-readonly', bucket: 'diagnostics-2026', key: 'probes/readiness.txt',
    })
    expect(wrapper.text()).toContain('200 OK')
    expect(wrapper.text()).toContain('request-7')
    expect(wrapper.text()).toContain('"abc123"')
    expect(wrapper.text()).toContain('42')
    expect(wrapper.text()).toContain('HEAD')
    expect(wrapper.text()).toContain('https://s3.example.test/data/diagnostics-2026/probes/readiness.txt')
    expect(wrapper.text()).toContain('S3 accepted the request')
    expect(wrapper.text()).toContain('Credentials and signed headers are never displayed')
    expect(wrapper.text()).not.toContain('tenant-a-readonly')
    expect(wrapper.text()).toContain('never calls Admin or private storage-engine object endpoints')
  })

  it('renders a returned S3 error code but no signed headers, authorization, or credential profile', async () => {
    const headObject = vi.fn(async () => ({
      request: { method: 'HEAD' as const, target: 'https://s3.example.test/data/diagnostics-2026/missing.txt' },
      ok: false, status: 403, statusText: 'Forbidden', requestId: 'request-denied-7',
      errorCode: 'AccessDenied',
      authorization: 'AWS4-HMAC-SHA256 Credential=leaked',
      signedHeaders: { authorization: 'secret', 'x-amz-security-token': 'token' },
    }))
    const wrapper = await mountRoute('/admin/s3-diagnostics', adminClient(), { headObject } as unknown as S3HeadObjectClient)
    const inputs = wrapper.findAll('input')
    await inputs[0]!.setValue('tenant-a-readonly')
    await inputs[1]!.setValue('diagnostics-2026')
    await inputs[2]!.setValue('missing.txt')
    await wrapper.get('form').trigger('submit')
    await flushPromises()

    expect(wrapper.text()).toContain('403 Forbidden')
    expect(wrapper.text()).toContain('AccessDenied')
    expect(wrapper.text()).toContain('request-denied-7')
    expect(wrapper.text()).not.toContain('tenant-a-readonly')
    expect(wrapper.text()).not.toContain('AWS4-HMAC-SHA256')
    expect(wrapper.text()).not.toContain('x-amz-security-token')
    expect(wrapper.text()).not.toContain('secret')
  })

  it('shows only bucket capacity accounting and quota administration, with no object CRUD surface', async () => {
    const getBucketCapacity = vi.fn(async () => ({
      bucket: 'archive-2026', usedBytes: 7340032, reservedBytes: 1048576,
      quotaBytes: 10737418240, rejectedReservations: 2, lastRejectedBytes: 524288,
    }))
    const wrapper = await mountRoute('/admin/capacity/archive-2026', adminClient({ getBucketCapacity }))

    expect(getBucketCapacity).toHaveBeenCalledWith('archive-2026')
    expect(wrapper.text()).toContain('archive-2026')
    expect(wrapper.text()).toMatch(/7[.,]340[.,]032 bytes/)
    expect(wrapper.text()).toMatch(/1[.,]048[.,]576 bytes/)
    expect(wrapper.text()).toMatch(/10[.,]737[.,]418[.,]240 bytes/)
    expect(wrapper.text()).toContain('Rejected reservations')
    expect(wrapper.text()).toContain('No bucket or object browsing is available')
    expect(wrapper.findAll('button').map((button) => button.text())).toEqual(['Update quota'])
    expect(wrapper.findAll('label').map((label) => label.text())).toEqual(['New quota in bytes'])
    expect(wrapper.findAll('a').map((link) => link.text()).join(' ')).not.toMatch(/\b(object|bucket)\b/i)
  })
})
