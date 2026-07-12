export interface S3HeadObjectInput {
  readonly credentialProfile: string
  readonly bucket: string
  readonly key: string
  readonly versionId?: string
}

export interface S3HeadObjectResult {
  readonly request: Readonly<{ method: 'HEAD'; target: string }>
  readonly ok: boolean
  readonly status: number
  readonly statusText: string
  readonly requestId?: string
  readonly extendedRequestId?: string
  readonly eTag?: string
  readonly contentLength?: number
  readonly contentType?: string
  readonly lastModified?: string
  readonly versionId?: string
  readonly errorCode?: string
}

export type S3DiagnosticErrorKind = 'offline' | 'network' | 'aborted' | 'timeout' | 'configuration' | 'signing'

export class S3DiagnosticError extends Error {
  readonly name = 'S3DiagnosticError'

  constructor(
    readonly kind: S3DiagnosticErrorKind,
    message: string,
    readonly context: {
      readonly url?: string
    } = {},
  ) {
    super(message)
  }
}

export type S3HeadObjectSigner = (
  request: Request,
  context: { readonly credentialProfile: string },
) => Request | Promise<Request>

export interface S3HeadObjectClientOptions {
  /** An explicit S3 Data Plane URL, separate from the Admin API base URL. */
  readonly s3BaseUrl: string
  readonly signRequest: S3HeadObjectSigner
  readonly fetch?: typeof globalThis.fetch
  readonly isOnline?: () => boolean
  readonly signal?: AbortSignal
  readonly timeoutMs?: number
}

export interface S3HeadObjectClient {
  headObject(input: S3HeadObjectInput): Promise<S3HeadObjectResult>
}

function required(value: string, label: string): string {
  if (!value.trim()) throw new TypeError(`${label} must not be empty`)
  return value
}

function encodeObjectKey(key: string): string {
  required(key, 'Object key')
  return key.split('/').map((segment) => {
    if (segment === '.' || segment === '..') {
      throw new TypeError('Object key must not contain dot path segments')
    }
    return encodeURIComponent(segment)
  }).join('/')
}

function normalizedBaseUrl(value: string): URL {
  required(value, 'S3 base URL')
  let url: URL
  try {
    url = new URL(value)
  } catch {
    throw new S3DiagnosticError('configuration', 'S3 base URL must be an absolute URL')
  }
  if (url.protocol !== 'http:' && url.protocol !== 'https:') {
    throw new S3DiagnosticError('configuration', 'S3 base URL must use HTTP or HTTPS', { url: url.toString() })
  }
  if (url.username || url.password || url.search || url.hash) {
    throw new S3DiagnosticError(
      'configuration',
      'S3 base URL must not contain credentials, a query, or a fragment',
    )
  }
  const reservedSegments = new Set(['admin', 'storage-engine'])
  const segments = url.pathname.split('/').filter(Boolean)
  if (segments.some((segment) => reservedSegments.has(segment.toLowerCase()))) {
    throw new S3DiagnosticError(
      'configuration',
      'S3 diagnostics cannot target an Admin or private storage-engine route',
      { url: url.toString() },
    )
  }
  url.pathname = `${url.pathname.replace(/\/+$/, '')}/`
  return url
}

function isWithinS3Base(candidate: URL, base: URL): boolean {
  return candidate.origin === base.origin && candidate.pathname.startsWith(base.pathname)
}

function publicUrl(value: URL): string {
  const redacted = new URL(value)
  redacted.username = ''
  redacted.password = ''
  redacted.search = ''
  redacted.hash = ''
  return redacted.toString()
}

function optionalHeader(headers: Headers, name: string): string | undefined {
  return headers.get(name) ?? undefined
}

function contentLength(headers: Headers): number | undefined {
  const value = headers.get('content-length')
  if (value === null || !/^\d+$/.test(value)) return undefined
  const parsed = Number(value)
  return Number.isSafeInteger(parsed) ? parsed : undefined
}

export function createS3HeadObjectClient(options: S3HeadObjectClientOptions): S3HeadObjectClient {
  const baseUrl = normalizedBaseUrl(options.s3BaseUrl)
  if (options.timeoutMs !== undefined && (!Number.isSafeInteger(options.timeoutMs) || options.timeoutMs <= 0)) {
    throw new TypeError('timeoutMs must be a positive safe integer')
  }
  const fetchImplementation = options.fetch ?? globalThis.fetch
  if (!fetchImplementation) throw new TypeError('A Fetch API implementation is required')
  const isOnline = options.isOnline ?? (() => globalThis.navigator?.onLine !== false)

  return Object.freeze({
    async headObject(input: S3HeadObjectInput): Promise<S3HeadObjectResult> {
      required(input.credentialProfile, 'Credential profile')
      const bucket = encodeURIComponent(required(input.bucket, 'Bucket'))
      const key = encodeObjectKey(input.key)
      const url = new URL(`${bucket}/${key}`, baseUrl)
      if (!isWithinS3Base(url, baseUrl)) {
        throw new S3DiagnosticError('configuration', 'The HeadObject path escaped the configured S3 Data Plane URL')
      }
      if (input.versionId !== undefined) url.searchParams.set('versionId', input.versionId)

      const unsignedRequest = new Request(url, {
        method: 'HEAD',
        headers: { Accept: '*/*' },
        redirect: 'manual',
      })

      let signedRequest: Request
      try {
        signedRequest = await options.signRequest(unsignedRequest, {
          credentialProfile: input.credentialProfile,
        })
      } catch {
        throw new S3DiagnosticError('signing', 'The S3 HeadObject request could not be signed', {
          url: publicUrl(url),
        })
      }

      const signedUrl = new URL(signedRequest.url)
      if (signedRequest.method !== 'HEAD' || !isWithinS3Base(signedUrl, baseUrl)) {
        throw new S3DiagnosticError(
          'configuration',
          'The signed HeadObject request escaped the configured S3 Data Plane URL',
          { url: publicUrl(signedUrl) },
        )
      }

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
        response = await fetchImplementation(signedRequest, { redirect: 'manual', signal: controller.signal })
      } catch {
        const kind: S3DiagnosticErrorKind = timedOut ? 'timeout' : controller.signal.aborted ? 'aborted' : !isOnline() ? 'offline' : 'network'
        const message = kind === 'timeout'
          ? 'The S3 HeadObject request timed out'
          : kind === 'aborted'
            ? 'The S3 HeadObject request was cancelled'
            : kind === 'offline'
              ? 'The S3 diagnostic is unavailable while the browser is offline'
              : 'The S3 HeadObject request could not reach the server'
        throw new S3DiagnosticError(kind, message, { url: publicUrl(signedUrl) })
      } finally {
        if (timer !== undefined) clearTimeout(timer)
        options.signal?.removeEventListener('abort', abort)
      }

      return {
        request: Object.freeze({ method: 'HEAD', target: publicUrl(signedUrl) }),
        ok: response.ok,
        status: response.status,
        statusText: response.statusText,
        requestId: optionalHeader(response.headers, 'x-amz-request-id'),
        extendedRequestId: optionalHeader(response.headers, 'x-amz-id-2'),
        eTag: optionalHeader(response.headers, 'etag'),
        contentLength: contentLength(response.headers),
        contentType: optionalHeader(response.headers, 'content-type'),
        lastModified: optionalHeader(response.headers, 'last-modified'),
        versionId: optionalHeader(response.headers, 'x-amz-version-id'),
        errorCode: optionalHeader(response.headers, 'x-amz-error-code'),
      }
    },
  })
}
