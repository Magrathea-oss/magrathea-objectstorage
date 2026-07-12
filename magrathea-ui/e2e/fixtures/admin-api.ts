import type { Page, Route } from '@playwright/test'

type JsonValue = Record<string, unknown> | readonly unknown[]
type FixtureResponse = { readonly status?: number; readonly json: JsonValue }

const links = {}
const deviceOne = {
  id: 'node-1-disk-0', storagePath: '/data/node-1/disk-0', totalCapacityBytes: 107374182400,
  availableCapacityBytes: 26843545600, health: 'DEGRADED', writeEligible: false, readEligible: true, _links: links,
}
const deviceTwo = {
  id: 'node-2-disk-0', storagePath: '/data/node-2/disk-0', totalCapacityBytes: 107374182400,
  availableCapacityBytes: 53687091200, health: 'HEALTHY', writeEligible: true, readEligible: true, _links: links,
}

const responses: Readonly<Record<string, FixtureResponse>> = Object.freeze({
  'GET /admin/health': { json: { status: 'ok', message: 'Fixture Admin API is available', mode: 'configuration-as-code', _links: links } },
  'GET /admin/live': { json: { status: 'ok', probe: 'liveness', message: 'Admin API process is live', _links: links } },
  'GET /admin/ready': { json: {
    status: 'not-ready', probe: 'readiness', mode: 'configuration-as-code',
    components: [{ name: 'storage-device-catalog', status: 'not-configured', reason: 'No catalog provider is configured' }], _links: links,
  } },
  'GET /admin/backend-status': { json: {
    selectedBackend: 'storage-engine',
    selection: { profile: 'storage-engine', property: { name: 'magrathea.object-store.backend', value: 'storage-engine' } },
    catalogs: {
      policies: { sourceDirectory: 'target/ep7-admin-api/config/storage-policies', availability: 'available', itemCount: 2 },
      devices: { sourceDirectory: 'target/ep7-admin-api/config/storage-devices', availability: 'available', itemCount: 2 },
      diskSets: { sourceDirectory: 'target/ep7-admin-api/config/disk-sets', availability: 'available', itemCount: 1 },
    },
    storageRoots: {
      primary: { path: 'target/ep7-admin-api/storage-engine', availability: 'available' },
      recovery: { path: 'target/ep7-admin-api/recovery', availability: 'not-configured' },
    },
    recoverySummary: { availability: 'not-configured' },
  } },
  'GET /admin/storage-policies': { json: {
    count: 1,
    storagePolicies: [{ storageClassId: 'MINIO_STANDARD', erasureCoding: { dataBlocks: 4, parityBlocks: 2 }, replication: { factor: 1 }, _links: links }],
    _links: links,
  } },
  'GET /admin/storage-policies/minio-standard': { json: {
    storageClassId: 'MINIO_STANDARD', erasureCoding: { dataBlocks: 4, parityBlocks: 2 }, replication: { factor: 1 }, _links: links,
  } },
  'GET /admin/storage-devices': { json: { count: 2, storageDevices: [deviceOne, deviceTwo], _links: links } },
  'GET /admin/storage-devices/node-1-disk-0': { json: deviceOne },
  'GET /admin/storage-devices/node-2-disk-0': { json: deviceTwo },
  'GET /admin/disk-sets': { json: { count: 1, diskSets: [{ name: 'rack-a', failureDomain: 'RACK', devices: ['node-1-disk-0', 'node-2-disk-0'], size: 2, _links: links }], _links: links } },
  'GET /admin/disk-sets/rack-a': { json: { name: 'rack-a', failureDomain: 'RACK', devices: ['node-1-disk-0', 'node-2-disk-0'], size: 2, _links: links } },
  'GET /admin/buckets/archive-2026/capacity': { json: { bucket: 'archive-2026', usedBytes: 7340032, reservedBytes: 1048576, quotaBytes: 10737418240, rejectedReservations: 2, lastRejectedBytes: 2097152 } },
})

function keyFor(route: Route): string {
  const request = route.request()
  return `${request.method()} ${new URL(request.url()).pathname}`
}

function unavailableReport(path: string): FixtureResponse {
  const reportType = path.split('/').at(-1) ?? 'unknown'
  return {
    status: 503,
    json: { error: { code: 'report-provider-not-configured', message: `${reportType} report provider is not configured`, path, details: { reportType, availability: 'not-configured' } } },
  }
}

export async function installAdminApiFixtures(
  page: Page,
  overrides: Readonly<Record<string, FixtureResponse | JsonValue>> = {},
): Promise<void> {
  await page.route('**/admin/**', async (route) => {
    if (route.request().resourceType() === 'document') return route.fallback()
    const key = keyFor(route)
    const override = overrides[key]
    const configured = override && 'json' in override && (Object.hasOwn(override, 'status') || Object.keys(override).length === 1)
      ? override as FixtureResponse
      : override ? { json: override as JsonValue } : responses[key]
    if (configured) return route.fulfill({ status: configured.status ?? 200, contentType: 'application/json', json: configured.json })
    if (key === 'POST /admin/storage-policies/validate') {
      return route.fulfill({ status: 200, contentType: 'application/json', json: { valid: true, errors: [], _links: links } })
    }
    if (key.startsWith('GET /admin/reports/')) {
      const fixture = unavailableReport(new URL(route.request().url()).pathname)
      return route.fulfill({ status: fixture.status, contentType: 'application/json', json: fixture.json })
    }
    return route.fulfill({
      status: 501,
      contentType: 'application/json',
      json: { error: { code: 'fixture-not-defined', message: `No fixture for ${key}`, path: new URL(route.request().url()).pathname } },
    })
  })
}
