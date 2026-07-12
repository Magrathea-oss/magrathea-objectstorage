export interface AdminLink {
  readonly href: string
  readonly method?: string
  readonly templated?: boolean
}

export type AdminLinks = Readonly<Record<string, AdminLink>>

export interface AdminHealth {
  readonly status: 'ok'
  readonly message: string
  readonly mode: 'configuration-as-code'
  readonly _links: AdminLinks
}

export interface AdminLiveness {
  readonly status: 'ok'
  readonly probe: 'liveness'
  readonly message: string
  readonly _links: AdminLinks
}

export interface ReadinessComponent {
  readonly name: string
  readonly status: 'ready' | 'not-configured' | 'unavailable'
  readonly items?: number
  readonly reason?: string
}

export interface AdminReadiness {
  readonly status: 'ready' | 'not-ready'
  readonly probe: 'readiness'
  readonly mode: 'configuration-as-code'
  readonly components: readonly ReadinessComponent[]
  readonly _links: AdminLinks
}

export interface CatalogStatus {
  readonly sourceDirectory: string
  readonly availability: 'available' | 'unavailable' | 'not-configured'
  readonly itemCount?: number
  readonly error?: string
}

export interface BackendStatus {
  readonly selectedBackend: string
  readonly selection: {
    readonly profile: string
    readonly property: { readonly name: string; readonly value: string }
  }
  readonly catalogs: {
    readonly policies: CatalogStatus
    readonly devices: CatalogStatus
    readonly diskSets: CatalogStatus
  }
  readonly storageRoots: Readonly<
    Record<string, { readonly path: string; readonly availability: 'available' | 'unavailable' }>
  >
  readonly recoverySummary: Readonly<Record<string, unknown>>
}

export interface DedupConfiguration {
  readonly scope: string
  readonly algorithm: string
  readonly chunkSize: number
  readonly alignment: string
}

export interface StoragePolicy {
  readonly storageClassId: string
  readonly dedup?: DedupConfiguration
  readonly compression?: { readonly algorithm: string; readonly level: number }
  readonly encryption?: { readonly algorithm: string; readonly defaultKeyReference?: string }
  readonly erasureCoding?: { readonly dataBlocks: number; readonly parityBlocks: number }
  readonly replication: { readonly factor: number }
  readonly _links: AdminLinks
}

export interface StoragePolicyProposal {
  readonly storageClassId: string
  readonly dedup?: Readonly<Record<string, unknown>>
  readonly compression?: Readonly<Record<string, unknown>>
  readonly encryption?: Readonly<Record<string, unknown>>
  readonly erasureCoding?: { readonly dataBlocks: number; readonly parityBlocks: number }
  readonly replication?: { readonly factor: number }
  readonly replicationFactor?: number
}

export interface PolicyValidationError {
  readonly field: string
  readonly message: string
}

export interface PolicyValidationResult {
  readonly valid: boolean
  readonly policy?: StoragePolicy
  readonly errors: readonly PolicyValidationError[]
  readonly _links: AdminLinks
}

export interface StorageDevice {
  readonly id: string
  readonly storagePath: string
  readonly totalCapacityBytes: number
  readonly availableCapacityBytes: number
  readonly health: string
  readonly writeEligible?: boolean
  readonly readEligible?: boolean
  readonly _links: AdminLinks
}

export interface DiskSet {
  readonly name: string
  readonly failureDomain: string
  readonly devices: readonly string[]
  readonly size: number
  readonly _links: AdminLinks
}

export interface AdminCollection {
  readonly count: number
  readonly _links: AdminLinks
}

export interface StoragePolicyCollection extends AdminCollection {
  readonly storagePolicies: readonly StoragePolicy[]
}

export interface StorageDeviceCollection extends AdminCollection {
  readonly storageDevices: readonly StorageDevice[]
}

export interface DiskSetCollection extends AdminCollection {
  readonly diskSets: readonly DiskSet[]
}

export interface BucketCapacity {
  readonly bucket: string
  readonly usedBytes: number
  readonly reservedBytes: number
  readonly quotaBytes: number
  readonly rejectedReservations: number
  readonly lastRejectedBytes: number
}

export type OperationalReportType =
  | 'recovery'
  | 'garbage-collection'
  | 'scrub'
  | 'audit'
  | 'metrics'
  | 'traces'

export type OperationalReport = Readonly<Record<string, unknown>>

export interface AdminErrorDocument {
  readonly error: {
    readonly code: string
    readonly message: string
    readonly path: string
    readonly details?: Readonly<Record<string, unknown>>
  }
  readonly _links?: AdminLinks
}

export type AdminClientErrorKind = 'offline' | 'network' | 'aborted' | 'timeout' | 'http' | 'invalid-response'

export class AdminClientError extends Error {
  readonly name = 'AdminClientError'

  constructor(
    readonly kind: AdminClientErrorKind,
    message: string,
    readonly context: {
      readonly method: string
      readonly url: string
      readonly status?: number
      readonly code?: string
      readonly path?: string
      readonly details?: Readonly<Record<string, unknown>>
    },
  ) {
    super(message)
  }
}

export interface AdminApiClient {
  getHealth(): Promise<AdminHealth>
  getLiveness(): Promise<AdminLiveness>
  getReadiness(): Promise<AdminReadiness>
  getBackendStatus(): Promise<BackendStatus>
  listPolicies(): Promise<StoragePolicyCollection>
  getPolicy(id: string): Promise<StoragePolicy>
  validatePolicy(proposal: StoragePolicyProposal): Promise<PolicyValidationResult>
  listDevices(): Promise<StorageDeviceCollection>
  getDevice(id: string): Promise<StorageDevice>
  listDiskSets(): Promise<DiskSetCollection>
  getDiskSet(id: string): Promise<DiskSet>
  getBucketCapacity(bucket: string): Promise<BucketCapacity>
  configureBucketQuota(bucket: string, quotaBytes: number): Promise<BucketCapacity>
  getOperationalReport(report: OperationalReportType): Promise<OperationalReport>
}

export interface AdminApiClientOptions {
  readonly baseUrl: string
  readonly fetch?: typeof globalThis.fetch
  readonly isOnline?: () => boolean
  readonly signal?: AbortSignal
  readonly timeoutMs?: number
}

function encodePathSegment(value: string, label: string): string {
  if (value.length === 0) throw new TypeError(`${label} must not be empty`)
  return encodeURIComponent(value)
}

function endpoint(baseUrl: URL, path: string): URL {
  let basePath = baseUrl.pathname.endsWith('/') ? baseUrl.pathname.slice(0, -1) : baseUrl.pathname
  if (basePath.endsWith('/admin') && path.startsWith('/admin/')) {
    basePath = basePath.slice(0, -'/admin'.length)
  }
  const url = new URL(baseUrl.toString())
  url.pathname = `${basePath}${path}`
  url.search = ''
  url.hash = ''
  return url
}

function isErrorDocument(value: unknown): value is AdminErrorDocument {
  if (typeof value !== 'object' || value === null || !('error' in value)) return false
  const error = (value as { error?: unknown }).error
  return typeof error === 'object' && error !== null
    && typeof (error as { code?: unknown }).code === 'string'
    && typeof (error as { message?: unknown }).message === 'string'
    && typeof (error as { path?: unknown }).path === 'string'
}

export function createAdminApiClient(options: AdminApiClientOptions): AdminApiClient {
  if (!options.baseUrl.trim()) throw new TypeError('Admin API baseUrl must not be empty')
  const baseUrl = new URL(options.baseUrl, globalThis.location?.href ?? 'http://localhost/')
  if (!['http:', 'https:'].includes(baseUrl.protocol)) throw new TypeError('Admin API baseUrl must use HTTP or HTTPS')
  if (baseUrl.username || baseUrl.password || baseUrl.search || baseUrl.hash) {
    throw new TypeError('Admin API baseUrl must not contain credentials, a query, or a fragment')
  }
  if (options.timeoutMs !== undefined && (!Number.isSafeInteger(options.timeoutMs) || options.timeoutMs <= 0)) {
    throw new TypeError('timeoutMs must be a positive safe integer')
  }
  const fetchImplementation = options.fetch ?? globalThis.fetch
  if (!fetchImplementation) throw new TypeError('A Fetch API implementation is required')
  const isOnline = options.isOnline ?? (() => globalThis.navigator?.onLine !== false)

  async function request<T>(
    method: 'GET' | 'POST' | 'PUT',
    path: string,
    body?: unknown,
    acceptedStatuses: readonly number[] = [200],
  ): Promise<T> {
    const url = endpoint(baseUrl, path)
    const controller = new AbortController()
    let timedOut = false
    const abort = () => controller.abort()
    options.signal?.addEventListener('abort', abort, { once: true })
    if (options.signal?.aborted) abort()
    const timer = options.timeoutMs === undefined ? undefined : setTimeout(() => {
      timedOut = true
      controller.abort()
    }, options.timeoutMs)
    let response: Response
    try {
      response = await fetchImplementation(url, {
        method,
        headers: body === undefined ? { Accept: 'application/json' } : {
          Accept: 'application/json',
          'Content-Type': 'application/json',
        },
        body: body === undefined ? undefined : JSON.stringify(body),
        signal: controller.signal,
      })
    } catch {
      const kind: AdminClientErrorKind = timedOut ? 'timeout' : controller.signal.aborted ? 'aborted' : !isOnline() ? 'offline' : 'network'
      const message = kind === 'timeout'
        ? 'The Admin API request timed out'
        : kind === 'aborted'
          ? 'The Admin API request was cancelled'
          : kind === 'offline'
            ? 'The Admin API is unavailable while the browser is offline'
            : 'The Admin API request could not reach the server'
      throw new AdminClientError(kind, message, { method, url: url.toString() })
    } finally {
      if (timer !== undefined) clearTimeout(timer)
      options.signal?.removeEventListener('abort', abort)
    }

    let payload: unknown
    try {
      payload = await response.json()
    } catch {
      throw new AdminClientError('invalid-response', 'The Admin API returned a non-JSON response', {
        method,
        url: url.toString(),
        status: response.status,
      })
    }

    if (!acceptedStatuses.includes(response.status)) {
      if (isErrorDocument(payload)) {
        throw new AdminClientError('http', payload.error.message, {
          method,
          url: url.toString(),
          status: response.status,
          code: payload.error.code,
          path: payload.error.path,
          details: payload.error.details,
        })
      }
      throw new AdminClientError('http', `The Admin API returned HTTP ${response.status}`, {
        method,
        url: url.toString(),
        status: response.status,
      })
    }
    return payload as T
  }

  const client: AdminApiClient = {
    getHealth: () => request<AdminHealth>('GET', '/admin/health'),
    getLiveness: () => request<AdminLiveness>('GET', '/admin/live'),
    getReadiness: () => request<AdminReadiness>('GET', '/admin/ready', undefined, [200, 503]),
    getBackendStatus: () => request<BackendStatus>('GET', '/admin/backend-status'),
    listPolicies: () => request<StoragePolicyCollection>('GET', '/admin/storage-policies'),
    getPolicy: (id) => request<StoragePolicy>('GET', `/admin/storage-policies/${encodePathSegment(id, 'Policy ID')}`),
    validatePolicy: (proposal) => request<PolicyValidationResult>('POST', '/admin/storage-policies/validate', proposal),
    listDevices: () => request<StorageDeviceCollection>('GET', '/admin/storage-devices'),
    getDevice: (id) => request<StorageDevice>('GET', `/admin/storage-devices/${encodePathSegment(id, 'Device ID')}`),
    listDiskSets: () => request<DiskSetCollection>('GET', '/admin/disk-sets'),
    getDiskSet: (id) => request<DiskSet>('GET', `/admin/disk-sets/${encodePathSegment(id, 'Disk-set ID')}`),
    getBucketCapacity: (bucket) => request<BucketCapacity>('GET', `/admin/buckets/${encodePathSegment(bucket, 'Bucket')}/capacity`),
    configureBucketQuota: (bucket, quotaBytes) => {
      if (!Number.isSafeInteger(quotaBytes) || quotaBytes < 0) {
        throw new TypeError('quotaBytes must be a non-negative safe integer')
      }
      return request<BucketCapacity>('PUT', `/admin/buckets/${encodePathSegment(bucket, 'Bucket')}/quota`, { quotaBytes })
    },
    getOperationalReport: (report) => request<OperationalReport>('GET', `/admin/reports/${report}`),
  }
  return Object.freeze(client)
}
