import { describe, expect, it, vi } from 'vitest'
import {
  AdminClientError,
  createAdminApiClient,
} from '../src/adapters/admin-api-client'
import {
  createS3HeadObjectClient,
  S3DiagnosticError,
} from '../src/adapters/s3-head-object-client'

function jsonResponse(body: unknown, status = 200): Response {
  return new Response(JSON.stringify(body), {
    status,
    headers: { 'Content-Type': 'application/json' },
  })
}

describe('Admin API browser adapter', () => {
  it('uses only typed Admin control-plane routes and encodes resource identifiers', async () => {
    const fetchMock = vi.fn(async () => jsonResponse({
      bucket: 'archive/2026',
      usedBytes: 7,
      reservedBytes: 1,
      quotaBytes: 20,
      rejectedReservations: 2,
      lastRejectedBytes: 3,
    }))
    const client = createAdminApiClient({
      baseUrl: 'https://admin.example.test/control',
      fetch: fetchMock,
    })

    await client.getBucketCapacity('archive/2026')

    expect(fetchMock).toHaveBeenCalledOnce()
    expect(String(fetchMock.mock.calls[0]?.[0])).toBe(
      'https://admin.example.test/control/admin/buckets/archive%2F2026/capacity',
    )
    expect(fetchMock.mock.calls[0]?.[1]?.method).toBe('GET')
  })

  it('returns a not-ready document from the readiness probe instead of treating it as an error', async () => {
    const client = createAdminApiClient({
      baseUrl: 'https://admin.example.test',
      fetch: async () => jsonResponse({
        status: 'not-ready',
        probe: 'readiness',
        mode: 'configuration-as-code',
        components: [{ name: 'storage-device-catalog', status: 'not-configured' }],
        _links: {},
      }, 503),
    })

    await expect(client.getReadiness()).resolves.toMatchObject({
      status: 'not-ready',
      components: [{ name: 'storage-device-catalog', status: 'not-configured' }],
    })
  })

  it('preserves structured Admin errors and classifies offline failures truthfully', async () => {
    const unavailable = createAdminApiClient({
      baseUrl: 'https://admin.example.test',
      fetch: async () => jsonResponse({
        error: {
          code: 'report-provider-not-configured',
          message: 'No real operational report provider is configured for scrub',
          path: '/admin/reports/scrub',
          details: { reportType: 'scrub', availability: 'not-configured' },
        },
      }, 503),
    })

    await expect(unavailable.getOperationalReport('scrub')).rejects.toMatchObject({
      kind: 'http',
      context: {
        status: 503,
        code: 'report-provider-not-configured',
        path: '/admin/reports/scrub',
        details: { reportType: 'scrub', availability: 'not-configured' },
      },
    } satisfies Partial<AdminClientError>)

    const offline = createAdminApiClient({
      baseUrl: 'https://admin.example.test',
      fetch: async () => { throw new TypeError('Failed to fetch') },
      isOnline: () => false,
    })
    await expect(offline.getHealth()).rejects.toMatchObject({ kind: 'offline' })
  })

  it('normalizes explicit cancellation without retaining the fetch failure', async () => {
    const controller = new AbortController()
    const client = createAdminApiClient({
      baseUrl: 'https://admin.example.test',
      signal: controller.signal,
      fetch: async (_input, init) => new Promise((_resolve, reject) => {
        if (init?.signal?.aborted) reject(new DOMException('Aborted', 'AbortError'))
        else init?.signal?.addEventListener('abort', () => reject(new DOMException('Aborted', 'AbortError')))
      }),
    })
    controller.abort()

    await expect(client.getHealth()).rejects.toMatchObject({
      kind: 'aborted',
      context: { url: 'https://admin.example.test/admin/health' },
    })
  })
})

describe('S3 HeadObject browser adapter', () => {
  it('signs and sends HEAD only beneath the separately configured S3 base URL', async () => {
    const signRequest = vi.fn(async (request: Request) => {
      const headers = new Headers(request.headers)
      headers.set('Authorization', 'AWS4-HMAC-SHA256 test-signature')
      return new Request(request, { headers })
    })
    const fetchMock = vi.fn(async (request: RequestInfo | URL) => {
      const received = request as Request
      expect(received.method).toBe('HEAD')
      expect(received.url).toBe(
        'https://s3.example.test/data/diagnostics-2026/probes/readiness.txt',
      )
      return new Response(null, {
        status: 200,
        headers: {
          etag: '"abc123"',
          'content-length': '42',
          'x-amz-request-id': 'request-7',
        },
      })
    })
    const client = createS3HeadObjectClient({
      s3BaseUrl: 'https://s3.example.test/data/',
      signRequest,
      fetch: fetchMock,
    })

    await expect(client.headObject({
      credentialProfile: 'tenant-a-readonly',
      bucket: 'diagnostics-2026',
      key: 'probes/readiness.txt',
    })).resolves.toMatchObject({
      ok: true,
      status: 200,
      requestId: 'request-7',
      eTag: '"abc123"',
      contentLength: 42,
    })
    expect(signRequest).toHaveBeenCalledWith(expect.any(Request), {
      credentialProfile: 'tenant-a-readonly',
    })
  })

  it('rejects Admin routes and a signer that redirects a diagnostic outside the S3 base', async () => {
    expect(() => createS3HeadObjectClient({
      s3BaseUrl: 'https://api.example.test/admin/objects',
      signRequest: (request) => request,
    })).toThrow(S3DiagnosticError)

    const client = createS3HeadObjectClient({
      s3BaseUrl: 'https://s3.example.test/data/',
      signRequest: () => new Request('https://admin.example.test/admin/objects/x', { method: 'HEAD' }),
      fetch: async () => new Response(null, { status: 200 }),
    })

    await expect(client.headObject({
      credentialProfile: 'tenant-a-readonly',
      bucket: 'diagnostics-2026',
      key: 'readiness.txt',
    })).rejects.toMatchObject({ kind: 'configuration' })
  })

  it('reports an S3 error response without inventing an error code', async () => {
    const client = createS3HeadObjectClient({
      s3BaseUrl: 'https://s3.example.test/',
      signRequest: (request) => request,
      fetch: async () => new Response(null, {
        status: 403,
        statusText: 'Forbidden',
        headers: { 'x-amz-request-id': 'denied-1' },
      }),
    })

    const result = await client.headObject({
      credentialProfile: 'tenant-a-readonly',
      bucket: 'diagnostics-2026',
      key: 'restricted.txt',
    })
    expect(result).toMatchObject({ ok: false, status: 403, requestId: 'denied-1' })
    expect(result.errorCode).toBeUndefined()
  })

  it('redacts signed query credentials when normalizing a network failure', async () => {
    const client = createS3HeadObjectClient({
      s3BaseUrl: 'https://s3.example.test/data/',
      signRequest: (request) => new Request(`${request.url}?X-Amz-Credential=secret&X-Amz-Signature=secret`, {
        method: 'HEAD',
      }),
      fetch: async () => { throw new TypeError('Failed to fetch with secret') },
    })

    await expect(client.headObject({
      credentialProfile: 'tenant-a-readonly',
      bucket: 'diagnostics-2026',
      key: 'restricted.txt',
    })).rejects.toMatchObject({
      kind: 'network',
      context: { url: 'https://s3.example.test/data/diagnostics-2026/restricted.txt' },
    })
  })
})
